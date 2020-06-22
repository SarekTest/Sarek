package dev.sarek.agent.remove_final;

import dev.sarek.agent.Agent;
import dev.sarek.agent.test.SeparateJVM;
import dev.sarek.app.FinalClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ObjectOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static dev.sarek.agent.test.TestHelper.isClassLoaded;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This test checks features which do not involve already loaded bootloader classes.
 * So we do not need a Java agent here.
 */
@Category(SeparateJVM.class)
public class RemoveFinalIT {
  private static final Instrumentation INSTRUMENTATION = Agent.getInstrumentation();

  @Test
  public void hotAttachDefinaliser()
    throws ReflectiveOperationException
  {
    // Ensure classes under test have not been loaded yet
    assertFalse(
      "This test needs to run in its own JVM, otherwise it could be too late for the" +
        "'remove final' transformation already",
      isClassLoaded("dev.sarek.app.FinalClass")
    );
    assertFalse(
      "This test needs to run in its own JVM, otherwise it could be too late for the" +
        "'remove final' transformation already",
      isClassLoaded("java.util.UUID")
    );

    // Activate definaliser
    INSTRUMENTATION.addTransformer(RemoveFinalTransformer.createTransformer(true), false);

    // Final application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getModifiers()));
    // Final method of application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getDeclaredMethod("doSomething").getModifiers()));
    // Final bootstrap class has been definalised
    assertFalse(Modifier.isFinal(UUID.class.getModifiers()));

    // Final method of already loaded bootstrap class has NOT been definalised
    assertTrue(Modifier.isFinal(ObjectOutputStream.class.getDeclaredMethod("writeObject", Object.class).getModifiers()));
  }

}
