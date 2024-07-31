package io.opentelemetry.instrumentation.auto.log4j.appender.v2_17;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.grpc.Context;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.logs.LogRecordBuilder;
import io.opentelemetry.logs.Severity;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;

public class LogEventMapper<T> {

  // copied from ThreadIncubatingAttributes
  private static final String THREAD_ID = "thread.id";
  private static final String THREAD_NAME = "thread.name";
  private static final String LOG_MARKER = "log4j.marker";
  private static final String SPECIAL_MAP_MESSAGE_ATTRIBUTE = "message";

  private static final Cache<String, String> contextDataAttributeKeyCache =
      CacheBuilder.newBuilder().maximumSize(100).build();
  private static final Cache<String, String> mapMessageAttributeKeyCache =
      CacheBuilder.newBuilder().maximumSize(100).build();

  private final ContextDataAccessor<T> contextDataAccessor;

  private final boolean captureExperimentalAttributes;
  private final boolean captureMapMessageAttributes;
  private final boolean captureMarkerAttribute;
  private final List<String> captureContextDataAttributes;
  private final boolean captureAllContextDataAttributes;

  public LogEventMapper(
      ContextDataAccessor<T> contextDataAccessor,
      boolean captureExperimentalAttributes,
      boolean captureMapMessageAttributes,
      boolean captureMarkerAttribute,
      List<String> captureContextDataAttributes) {

    this.contextDataAccessor = contextDataAccessor;
    this.captureExperimentalAttributes = captureExperimentalAttributes;
    this.captureMapMessageAttributes = captureMapMessageAttributes;
    this.captureMarkerAttribute = captureMarkerAttribute;
    this.captureContextDataAttributes = captureContextDataAttributes;
    this.captureAllContextDataAttributes =
        captureContextDataAttributes.size() == 1 && captureContextDataAttributes.get(0).equals("*");
  }

  /**
   * Map the {@link LogEvent} data model onto the {@link LogRecordBuilder}. Unmapped fields include:
   *
   * <ul>
   *   <li>Fully qualified class name - {@link LogEvent#getLoggerFqcn()}
   *   <li>Thread name - {@link LogEvent#getThreadName()}
   *   <li>Thread id - {@link LogEvent#getThreadId()}
   *   <li>Thread priority - {@link LogEvent#getThreadPriority()}
   *   <li>Marker - {@link LogEvent#getMarker()}
   *   <li>Nested diagnostic context - {@link LogEvent#getContextStack()}
   * </ul>
   */
  public void mapLogEvent(
      LogRecordBuilder builder,
      Message message,
      Level level,
      Marker marker,
      Throwable throwable,
      T contextData,
      String threadName,
      long threadId) {

    Attributes.Builder attributes = Attributes.newBuilder();

    captureMessage(builder, attributes, message);

    if (captureMarkerAttribute) {
      if (marker != null) {
        String markerName = marker.getName();
        attributes.setAttribute(LOG_MARKER, markerName);
      }
    }

    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.name());
    }

    if (throwable != null) {
      setThrowable(attributes, throwable);
    }

    captureContextDataAttributes(attributes, contextData);

    if (captureExperimentalAttributes) {
      attributes.setAttribute(THREAD_NAME, threadName);
      attributes.setAttribute(THREAD_ID, threadId);
    }

    builder.setAllAttributes(attributes.build());

    builder.setContext(Context.current());
  }

  // visible for testing
  void captureMessage(LogRecordBuilder builder, Attributes.Builder attributes, Message message) {
    if (message == null) {
      return;
    }
    if (!(message instanceof MapMessage)) {
      builder.setBody(message.getFormattedMessage());
      return;
    }

    MapMessage<?, ?> mapMessage = (MapMessage<?, ?>) message;

    String body = mapMessage.getFormat();
    boolean checkSpecialMapMessageAttribute = (body == null || body.isEmpty());
    if (checkSpecialMapMessageAttribute) {
      body = mapMessage.get(SPECIAL_MAP_MESSAGE_ATTRIBUTE);
    }

    if (body != null && !body.isEmpty()) {
      builder.setBody(body);
    }

    if (captureMapMessageAttributes) {
      // TODO (trask) this could be optimized in 2.9 and later by calling MapMessage.forEach()
      Map<String, ?> data = mapMessage.getData();
      for (Map.Entry<String, ?> entry : data.entrySet()) {
        if (entry.getValue() != null
            && (!checkSpecialMapMessageAttribute
                || !entry.getKey().equals(SPECIAL_MAP_MESSAGE_ATTRIBUTE))) {
          attributes.setAttribute(getMapMessageAttributeKey(entry.getKey()), entry.getValue().toString());
        }
      }
    }
  }

  // visible for testing
  void captureContextDataAttributes(final Attributes.Builder attributes, T contextData) {

    if (captureAllContextDataAttributes) {
      contextDataAccessor.forEach(
          contextData,
          new BiConsumer<String, Object>() {
            @Override
            public void accept(String key, Object value) {
              if (value != null) {
                attributes.setAttribute(getContextDataAttributeKey(key), value.toString());
              }
            }
          });
      return;
    }

    for (String key : captureContextDataAttributes) {
      Object value = contextDataAccessor.getValue(contextData, key);
      if (value != null) {
        attributes.setAttribute(getContextDataAttributeKey(key), value.toString());
      }
    }
  }

  public static String getContextDataAttributeKey(String key) {
    return key;
  }

  public static String getMapMessageAttributeKey(String key) {
    if (mapMessageAttributeKeyCache.getIfPresent(key) == null) {
      mapMessageAttributeKeyCache.put(key, "log4j.map_message." + key);
    }
    return mapMessageAttributeKeyCache.getIfPresent(key);
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
    switch (level.getStandardLevel()) {
      case ALL:
      case TRACE:
        return Severity.TRACE;
      case DEBUG:
        return Severity.DEBUG;
      case INFO:
        return Severity.INFO;
      case WARN:
        return Severity.WARN;
      case ERROR:
        return Severity.ERROR;
      case FATAL:
        return Severity.FATAL;
      case OFF:
        return Severity.UNDEFINED_SEVERITY_NUMBER;
    }
    return Severity.UNDEFINED_SEVERITY_NUMBER;
  }
}
