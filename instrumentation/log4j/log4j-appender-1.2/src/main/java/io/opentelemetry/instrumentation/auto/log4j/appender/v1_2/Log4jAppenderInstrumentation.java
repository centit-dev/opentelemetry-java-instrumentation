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

package io.opentelemetry.instrumentation.auto.log4j.appender.v1_2;

import java.util.HashMap;
import java.util.Map;

import com.google.auto.service.AutoService;

import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import org.apache.log4j.Category;
import org.apache.log4j.Priority;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@AutoService(Instrumenter.class)
public class Log4jAppenderInstrumentation extends Instrumenter.Default {

  public Log4jAppenderInstrumentation() {
    super("log4j-appender", "log4j-appender-1.2");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.log4j.Category");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.log4j.appender.v1_2.LogEventMapper",
      "io.opentelemetry.instrumentation.auto.log4j.appender.v1_2.LoggerHelper"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("forcedLog"))
            .and(takesArguments(4))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("org.apache.log4j.Priority")))
            .and(takesArgument(2, Object.class))
            .and(takesArgument(3, Throwable.class)),
        Log4jAppenderInstrumentation.class.getName() + "$ForcedLogAdvice");
    return transformers;
  }

  @SuppressWarnings("unused")
  public static class ForcedLogAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Category logger,
        @Advice.Argument(1) Priority level,
        @Advice.Argument(2) Object message,
        @Advice.Argument(3) Throwable t) {
      // need to track call depth across all loggers to avoid double capture when one logging
      // framework delegates to another
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Category.class);
      if (callDepth > 0) {
        return;
      }
      LogEventMapper.INSTANCE.capture(logger, level, message, t);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit() {
      CallDepthThreadLocalMap.decrementCallDepth(Category.class);
    }
  }
}
