package de.scrum_master.agent.global_mock;

import javassist.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class transformer injects code into constructors, optionally stopping the original code  from being executed
 * and thus avoiding any unwanted (and possibly expensive) side effects such as resource allocation. The result of
 * calling a constructor for a class which has been added to the {@link GlobalMockRegistry} will be an uninitialised
 * object, i.e. all its instance fields have default values such as <code>null</code>, <code>0</code>,
 * <code>false</code>. Such an object can then be used as the basis for a mock by stubbing its methods by another byte
 * code instrumentation stage.
 * <p></p>
 * The class also has been prepared to be used from Maven by providing methods which a class derived from
 * {@link de.icongmbh.oss.maven.plugin.javassist.ClassTransformer} can delegate to. This enables build time bytecode
 * instrumentation, if necessary.
 * <p></p>
 *
 * @see <a href="https://github.com/icon-Systemhaus-GmbH/javassist-maven-plugin">Javassist Maven Plugin</a>
 * <p></p>
 * TODO: implement configurability
 */
public class GlobalMockTransformer implements ClassFileTransformer {

  // TODO: make log level configurable
  public static boolean LOG_GLOBAL_MOCK = false;
  // TODO: make class file dumping configurable
  public static boolean DUMP_CLASS_FILES = true;
  public static String DUMP_CLASS_BASE_DIR = "global-mock-transform";

  private final String LOG_PREFIX = GlobalMockAgent.isActive()
    ? "[Global Mock Agent] "
    : "[Global Mock Transformer] ";

  private ClassPool classPool = ClassPool.getDefault();
  private String configFile;

  /**
   * Default constructor used by static transformer which injects its configuration via
   * {@link #configure(Properties)}
   */
  @SuppressWarnings("unused")
  public GlobalMockTransformer() {
    this(null);
  }

  /**
   * Constructor used by agent transformer which injects its configuration via
   * configuration properties file
   */
  public GlobalMockTransformer(String configFile) {
    this.configFile = configFile;
//    URL url = getClass().getClassLoader().getResource(configFile);
//    new File()
//    new Properties().
  }

  @Override
  public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
    CtClass ctClass;
    try {
      // Caveat: Do not just use 'classPool.get(className.replaceAll("/", "."))' because we would miss
      // previous transformations. It is necessary to really parse 'classfileBuffer'.
      ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
    }
    catch (IOException e) {
      return null;
    }
    if (!shouldTransform(ctClass))
      return null;
    applyTransformations(ctClass);
    byte[] transformedBytecode;
    try {
      transformedBytecode = ctClass.toBytecode();
    }
    catch (IOException | CannotCompileException e) {
      log("ERROR: Cannot get byte code for transformed class " + className);
      e.printStackTrace();
      return null;
    }
    if (DUMP_CLASS_FILES) {
      Path path = new File(DUMP_CLASS_BASE_DIR + "/" + className + ".class").toPath();
      try {
        Files.createDirectories(path.getParent());
        log("Dumping transformed class file " + path.toAbsolutePath());
        Files.write(path, transformedBytecode);
      }
      catch (IOException e) {
        log("ERROR: Cannot write class file to " + path.toAbsolutePath());
        e.printStackTrace();
      }
    }
    return transformedBytecode;
  }

  // TODO: This only works if all classes are being transformed via class-loading. Implement recursive manual mode which
  //       does not require the user to retransform parent classes by himself. For that purpose but also generally, it
  //       would be good to also have a registry of already transformed classes (per classloader?) so as to avoid
  //       multiple transformations of the same constructor.
  private void makeGloballyMockable(CtClass ctClass) throws NotFoundException, CannotCompileException {
    if (LOG_GLOBAL_MOCK)
      log("Adding global mock capability to class " + ctClass.getName());
    String superCall = getSuperCall(ctClass);
    for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
      if (LOG_GLOBAL_MOCK)
        log("Adding global mock capability to constructor " + ctConstructor.getLongName());
      ctConstructor.insertBefore(
        String.join("\n",
          "if (de.scrum_master.agent.global_mock.GlobalMockRegistry.isObjectInConstructionMock()) {",
          "  " + superCall,
          "  return;",
          "}"
        )
      );
      // Alternative idea by Björn Kautler without stack trace generation + parsing:
/*
      if (GlobalMockRegistry.isCurrentlyInitializingMockThreadLocal()) {
        super(null, 0, false);
        return;
      }
      if (GlobalMockRegistry.isMock(Super.class)) {
        GlobalMockRegistry.setCurrentlyInitializingMockThreadLocal(true);
        try {
          super(null, 0, false);
          return;
        }
        finally {
          GlobalMockRegistry.setCurrentlyInitializingMockThreadLocal(false);
        }
      }
*/
    }
  }

  private String getSuperCall(CtClass childClass) throws NotFoundException {
    CtClass superClass = childClass.getSuperclass();
    // Get declared non-private constructors (we cannot call private ones via 'super()')
    // TODO: Maybe transform private constructors, too -> `.getInstance()` methods in typical singleton classes!
    CtConstructor[] superConstructors = superClass.getConstructors();
    assert superConstructors.length > 0
      : "There has to be at least one accessible super class constructor";
    // Use first super constructor found
    // TODO(?): use constructor with shortest parameter list (but we have to solve the general case anyway)
    CtConstructor superConstructor = superConstructors[0];
    String parameterList = Stream.of(superConstructor.getParameterTypes())
      .map(parameterType -> {
        switch (parameterType.getName()) {
          case "byte":
            return "(byte) 0";
          case "short":
            return "(short) 0";
          case "int":
            return "0";
          case "long":
            return "0L";
          case "float":
            return "0.0f";
          case "double":
            return "0.0d";
          case "char":
            return "(char) 0";
          case "boolean":
            return "false";
          default:
            return "null";
        }
      })
      .collect(Collectors.joining(", "));
    return "super(" + parameterList + ");";
  }

  private void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

  // ======================================================================

  private static final String APPEND_VALUE_PROPERTY_KEY = "append.value";

  private String appendValue;

  /**
   * This method is meant to be delegated to from a subclass of
   * {@link de.icongmbh.oss.maven.plugin.javassist.ClassTransformer} in order to enable build time bytecode
   * instrumentation.
   * <p></p>
   * TODO: make black/white list of class and package names configurable
   */
  public boolean shouldTransform(final CtClass candidateClass) {
    String className = candidateClass.getName();

    // Default exclude list for transformation
    return
      // Our own agent-related stuff
      !className.startsWith("de.scrum_master.agent.")
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
        && !className.startsWith("com.intellij.");
  }

  /**
   * This method is meant to be delegated to from a subclass of
   * {@link de.icongmbh.oss.maven.plugin.javassist.ClassTransformer} in order to enable build time bytecode
   * instrumentation.
   */
  public void applyTransformations(CtClass classToTransform) {
    classToTransform.defrost();
    try {
      makeGloballyMockable(classToTransform);
    }
    catch (NotFoundException | CannotCompileException e) {
      e.printStackTrace();
    }
    classToTransform.detach();
  }

  /**
   * This method is meant to be delegated to from a subclass of
   * {@link de.icongmbh.oss.maven.plugin.javassist.ClassTransformer} in order to enable build time bytecode
   * instrumentation.
   * <p></p>
   * TODO: implement configurability
   */
  public void configure(final Properties properties) {
    if (null == properties) {
      return;
    }
    this.appendValue = properties.getProperty(APPEND_VALUE_PROPERTY_KEY);
  }

}
