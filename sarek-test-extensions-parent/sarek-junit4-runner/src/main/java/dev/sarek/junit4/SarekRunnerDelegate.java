package dev.sarek.junit4;

import org.junit.runner.Runner;
import org.junit.runners.JUnit4;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This optional annotation tells {@link SarekRunner} to use a specific JUnit 4 {@link Runner} as a delegate. The
 * default delegate type is {@link JUnit4}.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Inherited
public @interface SarekRunnerDelegate {
  /**
   * JUnit 4 runner class to delegate execution to. Defaults to {@link JUnit4}, but can be changed to something else,
   * e.g. to a Spock 1.x {@code Sputnik} runner or a {@code PowerMockRunner}.
   * <p>
   * The delegate class must have a public constructor taking a single {@code Class<?> testClass} argument. If you want
   * to use an incompatible type of runner, please be advised to just run Sarek as a Java agent via {@code -javaagent}
   * command line parameter, then there is no need to use {@link SarekRunner} or {@link SarekRunnerDelegate} at all.
   */
  Class<? extends Runner> value() default JUnit4.class;
}
