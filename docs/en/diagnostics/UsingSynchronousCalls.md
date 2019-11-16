# Using synchronous calls

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
--- | --- | --- | --- | --- | ---
`Code smell` | `BSL` | `Major` | `No` | `15` | `standard`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

При разработке конфигураций, предназначенных для работы в веб-клиенте, запрещено использовать модальные формы и диалоги и синхронные вызовы. В противном случае, конфигурация окажется неработоспособной в ряде веб-браузеров, так как модальные окна не входят в стандарт веб-разработки, а для обеспечения взаимодействия с пользователем требуются асинхронные средства.

### Diagnostic restrictions

На данный момент диагностируется **только использование методов глобального контекста**.

Method list:

Rusian variant | English variant
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
ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ | ATTACHCRYPTOEXTENSION
ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ | ATTACHFILESYSTEMEXTENSION
ПОМЕСТИТЬФАЙЛ | PUTFILE
КОПИРОВАТЬФАЙЛ | FILECOPY
ПЕРЕМЕСТИТЬФАЙЛ | MOVEFILE
НАЙТИФАЙЛЫ | FINDFILES
УДАЛИТЬФАЙЛЫ | DELETEFILES
СОЗДАТЬКАТАЛОГ | CREATEDIRECTORY
КАТАЛОГВРЕМЕННЫХФАЙЛОВ | TEMPFILESDIR
КАТАЛОГДОКУМЕНТОВ | DOCUMENTSDIR
РАБОЧИЙКАТАЛОГДАННЫХПОЛЬЗОВАТЕЛЯ | USERDATAWORKDIR
ПОЛУЧИТЬФАЙЛЫ | GETFILES
ЗАПРОСИТЬРАЗРЕШЕНИЕПОЛЬЗОВАТЕЛЯ | REQUESTUSERPERMISSION
ЗАПУСТИТЬПРИЛОЖЕНИЕ | RUNAPP

## Источники

- [Ограничение на использование модальных окон и синхронных вызовов](https://its.1c.ru/db/v8std/content/703/hdoc/)
- [Opt out of modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
- [Correspondence of synchronous methods to asynchronous analogues (RU)](https://its.1c.ru/db/v838doc#bookmark:dev:TI000000438)
- [Asynchronous calls to extensions and external components (RU)](http://v8.1c.ru/o7/201412async/index.htm)
