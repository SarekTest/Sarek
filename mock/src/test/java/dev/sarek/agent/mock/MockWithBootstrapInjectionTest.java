package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.Weaver;
import dev.sarek.agent.test.SeparateJVM;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.UUID;
import java.util.jar.JarFile;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(SeparateJVM.class)
public class MockWithBootstrapInjectionTest {
  @Test
  public void canMockBootstrapClassesAfterAmendingClassPath() throws IOException {
    addDependenciesToBootClassPath();
    assertNull(
      "Agent library classes must be loaded by the bootstrap class loader, which is not the case here. " +
        "This indicates that you did not run this test in a separate JVM.",
      Weaver.class.getClassLoader()
    );

    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(UUID.class)) {
      // Calling instrumented constructors/methods requires helper classes on the bootstrap classpath
      assertNull(new UUID(0xABBA, 0xCAFE).toString());
      //noinspection ConstantConditions
      assertNull(UUID.randomUUID());
    }

    // After auto-close, class transformations have been reverted
    assertTrue(new UUID(0xABBA, 0xCAFE).toString().contains("abba"));
    assertTrue(UUID.randomUUID().toString().matches("\\p{XDigit}+(-\\p{XDigit}+){4}"));
  }

  private static void addDependenciesToBootClassPath() throws IOException {
    Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();

    // This property is usually set in Maven in order to tell us the path to the constructor mock agent.
    // Important: The JAR needs to contain Javassist too, so it should be the '-all' or '-all-special' artifact.
    JarFile constructorMockAgentJar = new JarFile(System.getProperty("constructor-mock-agent.jar"));
    // Inject constructor mock agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(constructorMockAgentJar);

    // This property is usually set in Maven in order to tell us the path to the aspect agent.
    // Important: The JAR needs to contain ByteBuddy too, so it should be the '-all' or '-all-special' artifact.
    // For now this test expects '-all'. With '-all-special' the ByteBuddy packages would be relocated to
    // 'dev.sarek.jar.bytebuddy' and e.g. the ElementMatcher imports would need to be changed.
    JarFile aspectAgentJar = new JarFile(System.getProperty("aspect-core.jar"));
    // Inject aspect agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(aspectAgentJar);
  }

}
