# Using hardcode ip addresses in code

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Vulnerability` | `BSL`<br/>`OS` | `Critical` | `Yes` | `15` | `standard` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `searchWordsExclusion` | `Pattern` | Ключевые слова поиска для исключения выражений при поиске IP адресов | `"Верси|Version|ЗапуститьПриложение|RunApp|Пространств|Namespace|Драйвер|Driver"` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

It's forbidden to store in code:
* Network addresses (ip6, ip4)

There are several ways to properly store such information:
* Store in Constants.
* Store in Information registers.
* Store in separate module, where this diagnostic is disabled (not recommended).
* Store in Catalog, Exchange plan node and etc.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:
```bsl
СетевойАдрес = "192.168.0.1";
```

Correct:
```bsl
СетевойАдрес = МойМодульПовтИсп.СетевойАдресСервера();
```