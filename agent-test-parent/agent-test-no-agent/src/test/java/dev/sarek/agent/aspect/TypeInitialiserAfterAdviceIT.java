package dev.sarek.agent.aspect;

import dev.sarek.agent.test.SeparateJVM;
import dev.sarek.app.UnderTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static dev.sarek.agent.test.TestHelper.isClassLoaded;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * This test checks features which do not involve bootloader classes.
 * So we do not need a Java agent here.
 */
@Category(SeparateJVM.class)
public class TypeInitialiserAfterAdviceIT {
  private Weaver weaver;

  @After
  public void cleanUp() {
    if (weaver != null)
      weaver.unregisterTransformer();
  }

  @Test
  public void typeInitialiser() {
    assertFalse(
      "This test needs to run in its own JVM, otherwise the type initialiser (static block) " +
        "for the class under test could have run before already",
      isClassLoaded("dev.sarek.app.UnderTest")
    );

    // Create weaver, directly registering a target class in the constructor
    weaver = Weaver
      .forTypes(is(UnderTest.class))
      .addAdvice(
        null,
        new TypeInitialiserAroundAdvice(
          clazz -> {
            System.out.println("Before type initialiser advice #" + ++UnderTest.typeInitialiserAdviceCounter);
            // false = suppress type initialiser
            // true = initialiser runs, sets static property and prints something on the console
            return true;
          },
          // Set static property to a value different from the one set by the type initialiser
          (clazz, proceedMode, throwable) -> {
            System.out.println("After type initialiser advice #" + UnderTest.typeInitialiserAdviceCounter);
            UnderTest.staticBlockCounter *= 11;
          }
        )
      )
      .addTargets(UnderTest.class)
      .build();

    // Caveat: Even though there are 3 static blocks in the target class, only one advice pair is being triggered.
    assertEquals(1, UnderTest.typeInitialiserAdviceCounter);
    // 3 static blocks, each incrementing by 1 -> 3. The after advice multiplies by 11 -> 33.
    assertEquals(33, UnderTest.staticBlockCounter);
  }

}
