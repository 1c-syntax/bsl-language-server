# Выражение должно заканчиваться символом ";" (SemicolonPresence)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

В текстах программных процедур и функций операторы между собой обязательно стоит разделять точкой с запятой (";"). Конец строки не является признаком конца оператора.
Не смотря на то, что в некоторых случаях платформа позволяет опускать точку с запятой, необходимо указывать этот символ всегда, явно указывая завершение оператора.

**ПРИМЕЧАНИЕ**: Ключевые слова `Процедура`, `КонецПроцедуры`, `Функция`, `КонецФункции` являются не операторами, а операторными скобками, поэтому **НЕ** должны заканчиваться точкой с запятой (это может приводить к ошибкам выполнения модуля).
