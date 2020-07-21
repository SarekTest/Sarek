package dev.sarek.agent.constructor_mock;

import org.acme.*;
import org.junit.Test;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static dev.sarek.agent.constructor_mock.ConstructorMockRegistry.pollMockInstance;
import static org.junit.Assert.*;

public class ConstructorMockAgentIT {
  Base base;
  AnotherSub anotherSub;
  Sub sub;
  ExtendsSub extendsSub;

  private void initialiseSubjectsUnderTest() {
    base = new Base(11);
    sub = new Sub(22, "foo");
    anotherSub = new AnotherSub(33, "bar");
    extendsSub = new ExtendsSub(44, "zot", Date.from(Instant.now()));
  }

  @Test
  public void constructorMockOnApplicationClass() {
    // (1) Before activating constructor mock mode for class Sub, everything is normal

    assertFalse(ConstructorMockRegistry.isMock(Sub.class));
    initialiseSubjectsUnderTest();
    assertEquals(11, base.getId());
    assertEquals(22, sub.getId());
    assertEquals("foo", sub.getName());
    assertEquals(33, anotherSub.getId());
    assertEquals("bar", anotherSub.getName());
    assertEquals(44, extendsSub.getId());
    assertEquals("zot", extendsSub.getName());
    assertNotNull(extendsSub.getDate());

    System.out.println("-----");

    // (2a) Retransform already loaded application class (incl. all ancestors) in order to make constructor mockable
    // TODO: make this easier by just providing Sub and letting the framework take care of the ancestors
    // TODO: Multiple retransformations work fine, buy will byte code be inserted multiple times? -> check & improve
//    INSTRUMENTATION.retransformClasses(Sub.class, Base.class);

    // (2b) After activating constructor mock mode for class Sub,
    //   - fields should be uninitialised for Sub instances,
    //   - but not for direct base class instances or siblings in the inheritance hierarchy such as AnotherSub.
    //   - When instantiating child classes of Sub, their own constructors will not be mocked either, but the
    //     parent constructors from Sub upwards (i.e. Sub, Base) will be.

    try (
      ConstructorMockTransformer<Sub> constructorMockTransformer =
        ConstructorMockTransformer.forClass(Sub.class).build()
    )
    {
      // TODO: add auto activation/cleanup features to ConstructorMockTransformer
      ConstructorMockRegistry.activate(Sub.class);
      assertTrue(ConstructorMockRegistry.isMock(Sub.class));
      initialiseSubjectsUnderTest();
      // No change in behaviour for base class Base
      assertEquals(11, base.getId());
      // Constructor mock effect for target class Sub
      assertEquals(0, sub.getId());
      assertNull(sub.getName());
      // No change in behaviour for sibling class AnotherSub
      assertEquals(33, anotherSub.getId());
      assertEquals("bar", anotherSub.getName());
      // ExtendsSub extends Sub is unaffected because it is not a target class but only a sub class
      assertEquals(44, extendsSub.getId());
      assertEquals("zot", extendsSub.getName());
      assertNotNull(extendsSub.getDate());

      System.out.println("-----");

      // (3) After deactivating constructor mock mode for class Sub, everything is normal again

      ConstructorMockRegistry.deactivate(Sub.class);
      assertFalse(ConstructorMockRegistry.isMock(Sub.class));
      initialiseSubjectsUnderTest();
      assertEquals(11, base.getId());
      assertEquals(22, sub.getId());
      assertEquals("foo", sub.getName());
      assertEquals(33, anotherSub.getId());
      assertEquals("bar", anotherSub.getName());
      assertEquals(44, extendsSub.getId());
      assertEquals("zot", extendsSub.getName());
      assertNotNull(extendsSub.getDate());
    }
  }

  @Test
  public void constructorMockOnAlreadyLoadedBootstrapClass() {
    // (1) Before activating constructor mock mode for class UUID, everything is normal
    assertFalse(ConstructorMockRegistry.isMock(UUID.class));
    assertEquals("00000000-0000-abba-0000-00000000cafe", new UUID(0xABBA, 0xCAFE).toString());

    // (2a) Retransform already loaded (bootstrap) class UUID in order to make constructors mockable
//    INSTRUMENTATION.retransformClasses(UUID.class);

    // (2b) Make UUID constructor mockable
    try (
      ConstructorMockTransformer<UUID> constructorMockTransformer =
        ConstructorMockTransformer.forClass(UUID.class).build()
    )
    {
      ConstructorMockRegistry.activate(UUID.class);
      assertTrue(ConstructorMockRegistry.isMock(UUID.class));
      assertEquals("00000000-0000-0000-0000-000000000000", new UUID(0xABBA, 0xCAFE).toString());

      // (3) After deactivating constructor mock mode for class UUID, everything is normal again
      ConstructorMockRegistry.deactivate(UUID.class);
      assertFalse(ConstructorMockRegistry.isMock(UUID.class));
      assertEquals("00000000-0000-abba-0000-00000000cafe", new UUID(0xABBA, 0xCAFE).toString());
    }
  }

  /**
   * This test serves the purpose of checking if the transformation is working for constructor arguments of
   * - all primitive types,
   * - a reference type and
   * - an array (technically also a reference type).
   */
  @Test
  public void constructorMockComplexConstructor() {
    try (
      ConstructorMockTransformer<SubWithComplexConstructor> constructorMockTransformer =
        ConstructorMockTransformer.forClass(SubWithComplexConstructor.class).build()
    )
    {
      ConstructorMockRegistry.activate(SubWithComplexConstructor.class);
      assertTrue(
        new SubWithComplexConstructor(
          (byte) 123, '@', 123.45, 67.89f,
          123, 123, (short) 123, true,
          "foo", new int[][] { { 12, 34 }, { 56, 78 } }
        ).toString().contains(
          "aByte=0, aChar=\0, aDouble=0.0, aFloat=0.0, " +
            "anInt=0, aLong=0, aShort=0, aBoolean=false, " +
            "string='null', ints=null"
        )
      );
      ConstructorMockRegistry.deactivate(SubWithComplexConstructor.class);
    }
  }

  @Test
  public void nonInjectableMock() {
    try (
      ConstructorMockTransformer<Sub> constructorMockTransformer =
        ConstructorMockTransformer.forClass(Sub.class).build()
    )
    {
      new SubUser().doSomething();
      assertNull(pollMockInstance(Sub.class));
      ConstructorMockRegistry.activate(Sub.class);
      new SubUser().doSomething();
      assertTrue(pollMockInstance(Sub.class) instanceof Sub);
      assertNull(pollMockInstance(Sub.class));
      new SubUser().doSomething();
      new SubUser().doSomething();
      assertTrue(pollMockInstance(Sub.class) instanceof Sub);
      assertTrue(pollMockInstance(Sub.class) instanceof Sub);
      assertNull(pollMockInstance(Sub.class));
      ConstructorMockRegistry.deactivate(Sub.class);
      new SubUser().doSomething();
      assertNull(pollMockInstance(Sub.class));
    }
  }

}
