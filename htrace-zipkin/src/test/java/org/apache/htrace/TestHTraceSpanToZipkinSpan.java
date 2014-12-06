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

import com.twitter.zipkin.gen.zipkinCoreConstants;

import org.apache.htrace.Span;
import org.apache.htrace.Trace;
import org.apache.htrace.impl.MilliSpan;
import org.apache.htrace.impl.POJOSpanReceiver;
import org.apache.htrace.zipkin.HTraceToZipkinConverter;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Creates HTrace and then convert it to Zipkin trace and checks whether it is a valid span or not.
 */
public class TestHTraceSpanToZipkinSpan {

  private static final String ROOT_SPAN_DESC = "ROOT";

  @Test
  public void testHTraceToZipkin() throws IOException {
    POJOSpanReceiver psr = new POJOSpanReceiver();
    Trace.addReceiver(psr);

    Span rootSpan = new MilliSpan(ROOT_SPAN_DESC, 1, Span.ROOT_SPAN_ID, 100, "test");
    Span innerOne = rootSpan.child("Some good work");
    Span innerTwo = innerOne.child("Some more good work");
    innerTwo.stop();
    innerOne.stop();
    rootSpan.addKVAnnotation("foo".getBytes(), "bar".getBytes());
    rootSpan.addTimelineAnnotation("timeline");
    rootSpan.stop();

    for (Span s : new Span[] {rootSpan, innerOne, innerTwo}) {
      com.twitter.zipkin.gen.Span zs =
          new HTraceToZipkinConverter(12345, (short) 12).convert(s);
      assertSpansAreEquivalent(s, zs);
    }
  }

  @Test
  public void testHTraceAnnotationTimestamp() throws IOException, InterruptedException {

    String traceName = "testHTraceAnnotationTimestamp";
    long startTime = System.currentTimeMillis() * 1000;
    Span ms = new MilliSpan(traceName, 1, Span.ROOT_SPAN_ID, 2, traceName);

    Thread.sleep(500);
    long annoStartTime = System.currentTimeMillis() * 1000;
    Thread.sleep(500);
    ms.addTimelineAnnotation("anno");
    Thread.sleep(500);
    long annoEndTime = System.currentTimeMillis() * 1000;
    Thread.sleep(500);
    ms.stop();
    long endTime = System.currentTimeMillis() * 1000;



    com.twitter.zipkin.gen.Span zs = new HTraceToZipkinConverter(12345, (short) -1).convert(ms);

    // Check to make sure that all times are in the proper order.
    for (com.twitter.zipkin.gen.Annotation annotation : zs.getAnnotations()) {
      // CS and SR should be before the annotation
      // the annotation should be in between annotationStart and annotationEnd times
      // SS and CR should be after annotationEnd and before endtime.
      if (annotation.getValue().equals(zipkinCoreConstants.CLIENT_SEND)
          || annotation.getValue().equals(zipkinCoreConstants.SERVER_RECV)) {
        assertTrue(startTime <= annotation.getTimestamp());
        assertTrue(annotation.getTimestamp() <= annoStartTime);
      } else if (annotation.getValue().equals(zipkinCoreConstants.CLIENT_RECV)
          || annotation.getValue().equals(zipkinCoreConstants.SERVER_SEND)) {
        assertTrue(annoEndTime <= annotation.getTimestamp());
        assertTrue(annotation.getTimestamp() <= endTime);
      } else {
        assertTrue(annoStartTime <= annotation.getTimestamp());
        assertTrue(annotation.getTimestamp() <= annoEndTime);
        assertTrue(annotation.getTimestamp() <= endTime);
      }
    }
  }

  @Test
  public void testHTraceDefaultPort() throws IOException {
    MilliSpan ms = new MilliSpan("test", 1, 2, 3, "hmaster");
    com.twitter.zipkin.gen.Span zs = new HTraceToZipkinConverter(12345, (short) -1).convert(ms);
    for (com.twitter.zipkin.gen.Annotation annotation:zs.getAnnotations()) {
      assertEquals((short)60000, annotation.getHost().getPort());
    }

    ms = new MilliSpan("test", 1, 2, 3, "HregIonServer");   // make sure it's all lower cased
    zs = new HTraceToZipkinConverter(12345, (short) -1).convert(ms);
    for (com.twitter.zipkin.gen.Annotation annotation:zs.getAnnotations()) {
      assertEquals((short)60020, annotation.getHost().getPort());
    }
  }

  private void assertSpansAreEquivalent(Span s, com.twitter.zipkin.gen.Span zs) {
    assertEquals(s.getTraceId(), zs.getTrace_id());
    if (s.getParentId() != Span.ROOT_SPAN_ID) {
      assertEquals(s.getParentId(), zs.getParent_id());
    }
    assertEquals(s.getSpanId(), zs.getId());
    Assert.assertNotNull(zs.getAnnotations());
    if (ROOT_SPAN_DESC.equals(zs.getName())) {
      assertEquals(5, zs.getAnnotations().size());// two start, two stop + one timeline annotation
      assertEquals(1, zs.getBinary_annotations().size());
    } else {
      assertEquals(4, zs.getAnnotations().size());
    }
  }
}
