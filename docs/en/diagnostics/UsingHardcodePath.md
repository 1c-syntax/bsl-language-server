# Using hardcode file paths and ip addresses in code

It's forbidden to store in code:

- Paths to files and folders (Windows, Unix)
- Network addresses (ip6, ip4)

## Parameters

- `searchWordsExclusion` - `string` - Keywords to exclude expressions when searching for IP addresses. Default`Верси|Version|ЗапуститьПриложение|RunApp|Пространств|Namespace|Драйвер|Driver`.
- `searchWordsStdPathsUnix` - `string` - Keywords to search standard root directory Unix.Default `bin|boot|dev|etc|home|lib|lost\\+found|misc|mnt|media|opt|proc|root|run|sbin|tmp|usr|var`.
- `enableSearchNetworkAddresses` - `boolean` - Use network address search. Default `Yes`.

There are several ways to properly store such information:

- Store in Constants.
- Store in Information registers.
- Store in separate module, where this diagnostic is disabled (not recommended).
- Store in Catalog, Exchange plan node and etc.

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

## Characteristic

When search for Windows / Unix  paths, also check for URL in a string. URL search keywords:

- `http`
- `https`
- `ftp`
