package de.scrum_master.bytebuddy.aspect;

import de.scrum_master.bytebuddy.util.TransformedClassFileWriter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher.Junction;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class Weaver {
  private static final ClassFileLocator CLASS_FILE_LOCATOR = ClassFileLocator.ForClassLoader.ofSystemLoader();

  // Do not use ByteBuddyAgent.install() in this class but get an Instrumentation instance injected.
  // Otherwise bytebuddy-agent.jar would have to be on the boot classpath. To put bytebuddy.jar there
  // is bothersome enough already. :-/
  private final Instrumentation instrumentation;
  private final Junction<TypeDescription> typeMatcher;
  private final Junction<MethodDescription> methodMatcher;
  private final ResettableClassFileTransformer transformer;
  private final AroundAdvice<?> advice;
  private final AdviceType adviceType;
  // TODO: maybe replace by a Set<WeakReference>
  private final Set<Object> targets = Collections.synchronizedSet(new HashSet<>());

  public enum AdviceType {
    METHOD(
      Advice.to(MethodAspect.class, CLASS_FILE_LOCATOR),
      isMethod()
    ),
    CONSTRUCTOR(
      Advice.to(ConstructorAspect.class, CLASS_FILE_LOCATOR),
      isConstructor()
    ),
    STATIC_INITIALISER(
      Advice.to(StaticInitialiserAspect.class, CLASS_FILE_LOCATOR),
      isTypeInitializer()
    );

    private final Advice advice;
    private final Junction<MethodDescription> methodType;

    AdviceType(Advice advice, Junction<MethodDescription> methodType) {
      this.advice = advice;
      this.methodType = methodType;
    }

    public Advice getAdvice() {
      return advice;
    }

    public Junction<MethodDescription> getMethodType() {
      return methodType;
    }
  }

  public Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    Junction<MethodDescription> methodMatcher,
    MethodAroundAdvice advice,
    Object... targets
  ) throws IllegalArgumentException, IOException {
    this(instrumentation, typeMatcher, methodMatcher, AdviceType.METHOD, advice, targets);
  }

  public Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    Junction<MethodDescription> methodMatcher,
    ConstructorAroundAdvice advice,
    Object... targets
  ) throws IllegalArgumentException, IOException {
    this(instrumentation, typeMatcher, methodMatcher, AdviceType.CONSTRUCTOR, advice, targets);
  }

  public Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    StaticInitialiserAroundAdvice advice,
    Object... targets
  ) throws IllegalArgumentException, IOException {
    this(instrumentation, typeMatcher, any(), AdviceType.STATIC_INITIALISER, advice, targets);
  }

  protected Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    Junction<MethodDescription> methodMatcher,
    AdviceType adviceType,
    AroundAdvice<?> advice,
    Object... targets
  ) throws IllegalArgumentException, IOException {
    if (instrumentation == null)
      throw new IllegalArgumentException("instrumentation must not be null");
    this.instrumentation = instrumentation;
    this.typeMatcher = typeMatcher == null ? any() : typeMatcher;
    this.methodMatcher = methodMatcher == null ? any() : methodMatcher;
    this.adviceType = adviceType;
    if (advice == null)
      throw new IllegalArgumentException("advice must not be null");
    this.advice = advice;
    for (Object target : targets)
      addTarget(target);
    // Register transformer last so as not to have a dangling active transformer if an exception occurs
    // in the constructor, e.g. because trying to register already registered targets
    this.transformer = registerTransformer();
  }

  public Weaver addTarget(Object target) throws IllegalArgumentException {
    Map<Object, AroundAdvice<?>> adviceRegistry = (Map<Object, AroundAdvice<?>>) getAdviceRegistry();
    synchronized (adviceRegistry) {
      if (adviceRegistry.get(target) != null)
        throw new IllegalArgumentException("target is already registered");
      adviceRegistry.put(target, advice);
      targets.add(target);
    }
    return this;
  }

  public Weaver removeTarget(Object target) {
    Map<Object, AroundAdvice<?>> adviceRegistry = (Map<Object, AroundAdvice<?>>) getAdviceRegistry();
    synchronized (adviceRegistry) {
      adviceRegistry.remove(target);
      targets.remove(target);
    }
    return this;
  }

  protected Map<Object, ? extends AroundAdvice<?>> getAdviceRegistry() {
    switch (adviceType) {
      case CONSTRUCTOR:
        return ConstructorAspect.adviceRegistry;
      case STATIC_INITIALISER:
        return StaticInitialiserAspect.adviceRegistry;
      default:
        return MethodAspect.adviceRegistry;
    }
  }

  protected ResettableClassFileTransformer registerTransformer() throws IOException {
    return createAgentBuilder().installOn(instrumentation);
  }

  public void unregisterTransformer() {
    unregisterTransformer(true);
  }

  public void unregisterTransformer(boolean reset) {
    for (Object target : targets.toArray())
      removeTarget(target);
    if (reset)
      resetTransformer();
    instrumentation.removeTransformer(transformer);
  }

  public void resetTransformer() {
    transformer.reset(instrumentation, RETRANSFORMATION);
  }

  protected AgentBuilder createAgentBuilder() throws IOException {
    return new AgentBuilder.Default()
      .disableClassFormatChanges()
      .ignore(none())
      .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
      .with(AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError())
      .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
      .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError())
      // Dump all transformed class files into a directory
      // .with(new TransformedClassFileWriter("transformed-aspect"))
      // Match type + method, then bind to advice
      .type(typeMatcher)
      .transform((builder, typeDescription, classLoader, module) ->
        builder.visit(
          adviceType.getAdvice()
            .on(adviceType.getMethodType().and(methodMatcher))
        )
      );
  }

}
