[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/SarekTest/Sarek/blob/master/LICENSE)
[![Gitter](https://badges.gitter.im/SarekTest.svg)](https://gitter.im/SarekTest/community)

[//]: # "[![Maven Central](https://img.shields.io/maven-central/v/org.spockframework/spock-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:org.spockframework)"
[//]: # "[![Linux Build Status](https://img.shields.io/travis/spockframework/spock/master.svg?label=Linux%20Build)](https://travis-ci.org/spockframework/spock)"
[//]: # "[![Windows Build Status](https://img.shields.io/appveyor/ci/spockframework/spock/master.svg?label=Windows%20Build)](https://ci.appveyor.com/project/spockframework/spock/branch/master)"
[//]: # "[![CircleCI branch](https://img.shields.io/circleci/project/github/spockframework/spock/master.svg?label=CircleCi)](https://github.com/spockframework/spock)"
[//]: # "[![Jitpack](https://jitpack.io/v/org.spockframework/spock.svg)](https://jitpack.io/#org.spockframework/spock)"
[//]: # "[![Codecov](https://codecov.io/gh/spockframework/spock/branch/master/graph/badge.svg)](https://codecov.io/gh/spockframework/spock)"

# Sarek

Sarek is a Spock-friendly mock framework adding features missing in Spock. It can also be used outside of Spock, e.g.
from JUnit. Some key features are:
  * Unfinal classes while they are being loaded. This makes them mockable by conventional means from Spock or other
    mock frameworks using dynamic proxy technology (JRE, CGLIB, ByteBuddy).
  * Simple aspect framework enabling the user to modify method, constructor or type initialiser (aka "static block")
    behaviour before/after or instead of execution.
  * Mock constructors, i.e. executing them but bypassing their original code, making them free from side effects and
    returning uninitialised objects, not unlike what Objenesis creates, but using a different approach. This also works
    globally, i.e. for objects created beyond user control. This problem cannot be solved via dependency injection
    without refactoring, which usually is not an option when dealing with third party libraries.
  * Mocking constructors and stubbing methods also works for final classes and methods.
  * Given proper JVM configuration, all of these features also work for bootstrap classes (usually JRE/JDK classes),
    some even for classes which have already been loaded by retransforming them by means of Java instrumentation.
  * For super hard cases there is even an option to transform (e.g. unfinal) classes during build time and then
    prepending them to the Java bootstrap class path, which works for legacy Java 8 as well as for Java 9+. This way,
    even classes loaded very early in the JVM start-up phase become mockable, e.g. final class `String` is no longer
    final, can thus be extended, which also means it can be mocked by conventional means.

## --- TODO & disclaimer ---

***This file is outdated and needs to be completely rewritten because dependency names have changed after a refactoring.
So have class names and features. stay tuned for an update.***

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
        -javaagent:../../aspect-agent/target/aspect-agent-1.0-SNAPSHOT.jar
      </argLine>
    </configuration>
  </plugin>
  ```

You can take a look at [`NoAgentIT`](https://github.com/kriegaex/ByteBuddyAspect/blob/master/bytebuddy-agent-test-parent/bytebuddy-agent-test-no-agent/src/test/java/de/scrum_master/bytebuddy/aspect/NoAgentIT.java)
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
        -javaagent:../../aspect-agent/target/aspect-agent-1.0-SNAPSHOT.jar
      </argLine>
    </configuration>
  </plugin>
  ```

You can take a look at [`CommandLineAgentIT`](https://github.com/kriegaex/ByteBuddyAspect/blob/master/bytebuddy-agent-test-parent/bytebuddy-agent-test-use-agent/src/test/java/de/scrum_master/bytebuddy/aspect/CommandLineAgentIT.java)
if you want to get an idea how to use the aspect framework with bootstrap classes, i.e. usually JRE/JDK classes which
might even have been loaded already (or not, it does not really make a difference in this case) and usually are
unreachable for instrumentation with normal test frameworks.

## Technical background

This framework uses ByteBuddy in a way to avoid modifying class structures, e.g. by changing modifiers like `private` or
`final`, by adding methods or field etc. The reason for these limitations is that I wanted the aspects to also be
applicable to bootstrap JRE classes like `String` or more generally to already loaded classes. For retransforming
loaded classes there are limitations as mentioned above.

_(to be continued)_
