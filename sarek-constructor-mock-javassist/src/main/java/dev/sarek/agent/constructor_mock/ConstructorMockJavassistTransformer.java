package dev.sarek.agent.constructor_mock;

import dev.sarek.agent.Transformer;
import javassist.*;
import net.bytebuddy.jar.asm.*;

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
 * TODO: implement configurability
 */
public class ConstructorMockJavassistTransformer implements ClassFileTransformer {
  // TODO: make log level configurable
  public static boolean LOG_CONSTRUCTOR_MOCK = false;
  // TODO: make class file dumping configurable
  public static boolean DUMP_CLASS_FILES = false;
  public static String DUMP_CLASS_BASE_DIR = "constructor-mock-transform-javassist";

  private static final String LOG_PREFIX = "[Javassist Constructor Mock Transformer] ";
  private static final String MOCK_REGISTRY = ConstructorMockRegistry.class.getName();

  private ClassPool classPool = ClassPool.getDefault();
  private File configFile;
  private final Set<String> targetClasses;

  /**
   * Default constructor used by static transformer which injects its configuration via
   * {@link #configure(Properties)}
   */
  @SuppressWarnings("unused")
  public ConstructorMockJavassistTransformer() {
    this((File) null);
  }

  public ConstructorMockJavassistTransformer(String... targetClasses) {
    this.targetClasses = Arrays
      .stream(targetClasses)
      .collect(Collectors.toSet());
  }

  public ConstructorMockJavassistTransformer(Class<?>... targetClasses) {
    this.targetClasses = Arrays
      .stream(targetClasses)
      .map(Class::getName)
      .collect(Collectors.toSet());
  }

  public ConstructorMockJavassistTransformer(Set<Class<?>> targetClasses) {
    this.targetClasses = targetClasses
      .stream()
      .map(Class::getName)
      .collect(Collectors.toSet());
  }

  /**
   * Constructor used by agent transformer which injects its configuration via
   * configuration properties file
   */
  public ConstructorMockJavassistTransformer(File configFile) {
    this.targetClasses = null;
    this.configFile = configFile;
//    URL url = getClass().getClassLoader().getResource(configFile);
//    new File()
//    new Properties().
  }

  public boolean hasTargetClasses() {
    return targetClasses != null;
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

    // TODO: remove after fix for https://github.com/jboss-javassist/javassist/issues/328 is released
    final boolean REPAIR = false;
    if (REPAIR)
      transformedBytecode = repairStackMapUsingASM(className, transformedBytecode);

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

  private byte[] repairStackMapUsingASM(String className, byte[] transformedBytecode) {
    if (DUMP_CLASS_FILES) {
      Path path = new File(DUMP_CLASS_BASE_DIR + "/" + className + ".unrepaired.class").toPath();
      try {
        Files.createDirectories(path.getParent());
        log("Dumping (unrepaired) transformed class file " + path.toAbsolutePath());
        Files.write(path, transformedBytecode);
      }
      catch (Exception e) {
        log("ERROR: Cannot write (unrepaired) class file to " + path.toAbsolutePath());
        e.printStackTrace();
      }
    }

    // Repair stack map frames via ASM
    ClassReader classReader = new ClassReader(transformedBytecode);

    // Directly passing the writer to the reader leads to re-ordering of the constant pool table. This is not a
    // problem with regard to functionality as such, but more difficult to diff when comparing the 'javap' output with
    // the corresponding result created directly via ASM.
    //
    //   ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    //   classReader.accept(classWriter, ClassReader.SKIP_FRAMES);
    //
    // So we use this slightly more complicated method which copies the original constant pool, new entries only being
    // appended to it as needed. Solution taken from https://stackoverflow.com/a/46644677/1082681.
    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
    classReader.accept(
      new ClassVisitor(Opcodes.ASM5, classWriter) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
          MethodVisitor writer = super.visitMethod(access, name, desc, signature, exceptions);
          return new MethodVisitor(Opcodes.ASM5, writer) {};
        }
      },
      ClassReader.SKIP_FRAMES
    );

    return classWriter.toByteArray();
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
        "{",
        "  int constructorStackDepth = " + MOCK_REGISTRY + "#isMockUnderConstruction();",
        "  if (constructorStackDepth > 0) {",
        "    " + superCall,
        "    if (constructorStackDepth == 1) {",
        "      " + MOCK_REGISTRY + "#registerMockInstance($0);",
        "    }",
        "    return;",
        "  }",
        "}"
      );
      if (LOG_CONSTRUCTOR_MOCK)
        log(ifCondition);
      ctConstructor.insertBefore(ifCondition);
    }
  }

  private String getSuperCall(CtClass superClass) throws NotFoundException {
    // Get declared non-private constructors (we cannot call private ones via 'super()')
    // TODO: Maybe transform private constructors, too -> '.getInstance()' methods in typical singleton classes!
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

  public boolean shouldTransform(String className) {
    // TODO: Which include/exclude method is better? A gives more power to the user, but is also more dangerous.

    // (A) If there is a target class list, ignore the global ignore list
    // if (hasTargetClasses())
    //   return classNames.contains(className);

    // (B) If there is a target class list and a class is on it, still exclude it if it is on the ignore list too
    if (hasTargetClasses() && !targetClasses.contains(className))
      return false;

    // Default exclude list for transformation
    return Transformer.shouldTransform(className);
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
