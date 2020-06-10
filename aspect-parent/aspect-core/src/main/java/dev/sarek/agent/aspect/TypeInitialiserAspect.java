package dev.sarek.agent.aspect;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.OnDefaultValue;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;

import java.util.List;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

public abstract class TypeInitialiserAspect extends Aspect<Class<?>> {

  // TODO: What happens if more than one transformer matches the same instance or class?
  // TODO: Try @Advice.Local for transferring additional state between before/after advices if necessary

  @OnMethodEnter(skipOn = OnDefaultValue.class)
  public static boolean before(
    @Advice.Origin("#t") String staticInitialiserClassName
  ) throws ClassNotFoundException
  {
    // Get advice for target class
    Class<?> targetClass = toClass(staticInitialiserClassName);
    TypeInitialiserAroundAdvice advice = getAroundAdvice(targetClass);

    // If no advice is registered, proceed to target type initialiser normally
    if (advice == null)
      return true;

    // Check if user-defined advice wants to proceed (true) to target type initialiser or not (false)
    return advice.before(targetClass);
  }

  @SuppressWarnings("UnusedAssignment")
  @OnMethodExit(onThrowable = Throwable.class, backupArguments = false)
  public static void after(
    @Advice.Origin("#t") String staticInitialiserClassName,
    @Advice.Enter boolean proceedMode,
    @Advice.Thrown(readOnly = false, typing = DYNAMIC) Throwable throwable
  ) throws ClassNotFoundException
  {
    // Get advice for target class
    Class<?> targetClass = toClass(staticInitialiserClassName);
    // TODO: use @Advice.Local in order to communicate advice to 'after' method instead of a 2nd lookup
    TypeInitialiserAroundAdvice advice = getAroundAdvice(targetClass);

    // If no advice is registered, just pass through result
    if (advice == null)
      return;

    try {
      advice.after(targetClass, proceedMode, throwable);
      throwable = null;
    }
    catch (Throwable e) {
      throwable = e;
    }
  }

  /**
   * Keep this method public because it must be callable from advice code woven into other classes
   */
  public static TypeInitialiserAroundAdvice getAroundAdvice(Class<?> target) {
    List<Weaver.Builder.AdviceDescription> adviceDescriptions = adviceRegistry.get(target);
    return adviceDescriptions == null ? null :
      adviceDescriptions
        .stream()
        .filter(adviceDescription -> adviceDescription.adviceType.equals(AdviceType.TYPE_INITIALISER_ADVICE))
        .map(adviceDescription -> (TypeInitialiserAroundAdvice) adviceDescription.advice)
        .findFirst()
        .orElse(null);
  }

  /**
   * Keep this method public because it must be callable from advice code woven into other classes
   */
  public static Class<?> toClass(String className) throws ClassNotFoundException {
    return Class.forName(className);
  }

}
