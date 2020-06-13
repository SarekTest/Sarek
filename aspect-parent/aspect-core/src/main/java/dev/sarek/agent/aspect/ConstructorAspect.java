package dev.sarek.agent.aspect;

import net.bytebuddy.asm.Advice.*;
import net.bytebuddy.description.method.MethodDescription;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Stack;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

public abstract class ConstructorAspect extends Aspect<Constructor<?>> {

  // TODO: What happens if more than one transformer matches the same class?
  //       Idea: enable class to register Constructor objects as keys in adviceRegistry.
  //       This would allow to register multiple ConstructorAroundAdvices per class and
  //       make a per-constructor mocking/stubbing scheme easy to implement.
  // TODO: Try @Advice.Local for transferring additional state between before/after advices if necessary

  @SuppressWarnings("UnusedAssignment")
  @OnMethodEnter
  public static void before(
    @Origin Constructor<?> constructor,
    @AllArguments(readOnly = false, typing = DYNAMIC) Object[] args
  )
  {
    // Get advice for target object instance or target class
    // TODO: use @Advice.Local in order to communicate advice to 'after' method instead of a 2nd lookup
    ConstructorAroundAdvice advice = getAroundAdvice(constructor);

    // If no advice is registered, proceed to target method normally
    if (advice == null)
      return;

    // Copy 'args' array because ByteBuddy performs special bytecode manipulation on 'args'.
    // See also https://github.com/raphw/byte-buddy/issues/850#issuecomment-621387855.
    // The only way to make it back here for argument changes applied to the array in the delegate advice
    // called by advice.before() is to pass it a copy and then re-assign that copy back to 'args'.
    Object[] argsCopy = Arrays.copyOf(args, args.length);

    // Dispatch to before advice
    advice.before(constructor, argsCopy);

    // Assign back copied arguments array to 'args' because this assignment is how ByteBuddy recognises
    // that the user wants to pass on parameter changes.
    args = argsCopy;
  }

  @OnMethodExit(backupArguments = false)
  public static void after(
    @This(typing = DYNAMIC, optional = true) Object target,
    @Origin Constructor<?> constructor,
    @AllArguments(readOnly = false, typing = DYNAMIC) Object[] args
  )
  {
    // Get advice for target object instance or target class
    ConstructorAroundAdvice advice = getAroundAdvice(constructor);

    // If no advice is registered, just pass through result
    if (advice == null)
      return;

    advice.after(target, constructor, args);
  }

  /**
   * Keep this method public because it must be callable from advice code woven into other classes
   *
   * @param constructor
   * @return
   */
  public static ConstructorAroundAdvice getAroundAdvice(Constructor<?> constructor) {
    return doGetAdvice(constructor.getDeclaringClass(), constructor);
  }

  // TODO: make thread-safe, maybe use ThreadLocal<Stack<Object>>
  private final static Stack<Object> targets = new Stack<>();

  private static ConstructorAroundAdvice doGetAdvice(Object target, Constructor<?> constructor) {
    // Detect endless (direct) recursion leading to stack overflow, such as (schematically simplified):
    // getAroundAdvice(target) → adviceRegistry.get(target) → target.hashCode() → getAroundAdvice(target)
    if (!targets.empty() && targets.peek() == target) {
      // CAVEAT: Do not print 'target' here, it would lead to another endless recursion via:
      // target.toString() -> getAroundAdvice(target) → adviceRegistry.get(target) → target.toString()
      // This recursion would get detected but still run away because after detection it would be printed again etc.
      // It is actually best to not call *any* target methods while just trying to access and call an around advice.
      System.out.println("Recursion detected - origin: " + new Exception().getStackTrace()[2]);
      return null;
    }
    targets.push(target);
    try {
      return adviceRegistry
        .getValues(target)
        .stream()
        .filter(
          adviceDescription ->
            adviceDescription.adviceType.equals(AdviceType.CONSTRUCTOR_ADVICE)
              && adviceDescription.methodMatcher.matches(new MethodDescription.ForLoadedConstructor(constructor))
        )
        .map(adviceDescription -> (ConstructorAroundAdvice) adviceDescription.advice)
        .findFirst()
        .orElse(null);
    }
    finally {
      targets.pop();
    }
  }

}
