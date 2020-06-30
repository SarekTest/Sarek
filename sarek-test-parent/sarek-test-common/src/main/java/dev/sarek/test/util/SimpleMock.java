package dev.sarek.test.util;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FixedValue;

import static net.bytebuddy.matcher.ElementMatchers.isToString;

/**
 * Dynamically creates a subclass and instantiates it via a user-selected constructor, stubbing the
 * {@link #toString()} method to return a fixed value.
 *
 * @param <T> class to create a subclass of
 */
public class SimpleMock<T> {
  private final Class<T> typeParameterClass;
  private String toStringMessage = "mocked";
  private Class<?>[] constructorParameterTypes = new Class<?>[0];

  protected SimpleMock(Class<T> typeParameterClass) {
    this.typeParameterClass = typeParameterClass;
  }

  /**
   * Factory method for mock creator
   *
   * @param typeParameterClass class of type to be mocked
   * @param <T>                type to be mocked
   * @return mock creator preconfigured for {@link #toString()} with default return value "mocked"
   * and default (parameter-less) constructor
   */
  public static <T> SimpleMock<T> of(Class<T> typeParameterClass) {
    return new SimpleMock<T>(typeParameterClass);
  }

  /**
   * Overrides (stubs) {@link #toString()} return value; default is "mocked"
   *
   * @param toStringMessage {@link #toString()} return value
   * @return the same mock creator instance
   */
  public SimpleMock<T> withToStringMessage(String toStringMessage) {
    this.toStringMessage = toStringMessage;
    return this;
  }

  /**
   * Defines which parent constructor is to be called when creating a mock instance via {@link #getInstance(Object...)}
   *
   * @param constructorParameterTypes constructor parameter types
   * @return the same mock creator instance
   */
  public SimpleMock<T> withConstructor(Class<?>... constructorParameterTypes) {
    this.constructorParameterTypes = constructorParameterTypes;
    return this;
  }

  /**
   * Creates mock instance
   *
   * @param constructorParameters parameters for parent constructor defined via {@link #withConstructor(Class[])}
   * @return new instance of type to be mocked (actually an instance of a dynamically created subclass)
   * @throws ReflectiveOperationException
   */
  public T getInstance(Object... constructorParameters) throws ReflectiveOperationException {
    return new ByteBuddy()
      .subclass(typeParameterClass)
      .method(isToString())
      .intercept(FixedValue.value(toStringMessage))
      .make()
      .load(getClass().getClassLoader())
      .getLoaded()
      .getConstructor(constructorParameterTypes)
      .newInstance(constructorParameters);
  }
}
