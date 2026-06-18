# Quick documentation (hover)

Hovering over a symbol shows its signature, type and the description from doc comments.

**Shortcut:** `mouse hover`

[← All features](index.md)

## Quick documentation on hover

The cursor hovers over a method call in the module code. The popup shows the method's signature and its description from the doc comment.

![hover-01-method](https://github.com/user-attachments/assets/cff69a27-5b22-45a0-abac-1681ba649cd1)

## Hover on a variable

The cursor hovers over a variable in the module code. The popup shows the variable name and the inferred type of its value.

![hover-02-variable](https://github.com/user-attachments/assets/6582bfce-5c67-4e00-9550-c158763d8a44)

## Hover on a platform method

The cursor hovers over the platform function `СтрДлина` in the code. The popup shows its signature, return type and description from the platform documentation.

![hover-03-platform-method](https://github.com/user-attachments/assets/49aea1d4-7b36-4469-b969-f97fac58caf5)

## Hover on a OneScript class constructor

The cursor hovers over a OneScript class constructor in a `Новый` expression. The popup shows the constructor's signature and the class description.

![hover-04-oscript-constructor](https://github.com/user-attachments/assets/2fc767ad-4ed1-48d6-9145-cdad6602b8ed)

## Hover on a keyword

The cursor hovers over a language keyword in the module code. The popup shows the description of that keyword.

![hover-05-keyword](https://github.com/user-attachments/assets/c792e76e-9eb6-4a6c-87fe-83573a8dd7aa)

## Variable type inferred from a structure field

The cursor hovers over the variable `НомерЗаказа`, assigned from `Заказ.Номер`. The popup shows the type `Строка`, inferred from the structure field's description in the BSLDoc comment.

![hover-06-struct-field](https://github.com/user-attachments/assets/c9ca2085-c7f2-4f5c-b992-5300f43002a7)

## Variable type inferred from an expression

The cursor hovers over the variable `Количество`, assigned the result of `Массив.Количество()`. The popup shows the type `Число`, inferred from that expression.

![hover-07-inferred-type](https://github.com/user-attachments/assets/e6f4ff0d-8a56-4027-836e-05d50ce7a5b5)

## Hover on a common module

The cursor hovers over a common module name in the code. The popup shows the common module's name and its description.

![hover-08-common-module](https://github.com/user-attachments/assets/52795e2a-612e-4e9a-aa38-02122f54f097)

## OneScript: hover on an annotation

The cursor hovers over a usage of the `&Кэшировать` annotation in a OneScript module. The popup shows the description of that annotation.

![hover-09-os-annotation](https://github.com/user-attachments/assets/1dea5538-ac4e-416a-9fa4-bea6574ff61b)

---

[← Back: Find references](references.md)  ·  [Next: Signature help →](signatureHelp.md)
