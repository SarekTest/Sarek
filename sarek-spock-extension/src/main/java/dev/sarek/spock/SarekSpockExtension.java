package dev.sarek.spock;

import dev.sarek.attach.AgentAttacher;
import dev.sarek.attach.AgentAttacherException;
import org.spockframework.runtime.extension.AbstractGlobalExtension;

public class SarekSpockExtension extends AbstractGlobalExtension {
  @Override
  public void start() {
    try {
      AgentAttacher.install();
    }
    catch (AgentAttacherException e) {
      throw new RuntimeException(e);
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot attach Sarek agent", e);
    }
  }
}
