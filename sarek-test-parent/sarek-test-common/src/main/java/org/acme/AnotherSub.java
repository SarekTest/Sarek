package org.acme;

public class AnotherSub extends Base {
  protected final String name;

  public AnotherSub(int id, String name) {
    super(id);
    this.name = name;
    System.out.println("Constructing AnotherSub -> " + this);
  }

  public AnotherSub(String name) {
    this(1234, name);
    System.out.println("Constructing AnotherSub -> " + this);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "AnotherSub@" + this.hashCode() + " [name=" + name + ", id=" + id + "]";
  }
}
