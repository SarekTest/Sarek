package dev.sarek.agent.mock;

import dev.sarek.app.Base;
import dev.sarek.app.FinalClass;
import dev.sarek.app.Sub;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.*;

public class MockTest {
  @Test
  public void canMockApplicationClasses() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(FinalClass.class, Base.class, Sub.class)) {
      System.out.println(new FinalClass().add(2, 3));
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
  public void cannotMockBootstrapClasses() throws IOException {
    // Try with resources works for Mock because it implements AutoCloseable
    try (Mock mock = new Mock(UUID.class)) {
      // Calling instrumented constructors/methods requires helper classes on the bootstrap classpath
      assertThrows(NoClassDefFoundError.class, () -> new UUID(0xABBA, 0xCAFE));
      //noinspection ResultOfMethodCallIgnored
      assertThrows(NoClassDefFoundError.class, UUID::randomUUID);
    }

    // After auto-close, class transformations have been reverted
    assertTrue(new UUID(0xABBA, 0xCAFE).toString().contains("abba"));
    assertTrue(UUID.randomUUID().toString().matches("\\p{XDigit}+(-\\p{XDigit}+){4}"));
  }

}
