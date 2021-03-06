// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.yahoo.cache.SizeCalculator;
import com.yahoo.search.Result;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;

/**
 * <p>A generic cache which keeps the total memory consumed by its content
 * below a configured maximum.</p>
 *
 * <p>Thread safe.</p>
 *
 * @author vegardh
 */
public class Cache<K, V> {
    private Value elems = null;
    private Value entrySizes = null;

    private Map<CacheKey<K>,CacheValue<K, V>> content=new LinkedHashMap<>(12500, 1.0f, true);
    private SizeCalculator calc = new SizeCalculator();
    private long maxSizeBytes;

    private long currentSizeBytes=0;

    /** The time an element is allowed to live, negative for indefinite lifespan */
    private long timeToLiveMillis=-1;

    /** The max allowed size of an entry */
    private long maxEntrySizeBytes=10000;

    /**
     * Creates a new cache
     *
     * @param maxSizeBytes the max size in bytes this cache is permitted to consume,
     *        including Result objects and Query keys
     * @param timeToLiveMillis a negative value means unlimited time
     * @param manager the current Statistics manager acquired by injection
     */
    public Cache(long maxSizeBytes,long timeToLiveMillis, long maxEntrySizeBytes, Statistics manager) {
        this.maxSizeBytes=maxSizeBytes;
        this.timeToLiveMillis=timeToLiveMillis;
        this.maxEntrySizeBytes=maxEntrySizeBytes;
        initStats(manager);
    }

    private void initStats(Statistics manager) {
        elems = new Value("querycache_elems", manager, new Value.Parameters()
                .setLogRaw(true).setNameExtension(true).setLogMax(true));
        entrySizes = new Value("querycache_entry_sizes", manager,
                new Value.Parameters().setLogRaw(false).setLogMean(true)
                        .setNameExtension(true).setLogMax(true));
    }

    private synchronized CacheValue<K, V> synchGet(CacheKey<K> k) {
        return content.get(k);
    }

    private synchronized boolean synchPut(K key,V value, long keySizeBytes, long valueSizeBytes) {
        // log.info("Put "+key.toString()+ " key size:"+keySizeBytes+" val size:"+valueSizeBytes);
        makeRoomForBytes(valueSizeBytes+keySizeBytes);
        CacheKey<K> cacheKey = new CacheKey<>(keySizeBytes, key);
        CacheValue<K, V> cacheValue;
        if (timeToLiveMillis<0) {
            cacheValue=new CacheValue<>(valueSizeBytes,value, cacheKey);
        } else {
            cacheValue=new AgingCacheValue<>(valueSizeBytes,value, cacheKey);
        }
        currentSizeBytes+=(valueSizeBytes+keySizeBytes);
        elems.put(content.size());
        content.put(cacheKey, cacheValue);
        return true;
    }

    /**
     * Attempts to add a value to the cache
     *
     * @param key the key of the value
     * @param value the value to add
     * @return true if the value was added, false if it could not be added
     */
    public boolean put(K key,V value) {
        if (value instanceof Result) { // Optimized for CachingSearcher. Assuming the key is the Query.
            long totalSizeBytes = calc.sizeOf(value); // Result has a Query field
            if (tooBigToCache(totalSizeBytes)) {
                return false;
            }
            entrySizes.put(totalSizeBytes);
            return synchPut(key, value, 0, totalSizeBytes);
        }
        long keySizeBytes=calc.sizeOf(key);
        long valueSizeBytes=calc.sizeOf(value);
        if (tooBigToCache(keySizeBytes+valueSizeBytes)) {
            return false;
        }
        entrySizes.put(keySizeBytes+valueSizeBytes);
        return synchPut(key, value, keySizeBytes, valueSizeBytes);
    }

    /**
     * Don't cache elems that are too big, even if there's space
     */
    private boolean tooBigToCache(long totalSize) {
        if (totalSize > maxEntrySizeBytes) {
            return true;
        }
        if (totalSize > maxSizeBytes) {
            return true;
        }
        return false;
    }

    private void makeRoomForBytes(long bytes) {
        if ((maxSizeBytes-currentSizeBytes) > bytes) {
                return;
        }
        if (content.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<CacheKey<K>, CacheValue<K, V>>> i = content.entrySet().iterator() ; i.hasNext() ; ) {
                Map.Entry<CacheKey<K>, CacheValue<K, V>> entry = i.next();
            CacheKey<K> key = entry.getKey();
            CacheValue<K, V> value = entry.getValue();
            // Can't call this.removeField(), breaks iterator.
            i.remove();  // Access order: first ones are LRU.
            currentSizeBytes-=key.sizeBytes();
            currentSizeBytes-=value.sizeBytes();
            if ((maxSizeBytes-currentSizeBytes) > bytes) {
                break;
            }
        }
    }

    public boolean containsKey(K k) {
        return content.containsKey(new CacheKey<>(-1, k));
    }

    /** Returns a value, if it is present in the cache */
    public V get(K key) {
        // Currently it works to make a new CacheKey object without size
        // because we have changed hashCode() there.
        CacheKey<K> cacheKey = new CacheKey<>(-1, key);
        CacheValue<K, V> value=synchGet(cacheKey);
        if (value==null) {
            return null;
        }
        if (timeToLiveMillis<0) {
            return value.value();
        }

        if (value.expired(timeToLiveMillis)) {
            //  There was a value, which has now expired
            remove(key);
            return null;
        } else {
            return value.value();
        }
    }

    /**
     * Removes a cache value if present
     *
     * @return true if the value was removed, false if it was not present
     */
    public synchronized boolean remove(K key) {
        CacheValue<K, V> value=content.remove(key);
        if (value==null) {
            return false;
        }
        currentSizeBytes-=value.sizeBytes();
        currentSizeBytes-=value.getKey().sizeBytes();
        elems.put(content.size());
        return true;
    }

    public int size() {
        return content.size();
    }

    private static class CacheKey<K> {
        private long sizeBytes;
        private K key;
        public CacheKey(long sizeBytes,K key) {
                this.sizeBytes=sizeBytes;
            this.key=key;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public K getKey() {
            return key;
        }

        public int hashCode() {
                return key.hashCode();
        }

        @SuppressWarnings("rawtypes")
        public boolean equals(Object k) {
                if (key==null) {
                return false;
            }
            if (k==null) {
                return false;
            }
            if (k instanceof CacheKey) {
                return key.equals(((CacheKey)k).getKey());
            }
            return false;
        }

        public String toString() {
            return key.toString();
        }

    }

    private static class CacheValue<K, V> {
        private long sizeBytes;
        private V value;
        private CacheKey<K> key;
        public CacheValue(long sizeBytes, V value, CacheKey<K> key) {
            this.sizeBytes=sizeBytes;
            this.value=value;
            this.key = key;
        }

        public boolean expired(long ttl) {
            return false;
        }

        public V value() {
            return value;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public CacheKey<K> getKey() {
            return key;
        }

        public String toString() {
            return value.toString();
        }

    }

    private static class AgingCacheValue<K, V> extends CacheValue<K, V> {
        private long birthTimeMillis;

        public AgingCacheValue(long sizeBytes,V value, CacheKey<K> key) {
            super(sizeBytes,value, key);
            this.birthTimeMillis=System.currentTimeMillis();
        }

        public long ageMillis() {
            return System.currentTimeMillis()-birthTimeMillis;
        }

        public boolean expired(long ttl) {
            return (ageMillis() >= ttl);
        }
    }

}
