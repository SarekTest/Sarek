package dev.sarek.agent.mock;

import dev.sarek.app.UnderTest;
import org.junit.Test;

import java.io.IOException;

import static dev.sarek.agent.mock.MockFactory.forClass;
import static org.junit.Assert.*;

public class MockTest {
  private static final Class<UnderTest> UNDER_TEST = UnderTest.class;

  @Test
  public void defaultMockIsNotGlobal() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UNDER_TEST).build()) {
      UnderTest underTest = new UnderTest();
      assertEquals("default", underTest.getName());
      assertEquals(3, underTest.add(1, 2));
    }
  }

  @Test
  public void createInstance() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UNDER_TEST).build()) {
      UnderTest underTest;

      underTest = mockFactory.createInstance();
      assertNull(underTest.getName());
      assertEquals(0, underTest.add(1, 2));
      mockFactory.removeTarget(underTest);
      assertEquals(3, underTest.add(1, 2));
      mockFactory.addTarget(underTest);

      underTest = mockFactory.createInstance(true);
      assertNull(underTest.getName());
      assertEquals(0, underTest.add(1, 2));
      mockFactory.removeTarget(underTest);
      assertEquals(3, underTest.add(1, 2));
      mockFactory.addTarget(underTest);

      underTest = mockFactory.createInstance(false);
      // Even though this method is not mocked, it returns null because the field it returns has not been initialised
      // because the instance has been created by Objenesis
      assertNull(underTest.getName());
      assertEquals(3, underTest.add(1, 2));
      mockFactory.addTarget(underTest);
      assertNull(underTest.getName());
      assertEquals(0, underTest.add(1, 2));
    }
  }

  @Test
  public void defaultMockOnlyStubsInstanceMethods() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UNDER_TEST).build()) {
      assertEquals("Hello world", UnderTest.greet("world"));
    }

    try (MockFactory<UnderTest> mockFactory = forClass(UNDER_TEST).mockStaticMethods(true).build()) {
      //noinspection ConstantConditions
      assertNull(UnderTest.greet("world"));
    }

    try (MockFactory<UnderTest> mockFactory = forClass(UNDER_TEST).mockInstanceMethods(false).build()) {
      // Cannot add/remove target because mock factory is configured to ignore instance methods
      assertThrows(IllegalArgumentException.class, mockFactory::createInstance);
      assertThrows(IllegalArgumentException.class, () -> mockFactory.addTarget(new UnderTest()));
      assertThrows(IllegalArgumentException.class, () -> mockFactory.removeTarget(new UnderTest()));
    }
  }
}
