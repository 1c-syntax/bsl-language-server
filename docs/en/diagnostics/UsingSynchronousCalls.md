# Using synchronous calls (UsingSynchronousCalls)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When developing configurations designed to work in the web client, it is forbidden to use modal forms and dialogs and synchronous calls. Otherwise, the configuration will be inoperative in a number of web browsers, since modal windows are not part of the web development standard, and asynchronous tools are required to ensure user interaction.

### Diagnostic restrictions

Currently, **only the use of global context methods** is diagnosed.

Method list:

|Russian variant|English variant|
| :-- | :-- |
|ВОПРОС|DOQUERYBOX|
|ОТКРЫТЬФОРМУМОДАЛЬНО|OPENFORMMODAL|
|ОТКРЫТЬЗНАЧЕНИЕ|OPENVALUE|
|ПРЕДУПРЕЖДЕНИЕ|DOMESSAGEBOX|
|ВВЕСТИДАТУ|INPUTDATE|
|ВВЕСТИЗНАЧЕНИЕ|INPUTVALUE|
|ВВЕСТИСТРОКУ|INPUTSTRING|
|ВВЕСТИЧИСЛО|INPUTNUMBER|
|УСТАНОВИТЬВНЕШНЮЮКОМПОНЕНТУ|INSTALLADDIN|
|УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ|INSTALLFILESYSTEMEXTENSION|
|УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ|INSTALLCRYPTOEXTENSION|
|ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ|ATTACHCRYPTOEXTENSION|
|ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ|ATTACHFILESYSTEMEXTENSION|
|ПОМЕСТИТЬФАЙЛ|PUTFILE|
|КОПИРОВАТЬФАЙЛ|FILECOPY|
|ПЕРЕМЕСТИТЬФАЙЛ|MOVEFILE|
|НАЙТИФАЙЛЫ|FINDFILES|
|УДАЛИТЬФАЙЛЫ|DELETEFILES|
|СОЗДАТЬКАТАЛОГ|CREATEDIRECTORY|
|КАТАЛОГВРЕМЕННЫХФАЙЛОВ|TEMPFILESDIR|
|КАТАЛОГДОКУМЕНТОВ|DOCUMENTSDIR|
|РАБОЧИЙКАТАЛОГДАННЫХПОЛЬЗОВАТЕЛЯ|USERDATAWORKDIR|
|ПОЛУЧИТЬФАЙЛЫ|GETFILES|ПОМЕСТИТЬФАЙЛЫ|PUTFILES|
|ЗАПРОСИТЬРАЗРЕШЕНИЕПОЛЬЗОВАТЕЛЯ|REQUESTUSERPERMISSION|
|ЗАПУСТИТЬПРИЛОЖЕНИЕ|RUNAPP|

## Sources

* [Limit on the use of modal windows and synchronous calls (RU)](https://its.1c.ru/db/v8std/content/703/hdoc/)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* [Correspondence of synchronous methods to asynchronous analogues (RU)](https://its.1c.ru/db/v838doc#bookmark:dev:TI000000438)
* [Asynchronous calls to extensions and external components (RU)](http://v8.1c.ru/o7/201412async/index.htm)
