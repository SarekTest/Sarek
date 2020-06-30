package org.acme;

import java.util.Date;

public class ExtendsSub extends Sub {
  protected final Date date;

  public ExtendsSub(int id, String name, Date date) {
    super(id, name);
    this.date = date;
    System.out.println("Constructing ExtendsSub -> " + this);
  }

  public Date getDate() {
    return date;
  }

  @Override
  public String toString() {
    return "ExtendsSub@" + this.hashCode() + " [name=" + name + ", id=" + id + ", date=" + date + ']';
  }
}
