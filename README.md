# Simple AOP framework for aspects with around advice via ByteBuddy

How to use:

* Build via `mvn install` in order to deploy the artifact to your local Maven repository. This is necessary because the
  library is not available on Maven Central (yet).

* Add something like this to your own project:
 
  ```xml
  <dependency>
    <groupId>de.scrum-master</groupId>
    <artifactId>bytebuddy-aspect</artifactId>
    <version>1.0-SNAPSHOT</version>
  </dependency>
  ```

* Either build your own Java Agent utilisung this library or use `ByteBuddyAgent` in order to bootstrap
  bytecode instrumentation.
    - In the former case your `premain` and/or `agentmain` methods will get `Instrumentation` instances injected.
    - In the latter case `ByteBuddyAgent.install()` will return you the `Instrumentation` instance you need. You would
      then also need this on your classpath:
      ```xml
      <dependency>
        <groupId>net.bytebuddy</groupId>
        <artifactId>byte-buddy-agent</artifactId>
        version>1.10.9</version>
      </dependency>
      ```

* This framework uses ByteBuddy in a way to avoid modifying class structures, e.g. by changing modifiers like `private`
  or `final`, by adding methods or field etc. The reason for these limitations is that I wanted the aspects to also be
  applicable to bootstrap JRE classes like `String` or more generally to already loaded classes. For retransforming
  loaded classes there are limitations as mentioned above.

* If you are targeting classes loaded by the JRE bootstrap classloader, you need to put this library and also ByteBuddy
  on the boot classpath via `-Xbootclasspath/a:${bytebuddy-aspect.jar};${bytebuddy.jar}` where the two variables have to 
  be defined in your Maven POM or otherwise expanded directly on your Java command line. The Maven property definitions
  could look like this: 
  ```xml
  <bytebuddy.version>1.10.9</bytebuddy.version>
  <bytebuddy.jar>${settings.localRepository}/net/bytebuddy/byte-buddy/${bytebuddy.version}/byte-buddy-${bytebuddy.version}.jar</bytebuddy.jar>
  <bytebuddy-aspect.jar>${settings.localRepository}/de/scrum-master/bytebuddy-aspect/1.0-SNAPSHOT/bytebuddy-aspect-1.0-SNAPSHOT.jar</bytebuddy-aspect.jar>
  ```
  If you do not wish to intercept bootstrap classes, you can skip this step.
