# Signature help

While typing a method call, shows the parameter list and highlights the active parameter.

**Shortcut:** `Ctrl+Shift+Space`

[← All features](index.md)

## Parameter hints while calling

The user types a call to the local function `Сумма = Сложить(` up to the opening parenthesis. A signature panel pops up over the code listing the parameters of `Сложить`, with the active first parameter highlighted.

![signatureHelp-01](https://github.com/user-attachments/assets/35fb211e-d2b7-417e-b090-605d1a1d4dab)

## Signature help for a constructor (New Array)

The user types the constructor `Список = Новый Массив(` up to the opening parenthesis. A signature panel appears for the `Массив` constructor, listing its parameters with the active one highlighted.

![signatureHelp-02-variadic](https://github.com/user-attachments/assets/44e9ffa6-f435-41d1-8e36-f82dfa8d9078)

## Signature help: optional parameter

The user types a call to `Р = Форматировать(`, whose `Префикс = "[]"` parameter is optional. The signature panel shows both parameters, marking `Префикс` as optional (with its default value) and highlighting the active parameter.

![signatureHelp-03-optional](https://github.com/user-attachments/assets/8001f71e-a18e-4cb8-8f9b-918ce3a66e08)

## Signature help for a platform method

The user invokes the platform method `Список.Добавить(` on a variable of type `Массив`. The signature panel shows the parameters of the platform method `Добавить`, highlighting the active parameter.

![signatureHelp-04-platform](https://github.com/user-attachments/assets/e44c3a15-438a-499b-a5a7-c0f85337160d)

## Signature help inside a nested call

The user types a nested call `Сообщить(Сложить(10, ` and moves to the second argument of the inner function. The signature panel shows the parameters of the inner `Сложить` call and highlights the active second parameter.

![signatureHelp-05-nested](https://github.com/user-attachments/assets/c11a4158-f92b-4299-993a-64a0b446d1c5)

## Signature help across a call chain

The user types a call chain `Строка = Таблица.Скопировать().Найти(` and triggers signature help. The panel resolves the result type of `Скопировать()` and shows the parameters of `Найти`, highlighting the active parameter.

![signatureHelp-06-chained](https://github.com/user-attachments/assets/2e3c980d-1063-4d80-8364-b77ef72f878e)

## Method with a variable number of arguments (optional parameters)

The user types `Позиция = СтрНайти("abc-def", "-", ` and moves to the third argument of this variable-arity method. The signature panel lists all parameters of `СтрНайти`, including the optional ones, and highlights the active third parameter.

![signatureHelp-07-overload](https://github.com/user-attachments/assets/c61449e6-847a-445d-be7e-9447d8a8478b)

## OneScript: signature help for a library class constructor

In a OneScript file with `#Использовать demolib`, the user types `Отчет = Новый ФорматировщикОтчета(` and triggers signature help. The panel shows the constructor parameters of the library class `ФорматировщикОтчета`, highlighting the active parameter.

![signatureHelp-08-os-constructor](https://github.com/user-attachments/assets/77a32046-36d2-4958-ab85-d3645046ce86)

## OneScript: signature help for a library module method

In a OneScript file, the user calls the library module method `СтроковыеУтилиты.ВыполнитьФорматирование(` and triggers signature help. The panel shows the parameters of the module method `ВыполнитьФорматирование`, highlighting the active parameter.

![signatureHelp-09-os-module-method](https://github.com/user-attachments/assets/f48bb24f-7611-45d1-8308-36a9f1d50796)

## OneScript: signature help for an instance method

The user calls the instance method `Отчет.ДобавитьПоказатель(` on an object of class `ФорматировщикОтчета`. The signature panel resolves the instance type and shows the parameters of `ДобавитьПоказатель`, highlighting the active parameter.

![signatureHelp-10-os-instance-method](https://github.com/user-attachments/assets/cbea4b1d-1d01-4915-843c-d56c5ef710d6)
