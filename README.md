# BSL Language Server

[![Actions Status](https://github.com/1c-syntax/bsl-language-server/workflows/Java%20CI/badge.svg)](https://github.com/1c-syntax/bsl-language-server/actions)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![GitHub Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/latest/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![GitHub All Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Benchmark](https://1c-syntax.github.io/bsl-language-server/dev/bench/benchmark.svg)](https://1c-syntax.github.io/bsl-language-server/dev/bench/index.html)
[![telegram](https://img.shields.io/badge/telegram-chat-green.svg)](https://t.me/bsl_language_server)

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

Сайт проекта - https://1c-syntax.github.io/bsl-language-server

Замеры производительности - [SSL 3.1](https://1c-syntax.github.io/bsl-language-server/dev/bench/index.html)

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

BSL language server
Usage: bsl-language-server [-h] [-c=<path>] [COMMAND [ARGS]]
  -c, --configuration=<path>
               Path to language server configuration file
  -h, --help   Show this help message and exit
Commands:
  analyze, -a, --analyze  Run analysis and get diagnostic info
  format, -f, --format    Format files in source directory
  version, -v, --version  Print version
  lsp, --lsp              LSP server mode (default)
```

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). Для взаимодействия используются stdin и stdout.

По умолчанию тексты диагностик выдаются на русском языке. Для переключения языка сообщений от движка диагностик необходимо настроить параметр `diagnosticLanguage` в конфигурационном файле или вызвав событие `workspace/didChangeConfiguration`:

<a id="analyze"></a>

## Запуск в режиме анализатора

Для запуска в режиме анализа используется параметр `--analyze` (сокращенно `-a`). 

```sh
Usage: bsl-language-server analyze [-hq] [-c=<path>] [-o=<path>] [-s=<path>]
                                   [-r=<keys>]...
Run analysis and get diagnostic info
  -c, --configuration=<path>
                           Path to language server configuration file
  -h, --help               Show this help message and exit
  -o, --outputDir=<path>   Output report directory
  -q, --silent             Silent mode
  -r, --reporter=<keys>    Reporter key (console, junit, json, tslint, generic)
  -s, --srcDir=<path>      Source directory
  -w, --workspaceDir=<path> 
                           Workspace directory
```

Для указания каталога расположения анализируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников. 
Для формирования отчета об анализе требуется указать один или "репортеров". Для указания репортера используется параметр `--reporter` (сокращенно `-r`), за которым следует ключ репортера. Допустимо указывать несколько репортеров. Список репортетов см. в разделе **Репортеры**.

Пример строки запуска анализа:

```sh
java -jar bsl-language-server.jar --analyze --srcDir ./src/cf --reporter json
```

> При анализе больших исходников рекомендуется дополнительно указывать параметр -Xmx, отвечающий за предел оперативной памяти для java процесса. Размер выделяемой памяти зависит от размера анализируемой кодовой базы.

```sh
java -Xmx4g -jar bsl-language-server.jar ...остальные параметры
```

<a id="format"></a>

## Запуск в режиме форматтера

Для запуска в режиме форматтера используется параметр `--format` (сокращенно `-f`).

```sh
Usage: bsl-language-server format [-hq] [-s=<path>]
Format files in source directory
  -h, --help            Show this help message and exit
  -q, --silent          Silent mode
  -s, --src=<path>      Source directory or file
```

Для указания каталога расположения форматируемых исходников (или файла) используется параметр `--src` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников (или файлу). 

Пример строки запуска форматирования:

```sh
java -jar bsl-language-server.jar --format --src ./src/cf
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

[![JetBrains](docs/assets/images/jetbrains-variant-4.png)](https://www.jetbrains.com?from=bsl-language-server)  

Создатель профессиональных инструментов разработки программного обеспечения, инновационных и мощных, [JetBrains](https://www.jetbrains.com) поддержал наш проект, предоставив лицензии на свои продукты, в том числе на `IntelliJ IDEA Ultimate`.

`IntelliJ IDEA Ultimate` один из лучших инструментов в своем классе.

## ToDo

После реализации построения контекста:

* Автодополнение методов текущего модуля
* Автодополнение контекстных методов (конфигурация 1С и OneScript)
* Сигнатура функций
* Подробная всплывающая подсказка по методам
* Переход к определению
* Поиск мест использования
* Предпросмотр определения процедуры
* Поиск определения по символу

Дополнительно:

* Автодополнение методов глобального контекста
