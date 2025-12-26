# Partially localized text is used in the StrTemplate function (MultilingualStringUsingWithTemplate)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

NStr in a multilingual configuration has different fragments for different languages. If you start a session under a language code that is not in the string passed to NStr, it will return an empty string. When used with StrTemplate, an empty string returned from NStr will throw an exception.

## Sources

- [localization requirements](https://its.1c.ru/db/v8std/content/763/hdoc)
