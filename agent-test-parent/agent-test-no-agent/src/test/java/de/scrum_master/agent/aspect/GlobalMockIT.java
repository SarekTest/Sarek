package de.scrum_master.agent.aspect;

import de.scrum_master.agent.global_mock.GlobalMockRegistry;
import de.scrum_master.agent.global_mock.GlobalMockTransformer;
import de.scrum_master.app.AnotherSub;
import de.scrum_master.app.Base;
import de.scrum_master.app.ExtendsSub;
import de.scrum_master.app.Sub;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

/**
 * This test runs without a Java agent set via command line. It attaches it dynamically after adding it to the
 * bootstrap class loader's search path. The latter is only necessary if we want to globally mock classes which are
 * either bootstrap classes themselves or have bootstrap classes in their ancestry (direct or indirect parent classes).
 * de.icongmbh.oss.maven.plugin.javassist.ClassTransformer
 * Furthermore, the test demonstrates how to retransform an already loaded class (in this case also a bootstrap class)
 * in order to add global mock functionality to it. This proves that the global mock transformation does not change the
 * class structure but only instruments constructor bodies. I.e. that this is more flexible than e.g. removing 'final'
 * modifiers because the latter change class/method signatures and are not permitted in retransformations, so they have
 * to be done during class-loading.
 */
public class GlobalMockIT {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();
  private GlobalMockTransformer transformer;

  @BeforeClass
  public static void beforeClass() throws IOException {
    useBootstrapClassBeforeInstrumentation();
    // Property is set in Maven in order to tell us the path
    JarFile globalMockAgentJar = new JarFile(System.getProperty("global-mock-agent.jar"));
    // Inject global mock agent JAR into bootstrap classloader
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(globalMockAgentJar);
  }

  private static void useBootstrapClassBeforeInstrumentation() {
    new UUID(0xABBA, 0xCAFE);
  }

  @Before
  public void beforeTest() {
    transformer = new GlobalMockTransformer();
    // Important: set 'canRetransform' parameter to true
    INSTRUMENTATION.addTransformer(transformer, true);
  }

  @After
  public void afterTest() {
    INSTRUMENTATION.removeTransformer(transformer);
    transformer = null;
  }

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
  public void globalMockOnApplicationClass() throws UnmodifiableClassException {
    final String className_Sub = Sub.class.getName();

    // (1) Before activating global mock mode for class Sub, everything is normal

    assertFalse(GlobalMockRegistry.isMock(className_Sub));
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

    // (2a) Retransform already loaded application class (incl. all ancestors) in order to make it globally mockable
    // TODO: make this easier by just providing Sub and letting the framework take care of the ancestors
    // TODO: Multiple retransformations work fine, buy will byte code be inserted multiple times? -> check & improve
    INSTRUMENTATION.retransformClasses(Sub.class, Base.class);

    // (2b) After activating global mock mode for class Sub,
    //   - fields should be uninitialised for Sub instances,
    //   - but not for direct base class instances or siblings in the inheritance hierarchy such as AnotherSub.
    //   - When instantiating child classes of Sub, their own constructors will not be mocked either, but the
    //     parent constructors from Sub upwards (i.e. Sub, Base) will be.

    GlobalMockRegistry.activate(className_Sub);
    assertTrue(GlobalMockRegistry.isMock(className_Sub));
    initialiseSubjectsUnderTest();
    // No change in behaviour for base class Base
    assertEquals(11, base.getId());
    // Global mock effect for target class Sub
    assertEquals(0, sub.getId());
    assertNull(sub.getName());
    // No change in behaviour for sibling class AnotherSub
    assertEquals(33, anotherSub.getId());
    assertEquals("bar", anotherSub.getName());
    // ExtendsSub extends Sub behaves normally in its own constructor, but Sub/Base still have global mock behaviour
    assertEquals(0, extendsSub.getId());
    assertNull(extendsSub.getName());
    assertNotNull(extendsSub.getDate());

    System.out.println("-----");

    // (3) After deactivating global mock mode for class Sub, everything is normal again

    GlobalMockRegistry.deactivate(className_Sub);
    assertFalse(GlobalMockRegistry.isMock(className_Sub));
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

  @Test
  public void globalMockOnAlreadyLoadedBootstrapClass() throws UnmodifiableClassException {
    // (1) Before activating global mock mode for class UUID, everything is normal
    assertFalse(GlobalMockRegistry.isMock(UUID.class.getName()));
    assertEquals("00000000-0000-abba-0000-00000000cafe", new UUID(0xABBA, 0xCAFE).toString());

    // (2a) Retransform already loaded (bootstrap) class UUID in order to make it globally mockable
    INSTRUMENTATION.retransformClasses(UUID.class);

    // (2b) Make class UUID globally mockable
    GlobalMockRegistry.activate(UUID.class.getName());
    assertTrue(GlobalMockRegistry.isMock(UUID.class.getName()));
    assertEquals("00000000-0000-0000-0000-000000000000", new UUID(0xABBA, 0xCAFE).toString());

    // (3) After deactivating global mock mode for class UUID, everything is normal again
    GlobalMockRegistry.deactivate(UUID.class.getName());
    assertFalse(GlobalMockRegistry.isMock(UUID.class.getName()));
    assertEquals("00000000-0000-abba-0000-00000000cafe", new UUID(0xABBA, 0xCAFE).toString());
  }
}
