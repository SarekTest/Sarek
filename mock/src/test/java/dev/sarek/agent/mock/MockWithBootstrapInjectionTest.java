package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.Weaver;
import dev.sarek.agent.constructor_mock.ConstructorMockTransformer;
import dev.sarek.agent.test.SeparateJVM;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Random;
import java.util.UUID;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

@SuppressWarnings(
  { "ConstantConditions", "StringBufferReplaceableByString", "StringBufferMayBeStringBuilder", "unused" }
)
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
    try (Mock mock = new Mock(UUID.class)) {
      assertNull(new UUID(0xABBA, 0xCAFE).toString());
      assertNull(UUID.randomUUID());
    }

    // After auto-close, class transformations have been reverted
    assertTrue(new UUID(0xABBA, 0xCAFE).toString().contains("abba"));
    assertTrue(UUID.randomUUID().toString().matches("\\p{XDigit}+(-\\p{XDigit}+){4}"));
  }

  @Test
  public void canMockBootstrapClass_FileInputStream() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(File.class, FileInputStream.class)) {
      File file = new File("CTeWTxRxRTmdf8JtvzmC");
      assertEquals(0, file.hashCode());
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

  @Test
  public void canMockBootstrapClass_StringBuffer() throws IOException {
    ConstructorMockTransformer.LOG_CONSTRUCTOR_MOCK = true;
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      // Use Mock constructor with String arguments here because AbstractStringBuilder is package-scoped
      // and we cannot directly refer to it via AbstractStringBuilder.class
      Mock mock = new Mock(
//        "java.lang.AbstractStringBuilder",
//        "java.lang.StringBuilder",
        "java.lang.StringBuffer"
      )
    )
    {
      StringBuffer stringBuffer = new StringBuffer("dummy");
      stringBuffer.append(42);
      stringBuffer.append("foo");
      assertNull(stringBuffer.toString());

      // TODO: Activate again after making mocks more individually configurable. At the moment, globally mocking
      //       StringBuilder and/or AbstractStringBuilder fails because those classes are also used internally for
      //       logging.
//      StringBuilder stringBuilder = new StringBuilder("dummy");
//      stringBuilder.append(42);
//      stringBuilder.append("foo");
//      assertNull(stringBuilder.toString());
    }
    finally {
      ConstructorMockTransformer.LOG_CONSTRUCTOR_MOCK = false;
    }
  }

  @Test
  public void canMockBootstrapClass_URL() throws IOException, URISyntaxException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(URL.class, URI.class)) {
      URL url = new URL("invalid URL, no problem");
      assertNull(url.getHost());
      assertNull(url.getContent());

      URI uri = new URI("invalid URI, no problem");
      assertNull(uri.getHost());
      assertNull(uri.getQuery());
    }
  }

  @Test
  public void canMockBootstrapClass_Random() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(Random.class)) {
      Random random = new Random();
      assertEquals(0, random.nextInt());
      assertEquals(0, random.nextDouble(), 1e-6);
    }
  }

  @Test
  public void canMockBootstrapClasses_Swing() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(JTable.class, GroupLayout.class, JTextField.class, JTextComponent.class)) {
      JTable jTable = new JTable(3, 3);
      assertEquals(0, jTable.getRowCount());
      assertEquals(0, jTable.getColumnCount());
      assertNull(new GroupLayout(null).getLayoutStyle());
      assertNull(new JTextField().getSelectedTextColor());
    }
  }

}
