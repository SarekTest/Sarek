package dev.sarek.agent;

public abstract class Transformer {
  public static boolean shouldTransform(String className) {
    // TODO: make include/exclude list of class and package names configurable

    // Default exclude list for transformation
    return
      // Sarek classes
      !className.startsWith("dev.sarek.")
        // The JVM does not tolerate definalisation of Object methods but says:
        //   Error occurred during initialization of VM
        //   Incompatible definition of java.lang.Object
        && !className.equals("java.lang.Object")
        // Byte code engineering
        && !className.startsWith("net.bytebuddy.")
        && !className.startsWith("org.objectweb.asm.")
        && !className.startsWith("groovyjarjarasm.asm.")
        && !className.startsWith("javassist.")
        && !className.startsWith("org.objenesis.")
        && !className.contains("$$EnhancerByCGLIB$$")
        // Testing
        && !className.startsWith("org.junit.")
        && !className.startsWith("junit.")
        && !className.startsWith("org.hamcrest.")
        && !className.startsWith("org.spockframework.")
        && !className.startsWith("spock.")
        // Mocking
        && !className.startsWith("org.mockito.")
        && !className.startsWith("mockit.")
        && !className.startsWith("org.powermock.")
        && !className.startsWith("org.easymock.")
        // Build
        && !className.startsWith("org.apache.maven.")
        // IDE
        && !className.startsWith("com.intellij.")
      ;
  }
}
