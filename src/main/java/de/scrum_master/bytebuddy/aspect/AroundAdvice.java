package de.scrum_master.bytebuddy.aspect;

import java.lang.reflect.Method;

public class AroundAdvice {
  private final Before before;
  private final After after;

  public static final Before BEFORE_DEFAULT = (target, method, args) -> true;

  public static final After AFTER_DEFAULT = (target, method, args, proceedMode, returnValue, throwable) -> {
    if (throwable != null)
      throw throwable;
    return returnValue;
  };

  public AroundAdvice(Before before, After after) {
    this.before = before == null ? BEFORE_DEFAULT : before;
    this.after = after == null ? AFTER_DEFAULT : after;
  }

  public boolean before(Object target, Method method, Object[] args) {
    return before.apply(target, method, args);
  }

  public Object after(Object target, Method method, Object[] args, boolean proceedMode, Object returnValue, Throwable throwable) throws Throwable {
    return after.apply(target, method, args, proceedMode, returnValue, throwable);
  }

  public interface Before {
    /**
     * @param target object on which method is called; if null, it is a static method - TODO cover constructors
     * @param method method to be executed - TODO: cover constructors, maybe use Executable as base class of Method and Constructor
     * @param args   method arguments; change if you want to pass on other arguments to the intercepted method
     * @return true if intercepted method should be called, false if you want to skip the call
     */
    boolean apply(Object target, Method method, Object[] args);
  }

  public interface After {
    /**
     * @param target      object on which method is called; if null, it is a static method - TODO cover constructors
     * @param method      method to be executed - TODO: cover constructors, maybe use Executable as base class of Method and Constructor
     * @param args        method arguments, possibly changed by shouldProceed
     * @param proceedMode true if intercepted method was called
     * @param returnValue result of intercepted method, if it was called and there was no exception; null otherwise
     * @param throwable   exception thrown by intercepted method, if any
     * @return result which should be returned to the caller; could either be the same as returnValue or something different
     * @throws Throwable Feel free to throw any checked exception type declared by the intercepted method or another
     *                   unchecked throwable; could either be the passed on 'throwable' parameter or something else
     */
    Object apply(Object target, Method method, Object[] args, boolean proceedMode, Object returnValue, Throwable throwable) throws Throwable;
  }
}
