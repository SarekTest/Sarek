package dev.sarek.agent;

import java.util.HashMap;
import java.util.Map;

public class AgentRegistry {
  public static final AgentRegistry AGENT_REGISTRY = new AgentRegistry();

  private final Map<Class<? extends Agent>, Agent> registeredAgents;

  private AgentRegistry() {
    registeredAgents = new HashMap<>();
  }

  public void register(Agent agent) throws AgentAlreadyRegisteredException {
    Class<? extends Agent> agentClass = agent.getClass();
    if (registeredAgents.containsKey(agentClass))
      throw new AgentAlreadyRegisteredException("an agent of class " + agentClass.getName() + " is already registered");
    registeredAgents.put(agentClass, agent);
  }

  public boolean isRegistered(Class<? extends Agent> agentClass) {
    return registeredAgents.containsKey(agentClass);
  }

  public Agent get(Class<? extends Agent> agentClass) {
    return registeredAgents.get(agentClass);
  }

  public static class AgentAlreadyRegisteredException extends Exception {
    public AgentAlreadyRegisteredException(String message) {
      super(message);
    }
  }

}
