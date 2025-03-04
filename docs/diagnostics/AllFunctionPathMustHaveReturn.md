# Все возможные пути выполнения функции должны содержать оператор Возврат (AllFunctionPathMustHaveReturn)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
Каждая функция в языке 1С имеет в самом конце неявный оператор "Возврат Неопределено". Если управление доходит до конца функции, то функция возвращает неопределено.

Как правило, это не является штатным функционированием, программист должен явно описать все возвращаемые значения функции. Однако, довольно легко пропустить ситуацию, при которой управление дойдет до строки КонецФункции и вернется непредусмотренное значение Неопределено.

Данная диагностика проверяет, что все возможные пути выполнения функции имеют явный оператор Возврат и функция не возвращает непредвиденных значений.

## Примеры

### Неправильно

```bsl
// если ставка заполнена, но не равна НДС20 и не равна НДС10 - вернется Неопределено
// это может быть, как запланированное поведение, 
// так и ошибка проверки прочих вариантов.
Функция ОпределитьСтавкуНДС(Знач Ставка)
    Если Ставка = Перечисления.СтавкиНДС.НДС20 Тогда
        Возврат 20;
    ИначеЕсли Ставка = Перечисления.СтавкиНДС.НДС10 Тогда
        Возврат 10;
    ИначеЕсли Не ЗначениеЗаполнено(Ставка) Тогда
        Возврат Константы.СтавкаНДСПоУмолчанию.Получить();
    КонецЕсли;
    
    // здесь будет неявный возврат Неопределено
КонецФункции
```

### Правильно

```
// явно указать намерение вернуть результат в конце функции.
Функция ОпределитьСтавкуНДС(Знач Ставка)
    Если Ставка = Перечисления.СтавкиНДС.НДС20 Тогда
        Возврат 20;
    ИначеЕсли Ставка = Перечисления.СтавкиНДС.НДС10 Тогда
        Возврат 10;
    ИначеЕсли Не ЗначениеЗаполнено(Ставка) Тогда
        Возврат Константы.СтавкаНДСПоУмолчанию.Получить();
    КонецЕсли;
    
    // Явно декларируем намерение вернуть Неопределено
    Возврат Неопределено;
КонецФункции
```

### Еще пример ошибочного кода:

```bsl
Функция СуммаСкидки(Знач КорзинаЗаказа)
    Если КорзинаЗаказа.Строки.Количество() > 10 Тогда
        Возврат Скидки.СкидкаНаКрупнуюКорзину(КорзинаЗаказа);
    ИначеЕсли КорзинаЗаказа.ЕстьКартаЛояльности Тогда
        // функция возвращает непредусмотренное значение Неопределено
        Скидки.СкидкаПоКартеЛояльности(КорзинаЗаказа);
    Иначе 
        Возврат 0;
    КонецЕсли;
КонецФункции
```
