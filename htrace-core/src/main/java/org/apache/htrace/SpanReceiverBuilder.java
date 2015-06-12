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
package org.apache.htrace;

import java.lang.reflect.Constructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A {@link SpanReceiver} builder. It reads a {@link SpanReceiver} class name from the provided
 * configuration using the {@link #SPAN_RECEIVER_CONF_KEY} key. Unqualified class names
 * are interpreted as members of the {@code org.apache.htrace.impl} package. The {@link #build()}
 * method constructs an instance of that class, initialized with the same configuration.
 */
public class SpanReceiverBuilder {
  private static final Log LOG = LogFactory.getLog(SpanReceiverBuilder.class);

  public final static String SPAN_RECEIVER_CONF_KEY = "span.receiver";
  private final static String DEFAULT_PACKAGE = "org.apache.htrace.impl";
  private final static ClassLoader classLoader =
      SpanReceiverBuilder.class.getClassLoader();
  private final HTraceConfiguration conf;
  private boolean logErrors;
  private String spanReceiverClass;

  public SpanReceiverBuilder(HTraceConfiguration conf) {
    this.conf = conf;
    reset();
  }

  /**
   * Set this builder back to defaults. Any previous calls to {@link #spanReceiverClass(String)}
   * are overridden by the value provided by configuration.
   * @return This instance
   */
  public SpanReceiverBuilder reset() {
    this.logErrors = true;
    this.spanReceiverClass = this.conf.get(SPAN_RECEIVER_CONF_KEY);
    return this;
  }

  /**
   * Override the {@code SpanReceiver} class name provided in configuration with a new value.
   * @return This instance
   */
  public SpanReceiverBuilder spanReceiverClass(final String spanReceiverClass) {
    this.spanReceiverClass = spanReceiverClass;
    return this;
  }

  /**
   * Configure whether we should log errors during build().
   * @return This instance
   */
  public SpanReceiverBuilder logErrors(boolean logErrors) {
    this.logErrors = logErrors;
    return this;
  }

  private void logError(String errorStr) {
    if (!logErrors) {
      return;
    }
    LOG.error(errorStr);
  }

  private void logError(String errorStr, Throwable e) {
    if (!logErrors) {
      return;
    }
    LOG.error(errorStr, e);
  }

  public SpanReceiver build() {
    SpanReceiver spanReceiver = newSpanReceiver();
    if (LOG.isTraceEnabled()) {
      LOG.trace("Created new span receiver of type " +
             ((spanReceiver == null) ? "(none)" :
               spanReceiver.getClass().getName()));
    }
    return spanReceiver;
  }

  private SpanReceiver newSpanReceiver() {
    if ((this.spanReceiverClass == null) ||
        this.spanReceiverClass.isEmpty()) {
      LOG.debug("No span receiver class specified.");
      return null;
    }
    String str = spanReceiverClass;
    if (!str.contains(".")) {
      str = DEFAULT_PACKAGE + "." + str;
    }
    Class cls = null;
    try {
      cls = classLoader.loadClass(str);
    } catch (ClassNotFoundException e) {
      logError("SpanReceiverBuilder cannot find SpanReceiver class " + str +
          ": disabling span receiver.");
      return null;
    }
    Constructor<SpanReceiver> ctor = null;
    try {
      ctor = cls.getConstructor(HTraceConfiguration.class);
    } catch (NoSuchMethodException e) {
      logError("SpanReceiverBuilder cannot find a constructor for class " +
          str + "which takes an HTraceConfiguration.  Disabling span " +
          "receiver.");
      return null;
    }
    try {
      LOG.debug("Creating new instance of " + str + "...");
      return ctor.newInstance(conf);
    } catch (ReflectiveOperationException e) {
      logError("SpanReceiverBuilder reflection error when constructing " + str +
          ".  Disabling span receiver.", e);
      return null;
    } catch (Throwable e) {
      logError("SpanReceiverBuilder constructor error when constructing " + str +
          ".  Disabling span receiver.", e);
      return null;
    }
  }
}
