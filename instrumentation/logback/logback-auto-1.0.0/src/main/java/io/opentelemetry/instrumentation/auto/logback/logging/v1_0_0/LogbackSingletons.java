/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.logback.logging.v1_0_0;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.List;

public final class LogbackSingletons {

  private static final LoggingEventMapper mapper;

  static {
    boolean captureExperimentalAttributes =
        Config.getBooleanSettingFromEnvironment(
            "otel.instrumentation.logback-appender.experimental-log-attributes", false);
    boolean captureCodeAttributes =
        Config.getBooleanSettingFromEnvironment(
            "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);
    boolean captureMarkerAttribute =
        Config.getBooleanSettingFromEnvironment(
            "otel.instrumentation.logback-appender.experimental.capture-marker-attribute", false);
    boolean captureKeyValuePairAttributes =
        Config.getBooleanSettingFromEnvironment(
            "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes",
            false);
    boolean captureLoggerContext =
        Config.getBooleanSettingFromEnvironment(
            "otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes",
            false);
    List<String> captureMdcAttributes =
        Config.getListSettingFromEnvironment(
            "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes",
            "");

    mapper =
        new LoggingEventMapper(
            captureExperimentalAttributes,
            captureMdcAttributes,
            captureCodeAttributes,
            captureMarkerAttribute,
            captureLoggerContext);
  }

  public static LoggingEventMapper mapper() {
    return mapper;
  }

  private LogbackSingletons() {}
}
