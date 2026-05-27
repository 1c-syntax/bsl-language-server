# Deprecated methods should not be used (DeprecatedMethodCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In cases where it becomes necessary to mark a procedure (function) as deprecated, the word "Deprecated." Is placed in the first line of its description (rus. "Устарела.").

Use or extension of deprecated methods should be avoided. Marking method as deprecated is a warning that means the method will be removed in future versions and left for temporary backward compatibility.

Exception: It is possible to call deprecated methods from deprecated methods.

Besides user-defined methods, the diagnostic also triggers on calls to platform methods and property accesses marked as deprecated in the syntax assistant. The deprecation data comes from the syntax assistant of the installed 1C platform (via `bsl-context`) or from the bundled reference, so the check works even without a connected platform reference. Triggering respects the target platform version: a member is treated as deprecated when the target version is not lower than the version the member was deprecated in. The target version is resolved by priority: the `platform.targetVersion` setting, then the configuration compatibility mode, and if neither is set the newest platform is assumed.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
// Deprecated. Need to use NotDeprecatedProcedure.
Procedure DeprecatedProcedure()
EndProcedure

DeprecatedProcedure(); // Triggering diagnostics
```

## Sources

* Standart: [Procedures and functions description (RU)](https://its.1c.ru/db/v8std#content:453:hdoc)
* [CWE-477 Use of Obsolete Function](http://cwe.mitre.org/data/definitions/477.html)
