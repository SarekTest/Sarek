package dev.sarek.agent;

import dev.sarek.agent.unfinal.UnFinalTransformer;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

public class SarekAgent {
  private static final String AGENT_PREFIX = "[Sarek Agent] ";
  private static final String MANIFEST_MF = "META-INF/MANIFEST.MF";
  private static final String BOOT_CLASS_PATH = "Boot-Class-Path:";

  private static boolean active;
  private static Instrumentation instrumentation;

  private static boolean verbose;
  private static boolean unFinalActive;

  /**
   * Attach agent dynamically after JVM start-up
   */
  public static void agentmain(String commandLineOptions, Instrumentation instr) throws AgentException {
    premain(commandLineOptions, instr);
  }

  /**
   * Start agent via <code>-javaagent:/path/to/my-agent.jar=<i>options</i></code> JVM parameter
   */
  public static void premain(String commandLineOptions, Instrumentation instr) throws AgentException {
    if (SarekAgent.class.getClassLoader() != null)
      throw new AgentException(
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
  }

  private static void attachUnFinalTransformer(boolean logUnFinal) {
    instrumentation.addTransformer(
      UnFinalTransformer.createTransformer(logUnFinal),
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

}
