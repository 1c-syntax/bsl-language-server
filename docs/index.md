# BSL Language Server

[![Build Status](https://travis-ci.org/1c-syntax/bsl-language-server.svg?branch=master)](https://travis-ci.org/1c-syntax/bsl-language-server)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

[English version](en/index.md)

* <a href="#capabilities">Возможности</a>
* <a href="#cli">Запуск из командной строки</a>
* <a href="#analyze">Запуск в режиме анализатора</a>
* <a href="#configuration">Конфигурационный файл</a>
* <a href="#reporters">Репортеры</a>
* <a href="#diagnostics">Диагностики</a>

<a id="capabilities"/>

## Возможности

* Форматирование файла
* Форматирование выбранного диапазона
* Определение символов текущего файла (области, процедуры, функции, переменные, объявленные через `Перем`)
* Определение сворачиваемых областей - `#Область`, `#Если`, процедуры и функции, блоки кода
* Диагностики
* "Быстрые исправления" (quick fixes) для ряда диагностик
* Запуск движка диагностик из командной строки

<a id="cli"/>

## Запуск из командной строки

Запуск jar-файлов осуществляется через `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help

usage: BSL language server [-a] [-c <arg>] [-h] [-o <arg>] [-r <arg>] [-s <arg>]
 -a,--analyze               Run analysis and get diagnostic info
 -c,--configuration <arg>   Path to language server configuration file
 -h,--help                  Show help.
 -o,--outputDir <arg>       Output report directory
 -r,--reporter <arg>        Reporter key
 -s,--srcDir <arg>          Source directory
 -v,--version               Version
```

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). Для взаимодействия используются stdin и stdout.

По умолчанию тексты диагностик выдаются на русском языке. Для переключения языка сообщений от движка диагностик необходимо настроить параметр `diagnosticLanguage` в конфигурационном файле или вызвав событие `workspace/didChangeConfiguration`:

<a id="analyze"/>

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

<a id="configuration"/>

## Конфигурационный файл

Конфигурационный файл представляет собой файл в формате JSON. Файл может содержать следующие настройки:

* `diagnosticLanguage` - `Строка` - язык сообщений от движка диагностик. Допустимые значения - `en` и `ru`. По умолчанию - `ru`.
* `traceLog` - `Строка` - путь к файлу для логирования всех входящих и исходящих запросов между BSL Language Server и Language Client из используемой IDE. Может быть абсолютным или относительным (от корня проекта). Заполнение настройки **значительно замедляет** скорость взаимодействия между сервером и клиентом. По умолчанию - значение не заполнено.
* `diagnostics` - `Объект` - коллекция настроек диагностик. Элементами коллекции являются объекты со следующей структурой:
    * ключ объекта - `Строка` - ключ диагностики, как он описан в разделе <a href="#diagnostics">Диагностики</a>.
    * значение объекта
      - `Булево` - `false` для отключения диагностики, `true` - для включения диагностики без дополнительных настроек. По умолчанию - `true`.  
      - `Объект` - Структура настроек каждой диагностики. Описание возможных параметров каждой диагностики приведено в ее описании.

Ниже приведен пример настройки, устанавливающий:
* язык сообщений диагностик - русский;
* настройка диагностики [LineLength - Ограничение на длину строки](diagnostics/LineLength.md) - установка предела длины строки в 140 символов;
* настройка диагностики [MethodSize - Ограничение на размер метода](diagnostics/MethodSize.md) - отключение диагностики.

```json
{
  "diagnosticLanguage": "ru",
  "diagnostics": {
    "LineLength": {
      "maxLineLength": 140
    },
    "MethodSize": false
  }
}
```

<a id="reporters"/>

## Репортеры

Используются для получения результатов анализа.

### Список реализованных репортеров

* [json](reporters/json.md) - вывод результата анализа в собственном формате JSON, поддерживаемым [SonarQube 1C (BSL) Community Plugin](https://github.com/1c-syntax/sonar-bsl-plugin-community);
* [generic](reporters/generic.md) - вывод результата анализа в формате [Generic issue](https://docs.sonarqube.org/latest/analysis/generic-issue/) для SonarQube;
* [junit](reporters/junit.md);
* [tslint](reporters/tslint.md);
* [console](reporters/console.md).

<a id="diagnostics"/>

## Диагностики

Используются для проверки кода на соответствие стандартам кодирования и для поиска возможных ошибок.

Некоторые диагностики выключены по умолчанию. Для их включения используйте <a href="#configuration">конфигурационный файл</a>.

### Список реализованных диагностик

| Ключ                                                                                                | Название                                                                          | Включена по умолчанию |
|-----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|:---------------------:|
| [CanonicalSpellingKeywords](diagnostics/CanonicalSpellingKeywords.md)                               | Каноническое написание ключевых слов                                              | Да                    |
| [DeprecatedMessage](diagnostics/DeprecatedMessage.md)                                               | Ограничение на использование устаревшего метода "Сообщить"                        | Да                    |
| [EmptyCodeBlock](diagnostics/EmptyCodeBlock.md)                                                     | Пустой блок кода                                                                  | Да                    |
| [EmptyStatement](diagnostics/EmptyStatement.md)                                                     | Пустой оператор                                                                   | Да                    |
| [ExtraCommas](diagnostics/ExtraCommas.md)                                                           | Запятые без указания параметра в конце вызова метода                              | Да                    |
| [FunctionShouldHaveReturn](diagnostics/FunctionShouldHaveReturn.md)                                 | Функция должна содержать возврат                                                  | Да                    |
| [IfConditionComplexityDiagnostic](diagnostics/IfConditionComplexity.md)                             | Использование сложных выражений в условии оператора Если                          | Да                    |
| [IfElseDuplicatedCodeBlockDiagnostic](diagnostics/IfElseDuplicatedCodeBlock.md)                     | Повторяющиеся блоки кода в синтаксической конструкции Если...Тогда...ИначеЕсли... | Да                    |
| [IfElseDuplicatedConditionDiagnostic](diagnostics/IfElseDuplicatedCondition.md)                     | Повторяющиеся условия в синтаксической конструкции Если...Тогда...ИначеЕсли...    | Да                    |
| [IfElseIfEndsWithElse](diagnostics/IfElseIfEndsWithElse.md)                                         | Использование синтаксической конструкции Если...Тогда...ИначеЕсли...              | Да                    |
| [LineLength](diagnostics/LineLength.md)                                                             | Ограничение на длину строки                                                       | Да                    |
| [MagicNumber](diagnostics/MagicNumber.md)                                                           | Использование магического числа                                                   | Да                    |
| [MethodSize](diagnostics/MethodSize.md)                                                             | Ограничение на размер метода                                                      | Да                    |
| [NestedConstructorsInStructureDeclaration](diagnostics/NestedConstructorsInStructureDeclaration.md) | Ограничение на использование конструкторов с параметрами при объявлении структуры | Да                    |
| [NestedStatements](diagnostics/NestedStatements.md)                                                 | Управляющие конструкции не должны быть вложены слишком глубоко                    | Да                    |
| [NestedTernaryOperator](diagnostics/NestedTernaryOperator.md)                                       | Вложенный тернарный оператор                                                      | Да                    |
| [NumberOfOptionalParams](diagnostics/NumberOfOptionalParams.md)                                     | Ограничение на количество не обязательных параметров метода                       | Да                    |
| [NumberOfParams](diagnostics/NumberOfParams.md)                                                     | Ограничение на количество параметров метода                                       | Да                    |
| [NumberOfValuesInStructureConstructor](diagnostics/NumberOfValuesInStructureConstructor.md)         | Ограничение на количество значений свойств, передаваемых в конструктор структуры  | Да                    |
| [OneStatementPerLine](diagnostics/OneStatementPerLine.md)                                           | Одно выражение в одной строке                                                     | Да                    |
| [ParseError](diagnostics/ParseError.md)                                                             | Ошибка разбора исходного кода                                                     | Да                    |
| [OrderOfParams](diagnostics/OrderOfParams.md)                                                       | Порядок параметров метода                                                         | Да                    |
| [ProcedureReturnsValue](diagnostics/ProcedureReturnsValue.md)                                       | Процедура не может возвращать значение                                            | Да                    |
| [SemicolonPresence](diagnostics/SemicolonPresence.md)                                               | Выражение должно заканчиваться ";"                                                | Да                    |
| [SelfAssign](diagnostics/SelfAssign.md)                                                             | Присвоение переменной самой себе                                                  | Да                    |
| [TernaryOperatorUsage](diagnostics/TernaryOperatorUsage.md)                                         | Использование тернарного оператора                                                | Нет                   |
| [TryNumber](diagnostics/TryNumber.md)                                                               | Приведение к числу в попытке                                                      | Да                    |
| [UnknownPreprocessorSymbol](diagnostics/UnknownPreprocessorSymbol.md)                               | Неизвестный символ препроцессора                                                  | Да                    |
| [UseLessForEach](diagnostics/UseLessForEach.md)                                                     | Бесполезный перебор коллекции                                                     | Да                    |
| [UsingCancelParameter](diagnostics/UsingCancelParameter.md)                                         | Работа с параметром «Отказ»                                                       | Да                    |
| [UsingFindElementByString](diagnostics/UsingFindElementByString.md)                                 | Использование методов «НайтиПоНаименованию» и «НайтиПоКоду»                       | Да                    |
| [UsingGoto](diagnostics/UsingGoto.md)                                                               | Использование оператора "Перейти"                                                 | Да                    |
| [UsingServiceTag](diagnostics/UsingServiceTag.md)                                                   | Использование служебных тегов                                                     | Да                    |
| [YoLetterUsageDiagnostic](diagnostics/YoLetterUsage.md)                                             | Использование буквы "ё" в текстах модулей                                         | Да                    |
