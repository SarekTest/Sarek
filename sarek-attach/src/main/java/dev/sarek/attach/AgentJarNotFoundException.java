package dev.sarek.attach;

public class AgentJarNotFoundException extends AgentAttacherException {
  public AgentJarNotFoundException(String message) {
    super(message);
  }

  public AgentJarNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
