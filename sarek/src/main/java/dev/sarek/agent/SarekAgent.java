package dev.sarek.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

// TODO:
//   - In order to avoid possible collisions with clients using BB in a conflicting
//     version already, I could relocate the BB classes (including ASM) to a
//     separate base package such as dev.sarek.jar and exclude that package
//     from both definalisation and aspect weaving.
//   - A fat JAR version of the wrapper agent could contain the aspect framework
//     JAR as a resource and unpack it upon start-up. This would make both JAR
//     detection more reliable and also relieve the user from having to add the
//     aspect framework to her class path.
public class SarekAgent {
  private static final String AGENT_PREFIX = "[Sarek Agent] ";
  private static final String MANIFEST_MF = "META-INF/MANIFEST.MF";
  private static final String BOOT_CLASS_PATH = "Boot-Class-Path:";

  private static boolean active;
  private static Instrumentation instrumentation;

  private static boolean verbose;
  private static boolean unFinalActive;
  private static boolean aspectActive;
  private static boolean constructorMockActive;
  private static boolean mockActive;

  public static void agentmain(String commandLineOptions, Instrumentation instr) throws Exception {
    premain(commandLineOptions, instr);
  }

  public static void premain(String commandLineOptions, Instrumentation instr) throws Exception {
    if (SarekAgent.class.getClassLoader() != null)
      throw new IllegalArgumentException(
        "Java agent is not on boot class path. Please do not rename the agent JAR file. " +
          "The file name at the end of the path specified with the '-javaagent:' parameter " +
          "must be identical with the value of '" + BOOT_CLASS_PATH + "' in '" + MANIFEST_MF + "' " +
          "inside the agent JAR."
      );

    active = true;
    instrumentation = instr;

    parseOptions(commandLineOptions);
    if (unFinalActive)
      attachUnFinalTransformer(verbose);
  }

  private static void parseOptions(String commandLineOptions) {
    // TODO: document available options
    List<String> options = Arrays.asList(commandLineOptions.trim().toLowerCase().split(","));
    verbose = options.contains("verbose");
    if (verbose)
      System.out.println(AGENT_PREFIX + "command line options = " + commandLineOptions);
    unFinalActive = options.contains("unfinal");
    mockActive = options.contains("mock");
    aspectActive = mockActive || options.contains("aspect");
    constructorMockActive = mockActive || options.contains("constructormock");
  }

  private static void attachUnFinalTransformer(boolean logUnFinal) throws ReflectiveOperationException {
    // TODO: refactor to use new Agent API
    instrumentation.addTransformer(
      (ClassFileTransformer) Class
        .forName("dev.sarek.agent.unfinal.UnFinalTransformer")
        .getDeclaredMethod("createTransformer", boolean.class)
        .invoke(null, logUnFinal),
      false
    );
  }

  public static boolean isActive() {
    return active;
  }

  public static Instrumentation getInstrumentation() {
    return instrumentation;
  }

  public static boolean isVerbose() {
    return verbose;
  }

  public static boolean isUnFinalActive() {
    return unFinalActive;
  }

  public static boolean isAspectActive() {
    return aspectActive;
  }

  public static boolean isConstructorMockActive() {
    return constructorMockActive;
  }

  public static boolean isMockActive() {
    return mockActive;
  }

}
