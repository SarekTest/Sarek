package dev.sarek.agent;

public class AgentException extends Exception {
  public AgentException(String message) {
    super(message);
  }

  public AgentException(String message, Throwable cause) {
    super(message, cause);
  }
}
