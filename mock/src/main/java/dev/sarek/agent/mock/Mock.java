package dev.sarek.agent.mock;

import dev.sarek.agent.Agent;
import dev.sarek.agent.aspect.MethodAroundAdvice;
import dev.sarek.agent.aspect.Weaver;
import dev.sarek.agent.constructor_mock.ConstructorMockRegistry;
import dev.sarek.agent.constructor_mock.ConstructorMockTransformer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.anyOf;

public class Mock implements AutoCloseable {
  private ConstructorMockTransformer constructorMockTransformer;
  private Weaver weaver;
  private Set<Class<?>> classes;
  private Set<String> classNames;

  public Mock(Class<?>... classes) throws IOException {
    constructorMockTransformer = new ConstructorMockTransformer(classes);
    Agent.getInstrumentation().addTransformer(constructorMockTransformer, true);
    this.classes = Arrays
      .stream(classes)
      .collect(Collectors.toSet());
    this.classes
      .stream()
      .map(Class::getName)
      .forEach(ConstructorMockRegistry::activate);
    // Automatically retransforms, thus also applies constructorMockTransformer
    weaver = Weaver
      .forTypes(anyOf(classes))
      .addAdvice(MethodAroundAdvice.MOCK, any())
      .addTargets(classes)
      .build();
    // INSTRUMENTATION.retransformClasses(classes);
  }

  public Mock(String... classNames) throws IOException {
    this(
      Arrays
        .stream(classNames)
        .map((String className) -> {
          try {
            return Class.forName(className);
          }
          catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }
        })
        .toArray(Class<?>[]::new)
    );
  }

  @Override
  public void close() {
    System.out.println("Closing Mock");
    classes
      .stream()
      .map(Class::getName)
      .forEach(ConstructorMockRegistry::deactivate);
    classes = null;
    Agent.getInstrumentation().removeTransformer(constructorMockTransformer);
    constructorMockTransformer = null;
    // Automatically retransforms, thus also unapplies constructorMockTransformer -> TODO: test!
    weaver.unregisterTransformer();
    // INSTRUMENTATION.retransformClasses(classes);
    weaver = null;
    System.out.println("Mock closed");
  }
}
