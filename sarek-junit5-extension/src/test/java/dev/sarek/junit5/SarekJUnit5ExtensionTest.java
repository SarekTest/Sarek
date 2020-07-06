package dev.sarek.junit5;

import dev.sarek.agent.mock.MockFactory;
import org.acme.Base;
import org.acme.FinalClass;
import org.acme.Sub;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SarekJUnit5ExtensionTest {
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
}
