package dev.sarek.attach;

/**
 * Describes a Sarek agent configuration key name used to configure {@link AgentAttacher}
 */
public enum ConfigKey {

  CONFIG_FILE("config.file"),
  AGENT_PATH("agent.path"),
  AGENT_TYPE("agent.type"),
  LOG_VERBOSE("log.verbose"),
  UNFINAL_ACTIVE("unfinal.active");

  public static final String SAREK_PREFIX = "dev.sarek.";

  public final String name;

  ConfigKey(String name) {
    this.name = SAREK_PREFIX + name;
  }

}
