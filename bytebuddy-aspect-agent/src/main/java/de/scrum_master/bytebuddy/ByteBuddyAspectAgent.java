package de.scrum_master.bytebuddy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarFile;

public class ByteBuddyAspectAgent {
  private static boolean active;
  private static Instrumentation instrumentation;

  public static void agentmain(String options, Instrumentation instr)
    throws IOException, URISyntaxException {
    premain(options, instr);
  }

  public static void premain(String options, Instrumentation instr)
    throws IOException, URISyntaxException {
    instr.appendToBootstrapClassLoaderSearch(findJarFile("de/scrum_master/bytebuddy/aspect/Aspect.class"));
    instr.appendToBootstrapClassLoaderSearch(findJarFile("net/bytebuddy/agent/builder/AgentBuilder.class"));
    instrumentation = instr;
    active = true;
  }

  // TODO: optionally pack both JARs into agent JAR, unpack and attach if not found in file system or on classpath
  private static JarFile findJarFile(String ressourcePath) throws IOException, URISyntaxException {
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
    return new JarFile(file);
  }

  public static boolean isActive() {
    return active;
  }

  public static Instrumentation getInstrumentation() {
    return instrumentation;
  }
}
