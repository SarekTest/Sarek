package de.scrum_master.agent.remove_final;

import java.lang.instrument.Instrumentation;

public class RemoveFinalAgent {
  private static boolean active;

  /**
   * Attach agent dynamically after JVM start-up
   *
   * @param options path to configuration properties file for class
   *                   {@link RemoveFinalTransformer}. Add this parameter on the command line
   *                   after the Java agent path via <code>=/path/to/my-config.properties</code>.
   */
  public static void agentmain(String options, Instrumentation instrumentation) {
    premain(options, instrumentation);
  }

  /**
   * Start agent via <code>-javaagent:/path/to/my-agent.jar=<i>options</i></code> JVM parameter
   *
   * @param options path to configuration properties file for class
   *                   {@link RemoveFinalTransformer}. Add this parameter on the command line
   *                   after the Java agent path via <code>=/path/to/my-config.properties</code>.
   */
  public static void premain(String options, Instrumentation instrumentation) {
    active = true;
    // TODO: document how to use '-javaagent:my.jar=verbose' -> Javadoc, read-me
    boolean logRemoveFinal = options != null && options.trim().toLowerCase().contains("verbose");

    RemoveFinalTransformer.install(instrumentation, logRemoveFinal);
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
