package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.InstanceMethodAroundAdvice;
import dev.sarek.agent.constructor_mock.ConstructorMockRegistry;
import org.acme.*;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static dev.sarek.agent.mock.MockFactory.forClass;
import static java.util.Calendar.MAY;
import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.Assert.*;

@SuppressWarnings("ConstantConditions")
public class MockTest {

  @Test
  public void defaultMockIsNotGlobal() {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).build()) {
      UnderTest underTest = new UnderTest();
      assertEquals("default", underTest.getName());
      assertEquals(3, underTest.add(1, 2));
    }
  }

  @Test
  public void createInstance() {
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
  public void defaultMockOnlyStubsInstanceMethods() {
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
  public void closeMockFactoryExplicitly() {
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
  public void closeMockFactoryTwice() {
    // Close mock factory twice, once explicitly and again implicitly via AutoCloseable -> nothing bad happens
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).build()) {
      UnderTest underTest = mockFactory.createInstance();
      assertEquals(0, underTest.add(1, 2));
      mockFactory.close();
      assertEquals(3, underTest.add(1, 2));
    }
  }

  @Test
  public void globalMock() {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).mockConstructors().build()) {
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
      // would you expect after using both `.mockConstructors()` and `.addGlobalInstance()`? It is as global as it
      // gets...
      mockFactory.addGlobalInstance();
      underTest = new UnderTest();
      assertEquals(0, underTest.add(1, 2));
      assertEquals(0, underTest.multiply(1, 2));
      assertNull(underTest.getName());
    }
  }

  @Test
  public void globalMockGlobalInstance() {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).mockConstructors().addGlobalInstance().build()) {
      // Global mock + global instance → instance methods are stubbed even for objects created via constructor. We do
      // not even need to register them as mock targets
      UnderTest underTest = new UnderTest();
      assertEquals(0, underTest.add(1, 2));
      assertEquals(0, underTest.multiply(1, 2));
      assertNull(underTest.getName());
    }
  }

  @Test
  public void spyWithoutStubbing() {
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
  public void spyWithStubbing() {
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
  public void globalSpyWithoutStubbing() {
    try (MockFactory<UnderTest> mockFactory = forClass(UnderTest.class).spy().mockConstructors().build()) {
      // Global spy → instance methods are not stubbed by default
      UnderTest underTest = new UnderTest();
      mockFactory.addTarget(underTest);
      assertEquals(3, underTest.add(1, 2));
      assertEquals(2, underTest.multiply(1, 2));
      assertEquals("default", underTest.getName());
    }
  }

  @Test
  public void globalSpyWithStubbing() {
    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .spy().mockConstructors().addGlobalInstance()
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
  public void mockAncestorClassMethods() {
    try (MockFactory<ExtendsSub> mockFactory = forClass(ExtendsSub.class).build()) {
      ExtendsSub extendsSub = mockFactory.createInstance();
      assertNull(extendsSub.getDate());     // target class ExtendsSub
      assertNull(extendsSub.getName());     // parent class Sub
      assertEquals(0, extendsSub.getId());  // grandparent class Base

      // Slightly off-topic: How to turn a real, fully initialised instance into a mock (not just into a spy) ex post
      extendsSub = new ExtendsSub(11, "John Doe", new GregorianCalendar(1971, MAY, 8).getTime());
      assertEquals(new GregorianCalendar(1971, MAY, 8).getTime(), extendsSub.getDate());
      assertEquals("John Doe", extendsSub.getName());
      assertEquals(11, extendsSub.getId());
      mockFactory.addTarget(extendsSub);
      assertNull(extendsSub.getDate());
      assertNull(extendsSub.getName());
      assertEquals(0, extendsSub.getId());
    }
  }

  @Test
  public void excludeSuperTypes() {
    try (
      MockFactory<UnderTestSub> mockFactory = forClass(UnderTestSub.class)
        // Exclude super type UnderTest from mocking
        .excludeSuperTypes(is(UnderTest.class))
        .build()
    )
    {
      // Slightly off-topic: How to turn a real, fully initialised instance into a mock (not just into a spy) ex post
      UnderTestSub underTestSub = new UnderTestSub("Jane Doe", 33);
      mockFactory.addTarget(underTestSub);
      assertEquals(0, underTestSub.getAge());            // target class UnderTestSub -> mocked
      assertEquals(3, underTestSub.add(1, 2));           // parent class UnderTest -> not mocked
      assertEquals("Jane Doe", underTestSub.getName());  // parent class UnderTest -> not mocked
      assertEquals(20, underTestSub.multiply(4, 5));     // parent class UnderTest -> not mocked
    }

    try (
      MockFactory<ExtendsSub> mockFactory = forClass(ExtendsSub.class)
        // Exclude super type Sub from mocking (but not Base)
        .excludeSuperTypes(is(Sub.class))
        .build()
    )
    {
      // Slightly off-topic: How to turn a real, fully initialised instance into a mock (not just into a spy) ex post
      ExtendsSub extendsSub = new ExtendsSub(11, "John Doe", new GregorianCalendar(1971, MAY, 8).getTime());
      mockFactory.addTarget(extendsSub);
      assertNull(extendsSub.getDate());                // target class ExtendsSub -> mocked
      assertEquals("John Doe", extendsSub.getName());  // parent class Sub -> not mocked
      assertEquals(0, extendsSub.getId());             // grandparent class Base -> mocked
    }

    try (
      MockFactory<ExtendsSub> mockFactory = forClass(ExtendsSub.class)
        // Exclude super type Sub from mocking (but not Base)
        .excludeSuperTypes(is(Sub.class))
        // But explicitly mock a method from the excluded class, too
        .mock(
          named("getName"),
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) -> "Who wins?"
        )
        .build()
    )
    {
      // Slightly off-topic: How to turn a real, fully initialised instance into a mock (not just into a spy) ex post
      ExtendsSub extendsSub = new ExtendsSub(11, "John Doe", new GregorianCalendar(1971, MAY, 8).getTime());
      mockFactory.addTarget(extendsSub);
      assertNull(extendsSub.getDate());                // target class ExtendsSub -> mocked
      assertEquals(0, extendsSub.getId());             // grandparent class Base -> mocked

      // Important to know: If a class is matched by an exclusion pattern and normally would not be mocked to return
      // null-ish values (null, 0, false), but at the same time there is an explicit stub definition for a method in the
      // excluded class, the stub definition loses and is not applied. Class exclusion trumps method stubbing!
      assertEquals("John Doe", extendsSub.getName());
    }

  }

  @Test
  public void excludeMethods() {
    try (
      MockFactory<UnderTestSub> mockFactory = forClass(UnderTestSub.class)
        // Exclude getters from mocking
        .excludeMethods(nameStartsWith("get"))
        .build()
    )
    {
      // Slightly off-topic: How to turn a real, fully initialised instance into a mock (not just into a spy) ex post
      UnderTestSub underTestSub = new UnderTestSub("Jane Doe", 33);
      mockFactory.addTarget(underTestSub);
      assertEquals(33, underTestSub.getAge());           // getter -> not mocked
      assertEquals(0, underTestSub.add(1, 2));           // no getter -> mocked
      assertEquals("Jane Doe", underTestSub.getName());  // getter -> not mocked
      assertEquals(0, underTestSub.multiply(4, 5));      // no getter -> mocked
      assertEquals(0, underTestSub.negate(42));          // no getter -> mocked
    }

    try (
      MockFactory<ExtendsSub> mockFactory = forClass(ExtendsSub.class)
        // Exclude methods with return type Date from mocking
        .excludeMethods(returns(Date.class))
        .build()
    )
    {
      // Slightly off-topic: How to turn a real, fully initialised instance into a mock (not just into a spy) ex post
      ExtendsSub extendsSub = new ExtendsSub(11, "John Doe", new GregorianCalendar(1971, MAY, 8).getTime());
      mockFactory.addTarget(extendsSub);
      assertNotNull(extendsSub.getDate());  // returns Date -> not mocked
      assertNull(extendsSub.getName());     // does not return Date -> mocked
      assertEquals(0, extendsSub.getId());  // does not return Date -> mocked
    }

    try (
      MockFactory<ExtendsSub> mockFactory = forClass(ExtendsSub.class)
        // Exclude methods with return type Date from mocking
        .excludeMethods(returns(Date.class))
        // But explicitly mock a method returning Date, too
        .mock(
          named("getDate"),
          (target, method, args) -> false,
          (target, method, args, proceedMode, returnValue, throwable) ->
            new GregorianCalendar(2222, Calendar.FEBRUARY, 22).getTime()
        )
        .build()
    )
    {
      // Slightly off-topic: How to turn a real, fully initialised instance into a mock (not just into a spy) ex post
      ExtendsSub extendsSub = new ExtendsSub(11, "John Doe", new GregorianCalendar(1971, MAY, 8).getTime());
      mockFactory.addTarget(extendsSub);
      assertNull(extendsSub.getName());     // does not return Date -> mocked
      assertEquals(0, extendsSub.getId());  // does not return Date -> mocked
      // Important to know: If a method is matched by an exclusion pattern and normally would not be mocked to return
      // null-ish values (null, 0, false), but at the same time there is an explicit stub definition for same method,
      // the stub definition wins and overrides general mocking rules as well as exclusion rules.
      assertEquals(new GregorianCalendar(2222, Calendar.FEBRUARY, 22).getTime(), extendsSub.getDate());
    }
  }

  @Test
  public void provideHashCodeEquals() {
    // By default, existing hashCode/equals are overridden by versions based on object identity as defined in
    // HashCodeAspect and EqualsAspect. Otherwise, the original methods might throw exceptions due to uninitialised
    // fields, because no constructors were executed during object creation.
    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .mockConstructors()
        .addGlobalInstance()
        .build()
    )
    {
      assertNotEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertNotEquals(new UnderTest().hashCode(), new UnderTest().hashCode());
      assertNotEquals(new UnderTest().hashCode(), mockFactory.createInstance().hashCode());
    }

    // Optionally, we can avoid existing hashCode/equals methods from being overridden. But then they will have to deal
    // with uninitialised fields. If they can, fine. Otherwise - uh oh! In the latter case however, we still have the
    // option to manually stub/advise those methods.
    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .mockConstructors()
        .addGlobalInstance()
        .provideHashCodeEquals(false)
        .build()
    )
    {
      assertEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertEquals(new UnderTest().hashCode(), new UnderTest().hashCode());
      assertEquals(new UnderTest().hashCode(), mockFactory.createInstance().hashCode());
    }

    // If the target class does not have any hashCode/equals methods, nothing is overridden, even if explicitly
    // requested. This is because the Sarek framework avoids structural class changes during (re)transformation in order
    // to also be applicable to already loaded classes (even JRE bootstrap classes). Thus, the user cannot expect any
    // methods to be added.
    try (
      MockFactory<Sub> mockFactory = forClass(Sub.class)
        .mockConstructors()
        .addGlobalInstance()
        .provideHashCodeEquals(true) // This is the default anyway, but just to make it clear...
        .build()
    )
    {
      assertNotEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertNotEquals(new Sub("test").hashCode(), new Sub("test").hashCode());
      assertNotEquals(new Sub("test").hashCode(), mockFactory.createInstance().hashCode());

      assertEquals(0, new Sub("test").getId());
      assertNull(new Sub("test").getName());
      assertNull(new Sub("test").toString());
    }

    // If there are hashCode/equals methods not directly in the target class but somewhere in the super class hierarchy,
    // overriding them like in the first scenario also works because super class methods are also subject to mocking or
    // stubbing by default, because that is what a user expects from mock behaviour. This not only applies to regular
    // methods but also to hashCode/equals.
    try (
      MockFactory<UnderTestSub> mockFactory = forClass(UnderTestSub.class)
        .mockConstructors()
        .addGlobalInstance()
        .build()
    )
    {
      assertNotEquals(mockFactory.createInstance().hashCode(), mockFactory.createInstance().hashCode());
      assertNotEquals(new UnderTestSub("John", 25).hashCode(), new UnderTestSub("John", 25).hashCode());
      assertNotEquals(new UnderTestSub("John", 25).hashCode(), mockFactory.createInstance().hashCode());
    }
  }

  @Test
  public void nonInjectableMock() throws InterruptedException {
    Sub sub;
    try (
      MockFactory<Sub> mockFactory = forClass(Sub.class)
        .mockConstructors()
        .addGlobalInstance()
        .build()
    )
    {
      // (A) Synchronous method calls

      // (1) Poll single instance from queue
      new SubUser().doSomething();
      assertTrue(mockFactory.pollGlobalInstance() instanceof Sub);
      assertNull(mockFactory.pollGlobalInstance());

      // (2) Poll 2 instances from queue
      new SubUser().doSomething();
      new SubUser().doSomething();
      assertTrue(mockFactory.pollGlobalInstance() instanceof Sub);
      assertTrue(mockFactory.pollGlobalInstance() instanceof Sub);
      assertNull(mockFactory.pollGlobalInstance());

      // (B) Asynchronous method calls

      final int pauseMillis = 200;

      // (1) Poll synchronously -> cannot fetch mock instance
      new SubUser().doSomethingAsynchronously(pauseMillis);
      assertNull(mockFactory.pollGlobalInstance());

      // (2) Poll timeout long enough -> can fetch mock instance
      sub = mockFactory.pollGlobalInstance(pauseMillis * 3 / 2);
      assertNull(sub.getName());
      // (2a) As expected, 'sub' behaves like a mock due to '.addGlobalInstance()'
      sub.setName("Alice");
      assertNull(sub.getName());
      // (2b) No more mock behaviour after 'removeGlobalInstance()'
      mockFactory.removeGlobalInstance();
      assertNull(sub.getName());
      sub.setName("Bob");
      assertEquals("Bob", sub.getName());
      // (2c) Buf if we manually register the previously fetched instance, we have mock behaviour again
      mockFactory.addTarget(sub);
      assertNull(sub.getName());

      // (3) Poll timeout too short -> cannot fetch mock instance
      new SubUser().doSomethingAsynchronously(pauseMillis);
      assertNull(mockFactory.pollGlobalInstance(pauseMillis / 2));
    }

    // After mock factory was closed, instance no longer is a mock
    assertEquals("Bob", sub.getName());

    new SubUser().doSomething();
    // Cannot use out-of-scope MockFactory here, got to directly check ConstructorMockRegistry
    assertNull(ConstructorMockRegistry.pollMockInstance(Sub.class));
  }

}
