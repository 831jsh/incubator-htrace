/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.htrace.impl;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.hbase.htrace.Sampler;

@SuppressWarnings("rawtypes")
@InterfaceAudience.Public
@InterfaceStability.Evolving
public final class NeverSampler implements Sampler {

  private static NeverSampler instance;

  // No need to ever have more than one of these created.
  public static NeverSampler getInstance() {
    if (instance == null) {
      instance = new NeverSampler();
    }
    return instance;
  }

  private NeverSampler() {
  }

  @Override
  public boolean next(Object info) {
    return false;
  }

}
