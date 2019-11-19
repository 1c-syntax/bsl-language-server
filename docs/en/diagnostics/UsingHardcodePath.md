# Using hardcode file paths in code

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Critical` | `Yes` | `15` | `standard` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `searchWordsStdPathsUnix` | `Pattern` | ```Ключевые слова поиска стандартных корневых каталогов Unix``` | ```"bin|boot|dev|etc|home|lib|lost\\+found|misc|mnt|media|opt|proc|root|run|sbin|tmp|usr|var"``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It's forbidden to store in code:

- Paths to files and folders (Windows, Unix)

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
