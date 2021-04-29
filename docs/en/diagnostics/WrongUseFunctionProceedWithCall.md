# Wrong use of ProceedWithCall function (WrongUseFunctionProceedWithCall)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Error` | `BSL` | `Blocker` | `Yes` | `1` | `error`<br>`suspicious` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
You should call ProceedWithCall function only in Extensions and only methods annotated with &AROUND.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
&AtClient
Procedure Test()
    ProceedWithCall(); // there is error    
EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Extensions. Functionality -> Modules](https://its.1c.ru/db/pubextensions#content:54:1)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:WrongUseFunctionProceedWithCall-off
// BSLLS:WrongUseFunctionProceedWithCall-on
```

### Parameter for config

```json
"WrongUseFunctionProceedWithCall": false
```
