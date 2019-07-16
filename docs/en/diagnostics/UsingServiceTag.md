# Using service tags

The diagnostic finds use of service tags in comments. Tags list:

- TODO
- FIXME
- !!
- @
- MRG
- ОТЛАДКА
- ДЛЯ ОТЛАДКИ
- КОНСТРУКТОР_ЗАПРОСА_С_ОБРАБОТКОЙ_РЕЗУЛЬТАТА
- КОНСТРУКТОР_ДВИЖЕНИЙ_РЕГИСТРОВ
- КОНСТРУКТОР_ПЕЧАТИ
- КОНСТРУКТОР_ВВОДА_НА_ОСНОВАНИИ

Tags list can be extended via options.

## Parameters

- `serviceTags` - `String` - keyword for search. Bu default : "todo|fixme|!!|mrg|@|отладка|debug|для\s*отладки|(\{\{|\}\})КОНСТРУКТОР_|(\{\{|\}\})MRG".
