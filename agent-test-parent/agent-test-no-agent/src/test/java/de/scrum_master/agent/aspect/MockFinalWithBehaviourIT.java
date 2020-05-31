package de.scrum_master.agent.aspect;

import de.scrum_master.agent.constructor_mock.ConstructorMockRegistry;
import de.scrum_master.agent.constructor_mock.ConstructorMockTransformer;
import de.scrum_master.app.FinalClass;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.Assert.*;

/**
 * This test runs without a Java agent set via command line. It attaches it dynamically after adding it to the
 * bootstrap class loader's search path. The latter is only necessary if we want to mock constructors of classes which
 * are either bootstrap classes themselves or have bootstrap classes in their ancestry (direct or indirect parent
 * classes).
 * <p></p>
 * Furthermore, the test demonstrates how to retransform an already loaded class in order to not just create a mock
 * without subclassing but also add stub behaviour to it. This proves that both the constructor mock transformation as
 * well as the aspect advices do not change the class structure but only instrument constructor and method bodies. I.e.
 * that this is more flexible than e.g. removing 'final' modifiers because the latter change class/method signatures and
 * are not permitted in retransformations, so they have to be done during class-loading.
 */
public class MockFinalWithBehaviourIT {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();

  private ConstructorMockTransformer constructorMockTransformer;
  private Weaver weaver;

  @BeforeClass
  public static void beforeClass() throws IOException {
    useApplicationClassBeforeInstrumentation();

    // This property is usually set in Maven in order to tell us the path to the constructor mock agent.
    // Important: The JAR needs to contain Javassist too, so it should be the '-all' or '-all-special' artifact.
    JarFile constructorMockAgentJar = new JarFile(System.getProperty("constructor-mock-agent.jar"));
    // Inject constructor mock agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(constructorMockAgentJar);

    // This property is usually set in Maven in order to tell us the path to the aspect agent.
    // Important: The JAR needs to contain ByteBuddy too, so it should be the '-all' or '-all-special' artifact.
    // For now this test expects '-all'. With '-all-special' the ByteBuddy packages would be relocated to
    // 'de.scrum_master.jar.bytebuddy' and e.g. the ElementMatcher imports would need to be changed.
    JarFile aspectAgentJar = new JarFile(System.getProperty("aspect-agent.jar"));
    // Inject aspect agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(aspectAgentJar);
  }

  private static void useApplicationClassBeforeInstrumentation() {
    new FinalClass();
  }

  @Before
  public void beforeTest() {
    constructorMockTransformer = new ConstructorMockTransformer(FinalClass.class);
    // Important: set 'canRetransform' parameter to true
    INSTRUMENTATION.addTransformer(constructorMockTransformer, true);
  }

  @After
  public void afterTest() {
    INSTRUMENTATION.removeTransformer(constructorMockTransformer);
    constructorMockTransformer = null;
    if (weaver != null)
      weaver.unregisterTransformer();
    weaver = null;
  }

  @Test
  public void mockAndStubFinalClass() throws IOException {

    // (1) Before mocking is active, class under test behaves normally

    // Check instance methods
    FinalClass.resetInstanceCounter();
    new FinalClass().doSomething();
    assertEquals("Hello world", new FinalClass().greet("world"));
    assertEquals(3, new FinalClass().add(1, 2));
    assertEquals(12.3, new FinalClass().percentOf(123, 10), 1e-6);
    assertEquals('t', new FinalClass().firstChar("test"));
    assertTrue(new FinalClass().invert(false));

    // Each constructor call was executed
    assertEquals(6, FinalClass.getInstanceCounter());

    // Static methods
    assertEquals("foo bar zot", FinalClass.concatenate("foo", "bar", "zot"));
    assertEquals(12, FinalClass.multiply(3, 4), 1e-6);

    // (2) Mock both constructors and methods
    ConstructorMockRegistry.activate(FinalClass.class.getName());
    weaver = new Weaver(
      INSTRUMENTATION,
      is(FinalClass.class),
      any(),
      MethodAroundAdvice.MOCK,
      FinalClass.class
    );

    // (3) After mocking was activated, class under test behaves like a mock

    // Check instance methods
    FinalClass.resetInstanceCounter();
    new FinalClass().doSomething();
    assertNull(new FinalClass().greet("world"));
    assertEquals(0, new FinalClass().add(1, 2));
    assertEquals(0, new FinalClass().percentOf(123, 10), 1e-6);
    assertEquals(0, new FinalClass().firstChar("test"));
    assertFalse(new FinalClass().invert(false));

    // No(!) constructor call was executed
    assertEquals(0, FinalClass.getInstanceCounter());

    // Static methods
    assertNull(FinalClass.concatenate("foo", "bar", "zot"));
    assertEquals(0, FinalClass.multiply(3, 4), 1e-6);
  }

}
