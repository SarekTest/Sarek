package dev.sarek.attach;

/**
 * Describes a Sarek agent JAR type which {@link AgentAttacher} tries to inject into the bootstrap class loader.
 */
public enum AgentType {

  /**
   * Sarek agent JAR containing all features (class unfinalisation, Sarek mocks). Third-party dependencies (ByteBuddy,
   * Objenesis) are kept under their original package names ({@code net.bytebuddy}, {@code net.bytebuddy.jar.asm},
   * {@code org.objenesis}).
   */
  SAREK("META-INF/sarek.txt"),
  /**
   * Sarek agent JAR containing all features (class unfinalisation, Sarek mocks). Third-party dependencies (ByteBuddy
   * incl. ASM, Objenesis) are relocated to internal Sarek package names ({@code dev.sarek.jar.bytebuddy},
   * {@code dev.sarek.jar.asm}, {@code dev.sarek.jar.objenesis}). Use this JAR if you use the same third-party
   * dependencies and experience any problems due to version conflicts (bugs, API changes).
   */
  SAREK_SPECIAL("META-INF/sarek-special.txt"),
  /**
   * Sarek class unfinaliser agent JAR. Third-party dependencies (ByteBuddy incl. ASM) are kept under their original
   * package names ({@code net.bytebuddy}, {@code net.bytebuddy.jar.asm}). Use this JAR if you only wish to use class
   * unfinalisation and do not need Sarek mocks.
   */
  UNFINAL("META-INF/sarek-unfinal.txt"),
  /**
   * Sarek class unfinaliser agent JAR. Third-party dependencies (ByteBuddy incl. ASM) are relocated to internal Sarek
   * package names ({@code dev.sarek.jar.bytebuddy}, {@code dev.sarek.jar.asm}). Use this JAR if you only wish to use
   * class unfinalisation and do not need Sarek mocks, but at the same time you already use the same third-party
   * dependencies and experience any problems due to version conflicts (bugs, API changes).
   */
  UNFINAL_SPECIAL("META-INF/sarek-unfinal-special.txt");

  public final String markerFile;

  AgentType(String markerFile) {
    this.markerFile = markerFile;
  }

  /**
   * @return value of config file property or system property {@code dev.sarek.agent.type} corresponding with this agent
   * type, i.e. any of {@code sarek}, {@code sarek-special}, {@code unfinal}, {@code unfinal-special}
   */
  public String toConfigValue() {
    return markerFile
      .replaceFirst("^META-INF/", "")
      .replaceFirst("\\.txt$", "")
      .replaceFirst("^sarek-unfinal", "unfinal");
  }

  /**
   * @param configValue value of config file property or system property {@code dev.sarek.agent.type}, i.e. any of
   *                    {@code sarek}, {@code sarek-special}, {@code unfinal}, {@code unfinal-special}
   * @return {@link AgentType} enum value corresponding to <i>configValue</i>. If <i>configValue</i> is {@code null} or
   * illegal, the return value will be {@code null}, no exception will be thrown.
   */
  public static AgentType of(String configValue) {
    try {
      // e.g. 'sarek' -> SAREK or 'unfinal-special' -> UNFINAL_SPECIAL
      configValue = configValue.trim().replace('-', '_').toUpperCase();
      return valueOf(configValue);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }
}
