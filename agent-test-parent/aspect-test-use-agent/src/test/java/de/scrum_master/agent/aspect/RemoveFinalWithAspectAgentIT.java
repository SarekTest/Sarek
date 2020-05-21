package de.scrum_master.agent.aspect;

import de.scrum_master.testing.SimpleMock;
import de.scrum_master.app.FinalClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.StringJoiner;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * When running this test in an IDE like IntelliJ IDEA, please make sure that the JARs for both this module
 * ('bytebuddy-aspect-agent') and 'bytebuddy-aspect' have been created. Just run 'mvn package' first. In IDEA
 * you can also edit the run configuration for this test or a group of tests and add a "before launch" action,
 * select "run Maven goal" and then add goal 'package'.
 * <p>
 * Furthermore, make sure add this to the Maven Failsafe condiguration:
 * <argLine>-javaagent:target/aspect-agent-1.0-SNAPSHOT.jar</argLine>
 * Otherwise you will see a NoClassDefFoundError when running the tests for the bootstrap JRE classes because
 * boot class loader injection for the Java agent does not work as expected.
 */
public class RemoveFinalWithAspectAgentIT {
  private static final Instrumentation INSTRUMENTATION = AspectAgent.getInstrumentation();

  @Test
  public void checkDefinaliser() throws NoSuchMethodException {
    // Final application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getModifiers()));
    // Final method of application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getDeclaredMethod("doSomething").getModifiers()));
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
    // Final application class can be mocked
    assertEquals(
      "mocked",
      SimpleMock
        .of(FinalClass.class)
        .getInstance()
        .toString()
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
    System.out.println(new UUID(0x01234567CAFEABBAL, 0xFACE009876543210L));
    assertEquals(
      "mocked String",
      SimpleMock
        .of(String.class)
        .withToStringMessage("mocked String")
        .withConstructor(String.class)
        .getInstance("dummy")
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
    assertEquals(
      "mocked ObjectOutputStream",
      SimpleMock
        .of(ObjectOutputStream.class)
        .withToStringMessage("mocked ObjectOutputStream")
        .withConstructor(OutputStream.class)
        .getInstance(new ByteArrayOutputStream())
        .toString()
    );
  }
}
