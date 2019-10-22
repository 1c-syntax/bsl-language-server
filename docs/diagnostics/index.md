# Диагностики

Используются для проверки кода на соответствие стандартам кодирования и для поиска возможных ошибок.

Некоторые диагностики выключены по умолчанию. Для их включения используйте <a href="/#configuration">конфигурационный файл</a>.

Для экранирования отдельных участков кода или файлов от срабатывания диагностик можно воспользоваться специальными комментариями вида `// BSLLS:КлючДиагностики-выкл`. Более подробно данная функциональность описана в [Экранирование участков кода](../features/DiagnosticIgnorance.md).

## Список реализованных диагностик

| Ключ | Название | Включена по умолчанию | Тэги |
| --- | --- | :-: | --- |
| [BeginTransactionBeforeTryCatch](BeginTransactionBeforeTryCatch.md) | Нарушение правил работы с транзакциями для метода 'НачатьТранзакцию' | Да | `standard` |
| [CanonicalSpellingKeywords](CanonicalSpellingKeywords.md) | Каноническое написание ключевых слов | Да | `standard` |
| [CognitiveComplexity](CognitiveComplexity.md) | Когнитивная сложность | Да | `brainoverload` |
| [CommentedCode](CommentedCode.md) | Закомментированный фрагмент кода | Да | `standard`<br/>`badpractice` |
| [CommitTransactionOutsideTryCatch](CommitTransactionOutsideTryCatch.md) | Нарушение правил работы с транзакциями для метода 'ЗафиксироватьТранзакцию' | Да | `standard` |
| [DeletingCollectionItem](DeletingCollectionItem.md) | Удаление элемента при обходе коллекции посредством оператора "Для каждого ... Из ... Цикл" | Да | `standard`<br/>`error` |
| [DeprecatedMessage](DeprecatedMessage.md) | Ограничение на использование устаревшего метода "Сообщить" | Да | `standard`<br/>`deprecated` |
| [EmptyCodeBlock](EmptyCodeBlock.md) | Пустой блок кода | Да | `badpractice`<br/>`suspicious` |
| [EmptyStatement](EmptyStatement.md) | Пустой оператор | Да | `badpractice` |
| [ExtraCommas](ExtraCommas.md) | Лишние запятые при вызове метода | Да | `standard`<br/>`badpractice` |
| [FunctionShouldHaveReturn](FunctionShouldHaveReturn.md) | Функция должна содержать возврат | Да | `suspicious`<br/>`unpredictable` |
| [IdenticalExpressions](IdenticalExpressions.md) | Одинаковые выражения слева и справа от "foo" оператора | Да | `suspicious` |
| [IfConditionComplexity](IfConditionComplexity.md) | Слишком сложное условие оператора Если | Да | `brainoverload` |
| [IfElseDuplicatedCodeBlock](IfElseDuplicatedCodeBlock.md) | Повторяющиеся блоки кода в синтаксической конструкции Если…Тогда…ИначеЕсли… | Да | `suspicious` |
| [IfElseDuplicatedCondition](IfElseDuplicatedCondition.md) | Повторяющиеся условия в синтаксической конструкции Если…Тогда…ИначеЕсли… | Да | `suspicious` |
| [IfElseIfEndsWithElse](IfElseIfEndsWithElse.md) | Использование синтаксической конструкции Если...Тогда...ИначеЕсли... | Да | `badpractice` |
| [LineLength](LineLength.md) | Ограничение на длину строки | Да | `standard`<br/>`badpractice` |
| [MagicNumber](MagicNumber.md) | Использование магического числа | Да | `badpractice` |
| [MethodSize](MethodSize.md) | Ограничение на размер метода | Да | `badpractice` |
| [MissingCodeTryCatchEx](MissingCodeTryCatchEx.md) | Конструкция "Попытка...Исключение...КонецПопытки" не содержит кода в исключении | Да | `standard`<br/>`badpractice` |
| [MissingSpace](MissingSpace.md) | Пропущен пробел | Да | `badpractice` |
| [MissingTemporaryFileDeletion](MissingTemporaryFileDeletion.md) | Отсутствует удаление временного файла после использования | Да | `badpractice`<br/>`standard` |
| [NestedConstructorsInStructureDeclaration](NestedConstructorsInStructureDeclaration.md) | Использование конструкторов с параметрами при объявлении структуры | Да | `badpractice`<br/>`brainoverload` |
| [NestedStatements](NestedStatements.md) | Управляющие конструкции не должны быть вложены слишком глубоко | Да | `badpractice`<br/>`brainoverload` |
| [NestedTernaryOperator](NestedTernaryOperator.md) | Вложенный тернарный оператор | Да | `brainoverload` |
| [NonExportMethodsInApiRegion](NonExportMethodsInApiRegion.md) | Неэкспортные методы в областях ПрограммныйИнтерфейс и СлужебныйПрограммныйИнтерфейс | Да | `standard` |
| [NumberOfOptionalParams](NumberOfOptionalParams.md) | Ограничение на количество не обязательных параметров метода | Да | `standard`<br/>`brainoverload` |
| [NumberOfParams](NumberOfParams.md) | Ограничение на количество параметров метода | Да | `standard`<br/>`brainoverload` |
| [NumberOfValuesInStructureConstructor](NumberOfValuesInStructureConstructor.md) | Ограничение на количество значений свойств, передаваемых в конструктор структуры | Да | `standard`<br/>`brainoverload` |
| [OneStatementPerLine](OneStatementPerLine.md) | Одно выражение в одной строке | Да | `standard`<br/>`design` |
| [OrderOfParams](OrderOfParams.md) | Порядок параметров метода | Да | `standard`<br/>`design` |
| [PairingBrokenTransaction](PairingBrokenTransaction.md) | Нарушение парности использования методов "НачатьТранзакцию()" и "ЗафиксироватьТранзакцию()" / "ОтменитьТранзакцию()" | Да | `standard` |
| [ParseError](ParseError.md) | Ошибка разбора исходного кода | Да | `error` |
| [ProcedureReturnsValue](ProcedureReturnsValue.md) | Процедура не должна возвращать значение | Да | `error` |
| [SelfAssign](SelfAssign.md) | Присвоение переменной самой себе | Да | `suspicious` |
| [SelfInsertion](SelfInsertion.md) | Вставка коллекции в саму себя | Да | `standard`<br/>`unpredictable`<br/>`performance` |
| [SemicolonPresence](SemicolonPresence.md) | Выражение должно заканчиваться ";" | Да | `standard`<br/>`badpractice` |
| [SeveralCompilerDirectives](SeveralCompilerDirectives.md) | Ошибочное указание нескольких директив компиляции | Да | `unpredictable`<br/>`error` |
| [SpaceAtStartComment](SpaceAtStartComment.md) | Пробел в начале комментария | Да | `standard` |
| [TernaryOperatorUsage](TernaryOperatorUsage.md) | Использование тернарного оператора | Нет | `brainoverload` |
| [TryNumber](TryNumber.md) | Приведение к числу в попытке | Да | `standard` |
| [UnaryPlusInConcatenation](UnaryPlusInConcatenation.md) | Унарный плюс в конкатенации строк | Да | `suspicious`<br/>`brainoverload` |
| [UnknownPreprocessorSymbol](UnknownPreprocessorSymbol.md) | Неизвестный символ препроцессора | Да | `standard`<br/>`error` |
| [UnreachableCode](UnreachableCode.md) | Недостижимый код | Да | `design`<br/>`suspicious` |
| [UseLessForEach](UseLessForEach.md) | Бесполезный перебор коллекции | Да | `clumsy` |
| [UsingCancelParameter](UsingCancelParameter.md) | Работа с параметром "Отказ" | Да | `standard`<br/>`badpractice` |
| [UsingFindElementByString](UsingFindElementByString.md) | Использование методов "НайтиПоНаименованию" и "НайтиПоКоду" | Да | `standard`<br/>`badpractice`<br/>`performance` |
| [UsingGoto](UsingGoto.md) | Использование "Перейти" | Да | `standard`<br/>`badpractice` |
| [UsingHardcodePath](UsingHardcodePath.md) | Хранение путей к файлам и ip-адресов в коде | Да | `standard` |
| [UsingHardcodeSecretInformation](UsingHardcodeSecretInformation.md) | Хранение конфиденциальной информации в коде | Да | `standard` |
| [UsingModalWindows](UsingModalWindows.md) | Использование модальных окон | Нет | `standard` |
| [UsingObjectNotAvailableUnix](UsingObjectNotAvailableUnix.md) | Использование объектов недоступных в Unix системах | Да | `standard`<br/>`lockinos` |
| [UsingServiceTag](UsingServiceTag.md) | Использование служебных тегов | Да | `badpractice` |
| [UsingSynchronousCalls](UsingSynchronousCalls.md) | Использование синхронных вызовов | Нет | `standard` |
| [UsingThisForm](UsingThisForm.md) | Использование свойства "ЭтаФорма" | Да | `standard`<br/>`deprecated` |
| [WorkingTimeoutWithExternalResources](WorkingTimeoutWithExternalResources.md) | Таймауты при работе с внешними ресурсами | Да | `unpredictable`<br/>`standard` |
| [YoLetterUsage](YoLetterUsage.md) | Использование буквы “ё” в текстах модулей | Да | `standard` |