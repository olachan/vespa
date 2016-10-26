// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.io.File;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * API to simplify the com.github.dockerjava API for clients,
 * and to avoid OSGi exporting those classes.
 */
public interface Docker {
    interface CreateContainerCommand {
        CreateContainerCommand withLabel(String name, String value);
        CreateContainerCommand withEnvironment(String name, String value);
        CreateContainerCommand withVolume(String path, String volumePath);
        CreateContainerCommand withMemoryInMb(long megaBytes);
        CreateContainerCommand withNetworkMode(String mode);
        CreateContainerCommand withIpAddress(InetAddress address);
        CreateContainerCommand withUlimit(String name, int softLimit, int hardLimit);
        CreateContainerCommand withEntrypoint(String... entrypoint);

        void create();
    }

    CreateContainerCommand createContainerCommand(
            DockerImage dockerImage,
            ContainerName containerName,
            String hostName);


    interface ContainerInfo {
        /** returns Optional.empty() if not running. */
        Optional<Integer> getPid();
    }

    ContainerInfo inspectContainer(ContainerName containerName);


    interface ContainerStats {
        Map<String, Object> getNetworks();
        Map<String, Object> getCpuStats();
        Map<String, Object> getMemoryStats();
        Map<String, Object> getBlkioStats();
    }

    ContainerStats getContainerStats(ContainerName containerName);
    
    void startContainer(ContainerName containerName);

    void stopContainer(ContainerName containerName);

    void deleteContainer(ContainerName containerName);

    void connectContainerToNetwork(ContainerName containerName, String networkName);

    void copyArchiveToContainer(String sourcePath, ContainerName destinationContainer, String destinationPath);

    List<Container> getAllManagedContainers();

    Optional<Container> getContainer(String hostname);

    CompletableFuture<DockerImage> pullImageAsync(DockerImage image);

    boolean imageIsDownloaded(DockerImage image);

    void deleteImage(DockerImage dockerImage);

    void buildImage(File dockerfile, DockerImage dockerImage);

    /**
     * Deletes the local images that are currently not in use by any container and not recently used.
     */
    void deleteUnusedDockerImages();

    /**
     * TODO: Make this function interruptible, see https://github.com/spotify/docker-client/issues/421
     *
     * @param args          Program arguments. args[0] must be the program filename.
     * @throws RuntimeException  (or some subclass thereof) on failure, including docker failure, command failure
     */
    ProcessResult executeInContainer(ContainerName containerName, String... args);
}
