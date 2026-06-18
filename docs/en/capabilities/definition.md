# Go to definition

Jump from a usage to the declaration of a procedure, function, variable or method. Works within a module and across configuration modules.

**Shortcut:** `F12 / Ctrl+Click (peek: Alt+F12)`

[← All features](index.md)

## Переход к объявлению процедуры

The user places the cursor on the call `ВывестиПриветствие()` and presses F12. The editor jumps to the `Процедура ВывестиПриветствие()` declaration line in the same module.

![def-01-procedure](https://github.com/user-attachments/assets/f487ae04-2f31-4439-93ab-b3f2df72431a)

## Go to a function declaration

The user places the cursor on the call `Сложить(2, 3)` and presses F12. The editor jumps to the `Функция Сложить(Знач Первое, Знач Второе) Экспорт` declaration line in the same module.

![def-02-function](https://github.com/user-attachments/assets/3d738302-be92-4d49-843f-477ac9044aea)

## Go to a local variable declaration

The user places the cursor on a usage of the local variable `Счётчик` and presses F12. The editor jumps to its declaration `Перем Счётчик;` inside the same procedure.

![def-03-local-variable](https://github.com/user-attachments/assets/e28a3caa-d6cd-4b18-8d92-f175e393d0d9)

## Go to a module-level variable

The user places the cursor on a usage of the module-level variable `НастройкиМодуля` inside a procedure and presses F12. The editor jumps to its module-level declaration `Перем НастройкиМодуля Экспорт;`.

![def-05-module-variable](https://github.com/user-attachments/assets/1f0f1e83-70da-440a-ab0e-df842968e482)

## Go to another common module (cross-file)

The user places the cursor on the common module name in a qualified call and presses F12. The editor opens the `Module.bsl` of another common module and navigates to it across configuration files.

![def-06-cross-module](https://github.com/user-attachments/assets/03ef3960-7ee6-42c8-9b84-6f9175fa5a47)

## Peek Definition in place (Alt+F12)

The user places the cursor on the call `ВывестиПриветствие()` and presses Alt+F12 to peek the definition. The procedure's definition opens in an inline Peek window right below the call line, without navigating away.

![def-07-peek](https://github.com/user-attachments/assets/9e0a324c-e991-46a7-8fb8-421b3051abdb)

## Go to a common module method (cross-file)

The user places the cursor on the method name in a call to another common module and presses F12. The editor opens the target common module's `Module.bsl` and navigates to that method's declaration across files.

![def-08-common-module-method](https://github.com/user-attachments/assets/52b4fbdf-22a2-4298-8e1d-a09bda57db15)

## Go to a manager module method (cross-file)

The user places the cursor on a manager module method name in a call and presses F12. The editor opens the manager module of the corresponding metadata object and navigates to the method's declaration across files.

![def-09-manager-module-method](https://github.com/user-attachments/assets/f7a69d37-724a-4bcc-b603-12596e98dddd)

## Go to a OneScript module definition

In `demo.os`, the user places the cursor on the name of an imported OneScript module and presses F12. The editor opens that library module's file and navigates to it.

![def-10-oscript-module](https://github.com/user-attachments/assets/855e9a8a-d24f-4fdb-bc08-58f3372b9259)

## Go to a OneScript class without a constructor

In `demo.os`, the user places the cursor on the name of a OneScript class without an explicit constructor in a `Новый` expression and presses F12. The editor opens the class file and navigates to its definition.

![def-11-oscript-class-no-constructor](https://github.com/user-attachments/assets/b6a4311b-d91c-47a8-aee8-ed8a512dfcba)

## Go to a OneScript class constructor (explicit ПриСозданииОбъекта)

In `demo.os`, the user places the cursor on a OneScript class name in a `Новый` expression and presses F12. The editor opens the class file and navigates to its explicit `Процедура ПриСозданииОбъекта` constructor.

![def-12-oscript-class-with-constructor](https://github.com/user-attachments/assets/845d5902-509f-476c-af30-2211fe6ef565)

## Go to a OneScript module method

In `demo.os`, the user places the cursor on a method name in a OneScript module call and presses F12. The editor opens the module file and navigates to that method's declaration.

![def-13-oscript-module-method](https://github.com/user-attachments/assets/2c5146c4-0154-4b5b-be21-e37251cf9b0e)

## Go to a OneScript class instance method

In `demo.os`, the user places the cursor on a method called on a OneScript class instance and presses F12. The editor resolves the instance type, opens the class file and navigates to the method's declaration.

![def-14-oscript-instance-method](https://github.com/user-attachments/assets/5239534e-339e-44ad-ba31-e0dd5a08d56d)

## Go to a registered OneScript annotation

In `demo-annotation.os`, the user places the cursor on the name of a registered OneScript annotation and presses F12. The editor navigates to where that annotation is declared.

![def-15-oscript-annotation](https://github.com/user-attachments/assets/914c7491-7c46-456d-88ef-93b38abae27d)

## Go to a common module method via the ОбщийМодуль() result

The user works with a variable assigned the result of `ОбщийМодуль("...")`, places the cursor on a method called on it and presses F12. The editor resolves the common module from the `ОбщийМодуль()` result, opens its `Module.bsl` and navigates to the method's declaration.

![def-16-method-via-commonmodule-result](https://github.com/user-attachments/assets/13947cf8-f555-4573-8431-fb1e5b1c7ad2)
