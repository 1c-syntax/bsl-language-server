# Missing spaces to the left or right of operators + - * / = % < > <> <= >=, and also to the right of , и ;

To improve code readability to the left and right of operators `+ - * / = % < > <> <= >=` there must be spaces.
Also, the space should be to the right of `,` и `;`

## Base parameters

Symbols (or groups of symbols) to check are set by three lists. Symbols inside the list are separated by space.

- **`listForCheckLeft`** - `String` - Symbols to check the space exists to the left of them.
    By default the list is empty.

- **`listForCheckRight`** - `String` - Symbols to check the space exists to the right of them.
    By default `, ;`

- **`listForCheckLeftAndRight`** - `String` - Symbols to check the space exists to the left and right of them.
    By default `+ - * / = % < > <> <= >=`

Incorrect

```bsl
Procedure Sum(Param1,Param2)
    If Param1=Param2 Then
        Sum=Price*Quantity;
    EndIf;
EndProcedure
```

Correct

```bsl
Procedure Sum(Param1, Param2)
    If Param1 = Param2 Then
        Sum = Price * Quantity;
    EndIf;
EndProcedure
```

###### Additional parameters

- **`checkSpaceToRightOfUnary`** - `Boolean` - Check space to the right of unary operator (+ и -).
    The parameter makes sense only in case the operator if listed in one of three base parameters. By default `false`

    If set to `false`

    ```bsl
    А = -B;     // Correct
    А = - B;    // Correct
    ```

    If set to `true`

    ```bsl
    А = -B;     // Incorrect
    А = - B;    // Correct
    ```

- **`allowMultipleCommas`** - `Boolean` - Allow multiple commas in a row.
    The parameter has sense only if `,` is listed in one of three base parameters. By default `false`

    If set to `false`

    ```bsl
    ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения,,,, Отказ);        // Incorrect
    ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения, , , , Отказ);     // Correct
    ```

    If set to `true`

    ```bsl
    ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения,,,, Отказ);        // Correct
    ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения, , , , Отказ);     // Correct
    ```
