package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.*;
import dev.sarek.agent.constructor_mock.ConstructorMockRegistry;
import dev.sarek.agent.constructor_mock.ConstructorMockTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import java.util.LinkedHashSet;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * A Sarek mock factory and its corresponding builder class are a high level wrapper around other, lower-level Sarek
 * functionalities such as constructor mocking and aspect advices. Sarek mocks/spies are not meant to replace other mock
 * frameworks such as Mockito, EasyMock or the mock functionality contained in the Spock framework, but to complement
 * them. While there are some functional intersections, some key features go beyond normal mock frameworks. They might
 * not be as nice and easy to use as the mentioned frameworks, but they do work and hopefully offer added value for
 * difficult test cases. Some basic functionalities are:
 * <ol>
 *   <li>
 *     Mock final classes: Sarek does not use dynamic proxies and thus does not rely on subclassing. Instead, it
 *     directly instruments target classes. Thus, the {@code final} qualifier on classes is not a show stopper.
 *   </li>
 *   <li>
 *     Stub final methods: For the same reasons as above, Sarek can directly instrument final methods and thus stub them
 *     or spy on them.
 *   </li>
 *   <li>
 *     Mock, stub and spy on already loaded classes, including most JRE classes: Because Sarek instrumentation does not
 *     change class structure, in-memory instumentation on already loaded classes (<i>retransformation</i> in technical
 *     terms), including JRE bootstrap classes, can be performed.
 *   </li>
 *   <li>
 *     Mock, stub and spy functionality can be limited to single instances or applied globally (similar to Spock's
 *     global Groovy mocks, but applicable to Java, Kotlin or other JVM language classes too). I.e., it is possible to
 *     intercept constructor calls on registered target classes and dynamically skip the constructor code, effectively
 *     creating an uninitialised instance. In combination with method mocking/stubbing, this enables users to mock
 *     non-injectable objects, for example objects created outside the control of a test inside a method, directly
 *     instantiated with {@code new}.
 *   </li>
 *   <li>
 *     For said global, non-injectable mocks it is possible to also obtain references to them by polling a queue of
 *     created mock objects per target class. This way, even though the target object has been created outside the
 *     control of a test, the user can still grab a reference and then interact with it. In many cases this is not
 *     necessary, but when it is, this feature comes in handy.
 *   </li>
 *   <li>
 *     Method stubbing is not limited to instance methods, but also encompasses static methods.
 *   </li>
 *   <li>
 *     All of Sarek's mock, stub, spy byte code transformations are reversible, i.e. all target classes can be reset to
 *     their original versions by just calling {@code #close} on the mock factory or using try-with-resources because
 *     this class implements the {@code AutoCloseable} interface.
 *   </li>
 *   <li>
 *     It is also possible to manually create mock instances via {@code #createInstance}. In this case, Sarek does not
 *     use constructor instrumentation but simply uses <i>Objenesis</i> in order to create an instance. This is similar
 *     to what other mock frameworks do in certain cases, but in combination with method stubbing it also works for
 *     final classes, as mentioned above.
 *   </li>
 * </ol>
 * <p></p>
 * The generic usage pattern is:
 * <ol>
 *   <li>Create a {@link Builder} via {@link #forClass(Class)} or {@link #forClass(String)}</li>
 *   <li>Configure the builder, calling a chained set of builder methods</li>
 *   <li>Create a {@link MockFactory} instance via {@link Builder#build()}</li>
 *   <li>
 *     Create and interact with mock instances, either directly by calling methods on the mock factory or on the created
 *     mocks as such.
 *   </li>
 *   <li>
 *     Close the mock factory, either via try-with-resources or by directly calling {@link #close()}. This reverts the
 *     corresponding code instrumentation for the target class and also unregisters all existing mocks, empties global
 *     mock polling queues and frees other resources.
 *   </li>
 * </ol>
 * For more information, please look at Sarek's own test suite or read the tutorial.
 *
 * @param <T> target type to create mock/spy opjects for
 */
public class MockFactory<T> implements AutoCloseable {
  private Class<T> targetClass;
  private Weaver weaver;
  private ConstructorMockTransformer constructorMockTransformer;
  private final boolean mockInstanceMethods;
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
    private boolean needsTargetClassTarget = false;
    private MockType mockType = MockType.MOCK;

    private Builder(Class<T> targetClass) {
      this.targetClass = targetClass;
      weaverBuilder = Weaver.forTypes(
        is(targetClass).or(isSuperTypeOf(targetClass).and(not(is(Object.class)))));
      weaverBuilder.provideHashCodeEquals(true);
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
    )
    {
      return mock(methodMatcher, new InstanceMethodAroundAdvice(before, after));
    }

    public Builder<T> mock(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      InstanceMethodAroundAdvice advice
    )
    {
      weaverBuilder.addAdvice(methodMatcher, advice);
      return this;
    }

    public Builder<T> mockStatic(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      StaticMethodAroundAdvice.Before before,
      StaticMethodAroundAdvice.After after
    )
    {
      return mockStatic(methodMatcher, new StaticMethodAroundAdvice(before, after));
    }

    public Builder<T> mockStatic(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      StaticMethodAroundAdvice advice
    )
    {
      weaverBuilder.addAdvice(methodMatcher, advice);
      return this;
    }

    public Builder<T> addAdvice(ElementMatcher.Junction<MethodDescription> methodMatcher, AroundAdvice<?> advice) {
      weaverBuilder.addAdvice(methodMatcher, advice);
      if (advice instanceof ConstructorAroundAdvice || advice instanceof TypeInitialiserAroundAdvice)
        needsTargetClassTarget = true;
      return this;
    }

    public Builder<T> provideHashCodeEquals(boolean value) {
      weaverBuilder.provideHashCodeEquals(value);
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

    public MockFactory<T> build() {
      return new MockFactory<T>(this);
    }

  }

  /**
   * There are two basic types of test doubles: mock and spy. See enum value documentation for more details.
   */
  public enum MockType {
    /**
     * A Sarek mock is an uninitialised target class instance (not a subclass or a proxy), instrumented to be a mock
     * object. Its default behaviour for called methods is to return a dummy "null-ish" value like {@code null},
     * {@code 0} or {@code false}. This behaviour can be modified on a per-method basis.
     */
    MOCK,
    /**
     * A Sarek spy is based on a fully initialised target class instance (not a subclass or a proxy), instrumented to be
     * a spy object. Its default behaviour for called methods is to pass through each call to the original method and
     * return the original result. This behaviour can be modified on a per-method basis.
     */
    SPY
  }

  private MockFactory(Builder<T> builder) {
    targetClass = builder.targetClass;
    mockInstanceMethods = builder.mockInstanceMethods;
    if (builder.mockType == MockType.MOCK) {
      if (builder.global) {
        Set<Class<?>> classHierarchy = new LinkedHashSet<>();
        classHierarchy.add(builder.targetClass);
        Class<?> superClass = builder.targetClass.getSuperclass();
        while (!superClass.equals(Object.class)) {
          classHierarchy.add(superClass);
          superClass = superClass.getSuperclass();
        }
        // TODO: option to exclude super types also for constructor mocking
        constructorMockTransformer = ConstructorMockTransformer.forClass(targetClass).build();
      }
      if (mockInstanceMethods) {
        builder.weaverBuilder.addAdvice(
          not(builder.excludedMethods),
          InstanceMethodAroundAdvice.MOCK
        );
      }
      if (builder.mockStaticMethods)
        builder.weaverBuilder.addAdvice(
          not(builder.excludedMethods),
          StaticMethodAroundAdvice.MOCK
        );
    }
    if (builder.global || builder.mockStaticMethods || builder.needsTargetClassTarget) {
      builder.weaverBuilder.addTargets(targetClass);
    }

    // Important: First build weaver, then activate constructor mock targets. Otherwise the weaver builder might call
    // already mocked constructors during setup.
    weaver = builder.weaverBuilder.build();
    if (constructorMockTransformer != null)
      ConstructorMockRegistry.activate(targetClass);
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

  public T pollGlobalInstance() {
    return (T) ConstructorMockRegistry.pollMockInstance(targetClass);
  }

  public T pollGlobalInstance(int timeoutMillis) throws InterruptedException {
    return (T) ConstructorMockRegistry.pollMockInstance(targetClass, timeoutMillis);
  }

  @Override
  public void close() {
    if (closed)
      return;
    try {
      System.out.println("Closing Mock");
      // Important: First deactivate constructor mock targets, then shut down weaver. Otherwise the weaver might call
      // mocked constructors during shutdown.
      ConstructorMockRegistry.deactivate(targetClass);
      targetClass = null;
      weaver.unregisterTransformer();
      weaver = null;
      if (constructorMockTransformer != null) {
        constructorMockTransformer.close();
        constructorMockTransformer = null;
      }
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
