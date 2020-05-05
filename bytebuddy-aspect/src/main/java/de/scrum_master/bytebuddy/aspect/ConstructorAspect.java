package de.scrum_master.bytebuddy.aspect;

import net.bytebuddy.asm.Advice.*;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

public abstract class ConstructorAspect {

  // TODO: What happens if more than one transformer matches the same instance or class?
  // TODO: Try @Advice.Local for transferring additional state between before/after advices if necessary
  public static final Map<Object, ConstructorAroundAdvice> adviceRegistry = Collections.synchronizedMap(new HashMap<>());

  @SuppressWarnings("UnusedAssignment")
  @OnMethodEnter
  public static void before(
    @Origin Constructor constructor,
    @AllArguments(readOnly = false, typing = DYNAMIC) Object[] args
  ) {
    // Get advice for target object instance or target class
    ConstructorAroundAdvice advice = getAroundAdvice(constructor);

    // If no advice is registered, proceed to target method normally
    if (advice == null)
      return;

    // Copy 'args' array because ByteBuddy performs special bytecode manipulation on 'args'.
    // See also https://github.com/raphw/byte-buddy/issues/850#issuecomment-621387855.
    // The only way to make it back here for argument changes applied to the array in the delegate advice
    // called by advice.before() is to pass it a copy and then re-assign that copy back to 'args'.
    Object[] argsCopy = new Object[args.length];
    System.arraycopy(args, 0, argsCopy, 0, args.length);

    // Dispatch to before advice
    advice.before(constructor, argsCopy);

    // Assign back copied arguments array to 'args' because this assignment is how ByteBuddy recognises
    // that the user wants to pass on parameter changes.
    args = argsCopy;
  }

  @SuppressWarnings("UnusedAssignment")
  @OnMethodExit(backupArguments = false)
  public static void after(
    @This(typing = DYNAMIC, optional = true) Object target,
    @Origin Constructor constructor,
    @AllArguments(readOnly = false, typing = DYNAMIC) Object[] args
  ) {
    // Get advice for target object instance or target class
    ConstructorAroundAdvice advice = getAroundAdvice(constructor);

    // If no advice is registered, just pass through result
    if (advice == null)
      return;

    advice.after(target, constructor, args);
  }

  /**
   * Keep this method public because it must be callable from advice code woven into other classes
   *
   * @param method
   * @return
   */
  public static ConstructorAroundAdvice getAroundAdvice(Constructor method) {
    return adviceRegistry.get(method.getDeclaringClass());
  }

}
