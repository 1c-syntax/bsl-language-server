# Using hardcode file paths in code (UsingHardcodePath)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It's forbidden to store in code:

* Paths to files and folders (Windows, Unix)

There are several ways to correctly store this information:

* Store in Constants.
* Store in Information registers.
* Store in separate module, where this diagnostic is disabled (not recommended).
* Store in Catalog, Exchange plan node and etc.

### Nuances

When search for Windows / Unix  paths, also check for URL in a string. URL search keywords:
* ``http``
* ``https``
* ``ftp``

## Examples

Incorrect:

```bsl
EchangeFolder = "c:/exchange/dataexchange";
```

Correct:

```bsl
ExchangeFolder = Constants.ExchangeFolder.Get();
```

or

```bsl
ExchangeFolder = DataExchangeReuse.ExchangeFolder();
```
