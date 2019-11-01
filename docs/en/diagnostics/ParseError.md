# Source code parse error

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Critical` | `Yes` | `5` | `error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

An error occurs if the source code of the module is written with a violation of the syntax of the language or if the preprocessor instructions are used incorrectly.

Do not split separate grammatical structures, expressions, declarations and procedures and functions calls by the preprocessor instructions and regions.

## Examples

```bsl
Процедура Пример1()
  а = 1
#Область ИмяОбласти
    + 2;
#КонецОбласти // statement split
КонецПроцедуры

#Область ИмяОбласти
Процедура Пример2()
    // ...
#КонецОбласти // procedure split
КонецПроцедуры

Если <...> Тогда
    // ...
#Если ВебКлиент Тогда // If-Then block split
Иначе
    // ...
#КонецЕсли
КонецЕсли;

Результат = Пример4(Параметр1, 
#Если Клиент Тогда
  Параметр2, // incorrect function call
#КонецЕсли
  Параметр3);
```

## Sources

* [Standard: #439 Use of compilation and preprocessor directives](https://its.1c.ru/db/v8std#content:439:hdoc)
