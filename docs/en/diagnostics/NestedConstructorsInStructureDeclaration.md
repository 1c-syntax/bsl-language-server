# Using constructors with parameters when declaring a structure

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
--- | --- | --- | --- | --- | ---
`Code smell` | `BSL`<br>`OS` | `Minor` | `Yes` | `10` | `badpractice`<br>`brainoverload`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

It is not recommended to use constructors of other objects in the structure constructor if these constructors accept parameters. In particular, in the constructor of one structure it is not recommended to create other structures with the declaration of property values.

## Examples

Incorrect:

```bsl
НоменклатураСервер.ЗаполнитьСлужебныеРеквизитыПоНоменклатуреВКоллекции(
  Объект.Товары,
  Новый Структура(
  "ЗаполнитьПризнакХарактеристикиИспользуются,
  |ЗаполнитьПризнакТипНоменклатуры, ЗаполнитьПризнакВариантОформленияПродажи",
   Новый Структура("Номенклатура", "ХарактеристикиИспользуются"),
   Новый Структура("Номенклатура", "ТипНоменклатуры"),
   Новый Структура("Номенклатура", "ВариантОформленияПродажи")
  )
 );
```

Correct:

```bsl
ПараметрыЗаполненияРеквизитов = Новый Структура;
ПараметрыЗаполненияРеквизитов.Вставить("ЗаполнитьПризнакХарактеристикиИспользуются",
                                                          Новый Структура("Номенклатура", "ХарактеристикиИспользуются"));
ПараметрыЗаполненияРеквизитов.Вставить("ЗаполнитьПризнакТипНоменклатуры",
                                                          Новый Структура("Номенклатура", "ТипНоменклатуры"));
НоменклатураСервер.ЗаполнитьСлужебныеРеквизитыПоНоменклатуреВКоллекции(Объект.Товары, 
                                                          ПараметрыЗаполненияРеквизитов);
```
