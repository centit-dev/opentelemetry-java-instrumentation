package application.ch.qos.logback.classic.spi;

import application.org.slf4j.Marker;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import java.util.Map;

public interface ILoggingEvent {
  String getThreadName();

  Level getLevel();

  String getFormattedMessage();

  String getLoggerName();

  LoggerContextVO getLoggerContextVO();

  IThrowableProxy getThrowableProxy();

  StackTraceElement[] getCallerData();

  Marker getMarker();

  Map<String, String> getMDCPropertyMap();

  /** @deprecated */
  Map<String, String> getMdc();

  long getTimeStamp();
}
