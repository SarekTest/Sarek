package dev.sarek.agent.aspect;

import dev.sarek.agent.util.BiMultiMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static dev.sarek.agent.aspect.Aspect.AdviceScope.*;
import static dev.sarek.agent.aspect.Aspect.AdviceTargetType.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class Aspect<T> {
  public static final BiMultiMap<Object, Weaver.Builder.AdviceDescription> adviceRegistry = new BiMultiMap<>();

  public static final ClassFileLocator CLASS_FILE_LOCATOR = ClassFileLocator.ForClassLoader.ofSystemLoader();

  /**
   * A concrete advice can be registered with different scopes, e.g. per class or for a specific object instance.
   * See the description of each defined enum constant for more details.
   */
  public enum AdviceScope {
    /**
     * Apply advice globally. I.e whatever the class and method matchers of the defined aspect match, however broadly
     * they are defined, the advice will be applied, unless a less global scope is registered on the weaver.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     */
    SCOPE_GLOBAL,
    /**
     * Limit advice application to anything matched within a specific Java module.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     */
    SCOPE_MODULE,
    /**
     * Limit advice application to anything matched within a specific package and its subpackages.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * TODO: Modules and packages can intersect. How do we handle this?
     */
    SCOPE_PACKAGE,
    /**
     * Limit advice application to anything matched within a specific class.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * TODO: What about subclasses? Probably we shall not support them. Why recreate Aspect? This framework is mostly
     * targeted to people looking for a mocking tool which can also be applied to already loaded classes, even
     * JRE bootstrap classes.
     */
    SCOPE_CLASS,
    /**
     * Limit advice application to anything matching a specific method or constructor with a unique signature, i.e.
     * explicitly do not match multiple overloaded methods of the same name.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     */
    SCOPE_METHOD,
    /**
     * Limit advice application to a specific instance registered on the weaver.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * TODO: Instance scope intersecs with all other scopes. How do we handle preference?
     */
    SCOPE_INSTANCE
  }

  /**
   * There are two principal types of advice targets: instance or class.
   * <p>
   * If an instance is registered on a {@link Weaver}, the case is clear: It only applies to a
   * {@link InstanceMethodAroundAdvice} if and only if that advice matches any instance methods.
   * <p>
   * If a class is registered on a {@link Weaver}, though, it could mean several things: For a
   * {@link InstanceMethodAroundAdvice} it could mean to globally match all instances for instance methods or to only match
   * static methods. So just registering a class here is ambiguous already. But further meanings are to match
   * constructors or type initialisers (static blocks) for the corresponding class.
   * <p>
   * This enumeration can express those registerable advice target types. See the description of each defined enum
   * constant for more details.
   */
  public enum AdviceTargetType {
    /**
     * The target object registered on the {@link Weaver} is meant to be a target for an instance method advice. This is
     * useful for mocking methods of specific target class instances. It is equally useful for cross-cutting concerns
     * such as logging, timing etc., the effect of which should be limited so certain object instances.
     * <p>
     * While this constant seems to be superfluous at first glance, in reality it is not because theoretically if the
     * target is a {@link Class} instance, it could also mean that an instance method advice for class {@link Class}
     * exists and is to be applied only to certain classes. This might be kind of esoteric, but it does not hurt to keep
     * open the opportunity to do something like this.
     */
    TARGET_INSTANCE,
    /**
     * The target class registered on the {@link Weaver} is meant to express that a matching advice ought to be applied
     * to all instances of the corresponding class. This is useful for global mocks the creation of which is outside the
     * control of the user and which are thuse not injectable. It is equally useful for cross-cutting concerns such as
     * logging, timing etc.
     */
    TARGET_INSTANCE_ALL,
    /**
     * The target class registered on the {@link Weaver} is meant to express that a matching advice ought to be applied
     * to static methods of the corresponding class. This is useful for mocking static methods. It is equally useful for
     * cross-cutting concerns such as logging, timing etc.
     */
    TARGET_STATIC_METHOD,
    /**
     * The target class registered on the {@link Weaver} is meant to express that a matching advice ought to be applied
     * to constructors of the corresponding class. This is useful for manipulating constructor parameters before
     * execution or to change the state of a new target instance right after creation mocking static methods. It is
     * equally useful for cross-cutting concerns such as logging, timing etc.
     */
    TARGET_CONSTRUCTOR,
    /**
     * The target class registered on the {@link Weaver} is meant to express that a matching advice ought to be applied
     * to type initialisers (static blocks) of the corresponding class. This is useful blocking type initialiser
     * execution or handling exceptions occurring inside of initialisers. It is equally useful for cross-cutting
     * concerns such as logging, timing etc.
     */
    TARGET_TYPE_INITIALISER
  }

  public enum AdviceType {
    INSTANCE_METHOD_ADVICE(
      Advice.to(InstanceMethodAspect.class, CLASS_FILE_LOCATOR),
      isMethod().and(not(isStatic().or(isAbstract()))),
      Collections.unmodifiableList(
        Arrays.asList(SCOPE_GLOBAL, SCOPE_MODULE, SCOPE_PACKAGE, SCOPE_CLASS, SCOPE_METHOD, SCOPE_INSTANCE)
      ),
      Collections.unmodifiableList(
        Arrays.asList(TARGET_INSTANCE, TARGET_INSTANCE_ALL)
      )
    ),
    STATIC_METHOD_ADVICE(
      Advice.to(StaticMethodAspect.class, CLASS_FILE_LOCATOR),
      isMethod().and(isStatic()),
      Collections.unmodifiableList(
        Arrays.asList(SCOPE_GLOBAL, SCOPE_MODULE, SCOPE_PACKAGE, SCOPE_CLASS, SCOPE_METHOD)
      ),
      Collections.singletonList(TARGET_STATIC_METHOD)
    ),
    CONSTRUCTOR_ADVICE(
      Advice.to(ConstructorAspect.class, CLASS_FILE_LOCATOR),
      isConstructor(),
      Collections.unmodifiableList(
        Arrays.asList(SCOPE_GLOBAL, SCOPE_MODULE, SCOPE_PACKAGE, SCOPE_CLASS, SCOPE_METHOD)
      ),
      Collections.singletonList(TARGET_CONSTRUCTOR)
    ),
    TYPE_INITIALISER_ADVICE(
      Advice.to(TypeInitialiserAspect.class, CLASS_FILE_LOCATOR),
      isTypeInitializer(),
      Collections.unmodifiableList(
        Arrays.asList(SCOPE_GLOBAL, SCOPE_MODULE, SCOPE_PACKAGE, SCOPE_CLASS)
      ),
      Collections.singletonList(TARGET_TYPE_INITIALISER)
    );

    private final Advice advice;
    private final ElementMatcher.Junction<MethodDescription> methodType;
    private final List<AdviceScope> allowedTargetScopes;
    private final List<AdviceTargetType> allowedTargetTypes;

    AdviceType(
      Advice advice,
      ElementMatcher.Junction<MethodDescription> methodType,
      List<AdviceScope> allowedTargetScopes,
      List<AdviceTargetType> allowedTargetTypes
    )
    {
      this.advice = advice;
      this.methodType = methodType;
      this.allowedTargetScopes = allowedTargetScopes;
      this.allowedTargetTypes = allowedTargetTypes;
    }

    public Advice getAdvice() {
      return advice;
    }

    public ElementMatcher.Junction<MethodDescription> getMethodType() {
      return methodType;
    }

    public List<AdviceScope> getAllowedTargetScopes() {
      return allowedTargetScopes;
    }

    public List<AdviceTargetType> getAllowedTargetTypes() {
      return allowedTargetTypes;
    }

    public static AdviceType forAdvice(AroundAdvice<?> aroundAdvice) throws IllegalArgumentException {
      return forAdviceClass(aroundAdvice.getClass());
    }

    private static AdviceType forAdviceClass(Class<?> aroundAdviceClass) throws IllegalArgumentException {
      if (aroundAdviceClass.equals(InstanceMethodAroundAdvice.class))
        return INSTANCE_METHOD_ADVICE;
      if (aroundAdviceClass.equals(StaticMethodAroundAdvice.class))
        return STATIC_METHOD_ADVICE;
      if (aroundAdviceClass.equals(ConstructorAroundAdvice.class))
        return CONSTRUCTOR_ADVICE;
      if (aroundAdviceClass.equals(TypeInitialiserAroundAdvice.class))
        return TYPE_INITIALISER_ADVICE;
      throw new IllegalArgumentException("unknown advice type " + aroundAdviceClass);
    }
  }

}
