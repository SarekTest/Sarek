package dev.sarek.agent.constructor_mock;

import java.util.HashSet;
import java.util.Set;

public class ConstructorMockRegistry {
  private static Set<String> mockClasses = new HashSet<>();
  private static ThreadLocal<Boolean> mockInCreation = ThreadLocal.withInitial(() -> false);

  public static boolean isMockInCreation() {
    return mockInCreation.get();
  }

  public static void setMockInCreation(boolean inCreation) {
    mockInCreation.set(inCreation);
  }

  public static boolean isMock(String className) {
    return mockClasses.contains(className);
  }

  public static boolean activate(String className) {
    return mockClasses.add(className);
  }

  public static boolean deactivate(String className) {
    return mockClasses.remove(className);
  }

  public static boolean isObjectInConstructionMock() {
    // TODO: This is slow and assumes that constructor mocks should be mocked on all class loaders.
    //       Consider Björn Kautler's ThreadLocal idea or an alternative approach?

    // This is said to be faster than Thread.currentThread().getStackTrace()
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    int constructorIndex = -1;
    for (int i = 1; i < stackTrace.length; i++) {
      if (!stackTrace[i].getMethodName().equals("<init>"))
        break;
      constructorIndex = i;
    }
    return constructorIndex > 0 && isMock(stackTrace[constructorIndex].getClassName());
  }
}
