# System requirements

Using ` BSL Language Server ` has some limitations, listed bellow

## Supported Java Versions

`BSL Language Server` is a console Java application and requires the presence of a Java virtual machine on the computer.

The minimum supported version is Java 11, but as part of the build pipelines, a health check is performed when using more recent versions. Java versions 11 and 15 are currently supported.

JDK vendor is also interesting. Due to the changed licensing policy of Oracle, it is recommended to use open implementations of the `OpenJDK` virtual machine: AdoptOpenJDK, Liberica JDK.

## Supported OS

`BSL Language Server` should work on all systems running modern desktop and server operating systems which supported Java. The most popular environments are tested as part of the build pipelines:

- Windows 7/10, Windows server 2012 and higher supported
- latest Linux OS supported
- latest macos supported
