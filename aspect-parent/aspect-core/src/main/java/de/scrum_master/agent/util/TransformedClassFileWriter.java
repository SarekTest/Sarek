package de.scrum_master.agent.util;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TODO: Can this be replaced by setting system property 'net.bytebuddy.dump' to a directory?
 */
public class TransformedClassFileWriter extends AgentBuilder.Listener.Adapter {
  String rootDirectory;

  public TransformedClassFileWriter(String rootDirectory) {
    this.rootDirectory = rootDirectory;
  }

  @Override
  public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
    try {
      Path path = new File(rootDirectory + "/" + typeDescription.getInternalName() + ".class").toPath();
      Files.createDirectories(path.getParent());
//      System.out.println("[Aspect Agent] onTransformation: path = " + path);
      Files.write(path, dynamicType.getBytes());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
