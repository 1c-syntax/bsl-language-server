# System requirements

Using ` BSL Language Server ` has some limitations, listed bellow

## Поддерживаемые версии Java

`BSL Language Server` is a console Java application and requires the presence of a Java virtual machine on the computer.

The minimum supported version is Java 11, but as part of the build pipelines, a health check is performed when using more recent versions. Java versions 11, 13 and 14 are currently supported.

JDK vendor is also interesting. Due to the changed licensing policy of Oracle, it is recommended to use open implementations of the `OpenJDK` virtual machine: AdoptOpenJDK, Liberica JDK.

## Поддерживаемые операционные системы

`BSL Language Server` should work on all systems running modern desktop and server operating systems which supported Java. The most popular environments are tested as part of the build pipelines:

- гарантированно работает на последних версиях OS семейства Windows 7/10 включая серверные 2012 и выше
- поддерживаются OS на ядре Linux, в частности проводится тестирование каждого изменения на Ubuntu, в промышленной эксплуатации подтверждена работоспособность на CentOS версий 6 и 7.
- поддерживается MacOS последних версий
