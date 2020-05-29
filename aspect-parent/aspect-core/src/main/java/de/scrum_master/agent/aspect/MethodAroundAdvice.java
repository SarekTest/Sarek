package de.scrum_master.agent.aspect;

import java.lang.reflect.Method;

public class MethodAroundAdvice extends AroundAdvice<Method> {
  private final Before before;
  private final After after;

  public static final Before BEFORE_DEFAULT = (target, method, args) -> true;

  public static final After AFTER_DEFAULT = (target, method, args, proceedMode, returnValue, throwable) -> {
    if (throwable != null)
      throw throwable;
    return returnValue;
  };

  public static final MethodAroundAdvice MOCK = new MethodAroundAdvice(
    // Skip target method (do not proceed)
    (target, method, args) -> false,
    // Just return original value (which in this case should be the @StubValue)
    (target, method, args, proceedMode, returnValue, throwable) -> returnValue
  );

  public MethodAroundAdvice(Before before, After after) {
    this.before = before == null ? BEFORE_DEFAULT : before;
    this.after = after == null ? AFTER_DEFAULT : after;
  }

  public boolean before(Object target, Method method, Object[] args) {
    return before.apply(target, method, args);
  }

  public Object after(
    Object target, Method method, Object[] args,
    boolean proceedMode, Object returnValue, Throwable throwable
  ) throws Throwable {
    return after.apply(target, method, args, proceedMode, returnValue, throwable);
  }

  public interface Before {
    /**
     * @param target object on which method is called; if null, it is a static method
     * @param method method to be executed
     * @param args   method arguments; change if you want to pass on other arguments to the intercepted method
     * @return true if intercepted method should be called, false if you want to skip the call
     */
    boolean apply(Object target, Method method, Object[] args);
  }

  public interface After {
    /**
     * @param target      object on which method was called; if null, it is a static method
     * @param method      method which has just been executed
     * @param args        method arguments, possibly changed by shouldProceed
     * @param proceedMode true if intercepted method was called, false if it was skipped because
     *                    {@link Before#apply(Object, Method, Object[])} returned false
     * @param returnValue result of intercepted method, if it was called and there was no exception; null otherwise
     * @param throwable   exception thrown by intercepted method, if any
     * @return result which should be returned to the caller; change if you want to return a different result instead
     * @throws Throwable Feel free to not throw any exception, to re-throw <i>throwable</i> or to throw any other
     *                   checked exception type declared by the intercepted method or an unchecked exception
     */
    Object apply(
      Object target, Method method, Object[] args,
      boolean proceedMode, Object returnValue, Throwable throwable
    )
      throws Throwable;
  }
}
