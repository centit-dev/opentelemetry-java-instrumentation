package io.opentelemetry.instrumentation.auto.log4j.appender.v2_17;

public interface BiConsumer<T, U> {
  void accept(T t, U u);
}
