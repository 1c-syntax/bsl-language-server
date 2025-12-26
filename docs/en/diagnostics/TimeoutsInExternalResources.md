# Timeouts working with external resources (TimeoutsInExternalResources)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When working with external resources using the WSDefinitions, WSProxy, HTTPConnection, FTPConnection there should be a time out - the time limit for the operation to be completed. Otherwise, as a result of endless waiting, the program will freeze or some of the functionality of the program will become unavailable.  
For the InternetMailProfile the platform sets the default timeout value to 30 seconds, but despite this, it is worth explicitly specifying the timeout value when using it.

Setting a timeout protects against external factors:

* unstable Internet connection, when the connection is interrupted regularly, and the system cannot receive a complete response from the server to which the connection is made;
* when anti-virus programs are enabled or if the firewall settings are incorrect;
* incorrect proxy-server settings;
* unreliable operation of the web server due to increased load or incorrect operation of scripts.

## Examples

Incorrect:

```bsl
HTTPConnection = New HTTPConnection("zabbix.localhost", 80);
```

or

```bsl
FTPConnection = New FTPConnection(Server, Port, Login, Password, Proxy, PassiveMode);
```

Correct:

```bsl
HTTPConnection = New HTTPConnection("zabbix.localhost", 80,,,, 1);
```

or

```bsl
ConnectiomTimeout = 180;
HTTPConnection = New HTTPConnection("zabbix.localhost", 80,,,, ConnectiomTimeout);
```

## Reference

* [Timeouts when working with external resources](https://its.1c.ru/db/v8std#content:748:hdoc)
* [InternetMail Profilehas a default timeout](https://its.1c.ru/db/metod8dev/content/2358/hdoc)
