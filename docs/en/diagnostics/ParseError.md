# Source code parse error

Ошибка возникает если исходный код модуля написан с нарушением синтаксиса языка или при
неправильном использовании инструкций препроцессора.

3. Do not split separate grammatical structures, expressions, declarations and procedures and functions calls by the preprocessor instructions and regions.

Incorrect:

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

Reference: [Standard: #439 Use of compilation and preprocessor directives](https://its.1c.ru/db/v8std#content:439:hdoc)
