package de.scrum_master.bytebuddy.aspect;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.*;

import static de.scrum_master.bytebuddy.aspect.Aspect.TargetType.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class Aspect<T> {
  public static final Map<Object, MethodAroundAdvice> adviceRegistryXXX = Collections.synchronizedMap(new HashMap<>());

  private static final ClassFileLocator CLASS_FILE_LOCATOR = ClassFileLocator.ForClassLoader.ofSystemLoader();

  public enum TargetType {
    GLOBAL, MODULE, PACKAGE, CLASS, METHOD, INSTANCE
  }

  public enum AdviceType {
    METHOD_ADVICE(
      Advice.to(MethodAspect.class, CLASS_FILE_LOCATOR),
      isMethod(),
      GLOBAL, MODULE, PACKAGE, CLASS, INSTANCE
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
    private final SortedSet<TargetType> allowedTargetTypes = new TreeSet<>();

    AdviceType(Advice advice, ElementMatcher.Junction<MethodDescription> methodType, TargetType... allowedTargetTypes) {
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

    public SortedSet<TargetType> getAllowedTargetTypes() {
      return allowedTargetTypes;
    }
  }

}
