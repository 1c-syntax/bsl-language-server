# Using of the deprecated method "CurrentDate" (DeprecatedCurrentDate)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The "CurrentDate" function has been deprecated. It is recommended to use the "CurrentSessionDate" function.

## Examples
Incorrect:

```bsl
OperationDate = CurrentDate();
```


Correct:

```bsl
OperationDate = CurrentSessionDate();
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* Reference: [Metadata creation and change. Work in different timezones (RU)](https://its.1c.ru/db/v8std/content/643/hdoc)
