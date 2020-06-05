package dev.sarek.agent.constructor_mock;

import de.icongmbh.oss.maven.plugin.javassist.ClassTransformer;
import javassist.CtClass;
import javassist.build.JavassistBuildException;

import java.util.Properties;

/**
 * This class can be used from Maven in order to create constructor mocks during build time by delegating to
 * {@link ConstructorMockTransformer}
 * <p></p>
 *
 * @see <a href="https://github.com/icon-Systemhaus-GmbH/javassist-maven-plugin">Javassist Maven Plugin</a>
 * <p></p>
 * TODO: implement configurability in ConstructorMockTransformer
 */
public class ConstructorMockPluginTransformer extends ClassTransformer {
  ConstructorMockTransformer constructorMockTransformer = new ConstructorMockTransformer();

  @Override
  public void applyTransformations(CtClass ctClass) throws JavassistBuildException {
    constructorMockTransformer.applyTransformations(ctClass);
  }

  @Override
  public boolean shouldTransform(CtClass ctClass) throws JavassistBuildException {
    return constructorMockTransformer.shouldTransform(ctClass);
  }

  @Override
  public void configure(Properties properties) throws Exception {
    constructorMockTransformer.configure(properties);
  }
}
