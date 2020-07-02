package dev.sarek.junit4;

import org.junit.runner.Runner;
import org.junit.runners.JUnit4;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This optional annotation helps configure {@link SarekRunner} in a non-default way. See parameter documentation for
 * more details.
 * <p></p>
 * Please note that only the configuration for the <b>first {@link SarekRunner} ever instantiated</b> during the JVM
 * life time (or the default configuration in its absence) will be used in order to avoid conflicting agents and
 * configurations.
 * <p></p>
 * E.g it would not make sense to use the normal and the special (relocated) versions of the same JAR at the same time
 * because there would be conflicting Sarek classes with identical names, but importing dependencies from different
 * package names. There can only be one version of any class in any class loader, which is even more important for
 * classes loaded by the bootstrap class loader. So during the JVM life time please decide to either use an agent JAR
 * with or without relocated packages:
 * <ul>
 *   <li>
 *     If you need Sarek features other than class unfinalisation, either use the {@link AgentJarType#SAREK SAREK} or
 *     the {@link AgentJarType#SAREK_SPECIAL SAREK_SPECIAL} JAR.
 *   </li>
 *   <li>
 *     If you only need unfinalisation however, either use the {@link AgentJarType#UNFINAL UNFINAL} or the
 *     {@link AgentJarType#UNFINAL_SPECIAL UNFINAL_SPECIAL} JAR.
 *   </li>
 * </ul>
 */
@Retention(RUNTIME)
@Target(TYPE)
@Inherited
public @interface SarekRunnerConfig {
  /**
   * JUnit 4 runner class to delegate execution to. Defaults to {@link JUnit4}, but can be changed to something else,
   * e.g. to a Spock 1.x {@code Sputnik} runner or a {@code PowerMockRunner}.
   * <p></p>
   * The delegate class must have a public constructor taking a single {@code Class<?> testClass} argument. If you want
   * to use an incompatible type of runner, please be advised to just run Sarek as a Java agent via {@code -javaagent}
   * command line parameter, then there is no need to use {@link SarekRunner} or {@link SarekRunnerConfig} at all.
   */
  Class<? extends Runner> delegateClass() default JUnit4.class;

  /**
   * Sarek {@link AgentJarType AgentJarType} to be injected into the bootstrap class loader. Defaults to
   * {@link AgentJarType#AUTO_DETECT AUTO_DETECT}.
   * <p></p>
   * Please note: Any option here can be overridden via {@link #agentJarPathProperty()}.
   */
  AgentJarType agentJarType() default AgentJarType.AUTO_DETECT;

  /**
   * {@link SarekRunner} tries to identify the agent JAR according to your {@link #agentJarType()} configuration on the
   * class path and then inject it into the bootstrap class loader. In some cases this can fail, e.g. if you happen to
   * have Sarek libraries other an agent JAR agent on the class path.
   * <p></p>
   * Under normal circumstances you ought to fix your build/run configuration in that case, but some IDEs like IntelliJ
   * IDEA have their own ideas about how to produce a class path, especially when running internal integration tests
   * from a Sarek build itself. So just in case, here you can configure the name of a Java system property the value of
   * which is the path to the agent JAR that should be injected into the bootstrap class path.
   * <p></p>
   * If this option is set (i.e. non-empty) and the corresponding system property contains a valid path to an existing
   * agent JAR, that path will be used and {@link SarekRunner} will not try to detect the agent JAR on the class path by
   * itself. By default the property name is empty. If you e.g. set it here to something like {@code sarek.jar}, make
   * sure it is initialised before any tests run, e.g. via {@code -Dsarek.jar=/path/to/sarek-[version].jar}. If you
   * prefer to initialise the property via {@link System#setProperty(String, String)}, make sure this happens before the
   * first test is started via {@link SarekRunner}.
   */
  String agentJarPathProperty() default "";

  /**
   * Sarek agent command line options normally specified via {@code -javaagent:/my/agent.jar=options}. Defaults to none,
   * but can be overridden according to your needs, e.g.
   * <ul>
   *   <li>
   *     UnFinal agent: {@code verbose} (unfinaliser is always active)
   *   </li>
   *   <li>
   *     Sarek compound agent: {@code verbose} or {@code unfinal} (unfinaliser must be activated manually) or the
   *     combination {@code verbose,unfinal}
   *   </li>
   * </ul>
   */
  String agentOptions() default "";

  /**
   * Describes the Sarek agent JAR type which {@link SarekRunner} tries to inject into the bootstrap class loader.
   */
  enum AgentJarType {
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
    UNFINAL_SPECIAL("META-INF/sarek-unfinal-special.txt"),
    /**
     * Try to automatically detect the type of agent JAR on the class path. The other modes will also try to detect the
     * JAR file itself on the class path, but only for the specified type. Type auto detection only works predictably if
     * there is exactly one type of Sarek agent JAR on the class path, otherwise just the first one found will be used,
     * whatever the type - do not rely on a specific search order in that case.
     */
    AUTO_DETECT(
      SAREK.markerFiles[0], SAREK_SPECIAL.markerFiles[0],
      UNFINAL.markerFiles[0], UNFINAL_SPECIAL.markerFiles[0]
    );

    public final String[] markerFiles;

    AgentJarType(String... markerFiles) {
      this.markerFiles = markerFiles;
    }
  }

}
