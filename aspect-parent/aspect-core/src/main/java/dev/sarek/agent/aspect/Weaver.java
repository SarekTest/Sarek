package dev.sarek.agent.aspect;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher.Junction;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.*;

import static dev.sarek.agent.aspect.Aspect.AdviceType.*;
import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.none;

public class Weaver {
  private static final WovenMethodRegistry wovenMethodRegistry = new WovenMethodRegistry();

  /**
   * TODO: make thread-safe
   */
  public static class WovenMethodRegistry {
    private Map<MethodDescription, Set<Weaver>> registry = new HashMap<>();

    public boolean isWoven(MethodDescription methodDescription) {
      Set<Weaver> weavers = registry.get(methodDescription);
      if (weavers == null)
        return false;
      return weavers.size() > 0;
    }

    public WovenMethodRegistry add(MethodDescription methodDescription, Weaver weaver) {
      registry
        .computeIfAbsent(methodDescription, methDesc -> new HashSet<>())
        .add(weaver);
      return this;
    }

    public WovenMethodRegistry remove(MethodDescription methodDescription, Weaver weaver) {
      Set<Weaver> weavers = registry.get(methodDescription);
      if (weavers != null)
        weavers.remove(weaver);
      return this;
    }

    public WovenMethodRegistry removeAll(Weaver weaver) {
      for (MethodDescription methodDescription : registry.keySet())
        remove(methodDescription, weaver);
      return this;
    }

    public WovenMethodRegistry clear() {
      registry.clear();
      return this;
    }
  }

  // Do not use ByteBuddyAgent.install() in this class but get an Instrumentation instance injected.
  // Otherwise bytebuddy-agent.jar would have to be on the boot classpath. To put bytebuddy.jar there
  // is bothersome enough already. :-/
  // TODO: Think about using ByteBuddyAgent anyway in order to unburden the user from providing an Instrumentation
  //       instance in the constructor. The functionality to obtain an instance could be located in base class
  //       dev.sarek.agent.Agent and lazily initialised in a getter method if not explicitly set in a subclass agent's
  //       premain method. As there is no such thing as a static super class method being automatically called, we have
  //       to rely on subclasses initialising the parent class member if in situations where the JVM is started via
  //       '-javaagent' we want to avoid calling ByteBuddyAgent.install(), because that requires a JDK, not just a JRE.
  private final Instrumentation instrumentation;
  private final Junction<TypeDescription> typeMatcher;
  private final Junction<MethodDescription> methodMatcher;
  private final ResettableClassFileTransformer transformer;
  private final AroundAdvice<?> advice;
  private final Aspect.AdviceType adviceType;
  // TODO: maybe replace by a Set<WeakReference>
  private final Set<Object> targets = Collections.synchronizedSet(new HashSet<>());

  public Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    Junction<MethodDescription> methodMatcher,
    MethodAroundAdvice advice,
    Object... targets
  ) throws IllegalArgumentException, IOException
  {
    this(instrumentation, typeMatcher, methodMatcher, METHOD_ADVICE, advice, targets);
  }

  public Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    Junction<MethodDescription> methodMatcher,
    ConstructorAroundAdvice advice,
    Object... targets
  ) throws IllegalArgumentException, IOException
  {
    this(instrumentation, typeMatcher, methodMatcher, CONSTRUCTOR_ADVICE, advice, targets);
  }

  public Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    TypeInitialiserAroundAdvice advice,
    Object... targets
  ) throws IllegalArgumentException, IOException
  {
    this(instrumentation, typeMatcher, any(), TYPE_INITIALISER_ADVICE, advice, targets);
  }

  protected Weaver(
    Instrumentation instrumentation,
    Junction<TypeDescription> typeMatcher,
    Junction<MethodDescription> methodMatcher,
    Aspect.AdviceType adviceType,
    AroundAdvice<?> advice,
    Object... targets
  ) throws IllegalArgumentException, IOException
  {
    System.out.println("Creating new weaver " + this);
    if (instrumentation == null)
      throw new IllegalArgumentException("instrumentation must not be null");
    this.instrumentation = instrumentation;
    this.typeMatcher = typeMatcher == null ? any() : typeMatcher;
    this.methodMatcher = methodMatcher == null ? any() : methodMatcher;
    this.adviceType = adviceType;
    if (advice == null)
      throw new IllegalArgumentException("advice must not be null");
    this.advice = advice;
    // TODO: exception during target registration -> dangling targets in advice registry -> FIXME via:
    //   (a) memorise + clean up (argh!) or
    //   (b) don't allow target registration in constructor or
    //   (c) reverse order: register transformer first, then after target registration error
    //       unregister transformer again, which also removes targets already added for that transformer
    //   (a) difficult + inefficient, (b) easy to implement, (c) also fairly easy, better usability
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
        throw new IllegalArgumentException("target " + target + " is already registered");
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
      case CONSTRUCTOR_ADVICE:
        return ConstructorAspect.adviceRegistry;
      case TYPE_INITIALISER_ADVICE:
        return TypeInitialiserAspect.adviceRegistry;
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

  // TODO: Maybe delete this method because usually when unregistering the transformer we also reset it.
  private void unregisterTransformer(boolean reset) {
    for (Object target : targets.toArray())
      removeTarget(target);
    if (reset)
      resetTransformer();
    instrumentation.removeTransformer(transformer);
  }

  private void resetTransformer() {
//    System.out.println("[Aspect Agent] Resetting transformer for weaver " + this);
    // If transformation was reversed successfully (i.e. target classes are no longer woven),
    // remove all associated methods for this weaver from the woven method registry
    if (transformer.reset(instrumentation, RETRANSFORMATION))
      wovenMethodRegistry.removeAll(this);
//    System.out.println("[Aspect Agent] Resetting transformer for weaver " + this + " finished");
  }

  protected AgentBuilder createAgentBuilder() throws IOException {
    return new AgentBuilder.Default()
      .disableClassFormatChanges()
      .ignore(none())
      .with(RETRANSFORMATION)
      .with(AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError())
      // TODO: make weaver logging configurable in general and with regard to '.withTransformationsOnly()' in particular
      .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
      .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError())
      .with(new AgentBuilder.InstallationListener.Adapter() {
        @Override
        public void onBeforeInstall(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {
//          System.out.println("[Aspect Agent] onBeforeInstall: instrumentation = " + instrumentation + ", classFileTransformer = " + classFileTransformer);
          wovenMethodRegistry.clear();
          super.onBeforeInstall(instrumentation, classFileTransformer);
        }

/*
        @Override
        public void onInstall(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {
          System.out.println("[Aspect Agent] onInstall: instrumentation = " + instrumentation + ", classFileTransformer = " + classFileTransformer);
          super.onInstall(instrumentation, classFileTransformer);
        }

        @Override
        public Throwable onError(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer, Throwable throwable) {
          System.out.println("[Aspect Agent] onError: instrumentation = " + instrumentation + ", classFileTransformer = " + classFileTransformer + ", throwable = " + throwable);
          return super.onError(instrumentation, classFileTransformer, throwable);
        }

        @Override
        public void onReset(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {
          System.out.println("[Aspect Agent] onReset: instrumentation = " + instrumentation + ", classFileTransformer = " + classFileTransformer);
          super.onReset(instrumentation, classFileTransformer);
        }
*/
      })
      // Dump all transformed class files into a directory
      //.with(new TransformedClassFileWriter("transformed-aspect"))
      // Match type + method, then bind to advice
      .type(typeMatcher)
      .transform((builder, typeDescription, classLoader, module) ->
          builder.visit(
            adviceType.getAdvice().on(
              adviceType.getMethodType()
                .and(methodMatcher)
//              .and(methodDescription -> wovenMethods.add(methodDescription))
                .and(methodDescription -> {
                    boolean woven = wovenMethodRegistry.isWoven(methodDescription);
                    System.out.println(
                      (woven ? "Avoid double" : "Perform")
                        + " aspect weaving for: " + methodDescription
                        + " / weaver = " + this
                    );
                    wovenMethodRegistry.add(methodDescription, this);
                    return !woven;
                  }
                )
            )
          )
      );
  }

}
