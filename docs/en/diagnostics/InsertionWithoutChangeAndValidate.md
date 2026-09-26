# Using #Insert without &ChangeAndValidate (InsertionWithoutChangeAndValidate)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

The `#Insert` / `#EndInsert` directive is used inside a method (procedure or function) that is not annotated with `&ChangeAndValidate`. This causes a module compilation error.

The `#Insert` and `#EndInsert` directives are intended for configuration extension code insertion. The platform only allows them in methods annotated with `&ChangeAndValidate`, which explicitly declare the intent to modify the code of the extended object.

## Examples

### Incorrect

```bsl
Procedure AddAttribute()

    #Insert
    ... // insertion code
    #EndInsert

EndProcedure
```

### Correct

```bsl
&ChangeAndValidate
Procedure AddAttribute()

    #Insert
    ... // insertion code
    #EndInsert

EndProcedure
```

## See also

- [Standard 455: Module structure](https://its.1c.ru/db/v8std/content/455/hdoc)
