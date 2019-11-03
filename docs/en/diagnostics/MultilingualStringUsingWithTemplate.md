# There is a localized text for all languages declared in the configuration

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Major` | `Yes` | `2` | `error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

# There is a localized text for all languages declared in the configuration

NStr in a multilingual configuration has different fragments for different languages.
If you start a session under a language code that is not in the string passed to NStr, it will return an empty string.
When used with StrTemplate, an empty string returned from NStr will throw an exception.

Source: [localization requirements] (https://its.1c.ru/db/v8std/content/763/hdoc)

## Options

* `Declared languages` -` String` - Comma-separated lines of language codes that support configuration. For example: `ru, en`