package de.scrum_master.app;

public class AnotherSub extends Base{
  private final String name;

  public AnotherSub(int id, String name) {
    super(id);
    System.out.println("Constructing AnotherSub -> " + this);
    this.name = name;
  }

  public AnotherSub(String name) {
    this(1234, name);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "AnotherSub@" + this.hashCode() + " [name=" + name + ", id=" + id + "]";
  }
}
