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
package org.htrace;

import com.google.common.collect.Multimap;
import org.htrace.impl.LocalFileSpanReceiver;
import org.htrace.impl.POJOSpanReceiver;
import org.htrace.impl.StandardOutSpanReceiver;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TestHTrace {

  public static final String SPAN_FILE_FLAG = "spanFile";

  /**
   * Basic system test of HTrace.
   *
   * @throws Exception
   */
  @Test
  public void testHtrace() throws Exception {
    final int numTraces = 3;
    String fileName = System.getProperty(SPAN_FILE_FLAG);

    Collection<SpanReceiver> rcvrs = new HashSet<SpanReceiver>();

    // writes spans to a file if one is provided to maven with
    // -DspanFile="FILENAME", otherwise writes to standard out.
    if (fileName != null) {
      File f = new File(fileName);
      File parent = f.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        throw new IllegalArgumentException("Couldn't create file: "
            + fileName);
      }
      HashMap<String, String> conf = new HashMap<String, String>();
      conf.put("local-file-span-receiver.path", fileName);
      LocalFileSpanReceiver receiver = new LocalFileSpanReceiver();
      receiver.configure(HTraceConfiguration.fromMap(conf));
      rcvrs.add(receiver);
    } else {
      rcvrs.add(new StandardOutSpanReceiver());
    }

    POJOSpanReceiver psr = new POJOSpanReceiver();
    rcvrs.add(psr);
    runTraceCreatorTraces(new TraceCreator(rcvrs));

    for (SpanReceiver receiver : rcvrs) {
      receiver.close();
    }

    Collection<Span> spans = psr.getSpans();
    TraceTree traceTree = new TraceTree(spans);
    Collection<Span> roots = traceTree.getRoots();
    Assert.assertEquals(numTraces, roots.size());

    Map<String, Span> descriptionToRootSpan = new HashMap<String, Span>();
    for (Span root : roots) {
      descriptionToRootSpan.put(root.getDescription(), root);
    }

    Assert.assertTrue(descriptionToRootSpan.keySet().contains(
        TraceCreator.RPC_TRACE_ROOT));
    Assert.assertTrue(descriptionToRootSpan.keySet().contains(
        TraceCreator.SIMPLE_TRACE_ROOT));
    Assert.assertTrue(descriptionToRootSpan.keySet().contains(
        TraceCreator.THREADED_TRACE_ROOT));

    Multimap<Long, Span> spansByParentId = traceTree.getSpansByParentIdMap();
    Span rpcTraceRoot = descriptionToRootSpan.get(TraceCreator.RPC_TRACE_ROOT);
    Assert.assertEquals(1, spansByParentId.get(rpcTraceRoot.getSpanId()).size());

    Span rpcTraceChild1 = spansByParentId.get(rpcTraceRoot.getSpanId())
        .iterator().next();
    Assert.assertEquals(1, spansByParentId.get(rpcTraceChild1.getSpanId()).size());

    Span rpcTraceChild2 = spansByParentId.get(rpcTraceChild1.getSpanId())
        .iterator().next();
    Assert.assertEquals(1, spansByParentId.get(rpcTraceChild2.getSpanId()).size());

    Span rpcTraceChild3 = spansByParentId.get(rpcTraceChild2.getSpanId())
        .iterator().next();
    Assert.assertEquals(0, spansByParentId.get(rpcTraceChild3.getSpanId()).size());
  }

  private void runTraceCreatorTraces(TraceCreator tc) {
    tc.createThreadedTrace();
    tc.createSimpleTrace();
    tc.createSampleRpcTrace();
  }
}
