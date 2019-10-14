# Storing confidential information in code

It is prohibited to store any confidential information in the code. The confidential information is:

- Passwords
- Personal access tokens/keys

## Parameters

- `searchWords` - `String` - keyword for confidential information search in variables, structures, mappings. By default: `Пароль|Password`

If the project uses SSL sub-system, then passwords should be stored in safe storage. More information on this topic can be found here:
[Standard: Store passwords safe](https://its.1c.ru/db/v8std#content:740:hdoc)

Incorrect:

```bsl
Password = "12345";
```

Correct:

```bsl
Passwords = CommonModule.ReadDataFromSafeStorage("StoringIdentifier", "Password");
Password = Passwords.Password;
```
