# Usage AttachIdleHandler (AttachIdleHandler)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Info` | `Yes` | `1` | `error`<br/>`unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Attach or detach idle handler with not existed method

## Examples

BAD

```Bsl

&НаКлиенте
Процедура НайтиСтрокуСОшибкой(Команда)

    ПодключитьОбработчикОжидания("НеизвестныйМетод", 0.1, Истина);

КонецПроцедуры

```

GOOD

```Bsl
&НаКлиенте
Процедура НайтиСтроку(Команда)

    ПодключитьОбработчикОжидания("ВыполнитьПоиск", 0.1, Истина);

КонецПроцедуры

&НаКлиенте
Процедура ВыполнитьПоиск()

КонецПроцедуры

```
## Sources

* Usage info: [Отложенная обработка события элемента управления в форме](https://its.1c.ru/db/metod8dev/content/1820/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:AttachIdleHandler-off
// BSLLS:AttachIdleHandler-on
```

### Parameter for config

```json
"AttachIdleHandler": false
```
