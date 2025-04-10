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

package org.apache.hadoop.ozone.container.common.states.datanode;

import static org.apache.hadoop.hdds.HddsUtils.getReconAddresses;
import static org.apache.hadoop.hdds.HddsUtils.getSCMAddressForDatanodes;

import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.utils.HddsServerUtil;
import org.apache.hadoop.ozone.container.common.helpers.ContainerUtils;
import org.apache.hadoop.ozone.container.common.statemachine.DatanodeStateMachine;
import org.apache.hadoop.ozone.container.common.statemachine.SCMConnectionManager;
import org.apache.hadoop.ozone.container.common.statemachine.StateContext;
import org.apache.hadoop.ozone.container.common.states.DatanodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Init Datanode State is the task that gets run when we are in Init State.
 */
public class InitDatanodeState implements DatanodeState,
    Callable<DatanodeStateMachine.DatanodeStates> {
  static final Logger LOG = LoggerFactory.getLogger(InitDatanodeState.class);
  private final SCMConnectionManager connectionManager;
  private final ConfigurationSource conf;
  private final StateContext context;
  private Future<DatanodeStateMachine.DatanodeStates> result;

  /**
   *  Create InitDatanodeState Task.
   *
   * @param conf - Conf
   * @param connectionManager - Connection Manager
   * @param context - Current Context
   */
  public InitDatanodeState(ConfigurationSource conf,
                           SCMConnectionManager connectionManager,
                           StateContext context) {
    this.conf = conf;
    this.connectionManager = connectionManager;
    this.context = context;
  }

  /**
   * Computes a result, or throws an exception if unable to do so.
   *
   * @return computed result
   * @throws Exception if unable to compute a result
   */
  @Override
  public DatanodeStateMachine.DatanodeStates call() throws Exception {
    Collection<InetSocketAddress> addresses = null;
    try {
      addresses = getSCMAddressForDatanodes(conf);
    } catch (IllegalArgumentException e) {
      if (!Strings.isNullOrEmpty(e.getMessage())) {
        LOG.error("Failed to get SCM addresses: {}", e.getMessage());
      }
      return DatanodeStateMachine.DatanodeStates.SHUTDOWN;
    }

    if (addresses.isEmpty()) {
      LOG.error("Null or empty SCM address list found.");
      return DatanodeStateMachine.DatanodeStates.SHUTDOWN;
    } else {
      for (InetSocketAddress addr : addresses) {
        if (addr.isUnresolved()) {
          LOG.warn("One SCM address ({}) can't (yet?) be resolved. Postpone "
              + "initialization.", addr);

          //skip any further initialization. DatanodeStateMachine will try it
          // again after the hb frequency
          return this.context.getState();
        }
      }
      for (InetSocketAddress addr : addresses) {
        connectionManager.addSCMServer(addr, context.getThreadNamePrefix());
        this.context.addEndpoint(addr);
      }
      InetSocketAddress reconAddress = getReconAddresses(conf);
      if (reconAddress != null) {
        connectionManager.addReconServer(reconAddress,
            context.getThreadNamePrefix());
        this.context.addEndpoint(reconAddress);
      }
    }

    // If datanode ID is set, persist it to the ID file.
    persistContainerDatanodeDetails();

    return this.context.getState().getNextState();
  }

  /**
   * Persist DatanodeDetails to datanode.id file.
   */
  private void persistContainerDatanodeDetails() {
    String dataNodeIDPath = HddsServerUtil.getDatanodeIdFilePath(conf);
    File idPath = new File(dataNodeIDPath);
    DatanodeDetails datanodeDetails = this.context.getParent()
        .getDatanodeDetails();
    if (datanodeDetails != null) {
      try {
        ContainerUtils.writeDatanodeDetailsTo(datanodeDetails, idPath, conf);
      } catch (IOException ex) {
        // As writing DatanodeDetails in to datanodeid file failed, which is
        // a critical thing, so shutting down the state machine.
        LOG.error("Writing to {} failed {}", dataNodeIDPath, ex.getMessage());
        this.context.setState(DatanodeStateMachine.DatanodeStates.SHUTDOWN);
        return;
      }
      LOG.info("DatanodeDetails is persisted to {}", dataNodeIDPath);
    }
  }

  /**
   * Called before entering this state.
   */
  @Override
  public void onEnter() {
    LOG.trace("Entering init container state");
  }

  /**
   * Called After exiting this state.
   */
  @Override
  public void onExit() {
    LOG.trace("Exiting init container state");
  }

  /**
   * Executes one or more tasks that is needed by this state.
   *
   * @param executor -  ExecutorService
   */
  @Override
  public void execute(ExecutorService executor) {
    result = executor.submit(this);
  }

  /**
   * Wait for execute to finish.
   *
   * @param time     - Time
   * @param timeUnit - Unit of time.
   */
  @Override
  public DatanodeStateMachine.DatanodeStates await(long time,
      TimeUnit timeUnit) throws InterruptedException,
      ExecutionException, TimeoutException {
    return result.get(time, timeUnit);
  }
}
