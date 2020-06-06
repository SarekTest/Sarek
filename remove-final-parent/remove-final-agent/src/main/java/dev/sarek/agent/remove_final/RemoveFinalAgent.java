package dev.sarek.agent.remove_final;

import dev.sarek.agent.Agent;
import dev.sarek.agent.Agent.TransformerFactoryMethod.IllegalTransformerFactoryMethodException;
import dev.sarek.agent.AgentRegistry.AgentAlreadyRegisteredException;

import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Set;

public class RemoveFinalAgent extends Agent {
  public RemoveFinalAgent(String options, Instrumentation instrumentation)
    throws ReflectiveOperationException, AgentAlreadyRegisteredException, IllegalTransformerFactoryMethodException
  {
    super(options, instrumentation);
  }

  public RemoveFinalAgent(String options)
    throws ReflectiveOperationException, AgentAlreadyRegisteredException, IllegalTransformerFactoryMethodException
  {
    super(options);
  }

  /**
   * Attach agent dynamically after JVM start-up
   *
   * @param options path to configuration properties file for class
   *                {@link RemoveFinalTransformer}. Add this parameter on the command line
   *                after the Java agent path via <code>=/path/to/my-config.properties</code>.
   */
  public static void agentmain(String options, Instrumentation instrumentation)
    throws ReflectiveOperationException, AgentAlreadyRegisteredException, IllegalTransformerFactoryMethodException
  {
    premain(options, instrumentation);
  }

  /**
   * Start agent via <code>-javaagent:/path/to/my-agent.jar=<i>options</i></code> JVM parameter
   *
   * @param options path to configuration properties file for class
   *                {@link RemoveFinalTransformer}. Add this parameter on the command line
   *                after the Java agent path via <code>=/path/to/my-config.properties</code>.
   */
  public static void premain(String options, Instrumentation instrumentation)
    throws AgentAlreadyRegisteredException, ReflectiveOperationException, IllegalTransformerFactoryMethodException
  {
    // TODO: Maybe catch exceptions + log errors instead so as to enable the system to start up anyway
    new RemoveFinalAgent(options, instrumentation);
  }

  @Override
  public String getAgentId() {
    return "RemoveFinal";
  }

  @Override
  public Set<String> getOptionKeys() {
    Set<String> optionKeys = new HashSet<>();
    optionKeys.add("verbose");
    return optionKeys;
  }

  @Override
  public boolean canRetransform() {
    return false;
  }

  @Override
  public TransformerFactoryMethod getTransformerFactoryMethod()
    throws NoSuchMethodException, IllegalTransformerFactoryMethodException, ClassNotFoundException
  {
    return new TransformerFactoryMethod(
      "dev.sarek.agent.remove_final.RemoveFinalTransformer",
      "createTransformer",
      new Class<?>[] { boolean.class }
    );
  }

  @Override
  public Object[] getDefaultTransformerArgs(Object... factoryMethodArgs) {
    return new Object[] {
      getOptions().containsKey("verbose")
    };
  }

}
