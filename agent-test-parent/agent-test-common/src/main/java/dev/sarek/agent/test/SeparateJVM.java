package dev.sarek.agent.test;

/**
 * In JUnit 4, use <code>@Category(SeparateJVM.class)</code> on each class you wish to run in a separate JVM in order to
 * ensure certain JVM start-up conditions, e.g.
 * <ul>
 *   <li>certain bootstrap or application classes have not been loaded yet,</li>
 *   <li>certain byte code transformers have not been registered yet.</li>
 * </ul>
 * <p></p>
 * <b>Please only use on class level</b>, because in Maven Surefire/Failsafe the highest test isolation level is
 * one JVM per class. So if a test class contains multiple methods requiring separate JVMs, please split them
 * into separate classes.
 */
public interface SeparateJVM {
}
