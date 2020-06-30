package org.acme;

import java.util.Objects;

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

  public int negate(int a) {
//    System.out.println("instance method 'negate' with parameters: " + a);
    return -a;
  }

  @Override
  public boolean equals(Object o) {
//    System.out.println("instance method 'equals' with parameters: " + o);
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    UnderTest underTest = (UnderTest) o;
    return Objects.equals(name, underTest.name);
  }

  @Override
  public int hashCode() {
//    System.out.println("instance method 'hashCode'");
    return Objects.hash(name);
  }

  public static String greet(String recipient) {
//    System.out.println("static method with parameter: " + recipient);
    return "Hello " + recipient;
  }
}
