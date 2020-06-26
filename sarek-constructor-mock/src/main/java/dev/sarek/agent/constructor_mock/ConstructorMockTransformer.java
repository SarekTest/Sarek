package dev.sarek.agent.constructor_mock;

import dev.sarek.agent.util.TransformedClassFileWriter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
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
          .and(not(isInterface()))
          .and(not(anyOf(transformerBuilder.excludedSuperClasses)))
      )
      .transform((builder, typeDescription, classLoader, module) ->
        builder.visit(new ConstructorMockVisitorWrapper(transformerBuilder.logVerbose))
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

  private static class ConstructorMockVisitorWrapper implements AsmVisitorWrapper {
    private ConstructorMockVisitorWrapper(boolean logVerbose) {
      this.logVerbose = logVerbose;
    }

    private final boolean logVerbose;

    @Override
    public int mergeWriter(int flags) {
      return COMPUTE_FRAMES;
    }

    @Override
    public int mergeReader(int flags) {
      return 0;
    }

    @Override
    public ClassVisitor wrap(
      TypeDescription instrumentedType,
      ClassVisitor classVisitor,
      Implementation.Context implementationContext,
      TypePool typePool,
      FieldList<FieldDescription.InDefinedShape> fields,
      MethodList<?> methods,
      int writerFlags,
      int readerFlags
    )
    {
      return new ConstructorMockClassVisitor(instrumentedType, classVisitor, logVerbose);
    }
  }
}
