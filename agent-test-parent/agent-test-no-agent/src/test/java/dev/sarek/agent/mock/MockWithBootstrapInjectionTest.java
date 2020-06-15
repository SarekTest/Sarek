package dev.sarek.agent.mock;

import dev.sarek.agent.Agent;
import dev.sarek.agent.constructor_mock.ConstructorMockTransformer;
import dev.sarek.agent.test.SeparateJVM;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.swing.*;
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

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.Assert.*;

@SuppressWarnings(
  { "ConstantConditions", "StringBufferReplaceableByString", "StringBufferMayBeStringBuilder", "unused" }
)
@Category(SeparateJVM.class)
public class MockWithBootstrapInjectionTest {

  @BeforeClass
  public static void addDependenciesToBootClassPath() throws IOException {
    Instrumentation INSTRUMENTATION = Agent.getInstrumentation();

    // This property is usually set in Maven in order to tell us the path to the constructor mock agent.
    // Important: The JAR needs to contain Javassist too, so it should be the '-all' or '-all-special' artifact.
    JarFile sarekAllJar = new JarFile(System.getProperty("sarek-all.jar"));
    // Inject constructor mock agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(sarekAllJar);
    assertNull(
      "Agent library classes must be loaded by the bootstrap class loader, which is not the case here. " +
        "This indicates that you did not run this test in a separate JVM.",
      ConstructorMockTransformer.class.getClassLoader()
    );
  }

  @Test
  public void canMockBootstrapClass_UUID() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<UUID> mockFactory = MockFactory
        .forClass(UUID.class)
        .mockStaticMethods(true)  // include static methods, too
        .provideHashCodeMethod()
        .global()
        .build()
    )
    {
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
    try (
      MockFactory<File> mockFactory1 = MockFactory.forClass(File.class).provideHashCodeMethod().global().build();
      MockFactory<FileInputStream> mockFactory2 = MockFactory.forClass(FileInputStream.class).global().build()
    )
    {
      File file = new File("CTeWTxRxRTmdf8JtvzmC");
      // Check that HashCodeAspect was applied
      assertEquals(System.identityHashCode(file), file.hashCode());
      assertNull(file.getName());

      FileInputStream fileInputStream = new FileInputStream(file);
      assertEquals(0, fileInputStream.read());
      assertNull(fileInputStream.getFD());
    }

    // After auto-close, class transformations have been reverted
    File file = new File("CTeWTxRxRTmdf8JtvzmC");
    assertNotEquals(System.identityHashCode(file), file.hashCode());
    assertNotNull(file.getName());
    assertThrows(FileNotFoundException.class, () -> new FileInputStream(file));
  }

  @Test
  public void canMockBootstrapClass_StringBuffer() throws IOException, ClassNotFoundException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<StringBuffer> mockFactory1 = MockFactory
        .forClass(StringBuffer.class)
        .global()
        // Use String class name here because AbstractStringBuilder is package-scoped and we cannot directly
        // refer to it via AbstractStringBuilder.class
        .excludeSuperTypes(named("java.lang.AbstractStringBuilder"))
        .build();
/*
      MockFactory<StringBuilder> mockFactory2 = MockFactory
        .forClass(StringBuilder.class)
        // Use String class name here because AbstractStringBuilder is package-scoped and we cannot directly
        // refer to it via AbstractStringBuilder.class
        .excludeSuperTypes(named("java.lang.AbstractStringBuilder"))
        .spy()
        // TODO: This only works if all libraries are loaded from the bootstrap class loader. Currently there are
        //       VerifyErrors because some classes are loaded from the application class loader and others from
        //       bootstrap after injection in @BeforeClass. This can only be healed in an integration test scenario.
        .addAdvice(InstanceMethodAroundAdvice.MOCK, named("toString"))
        .build()
*/
    )
    {
      StringBuffer stringBuffer = new StringBuffer("dummy");
      mockFactory1.addTarget(stringBuffer);
      stringBuffer.append(42);
      stringBuffer.append("foo");
      assertNull(stringBuffer.toString());

      // TODO: Activate again after making mocks more individually configurable. At the moment, globally mocking
      //       StringBuilder and/or AbstractStringBuilder fails because those classes are also used internally for
      //       logging.
/*
      StringBuilder stringBuilder = new StringBuilder("dummy");
      mockFactory2.addTarget(stringBuilder);
      stringBuilder.append(42);
      stringBuilder.append("foo");
      assertNull(stringBuilder.toString());
*/
    }
  }

  /**
   * Do not mock URL and URI at the same time because they are used inside ByteBuddy in order to locate class files,
   * which leads to strange exceptions thrown by ByteBuddy's class file locator such as:
   * <p></p>
   * <code>IllegalStateException: Could not locate class file for dev.sarek.agent.aspect.HashCodeAspect</code>
   * <p></p>
   * If we mock them separately, we do not hit this problem, but this test might still fail in the future. Keep it as
   * a show case for more difficult situations and to document known edge cases.
   *
   * @throws IOException
   * @throws URISyntaxException
   */
  // TODO: There is a chance that this problem does not occur when running in Java agent mode and the mock classes are
  //       also injected into the bootstrap class loader. Check if this is true.
  @Test
  public void canMockBootstrapClass_URL() throws IOException, URISyntaxException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<URL> mockFactory = MockFactory.forClass(URL.class).provideHashCodeMethod().global().build();
    )
    {
      URL url = new URL("invalid URL, no problem");
      assertNull(url.getHost());
      assertNull(url.getContent());
    }

    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<URI> mockFactory = MockFactory.forClass(URI.class).provideHashCodeMethod().global().build()
    )
    {
      URI uri = new URI("invalid URI, no problem");
      assertNull(uri.getHost());
      assertNull(uri.getQuery());
    }
  }

  @Test
  public void canMockBootstrapClass_Random() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<Random> mockFactory = MockFactory.forClass(Random.class).global().build()
    )
    {
      Random random = new Random();
      assertEquals(0, random.nextInt());
      assertEquals(0, random.nextDouble(), 1e-6);
    }
  }

  @Test
  public void canMockBootstrapClasses_Swing() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<JTable> mockFactory1 = MockFactory.forClass(JTable.class).global().build();
      MockFactory<GroupLayout> mockFactory2 = MockFactory.forClass(GroupLayout.class).global().build();
      MockFactory<JTextField> mockFactory3 = MockFactory.forClass(JTextField.class).global().build()
    )
    {
      JTable jTable = new JTable(3, 3);
      assertEquals(0, jTable.getRowCount());
      assertEquals(0, jTable.getColumnCount());
      assertNull(new GroupLayout(null).getLayoutStyle());
      assertNull(new JTextField().getSelectedTextColor());
    }
  }

  @Test
  public void createInstance() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<UUID> mockFactory = MockFactory
        .forClass(UUID.class)
        .mockStaticMethods(true)  // include static methods, too
        .provideHashCodeMethod()
        .build()
    )
    {
      // Static method is mocked
      // TODO: implement way to differentiate between global mock mode (constructors + instance methods) and registering
      //       a class for static and type initialiser mocking
//      assertNull(UUID.randomUUID());

      // Create mock and automatically register it as an active target
      assertNull(mockFactory.createInstance().toString());

      // Create mock and manually automatically (de-)register it as an active target
      UUID uuid = mockFactory.createInstance(false);
      assertNotNull(uuid.toString());
      mockFactory.addTarget(uuid);
      assertNull(uuid.toString());
      mockFactory.removeTarget(uuid);
      assertNotNull(uuid.toString());
    }

    // After auto-close, class transformations have been reverted
    assertTrue(new UUID(0xABBA, 0xCAFE).toString().contains("abba"));
    assertTrue(UUID.randomUUID().toString().matches("\\p{XDigit}+(-\\p{XDigit}+){4}"));
  }

}
