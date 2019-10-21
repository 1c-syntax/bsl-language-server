# BSL Language Server

[![Build Status](https://travis-ci.org/1c-syntax/bsl-language-server.svg?branch=master)](https://travis-ci.org/1c-syntax/bsl-language-server)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

[English version](en/index.md)

* [Руководство контрибьютора](contributing/index.md)
* <a href="#capabilities">Возможности</a>
* <a href="#cli">Запуск из командной строки</a>
* <a href="#analyze">Запуск в режиме анализатора</a>
* <a href="#format">Запуск в режиме форматтера</a>
* <a href="#configuration">Конфигурационный файл</a>
* <a href="#reporters">Репортеры</a>
* <a href="#diagnostics">Диагностики</a>

<a id="capabilities"/>

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

<a id="cli"/>

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

<a id="format"/>

## Запуск в режиме форматтера

Для запуска в режиме форматтера используется параметр `--format` (сокращенно `-f`). Для указания каталога расположения форматируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников.

Пример строки запуска форматирования:

```sh
java -jar bsl-language-server.jar --format --srcDir ./src/cf
```

<a id="configuration"/>

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

Для экранирования отдельных участков кода или файлов от срабатывания диагностик можно воспользоваться специальными комментариями вида `// BSLLS:КлючДиагностики-выкл`. Более подробно данная функциональность описана в [Экранирование участков кода](features/DiagnosticIgnorance.md).

### Список реализованных диагностик

| Ключ | Название | Включена по умолчанию | Тэги |
| --- | --- | :-: | --- |
| [BeginTransactionBeforeTryCatch](diagnostics/BeginTransactionBeforeTryCatch.md) | Нарушение правил работы с транзакциями для метода 'НачатьТранзакцию' | Да | `standard` |
| [CanonicalSpellingKeywords](diagnostics/CanonicalSpellingKeywords.md) | Каноническое написание ключевых слов | Да | `standard` |
| [CognitiveComplexity](diagnostics/CognitiveComplexity.md) | Когнитивная сложность | Да | `brainoverload` |
| [CommentedCode](diagnostics/CommentedCode.md) | Закомментированный фрагмент кода | Да | `standard`<br/>`badpractice` |
| [CommitTransactionOutsideTryCatch](diagnostics/CommitTransactionOutsideTryCatch.md) | Нарушение правил работы с транзакциями для метода 'ЗафиксироватьТранзакцию' | Да | `standard` |
| [DeletingCollectionItem](diagnostics/DeletingCollectionItem.md) | Удаление элемента при обходе коллекции посредством оператора "Для каждого ... Из ... Цикл" | Да | `standard`<br/>`error` |
| [DeprecatedMessage](diagnostics/DeprecatedMessage.md) | Ограничение на использование устаревшего метода "Сообщить" | Да | `standard`<br/>`deprecated` |
| [EmptyCodeBlock](diagnostics/EmptyCodeBlock.md) | Пустой блок кода | Да | `badpractice`<br/>`suspicious` |
| [EmptyStatement](diagnostics/EmptyStatement.md) | Пустой оператор | Да | `badpractice` |
| [ExtraCommas](diagnostics/ExtraCommas.md) | Лишние запятые при вызове метода | Да | `standard`<br/>`badpractice` |
| [FunctionShouldHaveReturn](diagnostics/FunctionShouldHaveReturn.md) | Функция должна содержать возврат | Да | `suspicious`<br/>`unpredictable` |
| [IdenticalExpressions](diagnostics/IdenticalExpressions.md) | Одинаковые выражения слева и справа от "foo" оператора | Да | `suspicious` |
| [IfConditionComplexity](diagnostics/IfConditionComplexity.md) | Слишком сложное условие оператора Если | Да | `brainoverload` |
| [IfElseDuplicatedCodeBlock](diagnostics/IfElseDuplicatedCodeBlock.md) | Повторяющиеся блоки кода в синтаксической конструкции Если…Тогда…ИначеЕсли… | Да | `suspicious` |
| [IfElseDuplicatedCondition](diagnostics/IfElseDuplicatedCondition.md) | Повторяющиеся условия в синтаксической конструкции Если…Тогда…ИначеЕсли… | Да | `suspicious` |
| [IfElseIfEndsWithElse](diagnostics/IfElseIfEndsWithElse.md) | Использование синтаксической конструкции Если...Тогда...ИначеЕсли... | Да | `badpractice` |
| [LineLength](diagnostics/LineLength.md) | Ограничение на длину строки | Да | `standard`<br/>`badpractice` |
| [MagicNumber](diagnostics/MagicNumber.md) | Использование магического числа | Да | `badpractice` |
| [MethodSize](diagnostics/MethodSize.md) | Ограничение на размер метода | Да | `badpractice` |
| [MissingCodeTryCatchEx](diagnostics/MissingCodeTryCatchEx.md) | Конструкция "Попытка...Исключение...КонецПопытки" не содержит кода в исключении | Да | `standard`<br/>`badpractice` |
| [MissingSpace](diagnostics/MissingSpace.md) | Пропущен пробел | Да | `badpractice` |
| [NestedConstructorsInStructureDeclaration](diagnostics/NestedConstructorsInStructureDeclaration.md) | Использование конструкторов с параметрами при объявлении структуры | Да | `badpractice`<br/>`brainoverload` |
| [NestedStatements](diagnostics/NestedStatements.md) | Управляющие конструкции не должны быть вложены слишком глубоко | Да | `badpractice`<br/>`brainoverload` |
| [NestedTernaryOperator](diagnostics/NestedTernaryOperator.md) | Вложенный тернарный оператор | Да | `brainoverload` |
| [NumberOfOptionalParams](diagnostics/NumberOfOptionalParams.md) | Ограничение на количество не обязательных параметров метода | Да | `standard`<br/>`brainoverload` |
| [NumberOfParams](diagnostics/NumberOfParams.md) | Ограничение на количество параметров метода | Да | `standard`<br/>`brainoverload` |
| [NumberOfValuesInStructureConstructor](diagnostics/NumberOfValuesInStructureConstructor.md) | Ограничение на количество значений свойств, передаваемых в конструктор структуры | Да | `standard`<br/>`brainoverload` |
| [OneStatementPerLine](diagnostics/OneStatementPerLine.md) | Одно выражение в одной строке | Да | `standard`<br/>`design` |
| [OrderOfParams](diagnostics/OrderOfParams.md) | Порядок параметров метода | Да | `standard`<br/>`design` |
| [PairingBrokenTransaction](diagnostics/PairingBrokenTransaction.md) | Нарушение парности использования методов "НачатьТранзакцию()" и "ЗафиксироватьТранзакцию()" / "ОтменитьТранзакцию()" | Да | `standard` |
| [ParseError](diagnostics/ParseError.md) | Ошибка разбора исходного кода | Да | `error` |
| [ProcedureReturnsValue](diagnostics/ProcedureReturnsValue.md) | Процедура не должна возвращать значение | Да | `error` |
| [SelfAssign](diagnostics/SelfAssign.md) | Присвоение переменной самой себе | Да | `suspicious` |
| [SelfInsertion](diagnostics/SelfInsertion.md) | Вставка коллекции в саму себя | Да | `standard`<br/>`unpredictable`<br/>`performance` |
| [SemicolonPresence](diagnostics/SemicolonPresence.md) | Выражение должно заканчиваться ";" | Да | `standard`<br/>`badpractice` |
| [SeveralCompilerDirectives](diagnostics/SeveralCompilerDirectives.md) | Ошибочное указание нескольких директив компиляции | Да | `unpredictable`<br/>`error` |
| [SpaceAtStartComment](diagnostics/SpaceAtStartComment.md) | Пробел в начале комментария | Да | `standard` |
| [TernaryOperatorUsage](diagnostics/TernaryOperatorUsage.md) | Использование тернарного оператора | Нет | `brainoverload` |
| [TryNumber](diagnostics/TryNumber.md) | Приведение к числу в попытке | Да | `standard` |
| [UnknownPreprocessorSymbol](diagnostics/UnknownPreprocessorSymbol.md) | Неизвестный символ препроцессора | Да | `standard`<br/>`error` |
| [UnreachableCode](diagnostics/UnreachableCode.md) | Недостижимый код | Да | `design`<br/>`suspicious` |
| [UseLessForEach](diagnostics/UseLessForEach.md) | Бесполезный перебор коллекции | Да | `clumsy` |
| [UsingCancelParameter](diagnostics/UsingCancelParameter.md) | Работа с параметром "Отказ" | Да | `standard`<br/>`badpractice` |
| [UsingFindElementByString](diagnostics/UsingFindElementByString.md) | Использование методов "НайтиПоНаименованию" и "НайтиПоКоду" | Да | `standard`<br/>`badpractice`<br/>`performance` |
| [UsingGoto](diagnostics/UsingGoto.md) | Использование "Перейти" | Да | `standard`<br/>`badpractice` |
| [UsingHardcodePath](diagnostics/UsingHardcodePath.md) | Хранение путей к файлам и ip-адресов в коде | Да | `standard` |
| [UsingHardcodeSecretInformation](diagnostics/UsingHardcodeSecretInformation.md) | Хранение конфиденциальной информации в коде | Да | `standard` |
| [UsingModalWindows](diagnostics/UsingModalWindows.md) | Использование модальных окон | Нет | `standard` |
| [UsingObjectNotAvailableUnix](diagnostics/UsingObjectNotAvailableUnix.md) | Использование объектов недоступных в Unix системах | Да | `standard`<br/>`lockinos` |
| [UsingServiceTag](diagnostics/UsingServiceTag.md) | Использование служебных тегов | Да | `badpractice` |
| [UsingSynchronousCalls](diagnostics/UsingSynchronousCalls.md) | Использование синхронных вызовов | Нет | `standard` |
| [UsingThisForm](diagnostics/UsingThisForm.md) | Использование свойства "ЭтаФорма" | Да | `standard`<br/>`deprecated` |
| [YoLetterUsage](diagnostics/YoLetterUsage.md) | Использование буквы “ё” в текстах модулей | Да | `standard` |