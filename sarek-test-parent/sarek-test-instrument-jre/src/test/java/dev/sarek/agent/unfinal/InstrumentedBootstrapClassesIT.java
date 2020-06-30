package dev.sarek.agent.unfinal;

import dev.sarek.test.util.SimpleMock;
import org.acme.FinalClass;
import org.junit.Test;

import java.io.ObjectOutputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.StringJoiner;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * When running this test in an IDE like IntelliJ IDEA, please make sure that the instrumented boot classes JAR for
 * this module has been created. Just run 'mvn package' first. In IDEA you can also edit the run configuration for this
 * test or a group of tests and add a "before launch" action, select "run Maven goal" and then add goal 'package'.
 */
public class InstrumentedBootstrapClassesIT {
  @Test
  public void checkDefinaliser() throws NoSuchMethodException {
    // Final application class has *not* been definalised because it is not on the bootstrap class path and
    // no unfinal agent is active
    assertTrue(Modifier.isFinal(FinalClass.class.getModifiers()));
    assertTrue(Modifier.isFinal(FinalClass.class.getDeclaredMethod("doSomething").getModifiers()));

    // Final bootstrap classes have been definalised
    assertFalse(Modifier.isFinal(UUID.class.getModifiers()));
    assertFalse(Modifier.isFinal(String.class.getModifiers()));
    assertFalse(Modifier.isFinal(StringJoiner.class.getModifiers()));
    assertFalse(Modifier.isFinal(URL.class.getModifiers()));

    // Final method of non-final bootstrap class has been definalised
    assertFalse(Modifier.isFinal(ObjectOutputStream.class.getDeclaredMethod("writeObject", Object.class).getModifiers()));
  }

  @Test
  public void checkMockability() throws ReflectiveOperationException {
    // Final application class *cannot* be mocked because it is not on the bootstrap class path and
    // no unfinal agent is active
    assertThrows(
      IllegalArgumentException.class,
      () -> SimpleMock.of(FinalClass.class).getInstance()
    );

    // Final bootstrap classes can be mocked
    assertEquals(
      "mocked UUID",
      SimpleMock
        .of(UUID.class)
        .withToStringMessage("mocked UUID")
        .withConstructor(long.class, long.class)
        .getInstance(123, 456)
        .toString()
    );
    //noinspection StringOperationCanBeSimplified
    assertEquals(
      "mocked String",
      SimpleMock
        .of(String.class)
        .withToStringMessage("mocked String")
        .withConstructor(String.class)
        .getInstance("dummy")
        // Keep this in order to make the test pass
        .toString()
    );
    assertEquals(
      "mocked StringJoiner",
      SimpleMock
        .of(StringJoiner.class)
        .withToStringMessage("mocked StringJoiner")
        .withConstructor(CharSequence.class)
        .getInstance("dummy")
        .toString()
    );
    assertEquals(
      "mocked URL",
      SimpleMock
        .of(URL.class)
        .withToStringMessage("mocked URL")
        .withConstructor(String.class)
        .getInstance("http://localhost")
        .toString()
    );
  }
}
