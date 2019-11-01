# Диагностики

Используются для проверки кода на соответствие стандартам кодирования и для поиска возможных ошибок.

Некоторые диагностики выключены по умолчанию. Для их включения используйте <a href="/#configuration">конфигурационный файл</a>.

Для экранирования отдельных участков кода или файлов от срабатывания диагностик можно воспользоваться специальными комментариями вида `// BSLLS:КлючДиагностики-выкл`. Более подробно данная функциональность описана в [Экранирование участков кода](../features/DiagnosticIgnorance.md).

## Список реализованных диагностик

Общее количество: **59**

* Дефект кода: **38**
* Уязвимость: **1**
* Ошибка: **20**

| Ключ | Название | Включена по умолчанию | Важность | Тип | Тэги |
| --- | --- | :-: | --- | --- | --- |
| [BeginTransactionBeforeTryCatch](BeginTransactionBeforeTryCatch.md) | Нарушение правил работы с транзакциями для метода 'НачатьТранзакцию' | Да | Важный | Ошибка | `standard` |
| [CanonicalSpellingKeywords](CanonicalSpellingKeywords.md) | Каноническое написание ключевых слов | Да | Информационный | Дефект кода | `standard` |
| [CognitiveComplexity](CognitiveComplexity.md) | Когнитивная сложность | Да | Критичный | Дефект кода | `brainoverload` |
| [CommentedCode](CommentedCode.md) | Закомментированный фрагмент кода | Да | Незначительный | Дефект кода | `standard`<br/>`badpractice` |
| [CommitTransactionOutsideTryCatch](CommitTransactionOutsideTryCatch.md) | Нарушение правил работы с транзакциями для метода 'ЗафиксироватьТранзакцию' | Да | Важный | Ошибка | `standard` |
| [DeletingCollectionItem](DeletingCollectionItem.md) | Удаление элемента при обходе коллекции посредством оператора "Для каждого ... Из ... Цикл" | Да | Важный | Ошибка | `standard`<br/>`error` |
| [DeprecatedFind](DeprecatedFind.md) | Использование устаревшего метода "Найти" | Да | Незначительный | Дефект кода | `deprecated` |
| [DeprecatedMessage](DeprecatedMessage.md) | Ограничение на использование устаревшего метода "Сообщить" | Да | Незначительный | Дефект кода | `standard`<br/>`deprecated` |
| [EmptyCodeBlock](EmptyCodeBlock.md) | Пустой блок кода | Да | Важный | Дефект кода | `badpractice`<br/>`suspicious` |
| [EmptyStatement](EmptyStatement.md) | Пустой оператор | Да | Информационный | Дефект кода | `badpractice` |
| [ExtraCommas](ExtraCommas.md) | Запятые без указания параметра в конце вызова метода | Да | Важный | Дефект кода | `standard`<br/>`badpractice` |
| [FunctionShouldHaveReturn](FunctionShouldHaveReturn.md) | Функция должна содержать возврат | Да | Важный | Ошибка | `suspicious`<br/>`unpredictable` |
| [IdenticalExpressions](IdenticalExpressions.md) | Одинаковые выражения слева и справа от "foo" оператора | Да | Важный | Ошибка | `suspicious` |
| [IfConditionComplexity](IfConditionComplexity.md) | Использование сложных выражений в условии оператора "Если" | Да | Незначительный | Дефект кода | `brainoverload` |
| [IfElseDuplicatedCodeBlock](IfElseDuplicatedCodeBlock.md) | Повторяющиеся блоки кода в синтаксической конструкции Если...Тогда...ИначеЕсли... | Да | Незначительный | Дефект кода | `suspicious` |
| [IfElseDuplicatedCondition](IfElseDuplicatedCondition.md) | Повторяющиеся условия в синтаксической конструкции Если...Тогда...ИначеЕсли... | Да | Важный | Дефект кода | `suspicious` |
| [IfElseIfEndsWithElse](IfElseIfEndsWithElse.md) | Использование синтаксической конструкции Если...Тогда...ИначеЕсли... | Да | Важный | Дефект кода | `badpractice` |
| [LineLength](LineLength.md) | Ограничение на длину строки | Да | Незначительный | Дефект кода | `standard`<br/>`badpractice` |
| [MagicNumber](MagicNumber.md) | Использование магического числа | Да | Незначительный | Дефект кода | `badpractice` |
| [MethodSize](MethodSize.md) | Ограничение на размер метода | Да | Важный | Дефект кода | `badpractice` |
| [MissingCodeTryCatchEx](MissingCodeTryCatchEx.md) | Конструкция "Попытка...Исключение...КонецПопытки" не содержит кода в исключении | Да | Важный | Ошибка | `standard`<br/>`badpractice` |
| [MissingSpace](MissingSpace.md) | Пропущены пробелы слева или справа от операторов `+ - * / = % < > <> <= >=`, а так же справа от `,` и `;` | Да | Информационный | Дефект кода | `badpractice` |
| [MissingTemporaryFileDeletion](MissingTemporaryFileDeletion.md) | Отсутствует удаление временного файла после использования | Да | Важный | Ошибка | `badpractice`<br/>`standard` |
| [NestedConstructorsInStructureDeclaration](NestedConstructorsInStructureDeclaration.md) | Использование конструкторов с параметрами при объявлении структуры | Да | Незначительный | Дефект кода | `badpractice`<br/>`brainoverload` |
| [NestedStatements](NestedStatements.md) | Управляющие конструкции не должны быть вложены слишком глубоко | Да | Критичный | Дефект кода | `badpractice`<br/>`brainoverload` |
| [NestedTernaryOperator](NestedTernaryOperator.md) | Вложенный тернарный оператор | Да | Важный | Дефект кода | `brainoverload` |
| [NonExportMethodsInApiRegion](NonExportMethodsInApiRegion.md) | Неэкспортные методы в областях ПрограммныйИнтерфейс и СлужебныйПрограммныйИнтерфейс | Да | Важный | Дефект кода | `standard` |
| [NumberOfOptionalParams](NumberOfOptionalParams.md) | Ограничение на количество не обязательных параметров метода | Да | Незначительный | Дефект кода | `standard`<br/>`brainoverload` |
| [NumberOfParams](NumberOfParams.md) | Ограничение на количество параметров метода | Да | Незначительный | Дефект кода | `standard`<br/>`brainoverload` |
| [NumberOfValuesInStructureConstructor](NumberOfValuesInStructureConstructor.md) | Ограничение на количество значений свойств, передаваемых в конструктор структуры | Да | Незначительный | Дефект кода | `standard`<br/>`brainoverload` |
| [OneStatementPerLine](OneStatementPerLine.md) | Одно выражение в одной строке | Да | Незначительный | Дефект кода | `standard`<br/>`design` |
| [OrderOfParams](OrderOfParams.md) | Порядок параметров метода | Да | Важный | Дефект кода | `standard`<br/>`design` |
| [PairingBrokenTransaction](PairingBrokenTransaction.md) | Нарушение парности использования методов "НачатьТранзакцию()" и "ЗафиксироватьТранзакцию()" / "ОтменитьТранзакцию()" | Да | Важный | Ошибка | `standard` |
| [ParseError](ParseError.md) | Ошибка разбора исходного кода | Да | Критичный | Ошибка | `error` |
| [ProcedureReturnsValue](ProcedureReturnsValue.md) | Процедура не должна возвращать значение | Да | Блокирующий | Ошибка | `error` |
| [SelfAssign](SelfAssign.md) | Присвоение переменной самой себе | Да | Важный | Ошибка | `suspicious` |
| [SelfInsertion](SelfInsertion.md) | Вставка коллекции в саму себя | Да | Важный | Ошибка | `standard`<br/>`unpredictable`<br/>`performance` |
| [SemicolonPresence](SemicolonPresence.md) | Выражение должно заканчиваться символом ";" | Да | Незначительный | Дефект кода | `standard`<br/>`badpractice` |
| [SeveralCompilerDirectives](SeveralCompilerDirectives.md) | Ошибочное указание нескольких директив компиляции | Да | Критичный | Ошибка | `unpredictable`<br/>`error` |
| [SpaceAtStartComment](SpaceAtStartComment.md) | Пробел в начале комментария | Да | Информационный | Дефект кода | `standard` |
| [TernaryOperatorUsage](TernaryOperatorUsage.md) | Использование тернарного оператора | Нет | Незначительный | Дефект кода | `brainoverload` |
| [TryNumber](TryNumber.md) | Приведение к числу в попытке | Да | Важный | Дефект кода | `standard` |
| [UnaryPlusInConcatenation](UnaryPlusInConcatenation.md) | Унарный плюс в конкатенации строк | Да | Блокирующий | Ошибка | `suspicious`<br/>`brainoverload` |
| [UnknownPreprocessorSymbol](UnknownPreprocessorSymbol.md) | Неизвестный символ препроцессора | Да | Критичный | Ошибка | `standard`<br/>`error` |
| [UnreachableCode](UnreachableCode.md) | Недостижимый код | Да | Незначительный | Ошибка | `design`<br/>`suspicious` |
| [UnusedLocalMethod](UnusedLocalMethod.md) | Неиспользуемый локальный метод | Да | Важный | Дефект кода | `standard`<br/>`suspicious` |
| [UseLessForEach](UseLessForEach.md) | Бесполезный перебор коллекции | Да | Критичный | Ошибка | `clumsy` |
| [UsingCancelParameter](UsingCancelParameter.md) | Работа с параметром "Отказ" | Да | Важный | Дефект кода | `standard`<br/>`badpractice` |
| [UsingFindElementByString](UsingFindElementByString.md) | Использование методов "НайтиПоНаименованию" и "НайтиПоКоду" | Да | Важный | Дефект кода | `standard`<br/>`badpractice`<br/>`performance` |
| [UsingGoto](UsingGoto.md) | Оператор "Перейти" не должен использоваться | Да | Критичный | Дефект кода | `standard`<br/>`badpractice` |
| [UsingHardcodePath](UsingHardcodePath.md) | Хранение путей к файлам и ip-адресов в коде | Да | Критичный | Ошибка | `standard` |
| [UsingHardcodeSecretInformation](UsingHardcodeSecretInformation.md) | Хранение конфиденциальной информации в коде | Да | Критичный | Уязвимость | `standard` |
| [UsingModalWindows](UsingModalWindows.md) | Использование модальных окон | Нет | Важный | Дефект кода | `standard` |
| [UsingObjectNotAvailableUnix](UsingObjectNotAvailableUnix.md) | Использование объектов недоступных в Unix системах | Да | Критичный | Ошибка | `standard`<br/>`lockinos` |
| [UsingServiceTag](UsingServiceTag.md) | Использование служебных тегов | Да | Информационный | Дефект кода | `badpractice` |
| [UsingSynchronousCalls](UsingSynchronousCalls.md) | Использование синхронных вызовов | Нет | Важный | Дефект кода | `standard` |
| [UsingThisForm](UsingThisForm.md) | Использование устаревшего свойства "ЭтаФорма" | Да | Незначительный | Дефект кода | `standard`<br/>`deprecated` |
| [WorkingTimeoutWithExternalResources](WorkingTimeoutWithExternalResources.md) | Таймауты при работе с внешними ресурсами | Да | Критичный | Ошибка | `unpredictable`<br/>`standard` |
| [YoLetterUsage](YoLetterUsage.md) | Использование буквы "ё" в текстах модулей | Да | Информационный | Дефект кода | `standard` |