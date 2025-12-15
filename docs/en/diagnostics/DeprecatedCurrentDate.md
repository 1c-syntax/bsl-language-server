# Using of the deprecated method "CurrentDate" (DeprecatedCurrentDate)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The configurations must be designed to work in conditions where the time zone on the server computer does not match the real time zone of the infobase users. For example, employees of a company from Vladivostok work with a server located in Moscow, and all operations in the system must be performed in local time (Vladivostok).

Such a work scenario is often in demand in client-server infobases and in applied solutions in the service model (SaaS).

In all server procedures and functions, instead of the CurrentDate function, which returns the server computer's date and time, you should use the CurrentSessionDate function, which converts the server's time to the user's session time zone.

In client code, using the CurrentDate function is also not allowed. This requirement is due to the fact that the current time calculated in the client and server code must not differ.

When using the Library of Standard Subsystems, it is recommended to use the DateSession function of the general module GeneralPurposeClient.

## Examples

### On the client
Incorrect:

```bsl
OperationDate = CurrentDate();
```

Correct:

```bsl
ДатаОперации = ОбщегоНазначенияКлиент.ДатаСеанса();
```

### On server

```bsl
OperationDate = CurrentDate();
```

Right:

```bsl
OperationDate = CurrentSessionDate();
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* Reference: [Metadata creation and change. Work in different timezones](https://its.1c.ru/db/v8std/content/643/hdoc)
