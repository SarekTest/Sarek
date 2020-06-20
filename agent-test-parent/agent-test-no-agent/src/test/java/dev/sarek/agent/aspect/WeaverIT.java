package dev.sarek.agent.aspect;

import dev.sarek.agent.test.SeparateJVM;
import dev.sarek.app.StringWrapper;
import dev.sarek.app.UnderTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.UUID;

import static dev.sarek.agent.test.TestHelper.isClassLoaded;
import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.Assert.*;

/**
 * This test checks features which do not involve bootloader classes.
 * So we do not need a Java agent here.
 * <ul>
 *   <li>
 *     TODO: multiple advices on same object
 *   </li>
 *   <li>
 *     TODO: inject ByteBuddy + aspect framework into boot class loader in order to use non-loaded JRE classes
 *   </li>
 *   <li>
 *     TODO: show how to weave already loaded classes via retransformation, also JRE bootstrap classes by adding
 *           the aspect agent JAR to to bootstrap class path
 *   </li>
 * </ul>
 */
@Category(SeparateJVM.class)
public class WeaverIT {
  private Weaver weaver;

  @After
  public void cleanUp() {
    if (weaver != null)
      weaver.unregisterTransformer();
  }

  @Test
  public void weaveLoadedApplicationClass() throws IOException {
    final String CLASS_NAME = "dev.sarek.app.UnderTest";

    // Load application class
    UnderTest underTest = new UnderTest();
    assertTrue(isClassLoaded(CLASS_NAME));

    // Create weaver, directly registering a target in the constructor
    weaver = Weaver
      .forTypes(named(CLASS_NAME))
      .addAdvice(
        named("add"),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        )
      )
      .addTargets(underTest)
      .build();

    // Registered target is affected by aspect, unregistered one is not
    assertEquals(55, underTest.add(2, 3));
    assertNotEquals(55, new UnderTest().add(2, 3));

    // After unregistering the transformer, the target is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals(15, underTest.add(7, 8));
  }

  @Test
  public void cannotWeaveJREUtilityBootstrapClass() throws IOException {
    final String CLASS_NAME = "java.util.UUID";

    weaver = Weaver
      .forTypes(named(CLASS_NAME))
      .addAdvice(
        // No-op advice just passing through results and exceptions
        named("toString"),
        new InstanceMethodAroundAdvice(
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) -> false
        )
      )
      .build();

    // Load bootstrap class by instantiating it, if it was not loaded yet (should not make any difference)
    UUID uuid = UUID.randomUUID();
    assertTrue(isClassLoaded(CLASS_NAME));

    // Even though no target instance has been registered on the weaver, the aspect runs in order to check
    // if the advice should fire. But in order to do that, the aspect classes need to exist in the target
    // class' class loader, which in this case they do not because the test runs without the Java agent
    // injecting them into the boot class loader.
    //noinspection ResultOfMethodCallIgnored
    assertThrows(NoClassDefFoundError.class, uuid::toString);
  }

  @Test
  public void cannotWeaveJRECoreBootstrapClass() throws IOException {
    final String CLASS_NAME = "java.lang.String";

    // Create weaver *after* bootstrap class is loaded (should not make a difference, but check anyway)
    assertTrue(isClassLoaded(CLASS_NAME));
    weaver = Weaver
      .forTypes(named(CLASS_NAME))
      .addAdvice(
        // No-op advice just passing through results and exceptions
        named("replaceAll").and(takesArguments(String.class, String.class)),
        new InstanceMethodAroundAdvice(
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) -> false
        )
      )
      .build();

    // Even though no target instance has been registered on the weaver, the aspect runs in order to check
    // if the advice should fire. But in order to do that, the aspect classes need to exist in the target
    // class' class loader, which in this case they do not because the test runs without the Java agent
    // injecting them into the boot class loader.
    //noinspection ResultOfMethodCallIgnored
    assertThrows(NoClassDefFoundError.class, () -> "dummy".replaceAll("foo", "bar"));
  }

  @Test
  public void complexAroundAdvice() throws IOException {
    StringWrapper TEXT = new StringWrapper("To be, or not to be, that is the question");

    Weaver weaver = Weaver
      .forTypes(is(StringWrapper.class))
      .addAdvice(
        named("replaceAll").and(takesArguments(String.class, String.class)),
        replaceAllAdvice()
      )
      .build();

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
  private InstanceMethodAroundAdvice replaceAllAdvice() {
    return new InstanceMethodAroundAdvice(
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
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        named("greet"),
        new StaticMethodAroundAdvice(
          null,
          (method, args, proceedMode, returnValue, throwable) -> "Hi world!"
        )
      )
      .addTargets(UnderTest.class)
      .build();

    // Registered class is affected by aspect
    assertEquals("Hi world!", UnderTest.greet("Sir"));

    // After unregistering the transformer, the class is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals("Hello Sir", UnderTest.greet("Sir"));
  }

  @Test
  public void perClassAdvice() throws IOException {
    // Create weaver, directly registering a target class in the constructor
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        isMethod(),
        new StaticMethodAroundAdvice(
          null,
          (method, args, proceedMode, returnValue, throwable) ->
            returnValue instanceof Integer
              ? ((int) returnValue) * 11
              : "Welcome, dear " + args[0]
        )
      )
      .addTargets(UnderTest.class)
      .build();

    // Static method is affected by aspect
    assertEquals("Welcome, dear Sir", UnderTest.greet("Sir"));
    // Instance method is unaffected by aspect
    assertEquals(3, new UnderTest().add(1, 2));

    // After unregistering the transformer, the class is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals("Hello Sir", UnderTest.greet("Sir"));
    assertEquals(3, new UnderTest().add(1, 2));
  }

  @Test
  public void constructorAdvice() throws IOException {
    // Create weaver, directly registering a target class in the constructor
    final ThreadLocal<Integer> callCount = ThreadLocal.withInitial(() -> 0);

    // Before registering the transformer, the class is unaffected by the aspect
    assertEquals("whatever", new UnderTest("whatever").getName());
    assertEquals(0, (int) callCount.get());

    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        takesArguments(String.class),
        new ConstructorAroundAdvice(
          (constructor, args) -> {
            args[0] = "ADVISED";
            callCount.set(callCount.get() + 1);
          },
          null
        )
      )
      .addTargets(UnderTest.class)
      .build();

    // Registered class is affected by aspect
    assertEquals("ADVISED", new UnderTest("whatever").getName());
    assertEquals("ADVISED", new UnderTest("whenever").getName());
    assertEquals(2, (int) callCount.get());

    // After unregistering the transformer, the class is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals("whatever", new UnderTest("whatever").getName());
    assertEquals(2, (int) callCount.get());
  }

  @Test
  public void multiAdvice() throws IOException {
    UnderTest underTest = new UnderTest();
    UnderTest underTestUnregistered = new UnderTest();
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        named("add"),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        )
      )
      .addAdvice(
        named("multiply"),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) / 5
        )
      )
      .addAdvice(
        named("greet"),
        new StaticMethodAroundAdvice(
          null,
          (method, args, proceedMode, returnValue, throwable) -> "Hi world!"
        )
      )
      .addAdvice(
        takesArguments(String.class),
        new ConstructorAroundAdvice(
          (constructor, args) -> args[0] = "ADVISED",
          null
        )
      )
      .addTargets(underTest, UnderTest.class)
      .build();

    // Weaver is only active for registered instance and we can advise multiple instance methods for the same advice
    assertEquals(55, underTest.add(2, 3));
    assertEquals(3, underTest.multiply(3, 6));
    assertEquals(5, underTestUnregistered.add(2, 3));
    assertEquals(18, underTestUnregistered.multiply(3, 6));
    // Weaver is also active for static method and constructor
    assertEquals("Hi world!", UnderTest.greet("Sir"));
    assertEquals("ADVISED", new UnderTest("whatever").getName());

    // Now there are two registered instances
    weaver.addTarget(underTestUnregistered);
    assertEquals(55, underTest.add(2, 3));
    assertEquals(55, underTestUnregistered.add(2, 3));
    assertEquals(5, new UnderTest().add(2, 3));

    // If no more instances are registered but still the class is registered, no instances will be advised
    weaver.removeTarget(underTest);
    weaver.removeTarget(underTestUnregistered);
    assertEquals(5, underTest.add(2, 3));
    assertEquals(5, underTestUnregistered.add(2, 3));
    assertEquals(5, new UnderTest().add(2, 3));

    // If the class target is also removed, no static methods and constructors will be advised either
    weaver.removeTarget(UnderTest.class);
    assertEquals(5, underTest.add(2, 3));
    assertEquals(5, underTestUnregistered.add(2, 3));
    assertEquals(5, new UnderTest().add(2, 3));
    assertEquals("Hello Sir", UnderTest.greet("Sir"));
    assertEquals("whatever", new UnderTest("whatever").getName());

    // If a global instance target is registered, all instances are advised, but no static methods or constructors
    weaver.addTarget(GlobalInstance.of(UnderTest.class));
    assertEquals(55, underTest.add(2, 3));
    assertEquals(55, underTestUnregistered.add(2, 3));
    assertEquals(55, new UnderTest().add(2, 3));
    assertEquals("Hello Sir", UnderTest.greet("Sir"));
    assertEquals("whatever", new UnderTest("whatever").getName());
  }

  @Test
  public void multiAdvicePrecedence() throws IOException {
    UnderTest underTest = new UnderTest("John Doe");
    UnderTest underTestUnregistered = new UnderTest("Nobody");

    // Scenario 1: advices ordered from more to less specific
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        named("add"),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        )
      )
      .addAdvice(
        takesArguments(int.class, int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) / 5
        )
      )
      .addAdvice(
        returns(int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> 42
        )
      )
      .addAdvice(
        any(),
        InstanceMethodAroundAdvice.MOCK
      )
      .addTargets(underTest)
      .build();

    // Weaver is only active for registered instance
    assertEquals(5, underTestUnregistered.add(2, 3));
    assertEquals(18, underTestUnregistered.multiply(3, 6));
    assertEquals(-11, underTestUnregistered.negate(11));
    assertEquals("Nobody", underTestUnregistered.getName());

    // Advice matching is sensitive to chronological order in which advices were added
    assertEquals(55, underTest.add(2, 3));
    assertEquals(3, underTest.multiply(3, 6));
    assertEquals(42, underTest.negate(11));
    assertNull(underTest.getName());

    weaver.unregisterTransformer();

    // Scenario 2: advice #2 is less specific than #3, so #3 is never in effect
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        named("add"),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        )
      )
      .addAdvice(
        returns(int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> 42
        )
      )
      .addAdvice(
        takesArguments(int.class, int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) / 5
        )
      )
      .addAdvice(
        any(),
        InstanceMethodAroundAdvice.MOCK
      )
      .addTargets(underTest)
      .build();

    // Advice matching is sensitive to chronological order in which advices were added
    assertEquals(55, underTest.add(2, 3));
    assertEquals(42, underTest.multiply(3, 6));
    assertEquals(42, underTest.negate(11));
    assertNull(underTest.getName());

    weaver.unregisterTransformer();

    // Scenario 3: advice #1 is less specific than #2 and #3, so #2 and #3 are never in effect
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        returns(int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> 42
        )
      )
      .addAdvice(
        named("add"),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        )
      )
      .addAdvice(
        takesArguments(int.class, int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) / 5
        )
      )
      .addAdvice(
        any(),
        InstanceMethodAroundAdvice.MOCK
      )
      .addTargets(underTest)
      .build();

    // Advice matching is sensitive to chronological order in which advices were added
    assertEquals(42, underTest.add(2, 3));
    assertEquals(42, underTest.multiply(3, 6));
    assertEquals(42, underTest.negate(11));
    assertNull(underTest.getName());

    weaver.unregisterTransformer();

    // Scenario 4: advice #1 is less specific than all the others, so none of them are never in effect
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        any(),
        InstanceMethodAroundAdvice.MOCK
      )
      .addAdvice(
        named("add"),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        )
      )
      .addAdvice(
        takesArguments(int.class, int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) / 5
        )
      )
      .addAdvice(
        returns(int.class),
        new InstanceMethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> 42
        )
      )
      .addTargets(underTest)
      .build();

    // Advice matching is sensitive to chronological order in which advices were added
    assertEquals(0, underTest.add(2, 3));
    assertEquals(0, underTest.multiply(3, 6));
    assertEquals(0, underTest.negate(11));
    assertNull(underTest.getName());

  }

}
