package dev.sarek.test.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;

public class TestHelper {
  private static final ClassLoader classLoader = TestHelper.class.getClassLoader();
  private static final MethodHandle findLoadedClass;

  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      Method findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
      findLoadedClassMethod.setAccessible(true);
      findLoadedClass = lookup.unreflect(findLoadedClassMethod);
    }
    catch (NoSuchMethodException | IllegalAccessException methodHandleProblem) {
      throw new RuntimeException(
        "Cannot use ClassLoader.findLoadedClass in order to check for already loaded classes",
        methodHandleProblem
      );
    }
  }

  public static boolean isClassLoaded(String className) {
    try {
      return findLoadedClass.invoke(classLoader, className) != null;
    }
    catch (RuntimeException runtimeException) {
      throw runtimeException;
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

}
