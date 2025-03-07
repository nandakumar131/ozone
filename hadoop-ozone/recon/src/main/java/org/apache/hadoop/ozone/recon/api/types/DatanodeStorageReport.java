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

package org.apache.hadoop.ozone.recon.api.types;

/**
 * Metadata object that contains storage report of a Datanode.
 */
public class DatanodeStorageReport {
  private long capacity;
  private long used;
  private long remaining;
  private long committed;

  public DatanodeStorageReport(long capacity, long used, long remaining,
                               long committed) {
    this.capacity = capacity;
    this.used = used;
    this.remaining = remaining;
    this.committed = committed;
  }

  public long getCapacity() {
    return capacity;
  }

  public long getUsed() {
    return used;
  }

  public long getRemaining() {
    return remaining;
  }

  public long getCommitted() {
    return committed;
  }
}
