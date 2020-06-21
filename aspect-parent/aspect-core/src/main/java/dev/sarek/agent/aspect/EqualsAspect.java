package dev.sarek.agent.aspect;

import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

/**
 * This advice is specifically designed to override the {@link #equals(Object)} method for an advised (usually mocked)
 * class. It is meant to be used together with {@link HashCodeAspect}. For more details, please look there.
 */
public abstract class EqualsAspect extends Aspect<Method> {
  @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
  public static boolean before() {
    return false;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, backupArguments = false)
  public static void after(
    @Advice.This(typing = DYNAMIC) Object target,
    @Advice.Argument(value = 0, readOnly = false, typing = DYNAMIC) Object other,
    @Advice.Return(readOnly = false, typing = DYNAMIC) Object returnValue
  )
  {
    System.out.println("HashCodeAspect");
    returnValue = target == other;
  }
}
