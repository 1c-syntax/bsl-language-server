# Methods should not have too many return statements (TooManyReturns)

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
