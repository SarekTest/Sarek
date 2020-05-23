package de.scrum_master.agent.aspect;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.*;

import static de.scrum_master.agent.aspect.Aspect.AdviceScope.*;
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
     * <p>
     * <i>Not implemented yet.</i>
     */
    GLOBAL,
    /**
     * Limit advice application to anything matched within a specific Java module.
     * <p>
     * <i>Not implemented yet.</i>
     */
    MODULE,
    /**
     * Limit advice application to anything matched within a specific package and its subpackages.
     * <p>
     * <i>Not implemented yet.</i>
     * <p>
     * TODO: Modules and packages can intersect. How do we handle this?
     */
    PACKAGE,
    /**
     * Limit advice application to anything matched within a specific class.
     * <p>
     * <i>Not implemented yet.</i>
     * <p>
     * TODO: What about subclasses? Probably we shall not support them. Why recreate Aspect? This framework is mostly
     *       targeted to people looking for a mocking tool which can also be applied to already loaded classes, even
     *       JRE bootstrap classes.
     */
    CLASS,
    /**
     * Limit advice application to anything matching a specific method or constructor with a unique signature, i.e.
     * explicitly do not match multiple overloaded methods of the same name.
     * <p>
     * <i>Not implemented yet.</i>
     */
    METHOD,
    /**
     * Limit advice application to a specific instance registered on the weaver.
     * <p>
     * <i>Not implemented yet.</i>
     * <p>
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
    private final SortedSet<AdviceScope> allowedTargetTypes = new TreeSet<>();

    AdviceType(Advice advice, ElementMatcher.Junction<MethodDescription> methodType, AdviceScope... allowedTargetTypes) {
      this.advice = advice;
      this.methodType = methodType;
      Collections.addAll(this.allowedTargetTypes, allowedTargetTypes);
    }

    public Advice getAdvice() {
      return advice;
    }

    public ElementMatcher.Junction<MethodDescription> getMethodType() {
      return methodType;
    }

    public SortedSet<AdviceScope> getAllowedTargetTypes() {
      return allowedTargetTypes;
    }
  }

}
