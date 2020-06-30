package org.acme;

public class Sub extends Base {
  protected final String name;

  public Sub(int id, String name) {
    super(new Expensive(id).getId());
    this.name = name;
    System.out.println("Constructing Sub -> " + this);
  }

  public Sub(String name) {
    this(1234, name);
    System.out.println("Constructing Sub -> " + this);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "Sub@" + this.hashCode() + " [name=" + name + ", id=" + id + "]";
  }

  static class Expensive {
    private int id;

    public Expensive(int id) {
      System.out.println("Expensive constructor");
      this.id = id;
    }

    public int getId() {
      System.out.println("Expensive method");
      return id;
    }
  }
}
