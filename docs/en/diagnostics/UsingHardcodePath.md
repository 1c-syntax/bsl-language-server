# Using hardcode file paths and ip addresses in code

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Critical` | `Нет` | `15` | `standard` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-: | :-: |
| `searchWordsExclusion` | `Pattern` | Ключевые слова поиска для исключения выражений при поиске IP адресов |  |
| `searchWordsStdPathsUnix` | `Pattern` | Ключевые слова поиска стандартных корневых каталогов Unix |  |
| `enableSearchNetworkAddresses` | `boolean` | Использовать поиск сетевых адресов |  |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It's forbidden to store in code:

- Paths to files and folders (Windows, Unix)
- Network addresses (ip6, ip4)

There are several ways to properly store such information:

- Store in Constants.
- Store in Information registers.
- Store in separate module, where this diagnostic is disabled (not recommended).
- Store in Catalog, Exchange plan node and etc.

### Characteristic

When search for Windows / Unix  paths, also check for URL in a string. URL search keywords:

- `http`
- `https`
- `ftp`

## Examples

**Incorrect:**

```bsl
EchangeFolder = "c:/exchange/dataexchange";
```

**Correct:**

```bsl
ExchangeFolder = Constants.ExchangeFolder.Get();
```

or

```bsl
ExchangeFolder = DataExchangeReuse.ExchangeFolder();
```
