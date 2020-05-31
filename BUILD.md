# How to build

You can build this project with Maven and/or by importing the Maven multi-module structure into your IDE of choice, such
as IntelliJ IDEA (use by the maintainer), Eclipse or NetBeans.

Minimal versions (also enforced by the Maven POM):
  * Maven 3.3.9
  * Java 8

In order to build from the command line, please edit file `${projectRootDir}/.mvn/jvm.config` and in order to hand over
the correct `${projectRootDir}` path to Maven as a system property, e.g. something like

```text
-DprojectRootDir="C:/Users/me/java-src/Sarek"
```

Because IntelliJ IDEA currently does not recognise this file, you also need to redundantly set the property as a
project-specific property for the Maven runner via configuration menu  
  * File | Settings | Build, Execution, Deployment | Build Tools | Maven | Runner
  * Look for the properties list at the bottom of the dialogue, click the `+` (add) icon and then set name and value to
    `projectRootDir` → `C:/Users/me/java-src/Sarek`, just like in the `jvm.config` file before.

You may be wondering why we do not use these two options in order to get a reference to the `${projectRootDir}`:
  * Build Helper Maven Plugin, goal `rootlocation`: The goal currently does not work because of an old, unfixed bug:
      * https://github.com/mojohaus/build-helper-maven-plugin/issues/48
  * Maven property `maven.multiModuleProjectDirectory`: First of all, the property is undocumented and only meant to be
    used internally for internal use, even though nowadays popular amongg developers. Moreover, IDEA has two problems
    concerning that property:
      * https://youtrack.jetbrains.com/issue/IDEA-242198
      * https://youtrack.jetbrains.com/issue/IDEA-190202 
