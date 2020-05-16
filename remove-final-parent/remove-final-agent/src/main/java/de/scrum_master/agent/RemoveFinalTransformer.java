package de.scrum_master.agent;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;

import static net.bytebuddy.jar.asm.ClassReader.SKIP_DEBUG;
import static net.bytebuddy.jar.asm.ClassReader.SKIP_FRAMES;
import static net.bytebuddy.jar.asm.Opcodes.ASM8;

public class RemoveFinalTransformer extends ClassVisitor {
  // We just change class/method modifiers -> no need to visit all parts of the code
  public final static int PARSING_FLAGS = SKIP_DEBUG | SKIP_FRAMES;

  // TODO: make log level configurable
  private boolean logRemoveFinal;

  private final String LOG_PREFIX = RemoveFinalAgent.isActive()
    ? "[Remove Final Agent] "
    : "[Remove Final Transformer] ";

  private String className;

  public static void install(Instrumentation instrumentation) {
    instrumentation.addTransformer(
      (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
        ClassReader classReader = new ClassReader(classfileBuffer);
        ClassWriter classWriter = new ClassWriter(classReader, PARSING_FLAGS);
        classReader.accept(new RemoveFinalTransformer(classWriter, false), PARSING_FLAGS);
        return classWriter.toByteArray();
      }
    );
  }

  public RemoveFinalTransformer(ClassVisitor cv, boolean logRemoveFinal) {
    super(ASM8, cv);
    this.logRemoveFinal = logRemoveFinal;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    className = name.replace('/', '.');
    if (!shouldTransform()) {
      cv.visit(version, access, name, signature, superName, interfaces);
      return;
    }
    if (logRemoveFinal && (access & Modifier.FINAL) != 0)
      log("Removing final from class " + className);
    cv.visit(version, access & ~Modifier.FINAL, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    if (!shouldTransform())
      return super.visitMethod(access, name, desc, signature, exceptions);
    if (logRemoveFinal && (access & Modifier.FINAL) != 0) {
      log("Removing final from method " + className + "." + name + desc);
    }
    return super.visitMethod(access & ~Modifier.FINAL, name, desc, signature, exceptions);
  }

  /**
   * TODO: make black/white list of class and package names configurable
   */
//  @Override
  public boolean shouldTransform() {
    // Some sample packages to always exclude from transformation
    return !className.startsWith("de.scrum_master.agent.")
      && !className.startsWith("de.scrum_master.bytebuddy.")
      && !className.startsWith("net.bytebuddy.")
      && !className.startsWith("org.junit.")
      && !className.startsWith("junit.")
      && !className.startsWith("org.hamcrest.")
      && !className.startsWith("com.intellij.")
      && !className.startsWith("org.spockframework.");
  }

  private void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

}
