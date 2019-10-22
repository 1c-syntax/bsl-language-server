# BSL Language Server

[![Build Status](https://travis-ci.org/1c-syntax/bsl-language-server.svg?branch=master)](https://travis-ci.org/1c-syntax/bsl-language-server)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

* [Руководство контрибьютора](contributing/index.md)
* <a href="#capabilities">Возможности</a>
* <a href="#cli">Запуск из командной строки</a>
* <a href="#analyze">Запуск в режиме анализатора</a>
* <a href="#format">Запуск в режиме форматтера</a>
* <a href="#configuration">Конфигурационный файл</a>
* <a href="reporters">Репортеры</a>
* <a href="diagnostics">Диагностики</a>

<a id="capabilities"></a>

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

<a id="cli"></a>

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

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). Для взаимодействия используются stdin и stdout.

По умолчанию тексты диагностик выдаются на русском языке. Для переключения языка сообщений от движка диагностик необходимо настроить параметр `diagnosticLanguage` в конфигурационном файле или вызвав событие `workspace/didChangeConfiguration`:

<a id="analyze"></a>

## Запуск в режиме анализатора

Для запуска в режиме анализа используется параметр `--analyze` (сокращенно `-a`). Для указания каталога расположения анализируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников.

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

Для запуска в режиме форматтера используется параметр `--format` (сокращенно `-f`). Для указания каталога расположения форматируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников.

Пример строки запуска форматирования:

```sh
java -jar bsl-language-server.jar --format --srcDir ./src/cf
```

<a id="configuration"></a>

## Конфигурационный файл

Конфигурационный файл представляет собой файл в формате JSON. Файл может содержать следующие настройки:

* `diagnosticLanguage` - `Строка` - язык сообщений от движка диагностик. Допустимые значения - `en` и `ru`. По умолчанию - `ru`.
* `showCognitiveComplexityCodeLens` - `Булево` - показывать когнитивную сложность метода над определением метода (codeLens). По умолчанию - `true`.
* `computeDiagnostics` - `Строка` - триггер для вызова процедуры рассчета диагностик. Допустимые значения - `onType` (при редактировании файла), `onSave` (при сохранении файла), `never` (никогда). По умолчанию - `onSave`.
* `traceLog` - `Строка` - путь к файлу для логирования всех входящих и исходящих запросов между BSL Language Server и Language Client из используемой IDE. Может быть абсолютным или относительным (от корня проекта). Заполнение настройки **значительно замедляет** скорость взаимодействия между сервером и клиентом. По умолчанию - значение не заполнено.
* `diagnostics` - `Объект` - коллекция настроек диагностик. Элементами коллекции являются объекты со следующей структурой:
    * ключ объекта - `Строка` - ключ диагностики, как он описан в разделе <a href="#diagnostics">Диагностики</a>.
    * значение объекта
      - `Булево` - `false` для отключения диагностики, `true` - для включения диагностики без дополнительных настроек. По умолчанию - `true`.  
      - `Объект` - Структура настроек каждой диагностики. Описание возможных параметров каждой диагностики приведено в ее описании.

Вы можете использовать следующую JSON-схему для упрощения редактирования файла:

```
https://raw.githubusercontent.com/1c-syntax/bsl-language-server/master/src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json
```

Ниже приведен пример настройки, устанавливающий:
* язык сообщений диагностик - русский;
* настройка диагностики [LineLength - Ограничение на длину строки](diagnostics/LineLength.md) - установка предела длины строки в 140 символов;
* настройка диагностики [MethodSize - Ограничение на размер метода](diagnostics/MethodSize.md) - отключение диагностики.

```json
{
  "$schema": "https://raw.githubusercontent.com/1c-syntax/bsl-language-server/master/src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json",
  "diagnosticLanguage": "ru",
  "diagnostics": {
    "LineLength": {
      "maxLineLength": 140
    },
    "MethodSize": false
  }
}
```
