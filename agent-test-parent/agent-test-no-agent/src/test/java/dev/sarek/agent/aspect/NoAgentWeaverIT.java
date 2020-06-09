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
    weaver = new Weaver(
      named(CLASS_NAME),
      named("add"),
      new MethodAroundAdvice(
        null,
        (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11
      ),
      underTest
    );

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

    weaver = new Weaver(
      named(CLASS_NAME),
      named("toString"),
      // No-op advice just passing through results and exceptions
      new MethodAroundAdvice(
        (target, method, args) -> false,
        (target, method, args, proceedMode, returnValue, throwable) -> false
      )
    );

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
    weaver = new Weaver(
      named(CLASS_NAME),
      named("replaceAll").and(takesArguments(String.class, String.class)),
      // No-op advice just passing through results and exceptions
      new MethodAroundAdvice(
        (target, method, args) -> false,
        (target, method, args, proceedMode, returnValue, throwable) -> false
      )
    );

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

    Weaver weaver = new Weaver(
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
    weaver = new Weaver(
      is(UnderTest.class),
      named("greet"),
      new MethodAroundAdvice(
        null,
        (target, method, args, proceedMode, returnValue, throwable) -> "Hi world!"
      ),
      UnderTest.class
    );

    // Registered class is affected by aspect
    assertEquals("Hi world!", UnderTest.greet("Sir"));

    // After unregistering the transformer, the class is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals("Hello Sir", UnderTest.greet("Sir"));
  }

  @Test
  public void perClassAdvice() throws IOException {
    // Create weaver, directly registering a target class in the constructor
    weaver = new Weaver(
      is(UnderTest.class),
      isMethod(),
      new MethodAroundAdvice(
        null,
        (target, method, args, proceedMode, returnValue, throwable) ->
          returnValue instanceof Integer
            ? ((int) returnValue) * 11
            : "Welcome, dear " + args[0]
      ),
      UnderTest.class
    );

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

    weaver = new Weaver(
      is(UnderTest.class),
      takesArguments(String.class),
      new ConstructorAroundAdvice(
        (constructor, args) -> {
          args[0] = "ADVISED";
          callCount.set(callCount.get() + 1);
        },
        null
      ),
      UnderTest.class
    );

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
  public void multipleWeavers() throws IOException {
    UnderTest underTest = new UnderTest();
    weaver = new Weaver(
      is(UnderTest.class),
      named("add"),
      new MethodAroundAdvice(null, (target, method, args, proceedMode, returnValue, throwable) -> ((int) returnValue) * 11),
      underTest
    );

    // Weaver is active for registered instance
    assertEquals(55, underTest.add(2, 3));

    Weaver weaver2 = new Weaver(
      is(UnderTest.class),
      named("greet"),
      new MethodAroundAdvice(null, (target, method, args, proceedMode, returnValue, throwable) -> {
        System.out.println("### greet1");
        return "Hi world!";
      }),
      UnderTest.class
    );
//    new Weaver(
//      INSTRUMENTATION,
//      is(UnderTest.class),
//      named("greet"),
//      new MethodAroundAdvice(null, (target, method, args, proceedMode, returnValue, throwable) -> {
//        System.out.println("### greet2");
//        return returnValue + "x";
//      }),
//      UnderTest.class
//    );

    // Both weavers are active because the second one targets a static method and thus is registered under the class
    assertEquals(55, underTest.add(2, 3));
    assertEquals("Hi world!", UnderTest.greet("Sir"));

    // Cannot create a weaver, trying to register an already registered target to it from the constructor
    assertThrows(
      IllegalArgumentException.class,
      () -> new Weaver(is(UnderTest.class), any(), new MethodAroundAdvice(null, null), underTest)
    );

    // Cannot create a weaver, trying to register an already registered target to it from the constructor
    assertThrows(
      IllegalArgumentException.class,
      () -> new Weaver(is(UnderTest.class), any(), new MethodAroundAdvice(null, null), UnderTest.class)
    );

    // Both weavers are active because the second one targets a static method and thus is registered under the class
    assertEquals(55, underTest.add(2, 3));
    assertEquals("Hi world!", UnderTest.greet("Sir"));

    //After unregistering one target, it can be registered on another weaver
    weaver2.removeTarget(UnderTest.class);
    weaver.addTarget(UnderTest.class);

    // Now the aspect advising 'greet' is global because the whole class has been registered
    assertEquals(55, underTest.add(2, 3));
    assertEquals(99, underTest.add(4, 5));

    // Another advice type can be registered for the same class (method vs. constructor)
    Weaver weaver3 = new Weaver(
      is(UnderTest.class),
      takesArguments(String.class),
      new ConstructorAroundAdvice((constructor, args) -> args[0] = "ADVISED", null),
      UnderTest.class
    );

    // Now both class-global aspects are active at the same time
    assertEquals(99, underTest.add(4, 5));
    assertEquals("ADVISED", new UnderTest("whatever").getName());

    // House-keeping: unregister transformers which are not taken care of by the @After method
    weaver2.unregisterTransformer();
    weaver3.unregisterTransformer();
  }

}
