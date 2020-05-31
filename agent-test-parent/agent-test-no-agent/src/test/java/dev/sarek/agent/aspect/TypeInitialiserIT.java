package dev.sarek.agent.aspect;

import dev.sarek.app.UnderTest;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import static dev.sarek.testing.TestHelper.isClassLoaded;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * This test checks features which do not involve bootloader classes.
 * So we do not need a Java agent here.
 */
public class TypeInitialiserIT {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();

  private Weaver weaver;

  @After
  public void cleanUp() {
    if (weaver != null)
      weaver.unregisterTransformer();
  }

  @Test
  public void staticInitialiser() throws IOException {
    assertFalse(
      "This test needs to run in its own JVM, otherwise the type initialiser (static block) " +
        "for the class under test could have run before already",
      isClassLoaded("UnderTest")
    );

    // Create weaver, directly registering a target class in the constructor
    weaver = new Weaver(
      INSTRUMENTATION,
      is(UnderTest.class),
      new TypeInitialiserAroundAdvice(
        // false = suppress type initialiser
        // true = initialiser runs, sets static property and prints something on the console
        clazz -> false,
        // Set static property to a value different from the one set by the type initialiser
        (clazz, proceedMode, throwable) -> UnderTest.staticText = "aspect override"
      ),
      UnderTest.class
    );

    // Registered class is affected by aspect
    assertEquals("aspect override", UnderTest.staticText);
  }

}
