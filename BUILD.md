# How to build

You can build this project with Maven and/or by importing the Maven multi-module structure into your IDE of choice, such
as IntelliJ IDEA (use by the maintainer), Eclipse or NetBeans.

Minimal versions (also enforced by the Maven POM):
  * Maven 3.3.9
  * Java 8 (tested up to Java 14)

In order to build from the command line with Maven, there is nothing else you need to do. Build Helper Maven plugin
takes care of determining the project root directory automatically.

Unfortunately, IntelliJ IDEA does not recognise the property set by Build Helper. So before building from IDEA, please
edit file `${projectRootDir}/.mvn/jvm.config` in order to hand over the correct `${projectRootDir}` path to Maven as a
system property, e.g. something like:

```text
-DprojectRootDir=C:/Users/me/java-src/Sarek
```

**Caveat:** Please avoid spaces in the path name, because enclosing the path or the whole key/value pair with
single or double quotes causes problems. There are three scenarios for me on Windows:
  1. Maven build from cmd.exe
  2. Maven build from Git Bash
  3. Build + test runs from IntelliJ IDEA

Quoting problems I experienced:
  * While `"-DprojectRootDir=C:/a/b/Sarek"` works fine for #1 and #3, there were problems in #2 because Java thinks the
    parameter is a main class name.
  * The intuitive `-DprojectRootDir="C:/a/b/Sarek"` is even worse because it only works for #1 but not #2 and #3 for the
    same reason as above.

`-DprojectRootDir=C:/a/b/Sarek` works for all scenarios, but now there is no way to quote paths with spaces anymore. So
be careful, please.
 
You may be wondering why the Sarek build does not just the Maven property `maven.multiModuleProjectDirectory`: First of
all, the property is undocumented and only meant to be used internally for internal use, even though nowadays popular
among developers and even used by IDEA itself, but not consistently. IDEA has two problems concerning that property:
  * https://youtrack.jetbrains.com/issue/IDEA-242198
  * https://youtrack.jetbrains.com/issue/IDEA-190202 
