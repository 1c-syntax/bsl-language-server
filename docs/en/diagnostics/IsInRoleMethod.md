# IsInRole global method call (IsInRoleMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
To check access rights in the code, use the AccessRight method.

When a role does not grant access rights to metadata objects and defines an additional access right only, use the IsInRole method.

If Standard Subsystems Library is used in a configuration, use the RolesAvailable function of the Users common module, otherwise IsInRole method call must be combined with PrivilegedMode() method call. If Standard Subsystems Library is used in a configuration, use the RolesAvailable() function of the Users common module, otherwise IsInRole() method call must be combined with PrivilegedMode() method call.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Wrong:
```bsl
If RolesAvailable("AddingChangingCountriesWorld") Then...
If RolesAvailable("ViewPopularCountriesReport") Then ...
```
Correct:
```bsl
If AccessRight("Edit", Metadata.Catalogs.WorldCountries) Then ...
If AccessRight("View", Metadata.Reports.PopularCountries) Then ...
```
Wrong:
```bsl
If RolesAvailable("Treasurer") Then...
```
Сorrect:
```bsl
If IsInRole("Treasurer") OR PrivilegedMode() Then ...
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Standard: [Checking access rights](https://its.1c.ru/db/v8std#content:737:hdoc)
