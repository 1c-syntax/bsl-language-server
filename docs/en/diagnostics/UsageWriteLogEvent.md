# Incorrect use of the method (UsageWriteLogEvent)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
It is important to specify the parameters correctly when writing to the Log event.

You can't hide exceptions. При обработке исключений нужно выполнять запись в журнал регистрации с подробным представлением ошибки. To do this, add to the event comment the result `DetailErrorDescription(ErrorInfo())`

Do not skip the 2nd parameter Log level. If you do not specify it, by default 1C will apply the Information error level, and this record may be lost in the stream of records.

The 5th parameter - a comment to the event of writing to the logging log - must not be omitted either.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Examples of Invalid Code
```bsl
    WriteLogEvent("Event");// error
    WriteLogEvent("Event", EventLogLevel.Error);// error
    WriteLogEvent("Event", EventLogLevel.Error, , );// error
    WriteLogEvent("Event", , , , DetailErrorDescription(ErrorInfo()));

    WriteLogEvent("Event", EventLogLevel.Error, , , );// error

    Try
      ServerCode();
    Except
      WriteLogEvent("Event", EventLogLevel.Error, , ,
        ErrorDescription()); // error
      WriteLogEvent("Event", EventLogLevel.Error, , ,
        "Commentary 1"); // error
    EndTry;
```

Correct code
```bsl
    Try
      ServerCode();
    Except

      ErrorText = DetailErrorDescription(ErrorInfo());
      WriteLogEvent(NStr("en = 'Performing an operation'"), EventLogLevel.Error, , ,
         ErrorText);
    EndTry;

    Try
      ServerCode();
    Except

      ErrorText = DetailErrorDescription(ErrorInfo());
      WriteLogEvent(NStr("en = 'Performing an operation'"), EventLogLevel.Error, , ,
         ErrorText);

      Raise;
    EndTry;
```
If an outer attempt makes a log entry, then there is no need to do it again in a nested attempt:
```bsl
Процедура ЗагрузитьДанные() Экспорт
    Попытка
        ВыполнитьЗаписьДанных();
    Исключение
        ЗаписьЖурналаРегистрации(); // <- исключение подавляется с записью в ЖР
    КонецПопытки;
КонецПроцедуры

Процедура ВыполнитьЗаписьДанных()
    НачатьТранзакцию();
    Попытка
        // ...
        ЗафиксироватьТранзакцию();
    Исключение
        ОтменитьТранзакцию();
        ВызватьИсключение; // <- вложенная попытка, запись в ЖР не требуется
    КонецПопытки;
КонецПроцедуры
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* [Using the event log (RU)](https://its.1c.ru/db/v8std#content:498:hdoc)
* [Catching Exceptions in Code (RU)](https://its.1c.ru/db/v8std#content:499:hdoc)
