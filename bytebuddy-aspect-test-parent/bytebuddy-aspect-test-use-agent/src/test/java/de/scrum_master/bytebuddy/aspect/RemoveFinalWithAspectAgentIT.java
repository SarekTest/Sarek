package de.scrum_master.bytebuddy.aspect;

import de.scrum_master.app.FinalClass;
import de.scrum_master.bytebuddy.ByteBuddyAspectAgent;
import org.junit.Test;

import java.io.ObjectOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.junit.Assert.assertFalse;

/**
 * When running this test in an IDE like IntelliJ IDEA, please make sure that the JARs for both this module
 * ('bytebuddy-aspect-agent') and 'bytebuddy-aspect' have been created. Just run 'mvn package' first. In IDEA
 * you can also edit the run configuration for this test or a group of tests and add a "before launch" action,
 * select "run Maven goal" and then add goal 'package'.
 * <p>
 * Furthermore, make sure add this to the Maven Failsafe condiguration:
 * <argLine>-javaagent:target/bytebuddy-aspect-agent-1.0-SNAPSHOT.jar</argLine>
 * Otherwise you will see a NoClassDefFoundError when running the tests for the bootstrap JRE classes because
 * boot classloader injection for the Java agent does not work as expected.
 */
public class RemoveFinalWithAspectAgentIT {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAspectAgent.getInstrumentation();

  @Test
  public void checkDefinaliser() throws NoSuchMethodException {
//    RemoveFinalTransformer.install(INSTRUMENTATION);

    // Final application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getModifiers()));
    // Final method of application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getDeclaredMethod("doSomething").getModifiers()));
    // Final bootstrap class has been definalised
    assertFalse(Modifier.isFinal(UUID.class.getModifiers()));
    // Final method of bootstrap class has been definalised
    assertFalse(Modifier.isFinal(ObjectOutputStream.class.getDeclaredMethod("writeObject", Object.class).getModifiers()));
  }

}
