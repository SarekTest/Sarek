package dev.sarek.agent.aspect;

import dev.sarek.app.StringWrapper;
import dev.sarek.app.UnderTest;
import org.junit.After;
import org.junit.Test;

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
 *           the aspect agent JAR to to bopotstrap class path
 *   </li>
 * </ul>
 */
public class NoAgentWeaverIT {
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
        new MethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        ),
        named("add")
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
        new MethodAroundAdvice(
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) -> false
        ),
        named("toString")
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
        new MethodAroundAdvice(
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) -> false
        ),
        named("replaceAll").and(takesArguments(String.class, String.class))
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
        replaceAllAdvice(),
        named("replaceAll").and(takesArguments(String.class, String.class))
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
  private MethodAroundAdvice replaceAllAdvice() {
    return new MethodAroundAdvice(
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
        new MethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> "Hi world!"
        ),
        named("greet")
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
        new MethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) ->
            returnValue instanceof Integer
              ? ((int) returnValue) * 11
              : "Welcome, dear " + args[0]
        ),
        isMethod()
      )
      .addTargets(UnderTest.class)
      .build();

    // Registered class is affected by aspect, both for static and instance methods
    assertEquals("Welcome, dear Sir", UnderTest.greet("Sir"));
    assertEquals(33, new UnderTest().add(1, 2));

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
        new ConstructorAroundAdvice(
          (constructor, args) -> {
            args[0] = "ADVISED";
            callCount.set(callCount.get() + 1);
          },
          null
        ),
        takesArguments(String.class)
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
  public void multiAdviceWeaver() throws IOException {
    UnderTest underTest = new UnderTest();
    UnderTest underTestUnregistered = new UnderTest();
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        new MethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
        ),
        named("add")
      )
      .addAdvice(
        new MethodAroundAdvice(
          null,
          (target, method, args, proceedMode, returnValue, throwable) -> "Hi world!"
        ),
        named("greet")
      )
      .addAdvice(
        new ConstructorAroundAdvice(
          (constructor, args) -> args[0] = "ADVISED",
          null
        ),
        takesArguments(String.class)
      )
      .addTargets(underTest)
      .build();

    // Weaver is only active for registered instance
    assertEquals(55, underTest.add(2, 3));
    assertEquals(5, underTestUnregistered.add(2, 3));

    // Cannot create a weaver, trying to register an already registered target to it from the constructor
    // TODO: Implement n-to-m relationships between targets and advices, otherwise mocking different instance/static
    //       methods or different constructors in the same class is impossible.
    assertThrows(
      IllegalArgumentException.class,
      () -> Weaver
        .forTypes(is(UnderTest.class))
        .addAdvice(new MethodAroundAdvice(null, null), any())
        .addTargets(underTest)
        .build()
    );

    // TODO: Currently when a class is registered, e.g. in order to support constructor, static method or type
    //       initialiser advices, it also has the effect of globally affecting instance methods -> re-implement a way to
    //       differentiate between advice types.
    weaver.removeTarget(underTest);
    weaver.addTarget(UnderTest.class);

    // Cannot create a weaver, trying to register an already registered target to it from the constructor
    // TODO: Implement n-to-m relationships between targets and advices, otherwise mocking different instance/static
    //       methods or different constructors in the same class is impossible.
    assertThrows(
      IllegalArgumentException.class,
      () -> Weaver
        .forTypes(is(UnderTest.class))
        .addAdvice(new MethodAroundAdvice(null, null), any())
        .addTargets(UnderTest.class)
        .build()
    );

    assertEquals(55, underTest.add(2, 3));
    assertEquals(55, underTestUnregistered.add(2, 3));
    assertEquals("Hi world!", UnderTest.greet("Sir"));
    assertEquals("ADVISED", new UnderTest("whatever").getName());
  }

}
