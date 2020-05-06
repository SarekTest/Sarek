package de.scrum_master.bytebuddy.aspect;

import de.scrum_master.app.UnderTest;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static org.junit.Assert.assertEquals;

/**
 * This test checks features which do not involve bootloader classes.
 * So we do not need a Java agent here.
 */
public class StaticInitialiserIT {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();

  private Weaver weaver;

  @After
  public void cleanUp() {
    if (weaver != null)
      weaver.unregisterTransformer();
  }

  @Test
  public void staticInitialiser() throws IOException {
    // Create weaver, directly registering a target class in the constructor
    weaver = new Weaver(
      INSTRUMENTATION,
      is(UnderTest.class),
      new StaticInitialiserAroundAdvice(
        // false = suppress static initialiser
        // true = initialiser runs, sets static property and prints something on the console
        clazz -> false,
        // Set static property to a value different from the one set by the static initialiser
        (clazz, proceedMode, throwable) -> UnderTest.staticText = "aspect override"
      ),
      UnderTest.class
    );

    // Registered class is affected by aspect
    assertEquals(
      "This test needs to run in its own JVM, otherwise the static initialiser " +
        "for the class under test could have run before already",
      "aspect override",
      UnderTest.staticText
    );
  }

}
