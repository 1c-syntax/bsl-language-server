# Using service tags (UsingServiceTag)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Info` | `Yes` | `0` | `badpractice` 

## Parameters 

 Name | Type | Description | Default value 
 :-: | :-: | :-- | :-: 
 `serviceTags` | `String` | ```Service tags``` | ```todo|fixme|!!|mrg|@|отладка|debug|для\s*отладки|(\{\{|\}\})КОНСТРУКТОР_|(\{\{|\}\})MRG|Вставить\s*содержимое\s*обработчика|Paste\s*handler\s*content|Insert\s*handler\s*code|Insert\s*handler\s*content|Insert\s*handler\s*contents``` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The diagnostic finds use of service tags in comments. Tags list:

* TODO
* FIXME
* !!
* @
* MRG
* ОТЛАДКА
* ДЛЯ ОТЛАДКИ
* КОНСТРУКТОР_ЗАПРОСА_С_ОБРАБОТКОЙ_РЕЗУЛЬТАТА
* КОНСТРУКТОР_ДВИЖЕНИЙ_РЕГИСТРОВ
* КОНСТРУКТОР_ПЕЧАТИ
* КОНСТРУКТОР_ВВОДА_НА_ОСНОВАНИИ
* Вставить содержимое обработчика
* Insert handler code
* Insert handler contents
* Paste handler content

Tags list can be extended via options.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UsingServiceTag-off
// BSLLS:UsingServiceTag-on
```

### Parameter for config

```json
"UsingServiceTag": {
    "serviceTags": "todo|fixme|!!|mrg|@|отладка|debug|для\\s*отладки|(\\{\\{|\\}\\})КОНСТРУКТОР_|(\\{\\{|\\}\\})MRG|Вставить\\s*содержимое\\s*обработчика|Paste\\s*handler\\s*content|Insert\\s*handler\\s*code|Insert\\s*handler\\s*content|Insert\\s*handler\\s*contents"
}
```
