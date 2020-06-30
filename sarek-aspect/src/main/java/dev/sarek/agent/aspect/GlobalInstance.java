package dev.sarek.agent.aspect;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GlobalInstance<T> {
  private static final Map<Class<?>, GlobalInstance<?>> GLOBAL_INSTANCE_CACHE = new HashMap<>();

  private final Class<T> targetClass;

  public static <T> GlobalInstance<T> of(Class<T> targetClass) {
    GlobalInstance<T> globalInstance = (GlobalInstance<T>) GLOBAL_INSTANCE_CACHE.get(targetClass);
    if (globalInstance == null) {
      globalInstance = new GlobalInstance<>(targetClass);
      GLOBAL_INSTANCE_CACHE.put(targetClass, globalInstance);
    }
    return globalInstance;
  }

  private GlobalInstance(Class<T> targetClass) {
    if (targetClass == null)
      throw new IllegalArgumentException("target class must not be null");
    this.targetClass = targetClass;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    GlobalInstance<?> that = (GlobalInstance<?>) o;
    return targetClass.equals(that.targetClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetClass);
  }

  public Class<T> getTargetClass() {
    return targetClass;
  }

  @Override
  public String toString() {
    return "InstanceGlobal(" + targetClass.getName() + ')';
  }
}
