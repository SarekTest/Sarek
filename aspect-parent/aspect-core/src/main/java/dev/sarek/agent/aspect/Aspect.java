package dev.sarek.agent.aspect;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.*;

import static dev.sarek.agent.aspect.Aspect.AdviceScope.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class Aspect<T> {
  public static final Map<Object, MethodAroundAdvice> adviceRegistryXXX = Collections.synchronizedMap(new HashMap<>());

  private static final ClassFileLocator CLASS_FILE_LOCATOR = ClassFileLocator.ForClassLoader.ofSystemLoader();

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
    GLOBAL,
    /**
     * Limit advice application to anything matched within a specific Java module.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     */
    MODULE,
    /**
     * Limit advice application to anything matched within a specific package and its subpackages.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * TODO: Modules and packages can intersect. How do we handle this?
     */
    PACKAGE,
    /**
     * Limit advice application to anything matched within a specific class.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * TODO: What about subclasses? Probably we shall not support them. Why recreate Aspect? This framework is mostly
     *       targeted to people looking for a mocking tool which can also be applied to already loaded classes, even
     *       JRE bootstrap classes.
     */
    CLASS,
    /**
     * Limit advice application to anything matching a specific method or constructor with a unique signature, i.e.
     * explicitly do not match multiple overloaded methods of the same name.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     */
    METHOD,
    /**
     * Limit advice application to a specific instance registered on the weaver.
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * <i>Not implemented yet.</i>
     * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
     * TODO: Instance scope intersecs with all other scopes. How do we handle preference?
     */
    INSTANCE
  }

  public enum AdviceType {
    METHOD_ADVICE(
      Advice.to(MethodAspect.class, CLASS_FILE_LOCATOR),
      isMethod(),
      GLOBAL, MODULE, PACKAGE, CLASS, METHOD, INSTANCE
    ),
    CONSTRUCTOR_ADVICE(
      Advice.to(ConstructorAspect.class, CLASS_FILE_LOCATOR),
      isConstructor(),
      GLOBAL, MODULE, PACKAGE, CLASS

    ),
    TYPE_INITIALISER_ADVICE(
      Advice.to(TypeInitialiserAspect.class, CLASS_FILE_LOCATOR),
      isTypeInitializer(),
      GLOBAL, MODULE, PACKAGE, CLASS
    );

    private final Advice advice;
    private final ElementMatcher.Junction<MethodDescription> methodType;
    private final SortedSet<AdviceScope> allowedTargetScopes = new TreeSet<>();

    AdviceType(Advice advice, ElementMatcher.Junction<MethodDescription> methodType, AdviceScope... allowedTargetScopes) {
      this.advice = advice;
      this.methodType = methodType;
      Collections.addAll(this.allowedTargetScopes, allowedTargetScopes);
    }

    public Advice getAdvice() {
      return advice;
    }

    public ElementMatcher.Junction<MethodDescription> getMethodType() {
      return methodType;
    }

    public SortedSet<AdviceScope> getAllowedTargetScopes() {
      return allowedTargetScopes;
    }

    public static AdviceType forAdvice(AroundAdvice<?> aroundAdvice) throws IllegalArgumentException {
      return forAdviceClass(aroundAdvice.getClass());
    }

    private static AdviceType forAdviceClass(Class<?> aroundAdviceClass) throws IllegalArgumentException {
      if (aroundAdviceClass.equals(MethodAroundAdvice.class))
        return METHOD_ADVICE;
      if (aroundAdviceClass.equals(ConstructorAroundAdvice.class))
        return CONSTRUCTOR_ADVICE;
      if (aroundAdviceClass.equals(TypeInitialiserAroundAdvice.class))
        return TYPE_INITIALISER_ADVICE;
      throw new IllegalArgumentException("unknown advice type " + aroundAdviceClass);
    }
  }

}
