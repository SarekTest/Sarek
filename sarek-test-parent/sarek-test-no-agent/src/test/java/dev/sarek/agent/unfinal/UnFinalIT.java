package dev.sarek.agent.unfinal;

import dev.sarek.junit4.SarekRunner;
import dev.sarek.test.util.SeparateJVM;
import dev.sarek.test.util.SimpleMock;
import org.acme.FinalClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@Category(SeparateJVM.class)
@RunWith(SarekRunner.class)
public class UnFinalIT {
  @Test
  public void checkDefinaliser() throws NoSuchMethodException {
    // Final application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getModifiers()));
    // Final method of application class has been definalised
    assertFalse(Modifier.isFinal(FinalClass.class.getDeclaredMethod("doSomething").getModifiers()));
    // Final bootstrap classes have been definalised
    assertFalse(Modifier.isFinal(UUID.class.getModifiers()));
    // These only work with woven classes prepended to the bootstrap class path
    /*
    assertFalse(Modifier.isFinal(String.class.getModifiers()));
    assertFalse(Modifier.isFinal(StringJoiner.class.getModifiers()));
    assertFalse(Modifier.isFinal(URL.class.getModifiers()));
    */
    // Final method of non-final bootstrap class has been definalised
    assertFalse(Modifier.isFinal(ObjectOutputStream.class.getDeclaredMethod("writeObject", Object.class).getModifiers()));
  }

  @Test
  public void checkMockability() throws ReflectiveOperationException {
    // Final application class can be mocked
    assertEquals(
      "mocked",
      SimpleMock
        .of(FinalClass.class)
        .getInstance()
        .toString()
    );

    // Final bootstrap classes can be mocked during class-loading
    assertEquals(
      "mocked UUID",
      SimpleMock
        .of(UUID.class)
        .withToStringMessage("mocked UUID")
        .withConstructor(long.class, long.class)
        .getInstance(123, 456)
        .toString()
    );
    //System.out.println(new UUID(0x01234567CAFEABBAL, 0xFACE009876543210L));
    // These only work with woven classes prepended to the bootstrap class path
    /*
    assertEquals(
      "mocked String",
      SimpleMock
        .of(String.class)
        .withToStringMessage("mocked String")
        .withConstructor(String.class)
        .getInstance("dummy")
        .toString()
    );
    assertEquals(
      "mocked StringJoiner",
      SimpleMock
        .of(StringJoiner.class)
        .withToStringMessage("mocked StringJoiner")
        .withConstructor(CharSequence.class)
        .getInstance("dummy")
        .toString()
    );
    assertEquals(
      "mocked URL",
      SimpleMock
        .of(URL.class)
        .withToStringMessage("mocked URL")
        .withConstructor(String.class)
        .getInstance("http://localhost")
        .toString()
    );
    assertEquals(
      "mocked ObjectOutputStream",
      SimpleMock
        .of(ObjectOutputStream.class)
        .withToStringMessage("mocked ObjectOutputStream")
        .withConstructor(OutputStream.class)
        .getInstance(new ByteArrayOutputStream())
        .toString()
    );
    */
    assertEquals(
      "mocked ObjectOutputStream",
      SimpleMock
        .of(ObjectOutputStream.class)
        .withToStringMessage("mocked ObjectOutputStream")
        .withConstructor(OutputStream.class)
        .getInstance(new ByteArrayOutputStream())
        .toString()
    );
  }
}
