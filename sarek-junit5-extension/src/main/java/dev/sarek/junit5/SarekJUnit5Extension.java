package dev.sarek.junit5;

import dev.sarek.attach.AgentAttacher;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SarekJUnit5Extension implements BeforeAllCallback {
  private final static String LOG_PREFIX = "[Sarek JUnit 5 Extension] ";

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    log("going to attach Sarek agent (if not attached yet)");
    AgentAttacher.install();
  }

  private static void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

}
