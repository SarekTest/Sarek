package de.scrum_master.agent.mock;

import de.scrum_master.agent.aspect.MethodAspect;
import de.scrum_master.app.Base;
import de.scrum_master.app.FinalClass;
import de.scrum_master.app.Sub;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.UUID;
import java.util.jar.JarFile;

import static de.scrum_master.testing.TestHelper.isClassLoaded;
import static org.junit.Assert.*;

@org.junit.FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MockTest {
  @BeforeClass
  public static void beforeClass() throws IOException {
//    addDependenciesToBootClassPath();
  }

  @Test
  public void a_canMockApplicationClasses() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(FinalClass.class, Base.class, Sub.class)) {
      assertEquals(0, new FinalClass().add(2, 3));
      assertEquals(0, new Base(11).getId());
      assertNull(new Sub("foo").getName());
    }

    // After auto-close, class transformations have been reverted
    assertEquals(5, new FinalClass().add(2, 3));
    assertEquals(11, new Base(11).getId());
    assertEquals("foo", new Sub("foo").getName());
  }

  @Test
  public void b_cannotMockBootstrapClasses() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(UUID.class)) {
      // Calling instrumented constructors/methods requires helper classes on the bootstrap classpath
      assertThrows(NoClassDefFoundError.class, () -> new UUID(0xABBA, 0xCAFE));
      assertThrows(NoClassDefFoundError.class, () -> UUID.randomUUID());
    }

    // After auto-close, class transformations have been reverted
    assertTrue(new UUID(0xABBA, 0xCAFE).toString().contains("abba"));
    assertTrue(UUID.randomUUID().toString().matches("\\p{XDigit}+(-\\p{XDigit}+){4}"));
  }

  @Test
  @Ignore
  public void c_canMockBootstrapClassesAfterAmendingClassPath() throws IOException {
//    addDependenciesToBootClassPath();

    assertFalse(isClassLoaded("java.util.UUID"));
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(UUID.class)) {
      System.out.println("UUID class loader = " + UUID.class.getClassLoader());
      System.out.println("MethodAspect class loader = " + MethodAspect.class.getClassLoader());
      // Calling instrumented constructors/methods requires helper classes on the bootstrap classpath
      assertNull(new UUID(0xABBA, 0xCAFE).toString());
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
    // 'de.scrum_master.jar.bytebuddy' and e.g. the ElementMatcher imports would need to be changed.
    JarFile aspectAgentJar = new JarFile(System.getProperty("aspect-agent.jar"));
    // Inject aspect agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(aspectAgentJar);

//    JarFile mockAgentJar = new JarFile("C:\\Users\\alexa\\Documents\\java-src\\TestHelperAgents\\mock\\target\\mock-1.0-SNAPSHOT.jar");
//    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(mockAgentJar);
//    Mock.INSTRUMENTATION = INSTRUMENTATION;
//
//    JarFile byteBuddyAgentJar = new JarFile("C:\\Users\\alexa\\.m2\\repository\\net\\bytebuddy\\byte-buddy-agent\\1.10.10\\byte-buddy-agent-1.10.10.jar");
//    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(byteBuddyAgentJar);
  }
}
