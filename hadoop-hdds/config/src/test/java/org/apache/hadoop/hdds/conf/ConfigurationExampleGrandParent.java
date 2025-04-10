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

package org.apache.hadoop.hdds.conf;

/**
 * Example configuration to test inherited configuration injection.
 */
public class ConfigurationExampleGrandParent extends ReconfigurableConfig {

  @Config(key = "number", defaultValue = "2", description = "Example numeric "
      + "configuration", tags = ConfigTag.MANAGEMENT)
  private int number = 1;

  @Config(key = "grandpa.dyna", reconfigurable = true, defaultValue = "x",
      description = "Test inherited dynamic property", tags = {})
  private String grandpaDynamic;

  public int getNumber() {
    return number;
  }

  public void setNumber(int number) {
    this.number = number;
  }
}
