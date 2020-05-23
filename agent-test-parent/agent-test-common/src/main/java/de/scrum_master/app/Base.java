package de.scrum_master.app;

import de.scrum_master.agent.aspect.GlobalMockRegistry;

public class Base {
  // TODO: make final again after extracting information for ASM
  protected /*final*/ int id;

  public Base(int id) {
    // TODO: can ASM move this to before final field initialiser?
    if (GlobalMockRegistry.isMock(getClass()))
      return;
    this.id = id;
    System.out.println("Constructing Base -> " + this);
  }

  public int getId() {
    return id;
  }
}
