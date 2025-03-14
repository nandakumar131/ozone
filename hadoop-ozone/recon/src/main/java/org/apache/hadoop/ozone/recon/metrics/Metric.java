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

package org.apache.hadoop.ozone.recon.metrics;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.hadoop.hdds.annotation.InterfaceAudience;

/**
 * Class for wrapping a metric response from
 * {@link org.apache.hadoop.ozone.recon.spi.MetricsServiceProvider}.
 */
@InterfaceAudience.Private
public final class Metric {

  private final Map<String, String> metadata;
  private final TreeMap<Double, Double> values;

  public Metric(Map<String, String> metadata,
                SortedMap<Double, Double> values) {
    this.metadata = metadata;
    this.values = new TreeMap<>();
    this.values.putAll(values);
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public SortedMap<Double, Double> getValues() {
    return values;
  }
}
