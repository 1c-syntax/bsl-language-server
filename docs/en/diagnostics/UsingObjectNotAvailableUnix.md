# Using unavailable in Unix objects

In Linux COM, OLE, ActiveDocument are not available. For integration use other options, for example XML file exchange or web-services.
For external components, using COM, it is recommended to change them to NativeAPI AddIn.

Checked the use of unavailable in :

- COMObject
- Mail

Example:

```bsl
Component = New COMObject("System.Text.UTF8Encoding");
```

or

```bsl
Mail = New Mail;
```

Instead of this you can use `StartApplication()`.

## Addition

When found unavailable in Linux objects, the method is checked to include condition flow for platform type.

Например:

```bsl
SystemInformation = New SystemInformation();
If Not SystemInformation.PlatformType = PlatformType.Linux_x86 OR PlatformType.Linux_x86_64 Then
	Mail = New Mail;
EndIf;
```

## Reference

More information is available on ITS:

- [Особенности разработки кроссплатформенных прикладных решений](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001208)
- [Особенности работы клиентского приложения под управлением ОС Linux](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001283)
