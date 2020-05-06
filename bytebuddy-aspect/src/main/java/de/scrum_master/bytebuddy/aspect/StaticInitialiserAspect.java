package de.scrum_master.bytebuddy.aspect;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

public abstract class StaticInitialiserAspect {

  // TODO: What happens if more than one transformer matches the same instance or class?
  // TODO: Try @Advice.Local for transferring additional state between before/after advices if necessary
  public static final Map<Object, StaticInitialiserAroundAdvice> adviceRegistry = Collections.synchronizedMap(new HashMap<>());

  @SuppressWarnings("UnusedAssignment")
  @OnMethodEnter(skipOn = OnDefaultValue.class)
  public static boolean before(
    @Advice.Origin("#t") String staticInitialiserClassName
  ) throws ClassNotFoundException {
    // Get advice for target class
    Class<?> targetClass = toClass(staticInitialiserClassName);
    StaticInitialiserAroundAdvice advice = getAroundAdvice(targetClass);

    // If no advice is registered, proceed to target static initialiser normally
    if (advice == null)
      return true;

    // Check if user-defined advice wants to proceed (true) to target static initialiser or not (false)
    return advice.before(targetClass);
  }

  @SuppressWarnings("UnusedAssignment")
  @OnMethodExit(onThrowable = Throwable.class, backupArguments = false)
  public static void after(
    @Advice.Origin("#t") String staticInitialiserClassName,
    @Advice.Enter boolean proceedMode,
    @Advice.Thrown(readOnly = false, typing = DYNAMIC) Throwable throwable
  ) throws ClassNotFoundException {
    // Get advice for target class
    Class<?> targetClass = toClass(staticInitialiserClassName);
    StaticInitialiserAroundAdvice advice = getAroundAdvice(targetClass);

    // If no advice is registered, just pass through result
    if (advice == null)
      return;

    try {
      advice.after(targetClass, proceedMode, throwable);
      throwable = null;
    } catch (Throwable e) {
      throwable = e;
    }
  }

  /**
   * Keep this method public because it must be callable from advice code woven into other classes
   */
  public static StaticInitialiserAroundAdvice getAroundAdvice(Class<?> clazz) {
      return adviceRegistry.get(clazz);
  }

  /**
   * Keep this method public because it must be callable from advice code woven into other classes
   */
  public static Class<?> toClass(String className) throws ClassNotFoundException {
    return Class.forName(className);
  }

}
