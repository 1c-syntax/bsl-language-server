# Using hardcode ip addresses in code (UsingHardcodeNetworkAddress)

|      Type       |        Scope        |  Severity  | Activated<br>by default | Minutes<br>to fix |    Tags    |
|:---------------:|:-------------------:|:----------:|:-----------------------------:|:-----------------------:|:----------:|
| `Vulnerability` | `BSL`<br>`OS` | `Critical` |             `Yes`             |          `15`           | `standard` |

## Parameters


|              Name               |   Type   |                                  Description                                  |                                  Default value                                  |
|:-------------------------------:|:--------:|:-----------------------------------------------------------------------------:|:-------------------------------------------------------------------------------:|
|     `searchWordsExclusion`      | `String` |                       `Keywords to exclude from search`                       | `Верси|Version|ЗапуститьПриложение|RunApp|Пространств|Namespace|Драйвер|Driver` |
| `searchPopularVersionExclusion` | `String` | `Регулярное выражение для исключения популярных версий при поиске IP адресов` |                             `^(1|2|3|8\.3|11)\.`                              |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

It's forbidden to store in code:

* Сетевые адреса (ip6, ip4)

There are several ways to properly store such information:

* Store in Constants.
* Store in Information registers.
* Store in separate module, where this diagnostic is disabled (not recommended).
* Store in Catalog, Exchange plan node and etc.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:
```bsl
NetworkAddress = "192.168.0.1";
```

Correct:
```bsl
NetworkAddress = MyModuleReUse.ServerNetworkAddress();
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UsingHardcodeNetworkAddress-off
// BSLLS:UsingHardcodeNetworkAddress-on
```

### Parameter for config

```json
"UsingHardcodeNetworkAddress": {
    "searchWordsExclusion": "Верси|Version|ЗапуститьПриложение|RunApp|Пространств|Namespace|Драйвер|Driver",
    "searchPopularVersionExclusion": "^(1|2|3|8\\.3|11)\\."
}
```
