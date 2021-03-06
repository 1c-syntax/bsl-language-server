# Выполнение запроса в цикле (CreateQueryInCycle)

|   Тип    |    Поддерживаются<br>языки    |  Важность   |    Включена<br>по умолчанию    |    Время на<br>исправление (мин)    |     Теги      |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:-------------:|
| `Ошибка` |         `BSL`<br>`OS`         | `Критичный` |              `Да`              |                `20`                 | `performance` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

Исполнение запроса в цикле.

## Примеры

Неправильно

```bsl

СписокДокументов = Новый Массив;    
СуммаДокументов = 0;
Для индекс = 0 По СписокДокументов.ВГраница() Цикл
	Запрос = Новый Запрос;
	Запрос.Текст = 
		"ВЫБРАТЬ
		|	ПоступлениеТоваровУслуг.СуммаДокумента
		|ИЗ
		|	Документ.ПоступлениеТоваровУслуг КАК ПоступлениеТоваровУслуг
		|ГДЕ
		|	ПоступлениеТоваровУслуг.Ссылка = &Ссылка";
	
	Запрос.УстановитьПараметр("Ссылка", СписокДокументов[индекс]);
	
	РезультатЗапроса = Запрос.Выполнить();

	ВыборкаДетальныеЗаписи = РезультатЗапроса.Выбрать();

	Пока ВыборкаДетальныеЗаписи.Следующий() Цикл
		СуммаДокументов = СуммаДокументов + ВыборкаДетальныеЗаписи.СуммаДокумента;
	КонецЦикла;
КонецЦикла;


```

Правильно

```bsl
СписокДокументов = Новый Массив;    
СуммаДокументов = 0;

Запрос = Новый Запрос;
Запрос.Текст = 
	"ВЫБРАТЬ
	|	СУММА(ПоступлениеТоваровУслуг.СуммаДокумента) КАК СуммаДокумента
	|ИЗ
	|	Документ.ПоступлениеТоваровУслуг КАК ПоступлениеТоваровУслуг
	|ГДЕ
	|	ПоступлениеТоваровУслуг.Ссылка В(&СписокДокументов)";

Запрос.УстановитьПараметр("Ссылка", СписокДокументов);

РезультатЗапроса = Запрос.Выполнить();

ВыборкаДетальныеЗаписи = РезультатЗапроса.Выбрать();

Пока ВыборкаДетальныеЗаписи.Следующий() Цикл
	СуммаДокументов = ВыборкаДетальныеЗаписи.СуммаДокумента;
КонецЦикла;

```

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Экранирование кода

```bsl
// BSLLS:CreateQueryInCycle-off
// BSLLS:CreateQueryInCycle-on
```

### Параметр конфигурационного файла

```json
"CreateQueryInCycle": false
```
