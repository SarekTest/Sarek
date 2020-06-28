package dev.sarek.agent.constructor_mock;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static net.bytebuddy.jar.asm.Opcodes.*;

public class ConstructorMockMethodVisitor extends MethodVisitor {
  private static final int ASM_API_VERSION = ASM8;

  private final String className;
  private final boolean shouldTransform;
  private final String superClassNameJVM;
  private final String superConstructorSignatureJVM;
  private final List<String> superConstructorParameterTypes;

  public ConstructorMockMethodVisitor(TypeDescription instrumentedType, MethodVisitor mv) {
    super(ASM_API_VERSION, mv);

    className = instrumentedType.getTypeName();
    shouldTransform = shouldTransform();

    if (!shouldTransform) {
      superClassNameJVM = null;
      superConstructorSignatureJVM = null;
      superConstructorParameterTypes = new ArrayList<>();
      return;
    }

    MethodDescription.InGenericShape superConstructor = instrumentedType
      .getSuperClass()
      .getDeclaredMethods()
      .stream()
      .filter(MethodDescription::isConstructor)
      .min(Comparator.comparingInt(constructor -> constructor.getParameters().size()))
      .orElse(null);
    if (superConstructor == null)
      throw new IllegalArgumentException("Type " + instrumentedType + " has no super constructor");

    superClassNameJVM = superConstructor.getDeclaringType().getActualName().replace('.', '/');
    superConstructorSignatureJVM = superConstructor.getDescriptor();
    superConstructorParameterTypes = superConstructor
      .getParameters()
      .stream()
      .map(ParameterDescription::getType)
      .map(TypeDefinition::getTypeName)
      .collect(Collectors.toList());
  }

  @Override
  public void visitCode() {
    if (!shouldTransform)
      return;

    // Constructor mock code starts here
    Label labelMockCode = new Label();
    super.visitLabel(labelMockCode);

    // If class for instance under construction is registered for constructor mocking, call super constructor with
    // dummy values (null, 0, false), otherwise jump to original code
    super.visitMethodInsn(INVOKESTATIC, "dev/sarek/agent/constructor_mock/ConstructorMockRegistry", "isMockUnderConstruction", "()Z", false);
    Label labelOriginalCode = new Label();
    super.visitJumpInsn(IFEQ, labelOriginalCode);

    // Push uninitialised 'this' onto the stack
    super.visitVarInsn(ALOAD, 0);

    // Push dummy values for all super constructor parameters onto the stack
    for (String parameterTypeName : superConstructorParameterTypes) {
      switch (parameterTypeName) {
        case "byte":
          super.visitInsn(ICONST_0);
          super.visitInsn(I2B);
          break;
        case "char":
          super.visitInsn(ICONST_0);
          super.visitInsn(I2C);
          break;
        case "double":
          super.visitInsn(DCONST_0);
          break;
        case "float":
          super.visitInsn(FCONST_0);
          break;
        case "int":
          super.visitInsn(ICONST_0);
          break;
        case "long":
          super.visitInsn(LCONST_0);
          break;
        case "short":
          super.visitInsn(ICONST_0);
          super.visitInsn(I2S);
          break;
        case "boolean":
          super.visitInsn(ICONST_0);
          break;
        // Reference types, arrays -> null
        default:
          super.visitInsn(ACONST_NULL);
      }
    }

    // Invoke super constructor
    super.visitMethodInsn(INVOKESPECIAL, superClassNameJVM, "<init>", superConstructorSignatureJVM, false);
    // Skip original constructor code
    super.visitInsn(RETURN);

    // Original constructor code starts here
    super.visitLabel(labelOriginalCode);
  }


  /**
   * TODO: make include/exclude list of class and package names configurable
   */
  public boolean shouldTransform() {
    return shouldTransform(className);
  }

  public static boolean shouldTransform(String className) {
    // Default exclude list for transformation
    return
      // Our own agent-related stuff
      !className.startsWith("dev.sarek.agent.")
        && !className.startsWith("dev.sarek.jar")
        // Object has no super class -> no super constructor -> exclude from transformation
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
