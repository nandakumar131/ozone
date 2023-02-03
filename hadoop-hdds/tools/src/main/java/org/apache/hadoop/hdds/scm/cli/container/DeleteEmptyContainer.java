/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.scm.cli.container;

import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.hdds.scm.container.ContainerInfo;
import org.apache.hadoop.hdds.scm.container.common.helpers.ContainerWithPipeline;
import org.apache.hadoop.hdds.scm.protocol.StorageContainerLocationProtocol;
import org.apache.hadoop.hdds.utils.HAUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeleteEmptyContainer {

  private final StorageContainerLocationProtocol scmClient;
  private final int batchSize;


  private DeleteEmptyContainer(int batchSize) {
    final OzoneConfiguration conf = new OzoneConfiguration();
    this.scmClient = HAUtils.getScmContainerClient(conf);
    this.batchSize = batchSize;
  }


  private List<Long> getEmptyClosingContainer() throws IOException {
    final List<Long> containersToDelete = new ArrayList<>();
    final List<Long> emptyContainers = new ArrayList<>();
    final List<ContainerInfo> containers = scmClient.listContainer(
        0, batchSize, HddsProtos.LifeCycleState.CLOSING);

    if (containers.isEmpty()) {
      return Collections.emptyList();
    }

    for(ContainerInfo c : containers) {
      if (c.getNumberOfKeys() == 0 && c.getUsedBytes() == 0) {
        emptyContainers.add(c.getContainerID());
      }
    }

    final List<ContainerWithPipeline> containerWithPipelines = scmClient
        .getContainerWithPipelineBatch(emptyContainers);
    for (ContainerWithPipeline cwp : containerWithPipelines) {
      if(cwp.getPipeline().getNodes().isEmpty()) {
        final ContainerInfo ci = cwp.getContainerInfo();
        containersToDelete.add(ci.getContainerID());
        System.out.println("Container " + ci.containerID() + " is marked for deletion.");
      }
    }
    return containersToDelete;
  }

  private void run() throws IOException {
    while (true) {
      final List<Long> containersToDelete = getEmptyClosingContainer();
      if (containersToDelete.isEmpty()) {
        break;
      }
      for (Long containerID : containersToDelete) {
        scmClient.deleteContainer(containerID);
      }
      System.out.println("Deleted Containers: " + containersToDelete);
    }
  }

  public static void main(String[] args) throws IOException {
    int batchSize = 100;
    if (args.length != 0) {
      batchSize = Integer.parseInt(args[0]);
    }
    new DeleteEmptyContainer(batchSize).run();

  }
}
