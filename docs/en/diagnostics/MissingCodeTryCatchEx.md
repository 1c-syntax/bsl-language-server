# Missing code in Raise block in "Try ... Raise ... EndTry" (MissingCodeTryCatchEx)

|  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |               Tags                |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
| `Error` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `15`           | `standard`<br>`badpractice` |

## Parameters


|      Name       |   Type    |               Description                | Default value |
|:---------------:|:---------:|:----------------------------------------:|:-------------:|
| `commentAsCode` | `Boolean` | `Treat comment in Raise section as code` |    `false`    |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is unacceptable to catch any exception, without any trace for system administrator.

*Wrong*

```bsl
Try
    // code causing exception
    ....
Raise // catch any exception
EndTry;

```

As a rule, such a design hides a real problem, which is subsequently impossible to diagnose.

*Right*

```bsl
Try
    // code causing exception
    ....
Raise
    // Explanation why catching all exceptions untraceable for enduser.
    // ....
    // Write to log for system administrator.
    WriteLogEvent(NStr("en = 'Action'"),
       EventLogLevel.Error,,,
       DetailErrorDescription(ErrorInfo()));
EndTry;
```

## Sources

* [Catching Exceptions in Code (RU)](https://its.1c.ru/db/v8std#content:499:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MissingCodeTryCatchEx-off
// BSLLS:MissingCodeTryCatchEx-on
```

### Parameter for config

```json
"MissingCodeTryCatchEx": {
    "commentAsCode": false
}
```
