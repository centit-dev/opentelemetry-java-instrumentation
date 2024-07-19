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

package io.opentelemetry.javaagent.exporters.otlp;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporters.otlpnew.OtlpGrpcSpanExporter;
import io.opentelemetry.javaagent.tooling.exporter.ExporterConfig;
import io.opentelemetry.javaagent.tooling.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;

@AutoService(SpanExporterFactory.class)
public class OtlpSpanExporterFactory implements SpanExporterFactory {

  @Override
  public SpanExporter fromConfig(ExporterConfig config) {
    return OtlpGrpcSpanExporter.newBuilder()
        .readEnvironmentVariables()
        .readSystemProperties()
        .build();
  }
}
