package dev.sarek.agent.mock;

import dev.sarek.agent.Agent;
import dev.sarek.agent.aspect.*;
import dev.sarek.agent.constructor_mock.ConstructorMockRegistry;
import dev.sarek.agent.constructor_mock.ConstructorMockTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class MockFactory<T> implements AutoCloseable {
  private Class<T> targetClass;
  private Weaver weaver;
  private ConstructorMockTransformer constructorMockTransformer;
  private final boolean mockInstanceMethods;
  private final boolean mockStaticMethods;
  private boolean closed = false;
  private ObjectInstantiator<T> instantiator;

  public static <T> Builder<T> forClass(Class<T> targetClass) {
    return new Builder<T>(targetClass);
  }

  public static <T> Builder<T> forClass(String targetClassName) throws ClassNotFoundException {
    return new Builder<T>((Class<T>) Class.forName(targetClassName));
  }

  public static class Builder<T> {
    private final Class<T> targetClass;
    private final Weaver.Builder weaverBuilder;
    private ElementMatcher.Junction<MethodDescription> excludedMethods = none();
    private boolean mockInstanceMethods = true;
    private boolean mockStaticMethods = false;
    private boolean global = false;
    private MockType mockType = MockType.MOCK;

    private Builder(Class<T> targetClass) {
      this.targetClass = targetClass;
      weaverBuilder = Weaver.forTypes(
        is(targetClass).or(isSuperTypeOf(targetClass).and(not(is(Object.class)))));
    }

    public Builder<T> mockInstanceMethods(boolean active) {
      mockInstanceMethods = active;
      return this;
    }

    public Builder<T> mockStaticMethods(boolean active) {
      mockStaticMethods = active;
      return this;
    }

    public Builder<T> global() {
      global = true;
      return this;
    }

    public Builder<T> spy() {
      mockType = MockType.SPY;
      return this;
    }

    public Builder<T> mock(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      InstanceMethodAroundAdvice.Before before,
      InstanceMethodAroundAdvice.After after
    ) {
      return mock(methodMatcher, new InstanceMethodAroundAdvice(before, after));
    }

    public Builder<T> mock(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      InstanceMethodAroundAdvice advice
    ) {
      weaverBuilder.addAdvice(methodMatcher, advice);
      return this;
    }

    public Builder<T> mockStatic(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      StaticMethodAroundAdvice.Before before,
      StaticMethodAroundAdvice.After after
    ) {
      return mockStatic(methodMatcher, new StaticMethodAroundAdvice(before, after));
    }

    public Builder<T> mockStatic(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      StaticMethodAroundAdvice advice
    ) {
      weaverBuilder.addAdvice(methodMatcher, advice);
      return this;
    }

    public Builder<T> addAdvice(ElementMatcher.Junction<MethodDescription> methodMatcher, AroundAdvice<?> advice) {
      weaverBuilder.addAdvice(methodMatcher, advice);
      return this;
    }

    public Builder<T> provideHashCodeMethod() {
      weaverBuilder.provideHashCodeMethod();
      return this;
    }

    public Builder<T> excludeMethods(ElementMatcher.Junction<MethodDescription> methodMatcher) {
      excludedMethods = excludedMethods.or(methodMatcher);
      return this;
    }

    public Builder<T> excludeSuperTypes(ElementMatcher.Junction<TypeDescription> typeMatcher) {
      weaverBuilder.excludeTypes(typeMatcher);
      return this;
    }

    public Builder<T> addTargets(Object... targets) {
      weaverBuilder.addTargets(targets);
      return this;
    }

    public Builder<T> addGlobalInstance() {
      weaverBuilder.addTargets(GlobalInstance.of(targetClass));
      return this;
    }

    public MockFactory<T> build() throws IOException {
      return new MockFactory<T>(this);
    }

  }

  public enum MockType {MOCK, SPY}

  private MockFactory(Builder<T> builder) throws IOException {
    targetClass = builder.targetClass;
    mockInstanceMethods = builder.mockInstanceMethods;
    mockStaticMethods = builder.mockStaticMethods;
    if (builder.mockType == MockType.MOCK) {
      if (builder.global) {
        Set<Class<?>> classHierarchy = new LinkedHashSet<>();
        classHierarchy.add(builder.targetClass);
        Class<?> superClass = builder.targetClass.getSuperclass();
        while (!superClass.equals(Object.class)) {
          classHierarchy.add(superClass);
          superClass = superClass.getSuperclass();
        }
        constructorMockTransformer = new ConstructorMockTransformer(classHierarchy);
        Agent.getInstrumentation().addTransformer(constructorMockTransformer, true);
        ConstructorMockRegistry.activate(targetClass.getName());
      }
      if (mockInstanceMethods) {
        builder.weaverBuilder.addAdvice(
          not(builder.excludedMethods),
          InstanceMethodAroundAdvice.MOCK
        );
      }
      if (mockStaticMethods)
        builder.weaverBuilder.addAdvice(
          not(builder.excludedMethods),
          StaticMethodAroundAdvice.MOCK
        );
    }
    if (builder.global || mockStaticMethods) {
      builder.weaverBuilder.addTargets(targetClass);
    }
    weaver = builder.weaverBuilder.build();
  }

  public MockFactory<T> addTarget(Object target) throws IllegalArgumentException {
    verifyTarget(target);
    weaver.addTarget(target);
    return this;
  }

  public MockFactory<T> removeTarget(Object target) throws IllegalArgumentException {
    verifyTarget(target);
    weaver.removeTarget(target);
    return this;
  }

  public MockFactory<T> addGlobalInstance() {
    addTarget(GlobalInstance.of(targetClass));
    return this;
  }

  public MockFactory<T> removeGlobalInstance() {
    removeTarget(GlobalInstance.of(targetClass));
    return this;
  }

  private void verifyTarget(Object target) throws IllegalArgumentException {
    if (target instanceof Class)
      throw new IllegalArgumentException("cannot use class as target: " + target);
    if (!mockInstanceMethods)
      throw new IllegalArgumentException(
        "cannot add/remove target because mock factory is configured to ignore instance methods"
      );
  }

  public T createInstance() throws IllegalArgumentException {
    return createInstance(true);
  }

  public T createInstance(boolean addTarget) throws IllegalArgumentException {
    T newInstance = getInstantiator().newInstance();
    if (addTarget)
      addTarget(newInstance);
    return newInstance;
  }

  private ObjectInstantiator<T> getInstantiator() {
    if (instantiator == null)
      instantiator = new ObjenesisStd().getInstantiatorOf(targetClass);
    return instantiator;
  }

  @Override
  public void close() {
    if (closed)
      return;
    try {
      System.out.println("Closing Mock");
      ConstructorMockRegistry.deactivate(targetClass.getName());
      targetClass = null;
      if (constructorMockTransformer != null) {
        Agent.getInstrumentation().removeTransformer(constructorMockTransformer);
        constructorMockTransformer = null;
      }
      // Automatically retransforms, thus also unapplies constructorMockTransformer -> TODO: test!
      weaver.unregisterTransformer();
      // INSTRUMENTATION.retransformClasses(classes);
      weaver = null;
      System.out.println("Mock closed");
    }
    catch (Exception e) {
      System.err.println("Error caught while trying to close mock");
      e.printStackTrace();
    }
    finally {
      closed = true;
    }
  }
}
