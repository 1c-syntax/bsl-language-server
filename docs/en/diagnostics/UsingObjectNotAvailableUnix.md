# Using unavailable in Unix objects

In Linux COM, OLE, ActiveDocument are not available. For integration use other options, for example XML file exchange or web-services.
For external components, using COM, it is recommended to change them to NativeAPI AddIn.

Отслеживаемые механизмы, недоступные в Linux:

- COMОбъект
- Почта

Example:

```bsl
Component = New COMObject("System.Text.UTF8Encoding");
```

или

```bsl
Почта = Новый Почта;
```

Вместо этого можно использовать `ЗапуститьПриложение()`.

## Дополнительно

При проверке использования недоступных объектов в Linux учитываются условия в рамках метода, где проверяется
проверка на ТипПлатформы = Linux_x86 или Linux_x86_64.

Например:

```bsl
СистемнаяИнформация = Новый СистемнаяИнформация();
Если Не СистемнаяИнформация.ТипПлатформы = ТипПлатформы.Linux_x86 Или ТипПлатформы.Linux_x86_64 Тогда
	Почта = Новый Почта;
КонецЕсли
```

## Ссылки

Более подробную информацию можно изучить на ИТС:

- [Особенности разработки кроссплатформенных прикладных решений](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001208)
- [Особенности работы клиентского приложения под управлением ОС Linux](https://its.1c.ru/db/v8314doc#bookmark:dev:TI000001283)
