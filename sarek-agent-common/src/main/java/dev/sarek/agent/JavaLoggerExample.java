package dev.sarek.agent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.logging.*;

public class JavaLoggerExample {
  private final static Logger LOGGER = Logger.getLogger(JavaLoggerExample.class.getName());
  private final static Logger ROOT_LOGGER = Logger.getLogger("");

  public static void main(String[] args) {
    ROOT_LOGGER.log(Level.SEVERE, "oops", new IllegalArgumentException("no no"));

    ConsoleHandler consoleHandler = new ConsoleHandler();
    consoleHandler.setFormatter(getFormatter());
    LOGGER.addHandler(consoleHandler);
    LOGGER.setUseParentHandlers(false);

    LOGGER.setLevel(Level.SEVERE);
    LOGGER.severe("Info Log");
    LOGGER.log(Level.SEVERE, "oops", new IllegalArgumentException("no no"));
    LOGGER.warning("Info Log");
    LOGGER.info("Info Log");
    LOGGER.finest("Really not important");

    LOGGER.setLevel(Level.ALL);
    LOGGER.info("----------------------------------------");

    LOGGER.setLevel(Level.INFO);
    LOGGER.severe("Info Log");
    LOGGER.log(Level.SEVERE, "oops", new IllegalArgumentException("no no"));
    LOGGER.warning("Info Log");
    LOGGER.info("Info Log");
    LOGGER.finest("Really not important");

    LOGGER.setLevel(Level.ALL);
    LOGGER.info("----------------------------------------");

    LOGGER.setLevel(Level.OFF);
    LOGGER.severe("Info Log");
    LOGGER.log(Level.SEVERE, "oops", new IllegalArgumentException("no no"));
    LOGGER.warning("Info Log");
    LOGGER.info("Info Log");
    LOGGER.finest("Really not important");

    LOGGER.setLevel(Level.ALL);
    LOGGER.info("----------------------------------------");
  }

  private static Formatter getFormatter() {
    return new Formatter() {
      final MessageFormat messageFormat = new MessageFormat(
        "{0,date} {0,time} {1} {2} {4}\n"
      );

      @Override
      public String format(LogRecord record) {
        String formattedMessage = messageFormat.format(new Object[] {
          record.getMillis(),
          record.getLoggerName(),
          String.format("%-7s", record.getLevel()),
          record.getThreadID(),
          formatMessage(record),
          null
        });
        Throwable throwable = record.getThrown();
        if (throwable != null) {
          StringWriter stringWriter = new StringWriter();
          try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
            formattedMessage += stringWriter.toString();
          }
        }
        return formattedMessage;
      }
    };
  }
}
