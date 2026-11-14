# Deprecated HTTPConnection methods (DeprecatedHttpConnectionMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

In platform 8.3.21, `HTTPConnection` object methods are deprecated in client context. Use their asynchronous equivalents with the `Async` suffix instead.

Asynchronous methods are only available on the client. The diagnostic determines the possible compilation contexts of a call (module kind and its metadata, method directive, preprocessor branch) and reports the call only when **all** of them are client-side. Otherwise the suggested replacement would not compile on the server side.

In practice this means:

* managed and ordinary application modules — reported;
* managed form and command modules — only methods with the `&AtClient` directive; methods without a directive (the platform compiles them for the server) and methods with the `&AtServer`, `&AtServerNoContext`, `&AtClientAtServer*` directives are not reported;
* ordinary form modules — all code, because compilation directives are not used there;
* common module — all code when it is compiled for the client only; with the `Server` or `External connection` flag the diagnostic does not fire;
* when common module metadata is unavailable, the client context is not confirmed and the diagnostic does not fire;
* server modules are not analyzed.

## Deprecated methods

- `CallHTTPMethod()` → `CallHTTPMethodAsync()`
- `Put()` → `PutAsync()`
- `Patch()` → `PatchAsync()`
- `Post()` → `PostAsync()`
- `Get()` → `GetAsync()`
- `Head()` → `HeadAsync()`
- `Delete()` → `DeleteAsync()`

## Examples

### Incorrect

```bsl
&AtClient
Procedure GetData()
    Request = New HTTPRequest("/api/data");
    Connection = New HTTPConnection("example.com");
    Connection.Get(Request, Response);
EndProcedure
```

### Correct

```bsl
&AtClient
Async Procedure GetData()
    Request = New HTTPRequest("/api/data");
    Connection = New HTTPConnection("example.com");
    Response = Await Connection.GetAsync(Request);
    ResponseBody = Response.GetBodyAsString();
EndProcedure
```

## See also

- [Platform 8.3.21 changelog](https://dl04.1c.ru/content/Platform/8_3_21_1140/1cv8upd_8_3_21_1140.htm)
