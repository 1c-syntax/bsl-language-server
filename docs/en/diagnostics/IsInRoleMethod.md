# IsInRole global method call (IsInRoleMethod)

 |     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |  Tags   |
 |:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:-------:|
 | `Code smell` | `BSL` | `Major`  |             `Yes`             |           `5`           | `error` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
To check access rights in the code, use the AccessRight method.

When a role does not grant access rights to metadata objects and defines an additional access right only, use the IsInRole method.

If Standard Subsystems Library is used in a configuration, use the RolesAvailable function of the Users common module, otherwise IsInRole method call must be combined with PrivilegedMode() method call. If Standard Subsystems Library is used in a configuration, use the RolesAvailable() function of the Users common module, otherwise IsInRole() method call must be combined with PrivilegedMode() method call.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```
Incorrect: If IsInRole("AddChangeWorldCountries") Then ...
If IsInRole("ViewPopularCountriesReport") Then ...
```
```
Correct example: If AccessRight("Edit", Metadata.Catalogs.WorldCountries) Then ...
If AccessRight("View", Metadata.Reports.PopularCountries) Then ...
```
```
Incorrect: If IsInRole("Paymaster") Then ...
```
```
Correct example: If IsInRole("Paymaster") OR PrivilegedMode() Then ...
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Standard: Checking access rights

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IsInRoleMethod-off
// BSLLS:IsInRoleMethod-on
```

### Parameter for config

```json
"IsInRoleMethod": false
```
