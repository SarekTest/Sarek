package dev.sarek.app;

public final class FinalClass {
  private static int instanceCounter;

  public FinalClass() {
    instanceCounter++;
  }

  public static int getInstanceCounter() {
    return instanceCounter;
  }

  public static void resetInstanceCounter() {
    instanceCounter = 0;
  }

  public static String concatenate(String... strings) {
    return String.join(" ", strings);
  }

  public static double multiply(double factor1, double factor2) {
    return factor1 * factor2;
  }

  public final void doSomething() { }

  public final int add(int a, int b) {
    return a + b;
  }

  public final char firstChar(String text) {
    return text.charAt(0);
  }

  public final double percentOf(double total, double percentage) {
    return total * percentage / 100.0;
  }

  public final boolean invert(boolean truth) {
    return !truth;
  }

  public final String greet(String recipient) {
    return "Hello " + recipient;
  }
}
