package io.opentelemetry.instrumentation.auto.log4j.appender.v2_17;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.logs.LogRecordBuilder;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;

@AutoService(Instrumenter.class)
public class Log4jAppenderInstrumentation extends Instrumenter.Default {

  public Log4jAppenderInstrumentation() {
    super("log4j-appender", "log4j-appender-2.17");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.logging.log4j.spi.AbstractLogger"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
        "io.opentelemetry.instrumentation.auto.log4j.appender.v2_17.BiConsumer",
        "io.opentelemetry.instrumentation.auto.log4j.appender.v2_17.ContextDataAccessor",
        "io.opentelemetry.instrumentation.auto.log4j.appender.v2_17.Log4jHelper",
        "io.opentelemetry.instrumentation.auto.log4j.appender.v2_17.LogEventMapper",
        "io.opentelemetry.instrumentation.auto.log4j.appender.v2_17.LoggerHelper",
    };
  }
  
  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isProtected().or(isPublic()))
            .and(named("log"))
            .and(takesArguments(6))
            .and(takesArgument(0, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(1, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(2, String.class))
            .and(takesArgument(3, StackTraceElement.class))
            .and(takesArgument(4, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(5, Throwable.class)),
        Log4jAppenderInstrumentation.class.getName() + "$LogAdvice");
    transformers.put(
        isMethod()
            .and(isProtected().or(isPublic()))
            .and(named("logMessage"))
            .and(takesArguments(5))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(2, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(3, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(4, Throwable.class)),
        Log4jAppenderInstrumentation.class.getName() + "$LogMessageAdvice");
    return transformers;
  }

  @SuppressWarnings("unused")
  public static class LogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Logger logger,
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) Marker marker,
        @Advice.Argument(4) Message message,
        @Advice.Argument(5) Throwable t) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(LogRecordBuilder.class);
      if (callDepth == 0) {
        Log4jHelper.capture(logger, level, marker, message, t);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit() {
      CallDepthThreadLocalMap.decrementCallDepth(LogRecordBuilder.class);
    }
  }

  @SuppressWarnings("unused")
  public static class LogMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Logger logger,
        @Advice.Argument(1) Level level,
        @Advice.Argument(2) Marker marker,
        @Advice.Argument(3) Message message,
        @Advice.Argument(4) Throwable t) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(LogRecordBuilder.class);
      if (callDepth == 0) {
        Log4jHelper.capture(logger, level, marker, message, t);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit() {
      CallDepthThreadLocalMap.decrementCallDepth(LogRecordBuilder.class);
    }
  }
}
