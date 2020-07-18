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

***This file is incomplete and needs to be updated in order to explain basic Sarek use cases.***

