# Simple AOP framework for aspects with around advice via ByteBuddy

## How to use

Build via `mvn install` in order to deploy the artifact to your local Maven repository. This is necessary because the
library is not available on Maven Central (yet).

### Weave application classes only

Add something like this to your own project if you want to weave application classes only (no classes on the boot
classpath):
 
  ```xml
  <properties>
    <bytebuddy.version>1.10.9</bytebuddy.version>
    <bytebuddy-aspect.version>1.0-SNAPSHOT</bytebuddy-aspect.version>
    <bytebuddy-aspect-agent.jar>${settings.localRepository}/de/scrum-master/bytebuddy-aspect-agent/${bytebuddy-aspect.version}/bytebuddy-aspect-agent-${bytebuddy-aspect.version}.jar</bytebuddy-aspect-agent.jar>
  </properties>

  <dependency>
    <groupId>de.scrum-master</groupId>
    <artifactId>bytebuddy-aspect</artifactId>
    <version>${bytebuddy-aspect.version}</version>
  </dependency>
  <dependency>
    <groupId>net.bytebuddy</groupId>
    <artifactId>byte-buddy</artifactId>
    version>${bytebuddy.version}</version>
  </dependency>
  <dependency>
    <groupId>net.bytebuddy</groupId>
    <artifactId>byte-buddy-agent</artifactId>
    version>${bytebuddy.version}</version>
  </dependency>

  <plugin>
    <!-- Change to 'maven-failsafe-plugin' for integration tests -->
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
      <!-- Do not reuse forked VMs because of potentially unexpected instrumentation bleed-over -->
      <reuseForks>false</reuseForks>
      <!-- Java agent must be on command line in order to weave bootstrap classes. -->
      <argLine>
        -javaagent:../../bytebuddy-aspect-agent/target/bytebuddy-aspect-agent-1.0-SNAPSHOT.jar
      </argLine>
    </configuration>
  </plugin>
  ```

You can take a look at [`NoAgentIT`](https://github.com/kriegaex/ByteBuddyAspect/blob/master/bytebuddy-aspect-test-parent/bytebuddy-aspect-test-no-agent/src/test/java/de/scrum_master/bytebuddy/aspect/NoAgentIT.java)
if you want to get an idea how to use the aspect framework with your application classes.

### Weave bootstrap JRE/JDK classes too

If you also want to weave classes on the boot classpath, i.e. JRE/JDK classes or whatever else is on your boot
classpath, then use the same properties as above but modify the dependencies and Surefire/Failsafe configuration like
this:
 
  ```xml
  <properties>
    <bytebuddy.version>1.10.9</bytebuddy.version>
    <bytebuddy-aspect.version>1.0-SNAPSHOT</bytebuddy-aspect.version>
    <bytebuddy-aspect-agent.jar>${settings.localRepository}/de/scrum-master/bytebuddy-aspect-agent/${bytebuddy-aspect.version}/bytebuddy-aspect-agent-${bytebuddy-aspect.version}.jar</bytebuddy-aspect-agent.jar>
  </properties>

  <dependency>
    <groupId>de.scrum-master</groupId>
    <artifactId>bytebuddy-aspect-agent</artifactId>
    <version>${bytebuddy-aspect.version}</version>
  </dependency>
  <dependency>
    <groupId>de.scrum-master</groupId>
    <artifactId>bytebuddy-aspect</artifactId>
    <version>${bytebuddy-aspect.version}</version>
  </dependency>
  <dependency>
    <groupId>net.bytebuddy</groupId>
    <artifactId>byte-buddy</artifactId>
    version>${bytebuddy.version}</version>
  </dependency>

  <plugin>
    <!-- Change to 'maven-failsafe-plugin' for integration tests -->
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
      <!-- Do not reuse forked VMs because of potentially unexpected instrumentation bleed-over -->
      <reuseForks>false</reuseForks>
      <!-- Java agent must be on command line in order to weave bootstrap classes. -->
      <argLine>
        -javaagent:../../bytebuddy-aspect-agent/target/bytebuddy-aspect-agent-1.0-SNAPSHOT.jar
      </argLine>
    </configuration>
  </plugin>
  ```

You can take a look at [`CommandLineAgentIT`](https://github.com/kriegaex/ByteBuddyAspect/blob/master/bytebuddy-aspect-test-parent/bytebuddy-aspect-test-use-agent/src/test/java/de/scrum_master/bytebuddy/aspect/CommandLineAgentIT.java)
if you want to get an idea how to use the aspect framework with bootstrap classes, i.e. usually JRE/JDK classes which
might even have been loaded already (or not, it does not really make a difference in this case) and usually are
unreachable for instrumentation with normal test frameworks.

## Technical background

This framework uses ByteBuddy in a way to avoid modifying class structures, e.g. by changing modifiers like `private` or
`final`, by adding methods or field etc. The reason for these limitations is that I wanted the aspects to also be
applicable to bootstrap JRE classes like `String` or more generally to already loaded classes. For retransforming
loaded classes there are limitations as mentioned above.

_(to be continued)_
