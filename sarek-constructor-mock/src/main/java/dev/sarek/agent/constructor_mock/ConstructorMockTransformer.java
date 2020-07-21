package dev.sarek.agent.constructor_mock;

import dev.sarek.agent.util.TransformedClassFileWriter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.pool.TypePool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static dev.sarek.agent.Agent.getInstrumentation;
import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class ConstructorMockTransformer<T> implements AutoCloseable {
  private ResettableClassFileTransformer transformer;
  private boolean resetTransformationOnClose;

  public static class Builder<T> {
    private final Class<T> targetClass;
    private final Set<Class<? super T>> excludedSuperClasses = new HashSet<>();
    private boolean resetTransformationOnClose = true;
    private boolean logVerbose = false;
    private boolean dumpTransformedClassfiles = false;

    private Builder(Class<T> targetClass) {
      this.targetClass = targetClass;
    }

    public Builder<T> excludeSuperClasses(Class<? super T>... superClasses) {
      excludedSuperClasses.addAll(Arrays.asList(superClasses));
      return this;
    }

    public Builder<T> resetTransformationOnClose(boolean active) {
      resetTransformationOnClose = active;
      return this;
    }

    public Builder<T> logVerbose(boolean active) {
      logVerbose = active;
      return this;
    }

    public Builder<T> dumpTransformedClassfiles(boolean active) {
      dumpTransformedClassfiles = active;
      return this;
    }

    public ConstructorMockTransformer<T> build() {
      return new ConstructorMockTransformer<>(this);
    }
  }

  public static <T> Builder<T> forClass(Class<T> targetClass) {
    return new Builder<>(targetClass);
  }

  private ConstructorMockTransformer(Builder<T> transformerBuilder) {
    AgentBuilder agentBuilder = new AgentBuilder.Default()
      .disableClassFormatChanges()
      .ignore(none())
      .with(RETRANSFORMATION)
      .with(AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError())
      // TODO: make weaver logging configurable in general and with regard to '.withTransformationsOnly()' in particular
      .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
      .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError());

    // Optionally dump all transformed class files into a directory
    if (transformerBuilder.dumpTransformedClassfiles)
      agentBuilder = agentBuilder.with(new TransformedClassFileWriter("transformed-constructor"));

    transformer = agentBuilder
      .type(
        isSuperTypeOf(transformerBuilder.targetClass)
          .and(not(is(Object.class)))
          .and(not(isInterface()))
          .and(not(anyOf(transformerBuilder.excludedSuperClasses)))
      )
      .transform((builder, typeDescription, classLoader, module) ->
        builder.visit(
          new AsmVisitorWrapper
            .ForDeclaredMethods()
            .constructor(any(), new ConstructorMockMethodVisitorWrapper(transformerBuilder.logVerbose))
            .writerFlags(COMPUTE_FRAMES)
            .readerFlags(0)
        )
      )
      .installOn(getInstrumentation());
    resetTransformationOnClose = transformerBuilder.resetTransformationOnClose;
  }

  @Override
  public void close() {
    if (resetTransformationOnClose)
      transformer.reset(getInstrumentation(), RETRANSFORMATION);
    getInstrumentation().removeTransformer(transformer);
  }

  private static class ConstructorMockMethodVisitorWrapper implements MethodVisitorWrapper {
    private ConstructorMockMethodVisitorWrapper(boolean logVerbose) {
      this.logVerbose = logVerbose;
    }

    private final boolean logVerbose;

    @Override
    public MethodVisitor wrap(
      TypeDescription instrumentedType,
      MethodDescription instrumentedMethod,
      MethodVisitor methodVisitor,
      Implementation.Context implementationContext,
      TypePool typePool,
      int writerFlags,
      int readerFlags)
    {
      if (logVerbose)
        System.out.println("[Constructor Mock Transformer] Mocking constructor " + instrumentedMethod);
      return new ConstructorMockMethodVisitor(instrumentedType, methodVisitor, instrumentedMethod);
    }
  }

}
