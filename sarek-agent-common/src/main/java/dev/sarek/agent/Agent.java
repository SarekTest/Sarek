package dev.sarek.agent;

import dev.sarek.agent.Agent.TransformerFactoryMethod.IllegalTransformerFactoryMethodException;
import dev.sarek.agent.AgentRegistry.AgentAlreadyRegisteredException;
import dev.sarek.agent.OptionParser.IllegalAgentIdException;
import dev.sarek.agent.OptionParser.IllegalOptionNameException;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import static dev.sarek.agent.AgentRegistry.AGENT_REGISTRY;

public abstract class Agent {
  private static Instrumentation _instr;
  private final Map<String, String> options;

  // TODO: Javadoc incl. runtime exceptions
  public Agent(String options, Instrumentation instrumentation)
    throws IllegalOptionNameException, IllegalAgentIdException, ReflectiveOperationException,
    AgentAlreadyRegisteredException, IllegalTransformerFactoryMethodException

  {
    this.options = getOptionParser().parse(options);
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
    throws IllegalOptionNameException, IllegalAgentIdException, ReflectiveOperationException,
    AgentAlreadyRegisteredException, IllegalTransformerFactoryMethodException
  {
    this(options, null);
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

  public abstract String[] getOptionKeys();

  /**
   * Creates an option parser. Override this method if you want to provide a more specialised or sophisticated option
   * parser to be used in the constructor.
   *
   * @return a new instance created via <code>new OptionParser(getAgentId(), getOptionKeys())</code>
   * @see OptionParser#OptionParser(String, String...)
   */
  protected OptionParser getOptionParser()
    throws IllegalOptionNameException, IllegalAgentIdException
  {
    return new OptionParser(getAgentId(), getOptionKeys());
  }

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
