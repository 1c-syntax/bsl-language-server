# Deprecated HTTPConnection methods (DeprecatedHttpConnectionMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

In platform 8.3.21, `HTTPConnection` object methods are deprecated in client context. Use their asynchronous equivalents with the `Async` suffix instead.

Asynchronous methods are only available on the client. Therefore the diagnostic reports only calls that are guaranteed to execute on the client:

* application modules (managed, ordinary and `ApplicationModule.bsl`) — all code;
* client common modules — all code; a common module is considered client-side when at least one client flag is set and the `Server`, `External connection` and `Server call` flags are not set;
* managed form and command modules — only methods with the `&AtClient` directive;
* ordinary form modules — all code, because compilation directives are not used there.

The diagnostic does not fire in server modules, in methods with the `&AtServer`, `&AtServerNoContext` directives and `&AtClientAtServer`/`&AtClientAtServerNoContext` directives (such methods are also compiled for the server), as well as in methods without a directive in managed forms and commands — the platform executes them on the server. Common modules with mixed client-server flags are not reported either: client-side execution cannot be guaranteed.

## Deprecated methods

- `CallHTTPMethod()` → `CallHTTPMethodAsync()`
- `Write()` → `WriteAsync()`
- `Change()` → `ChangeAsync()`
- `SendForProcessing()` → `SendForProcessingAsync()`
- `Get()` → `GetAsync()`
- `GetHeaders()` → `GetHeadersAsync()`
- `Delete()` → `DeleteAsync()`

## Examples

### Incorrect

```bsl
Connection = New HTTPConnection("example.com");
Connection.Get(Request, Response);
```

### Correct

```bsl
Connection = New HTTPConnection("example.com");
Connection.GetAsync(Request, Response);
```

## See also

- [Platform 8.3.21 changelog](https://dl04.1c.ru/content/Platform/8_3_21_1140/1cv8upd_8_3_21_1140.htm)
