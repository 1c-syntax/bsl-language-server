# Using keyword "UNION" in queries (UnionAll)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                       Tags                       |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:------------------------------------------------:|
| `Code smell` | `BSL` | `Minor`  |             `Yes`             |           `5`           |       `standard`<br>`sql`<br>`performance`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
In most cases, when you need to combine the results of two or more queries into a single result set, employ UNION ALL clause instead of UNION. The recommendation is based on the algorithm of the UNION clause, which searches for and removes duplicates from the united result even when duplicates are impossible by the query design.

Employ UNION only when removing duplicates from the result is required.

## Examples

Wrong:
```bsl
SELECT
GoodsReceipt.Ref
FROM
Document.GoodsReceipt AS GoodsReceipt

UNION

SELECT
GoodsSale.Ref
FROM
Document.GoodsSale AS GoodsSale
```

Сorrect:

```bsl
SELECT
GoodsReceipt.Ref
FROM
Document.GoodsReceipt AS GoodsReceipt

UNION ALL

SELECT
GoodsSale.Ref
FROM
Document.GoodsSale AS GoodsSale
```

## Sources
* Link: [Development Standart: Using UNION and UNION ALL words in queries](https://its.1c.ru/db/v8std#content:434:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnionAll-off
// BSLLS:UnionAll-on
```

### Parameter for config

```json
"UnionAll": false
```
