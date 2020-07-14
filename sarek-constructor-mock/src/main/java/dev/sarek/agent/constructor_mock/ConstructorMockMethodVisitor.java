package dev.sarek.agent.constructor_mock;

import dev.sarek.agent.Transformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.utility.visitor.LocalVariableAwareMethodVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static net.bytebuddy.jar.asm.Opcodes.*;

public class ConstructorMockMethodVisitor extends LocalVariableAwareMethodVisitor {
  private static final int ASM_API_VERSION = ASM8;
  private static final String CONSTRUCTOR_MOCK_REGISTRY = "dev/sarek/agent/constructor_mock/ConstructorMockRegistry";

  private final String className;
  private final boolean shouldTransform;
  private final String superClassNameJVM;
  private final String superConstructorSignatureJVM;
  private final List<String> superConstructorParameterTypes;
  private final Type type;

  public ConstructorMockMethodVisitor(
    TypeDescription instrumentedType,
    MethodVisitor methodVisitor,
    MethodDescription methodDescription
  )
  {
    // TODO: ASM_API_VERSION is ASM8, ByteBuddy also uses ASM8 -> check if it can be synchronised
    // super(ASM_API_VERSION, methodVisitor);
    super(methodVisitor, methodDescription);

    type = Type.getType(instrumentedType.getDescriptor());
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

    // First free offset in local variable table
    final int freeOffset = getFreeOffset();

    // Define labels we want to insert as markers or jump targets later
    Label labelMockCode = new Label();
    Label labelOriginalCode = new Label();
    Label labelReturn = new Label();

    // Constructor mock code starts here
    super.visitLabel(labelMockCode);

    // If class for instance under construction is registered for constructor mocking, call super constructor with
    // dummy values (null, 0, false), otherwise jump to original code
    super.visitLdcInsn(type);
    super.visitMethodInsn(INVOKESTATIC, CONSTRUCTOR_MOCK_REGISTRY, "isMockUnderConstruction", "(Ljava/lang/Class;)I", false);
    final int mockUnderConstructionResult = freeOffset;
    super.visitVarInsn(ISTORE, mockUnderConstructionResult);
    super.visitVarInsn(ILOAD, mockUnderConstructionResult);
    super.visitInsn(ICONST_0);
    super.visitJumpInsn(IF_ICMPLE, labelOriginalCode);

    // Push uninitialised 'this' onto the stack as first super constructor parameter
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

    // If we are in the top-most constructor (mockUnderConstructionResult == 1), register mock via
    // ConstructorMockRegistry.registerMockInstance(this)
    super.visitVarInsn(ILOAD, mockUnderConstructionResult);
    super.visitInsn(ICONST_1);
    super.visitJumpInsn(IF_ICMPNE, labelReturn);
    // Now after super constructor was called, 'this' is initialised and can be used normally
    super.visitVarInsn(ALOAD, 0);
    super.visitMethodInsn(INVOKESTATIC, CONSTRUCTOR_MOCK_REGISTRY, "registerMockInstance", "(Ljava/lang/Object;)V", false);

    // Skip original constructor code by RETURN
    super.visitLabel(labelReturn);
    super.visitInsn(RETURN);

    // Original constructor code starts here
    super.visitLabel(labelOriginalCode);
  }

  public boolean shouldTransform() {
    return Transformer.shouldTransform(className);
  }

}
