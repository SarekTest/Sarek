package dev.sarek.spock;

import dev.sarek.attach.AgentAttacher;
import dev.sarek.attach.AgentAttacherException;
import org.spockframework.runtime.extension.AbstractGlobalExtension;

public class SarekSpockExtension extends AbstractGlobalExtension {
  private final static String LOG_PREFIX = "[Sarek Spock Extension] ";

  @Override
  public void start() {
    log("going to attach Sarek agent (if not attached yet)");
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

  private static void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

}
