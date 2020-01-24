# Timeouts working with external resources (TimeoutsInExternalResources)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Critical` | `Yes` | `5` | `unpredictable`<br/>`standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When working with external resources using the objects WSDefinitions, WSProxy, HTTPConnection, FTPConnection, InternetMailProfile, a timeout should be set - the time limit for the operation to be completed. Otherwise, as a result of endless waiting, the program will freeze or some of the functionality of the program will become unavailable.

Setting a timeout protects against external factors:

- unstable Internet connection, when the connection is interrupted regularly, and the system cannot receive a complete response from the server to which the connection is made;
- when anti-virus programs are enabled or if the firewall settings are incorrect;
- incorrect proxy-server settings;
- unreliable operation of the web server due to increased load or incorrect operation of scripts.

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
HTTPConnection = New HTTPConnection("zabbix.localhost", 80);
HTTPConnection.Timeout = 1;
```

## Reference

- [Timeouts when working with external resources](https://its.1c.ru/db/v8std#content:748:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:TimeoutsInExternalResources-off
// BSLLS:TimeoutsInExternalResources-on
```

### Parameter for config

```json
"TimeoutsInExternalResources": false
```
