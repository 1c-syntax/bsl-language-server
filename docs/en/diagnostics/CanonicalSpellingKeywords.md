# Canonical keyword writing (CanonicalSpellingKeywords)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

A built-in language constructs, keywords must be written canonically.

### Keywords

| RU                 | EN            |
|--------------------|---------------|
| ВызватьИсключение  | Raise         |
| Выполнить          | Execute       |
| ДобавитьОбработчик | AddHandler    |
| Для                | For           |
| Если               | If            |
| Знач               | Val           |
| И                  | AND, and      |
| Из                 | In            |
| ИЛИ, Или           | OR, Or        |
| Иначе              | Else          |
| ИначеЕсли          | ElsIf         |
| Исключение         | Except        |
| Истина             | True          |
| Каждого, каждого   | Each, each    |
| КонецЕсли          | EndIf         |
| КонецПопытки       | EndTry        |
| КонецПроцедуры     | EndProcedure  |
| КонецФункции       | EndFunction   |
| КонецЦикла         | EndDo         |
| НЕ, Не             | NOT, Not      |
| Неопределено       | Undefined     |
| Перейти            | Goto          |
| Перем              | Var           |
| По                 | To            |
| Пока               | While         |
| Попытка            | Try           |
| Процедура          | Procedure     |
| Прервать           | Break         |
| Продолжить         | Continue      |
| Тогда              | Then          |
| Цикл               | Do            |
| УдалитьОбработчик  | RemoveHandler |
| Функция            | Function      |
| Экспорт            | Export        |

### Preprocessor instrutions

| RU                                 | EN                             |
|------------------------------------|--------------------------------|
| ВебКлиент                          | WebClient                      |
| ВнешнееСоединение                  | ExternalConnection             |
| Если                               | If                             |
| И                                  | AND, And                       |
| ИЛИ, Или                           | OR, Or                         |
| Иначе                              | Else                           |
| ИначеЕсли                          | ElsIf                          |
| КонецЕсли                          | EndIf                          |
| КонецОбласти                       | EndRegion                      |
| Клиент                             | Client                         |
| МобильноеПриложениеКлиент          | MobileAppClient                |
| МобильноеПриложениеСервер          | MobileAppServer                |
| МобильныйКлиент                    | MobileClient                   |
| НаКлиенте                          | AtClient                       |
| НаСервере                          | AtServer                       |
| НЕ, Не                             | NOT, Not                       |
| Область                            | Region                         |
| Сервер                             | Server                         |
| Тогда                              | Then                           |
| ТолстыйКлиентОбычноеПриложение     | ThickClientOrdinaryApplication |
| ТолстыйКлиентУправляемоеПриложение | ThickClientManagedApplication  |
| ТонкийКлиент                       | ThinClient                     |

### Compilation directives

| RU                             | EN                        |
|--------------------------------|---------------------------|
| НаКлиенте                      | AtClient                  |
| НаСервере                      | AtServer                  |
| НаСервереБезКонтекста          | AtServerNoContext         |
| НаКлиентеНаСервереБезКонтекста | AtClientAtServerNoContext |
| НаКлиентеНаСервере             | AtClientAtServer          |

### Extension annotations

| RU                 | EN                |
|--------------------|-------------------|
| Перед              | Before            |
| После              | After             |
| Вместо             | Around            |
| ИзменениеИКонтроль  | ChangeAndValidate |

Only annotations recognized by the parser are checked for canonical spelling. Custom OneScript
annotations and method names in annotation parameters are not checked. This diagnostic does not
check whether an annotation is available in the selected platform version.

Annotations are checked by the existing `CanonicalSpellingKeywords` diagnostic, which is enabled
by default for BSL and OneScript. Disabling the diagnostic also disables annotation checks.

## Sources

+ [Standard: General requirements (RU)](https://its.1c.ru/db/v8std#content:441:hdoc)
