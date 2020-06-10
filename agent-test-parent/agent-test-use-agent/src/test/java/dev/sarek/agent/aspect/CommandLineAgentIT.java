package dev.sarek.agent.aspect;

import dev.sarek.app.UnderTest;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static dev.sarek.agent.test.TestHelper.isClassLoaded;
import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.Assert.*;

/**
 * When running this test in an IDE like IntelliJ IDEA, please make sure that the JARs for both this module
 * ('bytebuddy-aspect-agent') and 'bytebuddy-aspect' have been created. Just run 'mvn package' first. In IDEA
 * you can also edit the run configuration for this test or a group of tests and add a "before launch" action,
 * select "run Maven goal" and then add goal 'package'.
 * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
 * Furthermore, make sure add this to the Maven Failsafe condiguration:
 * <argLine>-javaagent:target/aspect-agent-1.0-SNAPSHOT.jar</argLine>
 * Otherwise you will see a NoClassDefFoundError when running the tests for the bootstrap JRE classes because
 * boot class loader injection for the Java agent does not work as expected.
 */
public class CommandLineAgentIT {
  private Weaver weaver;

  @After
  public void cleanUp() {
    if (weaver != null)
      weaver.unregisterTransformer();
  }

  @Test
  public void weaveLoadedApplicationClass() throws IOException {
    final String CLASS_NAME = "dev.sarek.app.UnderTest";

    // Create application class instance
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
        isMethod().and(not(named("greet")))
      )
      .addTargets(underTest)
      .build();

    // Registered target is affected by aspect, unregistered one is not
    assertEquals(55, underTest.add(2, 3));
    assertNotEquals(55, new UnderTest().add(2, 3));

    // Matcher too broad (all methods of target class) + sloppy advice implementation
    // (assuming specific parameter types) -> runtime exception
    //noinspection ResultOfMethodCallIgnored
    assertThrows(ClassCastException.class, underTest::getName);

    // After unregistering the transformer, the target is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals(15, underTest.add(7, 8));
  }

  @Test
  public void weaveJREUtilityBootstrapClass() throws IOException {
    final String CLASS_NAME = "java.util.UUID";
    final String UUID_TEXT_STUB = "111-222-333-444";

    weaver = Weaver
      .forTypes(named(CLASS_NAME))
      .addAdvice(
        // Skip target method and return fixed result -> a classical stub
        new MethodAroundAdvice(
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) -> UUID_TEXT_STUB
        ),
        named("toString")
      )
      .build();

    // Load bootstrap class by instantiating it, if it was not loaded yet (should not make any difference)
    UUID uuid = UUID.randomUUID();
    assertTrue(isClassLoaded(CLASS_NAME));

    // The target instance has not been registered on the weaver yet
    assertNotEquals(UUID_TEXT_STUB, uuid.toString());

    // After registration on the weaver, the aspect affects the target instance
    weaver.addTarget(uuid);
    assertEquals(UUID_TEXT_STUB, uuid.toString());

    // Another instance is unaffected by the aspect
    assertNotEquals(UUID_TEXT_STUB, UUID.randomUUID().toString());

    // After deregistration, the target instance is also unaffected again
    weaver.removeTarget(uuid);
    assertNotEquals(UUID_TEXT_STUB, uuid.toString());

    // The same instance can be registered again
    weaver.addTarget(uuid);
    assertEquals(UUID_TEXT_STUB, uuid.toString());

    // After unregistering the whole transformer from instrumentation, the aspect is ineffective
    weaver.unregisterTransformer();
    assertNotEquals(UUID_TEXT_STUB, uuid.toString());
  }

  @Test
  public void weaveJRECoreBootstrapClass() throws IOException {
    final String CLASS_NAME = "java.lang.String";
    final String TEXT = "To be, or not to be, that is the question";

    // Create weaver *after* bootstrap class is loaded (should not make a difference, but check anyway)
    assertTrue(isClassLoaded(CLASS_NAME));
    weaver = Weaver
      .forTypes(named(CLASS_NAME))
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
    String noTarget = "Let it be";
    assertEquals("Let it go", noTarget.replaceAll("be", "go"));
    assertEquals("Let it skip", noTarget.replaceAll("be", "skip"));
    assertEquals("Let it modify", noTarget.replaceAll("be", "modify"));

    // After unregistering TEXT as an advice target instance, 'replaceAll' behaves normally again
    weaver.removeTarget(TEXT);
    assertEquals("To modify, or not to modify, that is the question", TEXT.replaceAll("be", "modify"));
  }

  @Test
  public void weaveStaticJREMethods() throws IOException {
    weaver = Weaver
      .forTypes(is(System.class))
      .addAdvice(
        new MethodAroundAdvice(
          (target, method, args) -> !args[0].equals("java.version"),
          (target, method, args, proceedMode, returnValue, throwable) -> proceedMode ? returnValue : "42"
        ),
        named("getProperty")
      )
      .addTargets(System.class)
      .build();

    // Only system property 'java.version' is mocked
    assertEquals("42", System.getProperty("java.version"));
    assertEquals("42", System.getProperty("java.version", "1.2.3"));
    assertNotEquals("42", System.getProperty("java.home"));
    assertNotEquals("42", System.getProperty("line.separator"));
  }

  @Test
  public void weavingNativeMethodsHasNoEffect() throws IOException {
    weaver = Weaver
      .forTypes(is(System.class))
      .addAdvice(
        new MethodAroundAdvice(
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) -> 123
        ),
        named("currentTimeMillis").or(named("nanoTime"))
      )
      .addTargets(System.class)
      .build();

    assertNotEquals(123, System.currentTimeMillis());
    assertNotEquals(123, System.nanoTime());
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
          return ((String) target).replace("e", "ε").replace("o", "0");
        return returnValue;
      }
    );
  }

  @Test
  public void createFile() throws IOException, URISyntaxException {
    final ThreadLocal<Integer> callCount = ThreadLocal.withInitial(() -> 0);

    // Before registering the transformer, the class is unaffected by the aspect
    assertEquals("foo", new File("foo").getName());
    assertEquals(0, (int) callCount.get());

    // Count all File constructor calls, modify first argument if it is a String
    weaver = Weaver
      .forTypes(is(File.class))
      .addAdvice(
        new ConstructorAroundAdvice(
          (constructor, args) -> {
            if (args[0] instanceof String)
              args[0] = "ADVISED";
            callCount.set(callCount.get() + 1);
          },
          null
        ),
        any()
      )
      .addTargets(File.class)
      .build();

    // First argument is a String -> the aspect modifies it + increments the call counter
    assertEquals("ADVISED", new File("foo").getName());
    assertEquals("ADVISED", new File("bar").getName());
    assertEquals("ADVISED", new File("parent", "child").getParent());
    // First argument is not a String -> the aspect only increments the call counter
    assertEquals("bar", new File(new URI("file:///foo/bar")).getName());
    assertEquals(4, (int) callCount.get());

    // After unregistering the transformer, the class is unaffected by the aspect
    weaver.unregisterTransformer();
    assertEquals("foo", new File("foo").getName());
    assertEquals(4, (int) callCount.get());
  }

}
