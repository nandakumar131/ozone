/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.scm.container.states;

import static org.apache.hadoop.hdds.scm.exceptions.SCMException.ResultCodes.FAILED_TO_CHANGE_CONTAINER_STATE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hadoop.hdds.client.ReplicationConfig;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos.LifeCycleState;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos.ReplicationType;
import org.apache.hadoop.hdds.scm.container.ContainerID;
import org.apache.hadoop.hdds.scm.container.ContainerInfo;
import org.apache.hadoop.hdds.scm.container.ContainerReplica;
import org.apache.hadoop.hdds.scm.exceptions.SCMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container State Map acts like a unified map for various attributes that are
 * used to select containers when we need allocated blocks.
 * <p>
 * This class provides the ability to query 5 classes of attributes. They are
 * <p>
 * 1. LifeCycleStates - LifeCycle States of container describe in which state
 * a container is. For example, a container needs to be in Open State for a
 * client to able to write to it.
 * <p>
 * 2. Owners - Each instance of Name service, for example, Namenode of HDFS or
 * Ozone Manager (OM) of Ozone or CBlockServer --  is an owner. It is
 * possible to have many OMs for a Ozone cluster and only one SCM. But SCM
 * keeps the data from each OM in separate bucket, never mixing them. To
 * write data, often we have to find all open containers for a specific owner.
 * <p>
 * 3. ReplicationType - The clients are allowed to specify what kind of
 * replication pipeline they want to use. Each Container exists on top of a
 * pipeline, so we need to get ReplicationType that is specified by the user.
 * <p>
 * 4. ReplicationConfig - The replication config represents how many copies
 * of data should be made, right now we support 2 different types, ONE
 * Replica and THREE Replica. User can specify how many copies should be made
 * for a ozone key.
 * <p>
 * The most common access pattern of this class is to select a container based
 * on all these parameters, for example, when allocating a block we will
 * select a container that belongs to user1, with Ratis replication which can
 * make 3 copies of data. The fact that we will look for open containers by
 * default and if we cannot find them we will add new containers.
 *
 * All the calls are idempotent.
 */
public class ContainerStateMap {
  private static final Logger LOG =
      LoggerFactory.getLogger(ContainerStateMap.class);

  private final ContainerAttribute<LifeCycleState> lifeCycleStateMap;
  private final ContainerAttribute<String> ownerMap;
  private final ContainerAttribute<ReplicationConfig> repConfigMap;
  private final ContainerAttribute<ReplicationType> typeMap;
  private final Map<ContainerID, ContainerInfo> containerMap;
  private final Map<ContainerID, Set<ContainerReplica>> replicaMap;

  /**
   * Create a ContainerStateMap.
   */
  public ContainerStateMap() {
    this.lifeCycleStateMap = new ContainerAttribute<>();
    this.ownerMap = new ContainerAttribute<>();
    this.repConfigMap = new ContainerAttribute<>();
    this.typeMap = new ContainerAttribute<>();
    this.containerMap = new ConcurrentHashMap<>();
    this.replicaMap = new ConcurrentHashMap<>();
  }

  @VisibleForTesting
  public static Logger getLogger() {
    return LOG;
  }

  /**
   * Adds a ContainerInfo Entry in the ContainerStateMap.
   *
   * @param info - container info
   * @throws SCMException - throws if create failed.
   */
  public void addContainer(final ContainerInfo info)
      throws SCMException {
    Preconditions.checkNotNull(info, "Container Info cannot be null");
    final ContainerID id = info.containerID();
    if (!contains(id)) {
      containerMap.put(id, info);
      lifeCycleStateMap.insert(info.getState(), id);
      ownerMap.insert(info.getOwner(), id);
      repConfigMap.insert(info.getReplicationConfig(), id);
      typeMap.insert(info.getReplicationType(), id);
      replicaMap.put(id, Collections.emptySet());

      LOG.trace("Container {} added to ContainerStateMap.", id);
    }
  }

  public boolean contains(final ContainerID id) {
    return containerMap.containsKey(id);
  }

  /**
   * Removes a Container Entry from ContainerStateMap.
   *
   * @param id - ContainerID
   */
  public void removeContainer(final ContainerID id) {
    Preconditions.checkNotNull(id, "ContainerID cannot be null");
    if (contains(id)) {
      // Should we revert back to the original state if any of the below
      // remove operation fails?
      final ContainerInfo info = containerMap.remove(id);
      lifeCycleStateMap.remove(info.getState(), id);
      ownerMap.remove(info.getOwner(), id);
      repConfigMap.remove(info.getReplicationConfig(), id);
      typeMap.remove(info.getReplicationType(), id);
      replicaMap.remove(id);
      LOG.trace("Container {} removed from ContainerStateMap.", id);
    }
  }

  /**
   * Returns the latest state of Container from SCM's Container State Map.
   *
   * @param containerID - ContainerID
   * @return container info, if found else null.
   */
  public ContainerInfo getContainerInfo(final ContainerID containerID) {
    return containerMap.get(containerID);
  }

  /**
   * Returns the latest list of DataNodes where replica for given containerId
   * exist.
   */
  public Set<ContainerReplica> getContainerReplicas(
      final ContainerID containerID) {
    Preconditions.checkNotNull(containerID);
    return replicaMap.get(containerID);
  }

  /**
   * Adds given datanodes as nodes where replica for given containerId exist.
   * Logs a debug entry if a datanode is already added as replica for given
   * ContainerId.
   */
  public void updateContainerReplica(final ContainerID containerID,
      final ContainerReplica replica) {
    Preconditions.checkNotNull(containerID);
    if (contains(containerID)) {
      final Set<ContainerReplica> newSet = createNewReplicaSet(containerID);
      newSet.remove(replica);
      newSet.add(replica);
      replaceReplicaSet(containerID, newSet);
    }
  }

  /**
   * Remove a container Replica for given DataNode.
   */
  public void removeContainerReplica(final ContainerID containerID,
      final ContainerReplica replica) {
    Preconditions.checkNotNull(containerID);
    Preconditions.checkNotNull(replica);
    if (contains(containerID)) {
      final Set<ContainerReplica> newSet = createNewReplicaSet(containerID);
      newSet.remove(replica);
      replaceReplicaSet(containerID, newSet);
    }
  }

  private Set<ContainerReplica> createNewReplicaSet(ContainerID containerID) {
    Set<ContainerReplica> existingSet = replicaMap.get(containerID);
    return existingSet == null ? new HashSet<>() : new HashSet<>(existingSet);
  }

  private void replaceReplicaSet(ContainerID containerID,
      Set<ContainerReplica> newSet) {
    replicaMap.put(containerID, Collections.unmodifiableSet(newSet));
  }

  /**
   * Update the State of a container.
   *
   * @param containerID - ContainerID
   * @param currentState - CurrentState
   * @param newState - NewState.
   * @throws SCMException - in case of failure.
   */
  public void updateState(ContainerID containerID, LifeCycleState currentState,
      LifeCycleState newState) throws SCMException {
    Preconditions.checkNotNull(currentState);
    Preconditions.checkNotNull(newState);
    if (!contains(containerID)) {
      return;
    }
    // Return if updating state not changed
    if (currentState == newState) {
      LOG.debug("CurrentState and NewState are the same, return from " +
          "updateState directly.");
      return;
    }
    // TODO: Simplify this logic.
    final ContainerInfo currentInfo = containerMap.get(containerID);
    try {
      currentInfo.setState(newState);

      // We are updating two places before this update is done, these can
      // fail independently, since the code needs to handle it.

      // We update the attribute map, if that fails it will throw an
      // exception, so no issues, if we are successful, we keep track of the
      // fact that we have updated the lifecycle state in the map, and update
      // the container state. If this second update fails, we will attempt to
      // roll back the earlier change we did. If the rollback fails, we can
      // be in an inconsistent state,

      lifeCycleStateMap.update(currentState, newState, containerID);
      if (LOG.isTraceEnabled()) {
        LOG.trace("Updated the container {} to new state. Old = {}, new = " +
            "{}", containerID, currentState, newState);
      }
    } catch (SCMException ex) {
      LOG.error("Unable to update the container state.", ex);
      // we need to revert the change in this attribute since we are not
      // able to update the hash table.
      LOG.info("Reverting the update to lifecycle state. Moving back to " +
              "old state. Old = {}, Attempted state = {}", currentState,
          newState);

      currentInfo.revertState();

      // if this line throws, the state map can be in an inconsistent
      // state, since we will have modified the attribute by the
      // container state will not in sync since we were not able to put
      // that into the hash table.
      lifeCycleStateMap.update(newState, currentState, containerID);

      throw new SCMException("Updating the container map failed.", ex,
          FAILED_TO_CHANGE_CONTAINER_STATE);
    }
  }

  public Set<ContainerID> getAllContainerIDs() {
    return ImmutableSet.copyOf(containerMap.keySet());
  }

  /**
   * Returns Containers in the System by the Type.
   *
   * @param type - Replication type -- StandAlone, Ratis etc.
   * @return NavigableSet
   */
  public NavigableSet<ContainerID> getContainerIDsByType(final ReplicationType type) {
    Preconditions.checkNotNull(type);
    return typeMap.getCollection(type);
  }

  /**
   * Returns Containers by State.
   *
   * @param state - State - Open, Closed etc.
   * @return List of containers by state.
   */
  public NavigableSet<ContainerID> getContainerIDsByState(
      final LifeCycleState state) {
    Preconditions.checkNotNull(state);
    return lifeCycleStateMap.getCollection(state);
  }
}
