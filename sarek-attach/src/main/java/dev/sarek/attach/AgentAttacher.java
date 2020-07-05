package dev.sarek.attach;

import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;
import java.util.jar.JarFile;

import static dev.sarek.attach.ConfigKey.*;
import static java.lang.Boolean.parseBoolean;

public class AgentAttacher {
  // TODO: make logging configurable
  private final static String LOG_PREFIX = "[Sarek Agent Attacher] ";
  private static final Instrumentation INSTRUMENTATION = ByteBuddyAgent.install();
  private static AgentAttacher INSTANCE;

  private final Properties config;
  private final AgentType type;
  private final JarFile jar;
  private final String options;

  public static AgentAttacher install() throws AgentAttacherException
  {
    if (INSTANCE == null) {
      try {
        INSTANCE = new AgentAttacher();
      }
      catch (Exception e) {
        throw new AgentAttacherException("Cannot attach Sarek agent", e);
      }
    }
    return INSTANCE;
  }

  private AgentAttacher()
    throws IOException, InvalidConfigException, URISyntaxException, AgentJarNotFoundException,
    ReflectiveOperationException
  {
    // Configuration precedence: system properties -> config file -> Sarek defaults
    config = getSystemPropertiesConfig(getFileConfig(getDefaultConfig()));
    JarFile agentJar = getAgentJar(getConfigProperty(AGENT_PATH));
    if (agentJar != null) {
      jar = agentJar;
      type = jarToType(jar);
    }
    else {
      type = AgentType.of(getConfigProperty(AGENT_TYPE));
      if (type == null)
        throw new InvalidConfigException(
          "No legal agent type was configured for " + AGENT_TYPE.name +
            " and no " + AGENT_PATH.name + " was configured either"
        );
      jar = typeToJar(type);
    }
    boolean logVerbose = parseBoolean(getConfigProperty(LOG_VERBOSE));
    boolean unfinalActive = parseBoolean(getConfigProperty(UNFINAL_ACTIVE));
    options = (logVerbose ? "verbose," : "") + (unfinalActive ? "UnFinal" : "");
    appendAndStartAgent();
  }

  private Properties getSystemPropertiesConfig(Properties defaults) {
    Properties systemPropertiesConfig = new Properties(defaults);
    System.getProperties().forEach((key, value) -> {
      if (((String) key).startsWith(SAREK_PREFIX))
        systemPropertiesConfig.put(key, value);
    });
    log("system properties configuration = " + systemPropertiesConfig);
    return systemPropertiesConfig;
  }

  private Properties getFileConfig(Properties defaults) {
    Properties fileConfig = new Properties(defaults);
    String configFile = getConfigFileName();
    log("config file = " + configFile);
    try (BufferedReader propertiesReader = getConfigFileReader(configFile)) {
      fileConfig.load(propertiesReader);
      log("config file configuration = " + fileConfig);
    }
    catch (Exception e) {
      log("config file not found or cannot load, using system properties and/or default configuration instead");
    }
    return fileConfig;
  }

  private Properties getDefaultConfig() {
    Properties defaultConfig = new Properties();
    defaultConfig.setProperty(AGENT_PATH.name, "");
    defaultConfig.setProperty(AGENT_TYPE.name, "sarek");
    defaultConfig.setProperty(LOG_VERBOSE.name, "false");
    defaultConfig.setProperty(UNFINAL_ACTIVE.name, "true");
    log("default configuration = " + defaultConfig);
    return defaultConfig;
  }

  private String getConfigFileName() {
    String configFileName = System.getProperty(CONFIG_FILE.name, "").trim();
    if (configFileName.equals(""))
      configFileName = "sarek.properties";
    return configFileName;
  }

  private BufferedReader getConfigFileReader(String configFile) throws FileNotFoundException {
    // Look for regular file first
    if (new File(configFile).exists())
      return new BufferedReader(new FileReader(configFile));

    // Now look for resource file on class path
    ClassLoader classLoader = AgentAttacher.class.getClassLoader();
    InputStream resourceAsStream = classLoader.getResourceAsStream(configFile);
    if (resourceAsStream != null)
      return new BufferedReader(new InputStreamReader(resourceAsStream));

    return null;
  }

  private String getConfigProperty(ConfigKey configKey) {
    return config.getProperty(configKey.name);
  }

  private AgentType jarToType(JarFile jar) throws InvalidConfigException {
    for (AgentType agentType : AgentType.values()) {
      if (jar.getEntry(agentType.markerFile) != null)
        return agentType;
    }
    throw new InvalidConfigException("Cannot identify " + jar.getName() + " as a Sarek agent JAR");
  }

  private JarFile getAgentJar(String agentPath) throws IOException {
    if (agentPath == null)
      return null;
    agentPath = agentPath.trim();
    if (agentPath.isEmpty())
      return null;
    return getAgentJar(new File(agentPath));
  }

  private JarFile getAgentJar(File agentFile) throws IOException {
    if (!(agentFile.isFile() && agentFile.canRead()))
      throw new FileNotFoundException("Agent file '" + agentFile + "' must exist and be readable");
    return new JarFile(agentFile);
  }

  private JarFile typeToJar(AgentType type) throws URISyntaxException, IOException, AgentJarNotFoundException {
    URL agentMarkerURL = getResourceURL(type.markerFile);
    if (agentMarkerURL == null)
      throw new AgentJarNotFoundException(
        "Cannot find agent JAR for configured agent type " + type.name() + " on class path"
      );
    File agentJarPath = getJarPath(agentMarkerURL);
    if (agentJarPath == null)
      throw new AgentJarNotFoundException(
        "Cannot find agent JAR for configured agent type " + type.name() + " on class path, " +
          "marker file " + agentMarkerURL + " is not inside a JAR."
      );
    return getAgentJar(agentJarPath);
  }

  private URL getResourceURL(String resourceName) {
    return AgentAttacher.class.getClassLoader().getResource(resourceName);
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

  private void appendAndStartAgent() throws ReflectiveOperationException {
    INSTRUMENTATION.appendToBootstrapClassLoaderSearch(jar);
    if (jar.getName().contains("sarek-unfinal")) {
      Class
        .forName("dev.sarek.agent.unfinal.UnFinalAgent")
        .getMethod("premain", String.class, Instrumentation.class)
        .invoke(null, options, INSTRUMENTATION);
    }
    else {
      Class
        .forName("dev.sarek.agent.SarekAgent")
        .getMethod("premain", String.class, Instrumentation.class)
        .invoke(null, options, INSTRUMENTATION);
    }
  }

  @Override
  public String toString() {
    return "AgentAttacher(" +
      "type=" + type.name() +
      ", jar=" + jar.getName() +
      ", options=" + options +
      ')';
  }

  private static void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

}
