package de.scrum_master.bytebuddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.ModifierAdjustment;
import net.bytebuddy.description.modifier.MethodManifestation;
import net.bytebuddy.dynamic.ClassFileLocator;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class RemoveFinalTransformer {
  public static void install(Instrumentation instrumentation, File... jarFiles) {
    getDefaultAgentWithIgnoredTypes()
      // This setting still allows some simpler modifications such as modifier changes, see
      // https://github.com/raphw/byte-buddy/issues/859#issuecomment-626218240.
      .disableClassFormatChanges()
      // In agent mode, avoid IllegalStateException when trying to resolve ByteBuddy annotations
      .with(getLocationStrategyWithJars(jarFiles))
      .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
      .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
      .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError())
      .type(any())
      .transform(getRemoveFinalTransformation())
      .installOn(instrumentation);
  }

  private static AgentBuilder.Ignored getDefaultAgentWithIgnoredTypes() {
    return new AgentBuilder.Default()
      // Include bootstrap classes that otherwise would be excluded by default
      .ignore(none())
      // Exclude ByteBuddy and this transformer library
      .ignore(nameStartsWith("net.bytebuddy."))
      .ignore(nameStartsWith("de.scrum_master.bytebuddy."))
      // Exclude JUnit, Hamcrest, IntelliJ IDEA
      .ignore(nameStartsWith("org.junit."))
      .ignore(nameStartsWith("junit."))
      .ignore(nameStartsWith("org.hamcrest."))
      .ignore(nameStartsWith("com.intellij."));
  }

  private static AgentBuilder.LocationStrategy getLocationStrategyWithJars(File[] jarFiles) {
    List<AgentBuilder.LocationStrategy> locationStrategies = new ArrayList<>();

    // Add the default strategy first
    AgentBuilder.LocationStrategy defaultLocationStrategy = AgentBuilder.LocationStrategy.ForClassLoader.STRONG;
    locationStrategies.add(defaultLocationStrategy);

    // Add JAR files, usually provided by ByteBuddyAspectAgent and only needed
    // if they have been injected into boot class path
    for (File jarFile : jarFiles) {
//      System.out.println("[RFT] Adding class file locator for " + jarFile);
      try {
        ClassFileLocator locator = ClassFileLocator.ForJarFile.of(jarFile);
        locationStrategies.add((classLoader, module) -> locator);
      } catch (IOException e) {
        System.err.println("[RFT] Error creating class file locator for JAR file: " + jarFile);
        e.printStackTrace();
      }
    }
    return locationStrategies.size() < 2
      ? defaultLocationStrategy
      : new AgentBuilder.LocationStrategy.Compound(locationStrategies);
  }

  private static AgentBuilder.Transformer getRemoveFinalTransformation() {
    return (builder, typeDescription, classLoader, module) ->
      builder
        // Remove 'final' modifier from classes
        .modifiers(typeDescription.getModifiers() & ~Modifier.FINAL)
        // Exclude non-final methods
        .ignoreAlso(not(isFinal()))
        // Remove 'final' modifier from methods
        .visit(new ModifierAdjustment()
          .withMethodModifiers(isFinal().and(isNative()), MethodManifestation.NATIVE)
          .withMethodModifiers(isFinal().and(isBridge()), MethodManifestation.BRIDGE)
          .withMethodModifiers(isFinal().and(not(isNative().or(isBridge()))), MethodManifestation.PLAIN)
        );
  }

}
