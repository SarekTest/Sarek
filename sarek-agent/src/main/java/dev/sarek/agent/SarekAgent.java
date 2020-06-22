package dev.sarek.agent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarFile;

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

  private static boolean active;
  private static Instrumentation instrumentation;

  private static boolean verbose;
  private static boolean removeFinalActive;
  private static boolean aspectActive;
  private static boolean constructorMockActive;
  private static boolean mockActive;

  public static void agentmain(String commandLineOptions, Instrumentation instr) throws Exception {
    premain(commandLineOptions, instr);
  }

  public static void premain(String commandLineOptions, Instrumentation instr) throws Exception {
    active = true;
    instrumentation = instr;

    parseOptions(commandLineOptions);
    appendJarsToBootstrapClassLoaderSearch();
  }

  private static void parseOptions(String commandLineOptions) {
    // TODO: document available options
    List<String> options = Arrays.asList(commandLineOptions.trim().toLowerCase().split(","));
    verbose = options.contains("verbose");
    if (verbose)
      System.out.println(AGENT_PREFIX + "command line options = " + commandLineOptions);
    removeFinalActive = options.contains("removefinal");
    mockActive = options.contains("mock");
    aspectActive = mockActive || options.contains("aspect");
    constructorMockActive = mockActive || options.contains("constructormock");
  }

  private static void appendJarsToBootstrapClassLoaderSearch() throws IOException, URISyntaxException, ReflectiveOperationException {
    // TODO: implement logic of optional JARs depending on command line options
    // Multiple agents could have been shaded into the same JAR file -> use a set of canonical file names
    Set<String> jarFileNames = new HashSet<>();

    if (removeFinalActive)
      jarFileNames.add(findJarFile("dev/sarek/agent/remove_final/RemoveFinalTransformer.class"));
    if (mockActive)
      jarFileNames.add(findJarFile("dev/sarek/agent/mock/MockFactory.class"));
    if (aspectActive)
      jarFileNames.add(findJarFile("dev/sarek/agent/aspect/Weaver.class"));
    if (constructorMockActive)
      jarFileNames.add(findJarFile("dev/sarek/agent/constructor_mock/ConstructorMockTransformer.class"));

    jarFileNames
      .stream()
      .map(jarFileName -> {
        try {
          return new JarFile(jarFileName);
        }
        catch (IOException | SecurityException e) {
          throw new RuntimeException("Cannot create JarFile " + jarFileName, e);
        }
      })
      .forEach(instrumentation::appendToBootstrapClassLoaderSearch);

    if (removeFinalActive)
      attachRemoveFinalTransformer(verbose);
  }

  // TODO: optionally pack shaded JARs (all vs. all-special) into agent JAR, unpack and attach
  //       if not found in file system or on classpath
  private static String findJarFile(String ressourcePath) throws IOException, URISyntaxException {
    URL resource = ClassLoader.getSystemResource(ressourcePath);
    if (resource == null)
      throw new FileNotFoundException(
        "Cannot find resource file " + ressourcePath + ". " +
          "Please make sure the corresponding library is on the class path."
      );
    String resourceURL = resource.getPath().replaceFirst("!.*", "");
    if (resourceURL.equals(""))
      throw new FileNotFoundException(
        "Cannot determine URL for resource file " + ressourcePath + ". " +
          "Please make sure the corresponding library is on the class path."
      );
    if (verbose)
      System.out.println(AGENT_PREFIX + "resourceURL = " + resourceURL);
    File jarFile;
    if (resourceURL.contains("/target/classes/")) {
      // Try to fix the phenomenon that in IntelliJ IDEA when running the test via run configuration,
      // the runner insists on referring to the 'sarek-aspect' module locally via 'target/classes'
      // instead of to the JAR in the local Maven repository or in the module's 'target' directory.
      File targetDir = new File(resourceURL.replaceFirst("(/target)/classes/.*", "$1"));
      jarFile = Arrays
        .stream(Objects.requireNonNull(
          targetDir.listFiles((dir, name) ->
            // TODO: "-all" or "all-special" -> how to decide?
            name.endsWith("-all.jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-sources.jar")
          )
        ))
        .findFirst()
        .orElseThrow(() -> new FileNotFoundException(
          "Cannot find JAR file containing resource " + ressourcePath + " in directory " + targetDir
        ));
    }
    else {
      jarFile = new File(new URL(resourceURL).toURI());
    }
    if (verbose)
      System.out.println(AGENT_PREFIX + "Found resource JAR file: " + jarFile);
    return jarFile.getCanonicalPath();
  }

  private static void attachRemoveFinalTransformer(boolean logRemoveFinal) throws ReflectiveOperationException {
    // TODO: refactor to use new Agent API
    instrumentation.addTransformer(
      (ClassFileTransformer) Class
        .forName("dev.sarek.agent.remove_final.RemoveFinalTransformer")
        .getDeclaredMethod("createTransformer", boolean.class)
        .invoke(null, logRemoveFinal),
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

  public static boolean isRemoveFinalActive() {
    return removeFinalActive;
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
