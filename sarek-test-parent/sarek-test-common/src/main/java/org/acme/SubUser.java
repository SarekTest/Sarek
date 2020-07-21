package org.acme;

public class SubUser {
  public Object doSomething() {
    // Hard to test because Sub instance is not injectable -> global (constructor) mock to the rescue!
    Sub sub = new Sub(123, "real Sub");
    if (sub.getName() != null && sub.getName().contains("real"))
      return sub.getName();
    else
      return sub.id;
  }

  public void doSomethingAsynchronously(int pauseMillis) {
    new Thread(() -> {
      try {
        Thread.sleep(pauseMillis);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
      doSomething();
    }).start();
  }
}
