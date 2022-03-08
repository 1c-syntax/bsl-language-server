# Export methods in command and general command modules (CommandModuleExportMethods)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

You should not place export methods in command and General command modules. You can't access these modules from code external to them, so export methods in these modules do not make sense.

## Sources

* [Source (RU)](https://its.1c.ru/db/v8std/content/544/hdoc)
