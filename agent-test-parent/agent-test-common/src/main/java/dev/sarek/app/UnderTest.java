package dev.sarek.app;

public class UnderTest {
  public static int staticBlockCounter;
  public static int typeInitialiserAdviceCounter;

  private String name;

  static {
    System.out.println("Static block #" + ++staticBlockCounter);
  }

  static {
    System.out.println("Static block #" + ++staticBlockCounter);
  }

  static {
    System.out.println("Static block #" + ++staticBlockCounter);
  }

  public UnderTest() {
    this("default");
//    System.out.println("default constructor");
  }

  public UnderTest(String name) {
//    System.out.println("constructor with parameter: " + name);
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public int add(int a, int b) {
//    System.out.println("instance method 'add' with parameters: " + a + ", " + b);
    return a + b;
  }

  public int multiply(int a, int b) {
//    System.out.println("instance method 'multiply' with parameters: " + a + ", " + b);
    return a * b;
  }

  public static String greet(String recipient) {
//    System.out.println("static method with parameter: " + recipient);
    return "Hello " + recipient;
  }
}
