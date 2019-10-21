# Диагностики

Используются для проверки кода на соответствие стандартам кодирования и для поиска возможных ошибок.

Некоторые диагностики выключены по умолчанию. Для их включения используйте <a href="#configuration">конфигурационный файл</a>.

Для экранирования отдельных участков кода или файлов от срабатывания диагностик можно воспользоваться специальными комментариями вида `// BSLLS:КлючДиагностики-выкл`. Более подробно данная функциональность описана в [Экранирование участков кода](../features/DiagnosticIgnorance.md).

## Список реализованных диагностик

| Ключ | Название | Включена по умолчанию |
| --- | --- | :-: |
| [BeginTransactionBeforeTryCatch](BeginTransactionBeforeTryCatch.md) | Нарушение правил работы с транзакциями для метода 'НачатьТранзакцию' | Да |
| [CanonicalSpellingKeywords](CanonicalSpellingKeywords.md) | Каноническое написание ключевых слов | Да |
| [CognitiveComplexity](CognitiveComplexity.md) | Когнитивная сложность | Да |
| [CommentedCode](CommentedCode.md) | Закомментированный фрагмент кода | Да |
| [CommitTransactionOutsideTryCatch](CommitTransactionOutsideTryCatch.md) | Нарушение правил работы с транзакциями для метода 'ЗафиксироватьТранзакцию' | Да |
| [DeletingCollectionItem](DeletingCollectionItem.md) | Удаление элемента при обходе коллекции посредством оператора "Для каждого ... Из ... Цикл" | Да |
| [DeprecatedMessage](DeprecatedMessage.md) | Ограничение на использование устаревшего метода "Сообщить" | Да |
| [EmptyCodeBlock](EmptyCodeBlock.md) | Пустой блок кода | Да |
| [EmptyStatement](EmptyStatement.md) | Пустой оператор | Да |
| [ExtraCommas](ExtraCommas.md) | Лишние запятые при вызове метода | Да |
| [FunctionShouldHaveReturn](FunctionShouldHaveReturn.md) | Функция должна содержать возврат | Да |
| [IdenticalExpressions](IdenticalExpressions.md) | Одинаковые выражения слева и справа от "foo" оператора | Да |
| [IfConditionComplexity](IfConditionComplexity.md) | Слишком сложное условие оператора Если | Да |
| [IfElseDuplicatedCodeBlock](IfElseDuplicatedCodeBlock.md) | Повторяющиеся блоки кода в синтаксической конструкции Если…Тогда…ИначеЕсли… | Да |
| [IfElseDuplicatedCondition](IfElseDuplicatedCondition.md) | Повторяющиеся условия в синтаксической конструкции Если…Тогда…ИначеЕсли… | Да |
| [IfElseIfEndsWithElse](IfElseIfEndsWithElse.md) | Использование синтаксической конструкции Если...Тогда...ИначеЕсли... | Да |
| [LineLength](LineLength.md) | Ограничение на длину строки | Да |
| [MagicNumber](MagicNumber.md) | Использование магического числа | Да |
| [MethodSize](MethodSize.md) | Ограничение на размер метода | Да |
| [MissingCodeTryCatchEx](MissingCodeTryCatchEx.md) | Конструкция "Попытка...Исключение...КонецПопытки" не содержит кода в исключении | Да |
| [MissingSpace](MissingSpace.md) | Пропущен пробел | Да |
| [NestedConstructorsInStructureDeclaration](NestedConstructorsInStructureDeclaration.md) | Использование конструкторов с параметрами при объявлении структуры | Да |
| [NestedStatements](NestedStatements.md) | Управляющие конструкции не должны быть вложены слишком глубоко | Да |
| [NestedTernaryOperator](NestedTernaryOperator.md) | Вложенный тернарный оператор | Да |
| [NonExportMethodsInApiRegion](NonExportMethodsInApiRegion.md) | Неэкспортные методы в областях ПрограммныйИнтерфейс и СлужебныйПрограммныйИнтерфейс | Да |
| [NumberOfOptionalParams](NumberOfOptionalParams.md) | Ограничение на количество не обязательных параметров метода | Да |
| [NumberOfParams](NumberOfParams.md) | Ограничение на количество параметров метода | Да |
| [NumberOfValuesInStructureConstructor](NumberOfValuesInStructureConstructor.md) | Ограничение на количество значений свойств, передаваемых в конструктор структуры | Да |
| [OneStatementPerLine](OneStatementPerLine.md) | Одно выражение в одной строке | Да |
| [OrderOfParams](OrderOfParams.md) | Порядок параметров метода | Да |
| [PairingBrokenTransaction](PairingBrokenTransaction.md) | Нарушение парности использования методов "НачатьТранзакцию()" и "ЗафиксироватьТранзакцию()" / "ОтменитьТранзакцию()" | Да |
| [ParseError](ParseError.md) | Ошибка разбора исходного кода | Да |
| [ProcedureReturnsValue](ProcedureReturnsValue.md) | Процедура не должна возвращать значение | Да |
| [SelfAssign](SelfAssign.md) | Присвоение переменной самой себе | Да |
| [SelfInsertion](SelfInsertion.md) | Вставка коллекции в саму себя | Да |
| [SemicolonPresence](SemicolonPresence.md) | Выражение должно заканчиваться ";" | Да |
| [SeveralCompilerDirectives](SeveralCompilerDirectives.md) | Ошибочное указание нескольких директив компиляции | Да |
| [SpaceAtStartComment](SpaceAtStartComment.md) | Пробел в начале комментария | Да |
| [TernaryOperatorUsage](TernaryOperatorUsage.md) | Использование тернарного оператора | Нет |
| [TryNumber](TryNumber.md) | Приведение к числу в попытке | Да |
| [UnknownPreprocessorSymbol](UnknownPreprocessorSymbol.md) | Неизвестный символ препроцессора | Да |
| [UnreachableCode](UnreachableCode.md) | Недостижимый код | Да |
| [UseLessForEach](UseLessForEach.md) | Бесполезный перебор коллекции | Да |
| [UsingCancelParameter](UsingCancelParameter.md) | Работа с параметром "Отказ" | Да |
| [UsingFindElementByString](UsingFindElementByString.md) | Использование методов "НайтиПоНаименованию" и "НайтиПоКоду" | Да |
| [UsingGoto](UsingGoto.md) | Использование "Перейти" | Да |
| [UsingHardcodePath](UsingHardcodePath.md) | Хранение путей к файлам и ip-адресов в коде | Да |
| [UsingHardcodeSecretInformation](UsingHardcodeSecretInformation.md) | Хранение конфиденциальной информации в коде | Да |
| [UsingModalWindows](UsingModalWindows.md) | Использование модальных окон | Нет |
| [UsingObjectNotAvailableUnix](UsingObjectNotAvailableUnix.md) | Использование объектов недоступных в Unix системах | Да |
| [UsingServiceTag](UsingServiceTag.md) | Использование служебных тегов | Да |
| [UsingSynchronousCalls](UsingSynchronousCalls.md) | Использование синхронных вызовов | Нет |
| [UsingThisForm](UsingThisForm.md) | Использование свойства "ЭтаФорма" | Да |
| [YoLetterUsage](YoLetterUsage.md) | Использование буквы “ё” в текстах модулей | Да |