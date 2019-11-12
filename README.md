# BSL Language Server

[![Actions Status](https://github.com/1c-syntax/bsl-language-server/workflows/Java%20CI/badge.svg)](https://github.com/1c-syntax/bsl-language-server/actions)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![telegram](https://img.shields.io/badge/telegram-chat-green.svg)](https://t.me/bsl_language_server)

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

Сайт проекта - https://1c-syntax.github.io/bsl-language-server

[English version](docs/en/index.md)

## Возможности

* Форматирование файла
* Форматирование выбранного диапазона
* Определение символов текущего файла (области, процедуры, функции, переменные, объявленные через `Перем`)
* Определение сворачиваемых областей - `#Область`, `#Если`, процедуры и функции, блоки кода
* Показ когнитивной сложности метода
* Диагностики
* "Быстрые исправления" (quick fixes) для ряда диагностик
* Запуск движка диагностик из командной строки
* Запуск форматирования файлов в каталоге из командной строки

## Запуск из командной строки

Запуск jar-файлов осуществляется через `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help

usage: BSL language server [-a] [-c <arg>] [-f] [-h] [-o <arg>] [-r <arg>] [-s <arg>]
 -a,--analyze               Run analysis and get diagnostic info
 -c,--configuration <arg>   Path to language server configuration file
 -f,--format                Format files in source directory
 -h,--help                  Show help.
 -o,--outputDir <arg>       Output report directory
 -r,--reporter <arg>        Reporter key
 -s,--srcDir <arg>          Source directory
 -v,--version               Version
```

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP](https://microsoft.github.io/language-server-protocol/). Для взаимодействия используются stdin и stdout.

По умолчанию тексты диагностик выдаются на русском языке. Для переключения языка сообщений от движка диагностик необходимо настроить параметр `diagnosticLanguage` в конфигурационном файле или вызвав событие `workspace/didChangeConfiguration`:

## Запуск в режиме анализатора

Для запуска в режиме анализа используется параметр `--analyze` (сокращенно `-a`). Для указания каталога расположения анализируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников.

Для формирования отчета об анализе требуется указать один или "репортеров". Для указания репортера используется параметр `--reporter` (сокращенно `-r`), за которым следует ключ репортера. Допустимо указывать несколько репортеров. 

Список и описания репортеров, диагностик, конфигурационного файла доступны [на сайте проекта](https://1c-syntax.github.io/bsl-language-server/).

Пример строки запуска анализа:

```sh
java -jar bsl-language-server.jar --analyze --srcDir ./src/cf --reporter json
```

> При анализе больших исходников рекомендуется дополнительно указывать параметр -Xmx, отвечающий за предел оперативной памяти для java процесса. Размер выделяемой памяти зависит от размера анализируемой кодовой базы.

```sh
java -Xmx4g -jar bsl-language-server.jar ...остальные параметры
```

## Запуск в режиме форматтера

Для запуска в режиме форматтера используется параметр `--format` (сокращенно `-f`). Для указания каталога расположения форматируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников.

Пример строки запуска анализа:

```sh
java -jar bsl-language-server.jar --format --srcDir ./src/cf
```

## Благодарности

Огромное спасибо всем [контрибьюторам](https://github.com/1c-syntax/bsl-language-server/graphs/contributors) проекта, всем участвовавшим в обсуждениях, помогавшим с тестированием.

Вы потрясающие!  

Спасибо компаниям, поддерживающим проекты с открытым исходным кодом, а особенно тем, кто поддержали нас: 

---

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)  

Создатель инновационных и интеллектуальных инструментов для профилирования приложений `Java` и `.NET` [YourKit, LLC](https://www.yourkit.com) любезно предоставил нам лицензии на продукт `YourKit Java Profiler`.

С помощью `YourKit Java Profiler` мы мониторим и улучшаем производительность проекта.

---

[![JetBrains](https://upload.wikimedia.org/wikipedia/commons/1/1a/JetBrains_Logo_2016.svg)](https://www.jetbrains.com)  

Создатель профессиональных инструментов разработки программного обеспечения, инновационных и мощных, [JetBrains](https://www.jetbrains.com) поддержал наш проект, предоставив лицензии на свои продукты, в том числе на `IntelliJ IDEA Ultimate`.

`IntelliJ IDEA Ultimate` один из лучших инструментов в своем классе.

## ToDo

После реализации построения контекста:

* Автодополнение методов текущего модуля
* Автодополнение контекстных методов (конфигурация 1С и OneScript)
* Сигнатура функций
* Подброная всплывающая подсказка по методам
* Переход к определению
* Поиск мест использования
* Предпросмотр определения процедуры
* Поиск определения по символу

Дополнительно:

* Автодополнение методов глобального контекста
