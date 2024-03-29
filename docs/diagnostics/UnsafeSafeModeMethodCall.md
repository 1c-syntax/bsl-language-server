# Небезопасное использование функции БезопасныйРежим() (UnsafeSafeModeMethodCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
В "1С:Предприятии 8.3" метод глобального контекста БезопасныйРежим() возвращает тип Строка, 
если безопасный режим был установлен с указанием имени профиля безопасности.

Использования метода БезопасныйРежим(),
 в которых результат неявно преобразовывается к типу Булево является небезопасным, 
 необходимо исправить на код с явным сравнением результата со значением Ложь. 
 Таким образом, при установленном профиле безопасности код будет выполняться так же, как и в безопасном режиме.
## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Неправильно:
```bsl
Если БезопасныйРежим() Тогда
     // Логика выполнения в безопасном режиме...
КонецЕсли;

Если Не БезопасныйРежим() Тогда
     // Логика выполнения в небезопасном режиме...
КонецЕсли;
```
Правильно:
```bsl
Если БезопасныйРежим() <> Ложь Тогда
    // Логика выполнения в безопасном режиме...
КонецЕсли
```
## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Источник: [Тип значения, возвращаемый методом "БезопасныйРежим()](https://its.1c.ru/db/metod8dev#content:5293:hdoc:izmenenie_bezopasnyjrezhim)
