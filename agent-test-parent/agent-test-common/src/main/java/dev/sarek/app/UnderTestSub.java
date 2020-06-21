package dev.sarek.app;

public class UnderTestSub extends UnderTest {
  private int age;

  public UnderTestSub(String name, int age) {
    super(name);
    this.age = age;
  }

  public int getAge() {
    return age;
  }
}
