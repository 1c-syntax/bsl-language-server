# Missing temporary storage data deletion after using (MissingTempStorageDeletion)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

# Отсутствует удаление данных из временного хранилища после использования (MissingTempStorageDeletion)

|     Type     |        Scope        |  Severity  | Activated by default | Minutes<br> to fix |                           Tags                           |
|:------------:|:-------------------:|:----------:|:--------------------:|:------------------------:|:--------------------------------------------------------:|
| `Code smell` | `BSL`<br>`OS` | `Critical` |         `No`         |           `3`            | `standard`<br>`performance`<br>`badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Diagnostics is now tracking all `GetFromTempStorage` calls that do not have a corresponding `DeleteFromTempStorage` call
- within one method!

The rules for working with objects in temporary storage are often violated - storing data in it is not free. There are errors even in the documentation and numerous use cases.

When placing data in temporary storage, you should choose one of two options:

- put data into temporary storage for the lifetime of the form using the unique identifier of the form
  - and clean up this temporary storage after use.
- pre-initialize temporary storage and reuse it.

Otherwise, if an action is repeated many times in a form, for example, if a product is selected many times, this leads to unnecessary consumption of RAM, because temporary stores are accumulating.

Remember that when a value is retrieved from the temporary storage on the server, it will be retrieved by reference. This is a reference to the value that is stored in the cache. Within *20 minutes*, from the moment it was put into the storage or from the moment of the last access, the value will be stored in the cache, and then written to disk and deleted from the cache. On the next call, the value is loaded from disk and put back into the cache.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

1 - Example of correct code:
```bsl
&НаКлиенте
Процедура ПриОбработкеПодобранныхТоваров(Элемент, АдресТоваровВХранилище, СтандартнаяОбработка) Экспорт
    Если АдресТоваровВХранилище = Неопределено Тогда
        Возврат;
    КонецЕсли;
    ПолучитьТоварыИзХранилища(АдресТоваровВХранилище); 
КонецПроцедуры

&НаСервере
Процедура ПолучитьТоварыИзХранилища(АдресТоваровВХранилище)
    ПодобранныеТовары = ПолучитьИзВременногоХранилища(АдресТоваровВХранилище);
    Объект.Товары.Загрузить(ПодобранныеТовары);

    УдалитьИзВременногоХранилища(АдресТоваровДокумента); // очищается временное хранилище для минимизации расхода оперативной памяти
КонецПроцедуры 
```

2 - Consider this recommendation when working with background jobs

Incorrect:
- Each time a background job is executed, its result is placed in temporary storage for the lifetime of the form:
```bsl
Parameters = LongOperations.FunctionParameters(UUID);
LongOperations.ExecFunction(Pframeters, BackgroundJobParameter);
```

- If a long operation is performed by the user multiple times, then temporary storage accumulates, which causes an increase in memory consumption.
- To reduce the consumption of RAM, in most cases, it is recommended to clear the temporary storage immediately after receiving the result of the background job:

Correct:
```bsl
Настройки = ПолучитьИзВременногоХранилища(Результат.АдресРезультата);
УдалитьИзВременногоХранилища(Результат.АдресРезультата);  // Данные во временном хранилище больше не требуются.
```

- If the result of a background job needs to be saved over several server calls, then it is necessary to transfer a fixed address of a previously initialized temporary storage:
```bsl
&AtServer
Процедура ПриСозданииНаСервере(Отказ)
    АдресРезультатаФоновогоЗадания = ПоместитьВоВременноеХранилище(Неопределено, УникальныйИдентификатор); // Резервируем адрес временного хранилища
EndProcedure

&НаСервере
Функция НачатьПоискНастроекУчетнойЗаписи()
    ПараметрыВыполнения = ДлительныеОперации.ПараметрыВыполненияФункции(УникальныйИдентификатор);
    ПараметрыВыполнения.АдресРезультата = АдресРезультатаФоновогоЗадания; // всегда используем одно и то же временное хранилище

    Возврат ДлительныеОперации.ВыполнитьФункцию(ПараметрыВыполнения,
        "Справочники.УчетныеЗаписиЭлектроннойПочты.ОпределитьНастройкиУчетнойЗаписи",
        АдресЭлектроннойПочты, Пароль);
EndFunction
```

3 - Another example of preliminary initialization of temporary storage for reuse

```bsl
// в форме документа
&НаСервере
Процедура ПриСозданииНаСервере(Отказ)
    АдресТоваров = ПоместитьВоВременноеХранилище(Неопределено, УникальныйИдентификатор); // Инициализируется реквизит формы
КонецПроцедуры

&НаСервере
Функция ТоварыВоВременномХранилище()
    Возврат ПоместитьВоВременноеХранилище(Товары.Выгрузить(), АдресТоваров);
КонецФункции

// и далее при переиспользовании временного хранилища не требуется удалять значение из временного хранилища:

&НаСервере
Процедура ПолучитьТоварыИзХранилища(АдресТоваровВХранилище)
    ПодобранныеТовары = ПолучитьИзВременногоХранилища(АдресТоваровВХранилище);
    Объект.Товары.Загрузить(ПодобранныеТовары);
КонецПроцедуры
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Long-term operations on the server, part 3.1 - Standard 1C (RU)](https://its.1c.ru/db/v8std#content:642:hdoc)
- [Minimizing the number of server calls, part 7.3 - Standard 1C (RU)](https://its.1c.ru/db/v8std#content:487:hdoc)
- [Temporary Storage Engine - Developer's Guide (RU)](https://its.1c.ru/db/v8319doc#bookmark:dev:TI000000810)
