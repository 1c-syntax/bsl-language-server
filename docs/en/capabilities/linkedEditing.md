# Linked editing

Editing the declaration of a local symbol (variable, parameter) updates all of its occurrences in the module at once — without invoking rename.

**Shortcut:** `automatic`

[← All features](index.md)

## Linked editing of a local variable's occurrences

The cursor is placed inside the declaration of the local variable `Счётчик` and characters are appended to its name. Without invoking rename, all occurrences of the variable in the module change simultaneously as you type.

![linkedEditing-01](https://github.com/user-attachments/assets/6b524c10-1ee3-47db-8448-203158a24641)

## Linked editing of a method parameter

The cursor is placed inside the method parameter name `Заказ` and characters are appended to it. Without invoking rename, all occurrences of the parameter in the method body change simultaneously as you type.

![linkedEditing-02](https://github.com/user-attachments/assets/707221ea-def3-4b4f-848d-dce03288b8b3)
