package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.*;
import dev.sarek.agent.constructor_mock.ConstructorMockRegistry;
import dev.sarek.agent.constructor_mock.ConstructorMockTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

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
 * <p>
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
 * @param <T> target type to create mock/spy objects for
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

  /**
   * This is a classical builder class, creating {@link MockFactory} instances via the {@link #build()} method after
   * previously having configured the builder with various options. See method descriptions for more details.
   *
   * @param <T> target type to create a mock factory for
   */
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

    /**
     * Specifies if instance methods should be mocked/stubbed or not. Defaults to {@code true}, so you only need to call
     * this method if you want to override by {@code false} or to explicitly document your intent to mock instance
     * methods.
     *
     * @param active mock/stub instance methods or not?
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> mockInstanceMethods(boolean active) {
      mockInstanceMethods = active;
      return this;
    }

    /**
     * Specifies if static methods should be mocked/stubbed or not. Defaults to {@code false}, so you only need to call
     * this method if you want to override by {@code true} or to explicitly document your intent not to mock static
     * methods.
     *
     * @param active mock/stub static methods or not?
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> mockStaticMethods(boolean active) {
      mockStaticMethods = active;
      return this;
    }

    /**
     * Mock constructors for target class, i.e. the constructor code will be skipped and the resulting objects have
     * uninitialised fields, avoiding all side effects which would otherwise occur during constructor execution. This
     * does not activate method mocking (which can be activated separately, either per instance or globally), but it is
     * a precondition for globally mocking/stubbing methods.
     * <p>
     * A global mock in this context is a special type of mock which gets created every time a client calls
     * {@code new MyTargetClass(..)}. This enables users to also create mocks outside the direct control of the test
     * using them. Those non-injectable mocks cannot be created by conventionals means, i.e. creating a mock within a
     * test and then injecting it into a class under test.
     * <p>
     * Technically, global mocks are implemented by instrumenting target class constructors and their super class
     * constructors. The instrumented constructors will dynamically check if the defining class of the object under
     * construction is registered in the {@link ConstructorMockRegistry}, which will be the case if either the builder
     * was preconfigured with {@link #addGlobalInstance()} or if later the actual mock factory is dynamically configured
     * with {@link MockFactory#addGlobalInstance()}. Calling this method alone just initiates constructor
     * instrumentation as such, global mocking still needs to be activated explicitly as just described. This makes
     * global mocking flexible, because it can be dynamically (de)activated during runtime, such that not necessarily
     * all instances of a target class created during a whole test have to be mocks, but only the desired ones. I.e.
     * that global mocks can be used both like a surgeon's scalpel and like a big axe.
     * <p>
     * Because non-injectable or global mocks are created outside the control of the test using them, the instrumented
     * constructor also makes sure that after creation each mock is added to a queue from which it can be polled, if
     * necessary. The user can obtain access to global mock instances via {@link #pollGlobalInstance()} or
     * {@link #pollGlobalInstance(int)}, respectively.
     *
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> mockConstructors() {
      global = true;
      return this;
    }

    /**
     * There are two basic types of test doubles: mock and spy. Mocks return null-ish values by default, spies wrap real
     * objects and pass through method calls and results by default. See {@link MockType#MOCK} and {@link MockType#SPY}
     * for more details.
     * <p>
     * The default mode for methods not explicitly stubbed otherwise is mock behaviour, so you only need to call this
     * method if you want spy behaviour instead.
     *
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> spy() {
      mockType = MockType.SPY;
      return this;
    }

    /**
     * Define mock/stub behaviour for a (set of) instance method(s), using an in-line pair of before/after advices.
     * <p>
     * This is a convenience method for situations in which you have no predefined {@link InstanceMethodAroundAdvice} to
     * work with but would have to create one yourself using its constructor. This method will just pass on the
     * before/after advices and create the around advice instance for you.
     *
     * @param methodMatcher ByteBuddy element matcher for methods, e.g. {@code named("myMethod")}. It can match one or
     *                      multiple methods, limit matching by name or parts thereof, by parameter types, return types,
     *                      method modifier etc. See methods of ByteBuddy class
     *                      <a href="https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/net/bytebuddy/matcher/ElementMatchers.html">
     *                      <code>ElementMatchers</code></a>.
     * @param before        AOP-style "before" advice, i.e. code executed before an advice-wrapped method itself was
     *                      executed. See {@link InstanceMethodAroundAdvice.Before#apply(Object, Method, Object[])}.
     * @param after         AOP-style "after" advice, i.e. code executed after an advice-wrapped method itself was
     *                      either executed or skipped by a "before" advice. See
     *                      {@link InstanceMethodAroundAdvice.After#apply(Object, Method, Object[], boolean, Object, Throwable)}.
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> mock(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      InstanceMethodAroundAdvice.Before before,
      InstanceMethodAroundAdvice.After after
    )
    {
      return mock(methodMatcher, new InstanceMethodAroundAdvice(before, after));
    }

    /**
     * Define mock/stub behaviour for a (set of) instance method(s), using an around advice.
     *
     * @param methodMatcher ByteBuddy element matcher for methods, e.g. {@code named("myMethod")}. It can match one or
     *                      multiple methods, limit matching by name or parts thereof, by parameter types, return types,
     *                      method modifier etc. See methods of ByteBuddy class
     *                      <a href="https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/net/bytebuddy/matcher/ElementMatchers.html">
     *                      <code>ElementMatchers</code></a>.
     * @param advice        AOP-style "around" advice, i.e. code wrapping a target method into a pair of "before" and
     *                      "after" advices.
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> mock(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      InstanceMethodAroundAdvice advice
    )
    {
      weaverBuilder.addAdvice(methodMatcher, advice);
      return this;
    }

    /**
     * Define mock/stub behaviour for a (set of) static method(s), using an in-line pair of before/after advices.
     * <p>
     * This is a convenience method for situations in which you have no predefined {@link StaticMethodAroundAdvice} to
     * work with but would have to create one yourself using its constructor. This method will just pass on the
     * before/after advices and create the around advice instance for you.
     *
     * @param methodMatcher ByteBuddy element matcher for methods, e.g. {@code named("myMethod")}. It can match one or
     *                      multiple methods, limit matching by name or parts thereof, by parameter types, return types,
     *                      method modifier etc. See methods of ByteBuddy class
     *                      <a href="https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/net/bytebuddy/matcher/ElementMatchers.html">
     *                      <code>ElementMatchers</code></a>.
     * @param before        AOP-style "before" advice, i.e. code executed before an advice-wrapped method itself was
     *                      executed. See {@link StaticMethodAroundAdvice.Before#apply(Method, Object[])}.
     * @param after         AOP-style "after" advice, i.e. code executed after an advice-wrapped method itself was
     *                      either executed or skipped by a "before" advice. See
     *                      {@link StaticMethodAroundAdvice.After#apply(Method, Object[], boolean, Object, Throwable)}.
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> mockStatic(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      StaticMethodAroundAdvice.Before before,
      StaticMethodAroundAdvice.After after
    )
    {
      return mockStatic(methodMatcher, new StaticMethodAroundAdvice(before, after));
    }

    /**
     * Define mock/stub behaviour for a (set of) static method(s), using an around advice.
     *
     * @param methodMatcher ByteBuddy element matcher for methods, e.g. {@code named("myMethod")}. It can match one or
     *                      multiple methods, limit matching by name or parts thereof, by parameter types, return types,
     *                      method modifier etc. See methods of ByteBuddy class
     *                      <a href="https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/net/bytebuddy/matcher/ElementMatchers.html">
     *                      <code>ElementMatchers</code></a>.
     * @param advice        AOP-style "around" advice, i.e. code wrapping a target method into a pair of "before" and
     *                      "after" advices.
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> mockStatic(
      ElementMatcher.Junction<MethodDescription> methodMatcher,
      StaticMethodAroundAdvice advice
    )
    {
      weaverBuilder.addAdvice(methodMatcher, advice);
      return this;
    }

    /**
     * Define any type of {@link AroundAdvice} for the target class. For normal instance/static method stubbing you
     * would not use this method but rather {@link #mock(ElementMatcher.Junction, InstanceMethodAroundAdvice)} or
     * {@link #mockStatic(ElementMatcher.Junction, StaticMethodAroundAdvice)}. But you can also use this generic method
     * instead. There are other around advice types such as {@link ConstructorAroundAdvice} or
     * {@link TypeInitialiserAroundAdvice}, though. If you have a more exotic use case, know what you are doing and want
     * to mix the more high level method factory API with the more low level aspect API, you can do so using this
     * method.
     *
     * @param methodMatcher ByteBuddy element matcher for methods, e.g. {@code named("myMethod")}. It can match one or
     *                      multiple methods, limit matching by name or parts thereof, by parameter types, return types,
     *                      method modifier etc. See methods of ByteBuddy class
     *                      <a href="https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/net/bytebuddy/matcher/ElementMatchers.html">
     *                      <code>ElementMatchers</code></a>.
     * @param advice        AOP-style "around" advice, e.g. targeting static type initialiser or constructor code
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> addAdvice(ElementMatcher.Junction<MethodDescription> methodMatcher, AroundAdvice<?> advice) {
      weaverBuilder.addAdvice(methodMatcher, advice);
      if (advice instanceof ConstructorAroundAdvice || advice instanceof TypeInitialiserAroundAdvice)
        needsTargetClassTarget = true;
      return this;
    }

    /**
     * By default, the mock factory makes sure that existing target class methods {@link #hashCode()} and
     * {@link #equals(Object)} methods will be overwritten by versions stritcly depending on object identity, not on
     * equality as usual. This helps avoid problems with those methods depending on initialised fields when in reality
     * constructor execution and thus proper object initialisation has been skipped in order to create mock objects.
     * <p>
     * If for some reason you do not wish to use auto-generated {@link #hashCode()} and {@link #equals(Object)} methods,
     * you can deactivate their creation by calling this method with a parameter value of {@code false}.
     *
     * @param value create {@link #hashCode()} and {@link #equals(Object)} methods or not?
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> provideHashCodeEquals(boolean value) {
      weaverBuilder.provideHashCodeEquals(value);
      return this;
    }

    /**
     * Explicitly exclude certain methods from mocking/stubbing for the given target class.
     *
     * @param methodMatcher ByteBuddy element matcher for methods, e.g. {@code named("myMethod")}. It can match one or
     *                      multiple methods, limit matching by name or parts thereof, by parameter types, return types,
     *                      method modifier etc. See methods of ByteBuddy class
     *                      <a href="https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/net/bytebuddy/matcher/ElementMatchers.html">
     *                      <code>ElementMatchers</code></a>.
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> excludeMethods(ElementMatcher.Junction<MethodDescription> methodMatcher) {
      excludedMethods = excludedMethods.or(methodMatcher);
      return this;
    }

    /**
     * Explicitly exclude certain super types from mocking/stubbing (especially constructor mock instrumentation) for
     * the given target class.
     *
     * @param typeMatcher ByteBuddy element matcher for types (classes), e.g. {@code named("my.qualified.ClassName")} or
     *                    {@code is(ClassName.class)}. It can match one or multiple types, limit matching by name or
     *                    parts thereof, class modifiers etc. See methods of ByteBuddy class
     *                    <a href="https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/net/bytebuddy/matcher/ElementMatchers.html">
     *                    <code>ElementMatchers</code></a>.
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> excludeSuperTypes(ElementMatcher.Junction<TypeDescription> typeMatcher) {
      weaverBuilder.excludeTypes(typeMatcher);
      return this;
    }

    /**
     * Normally you would create mocks, either directly via {@link #createInstance()} or via global mocking mode and
     * calling their constructors, then register instances as mocks dynamically (automatically or manually, both is
     * possible). But if at the time of configuring the mock factory target objects that should behave like mocks or
     * spies already exist, you can also register them here already, so right after the mock factory is active, the new
     * mocks/spies will be active, too.
     *
     * @param targets target instances to be transformed into mocks at mock factory creation time. It is also possible
     *                to register a {@link GlobalInstance} for the target class, even though the canonical way of doing
     *                so is to use builder method {@link #addGlobalInstance()} instead.
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> addTargets(Object... targets) {
      weaverBuilder.addTargets(targets);
      return this;
    }

    /**
     * Define that global mock mode should be active for the target class right after mock factory creation. See also
     * {@link #mockConstructors()} for more details about global mocks.
     *
     * @return the same builder instance, i.e. {@code this}
     */
    public Builder<T> addGlobalInstance() {
      weaverBuilder.addTargets(GlobalInstance.of(targetClass));
      return this;
    }

    /**
     * Instantiate a mock factory for the target class, configured as specified by previously called builder methods.
     * After the mock factory has been created using this method, please no longer call any other builder methods.
     * Instead, you can call the mock factory's own methods in order to tune its runtime configuration.
     *
     * @return the newly created mock factory
     */
    public MockFactory<T> build() {
      return new MockFactory<T>(this);
    }

  }

  /**
   * There are two basic types of test doubles: mock and spy. See {@link #MOCK} and {@link #SPY} for more details.
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

  /**
   * Register a target class instance as an active mock.
   * <p>
   * Technical background: Sarek mocks are based on byte code transformations applied to target classes, not on dynamic
   * proxies implemented as subclasses. Thus, a transformation always affects a whole class. In order to avoid turning
   * each target class into a global mock (i.e. all instances behave like mocks), we need a way to differentiate mock
   * instances from non-mock instances. This is achieved via a mock registry for each target class, i.e. if global
   * mocking is inactive, only registered instances show mock behavious, others behave normally. So now you know the
   * purpose of this method.
   *
   * @param target instance to be registered as a mock
   * @return the same mock factory instance, i.e. {@code this}
   * @throws IllegalArgumentException if (a) the user tries to register an object which is not an instance of the target
   *                                  class, (b) the mock factory was configured to ignore instance methods via
   *                                  {@link Builder#mockInstanceMethods(boolean)} with parameter {@code false}, making
   *                                  target registration pointless in the first place
   */
  public MockFactory<T> addTarget(Object target) throws IllegalArgumentException {
    verifyTarget(target);
    weaver.addTarget(target);
    return this;
  }

  /**
   * Unregister a target class instance as an active mock.
   * <p>
   * Technical background: Sarek mocks are based on byte code transformations applied to target classes, not on dynamic
   * proxies implemented as subclasses. Thus, a transformation always affects a whole class. In order to avoid turning
   * each target class into a global mock (i.e. all instances behave like mocks), we need a way to differentiate mock
   * instances from non-mock instances. This is achieved via a mock registry for each target class, i.e. if global
   * mocking is inactive, only registered instances show mock behavious, others behave normally. So now you know the
   * purpose of this method.
   *
   * @param target instance to be unregistered as a mock
   * @return the same mock factory instance, i.e. {@code this}
   * @throws IllegalArgumentException if (a) the user tries to unregister an object which is not an instance of the
   *                                  target class, (b) the mock factory was configured to ignore instance methods via
   *                                  {@link Builder#mockInstanceMethods(boolean)} with parameter {@code false}, making
   *                                  target (un)registration pointless in the first place
   */
  public MockFactory<T> removeTarget(Object target) throws IllegalArgumentException {
    verifyTarget(target);
    weaver.removeTarget(target);
    return this;
  }

  private void verifyTarget(Object target) throws IllegalArgumentException {
    if (!mockInstanceMethods)
      throw new IllegalArgumentException(
        "cannot add/remove target because mock factory is configured to ignore instance methods"
      );
    if (!target.getClass().equals(targetClass))
      throw new IllegalArgumentException(
        "target is not an instance of " + targetClass.getName() + ": " + target
      );
  }

  /**
   * Activate global instance method mocking for the target class, i.e. after calling this method all target class
   * instances will behave according to the mock/stub/spy behaviour configured for the target class. Use
   * {@link #removeGlobalInstance()} in order to deactivate global mocking again.
   * <p>
   * Technical background: see {@link #addTarget(Object)}
   *
   * @return the same mock factory instance, i.e. {@code this}
   */
  public MockFactory<T> addGlobalInstance() {
    weaver.addTarget(GlobalInstance.of(targetClass));
    return this;
  }

  /**
   * Dectivate global mock mode for the target class, i.e. after calling this method only registered target class
   * instances will behave according to the mock/stub/spy behaviour configured for the target class, but no longer all
   * instances like after {@link #addGlobalInstance()}.
   *
   * @return the same mock factory instance, i.e. {@code this}
   */
  public MockFactory<T> removeGlobalInstance() {
    weaver.removeTarget(GlobalInstance.of(targetClass));
    return this;
  }

  /**
   * Create a new mock instance (via Objenesis) and immediately activate. This is a convenience method doing the same as
   * a call to {@link #createInstance(boolean)} with parameter value {@code true}.
   *
   * @return an uninitialised target class instance which can be used as a mock
   * @throws IllegalArgumentException as explained in {@link #addTarget(Object)}
   */
  public T createInstance() throws IllegalArgumentException {
    return createInstance(true);
  }

  /**
   * Create a new mock instance (via Objenesis)
   *
   * @param addTarget If {@code true}, the new instance is registered as a mock immediately after creation. Otherwise
   *                  the mock behaves just like an uninitialised non-mock target class instance, i.e. its methods are
   *                  executed normally when called. So be careful when doing so, because if a called method depends on
   *                  correctly initialised fields, exceptions might be thrown or results be different from what you
   *                  might expect. As soon as you then manually register the instance as a mock via
   *                  {@link #addTarget(Object)}, mock/stub behaviour for instance methods will kick in.
   * @return an uninitialised target class instance which can be used as a mock
   * @throws IllegalArgumentException as explained in {@link #addTarget(Object)}
   */
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

  /**
   * Check for available mock instances via synchronous polling according to {@link Queue#poll()}. In asynchronous
   * scenarios, please use {@link #pollGlobalInstance(int)} instead.
   * <p>
   * See {@link ConstructorMockRegistry#pollMockInstance(Class)} for more details.
   *
   * @return mock instance, if available in the queue; {@code null} otherwise
   */
  public T pollGlobalInstance() {
    return (T) ConstructorMockRegistry.pollMockInstance(targetClass);
  }

  /**
   * Check for available mock instances via asynchronous polling with a timeout. This works according to
   * {@link BlockingQueue#poll(long, TimeUnit)}. In synchronous scenarios, please use {@link #pollGlobalInstance()}
   * instead.
   * <p>
   * See {@link ConstructorMockRegistry#pollMockInstance(Class, int)} for more details.
   *
   * @param timeoutMillis polling timeout in milliseconds
   * @return mock instance, if available in the queue before the timeout expires; {@code null} otherwise
   */
  public T pollGlobalInstance(int timeoutMillis) throws InterruptedException {
    return (T) ConstructorMockRegistry.pollMockInstance(targetClass, timeoutMillis);
  }

  /**
   * Closes the mock factory, reverting the corresponding code instrumentation for the target class and also
   * unregistering all existing mocks, emptying global mock polling queues and freeing other resources. After closing
   * the mock factory, please do not use it anymore.
   * <p>
   * Please note that this method makes {@link MockFactory} implement {@link AutoCloseable}, which means that you can
   * use try-with-resources in order to let the JVM take care of automatically closing it.
   */
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
