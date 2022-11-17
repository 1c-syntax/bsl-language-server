# Using unavailable in Unix objects (UsingObjectNotAvailableUnix)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

On `Linux` OS `COM`, `OLE`, `ActiveDocument` are not available. For integration use other options, for example XML file exchange or web-services. For COM AddIns, it is recommended to change them to `NativeAPI` AddIn.

Checked the use of unavailable in:

* `COMObject`
* `Mail`

**Checking the value of the condition is not satisfied.**

### Addition

When found unavailable in Linux objects, the method is checked to include condition flow for platform type:

* `Linux_x86`
* `Windows`
* `MacOs`

## Examples

```bsl
Component = New COMObject("System.Text.UTF8Encoding");
```

or

```bsl
Mail = New Mail;
```
Instead of this you can use `StartApplication()`.

```bsl
SystemInformation = New SystemInformation();
If Not SystemInformation.PlatformType = PlatformType.Linux_x86 OR PlatformType.Linux_x86_64 Then
    Mail = New Mail;
EndIf;
```

## Sources

* [Features of the development of cross-platform applied solutions (RU)](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001208)
* [Features of the client application running Linux (RU)](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001283)
