/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jul;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.logs.LogRecordBuilder;
import io.opentelemetry.logs.Severity;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import application.java.util.logging.Logger;

public final class JavaUtilLoggingHelper {

  private static final Formatter FORMATTER = new AccessibleFormatter();

  private static final boolean captureExperimentalAttributes =
      Config.getBooleanSettingFromEnvironment("otel.instrumentation.java-util-logging.experimental-log-attributes", false);

  public static void capture(Logger logger, LogRecord logRecord) {
    if (!logger.isLoggable(logRecord.getLevel())) {
      // this is already checked in most cases, except if Logger.log(LogRecord) was called directly
      return;
    }

    String instrumentationName = logger.getName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }
    LogRecordBuilder builder = OpenTelemetry
        .getLogRecordBuilder(instrumentationName)
        .logRecordBuilder();
    mapLogRecord(builder, logRecord);
    builder.emit();
  }

  /**
   * Map the {@link LogRecord} data model onto the {@link LogRecordBuilder}. Unmapped fields
   * include:
   *
   * <ul>
   *   <li>Fully qualified class name - {@link LogRecord#getSourceClassName()}
   *   <li>Fully qualified method name - {@link LogRecord#getSourceMethodName()}
   *   <li>Thread id - {@link LogRecord#getThreadID()}
   * </ul>
   */
  private static void mapLogRecord(LogRecordBuilder builder, LogRecord logRecord) {
    // message
    String message = FORMATTER.formatMessage(logRecord);
    if (message != null) {
      builder.setBody(message);
    }

    // time
    // TODO (trask) use getInstant() for more precision on Java 9
    long timestamp = logRecord.getMillis();
    builder.setTimestamp(timestamp, TimeUnit.MILLISECONDS);

    // level
    Level level = logRecord.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(logRecord.getLevel().getName());
    }

    Attributes.Builder attributes = Attributes.newBuilder();

    // throwable
    Throwable throwable = logRecord.getThrown();
    if (throwable != null) {
      // TODO (trask) extract method for recording exception into
      // io.opentelemetry:opentelemetry-api
      attributes.setAttribute("exception.type", throwable.getClass().getName());
      attributes.setAttribute("exception.message", throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      attributes.setAttribute("exception.stacktrace", writer.toString());
    }

    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      attributes.setAttribute("thread.name", currentThread.getName());
      attributes.setAttribute("thread.id", currentThread.getId());
    }

    builder.setAllAttributes(attributes.build());

    // span context
    builder.setContext(Context.current());
  }

  private static Severity levelToSeverity(Level level) {
    int lev = level.intValue();
    if (lev <= Level.FINEST.intValue()) {
      return Severity.TRACE;
    }
    if (lev <= Level.FINER.intValue()) {
      return Severity.DEBUG;
    }
    if (lev <= Level.FINE.intValue()) {
      return Severity.DEBUG2;
    }
    if (lev <= Level.CONFIG.intValue()) {
      return Severity.DEBUG3;
    }
    if (lev <= Level.INFO.intValue()) {
      return Severity.INFO;
    }
    if (lev <= Level.WARNING.intValue()) {
      return Severity.WARN;
    }
    if (lev <= Level.SEVERE.intValue()) {
      return Severity.ERROR;
    }
    return Severity.FATAL;
  }
}
