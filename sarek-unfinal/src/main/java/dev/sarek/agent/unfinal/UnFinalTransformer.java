package dev.sarek.agent.unfinal;

import dev.sarek.agent.Transformer;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import static dev.sarek.agent.AgentRegistry.AGENT_REGISTRY;
import static net.bytebuddy.jar.asm.Opcodes.ASM8;

public class UnFinalTransformer extends ClassVisitor {
  /*
    Formerly it was: PARSING_FLAGS = SKIP_DEBUG | SKIP_FRAMES.
    This worked flawlessly for all application classes, but when activating group filters in Maven Surefire,
    it suddenly broke:

    java.lang.VerifyError: Expecting a stackmap frame at branch target 23
    Exception Details:
      Location:
        org/apache/maven/surefire/group/parse/GroupMatcherParser.group()
          Lorg/apache/maven/surefire/group/match/GroupMatcher; @14: ifeq
      Reason:
        Expected stackmap frame at this location.

    So we parse the complete class with stackmap frames, debug info and all. This is slower, but less error-prone.
    Better safe than sorry.
  */
  public final static int PARSING_FLAGS = 0;

  private final static String LOG_PREFIX = AGENT_REGISTRY.isRegistered(UnFinalAgent.class)
    ? "[UnFinal Agent] "
    : "[UnFinal Transformer] ";

  // TODO: make log level configurable
  private boolean logUnFinal;
  private String className;

  public static ClassFileTransformer createTransformer(boolean logUnFinal) {
    return new ClassFileTransformer() {
      @Override
      public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
      )
      {
        ClassReader classReader = new ClassReader(classfileBuffer);
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        classReader.accept(new UnFinalTransformer(classWriter, logUnFinal), PARSING_FLAGS);
        return classWriter.toByteArray();
      }
    };
  }

  public UnFinalTransformer(ClassVisitor cv, boolean logUnFinal) {
    super(ASM8, cv);
    this.logUnFinal = logUnFinal;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    className = name.replace('/', '.');
    if (!shouldTransform()) {
      cv.visit(version, access, name, signature, superName, interfaces);
      return;
    }
    if (logUnFinal && (access & Modifier.FINAL) != 0)
      log("Removing final from class " + className);
    cv.visit(version, access & ~Modifier.FINAL, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    if (!shouldTransform())
      return super.visitMethod(access, name, desc, signature, exceptions);
    if (logUnFinal && (access & Modifier.FINAL) != 0) {
      log("Removing final from method " + className + "." + name + desc);
    }
    return super.visitMethod(access & ~Modifier.FINAL, name, desc, signature, exceptions);
  }

  /**
   * TODO: make include/exclude list of class and package names configurable
   */
//  @Override
  public boolean shouldTransform() {
    return Transformer.shouldTransform(className);
  }

  private void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

}
