package dev.sarek.junit4;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.*;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarFile;

@SarekRunnerConfig
public class SarekRunner extends Runner implements Filterable, Sortable {
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();
  private static SarekRunnerConfig CONFIG;
  private static JarFile AGENT_JAR;

  private final Runner delegate;

  public SarekRunner(Class<?> testClass) throws InitializationError {
    this(testClass, null);
  }

  public SarekRunner(Class<?> testClass, Runner delegate) throws InitializationError {
    this(testClass, delegate, null);
  }

  protected SarekRunner(Class<?> testClass, Runner delegate, SarekRunnerConfig config) throws InitializationError {
    try {
      // Configuration could have been initialised by a previous test run
      if (CONFIG == null)
        CONFIG = config != null ? config : getConfig(testClass);
      assert CONFIG != null;

      // Agent JAR could have been initialised by a previous test run
      if (AGENT_JAR == null) {
        findAgentJarBySystemProperty();
        if (AGENT_JAR == null) {
          findAgentJarOnClassPath();
          assert AGENT_JAR != null;
        }
      }

      // IMPORTANT: Create runner delegate instance (and with it an instance of the test class) *after* bootstrap
      // injection, because the test class might reference Sarek classes or its dependencies. Those would be resolved as
      // soon as the test class is loaded, resulting in them being loaded via application class loader (uh-oh!) instead
      // of via bootstrap class loader like they should be.

      // Inject agent JAR into bootstrap classloader
      appendAndStartAgent();
      // Initialise runner delegate
      this.delegate = delegate != null ? delegate : createDelegate(CONFIG.delegateClass(), testClass);
    }

    catch (InitializationError initializationError) {
      throw initializationError;
    }
    catch (AssertionError | Exception exception) {
      throw new InitializationError(exception);
    }
  }

  private static SarekRunnerConfig getConfig(Class<?> testClass) {
    // Try to get configuration from test class annotation
    SarekRunnerConfig config = testClass.getAnnotation(SarekRunnerConfig.class);
    // If none present, get default configuration from own class
    return config != null
      ? config
      : SarekRunner.class.getAnnotation(SarekRunnerConfig.class);
  }

  private void findAgentJarBySystemProperty() throws IOException, InitializationError {
    String agentPath = null;
    String agentJarPathProperty = CONFIG.agentJarPathProperty().trim();
    if (!agentJarPathProperty.isEmpty())
      agentPath = System.getProperty(agentJarPathProperty, null);
    if (agentPath != null) {
      agentPath = agentPath.trim();
      if (!"".equals(agentPath))
        AGENT_JAR = getAgentJar(agentPath);
    }
  }

  private void findAgentJarOnClassPath() throws InitializationError, URISyntaxException, IOException {
    URL agentMarkerURL = getFirstFoundResourceURL(CONFIG.agentJarType().markerFiles);
    if (agentMarkerURL == null)
      throw new InitializationError(
        "Cannot find agent JAR for configured agent type " + CONFIG.agentJarType().name() + " on class path"
      );
    File agentJarPath = getJarPath(agentMarkerURL);
    if (agentJarPath == null)
      throw new InitializationError(
        "Cannot find agent JAR for configured agent type " + CONFIG.agentJarType().name() + " on class path, " +
          "marker file " + agentMarkerURL + " is not inside a JAR."
      );
    AGENT_JAR = getAgentJar(agentJarPath);
  }

  private void appendAndStartAgent() throws ReflectiveOperationException {
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(AGENT_JAR);
    if (AGENT_JAR.getName().contains("sarek-unfinal")) {
      Class
        .forName("dev.sarek.agent.unfinal.UnFinalAgent")
        .getMethod("premain", String.class, Instrumentation.class)
        .invoke(null, CONFIG.agentOptions(), INSTRUMENTATION);
    }
    else {
      Class
        .forName("dev.sarek.agent.SarekAgent")
        .getMethod("premain", String.class, Instrumentation.class)
        .invoke(null, CONFIG.agentOptions(), INSTRUMENTATION);
    }
  }

  private Runner createDelegate(Class<? extends Runner> delegateClass, Class<?> testClass)
    throws ReflectiveOperationException
  {
    return delegateClass.getConstructor(Class.class).newInstance(testClass);
  }

  private JarFile getAgentJar(String agentPath) throws IOException, InitializationError {
    return getAgentJar(new File(agentPath));
  }

  private JarFile getAgentJar(File agentFile) throws IOException, InitializationError {
    if (!(agentFile.isFile() && agentFile.canRead()))
      throw new InitializationError("Agent file '" + agentFile + "' must exist and be readable");
    return new JarFile(agentFile);
  }

  private File getJarPath(URL resourceURL) throws URISyntaxException {
    if (resourceURL == null)
      return null;
    if (!resourceURL.getProtocol().equals("jar"))
      return null;
    String resourcePath = resourceURL.getPath();
    if (!(resourcePath.startsWith("file:") && resourcePath.contains(".jar!/")))
      return null;
    return new File(new URI(resourcePath.replaceFirst("(\\.jar)!/.*", "$1")));
  }

  private URL getFirstFoundResourceURL(String... candidateResourceNames) {
    final ClassLoader classLoader = SarekRunner.class.getClassLoader();
    for (String resourceName : candidateResourceNames) {
      URL resourceURL = classLoader.getResource(resourceName);
      if (resourceURL != null)
        return resourceURL;
    }
    return null;
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

}
