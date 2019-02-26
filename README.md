# BSL Language Server

[![Build Status](https://travis-ci.org/1c-syntax/bsl-language-server.svg?branch=master)](https://travis-ci.org/1c-syntax/bsl-language-server)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

## Возможности

* Форматирование файла
* Форматирование выбранного диапазона
* Диагностики
* Запуск движка диагностик из командной строки

## Запуск из командной строки

Запуск jar-файлов осуществляется через `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar" --help
usage: BSL language server [-a] [-d <arg>] [-h] [-r <arg>] [-s <arg>]
 -a,--analyze                    Run analysis and get diagnostic info
 -d,--diagnosticLanguage <arg>   Language of diagnostic messages. Possible
                                 values: en, ru. Default is en.
 -h,--help                       Show help.
 -r,--reporter <arg>             Reporter key
 -s,--srcDir <arg>               Source directory
```

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). Для взаимодействия используются stdin и stdout.

По умолчанию тексты диагностик выдаются на английском языке. Для переключения языка сообщений от движка диагностик используется параметр `--diagnosticLanguage` (сокращенно `-d`), за которым следует код языка:

```sh
java -jar bsl-language-server.jar --diagnosticLanguage ru
```

Для запуска в режиме анализа используется параметр `--analyze` (сокращенно `-a`). Для указания каталога расположения анализируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников.

Для формирования отчета об анализе требуется указать один или "репортеров". Для указания репортера используется параметр `--reporter` (сокращенно `-r`), за которым следует ключ репортера. Допустимо указывать несколько репортеров. 

Список и описания репортеров доступны [на сайте проекта](https://1c-syntax.github.io/bsl-language-server/).

Пример строки запуска анализа:

```sh
java -jar bsl-language-server.jar --analyze --srcDir ./src/cf --reporter json
```

> При анализе больших исходников рекомендуется дополнительно указывать параметр -Xmx, отвечающий за предел оперативной памяти для java процесса. Размер выделяемой памяти зависит от размера анализируемой кодовой базы.

```sh
java -Xmx4g -jar bsl-language-server.jar ...остальные параметры
```

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
