# Частично локализованный текст используется в функции СтрШаблон (MultilingualStringUsingWithTemplate)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

НСтр в мультиязычной конфигурации имеет разные фрагменты для разных языков.
Если запустить сеанс под кодом языка, которого нет в строке передаваемой в NStr то она вернет пустую строку.
При совместном использовании с СтрШаблон возвращенная из НСтр пустая строка будет выброшено исключение.

## Источники

- [Требования по локализации](https://its.1c.ru/db/v8std/content/763/hdoc)
