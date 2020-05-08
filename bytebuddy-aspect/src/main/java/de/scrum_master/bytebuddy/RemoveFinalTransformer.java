package de.scrum_master.bytebuddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.ModifierAdjustment;
import net.bytebuddy.description.modifier.MethodManifestation;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class RemoveFinalTransformer {
  public static void install(Instrumentation instrumentation) {
    new AgentBuilder.Default()
      // TODO: Why is this needed? The class format (modifiers) *is* changed. Must be some side effect.
      .disableClassFormatChanges()

      // Include bootstrap classes that otherwise would be excluded by default
      //
      // Caveat: do not use something like
      //   .ignore(nameStartsWith("de.scrum_master.bytebuddy."))
      // because it does not work as expected and leads to
      //   IllegalStateException: Cannot resolve type description
      .ignore(none())

      // It works without this -> reactivate on demand
      //.ignore(AgentBuilder.RawMatcher.ForLoadState.LOADED)

      // It works without RedefinitionStrategy.* -> reactivate on demand
      //.with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
      //.with(AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError())

      .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
      .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
      .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError())

      .type(any()
          // Exclude ByteBuddy and this transformer library
          .and(not(nameStartsWith("net.bytebuddy.")))
          .and(not(nameStartsWith("de.scrum_master.bytebuddy.")))
          // Exclude dynamically created JRE classes without class files
          .and(not(nameMatches("com\\.sun\\.proxy\\.\\$Proxy.+")))
          .and(not(nameMatches("java\\.lang\\.invoke\\.BoundMethodHandle\\$Species_L.+")))
          // Exclude JUnit, Hamcrest, IntelliJ IDEA
          .and(not(nameStartsWith("org.junit.")))
          .and(not(nameStartsWith("junit.")))
          .and(not(nameStartsWith("org.hamcrest.")))
          .and(not(nameStartsWith("com.intellij.")))
      )

      .transform((builder, typeDescription, classLoader, module) ->
          builder
            // Remove 'final' modifier from classes
            .modifiers(typeDescription.getModifiers() & ~Modifier.FINAL)
            // Exclude non-final methods
            .ignoreAlso(not(isFinal()))
            // Remove 'final' modifier from methods
            .visit(new ModifierAdjustment()
              .withMethodModifiers(isFinal().and(isNative()), MethodManifestation.NATIVE)
              .withMethodModifiers(isFinal().and(isBridge()), MethodManifestation.BRIDGE)
              .withMethodModifiers(isFinal().and(not(isNative().or(isBridge()))), MethodManifestation.PLAIN))
      )

      .installOn(instrumentation);
  }
}
