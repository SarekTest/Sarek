package dev.sarek.app;

public class Base {
  protected final int id;

  public Base(int id) {
    this.id = id;
    System.out.println("Constructing Base -> " + this);
  }

  public int getId() {
    return id;
  }

  @Override
  public String toString() {
    return "Base@" + this.hashCode() + " [id=" + id + "]";
  }
}
