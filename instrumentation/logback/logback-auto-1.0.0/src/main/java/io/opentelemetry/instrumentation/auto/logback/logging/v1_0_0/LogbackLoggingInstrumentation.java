package io.opentelemetry.instrumentation.auto.logback.logging.v1_0_0;

import java.util.Collections;
import java.util.Map;

import com.google.auto.service.AutoService;

import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import application.ch.qos.logback.classic.spi.ILoggingEvent;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@AutoService(Instrumenter.class)
public class LogbackLoggingInstrumentation extends Instrumenter.Default {
  public LogbackLoggingInstrumentation() {
    super("logback-logging");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
        "io.opentelemetry.instrumentation.auto.logback.logging.v1_0_0.LogbackSingletons",
        "io.opentelemetry.instrumentation.auto.logback.logging.v1_0_0.LoggingEventMapper",
    };
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("ch.qos.logback.classic.Logger");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("callAppenders"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("ch.qos.logback.classic.spi.ILoggingEvent"))),
        LogbackLoggingInstrumentation.class.getName() + "$CallAppendersAdvice");
  }

  public static class CallAppendersAdvice {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) ILoggingEvent event) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(ILoggingEvent.class);
      if (callDepth == 0) {
        LogbackSingletons.mapper().emit(event, Thread.currentThread().getId());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit() {
      CallDepthThreadLocalMap.decrementCallDepth(ILoggingEvent.class);
    }
  }
}
