# Системные требования

Использование `BSL Language Server` имеет ряд ограничений, ниже приведены ключевые

## Поддерживаемые версии Java

`BSL Language Server` представляет собой консольное Java приложение, соответственно, для его функционирования необходимо наличие виртуальной машины Java на компьютере.

На данный момент минимальной поддерживаемой версией является Java 11, но в рамках сборочных конвейеров происходит проверка работоспособности при использовании более свежих версий. На данный момент поддерживаются Java версий 11 и 17.

Кроме версии Java интересен и вендор JDK. В связи с изменившейся политикой лицензирования Oracle, рекомендуется использование открытых реализаций виртуальной машины `OpenJDK`: AdoptOpenJDK, Liberica JDK.

## Поддерживаемые операционные системы

`BSL Language Server` должен работать на всех системах под управлением современных десктопных и серверных операционных систем, для которых существует поддержка Java. В рамках сборочных конвейеров происходит тестирование наиболее популярных окружений:

- гарантированно работает на последних версиях OS семейства Windows 7/10, включая серверные 2012 и выше
- поддерживаются OS на ядре Linux, в частности проводится тестирование каждого изменения на Ubuntu, в промышленной эксплуатации подтверждена работоспособность на CentOS версий 6 и 7.
- поддерживается MacOS последних версий