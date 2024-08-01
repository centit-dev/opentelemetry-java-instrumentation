package io.opentelemetry.instrumentation.auto.jdbc_ext;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.sql.PreparedStatement;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class PreparedStatementSetterInstrumentation extends Instrumenter.Default {

  public PreparedStatementSetterInstrumentation() {
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
    ElementMatcher.Junction<MethodDescription> setSignature = nameStartsWith("set").and(isPublic());
    return singletonMap(setSignature, this.getClass().getName() + "$PreparedStatementSetterAdvice");
  }

  public static class PreparedStatementSetterAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This PreparedStatement statement, @Advice.AllArguments Object[] args) {
      if (args == null || args.length == 0) {
        return;
      }

      // multiple delegates can be called in the same method
      // use a depth counter to avoid repeating the same work
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(PreparedStatement.class);
      if (callDepth > 0) {
        return;
      }

      // record the ordered arguments
      Object index = args[0];
      if (!(index instanceof Integer)) {
        return;
      }
      PreparedStatementArgumentHelper.put(statement, (Integer) index, args[1]);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      CallDepthThreadLocalMap.decrementCallDepth(PreparedStatement.class);
    }
  }
}
