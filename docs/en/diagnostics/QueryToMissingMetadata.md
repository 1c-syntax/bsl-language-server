# Using non-existent metadata in the query (QueryToMissingMetadata)

|   Type    |    Scope    |   Severity    |    Activated<br>by default    |    Minutes<br>to fix    |            Tags             |
|:--------:|:-----------------------------:|:-------------:|:------------------------------:|:-----------------------------------:|:---------------------------:|
| `Error` |             `BSL`             | `Blocker` |              `Yes`              |                 `5`                 |    `suspicious`<br>`sql`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Using non-existent metadata in the query (QueryToMissingMetadata)

Type

## Scope

Severity
Activated by default
Minutes<br> to fix
Tags

## Sources
`BSL`

* `Blocker`
* `Yes`
* `5`
- `suspicious`<br>`sql`
- <!-- Блоки выше заполняются автоматически, не трогать -->

## Diagnostics description

With frequent changes to the metadata model, queries may appear that refer to renamed or deleted metadata. In addition, erroneous table names can occur when manually modifying queries without validation with the query designer.
### Executing queries against non-existent metadata will generate a runtime error.

Examples

### Query for an already deleted register:

```sdbl
SELECT
    Table.Field1 AS Field1
FROM
    InformationRegister.InfoRegOld AS Table
```
