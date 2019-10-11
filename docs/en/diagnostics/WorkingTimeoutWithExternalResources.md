# Timeouts working with external resources

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

##Ссылка на источники
[Таймауты при работе с внешними ресурсами](https://its.1c.ru/db/v8std#content:748:hdoc)