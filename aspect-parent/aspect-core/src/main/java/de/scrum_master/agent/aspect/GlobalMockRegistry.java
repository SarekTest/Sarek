package de.scrum_master.agent.aspect;

import java.util.HashSet;
import java.util.Set;

public class GlobalMockRegistry {
  private static Set<Class<?>> mockClasses = new HashSet<>();

  public static boolean isMock(Class<?> candidateClass) {
    return mockClasses.contains(candidateClass);
  }

  public static boolean activate(Class<?> aClass) {
    return mockClasses.add(aClass);
  }

  public static boolean deactivate(Class<?> aClass) {
    return mockClasses.remove(aClass);
  }
}
