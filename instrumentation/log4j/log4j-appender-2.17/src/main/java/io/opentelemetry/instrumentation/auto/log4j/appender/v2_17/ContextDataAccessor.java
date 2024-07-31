package io.opentelemetry.instrumentation.auto.log4j.appender.v2_17;

public interface ContextDataAccessor<T> {
  Object getValue(T contextData, String key);

  void forEach(T contextData, BiConsumer<String, Object> action);
}
