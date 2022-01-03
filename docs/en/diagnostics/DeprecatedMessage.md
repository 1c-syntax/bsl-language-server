# Restriction on the use of deprecated "Message" method (DeprecatedMessage)

|      Type      |    Scope    |     Severity     |    Activated<br>by default    |    Minutes<br>to fix    |               Tags               |
|:-------------:|:-----------------------------:|:----------------:|:------------------------------:|:-----------------------------------:|:--------------------------------:|
| `Code smell` |             `BSL`             | `Minor` |              `Yes`              |                 `2`                 |    `standard`<br>`deprecated`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

To display messages to the user in all cases, you should use the MessageUser object, even when the message is not “bound” to some form control. The Message method should not be used.

*When used the Standard Subsystems Library it is recommended use procedure MessageUser from common module CommonPurposeClientServer, which use object UserMessage.*

## Sources

* [Standard: Limit use of the obsolete method Message](https://its.1c.ru/db/v8std#content:418:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedMessage-off
// BSLLS:DeprecatedMessage-on
```

### Parameter for config

```json
"DeprecatedMessage": false
```
