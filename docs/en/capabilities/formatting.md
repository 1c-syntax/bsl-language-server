# Formatting

Format the whole document, a selection, or on-the-fly while typing (indentation, keyword casing).

**Shortcut:** `Shift+Alt+F`

[← All features](index.md)

## Document formatting

In `messy.bsl`, which holds unformatted code with no indentation, the user runs `Format Document`. The whole module is reformatted: nested indentation is added for the procedure body, the loop and the condition, and keywords are normalized to a consistent case.

![formatting-01](https://github.com/user-attachments/assets/cb74acf8-e7dd-4d1a-992f-a923708682db)

## On-type formatting (indentation as you type)

The user types a procedure with nested `Если`/`Иначе` line by line, pressing `Enter` after each. On-type formatting applies indentation on the fly, so every new nesting level immediately gets the correct offset.

![formatting-02-ontype](https://github.com/user-attachments/assets/f555c49a-7b99-4d28-a8ed-42935afe6427)

## Format selection (ranges formatting)

In `range.bsl` the user selects several lines inside the `Если` block and runs `Format Selection`. Only the selected fragment is reformatted (it gets proper indentation), while the rest of the procedure's code is left untouched.

![formatting-03-range](https://github.com/user-attachments/assets/92a6b1c1-1204-42c7-86b2-6e7ff5d48543)

---

[← Back: Code actions / Quick fixes](codeAction.md)  ·  [Next: Rename →](rename.md)
