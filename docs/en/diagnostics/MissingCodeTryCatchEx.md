# Missing code in Except block in "Try ... Except ... EndTry" (MissingCodeTryCatchEx)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is unacceptable to catch any exception, without any trace for system administrator.

*Incorrect*

```bsl
Try
    // code causing exception
    ....
Except // catch any exception
EndTry;

```

As a rule, such a design hides a real problem, which is subsequently impossible to diagnose.

*Correct*

```bsl
Try
    // code causing exception
    ....
Except
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
