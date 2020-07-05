package dev.sarek.attach;

public class InvalidConfigException extends AgentAttacherException {
  public InvalidConfigException(String message) {
    super(message);
  }

  public InvalidConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
