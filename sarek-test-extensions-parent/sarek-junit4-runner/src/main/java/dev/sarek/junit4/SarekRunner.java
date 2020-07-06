package dev.sarek.junit4;

import dev.sarek.attach.AgentAttacher;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.*;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

@SarekRunnerDelegate
public class SarekRunner extends Runner implements Filterable, Sortable {
  // TODO: make logging configurable
  private final static String LOG_PREFIX = "[Sarek JUnit 4 Runner] ";
  private SarekRunnerDelegate sarekRunnerDelegate;

  private final Runner delegate;

  public SarekRunner(Class<?> testClass) throws InitializationError {
    this(testClass, null);
  }

  public SarekRunner(Class<?> testClass, Runner delegate) throws InitializationError {
    try {
      log("going to attach Sarek agent (if not attached yet)");

      // IMPORTANT: Create runner delegate instance (and with it an instance of the test class) *after* bootstrap
      // injection, because the test class might reference Sarek classes or its dependencies. Those would be resolved as
      // soon as the test class is loaded, resulting in them being loaded via application class loader (uh-oh!) instead
      // of via bootstrap class loader like they should be.

      // Inject agent JAR into bootstrap classloader
      AgentAttacher.install();
      // Initialise runner delegate
      this.delegate = delegate != null
        ? delegate
        : createDelegate(getDelegateClass(testClass), testClass);
    }
    catch (AssertionError | Exception exception) {
      throw new InitializationError(exception);
    }
  }

  private static Class<? extends Runner> getDelegateClass(Class<?> testClass) {
    // Try to get annotation from test class
    SarekRunnerDelegate delegateAnnotation = testClass.getAnnotation(SarekRunnerDelegate.class);
    // If none present, get default annotation from own class
    if (delegateAnnotation == null)
      delegateAnnotation = SarekRunner.class.getAnnotation(SarekRunnerDelegate.class);
    assert delegateAnnotation != null;
    return delegateAnnotation.value();
  }

  private Runner createDelegate(Class<? extends Runner> delegateClass, Class<?> testClass)
    throws ReflectiveOperationException
  {
    return delegateClass.getConstructor(Class.class).newInstance(testClass);
  }

  // ------------------------------------------------------------------------
  // Override some essential runner methods and redirect them to the delegate
  // ------------------------------------------------------------------------

  @Override
  public Description getDescription() {
    return delegate.getDescription();
  }

  @Override
  public void run(RunNotifier notifier) {
    delegate.run(notifier);
  }

  @Override
  public int testCount() {
    return delegate.testCount();
  }

  @Override
  public void filter(Filter filter) throws NoTestsRemainException {
    // This method is necessary for IntelliJ IDEA to be able to run single test methods.
    if (delegate instanceof Filterable)
      ((Filterable) delegate).filter(filter);
  }

  @Override
  public void sort(Sorter sorter) {
    // This method is not necessary for IntelliJ IDEA to be able to run single test methods, but I saw that Spock 1.x
    // Sputnik also implements it, so it cannot hurt.
    if (delegate instanceof Sortable)
      ((Sortable) delegate).sort(sorter);
  }

  private static void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

}
