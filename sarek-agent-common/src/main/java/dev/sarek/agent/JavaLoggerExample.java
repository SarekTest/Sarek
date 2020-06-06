package dev.sarek.agent;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaLoggerExample {
  private final static Logger LOGGER = Logger.getLogger(JavaLoggerExample.class.getName());

  public static void main(String[] args) {
    Logger.getLogger("");

    LOGGER.setLevel(Level.SEVERE);
    LOGGER.severe("Info Log");
    LOGGER.warning("Info Log");
    LOGGER.info("Info Log");
    LOGGER.finest("Really not important");

    LOGGER.setLevel(Level.ALL);
    LOGGER.info("----------");

    LOGGER.setLevel(Level.INFO);
    LOGGER.severe("Info Log");
    LOGGER.warning("Info Log");
    LOGGER.info("Info Log");
    LOGGER.finest("Really not important");

    LOGGER.setLevel(Level.ALL);
    LOGGER.info("----------");

    LOGGER.setLevel(Level.OFF);
    LOGGER.severe("Info Log");
    LOGGER.warning("Info Log");
    LOGGER.info("Info Log");
    LOGGER.finest("Really not important");

    LOGGER.setLevel(Level.ALL);
    LOGGER.info("----------");
  }
}
