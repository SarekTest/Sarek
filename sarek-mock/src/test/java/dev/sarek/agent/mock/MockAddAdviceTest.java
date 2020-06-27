package dev.sarek.agent.mock;

import dev.sarek.agent.aspect.ConstructorAroundAdvice;
import dev.sarek.agent.aspect.TypeInitialiserAroundAdvice;
import dev.sarek.agent.test.SeparateJVM;
import dev.sarek.app.UnderTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static dev.sarek.agent.mock.MockFactory.forClass;
import static dev.sarek.agent.test.TestHelper.isClassLoaded;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * This test checks features which do not involve bootloader classes.
 * So we do not need a Java agent here.
 */
@Category(SeparateJVM.class)
public class MockAddAdviceTest {
  @Test
  public void addAdvice() {
    assertFalse(
      "This test needs to run in its own JVM, otherwise the type initialiser (static block) " +
        "for the class under test could have run before already",
      isClassLoaded("dev.sarek.app.UnderTest")
    );

    try (
      MockFactory<UnderTest> mockFactory = forClass(UnderTest.class)
        .addAdvice(
          null,
          new TypeInitialiserAroundAdvice(
            clazz -> {
              System.out.println("Before type initialiser advice #" + ++UnderTest.typeInitialiserAdviceCounter);
              // false = suppress type initialiser
              // true = initialiser runs, sets static property and prints something on the console
              return false;
            },
            // Set static property to a value different from the one set by the type initialiser
            (clazz, proceedMode, throwable) -> {
              System.out.println("After type initialiser advice #" + UnderTest.typeInitialiserAdviceCounter);
              UnderTest.staticBlockCounter *= 11;
            }
          )
        )
        .addAdvice(
          takesArguments(String.class),
          new ConstructorAroundAdvice(
            (constructor, args) -> {
              if (!args[0].equals("default"))
                args[0] = "ADVISED";
            },
            null
          )
        )
        .build()
    )
    {
      // Caveat: Even though there are 3 static blocks in the target class, only one advice pair is being triggered.
      assertEquals(1, UnderTest.typeInitialiserAdviceCounter);
      // 3 static blocks suppressed -> 0. The after advice multiplies by 11 -> 0.
      assertEquals(0, UnderTest.staticBlockCounter);

      // Constructor UnderTest(String) was stubbed to pass through a modified value
      assertEquals("ADVISED", new UnderTest("whatever").getName());
      // Constructor UnderTest(String) passes through value "default" unchanged
      assertEquals("default", new UnderTest("default").getName());
      // The no-args constructor passes on this("default"), so again the value is not changed
      assertEquals("default", new UnderTest().getName());
    }
  }

}
