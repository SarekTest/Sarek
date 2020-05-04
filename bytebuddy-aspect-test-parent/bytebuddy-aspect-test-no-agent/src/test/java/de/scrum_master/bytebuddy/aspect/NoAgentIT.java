package de.scrum_master.bytebuddy.aspect;

import de.scrum_master.app.Calculator;
import de.scrum_master.app.StringWrapper;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.UUID;

import static de.scrum_master.testing.TestHelper.isClassLoaded;
import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.Assert.*;

/**
 * This test checks features which do not involve bootloader classes.
 * So we do not need a Java agent here.
 * <p>
 * TODO: multiple advices on same object
 * TODO: static methods
 * TODO: global mocks
 * TODO: inject ByteBuddy + aspect framework into boot classloader in order to use non-loaded JRE classes
 */
public class NoAgentIT {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();

  private Weaver weaver;

  @After
  public void cleanUp() {
    if (weaver != null)
      weaver.unregisterTransformer();
  }

  @Test
  public void weaveLoadedApplicationClass() throws IOException {
    final String CLASS_NAME = "de.scrum_master.app.Calculator";

    // Load application class
    Calculator calculator = new Calculator();
    assertTrue(isClassLoaded(CLASS_NAME));

    // Create weaver, directly registering a target in the constructor
    weaver = new Weaver(
      INSTRUMENTATION,
      named(CLASS_NAME),
      named("add"),
      new AroundAdvice(
        null,
        (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
      ),
      calculator
    );

    // Registered target is affected by aspect, unregistered one is not
    assertEquals(55, calculator.add(2, 3));
    assertNotEquals(55, new Calculator().add(2, 3));

    // After unregistering the transformer, the target is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals(15, calculator.add(7, 8));
  }

  @Test
  public void cannotWeaveNotLoadedJREBootstrapClass() throws IOException {
    final String CLASS_NAME = "java.util.UUID";

    // Create weaver *before* bootstrap class is loaded (should not make a difference, but check anyway)
    assertFalse(isClassLoaded(CLASS_NAME));
    weaver = new Weaver(
      INSTRUMENTATION,
      named(CLASS_NAME),
      named("toString"),
      // No-op advice just passing through results and exceptions
      new AroundAdvice(
        (target, method, args) -> false,
        (target, method, args, proceedMode, returnValue, throwable) -> false
      )
    );

    // Load bootstrap class by instantiating it
    UUID uuid = UUID.randomUUID();
    assertTrue(isClassLoaded(CLASS_NAME));

    // Even though no target instance has been registered on the weaver, the aspect runs in order to check
    // if the advice should fire. But in order to do that, the aspect classes need to exist in the target
    // class' classloader, which in this case they do not because the test runs without the Java agent
    // injecting them into the boot classloader.
    assertThrows(NoClassDefFoundError.class, uuid::toString);
  }

  @Test
  public void cannotWeaveLoadedJREBootstrapClass() throws IOException {
    final String CLASS_NAME = "java.lang.String";

    // Create weaver *after* bootstrap class is loaded (should not make a difference, but check anyway)
    assertTrue(isClassLoaded(CLASS_NAME));
    weaver = new Weaver(
      INSTRUMENTATION,
      named(CLASS_NAME),
      named("replaceAll").and(takesArguments(String.class, String.class)),
      // No-op advice just passing through results and exceptions
      new AroundAdvice(
        (target, method, args) -> false,
        (target, method, args, proceedMode, returnValue, throwable) -> false
      )
    );

    // Even though no target instance has been registered on the weaver, the aspect runs in order to check
    // if the advice should fire. But in order to do that, the aspect classes need to exist in the target
    // class' classloader, which in this case they do not because the test runs without the Java agent
    // injecting them into the boot classloader.
    assertThrows(NoClassDefFoundError.class, () -> "uuid".replaceAll("foo", "bar"));
  }

  @Test
  public void complexAroundAdvice() throws IOException {
    StringWrapper TEXT = new StringWrapper("To be, or not to be, that is the question");

    Weaver weaver = new Weaver(
      INSTRUMENTATION,
      is(StringWrapper.class),
      named("replaceAll").and(takesArguments(String.class, String.class)),
      replaceAllAdvice()
    );

    // Before registering TEXT as an advice target instance, 'replaceAll' behaves normally
    assertEquals("To modify, or not to modify, that is the question", TEXT.replaceAll("be", "modify"));

    // Register target instance on weaver, then check expected aspect behaviour
    weaver.addTarget(TEXT);
    // (1) Proceed to target method without any modifications
    assertEquals("To eat, or not to eat, that is the question", TEXT.replaceAll("be", "eat"));
    // (2) Do not proceed to target method, let aspect modify input text instead
    assertEquals("T0 bε, 0r n0t t0 bε, that is thε quεsti0n", TEXT.replaceAll("be", "skip"));
    // (3) Aspect handles exception, returns dummy result
    assertEquals("caught exception from proceed", TEXT.replaceAll("be", "$1"));
    // (4) Aspect modifies replacement parameter
    assertEquals("To ❤, or not to ❤, that is the question", TEXT.replaceAll("be", "modify"));

    // Negative test: aspect has no effect on an instance not registered as a target
    StringWrapper noTarget = new StringWrapper("Let it be");
    assertEquals("Let it go", noTarget.replaceAll("be", "go"));
    assertEquals("Let it skip", noTarget.replaceAll("be", "skip"));
    assertEquals("Let it modify", noTarget.replaceAll("be", "modify"));

    // After unregistering TEXT as an advice target instance, 'replaceAll' behaves normally again
    weaver.removeTarget(TEXT);
    assertEquals("To modify, or not to modify, that is the question", TEXT.replaceAll("be", "modify"));
  }

  /**
   * This is an example for a somewhat more complex aspect doing the following:
   * 1. conditionally skip proceeding to target method
   * 2. conditionally modify method argument before proceeding
   * 3. catch exception thrown by target method and return a value instead
   * 4. in case target method was not called (proceed), return special value
   * 5. otherwise pass through return value from target method
   */
  private AroundAdvice replaceAllAdvice() {
    return new AroundAdvice(
      // Should proceed?
      (target, method, args) -> {
        String replacement = (String) args[1];
        if (replacement.equalsIgnoreCase("skip"))
          return false;
        if (replacement.equalsIgnoreCase("modify"))
          args[1] = "❤";
        return true;
      },

      // Handle result of (optional) proceed
      (target, method, args, proceedMode, returnValue, throwable) -> {
        if (throwable != null)
          return "caught exception from proceed";
        if (!proceedMode)
          return ((StringWrapper) target).replace("e", "ε").replace("o", "0");
        return returnValue;
      }
    );
  }

  @Test
  public void staticMethodCall() throws IOException {
    // Create weaver, directly registering a target class in the constructor
    weaver = new Weaver(
      INSTRUMENTATION,
      is(Calculator.class),
      named("greet"),
      new AroundAdvice(
        null,
        (target, method, args, proceedMode, returnValue, throwable) -> "Hi world!"
      ),
      Calculator.class
    );

    // Registered class is affected by aspect
    assertEquals("Hi world!", Calculator.greet("Sir"));

    // After unregistering the transformer, the class is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals("Hello Sir", Calculator.greet("Sir"));
  }

  @Test
  public void perClassAdvice() throws IOException {
    // Create weaver, directly registering a target class in the constructor
    weaver = new Weaver(
      INSTRUMENTATION,
      is(Calculator.class),
      isMethod(),
      new AroundAdvice(
        null,
        (target, method, args, proceedMode, returnValue, throwable) ->
          returnValue instanceof Integer
            ? ((int) returnValue) * 11
            : "Welcome, dear " + args[0]
      ),
      Calculator.class
    );

    // Registered class is affected by aspect, both for static and instance methods
    assertEquals("Welcome, dear Sir", Calculator.greet("Sir"));
    assertEquals(33, new Calculator().add(1, 2));

    // After unregistering the transformer, the class is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals("Hello Sir", Calculator.greet("Sir"));
    assertEquals(3, new Calculator().add(1, 2));
  }

}
