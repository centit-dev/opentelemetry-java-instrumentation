/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.logback.logging.v1_0_0;

import application.ch.qos.logback.classic.spi.ILoggingEvent;
import application.org.slf4j.Marker;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.logs.LogRecordBuilder;
import io.opentelemetry.logs.Severity;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LoggingEventMapper {

  private static final String LOG_MARKER = "logback.marker";

  private final boolean captureExperimentalAttributes;
  private final List<String> captureMdcAttributes;
  private final boolean captureAllMdcAttributes;
  private final boolean captureCodeAttributes;
  private final boolean captureMarkerAttribute;
  private final boolean captureLoggerContext;

  public LoggingEventMapper(
      boolean captureExperimentalAttributes,
      List<String> captureMdcAttributes,
      boolean captureCodeAttributes,
      boolean captureMarkerAttribute,
      boolean captureLoggerContext) {
    this.captureExperimentalAttributes = captureExperimentalAttributes;
    this.captureCodeAttributes = captureCodeAttributes;
    this.captureMdcAttributes = captureMdcAttributes;
    this.captureMarkerAttribute = captureMarkerAttribute;
    this.captureLoggerContext = captureLoggerContext;
    this.captureAllMdcAttributes =
        captureMdcAttributes.size() == 1 && captureMdcAttributes.get(0).equals("*");
  }

  public void emit(ILoggingEvent event, long threadId) {
    String instrumentationName = event.getLoggerName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }

    LogRecordBuilder builder = OpenTelemetry
        .getLogRecordBuilder(instrumentationName)
        .logRecordBuilder();
    mapLoggingEvent(builder, event, threadId);
    builder.emit();
  }

  private void mapLoggingEvent(
      LogRecordBuilder builder, ILoggingEvent loggingEvent, long threadId) {
    // message
    String message = loggingEvent.getFormattedMessage();
    if (message != null) {
      builder.setBody(message);
    }

    // time
    long timestamp = loggingEvent.getTimeStamp();
    builder.setTimestamp(timestamp, TimeUnit.MILLISECONDS);

    // level
    Level level = loggingEvent.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.levelStr);
    }

    Attributes.Builder attributes = Attributes.newBuilder();

    // throwable
    Object throwableProxy = loggingEvent.getThrowableProxy();
    Throwable throwable = null;
    if (throwableProxy instanceof ThrowableProxy) {
      // there is only one other subclass of ch.qos.logback.classic.spi.IThrowableProxy
      // and it is only used for logging exceptions over the wire
      throwable = ((ThrowableProxy) throwableProxy).getThrowable();
    }
    if (throwable != null) {
      setThrowable(attributes, throwable);
    }

    captureMdcAttributes(attributes, loggingEvent.getMDCPropertyMap());

    if (captureExperimentalAttributes) {
      attributes.setAttribute("thread.name", loggingEvent.getThreadName());
      if (threadId != -1) {
        attributes.setAttribute("thread.id", threadId);
      }
    }

    if (captureCodeAttributes) {
      StackTraceElement[] callerData = loggingEvent.getCallerData();
      if (callerData != null && callerData.length > 0) {
        StackTraceElement firstStackElement = callerData[0];
        String fileName = firstStackElement.getFileName();
        if (fileName != null) {
          attributes.setAttribute("code.filepath", fileName);
        }
        attributes.setAttribute("code.namespace", firstStackElement.getClassName());
        attributes.setAttribute("code.function", firstStackElement.getMethodName());
        int lineNumber = firstStackElement.getLineNumber();
        if (lineNumber > 0) {
          attributes.setAttribute("code.lineno", lineNumber);
        }
      }
    }

    if (captureMarkerAttribute) {
      captureMarkerAttribute(attributes, loggingEvent);
    }

    if (captureLoggerContext) {
      captureLoggerContext(attributes, loggingEvent.getLoggerContextVO().getPropertyMap());
    }

    builder.setAllAttributes(attributes.build());

    // span context
    builder.setContext(Context.current());
  }

  // visible for testing
  void captureMdcAttributes(Attributes.Builder attributes, Map<String, String> mdcProperties) {
    if (captureAllMdcAttributes) {
      for (Map.Entry<String, String> entry : mdcProperties.entrySet()) {
        attributes.setAttribute(getMdcAttributeKey(entry.getKey()), entry.getValue());
      }
      return;
    }

    for (String key : captureMdcAttributes) {
      String value = mdcProperties.get(key);
      if (value != null) {
        attributes.setAttribute(getMdcAttributeKey(key), value);
      }
    }
  }

  public static String getMdcAttributeKey(String key) {
    return "logback.mdc." + key;
  }

  private static void setThrowable(Attributes.Builder attributes, Throwable throwable) {
    // TODO (trask) extract method for recording exception into
    // io.opentelemetry:opentelemetry-api
    attributes.setAttribute("exception.type", throwable.getClass().getName());
    attributes.setAttribute("exception.message", throwable.getMessage());
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    attributes.setAttribute("exception.stacktrace", writer.toString());
  }

  private static Severity levelToSeverity(Level level) {
    switch (level.levelInt) {
      case Level.ALL_INT:
      case Level.TRACE_INT:
        return Severity.TRACE;
      case Level.DEBUG_INT:
        return Severity.DEBUG;
      case Level.INFO_INT:
        return Severity.INFO;
      case Level.WARN_INT:
        return Severity.WARN;
      case Level.ERROR_INT:
        return Severity.ERROR;
      case Level.OFF_INT:
      default:
        return Severity.UNDEFINED_SEVERITY_NUMBER;
    }
  }

  private static void captureLoggerContext(
      Attributes.Builder attributes, Map<String, String> loggerContextProperties) {
    for (Map.Entry<String, String> entry : loggerContextProperties.entrySet()) {
      attributes.setAttribute(getAttributeKey(entry.getKey()), entry.getValue());
    }
  }

  public static String getAttributeKey(String key) {
    return key;
  }

  private static void captureMarkerAttribute(
      Attributes.Builder attributes, ILoggingEvent loggingEvent) {
    captureSingleMarkerAttribute(attributes, loggingEvent);
  }

  private static void captureSingleMarkerAttribute(
      Attributes.Builder attributes, ILoggingEvent loggingEvent) {
    Marker marker = loggingEvent.getMarker();
    if (marker != null) {
      attributes.setAttribute(LOG_MARKER, marker.getName());
    }
  }
}
