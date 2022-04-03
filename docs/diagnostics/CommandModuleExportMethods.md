# Экспортные методы в модулях команд и общих команд (CommandModuleExportMethods)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

Не следует размещать экспортные методы в модулях команд и общих команд. К этим модулям нет возможности 
обращаться из внешнего по отношению к ним кода, поэтому экспортные методы в этих модулях не имеют смысла.

## Источники

* [Источник](https://its.1c.ru/db/v8std/content/544/hdoc)