package org.cloudera.htrace.impl;

import org.cloudera.htrace.Span;
import org.cloudera.htrace.SpanReceiver;

/**
 * Used for testing. Simply prints to standard out any spans it receives.
 */
public class StandardOutSpanReceiver implements SpanReceiver {

  @Override
  public void receiveSpan(Span span) {
    System.out.println(span);
  }
}
