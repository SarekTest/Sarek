package dev.sarek.agent.aspect;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
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
public class AspectAgent {
  private static boolean active;
  private static boolean removeFinalActive;
  private static boolean logRemoveFinal;
  private static boolean constructorMockActive;
  private static Instrumentation instrumentation;

  public static void agentmain(String options, Instrumentation instr) throws Exception {
    premain(options, instr);
  }

  public static void premain(String options, Instrumentation instr) throws Exception {
    // TODO: remove after re-testing fix of https://issues.apache.org/jira/browse/SUREFIRE-1788
    System.out.println("[Aspect Agent] premain - options = " + options);

    File aspectJar = findJarFile("dev/sarek/agent/aspect/Weaver.class");
//    System.out.println("Aspect JAR: " + aspectJar);
    instr.appendToBootstrapClassLoaderSearch(new JarFile(aspectJar));
    File constructorMockJar = findJarFile("dev/sarek/agent/constructor_mock/ConstructorMockTransformer.class");
//    System.out.println("Constructor mock JAR: " + constructorMockJar);
    instr.appendToBootstrapClassLoaderSearch(new JarFile(constructorMockJar));
    // TODO: make more generic
//    instr.appendToBootstrapClassLoaderSearch(new JarFile("C:/Users/alexa/Documents/java-src/TestHelperAgents/constructor-mock-parent/constructor-mock-agent/target/constructor-mock-agent-1.0-SNAPSHOT-all.jar"));

    instrumentation = instr;
    active = true;

    // TODO: document how to use '-javaagent:my.jar=removeFinal' -> Javadoc, read-me
    removeFinalActive = options != null && options.trim().toLowerCase().contains("removefinal");
    // TODO: document how to use '-javaagent:my.jar=verbose' -> Javadoc, read-me
    logRemoveFinal = options != null && options.trim().toLowerCase().contains("verbose");
    if (removeFinalActive)
      attachRemoveFinalTransformer(logRemoveFinal);

    // TODO: document how to use '-javaagent:my.jar=constructorMock' -> Javadoc, read-me
    constructorMockActive = options != null && options.trim().toLowerCase().contains("constructormock");
    if (constructorMockActive)
      attachConstructorMockTransformer(logRemoveFinal);  // TODO: separate setting 'logConstructorMock'
  }

  // TODO: optionally pack shaded JARs (all vs. all-special) into agent JAR, unpack and attach
  //       if not found in file system or on classpath
  private static File findJarFile(String ressourcePath) throws IOException, URISyntaxException {
    String resourceURL = ClassLoader
      .getSystemResource(ressourcePath)
      .getPath()
      .replaceFirst("!.*", "");
//    System.out.println("resourceURL = " + resourceURL);
    File file;
    if (resourceURL.contains("/target/classes/")) {
      // Try to fix the phenomenon that in IntelliJ IDEA when running the test via run configuration,
      // the runner insists on referring to the 'aspect-core' module locally via 'target/classes'
      // instead of to the JAR in the local Maven repository or in the module's 'target' directory.
      File targetDir = new File(resourceURL.replaceFirst("(/target)/classes/.*", "$1"));
      File[] candidateJars = targetDir.listFiles((dir, name) ->
        // TODO: "-all" or "all-special" -> how to decide?
        name.endsWith("-all.jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-sources.jar")
      );
      if (candidateJars == null || candidateJars.length == 0)
        throw new FileNotFoundException(
          "Cannot find JAR file containing resource " + ressourcePath + " in directory " + targetDir
        );
      file = candidateJars[0];
    }
    else {
      file = new File(new URL(resourceURL).toURI());
    }
//    System.out.println("[Aspect Agent] Found resource JAR file: " + file);
    return file;
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

  private static void attachConstructorMockTransformer(boolean logConstructorMock) throws ReflectiveOperationException {
    instrumentation.addTransformer(
      // new ConstructorMockTransformer(configFile)
      (ClassFileTransformer) Class
        .forName("dev.sarek.agent.constructor_mock.ConstructorMockTransformer")
        .getDeclaredConstructor()
        .newInstance(), // TODO: pass through 'logConstructorMock'
      true
    );
  }

  public static boolean isActive() {
    return active;
  }

  public static boolean isRemoveFinalActive() {
    return removeFinalActive;
  }

  public static Instrumentation getInstrumentation() {
    return instrumentation;
  }

}
