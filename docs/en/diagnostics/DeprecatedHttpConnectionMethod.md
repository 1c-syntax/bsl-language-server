# Deprecated HTTPConnection methods (DeprecatedHttpConnectionMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

In platform 8.3.21, `HTTPConnection` object methods are deprecated in client context. Use their asynchronous equivalents with the `Async` suffix instead.

Asynchronous methods are only available on the client. Therefore the diagnostic only fires in modules with a client context (forms, commands, ordinary/managed application, client common modules) and only inside methods that execute on the client (`&AtClient`, `&AtClientAtServer` or no directive in a client module). Methods with `&AtServer` and `&AtServerNoContext` directives, as well as server modules, are not reported — there are no asynchronous equivalents available there.

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
