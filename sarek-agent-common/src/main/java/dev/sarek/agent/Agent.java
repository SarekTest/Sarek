package dev.sarek.agent;

import dev.sarek.agent.Agent.TransformerFactoryMethod.IllegalTransformerFactoryMethodException;
import dev.sarek.agent.AgentRegistry.AgentAlreadyRegisteredException;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.sarek.agent.AgentRegistry.AGENT_REGISTRY;

public abstract class Agent {
  private static Instrumentation _instr;
  private final Map<String, String> options = new HashMap<>();

  // TODO: parse options
  // TODO: Javadoc incl. runtime exceptions
  public Agent(String options, Instrumentation instrumentation)
    throws ReflectiveOperationException, AgentAlreadyRegisteredException, IllegalTransformerFactoryMethodException
  {
    parseOptions(options);
    setInstrumentation(instrumentation);
    Object[] transformerArgs = getDefaultTransformerArgs();
    if (transformerArgs != null) {
      getInstrumentation().addTransformer(
        getTransformerFactoryMethod().invoke(transformerArgs),
        canRetransform()
      );
    }
    AGENT_REGISTRY.register(this);
  }

  public Agent(String options)
    throws ReflectiveOperationException, AgentAlreadyRegisteredException, IllegalTransformerFactoryMethodException
  {
    this(options, null);
  }

  /**
   * Options have a simple format for single agents and an extended format for multi agents (i.e. one options string
   * needs to configure multiple agents started from the same JAR):
   * <ul>
   *   <li>Simple format: <code>key1,key2=value2,key3,key4=value4</code></li>
   *   <li>Extended format: <code>agentId1{key1,key2=value2,key3,key4=value4};agentId2{key1,key2=value2}</code></li>
   * </ul>
   * <b>Please note:</b>
   * <ul>
   *   <li>
   *     The options parser has a very simple implementation. Thus, agent IDs, option names and values
   *     must not contain any of the separator characters <code>{ } , ; =</code>.
   *   </li>
   *   <li>
   *     Furthermore, agent IDs and option names must be alphanumeric ASCII (regular expression
   *     <code>[A-Za-z0-9]+</code>) and are case-sensitive, so please mind your spelling. E.g. agent ID
   *     <code>myAgent</code> is not the same as <code>myagent</code> and key <code>logDebug</code> is not the same as
   *     <code>LoGdEBuG</code>.
   *   </li>
   *   <li>
   *     Option values are optional. If an option key has not value such as <code>myOption</code> it is interpreted as a
   *     quasi boolean option and its value will be set to the string <code>"true"</code> (not to the value
   *     <code>null</code>). However, it is permissible to use <code>myOption=</code> in order to initialise the value
   *     with an empty string <code>""</code>.
   *   </li>
   *   <li>
   *     The options parser tries to handle whitespace outside of option values gracefully, i.e. it would ignore it in
   *     instances such as <code>key1 , key2 =value2, key3,key4 =value4</code> or
   *     <code>agentId1 { key1,key2 =value2, key3,key4=value4} ; agentId2 { key1, key2 =value2}</code>.
   *   </li>
   *   <li>
   *     Whitespace characters inside option values are being preserved, also leading and trailing ones, just in case
   *     they have a special meaning for the corresponding agent, such as <code>indent=  </code> (value consists of two
   *     spaces) or <code>logPrefix=[Special Agent] </code>.
   *   </li>
   *   <li>
   *     If the same agent ID or option name (per agent) occurs multiple times, no error is raised but no specific
   *     result is guaranteed with regard to merging or ignoring options.
   *   </li>
   *   <li>
   *     If option parsing is lenient in some regard nopt mentioned here, please do not rely on it to stay like that in
   *     the future.
   *   </li>
   * </ul>
   * <p>
   *
   * @param options command line options for one (simple format) or more (extended format) agents
   */
  private void parseOptions(String options) {
    // No options -> do nothing
    if (options == null)
      return;

    // Extended format -> try to find configuration matching agent ID
    if (options.contains("{")) {
      Pattern ptExtendedFormat = Pattern.compile(getAgentId() + "\\s*\\{(.*)}");
      // Replace multi agent options in extended format by simple format for this agent ID
      options = Arrays
        .stream(options.split("\\s*;\\s*"))
        .filter(agentOptions -> agentOptions.startsWith(getAgentId() + "{"))
        .map(agentOptions -> ptExtendedFormat.matcher(agentOptions).group(1))
        .findFirst()
        .orElse(null);
    }

    // Multi-agent config, but no configuration for this particular agent ID found
    if (options == null)
      return;

    // Extract option key/value pairs from simple format
    Set<String> legalOptions = getOptionKeys();
    Pattern ptKeyWithOptionalValue = Pattern.compile("\\s*(\\p{Alnum}+)\\s*(=(.*))?");
    Arrays
      .stream(options.split(","))
      .forEach(keyValuePair -> {
        System.out.println("keyValuePair = " + keyValuePair);
        Matcher matcher = ptKeyWithOptionalValue.matcher(keyValuePair);
        if (matcher.matches()) {
          String key = matcher.group(1);
          if (legalOptions.contains(key)) {
            String value = matcher.group(3);
            this.options.put(key, value != null ? value : "true");
          }
        }
      });
  }

  public Map<String, String> getOptions() {
    return options;
  }

  /**
   * Obtain an instrumentation instance, lazily creating it via {@link ByteBuddyAgent#install()} if necessary.
   * <p></p>
   * <b>Please note:</b>
   * <ul>
   *   <li>
   *     Whenever a JVM ist started with <code>-javaagent:/path/to/my-agent.jar</code>, its
   *     <code>premain</code> method automatically obtains an {@link Instrumentation} instance, which it should pass on
   *     to {@link #Agent(String, Instrumentation)}. This has the effect that the internal instrumentation instance
   *     backing this getter will then also be initialised with the passed on value.
   *   </li>
   *   <li>
   *     This way we can avoid having to call {@link ByteBuddyAgent#install()}, which is an advantage in case the JVM
   *     runs on top of a simple JRE and not on top of a full-blown JDK, because {@link ByteBuddyAgent#install()} uses
   *     the Java istrumentation API which is only available in JDK environments.
   *   </li>
   * </ul>
   *
   * @return an instrumentation instance usable for (un)registering {@link ClassFileTransformer}s
   */
  public static Instrumentation getInstrumentation() {
    // Note to myself: An alternative to ByteBuddyAgent would be InstrumentationFactory from Apache OpenJPA kernel
    // module, see https://bit.ly/2z6yLzx
    if (_instr == null)
      _instr = ByteBuddyAgent.install();
    return _instr;
  }

  private static void setInstrumentation(Instrumentation instrumentation) {
    if (_instr == null && instrumentation != null)
      _instr = instrumentation;
  }

  public boolean isActive() {
    return AGENT_REGISTRY.isRegistered(getClass());
  }

  public abstract String getAgentId();

  public abstract Set<String> getOptionKeys();

  public abstract boolean canRetransform();

  public abstract TransformerFactoryMethod getTransformerFactoryMethod()
    throws NoSuchMethodException, IllegalTransformerFactoryMethodException, ClassNotFoundException;

  public abstract Object[] getDefaultTransformerArgs(Object... factoryMethodArgs);

  /**
   * Describes a static factory method returning a {@link ClassFileTransformer} instance
   */
  public static class TransformerFactoryMethod {
    private final Method factoryMethod;

    public TransformerFactoryMethod(String className, String methodName, Class<?>[] methodArgTypes)
      throws NoSuchMethodException, ClassNotFoundException, IllegalTransformerFactoryMethodException
    {
      factoryMethod = Class.forName(className).getDeclaredMethod(methodName, methodArgTypes);
      verify();
    }

    private void verify() throws IllegalTransformerFactoryMethodException {
      if (!Modifier.isStatic(factoryMethod.getModifiers()))
        throw new IllegalTransformerFactoryMethodException(
          "Transformer factory method " + factoryMethod + " must be static"
        );

      Class<?> factoryMethodReturnType = factoryMethod.getReturnType();
      if (!ClassFileTransformer.class.isAssignableFrom(factoryMethodReturnType))
        throw new IllegalTransformerFactoryMethodException(
          "Transformer factory method return type " + factoryMethodReturnType.getName() +
            " is incompatible with expected type " + ClassFileTransformer.class.getName()
        );
    }

    public ClassFileTransformer invoke(Object... args) throws InvocationTargetException, IllegalAccessException {
      // TODO: pass through 'logConstructorMock'
      return (ClassFileTransformer) factoryMethod.invoke(null, args);
    }

    public static class IllegalTransformerFactoryMethodException extends Exception {
      public IllegalTransformerFactoryMethodException(String message) {
        super(message);
      }
    }

  }

}
