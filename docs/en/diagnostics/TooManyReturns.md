# Methods should not have too many return statements (TooManyReturns)

 |     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |      Tags       |
 |:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------:|
 | `Code smell` | `BSL`<br>`OS` | `Minor`  |             `No`              |          `20`           | `brainoverload` |

## Parameters

 |       Name        |   Type    | Description                                    | Default value |
 |:-----------------:|:---------:|:---------------------------------------------- |:-------------:|
 | `maxReturnsCount` | `Integer` | `Maximum allowed return statements per method` |      `3`      | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

A large number of returns in a method (procedure or function) increases its complexity and reduces performance and perception.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Bad method example

```bsl
Function Example(Condition)
     If Condition = 1 Then
         Return "Check passed";
     ElsIf Condition = 2 Then
         ExecuteSomething();
         Return "Check not passed";
     ElsIf Condition > 7 Then
         Если Validate(Contidtion) Then
             Return "Check passed";
         Else
             Return "Check not passed";
         EndIf;
     EndIf;
     Return "";
EndFunction
```

## Sources

* [Why Many Return Statements Are a Bad Idea in OOP](https://www.yegor256.com/2015/08/18/multiple-return-statements-in-oop.html)
* [JAVA: Methods should not have too many return statements](https://rules.sonarsource.com/java/RSPEC-1142)
* [Why fast return is so important?](https://habr.com/ru/post/348074/)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:TooManyReturns-off
// BSLLS:TooManyReturns-on
```

### Parameter for config

```json
"TooManyReturns": {
    "maxReturnsCount": 3
}
```
