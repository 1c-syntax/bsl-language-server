# Timeouts working with external resources

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
--- | --- | --- | --- | --- | ---
`Error` | `BSL`<br>`OS` | `Critical` | `Yes` | `5` | `unpredictable`<br>`standard`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

When working with external resources using the objects WSDefinitions, WSProxy, HTTPConnection, FTPConnection, InternetMailProfile, a timeout should be set - the time limit for the operation to be completed. Otherwise, as a result of endless waiting, the program will freeze or some of the functionality of the program will become unavailable.

Установка таймаута является защитой от целого ряда внешних факторов:

- нестабильного подключения к Интернету, когда регулярно происходит прерывание связи, и система не может получить цельный ответ сервера, к которому выполняется подключение;
- при включенных антивирусных программах или при неправильных настройках брандмауэра;
- неправильной настройки прокси-сервера;
- ненадежной работы веб-сервера из-за возросшей нагрузки или некорректной работы скриптов.

## Examples

Incorrect:

```bsl
HTTPConnection = New HTTPConnection("zabbix.localhost", 80);
```

или

```bsl
FTPConnection = New FTPConnection(Server, Port, Login, Password, Proxy, PassiveMode);
```

Correct:

```bsl
HTTPConnection = New HTTPConnection("zabbix.localhost", 80,,,, 1);
```

или

```bsl
HTTPConnection = New HTTPConnection("zabbix.localhost", 80);
HTTPConnection.Timeout = 1;
```

## Reference

- [Timeouts when working with external resources](https://its.1c.ru/db/v8std#content:748:hdoc)
