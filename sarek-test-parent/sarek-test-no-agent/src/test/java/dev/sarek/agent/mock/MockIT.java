package dev.sarek.agent.mock;

import dev.sarek.test.util.SeparateJVM;
import org.acme.Base;
import org.acme.FinalClass;
import org.acme.Sub;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.UUID;

import static org.junit.Assert.*;

@Category(SeparateJVM.class)
public class MockIT {
  @Test
  public void canMockApplicationClasses() {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<FinalClass> mockFactory1 = MockFactory.forClass(FinalClass.class).global().addGlobalInstance().build();
      MockFactory<Sub> mockFactory2 = MockFactory.forClass(Sub.class).global().build();
      MockFactory<Base> mockFactory3 = MockFactory.forClass(Base.class).global().build()
    )
    {
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
  public void cannotMockBootstrapClasses() {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<UUID> mockFactory = MockFactory.forClass(UUID.class).global().build()
    )
    {
      // Calling instrumented constructors/methods requires helper classes on the bootstrap classpath
      assertThrows(NoClassDefFoundError.class, () -> new UUID(0xABBA, 0xCAFE));
      //noinspection ResultOfMethodCallIgnored
      assertThrows(NoClassDefFoundError.class, UUID::randomUUID);
    }

    // After auto-close, class transformations have been reverted
    assertTrue(new UUID(0xABBA, 0xCAFE).toString().contains("abba"));
    assertTrue(UUID.randomUUID().toString().matches("\\p{XDigit}+(-\\p{XDigit}+){4}"));
  }

  @Test
  public void createInstance() {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<FinalClass> mockFactory = MockFactory.forClass(FinalClass.class).build()
    )
    {
      // Create mock and automatically register it as an active target
      assertEquals(0, mockFactory.createInstance().add(2, 3));

      // Create mock and manually automatically (de-)register it as an active target
      FinalClass finalClass = mockFactory.createInstance(false);
      assertEquals(5, finalClass.add(2, 3));
      mockFactory.addTarget(finalClass);
      assertEquals(0, finalClass.add(2, 3));
      mockFactory.removeTarget(finalClass);
      assertEquals(5, finalClass.add(2, 3));
    }
  }

}
