package de.scrum_master.agent.aspect;

import de.scrum_master.app.AnotherSub;
import de.scrum_master.app.Base;
import de.scrum_master.app.Sub;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.Test;

import java.lang.instrument.Instrumentation;

import static org.junit.Assert.*;

public class GlobalMockTest {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();

  @Test
  public void testGlobalMock() {
    // Before activating global mock mode for class Sub, everything is normal
    assertFalse(GlobalMockRegistry.isMock(Sub.class));
    assertEquals(11, new Base(11).getId());
    assertEquals(33, new AnotherSub(33, "bar").getId());
    assertEquals("bar", new AnotherSub(33, "bar").getName());
    assertEquals(22, new Sub(22, "foo").getId());
    assertEquals("foo", new Sub(22, "foo").getName());
    System.out.println("-----");

    // After activating global mock mode for class Sub, fields should be uninitialised for Sub instances
    GlobalMockRegistry.activate(Sub.class);
    assertTrue(GlobalMockRegistry.isMock(Sub.class));
    assertEquals(11, new Base(11).getId());
    assertEquals(33, new AnotherSub(33, "bar").getId());
    assertEquals("bar", new AnotherSub(33, "bar").getName());
    // TODO: make these two lines pass despite fields being final by implementing global mocking via ASM
    assertEquals(0, new Sub(22, "foo").getId());
    assertNull(new Sub(22, "foo").getName());
    System.out.println("-----");

    // After deactivating global mock mode for class Sub, everything is normal again
    GlobalMockRegistry.deactivate(Sub.class);
    assertFalse(GlobalMockRegistry.isMock(Sub.class));
    assertEquals(11, new Base(11).getId());
    assertEquals(33, new AnotherSub(33, "bar").getId());
    assertEquals("bar", new AnotherSub(33, "bar").getName());
    assertEquals(22, new Sub(22, "foo").getId());
    assertEquals("foo", new Sub(22, "foo").getName());
  }
}
