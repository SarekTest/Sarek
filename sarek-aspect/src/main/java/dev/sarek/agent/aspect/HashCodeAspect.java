package dev.sarek.agent.aspect;

import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

/**
 * This advice is specifically designed to override the {@link #hashCode()} method for an advised (usually mocked)
 * class. It is meant to be used together with {@link EqualsAspect}. This can be helpful in order to
 * <ul>
 *   <li>
 *     avoid recursions such as {@code getAroundAdvice(target)} → {@code adviceRegistry.get(target)} →
 *     {@code target.hashCode()} → {@code getAroundAdvice(target)}, which would normally be caught by private
 *     helper methods such as {@link InstanceMethodAspect#doGetAdvice(Object, Method, boolean)} but still take time to
 *     execute,
 *   </li>
 *   <li>
 *     but more importantly mitigate problematic cases in which a mocked method's {@link #hashCode()} is needed to
 *     return something else than 0 because the object is used in a collection like a {@link java.util.HashMap HashMap}
 *     or {@link java.util.HashSet HashSet} and excluding the original method from mocking would not solve the problem
 *     because it internally calls several other methods or uses fields which have never been properly initialised due
 *     to constructor mocking.
 *   </li>
 * </ul>
 * The advice does not use the dynamic invocation infrastructure designed for user-defined advices, hence no recursion.
 * It simply makes {@link #hashCode()} return the result of {@link System#identityHashCode(Object)} for a given object,
 * which does not really implement the normal hash code semantics but returns a different result for distinct objects,
 * disregarding the fact that the original method might return identical values for objects equal to each other from the
 * perspective of {@link #equals(Object)}.
 */
public abstract class HashCodeAspect extends Aspect<Method> {
  @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
  public static boolean before() {
    return false;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, backupArguments = false)
  public static void after(
    @Advice.This(typing = DYNAMIC) Object target,
    @Advice.Return(readOnly = false, typing = DYNAMIC) Object returnValue
  )
  {
//    System.out.println("HashCodeAspect");
    returnValue = System.identityHashCode(target);
  }
}
