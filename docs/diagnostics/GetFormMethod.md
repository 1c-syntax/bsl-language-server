# Использование метода ПолучитьФорму (GetFormMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Для открытия форм следует применять метод глобального контекста ОткрытьФорму (при использовании версии платформы 1С:Предприятие 8.2 и более ранних версий - также ОткрытьФормуМодально). 
Применение альтернативного способа, с получением формы и ее последующим открытием с помощью метода ПолучитьФорму, не рекомендуется.
## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
Процедура Тест()
    Док=Документы.ЗаявкаНаОперацию.СоздатьДокумент();
    Форма=Док.ПолучитьФорму("ФормаДокумента"); // Срабатывание здесь
КонецПроцедуры
```
```bsl
Процедура Тест2()
    ФормаРедактора = ПолучитьФорму("Обработка.УниверсальныйРедактор.Форма");
КонецПроцедуры
```

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Источник: [Стандарты разработки](https://its.1c.ru/db/v8std/content/404/hdoc)