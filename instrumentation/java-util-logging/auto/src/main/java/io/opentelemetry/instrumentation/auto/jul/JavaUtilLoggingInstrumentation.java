/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jul;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.logs.LogRecordBuilder;

import java.util.Map;
import java.util.logging.LogRecord;

import com.google.auto.service.AutoService;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import application.java.util.logging.Logger;

@AutoService(Instrumenter.class)
public class JavaUtilLoggingInstrumentation extends Instrumenter.Default {

  public JavaUtilLoggingInstrumentation() {
    super("java-util-logging");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("application.java.util.logging.Logger"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("log"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("java.util.logging.LogRecord"))),
        JavaUtilLoggingInstrumentation.class.getName() + "$LogAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
        "io.opentelemetry.instrumentation.auto.jul.JavaUtilLoggingHelper",
        "io.opentelemetry.instrumentation.auto.jul.AccessibleFormatter",
    };
  }

  @SuppressWarnings("unused")
  public static class LogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Logger logger,
        @Advice.Argument(0) LogRecord logRecord) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(LogRecordBuilder.class);
      if (callDepth == 0) {
        JavaUtilLoggingHelper.capture(logger, logRecord);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit() {
      int callDepth = CallDepthThreadLocalMap.decrementCallDepth(LogRecordBuilder.class);
    }
  }
}
