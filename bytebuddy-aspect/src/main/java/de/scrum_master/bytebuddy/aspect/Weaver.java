package de.scrum_master.bytebuddy.aspect;

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
import java.util.Set;

import static de.scrum_master.bytebuddy.aspect.Aspect.adviceRegistry;
import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.none;

public class Weaver {
  private static final Advice ADVICE_TO_GENERIC_ASPECT = Advice.to(
    Aspect.class,
    ClassFileLocator.ForClassLoader.ofSystemLoader()
  );

  // Do not use ByteBuddyAgent.install() in this class but get an Instrumentation instance injected.
  // Otherwise bytebuddy-agent.jar would have to be on the boot classpath. To put bytebuddy.jar there
  // is bothersome enough already. :-/
  private final Instrumentation instrumentation;
  private final Junction<TypeDescription> typeMatcher;
  private final Junction<MethodDescription> methodMatcher;
  private final ResettableClassFileTransformer transformer;
  private final AroundAdvice advice;
  // TODO: maybe replace by a Set<WeakReference>
  private final Set<Object> targets = Collections.synchronizedSet(new HashSet<>());

  public Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    Junction<MethodDescription> methodMatcher,
    AroundAdvice advice,
    Object... targets
  ) throws IllegalArgumentException, IOException {
    if (instrumentation == null)
      throw new IllegalArgumentException("instrumentation must not be null");
    this.instrumentation = instrumentation;
    this.typeMatcher = typeMatcher == null ? any() : typeMatcher;
    this.methodMatcher = methodMatcher == null ? any() : methodMatcher;
    this.transformer = registerTransformer();
    if (advice == null)
      throw new IllegalArgumentException("advice must not be null");
    this.advice = advice;
    for (Object target : targets)
      addTarget(target);
  }

  public Weaver addTarget(Object target) throws IllegalArgumentException {
    synchronized (adviceRegistry) {
      if (adviceRegistry.get(target) != null)
        throw new IllegalArgumentException("target is already registered");
      adviceRegistry.put(target, advice);
      targets.add(target);
    }
    return this;
  }

  public Weaver removeTarget(Object target) {
    synchronized (adviceRegistry) {
      adviceRegistry.remove(target);
      targets.remove(target);
    }
    return this;
  }

  protected ResettableClassFileTransformer registerTransformer() throws IOException {
    return createAgentBuilder().installOn(instrumentation);
  }

  public void unregisterTransformer() {
    unregisterTransformer(true);
  }

  public void unregisterTransformer(boolean reset) {
    for (Object target : targets)
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
      // Match type + method, then bind to advice
      .type(typeMatcher)
      .transform((builder, typeDescription, classLoader, module) ->
        builder.visit(ADVICE_TO_GENERIC_ASPECT.on(methodMatcher))
      );
  }

}
