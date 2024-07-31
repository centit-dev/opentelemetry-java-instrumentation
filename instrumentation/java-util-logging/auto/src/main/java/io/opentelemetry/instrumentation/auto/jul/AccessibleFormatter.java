package io.opentelemetry.instrumentation.auto.jul;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

// this is just needed for calling formatMessage in abstract super class
class AccessibleFormatter extends Formatter {

  @Override
  public String format(LogRecord record) {
    throw new UnsupportedOperationException();
  }
}
