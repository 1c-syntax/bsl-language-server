# There is a localized text for all languages declared in the configuration

NStr in a multilingual configuration has different fragments for different languages.
If you start a session under a language code that is not in the string passed to NStr, it will return an empty string.

Source: [localization requirements] (https://its.1c.ru/db/v8std/content/763/hdoc)

## Options

* `Declared languages` -` String` - Comma-separated lines of language codes that support configuration. For example: `ru, en`