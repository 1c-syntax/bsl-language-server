# Code completion

Context-aware suggestions as you type: global functions, object methods and properties (with type inference), types after the `New` operator, keywords and local variables.

**Shortcut:** `Ctrl+Space`

[← All features](index.md)

## Global functions by prefix

After typing `Сообщ` inside the procedure, prefix-based completion is triggered. The popup lists global functions starting with that string, including `Сообщить`.

![01-global-functions](https://github.com/user-attachments/assets/2b736119-f6b8-4b38-b17c-0d887e9f0718)

## Object methods via dot (type inferred from New)

The type of `Список` is inferred from the `Новый Массив` assignment, and typing `Список.` pops up the array's methods. Typing `Доб` filters the list down to the `Добавить` method.

![02-method-via-dot](https://github.com/user-attachments/assets/4b8e934e-ea94-44d4-8db5-c20778605e9e)

## Types after the New operator

After typing `Новый Масс`, type completion is triggered. The list offers classes starting with `Масс` (e.g. `Массив`), and the highlighted class shows its constructor signature with the number of syntax variants.

![03-new-object](https://github.com/user-attachments/assets/1ca919e3-5456-41bc-8035-1a5cc30069f6)

## Local variable completion

Two local variables `КоличествоСтрок` and `СуммаПродаж` are declared in the procedure, and typing `Сумм` triggers completion. The popup offers the local variable `СуммаПродаж`.

![comp-04-local-variable](https://github.com/user-attachments/assets/5253a459-d914-4057-a19c-ed1b8bd111f9)

## Module procedures and functions

After typing `Вычисл` in the procedure body, completion of the current module's methods is triggered. The list offers the `ВычислитьИтог` function with its signature and description.

![comp-05-module-method](https://github.com/user-attachments/assets/f61d316c-50ce-41d9-9e11-7f188d8a0594)

## Collection members (ValueTable)

The type of `Таблица` is inferred from `Новый ТаблицаЗначений`, and typing `Таблица.` pops up its members. The popup offers the value table's properties and methods such as `Колонки` and `Добавить`.

![comp-06-collection-members](https://github.com/user-attachments/assets/5a3e6b04-0cf1-498d-923c-ef6e1d29a48a)

## Common module methods

After typing `ОбщегоНазначения.Знач`, completion of the common module's members is triggered. The list offers the module's exported methods (e.g. `ЗначениеРеквизитаОбъекта`) with their signatures and return types.

![comp-07-common-module-members](https://github.com/user-attachments/assets/ad573114-6e82-4c8d-a0eb-8ba2e7ae292c)

## Metadata collections (Catalogs.)

After typing `Справочники.`, completion of the metadata object collection is triggered. The list offers the configuration's catalogs along with manager methods.

![comp-08-metadata-collection](https://github.com/user-attachments/assets/c9a28547-6609-4233-8477-2b9756f1d99d)

## Enumeration manager members

After typing `Перечисления.ВажностьПроблемыУчета.`, completion of the enumeration manager's members is triggered. The list offers the enumeration's own values together with the manager's methods.

![comp-09-enum-values](https://github.com/user-attachments/assets/2cc322b5-126b-4186-9251-3c0a73fa1a27)

## OneScript: library classes after New (with #Использовать)

In a OneScript module with `#Использовать demolib`, typing `Новый Форматир` triggers completion of the imported library's classes. The list offers the library classes starting with that prefix.

![comp-10-os-new-library-class](https://github.com/user-attachments/assets/e1300f2e-048e-4256-923f-e85ffb809840)

## OneScript: library module methods

In a OneScript module with `#Использовать demolib`, typing `СтроковыеУтилиты.` triggers completion of the library module's members. The list offers the exported methods of the `СтроковыеУтилиты` module.

![comp-11-os-module-members](https://github.com/user-attachments/assets/2222c3f6-7402-446b-88ef-c5585077c4b8)

## OneScript: library classes/modules appear only with #Использовать (or within the same package)

First, completion after `Новый Форматир` without `#Использовать` offers nothing from the library, then the `#Использовать demolib` directive is added at the top of the file. After that the same input triggers completion of the library's classes and modules.

![comp-12-os-use-required](https://github.com/user-attachments/assets/5405a03d-20df-40cc-b73b-8acf35b512ca)

## Structure fields from the parameter description

The `Заказ` parameter is described in a BSLDoc comment as a structure with fields `Номер` and `Сумма`, and typing `Заказ.` triggers completion. The list offers the structure fields with the types and descriptions taken from the comment.

![comp-13-struct-doc](https://github.com/user-attachments/assets/4df58953-7a5b-4efa-b8ec-7df3f3018d31)

## Structure fields added via Insert()

Fields are added to the `Запись` structure via `Вставить("Имя", …)` and `Вставить("Возраст", …)`, and typing `Запись.` triggers completion. The list offers the fields `Имя` and `Возраст` inferred from those calls.

![comp-14-struct-insert](https://github.com/user-attachments/assets/88fcb630-0b5a-4397-9e80-05b9b9894017)

## Structure fields: description + dynamic Insert()

The `Заказ` structure is described in a BSLDoc comment with fields `Номер` and `Сумма`, and is extended in the body by `Вставить("Скидка", 0)`. After typing `Заказ.`, completion offers both the documented fields and the dynamically added `Скидка` field.

![comp-15-struct-combo](https://github.com/user-attachments/assets/9b742080-4430-4c9e-b409-9249214dc694)

## ValueTable row columns

Inside the `Для Каждого Строка Из Таблица` loop, typing `Строка.` triggers completion of the value table row's columns. The list offers the columns `Артикул` and `Цена` added earlier via `Колонки.Добавить`.

![comp-16-valuetable-columns](https://github.com/user-attachments/assets/c13e99a9-e7ef-4251-bc85-1bcbac3b2406)

## Predefined catalog items

After typing `Справочники.ВидыКонтактнойИнформации.`, completion of the catalog manager's members is triggered. The list offers the catalog's predefined items alongside the manager's methods.

![comp-17-predefined-items](https://github.com/user-attachments/assets/0cbcfb5b-c81f-4fdb-b1b5-1dec272ca7c7)

## Fuzzy completion (substring, not just prefix)

In an .os file, after `СтроковыеУтилиты.` the substring `Форм` is typed — taken from the middle of the name, not a prefix. Fuzzy matching finds the real module method `ВыполнитьФорматирование` (matched letters highlighted), even though the name does not start with `Форм`.

![comp-18-fuzzy](https://github.com/user-attachments/assets/1d1c4e2b-d7a6-4596-86bc-03489b287608)
