package de.scrum_master.agent.global_mock;

import de.icongmbh.oss.maven.plugin.javassist.ClassTransformer;
import javassist.CtClass;
import javassist.build.JavassistBuildException;

import java.util.Properties;

/**
 * This class can be used from Maven in order to create global mocks during build time by delegating to
 * {@link GlobalMockTransformer}
 * <p></p>
 * @see <a href="https://github.com/icon-Systemhaus-GmbH/javassist-maven-plugin">Javassist Maven Plugin</a>
 * <p></p>
 * TODO: implement configurability in GlobalMockTransformer
 */
public class GlobalMockPluginTransformer extends ClassTransformer {
  GlobalMockTransformer globalMockTransformer = new GlobalMockTransformer();

  @Override
  public void applyTransformations(CtClass ctClass) throws JavassistBuildException {
    globalMockTransformer.applyTransformations(ctClass);
  }

  @Override
  public boolean shouldTransform(CtClass ctClass) throws JavassistBuildException {
    return globalMockTransformer.shouldTransform(ctClass);
  }

  @Override
  public void configure(Properties properties) throws Exception {
    globalMockTransformer.configure(properties);
  }
}
