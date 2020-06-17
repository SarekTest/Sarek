package dev.sarek.agent.constructor_mock;

import javassist.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class transformer injects code into constructors, optionally stopping the original code  from being executed
 * and thus avoiding any unwanted (and possibly expensive) side effects such as resource allocation. The result of
 * calling a constructor for a class which has been added to the {@link ConstructorMockRegistry} will be an uninitialised
 * object, i.e. all its instance fields have default values such as {@code null</code>, <code>0},
 * {@code false}. Such an object can then be used as the basis for a mock by stubbing its methods by another byte
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
public class ConstructorMockTransformer implements ClassFileTransformer {
  // TODO: make log level configurable
  public static boolean LOG_CONSTRUCTOR_MOCK = false;
  // TODO: make class file dumping configurable
  public static boolean DUMP_CLASS_FILES = false;
  public static String DUMP_CLASS_BASE_DIR = "constructor-mock-transform";

  private static final String MOCK_REGISTRY = ConstructorMockRegistry.class.getName();

  private final String LOG_PREFIX = ConstructorMockAgent.isActive()
    ? "[Constructor Mock Agent] "
    : "[Constructor Mock Transformer] ";

  private ClassPool classPool = ClassPool.getDefault();
  private File configFile;
  private final Set<String> classWhiteList;

  /**
   * Default constructor used by static transformer which injects its configuration via
   * {@link #configure(Properties)}
   */
  @SuppressWarnings("unused")
  public ConstructorMockTransformer() {
    this((File) null);
  }

  public ConstructorMockTransformer(String... classWhiteList) {
    this.classWhiteList = Arrays
      .stream(classWhiteList)
      .collect(Collectors.toSet());
  }

  public ConstructorMockTransformer(Class<?>... classWhiteList) {
    this.classWhiteList = Arrays
      .stream(classWhiteList)
      .map(Class::getName)
      .collect(Collectors.toSet());
  }

  public ConstructorMockTransformer(Set<Class<?>> classWhiteList) {
    this.classWhiteList = classWhiteList
      .stream()
      .map(Class::getName)
      .collect(Collectors.toSet());
  }

  /**
   * Constructor used by agent transformer which injects its configuration via
   * configuration properties file
   */
  public ConstructorMockTransformer(File configFile) {
    this.classWhiteList = null;
    this.configFile = configFile;
//    URL url = getClass().getClassLoader().getResource(configFile);
//    new File()
//    new Properties().
  }

  public boolean hasClassWhiteList() {
    return classWhiteList != null;
  }

  @Override
  public byte[] transform(
    ClassLoader loader,
    String className,
    Class<?> classBeingRedefined,
    ProtectionDomain protectionDomain,
    byte[] classfileBuffer
  )
  {
    String canonicalClassName = className.replace('/', '.');
    if (!shouldTransform(canonicalClassName))
      return null;

    CtClass targetClass;
    try {
      // Caveat: Do not just use 'classPool.get(className)' because we would miss previous transformations.
      // It is necessary to really parse 'classfileBuffer'.
      targetClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
    }
    catch (Exception e) {
      log("ERROR: Cannot parse bytes for input class " + canonicalClassName);
      e.printStackTrace();
      return null;
    }

    try {
      applyTransformations(targetClass);
    }
    catch (Exception e) {
      log("ERROR: Cannot apply transformations to input class " + canonicalClassName);
      e.printStackTrace();
      return null;
    }

    byte[] transformedBytecode;
    try {
      transformedBytecode = targetClass.toBytecode();
    }
    catch (Exception e) {
      log("ERROR: Cannot get byte code for transformed class " + canonicalClassName);
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
      catch (Exception e) {
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
  private void makeConstructorsMockable(CtClass targetClass)
    throws NotFoundException, CannotCompileException
  {
    String canonicalClassName = targetClass.getName().replace('/', '.');
    CtClass superClass = classPool.getCtClass(canonicalClassName).getSuperclass();
    String superCall = getSuperCall(superClass);
    for (CtConstructor ctConstructor : targetClass.getDeclaredConstructors()) {
      if (LOG_CONSTRUCTOR_MOCK)
        log("Adding constructor mock capability to constructor " + ctConstructor.getLongName());
      String ifCondition = String.join("\n",
        // Original approach
/*
        "if (dev.sarek.agent.constructor_mock.ConstructorMockRegistry.isObjectInConstructionMock()) {",
        "  " + superCall,
        "  return;",
        "}"
*/

        // Alternative idea by Björn Kautler without stack trace generation + parsing:
        "{",
        "  if (" + MOCK_REGISTRY + ".isMockInCreation()) {",
        "    " + superCall,
        "    return;",
        "  }",
        "  if (" + MOCK_REGISTRY + ".isMock(\"" + ctConstructor.getDeclaringClass().getName() + "\")) {",
        "    " + MOCK_REGISTRY + ".setMockInCreation(true);",
        "    " + superCall,
        "    " + MOCK_REGISTRY + ".setMockInCreation(false);",
        "    return;",
        "  }",
        "}"
      );
      String catchClause = String.join("\n",
        "{",
        "  " + MOCK_REGISTRY + ".setMockInCreation(false);",
        "  throw $e;",
        "}"
      );
      if (LOG_CONSTRUCTOR_MOCK) {
        log(ifCondition);
        log(catchClause);
      }
      ctConstructor.insertBefore(ifCondition);
      // TODO: reactivate/refactor after knowing the result of https://github.com/jboss-javassist/javassist/issues/325
       ctConstructor.addCatch(catchClause, classPool.get("java.lang.Throwable"));
    }
  }

  private String getSuperCall(CtClass superClass) throws NotFoundException {
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
   */
  public boolean shouldTransform(final CtClass candidateClass) {
    return shouldTransform(candidateClass.getName());
  }

  /**
   * TODO: make black/white list of class and package names configurable
   */
  public boolean shouldTransform(String className) {

    // TODO: Which white-listing method is better? A gives more power to the user, but is also more dangerous.

    // (A) If there is a white list, ignore the global black list
    // if (hasClassWhiteList())
    //   return classNames.contains(className);

    // (B) If there is a white list and a class is on it, still exclude it if it is on the black list too
    if (hasClassWhiteList() && !classWhiteList.contains(className))
      return false;

    // Default exclude list for transformation
    return
      // Our own agent-related stuff
      !className.startsWith("dev.sarek.agent.")
        && !className.startsWith("dev.sarek.jar")
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
  public void applyTransformations(CtClass targetClass) {
    targetClass.defrost();
    try {
      makeConstructorsMockable(targetClass);
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot make constructors mockable for class " + targetClass.getName(), e);
    }
    targetClass.detach();
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
