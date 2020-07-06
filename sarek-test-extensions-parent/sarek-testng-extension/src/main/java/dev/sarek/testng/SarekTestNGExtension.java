package dev.sarek.testng;

import dev.sarek.attach.AgentAttacher;
import dev.sarek.attach.AgentAttacherException;
import org.testng.IExecutionListener;

public class SarekTestNGExtension implements IExecutionListener {
  private final static String LOG_PREFIX = "[Sarek TestNG Extension] ";

  @Override
  public void onExecutionStart() {
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
