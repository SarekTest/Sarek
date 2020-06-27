package dev.sarek.agent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

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
  private static boolean unFinalActive;
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
    unFinalActive = options.contains("unfinal");
    mockActive = options.contains("mock");
    aspectActive = mockActive || options.contains("aspect");
    constructorMockActive = mockActive || options.contains("constructormock");
  }

  private static void appendJarsToBootstrapClassLoaderSearch() throws IOException, URISyntaxException, ReflectiveOperationException {
    // TODO: implement logic of optional JARs depending on command line options
    // Multiple agents could have been shaded into the same JAR file -> use a set of canonical file names
    Set<String> jarFileNames = new HashSet<>();

    // ByteBuddy agent - TODO: check for relocated Sarek name too
    jarFileNames.add(findJarFile("net/bytebuddy/agent/ByteBuddyAgent.class"));

    if (unFinalActive) {
      // Sarek unfinal
      jarFileNames.add(findJarFile("dev/sarek/agent/unfinal/UnFinalTransformer.class"));
      // ByteBuddy ASM - TODO: check for relocated Sarek name too
      jarFileNames.add(findJarFile("net/bytebuddy/jar/asm/ClassVisitor.class"));
      // Sarek agent common
      jarFileNames.add(findJarFile("dev/sarek/agent/Agent.class"));
    }
    if (mockActive) {
      // Sarek mock
      jarFileNames.add(findJarFile("dev/sarek/agent/mock/MockFactory.class"));
      // Objenesis - TODO: check for relocated Sarek name too
      jarFileNames.add(findJarFile("org/objenesis/ObjenesisStd.class"));
      // ByteBuddy - TODO: check for relocated Sarek name too
      jarFileNames.add(findJarFile("net/bytebuddy/matcher/ElementMatcher.class"));
    }
    if (aspectActive) {
      // Sarek aspect
      jarFileNames.add(findJarFile("dev/sarek/agent/aspect/Weaver.class"));
      // ByteBuddy - TODO: check for relocated Sarek name too
      jarFileNames.add(findJarFile("net/bytebuddy/asm/Advice.class"));
      // Sarek common
      jarFileNames.add(findJarFile("dev/sarek/agent/util/BiMultiMap.class"));
      // Sarek agent commons
      jarFileNames.add(findJarFile("dev/sarek/agent/Agent.class"));
    }
    if (constructorMockActive) {
      // Sarek constructor mock
      jarFileNames.add(findJarFile("dev/sarek/agent/constructor_mock/ConstructorMockTransformer.class"));
      // ByteBuddy - TODO: check for relocated Sarek name too
      jarFileNames.add(findJarFile("net/bytebuddy/matcher/ElementMatcher.class"));
      // Sarek common
      jarFileNames.add(findJarFile("dev/sarek/agent/util/BiMultiMap.class"));
      // Sarek agent common
      jarFileNames.add(findJarFile("dev/sarek/agent/Agent.class"));
    }

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

    if (unFinalActive)
      attachUnFinalTransformer(verbose);
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
      Map<String, File> mappedJarFiles = Arrays
        .stream(
          Objects.requireNonNull(
            targetDir.listFiles((dir, name) ->
              // TODO: "-all" or "all-special" -> how to decide?
              name.endsWith(".jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-sources.jar")
            )
          )
        )
        .collect(
          Collectors.toMap(
            file -> file.getName().replaceFirst(".+?(-all(-special)?)?\\.jar", "$1"),
            Function.identity()
          )
        );
      jarFile = mappedJarFiles.get("-all.jar");
      if (jarFile == null)
        jarFile = mappedJarFiles.get("");
      if (jarFile == null)
        jarFile = mappedJarFiles.get("-all-special");
      if (jarFile == null)
        throw new FileNotFoundException(
          "Cannot find JAR file containing resource " + ressourcePath + " in directory " + targetDir
        );
    }
    else {
      jarFile = new File(new URL(resourceURL).toURI());
    }
    if (verbose)
      System.out.println(AGENT_PREFIX + "Found resource JAR file: " + jarFile);
    return jarFile.getCanonicalPath();
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
