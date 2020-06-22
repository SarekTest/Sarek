package dev.sarek.agent.constructor_mock;

import dev.sarek.agent.test.SeparateJVM;
import dev.sarek.app.ExtendsSub;
import dev.sarek.app.Sub;
import dev.sarek.app.SubWithComplexConstructor;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Date;

import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.*;

@Category(SeparateJVM.class)
public class MemberSubstitutionConstructorMockIT {

  public static class ConstructorAspect {
    @SuppressWarnings("UnusedAssignment")
    @Advice.OnMethodEnter(inline = true)
    public static void before(
      @Advice.Origin Constructor constructor,
      @Advice.AllArguments(readOnly = false, typing = DYNAMIC) Object[] args
    )
      throws IllegalAccessException, InstantiationException, InvocationTargetException
    {
      // Copy 'args' array because ByteBuddy performs special bytecode manipulation on 'args'.
      // See also https://github.com/raphw/byte-buddy/issues/850#issuecomment-621387855.
      // The only way to make it back here for argument changes applied to the array in the delegate advice
      // called by advice.before() is to pass it a copy and then re-assign that copy back to 'args'.
      Object[] argsCopy = Arrays.copyOf(args, args.length);

      // Dispatch to before advice
      if (!proceedCondition()) {
        callSuper(constructor);
      }

      // Assign back copied arguments array to 'args' because this assignment is how ByteBuddy recognises
      // that the user wants to pass on parameter changes.
      args = argsCopy;
    }

    public static boolean proceedCondition() {
      return false;
    }

    public static void callSuper(Constructor<Class<?>> constructor)
      throws IllegalAccessException, InvocationTargetException, InstantiationException
    {
      Class<?> superClass = constructor.getDeclaringClass().getSuperclass();
      if (superClass.equals(Object.class))
        return;
      Constructor<?> superConstructor = Arrays
        .stream(superClass.getDeclaredConstructors())
        .filter(declaredConstructor -> !Modifier.isPrivate(declaredConstructor.getModifiers()))
        .findFirst()
        .orElse(null);
      assert superConstructor != null;
      Object[] superConstructorArgs = Arrays
        .stream(superConstructor.getParameterTypes())
        .map(parameterType -> {
          if (!parameterType.isPrimitive())
            return null;
          if (parameterType.equals(byte.class))
            return (byte) 0;
          if (parameterType.equals(short.class))
            return (short) 0;
          if (parameterType.equals(int.class))
            return 0;
          if (parameterType.equals(long.class))
            return 0L;
          if (parameterType.equals(float.class))
            return 0.0f;
          if (parameterType.equals(double.class))
            return (byte) 0.0d;
          if (parameterType.equals(char.class))
            return (char) 0;
          if (parameterType.equals(boolean.class))
            return false;
          return null;
        })
        .toArray();
      System.out.println("Calling super constructor " + superConstructor + " with args " + Arrays.deepToString(superConstructorArgs));
      superConstructor.newInstance(superConstructorArgs);
    }
  }

  AgentBuilder createAgentBuilder(Class<?>... targetClasses) {
    ElementMatcher.Junction<TypeDescription> typeDescriptionJunction = none();
    for (Class<?> targetClass : targetClasses) {
      typeDescriptionJunction = typeDescriptionJunction
        .or(is(targetClass))
        .or(isSuperTypeOf(targetClass).and(not(is(Object.class))));
    }
    anyOf(Sub.class, SubWithComplexConstructor.class);
    return new AgentBuilder.Default()
      .disableClassFormatChanges()
      .ignore(none())
      .with(RETRANSFORMATION)
      .with(AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError())
      // TODO: make weaver logging configurable in general and with regard to '.withTransformationsOnly()' in particular
      .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
      .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError())
      // Dump all transformed class files into a directory
      //.with(new TransformedClassFileWriter("transformed-aspect"))
      // Match type + method, then bind to advice
      .type(typeDescriptionJunction)
      .transform((builder, typeDescription, classLoader, module) ->
        builder.visit(
          Advice
            .to(ConstructorAspect.class, ClassFileLocator.ForClassLoader.ofSystemLoader())
            .on(isConstructor())
        )
      );
  }

  @Test
  public void test() {
    createAgentBuilder(ExtendsSub.class, SubWithComplexConstructor.class).installOn(ByteBuddyAgent.install());
//    System.out.println("Sub = " + new Sub(111, "foo"));
    System.out.println("ExtendsSub = " + new ExtendsSub(111, "foo", new Date()));
    System.out.println("SubWithComplexConstructor = " + new SubWithComplexConstructor(
      (byte) 12, 'x', 11.11, 22.22f,
      12345, 678L, (short) 123,
      true, "foo",
      new int[][] { { 12, 34 }, { 56, 78 } }
    ));
  }

}
