# Storing confidential information in code (UsingHardcodeSecretInformation)

 |      Type       | Scope |  Severity  | Activated<br>by default | Minutes<br>to fix |    Tags    |
 |:---------------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:----------:|
 | `Vulnerability` | `BSL` | `Critical` |             `Yes`             |          `15`           | `standard` |

## Parameters

 |     Name      |   Type   | Description                                                                        |   Default value   |
 |:-------------:|:--------:|:---------------------------------------------------------------------------------- |:-----------------:|
 | `searchWords` | `String` | `Search keywords for confidential information in variables, structures, mappings.` | `Пароль|Password` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is prohibited to store any confidential information in the code. The confidential information is:

* Passwords
* Personal access tokens/keys

If the project uses SSL sub-system, then passwords should be stored in safe storage.

### Additionally

Strings with all symbols `*` are excluded from the chech:

```bsl
Password = "**********";
```

## Examples

Incorrect:

```bsl
Password = "12345";
```

Correct:

```bsl
Passwords = CommonModule.ReadDataFromSafeStorage("StoringIdentifier", "Password");
Password = Passwords.Password;
```

## Reference

* [Standard: Store passwords safe](https://its.1c.ru/db/v8std#content:740:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UsingHardcodeSecretInformation-off
// BSLLS:UsingHardcodeSecretInformation-on
```

### Parameter for config

```json
"UsingHardcodeSecretInformation": {
    "searchWords": "Пароль|Password"
}
```
