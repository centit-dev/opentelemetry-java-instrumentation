package io.opentelemetry.instrumentation.auto.jdbc_ext;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;
import java.sql.PreparedStatement;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class PreparedStatementExecutionInstrumentation extends Instrumenter.Default {

  public PreparedStatementExecutionInstrumentation() {
    super("jdbc", "jdbc-arguments");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.PreparedStatement"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.jdbc_ext.Constant",
      "io.opentelemetry.instrumentation.auto.jdbc_ext.PreparedStatementArgumentHelper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("execute").and(takesArguments(0)).and(isPublic()),
        this.getClass().getName() + "$PreparedStatementExecutionAdvice");
  }

  public static class PreparedStatementExecutionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This PreparedStatement statement) {
      Span span = TracingContextUtils.getSpan(Context.current());
      if (!span.getContext().isValid()) {
        return;
      }

      Map<Integer, Object> arguments = PreparedStatementArgumentHelper.get(statement);

      // append the arguments as a string after the span is created
      if (arguments == null || arguments.size() == 0) {
        return;
      }
      StringBuilder builder = new StringBuilder("[");
      int index = 0;
      for (Object value : arguments.values()) {
        if (value instanceof String) {
          builder.append(String.format("'%s'", value));
        } else {
          builder.append(String.format("%s", value));
        }
        index++;
        if (index < arguments.size()) {
          builder.append(",");
        }
      }
      builder.append("]");
      span.setAttribute(Constant.DB_STATEMENT_VALUES, builder.toString());
      PreparedStatementArgumentHelper.clear(statement);
    }
  }
}
