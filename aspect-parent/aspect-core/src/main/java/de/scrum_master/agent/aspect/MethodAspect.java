package de.scrum_master.agent.aspect;

import net.bytebuddy.asm.Advice.*;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

public abstract class MethodAspect extends Aspect<Method> {

  // TODO: What happens if more than one transformer matches the same instance?
  //       Idea: enable class to register Method objects as keys in adviceRegistry.
  //       This would allow to register multiple MethodAroundAdvices per instance and
  //       make a per-method mocking/stubbing scheme easy to implement.
  // TODO: Try @Advice.Local for transferring additional state between before/after advices if necessary
  public static final Map<Object, MethodAroundAdvice> adviceRegistry = Collections.synchronizedMap(new HashMap<>());

  @SuppressWarnings("UnusedAssignment")
  @OnMethodEnter(skipOn = OnDefaultValue.class)
  public static boolean before(
    @This(typing = DYNAMIC, optional = true) Object target,
    @Origin Method method,
    @AllArguments(readOnly = false, typing = DYNAMIC) Object[] args
  ) {
    // Get advice for target object instance or target class
    // TODO: use @Advice.Local in order to communicate advice to 'after' method instead of a 2nd lookup
    MethodAroundAdvice advice = getAroundAdvice(target, method);

    // If no advice is registered, proceed to target method normally
    if (advice == null)
      return true;

    // Copy 'args' array because ByteBuddy performs special bytecode manipulation on 'args'.
    // See also https://github.com/raphw/byte-buddy/issues/850#issuecomment-621387855.
    // The only way to make it back here for argument changes applied to the array in the delegate advice
    // called by advice.before() is to pass it a copy and then re-assign that copy back to 'args'.
    // TODO: https://github.com/raphw/byte-buddy/issues/850 has been fixed, so replace this by
    //       Object[] argsCopy = Arrays.copyOf(args, args.length);
    //       after ByteBuddy 1.10.11 has been released

    Object[] argsCopy = new Object[args.length];
    System.arraycopy(args, 0, argsCopy, 0, args.length);

    // Check if user-defined advice wants to proceed (true) to target method or not (false)
    boolean shouldProceed = advice.before(target, method, argsCopy);

    // Assign back copied arguments array to 'args' because this assignment is how ByteBuddy recognises
    // that the user wants to pass on parameter changes.
    args = argsCopy;

    return shouldProceed;
  }

  @SuppressWarnings("UnusedAssignment")
  @OnMethodExit(onThrowable = Throwable.class, backupArguments = false)
  public static void after(
    @This(typing = DYNAMIC, optional = true) Object target,
    @Origin Method method,
    @AllArguments(readOnly = false, typing = DYNAMIC) Object[] args,
    @Enter boolean proceedMode,
    @Return(readOnly = false, typing = DYNAMIC) Object returnValue,
    @Thrown(readOnly = false, typing = DYNAMIC) Throwable throwable
  ) {
    // Get advice for target object instance or target class
    MethodAroundAdvice advice = getAroundAdvice(target, method);

    // If no advice is registered, just pass through result
    if (advice == null)
      return;

    try {
      returnValue = advice.after(target, method, args, proceedMode, returnValue, throwable);
      throwable = null;
    } catch (Throwable e) {
      throwable = e;
      returnValue = null;
    }
  }

  /**
   * Keep this method public because it must be callable from advice code woven into other classes
   */
  public static MethodAroundAdvice getAroundAdvice(Object target, Method method) {
    MethodAroundAdvice advice = null;
    // Non-static method? -> search for instance advice
    if (target != null)
      advice = adviceRegistry.get(target);
    // Static method or no instance advice? -> search for class advice
    if (advice == null) {
      advice = adviceRegistry.get(method.getDeclaringClass());
    }
    return advice;

/*
    //    MethodAroundAdvice perInstance = adviceRegistry.get(target);
//    MethodAroundAdvice perMethod = adviceRegistry.get(method);
//    MethodAroundAdvice perClass = adviceRegistry.get(method.getDeclaringClass());
    MethodAroundAdvice advice;
    // (1) Search for method advice
    if ((advice = adviceRegistry.get(method)) != null) {

      // An instance must be registered for the exact same method advice
      if (!advice.equals(adviceRegistry.get(target)))
        return null;
    }
    // (2) No method advice found and non-static method? -> search for instance advice
    if (advice == null && target != null)
      advice = adviceRegistry.get(target);
    // (3) Static method or no instance advice? -> search for class advice
    if (advice == null)
      advice = adviceRegistry.get(method.getDeclaringClass());
    return advice;
*/

  }

}
