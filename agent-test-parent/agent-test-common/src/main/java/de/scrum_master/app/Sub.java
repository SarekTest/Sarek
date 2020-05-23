package de.scrum_master.app;

import de.scrum_master.agent.aspect.GlobalMockRegistry;

public class Sub extends Base{
  // TODO: make final again after extracting information for ASM
  private /*final*/ String name;

  public Sub(int id, String name) {
    super(id);
    // TODO: can ASM move this to before 'super(..)' or before final field initialiser?
    if (GlobalMockRegistry.isMock(getClass()))
      return;
    this.name = name;
    System.out.println("Constructing Sub -> " + this);
  }

  public Sub(String name) {
    this(1234, name);
    // TODO: can ASM move this to before 'this(..)'?
    if (GlobalMockRegistry.isMock(getClass()))
      return;
    System.out.println("Constructing Sub -> " + this);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "Sub@" + this.hashCode() + " [name=" + name + ", id=" + id + "]";
  }
}
