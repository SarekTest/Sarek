package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.Weaver;
import dev.sarek.agent.constructor_mock.ConstructorMockTransformer;
import dev.sarek.agent.test.SeparateJVM;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.UUID;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

@Category(SeparateJVM.class)
public class MockWithBootstrapInjectionTest {

  @BeforeClass
  public static void addDependenciesToBootClassPath() throws IOException {
    Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();

    // This property is usually set in Maven in order to tell us the path to the constructor mock agent.
    // Important: The JAR needs to contain Javassist too, so it should be the '-all' or '-all-special' artifact.
    JarFile constructorMockAgentJar = new JarFile(System.getProperty("constructor-mock-agent.jar"));
    // Inject constructor mock agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(constructorMockAgentJar);
    assertNull(
      "Agent library classes must be loaded by the bootstrap class loader, which is not the case here. " +
        "This indicates that you did not run this test in a separate JVM.",
      ConstructorMockTransformer.class.getClassLoader()
    );

    // This property is usually set in Maven in order to tell us the path to the aspect agent.
    // Important: The JAR needs to contain ByteBuddy too, so it should be the '-all' or '-all-special' artifact.
    // For now this test expects '-all'. With '-all-special' the ByteBuddy packages would be relocated to
    // 'dev.sarek.jar.bytebuddy' and e.g. the ElementMatcher imports would need to be changed.
    JarFile aspectAgentJar = new JarFile(System.getProperty("aspect-core.jar"));
    // Inject aspect agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(aspectAgentJar);
    assertNull(
      "Agent library classes must be loaded by the bootstrap class loader, which is not the case here. " +
        "This indicates that you did not run this test in a separate JVM.",
      Weaver.class.getClassLoader()
    );
  }

  @Test
  public void canMockBootstrapClass_UUID() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mockUUID = new Mock(UUID.class)) {
      assertNull(new UUID(0xABBA, 0xCAFE).toString());
      //noinspection ConstantConditions
      assertNull(UUID.randomUUID());
    }

    // After auto-close, class transformations have been reverted
    assertTrue(new UUID(0xABBA, 0xCAFE).toString().contains("abba"));
    assertTrue(UUID.randomUUID().toString().matches("\\p{XDigit}+(-\\p{XDigit}+){4}"));
  }

  @Test
  public void canMockBootstrapClass_FileInputStream() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      Mock MockFile = new Mock(File.class);
      Mock mockFIS = new Mock(FileInputStream.class)
    ) {
      File file = new File("CTeWTxRxRTmdf8JtvzmC");
      assertEquals(0, file.hashCode());
      //noinspection ConstantConditions
      assertNull(file.getName());

      FileInputStream fileInputStream = new FileInputStream(file);
      assertEquals(0, fileInputStream.read());
      assertNull(fileInputStream.getFD());
    }

    // After auto-close, class transformations have been reverted
    File file = new File("CTeWTxRxRTmdf8JtvzmC");
    assertNotEquals(0, file.hashCode());
    assertNotNull(file.getName());

    assertThrows(FileNotFoundException.class, () -> new FileInputStream(file));
  }
}
