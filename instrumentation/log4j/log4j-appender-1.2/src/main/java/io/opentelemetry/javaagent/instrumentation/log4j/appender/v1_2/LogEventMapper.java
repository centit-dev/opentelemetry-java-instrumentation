/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v1_2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.logs.LogRecordBuilder;
import io.opentelemetry.logs.Severity;
import io.opentelemetry.trace.attributes.SemanticAttributes;

import org.apache.log4j.Category;
import org.apache.log4j.MDC;
import org.apache.log4j.Priority;

public final class LogEventMapper {

  private static final Map<String, String> mdcAttributeKeys = new HashMap<>();

  public static final LogEventMapper INSTANCE = new LogEventMapper();

  // copied from org.apache.log4j.Level because it was only introduced in 1.2.12
  private static final int TRACE_INT = 5000;

  private static final boolean captureExperimentalAttributes =
      Config.getBooleanSettingFromEnvironment("otel.instrumentation.log4j-appender.experimental-log-attributes", false);

  private final Map<String, String> captureMdcAttributes = new HashMap<>();

  // cached as an optimization
  private final boolean captureAllMdcAttributes;

  private LogEventMapper() {
    List<String> captureMdcAttributes =
        Config.getListSettingFromEnvironment(
                "otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes",
                "");
    for (String attr : captureMdcAttributes) {
      this.captureMdcAttributes.put(attr, getMdcAttributeKey(attr));
    }
    this.captureAllMdcAttributes =
        captureMdcAttributes.size() == 1 && captureMdcAttributes.get(0).equals("*");
  }

  public void capture(Category logger, Priority level, Object message, Throwable throwable) {
    String instrumentationName = logger.getName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }

    LogRecordBuilder builder = OpenTelemetry
        .getLogRecordBuilder(instrumentationName)
        .logRecordBuilder();

    // message
    if (message != null) {
      builder.setBody(String.valueOf(message));
    }

    // level
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.toString());
    }

    Attributes.Builder attributes = Attributes.newBuilder();

    // throwable
    if (throwable != null) {
      // TODO (trask) extract method for recording exception into
      // io.opentelemetry:opentelemetry-api
      attributes.setAttribute(SemanticAttributes.EXCEPTION_TYPE.key(), throwable.getClass().getName());
      attributes.setAttribute(SemanticAttributes.EXCEPTION_MESSAGE.key(), throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      attributes.setAttribute(SemanticAttributes.EXCEPTION_STACKTRACE.key(), writer.toString());
    }

    captureMdcAttributes(attributes);

    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      attributes.setAttribute(SemanticAttributes.THREAD_NAME.key(), currentThread.getName());
      attributes.setAttribute(SemanticAttributes.THREAD_ID.key(), currentThread.getId());
    }

    builder.setAllAttributes(attributes.build());

    // span context
    builder.setContext(Context.current());

    builder.setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    builder.emit();
  }

  private void captureMdcAttributes(Attributes.Builder attributes) {

    Hashtable<?, ?> context = MDC.getContext();

    if (captureAllMdcAttributes) {
      if (context != null) {
        for (Map.Entry<?, ?> entry : context.entrySet()) {
          attributes.setAttribute(
              getMdcAttributeKey(String.valueOf(entry.getKey())), String.valueOf(entry.getValue()));
        }
      }
      return;
    }

    for (Map.Entry<String, String> entry : captureMdcAttributes.entrySet()) {
      Object value = context.get(entry.getKey());
      if (value != null) {
        attributes.setAttribute(entry.getValue(), value.toString());
      }
    }
  }

  private static String getMdcAttributeKey(String key) {
    if (!mdcAttributeKeys.containsKey(key)) {
      mdcAttributeKeys.put(key, "log4j.mdc." + key);
    }

    return mdcAttributeKeys.get(key);
  }

  private static Severity levelToSeverity(Priority level) {
    int lev = level.toInt();
    if (lev <= TRACE_INT) {
      return Severity.TRACE;
    }
    if (lev <= Priority.DEBUG_INT) {
      return Severity.DEBUG;
    }
    if (lev <= Priority.INFO_INT) {
      return Severity.INFO;
    }
    if (lev <= Priority.WARN_INT) {
      return Severity.WARN;
    }
    if (lev <= Priority.ERROR_INT) {
      return Severity.ERROR;
    }
    return Severity.FATAL;
  }
}
