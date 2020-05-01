package de.scrum_master.bytebuddy.aspect;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.Test;

import java.io.IOException;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.junit.Assert.assertEquals;

public class WeaverTest {
  private static final String TEXT = "To be, or not to be, that is the question";

  @Test
  public void test() throws IOException {
    Weaver weaver = new Weaver(
      ByteBuddyAgent.install(),
      named("java.lang.String"),
      named("replaceAll").and(takesArguments(String.class, String.class)),
      createAdvice()
    );

    // Before registering TEXT as an advice target instance, 'replaceAll' behaves normally
    assertEquals("To modify, or not to modify, that is the question", TEXT.replaceAll("be", "modify"));

    weaver.addTarget(TEXT);

    // --- Check expected aspect behaviour ---
    // (1) Proceed to target method without any modifications
    assertEquals("To eat, or not to eat, that is the question", TEXT.replaceAll("be", "eat"));
    // (2) Do not proceed to target method, let aspect modify input text instead
    assertEquals("T0 bε, 0r n0t t0 bε, that is thε quεsti0n", TEXT.replaceAll("be", "skip"));
    // (3) Aspect handles exception, returns dummy result
    assertEquals("caught exception from proceed", TEXT.replaceAll("be", "$1"));
    // (4) Aspect modifies replacement parameter
    assertEquals("To ❤, or not to ❤, that is the question", TEXT.replaceAll("be", "modify"));

    weaver.removeTarget("To be, or not to be, that is the question");

    // After unregistering TEXT as an advice target instance, 'replaceAll' behaves normally again
    assertEquals("To modify, or not to modify, that is the question", TEXT.replaceAll("be", "modify"));
  }

  private AroundAdvice createAdvice() {
    return new AroundAdvice(
      // Should proceed?
      (target, method, args) -> {
        String replacement = (String) args[1];
        if (replacement.equalsIgnoreCase("skip"))
          return false;
        if (replacement.equalsIgnoreCase("modify"))
          args[1] = "❤";
        return true;
      },

      // Handle result of (optional) proceed
      (target, method, args, proceedMode, returnValue, throwable) -> {
        if (throwable != null)
          return "caught exception from proceed";
        if (!proceedMode)
          return ((String) target).replace("e", "ε").replace("o", "0");
        return returnValue;
      }
    );
  }
}
