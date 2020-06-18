package dev.sarek.agent.aspect;

import java.util.Objects;

public class GlobalInstance<T> {
  private final Class<T> targetClass;

  public static <T> GlobalInstance<T> of(Class<T> targetClass) {
    return new GlobalInstance<>(targetClass);
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
