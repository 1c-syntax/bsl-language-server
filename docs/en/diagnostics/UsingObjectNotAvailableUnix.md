# Using unavailable in Unix objects

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Critical` | `No` | `30` | `standard`<br/>`lockinos` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In Linux COM, OLE, ActiveDocument are not available. For integration use other options, for example XML file exchange or web-services.
For external components, using COM, it is recommended to change them to NativeAPI AddIn.

Checked the use of unavailable in :

- COMObject
- Mail

### Addition

When found unavailable in Linux objects, the method is checked to include condition flow for platform type.

## Examples

```bsl
Component = New COMObject("System.Text.UTF8Encoding");
```

or

```bsl
Mail = New Mail;
```

Instead of this you can use `StartApplication()`.

For example:

```bsl
SystemInformation = New SystemInformation();
If Not SystemInformation.PlatformType = PlatformType.Linux_x86 OR PlatformType.Linux_x86_64 Then
	Mail = New Mail;
EndIf;
```

## Sources

- [Features of the development of cross-platform applied solutions](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001208)
- [Features of the client application running Linux](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001283)
