package io.opentelemetry.instrumentation.auto.jdbc_ext;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.TreeMap;

public class PreparedStatementArgumentHelper {

  private static final Cache<PreparedStatement, Map<Integer, Object>> ARGUMENTS =
      CacheBuilder.newBuilder().maximumSize(100).build();

  public static Map<Integer, Object> get(PreparedStatement statement) {
    return ARGUMENTS.getIfPresent(statement);
  }

  public static void put(PreparedStatement statement, Integer index, Object value) {
    Map<Integer, Object> arguments = get(statement);
    if (arguments == null) {
      arguments = new TreeMap<>();
      ARGUMENTS.put(statement, arguments);
    }
    arguments.put(index, value);
  }

  public static void clear(PreparedStatement statement) {
    ARGUMENTS.invalidate(statement);
  }
}
