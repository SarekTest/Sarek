package dev.sarek.junit5;

import dev.sarek.agent.mock.MockFactory;
import org.acme.Base;
import org.acme.FinalClass;
import org.acme.Sub;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.Assert.*;

@SuppressWarnings("ConstantConditions")
public class SarekJUnit5ExtensionTest {
  @Test
  public void canMockApplicationClasses() {

    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<FinalClass> mockFactory1 = MockFactory.forClass(FinalClass.class).mockConstructors().addGlobalInstance().build();
      MockFactory<Sub> mockFactory2 = MockFactory.forClass(Sub.class).mockConstructors().build();
      MockFactory<Base> mockFactory3 = MockFactory.forClass(Base.class).mockConstructors().build()
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

  @org.junit.Test
  public void canMockBootstrapClass_UUID() {
    // Try with resources works for Mock because it implements AutoCloseable
    try (
      MockFactory<UUID> mockFactory = MockFactory
        .forClass(UUID.class)
        .mockStaticMethods(true)  // include static methods, too
        .mockConstructors()
        .addGlobalInstance()
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

}
