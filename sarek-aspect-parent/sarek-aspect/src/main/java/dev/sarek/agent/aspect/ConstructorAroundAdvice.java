package dev.sarek.agent.aspect;

import java.lang.reflect.Constructor;

public class ConstructorAroundAdvice extends AroundAdvice<Constructor<?>> {
  private final Before before;
  private final After after;

  public static final Before BEFORE_DEFAULT = (method, args) -> { };

  public static final After AFTER_DEFAULT = (target, method, args) -> { };

  public ConstructorAroundAdvice(Before before, After after) {
    this.before = before == null ? BEFORE_DEFAULT : before;
    this.after = after == null ? AFTER_DEFAULT : after;
  }

  public void before(Constructor<?> constructor, Object[] args) {
    before.apply(constructor, args);
  }

  public void after(Object target, Constructor<?> constructor, Object[] args) {
    after.apply(target, constructor, args);
  }

  public interface Before {
    /**
     * @param constructor constructor to be executed
     * @param args        constructor arguments; change if you want to pass on other arguments
     *                    to the intercepted constructor
     */
    void apply(Constructor<?> constructor, Object[] args);
  }

  public interface After {
    /**
     * @param target      object which has just been created by the previously called constructor
     * @param constructor constructor which has just been executed
     * @param args        constructor arguments, possibly changed in {@link Before#apply(Constructor, Object[])}
     */
    void apply(Object target, Constructor<?> constructor, Object[] args);
  }
}
