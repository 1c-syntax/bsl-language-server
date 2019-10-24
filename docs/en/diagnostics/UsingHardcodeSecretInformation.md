# Storing confidential information in code

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Vulnerability` | `BSL` | `Critical` | `Нет` | `15` | `standard` |


## <TODO PARAMS>

## Description

It is prohibited to store any confidential information in the code. The confidential information is:

- Passwords
- Personal access tokens/keys

If the project uses SSL sub-system, then passwords should be stored in safe storage.

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

## Sources

* [Standard: Store passwords safe](https://its.1c.ru/db/v8std#content:740:hdoc)
