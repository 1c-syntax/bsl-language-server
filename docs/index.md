# BSL Language Server

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

## Возможности

* Показ всплывающей информации по текущей процедуре
* Диагностики
* Запуск движка диагностик из командной строки

## Запуск из командной строки

Запуск jar-файлов осуществляется через `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help
usage: BSL language server [-a] [-d <arg>] [-h] [-r <arg>] [-s <arg>]
 -a,--analyze                    Run analysis and get diagnostic info
 -d,--diagnosticLanguage <arg>   Language of diagnostic messages. Possible
                                 values: en, ru. Default is en.
 -h,--help                       Show help.
 -r,--reporter <arg>             Reporter key
 -s,--srcDir <arg>               Source directory
```

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP](https://microsoft.github.io/language-server-protocol/). Для взаимодействия используются stdin и stdout.

По умолчанию тексты диагностик выдаются на английском языке. Для переключения языка сообщений от движка диагностик используется параметр `--diagnosticLanguage` (сокращенно `-d`), за которым следует код языка:

```sh
java -jar bsl-language-server.jar --diagnosticLanguage ru
```

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

## Репортеры

Используются для получения результатов анализа.

### Список реализованных репортеров

* [json](reporters/json.md) - вывод результата анализа в собственном формате JSON, поддерживаемым [SonarQube 1C (BSL) Community Plugin](https://github.com/1c-syntax/sonar-bsl-plugin-community);
* [junit](reporters/junit.md);
* [tslint](reporters/tslint.md);
* [console](reporters/console.md).

## Диагностики

Используются для проверки кода на соответствие стандартам кодирования и для поиска возможных ошибок.

### Список реализованных диагностик

* [CanonicalSpellingKeywords - Каноническое написание ключевых слов](diagnostics/CanonicalSpellingKeywords.md)
* [EmptyCodeBlock - Пустой блок кода](diagnostics/EmptyCodeBlock.md)
* [EmptyStatement - Пустой оператор](diagnostics/EmptyStatement.md)
* [FunctionShouldHaveReturn - Функция должна содержать возврат](diagnostics/FunctionShouldHaveReturn.md)
* [IfElseIfEndsWithElse - Использование синтаксической конструкции Если...Тогда...ИначеЕсли...](diagnostics/IfElseIfEndsWithElse.md)
* [LineLength - Ограничение на длину строки](diagnostics/LineLength.md)
* [MethodSize - Ограничение на размер метода](diagnostics/MethodSize.md)
* [NestedTernaryOperator - Вложенный тернарный оператор](diagnostics/NestedTernaryOperator.md)
* [OneStatementPerLine - Одно выражение в одной строке](diagnostics/OneStatementPerLine.md)
* [SemicolonPresence - Выражение должно заканчиваться ";"](diagnostics/SemicolonPresence.md)
* [SelfAssign - Присвоение переменной самой себе](diagnostics/SelfAssign.md)
* [UnknownPreprocessorSymbol - Неизвестный символ препроцессора](diagnostics/UnknownPreprocessorSymbol.md)
* [YoLetterUsageDiagnostic - Использование буквы "ё" в текстах модулей](diagnostics/YoLetterUsageDiagnostic.md)
