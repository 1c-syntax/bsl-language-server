# Using modal windows

Modal windows and pop-ups are considered bad taste. Users are accustomed to working "in one window." When developing configurations designed to work in the web client, it is forbidden to use modal windows and dialogs. Otherwise, the configuration will be inoperative in a number of web browsers, since modal windows are not part of the web development standard.

```bsl
// Пример "Плохо"
Предупреждение(НСтр("ru = 'Выберите документ!'; en = 'Select a document!'"), 10);

// Пример "Хорошо"
ПоказатьПредупреждение(, НСтр("ru = 'Выберите документ!'; en = 'Select a document!'"), 10);
```

Source: [Limit on the use of modal windows and synchronous calls (RU)](https://its.1c.ru/db/v8std/content/703/hdoc/)

Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)

## Diagnostic restrictions

Currently, **only the use of global context methods** is diagnosed.

Method list:

Russian variant | English variant
--- | ---
ВОПРОС | DOQUERYBOX
ОТКРЫТЬФОРМУМОДАЛЬНО | OPENFORMMODAL
ОТКРЫТЬЗНАЧЕНИЕ | OPENVALUE
ПРЕДУПРЕЖДЕНИЕ | DOMESSAGEBOX
ВВЕСТИДАТУ | INPUTDATE
ВВЕСТИЗНАЧЕНИЕ | INPUTVALUE
ВВЕСТИСТРОКУ | INPUTSTRING
ВВЕСТИЧИСЛО | INPUTNUMBER
УСТАНОВИТЬВНЕШНЮЮКОМПОНЕНТУ | INSTALLADDIN
УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ | INSTALLFILESYSTEMEXTENSION
УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ | INSTALLCRYPTOEXTENSION
ПОМЕСТИТЬФАЙЛ | PUTFILE
