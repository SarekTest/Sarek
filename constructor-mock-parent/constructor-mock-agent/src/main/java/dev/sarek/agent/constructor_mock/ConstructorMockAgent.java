package dev.sarek.agent.constructor_mock;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class ConstructorMockAgent {
  private static boolean active;// = true;  // TODO: remove default value ###

  /**
   * Start agent via <code>-javaagent:/path/to/my-agent.jar=<i>configFile</i></code> JVM parameter
   *
   * @param configFile path to configuration properties file for class
   *                   {@link ConstructorMockTransformer}. Add this parameter on the command line
   *                   after the Java agent path via <code>=/path/to/my-config.properties</code>.
   */
  public static void premain(String configFile, Instrumentation inst) throws Exception {
    transform(configFile, inst);
  }

  /**
   * Attach agent dynamically after JVM start-up
   *
   * @param configFile path to configuration properties file for class
   *                   {@link ConstructorMockTransformer}
   */
  public static void agentmain(String configFile, Instrumentation inst) throws Exception {
    transform(configFile, inst);
  }

  private static void transform(String configFile, Instrumentation instrumentation) throws Exception {
    instrumentation.appendToBootstrapClassLoaderSearch(
      // TODO: make more generic
      // TODO: decide which artifact to use (normal, all, all-special)
      new JarFile("C:/Users/alexa/.m2/repository/dev/sarek/constructor-mock-agent-javassist/1.0-SNAPSHOT/constructor-mock-agent-javassist-1.0-SNAPSHOT-all.jar")
    );
    active = true;
    System.out.println("[Constructor Mock Agent] Installing ConstructorMockTransformer");
    instrumentation.addTransformer(
      // new ConstructorMockTransformer(configFile)
      (ClassFileTransformer) Class
        .forName("dev.sarek.agent.constructor_mock.ConstructorMockTransformer")
        .getDeclaredConstructor()
        .newInstance(), // TODO: pass through 'logConstructorMock'
      true
    );
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
