# Передача параметров между клиентом и сервером (TransferringParametersBetweenClientAndServer)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
При передаче управления с клиента на сервер (и обратно) всегда передаются копии параметров.

- При вызове серверной процедуры или функции с клиента происходит создание копии фактического параметра и передача этой копии на сторону сервера.
- При возврате управления с сервера на клиента также происходит создание копии формального параметра (с которым происходила работы в вызванной процедуре или функции) для передачи обратно на клиента.

Если формальный параметр указан с модификатором Знач, то значение параметра будет передаваться только при вызове процедуры или функции и не будет передаваться обратно при возврате управления на клиента.

Возможные сценарии:

- Если из клиентского метода в серверный метод без модификатора Знач передается структура со вложенными структурами, и параметр не меняется внутри серверного метода, в этом случае при возврате управления от сервера будет передана копия этой структуры со всеми ее вложениями. 
- В случае передачи плоской коллекции, которая не изменяется, например, массив, копия этой коллекции также зря будет возвращаться с сервера на клиент. 

В итоге отсутствие модификатора Знач при клиент-серверном взаимодействии может привести к ухудшению производительности и выполнению лишней\ненужной нагрузки как клиентом, так и сервером.

Текущее правило находит серверные методы, выполняемые из клиентских методов, и выдает замечания на параметры без модификатора Знач, для которых не выполняется установка значения.

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
1. Пример с передачей параметров с клиента на сервер без "Знач" и со "Знач"
```bsl
&НаСервереБезКонтекста
Процедура ПередачаПараметровНаСервер(Парам1, Знач ПарамСоЗнач, Коллекция, Знач КоллекцияСоЗнач)
	Парам1 = "Изменено1";	
	ПарамСоЗнач = "Изменено2";
	Коллекция.Вставить("Ключ1", "Изменено1");
	КоллекцияСоЗнач.Вставить("Ключ2", "Изменено2");
КонецПроцедуры

&НаКлиенте
Процедура ПередачаПараметров(Команда)
	Парам1 = "Исходное1";	
	ПарамСоЗнач = "Исходное2";
	Коллекция = Новый Структура("Ключ1", "Исходное1");
	КоллекцияСоЗнач = Новый Структура("Ключ2", "Исходное2");
	
	ПередачаПараметровНаСервер(Парам1, ПарамСоЗнач, Коллекция, КоллекцияСоЗнач);
	
	Шаблон = "после сервера %1 = <%2>";
	Сообщить(СтрШаблон(Шаблон, "Парам1", Парам1));
	Сообщить(СтрШаблон(Шаблон, "ПарамСоЗнач", ПарамСоЗнач));
	Сообщить(СтрШаблон(Шаблон, "Коллекция.Ключ1", Коллекция.Ключ1));
	Сообщить(СтрШаблон(Шаблон, "КоллекцияСоЗнач.Ключ2", КоллекцияСоЗнач.Ключ2));
КонецПроцедуры
```
Этот код при выполнении покажет следующий результат
```
после сервера Парам1 = <Изменено1>
после сервера ПарамСоЗнач = <Исходное2>
после сервера Коллекция.Ключ1 = <Изменено1>
после сервера КоллекцияСоЗнач.Ключ2 = <Исходное2>
```
Видно, что все параметры, передаваемые через Знач, после выполнения не меняют свои значения, в т.ч. и значения внутри коллекций.

2. Пример неточной передачи параметров
```bsl
&НаКлиенте
Процедура ГруппыПользователейПеретаскиваниеЗавершение(Ответ, ДополнительныеПараметры) Экспорт
	
	Если Ответ = КодВозвратаДиалога.Нет Тогда
		Возврат;
	КонецЕсли;
	
	СообщениеПользователю = ПеремещениеПользователяВНовуюГруппу(
		ДополнительныеПараметры.ПараметрыПеретаскивания,
		ДополнительныеПараметры.Строка,
		ДополнительныеПараметры.Перемещение);
	
КонецПроцедуры

// входные параметры МассивПользователей и остальные параметры не меняются 
// и поэтому нет смысла дополнительно возвращать их с сервера
&НаСервере
Функция ПеремещениеПользователяВНовуюГруппу(МассивПользователей, НоваяГруппаВладелец, Перемещение)
	
	Если НоваяГруппаВладелец = Неопределено Тогда
		Возврат Неопределено;
	КонецЕсли;
	
	ТекущаяГруппаВладелец = Элементы.ГруппыПользователей.ТекущаяСтрока;
	СообщениеПользователю = ПользователиСлужебный.ПеремещениеПользователяВНовуюГруппу(
		МассивПользователей, ТекущаяГруппаВладелец, НоваяГруппаВладелец, Перемещение);
	
	Элементы.ПользователиСписок.Обновить();
	Элементы.ГруппыПользователей.Обновить();
	
	Возврат СообщениеПользователю;
	
КонецФункции
```

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Статья на 1С:ИТС - Вызов с передачей управления с клиента на сервер](https://its.1c.ru/db/v8318doc#bookmark:dev:TI000000153)
