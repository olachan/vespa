// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadpoolimpl.h"
#include "threadimpl.h"
#include <vespa/vespalib/util/exceptions.h>

using vespalib::IllegalStateException;

namespace storage {
namespace framework {
namespace defaultimplementation {

ThreadPoolImpl::ThreadPoolImpl(Clock& clock)
    : _backendThreadPool(512 * 1024),
      _clock(clock),
      _stopping(false)
{ }

ThreadPoolImpl::~ThreadPoolImpl()
{
    {
        vespalib::LockGuard lock(_threadVectorLock);
        _stopping = true;
        for (uint32_t i=0, n=_threads.size(); i<n; ++i) {
            _threads[i]->interrupt();
        }
        for (uint32_t i=0, n=_threads.size(); i<n; ++i) {
            _threads[i]->join();
        }
    }
    for (uint32_t i=0; true; i+=10) {
        {
            vespalib::LockGuard lock(_threadVectorLock);
            if (_threads.empty()) break;
        }
        if (i > 1000) {
            fprintf(stderr, "Failed to kill thread pool. Threads won't die. (And "
                            "if allowing thread pool object to be deleted this "
                            "will create a segfault later)\n");
            abort();
        }
        FastOS_Thread::Sleep(10);
    }
    _backendThreadPool.Close();
}

Thread::UP
ThreadPoolImpl::startThread(Runnable& runnable,
                            vespalib::stringref id,
                            uint64_t waitTimeMs,
                            uint64_t maxProcessTime,
                            int ticksBeforeWait)
{
    vespalib::LockGuard lock(_threadVectorLock);
    if (_stopping) {
        throw vespalib::IllegalStateException("Threadpool is stopping", VESPA_STRLOC);
    }
    ThreadImpl* ti;
    Thread::UP t(ti = new ThreadImpl(
        *this, runnable, id, waitTimeMs, maxProcessTime, ticksBeforeWait));
    _threads.push_back(ti);
    return t;
}

void
ThreadPoolImpl::visitThreads(ThreadVisitor& visitor) const
{
    vespalib::LockGuard lock(_threadVectorLock);
    for (uint32_t i=0, n=_threads.size(); i<n; ++i) {
        visitor.visitThread(_threads[i]->getId(), _threads[i]->getProperties(),
                            _threads[i]->getTickData());
    }
}

void
ThreadPoolImpl::unregisterThread(ThreadImpl& t)
{
    vespalib::LockGuard lock(_threadVectorLock);
    std::vector<ThreadImpl*> threads;
    threads.reserve(_threads.size());
    for (uint32_t i=0, n=_threads.size(); i<n; ++i) {
        if (_threads[i] != &t) {
            threads.push_back(_threads[i]);
        }
    }
    _threads.swap(threads);
}

} // defaultimplementation
} // framework
} // storage
