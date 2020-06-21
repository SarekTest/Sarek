package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.InstanceMethodAroundAdvice;
import dev.sarek.app.Sub;
import dev.sarek.app.UnderTest;
import dev.sarek.app.UnderTestSub;
import org.junit.Test;

import java.io.IOException;

import static dev.sarek.agent.mock.MockFactory.forClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.Assert.*;

@SuppressWarnings("ConstantConditions")
public class MockTest {

  @Test
  public void defaultMockIsNotGlobal() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).build()) {
      UnderTest underTest = new UnderTest();
      assertEquals("default", underTest.getName());
      assertEquals(3, underTest.add(1, 2));
    }
  }

  @Test
  public void createInstance() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).build()) {
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
    // Static methods not stubbed by default
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).build()) {
      assertEquals("Hello world", UnderTest.greet("world"));
    }

    // Static methods stubbed explicitly
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).mockStaticMethods(true).build()) {
      assertNull(UnderTest.greet("world"));
    }

    // Instance methods not stubbed explicitly -> also cannot add as targets
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).mockInstanceMethods(false).build()) {
      // Cannot add/remove target because mock factory is configured to ignore instance methods
      assertThrows(IllegalArgumentException.class, () -> mockFactory.addTarget(new UnderTest()));
      assertThrows(IllegalArgumentException.class, () -> mockFactory.removeTarget(new UnderTest()));
      // Creating a mock instance by default also tries to add it as an instance mock target -> failure
      assertThrows(IllegalArgumentException.class, mockFactory::createInstance);

      // But we can create a mock instance when explicitly not adding it to the active target list.
      // Why would anyone do that? Maybe because they want to
      //   - stub some instance methods manually via Weaver API,
      //   - use the stub without ever calling any methods on it,
      //   - only call instance methods which are independent on proper initialisation via constructor,
      //   - use the stub later in another mock factory with active instance method mocking.
      // I find all of these reasons somewhat exotic, but FWIW the API does not prohibit it for now. Maybe there is some
      // valid use case for an uninitialised instance without any stubbed methods.
      assertTrue(mockFactory.createInstance(false) instanceof UnderTest);
    }
  }

  @Test
  @SuppressWarnings({ "TryFinallyCanBeTryWithResources" })
  public void closeMockFactoryExplicitly() throws IOException {
    // Instead of try with resources via AutoCloseable, we can also close (and thus reset) the mock factory and its
    // byte code transformations manually.
    MockFactory<UnderTest> mockFactory = null;
    try {
      mockFactory = forClass(UnderTest.class).build();
      assertEquals(0, mockFactory.createInstance().add(1, 2));
    }
    finally {
      if (mockFactory != null)
        mockFactory.close();
    }
  }

  @Test
  public void closeMockFactoryTwice() throws IOException {
    // Close mock factory twice, once explicitly and again implicitly via AutoCloseable -> nothing bad happens
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).build()) {
      UnderTest underTest = mockFactory.createInstance();
      assertEquals(0, underTest.add(1, 2));
      mockFactory.close();
      assertEquals(3, underTest.add(1, 2));
    }
  }

  @Test
  public void globalMock() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).global().build()) {
      // Global mock → instance methods are stubbed even for objects created via constructor call if we add them as
      // mock targets
      UnderTest underTest = new UnderTest();
      mockFactory.addTarget(underTest);
      assertEquals(0, underTest.add(1, 2));
      assertEquals(0, underTest.multiply(1, 2));
      assertNull(underTest.getName());

      // Even though global mocking is active, we can still create instances via mock API
      underTest = mockFactory.createInstance();
      assertEquals(0, underTest.add(1, 2));
      assertEquals(0, underTest.multiply(1, 2));
      assertNull(underTest.getName());

      // If we activate global instance mode, adding mocks as targets manually is no longer necessary, but it also means
      // that instances created outside the control of this thread/method will automatically become mocks. What else
      // would you expect after using both `.global()` and `.addGlobalInstance()`? It is as global as it gets...
      mockFactory.addGlobalInstance();
      underTest = new UnderTest();
      assertEquals(0, underTest.add(1, 2));
      assertEquals(0, underTest.multiply(1, 2));
      assertNull(underTest.getName());
    }
  }

  @Test
  public void globalMockGlobalInstance() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).global().addGlobalInstance().build()) {
      // Global mock + global instance → instance methods are stubbed even for objects created via constructor. We do
      // not even need to register them as mock targets
      UnderTest underTest = new UnderTest();
      assertEquals(0, underTest.add(1, 2));
      assertEquals(0, underTest.multiply(1, 2));
      assertNull(underTest.getName());
    }
  }

  @Test
  public void spyWithoutStubbing() throws IOException {
    // Create spy instance the normal way, using a constructor
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).spy().addGlobalInstance().build()) {
      // Spy → instance methods are not stubbed by default
      UnderTest underTest = new UnderTest("John Doe");
      assertEquals(3, underTest.add(1, 2));
      assertEquals(2, underTest.multiply(1, 2));
      // Return value is null due to mockFactory.createInstance() -> uninitialised field
      assertEquals("John Doe", underTest.getName());
    }

    // Create spy instance by avoiding constructor execution, which is a rather exotic use case
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).spy().addGlobalInstance().build()) {
      // Spy → instance methods are not stubbed by default
      UnderTest underTest = mockFactory.createInstance();
      assertEquals(3, underTest.add(1, 2));
      assertEquals(2, underTest.multiply(1, 2));
      // Return value is null due to mockFactory.createInstance() -> uninitialised field
      assertNull(underTest.getName());
    }
  }

  @Test
  public void spyWithStubbing() throws IOException {
    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .spy()
        .mock(named("multiply"), InstanceMethodAroundAdvice.MOCK)
        .mock(
          named("getName"),
          (target, method, args) -> true,
          (target, method, args, proceedMode, returnValue, throwable) -> ((String) returnValue).toUpperCase()
        )
        .build()
    )
    {
      // Spy → instance methods can be stubbed
      UnderTest underTest = new UnderTest();
      mockFactory.addTarget(underTest);
      // These methods are stubbed
      assertEquals(0, underTest.multiply(1, 2));
      assertEquals("DEFAULT", underTest.getName());
      // This method is not stubbed
      assertEquals(3, underTest.add(1, 2));
    }
  }

  @Test
  public void globalSpyWithoutStubbing() throws IOException {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).spy().global().build()) {
      // Global spy → instance methods are not stubbed by default
      UnderTest underTest = new UnderTest();
      mockFactory.addTarget(underTest);
      assertEquals(3, underTest.add(1, 2));
      assertEquals(2, underTest.multiply(1, 2));
      assertEquals("default", underTest.getName());
    }
  }

  @Test
  public void globalSpyWithStubbing() throws IOException {
    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .spy().global().addGlobalInstance()
        .mock(named("multiply"), InstanceMethodAroundAdvice.MOCK)
        .mock(
          named("getName"),
          (target, method, args) -> true,
          (target, method, args, proceedMode, returnValue, throwable) -> ((String) returnValue).toUpperCase()
        )
        // Off-topic: just because we can, let us also stub a static method
        .mockStatic(
          named("greet"),
          (method, args) -> {
            args[0] = "foo bar";
            return true;
          },
          (method, args, proceedMode, returnValue, throwable) -> ((String) returnValue).toUpperCase()
        )
        .build()
    )
    {
      // Global spy → instance methods can be stubbed
      UnderTest underTest = new UnderTest("John Doe");
      // These methods are stubbed
      assertEquals(0, underTest.multiply(1, 2));
      assertEquals("JOHN DOE", underTest.getName());
      // This method is not stubbed
      assertEquals(3, underTest.add(1, 2));

      // Off-topic: in global mode we can also mock static methods
      assertEquals("HELLO FOO BAR", UnderTest.greet("world"));
    }
  }

  @Test
  public void provideHashCodeEquals() throws IOException {
    // By default, existing hashCode/equals are overridden by versions based on object identity as defined in
    // HashCodeAspect and EqualsAspect. Otherwise, the original methods might throw exceptions due to uninitialised
    // fields, because no constructors were executed during object creation.
    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .global()
        .addGlobalInstance()
        .build()
    ) {
      assertNotEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertNotEquals(new UnderTest().hashCode(), new UnderTest().hashCode());
      assertNotEquals(new UnderTest().hashCode(), mockFactory.createInstance().hashCode());
    }

    // Optionally, we can avoid existing hashCode/equals methods from being overridden. But then they will have to deal
    // with uninitialised fields. If they can, fine. Otherwise - uh oh! In the latter case however, we still have the
    // option to manually stub/advise those methods.
    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .global()
        .addGlobalInstance()
        .provideHashCodeEquals(false)
        .build()
    ) {
      assertEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertEquals(new UnderTest().hashCode(), new UnderTest().hashCode());
      assertEquals(new UnderTest().hashCode(), mockFactory.createInstance().hashCode());
    }

    // If there are hashCode/equals methods not directly in the target class but somewhere in the super class hierarchy,
    // overriding them like in the first scenario also works because super class methods are also subject to mocking or
    // stubbing by default, because that is what a user expects from mock behaviour. This not only applies to regular
    // methods but also to hashCode/equals.
    try (
      MockFactory<UnderTestSub> mockFactory = forClass(UnderTestSub.class)
        .global()
        .addGlobalInstance()
        .build()
    ) {
      assertNotEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertNotEquals(new UnderTestSub("John", 25).hashCode(), new UnderTestSub("John", 25).hashCode());
      assertNotEquals(new UnderTestSub("John", 25).hashCode(), mockFactory.createInstance().hashCode());
    }

    // If the target class does not have any hashCode/equals methods, nothing is overridden, even if explicitly
    // requested. This is because the Sarek framework avoids structural class changes during (re)transformation in order
    // to also be applicable to already loaded classes (even JRE bootstrap classes). Thus, the user cannot expect any
    // methods to be added.
    try (
      MockFactory<Sub> mockFactory = forClass(Sub.class)
        .global()
        .addGlobalInstance()
        .provideHashCodeEquals(true) // This is the default anyway, but just to make it clear...
        .build()
    ) {
      assertNotEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertNotEquals(new Sub("test").hashCode(), new Sub("test").hashCode());
      assertNotEquals(new Sub("test").hashCode(), mockFactory.createInstance().hashCode());

      assertEquals(0, new Sub("test").getId());
      assertNull(new Sub("test").getName());
      assertNull(new Sub("test").toString());
    }
  }

}
