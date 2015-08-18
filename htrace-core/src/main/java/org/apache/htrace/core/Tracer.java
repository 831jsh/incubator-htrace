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
package org.apache.htrace.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A Tracer provides the implementation for collecting and distributing Spans
 * within a process.
 */
public class Tracer {
  private static final Log LOG = LogFactory.getLog(Tracer.class);

  static long nonZeroRandom64() {
    long id;
    Random random = ThreadLocalRandom.current();
    do {
      id = random.nextLong();
    } while (id == 0);
    return id;
  }

  private final List<SpanReceiver> receivers = new CopyOnWriteArrayList<SpanReceiver>();
  private static final ThreadLocal<Span> currentSpan = new ThreadLocal<Span>() {
    @Override
    protected Span initialValue() {
      return null;
    }
  };
  private static final SpanId EMPTY_PARENT_ARRAY[] = new SpanId[0];

  /**
   * Log a client error, and throw an exception.
   *
   * @param str     The message to use in the log and the exception.
   */
  static void clientError(String str) {
    LOG.error(str);
    throw new RuntimeException(str);
  }

  /**
   * Internal class for defered singleton idiom.
   * <p/>
   * https://en.wikipedia.org/wiki/Initialization_on_demand_holder_idiom
   */
  private static class TracerHolder {
    private static final Tracer INSTANCE = new Tracer();
  }

  public static Tracer getInstance() {
    return TracerHolder.INSTANCE;
  }

  protected Span createNew(String description) {
    Span parent = currentSpan.get();
    if (parent == null) {
      return new MilliSpan.Builder().
          begin(System.currentTimeMillis()).
          end(0).
          description(description).
          parents(EMPTY_PARENT_ARRAY).
          spanId(SpanId.fromRandom()).
          build();
    } else {
      return parent.child(description);
    }
  }

  protected boolean isTracing() {
    return currentSpan.get() != null;
  }

  protected Span currentSpan() {
    return currentSpan.get();
  }

  public void deliver(Span span) {
    for (SpanReceiver receiver : receivers) {
      receiver.receiveSpan(span);
    }
  }

  protected void addReceiver(SpanReceiver receiver) {
    receivers.add(receiver);
  }

  protected void removeReceiver(SpanReceiver receiver) {
    receivers.remove(receiver);
  }

  protected Span setCurrentSpan(Span span) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("setting current span " + span);
    }
    currentSpan.set(span);
    return span;
  }

  public TraceScope continueSpan(Span s) {
    Span oldCurrent = currentSpan();
    setCurrentSpan(s);
    return new TraceScope(s, oldCurrent);
  }

  protected int numReceivers() {
    return receivers.size();
  }
}
