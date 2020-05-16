package de.scrum_master.bytebuddy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * TODO:
 *   - In order to avoid possible collisions with clients using BB in a conflicting
 *     version already, I could relocate the BB classes (including ASM) to a
 *     separate base package such as de.scrum_master.jar and exclude that package
 *     from both definalisation and aspect weaving.
 *   - A fat JAR version of the wrapper agent could contain the aspect framework
 *     JAR as a resource and unpack it upon start-up. This would make both JAR
 *     detection more reliable and also relieve the user from having to add the
 *     aspect framework to her class path.
 */
public class ByteBuddyAspectAgent {
  private static boolean active;
  private static boolean removeFinalActive;
  private static Instrumentation instrumentation;

  public static void agentmain(String options, Instrumentation instr) throws Exception {
    premain(options, instr);
  }

  public static void premain(String options, Instrumentation instr) throws Exception {
    File transformerJar = findJarFile("de/scrum_master/bytebuddy/aspect/Weaver.class");
    instr.appendToBootstrapClassLoaderSearch(new JarFile(transformerJar));

    // TODO: Why is this this necessary in order to avoid a ClassCircularityError in ByteBuddy?
    ClassLoader
      .getSystemClassLoader()
      .getParent()
      .loadClass("java.lang.reflect.InvocationTargetException");

    instrumentation = instr;
    active = true;
    // TODO: document how to use '-javaagent:my.jar=removeFinal' -> Javadoc, read-me
    removeFinalActive = options != null && options.trim().toLowerCase().contains("removefinal");
    if (removeFinalActive)
      attachRemoveFinalTransformer();
  }

  // TODO: optionally pack both JARs into agent JAR, unpack and attach if not found in file system or on classpath

  private static File findJarFile(String ressourcePath) throws IOException, URISyntaxException {
    String resourceURL = ClassLoader
      .getSystemResource(ressourcePath)
      .getPath()
      .replaceFirst("!.*", "");

    File file;
    if (resourceURL.contains("/target/classes/")) {
      // Try to fix the phenomenon that in IntelliJ IDEA when running the test via run configuration,
      // the runner insists on referring to the 'bytebuddy-aspect' module locally via 'target/classes'
      // instead of to the JAR in the local Maven repository or in the module's 'target' directory.
      File targetDir = new File(resourceURL.replaceFirst("(/target)/classes/.*", "$1"));
      File[] candidateJars = targetDir.listFiles((dir, name) ->
        name.endsWith(".jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-sources.jar")
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
//    System.out.println("Found resource JAR file: " + file);
    return file;
  }

  private static void attachRemoveFinalTransformer() throws ReflectiveOperationException {
    Class
      .forName("de.scrum_master.agent.RemoveFinalTransformer")
      .getDeclaredMethod("install", Instrumentation.class)
      .invoke(null, instrumentation);
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
