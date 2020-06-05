package dev.sarek.agent.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestHelper {
  public static boolean isClassLoaded(String className) throws RuntimeException {
    try {
      Method findLoadedClass = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
      findLoadedClass.setAccessible(true);
      return findLoadedClass.invoke(TestHelper.class.getClassLoader(), className) != null;
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException reflectionProblem) {
      throw new RuntimeException(
        "Cannot use ClassLoader.findLoadedClass in order to check for already loaded classes",
        reflectionProblem
      );
    }
  }
}
