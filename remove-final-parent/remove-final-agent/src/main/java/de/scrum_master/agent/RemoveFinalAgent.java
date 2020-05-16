package de.scrum_master.agent;

import java.lang.instrument.Instrumentation;

public class RemoveFinalAgent {
  private static boolean active;

  /**
   * Attach agent dynamically after JVM start-up
   *
   * @param configFile path to configuration properties file for class
   *                   {@link RemoveFinalTransformer}. Add this parameter on the command line
   *                   after the Java agent path via <code>=/path/to/my-config.properties</code>.
   */
  public static void agentmain(String configFile, Instrumentation instrumentation) {
    premain(configFile, instrumentation);
  }

  /**
   * Start agent via <code>-javaagent:/path/to/my-agent.jar=<i>configFile</i></code> JVM parameter
   *
   * @param configFile path to configuration properties file for class
   *                   {@link RemoveFinalTransformer}. Add this parameter on the command line
   *                   after the Java agent path via <code>=/path/to/my-config.properties</code>.
   */
  public static void premain(String configFile, Instrumentation instrumentation) {
    active = true;
    RemoveFinalTransformer.install(instrumentation);
  }

  /**
   * Report agent status. This method can be used e.g. by tests which should be ignored if
   * the agent is inactive because then there would be unmockable target classes which only
   * become mockable if the agent transforms the classes during class-loading.
   *
   * @return <code>true</code> if agent was canonically started via <code>premain</code> or
   * <code>agentmain</code> methods; <code>false</code> otherwise
   */
  public static boolean isActive() {
    return active;
  }
}
