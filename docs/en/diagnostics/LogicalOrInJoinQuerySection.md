# Logical 'OR' in 'JOIN' query section (LogicalOrInJoinQuerySection)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Diagnostics reveals the use of the `OR` operator in the conditions of table joins.

The presence of the `OR` operators in connection conditions may cause the DBMS to be unable to use
table indexes and perform scans, which will increase query running time and the likelihood of locks.

The error can be solved by "spreading" the predicates of the condition with `OR` into different query packages with combining

IMPORTANT:
Diagnostics monitors the presence of predicates in the condition `OR`, over various fields, since the use of the operator `OR`
When executing a query on the SQL side, the control over the variants of one field is automatically converted to the IN condition.

## Examples
1) The error will not be fixed when using `OR` over variants of a single field.

```bsl
LEFT JOIN Catalog.NomenclatureTypes КАК NomenclatureTypes
    ON CatalogNomenclature.NomenclatureType = NomenclatureTypes.Reference
        AND (CatalogNomenclature.ExpirationDate > 1
     OR CatalogNomenclature.ExpirationDate < 10)
```

2) When using the `OR` operator over various fields, the error will be fixed for each occurrence of the operator.

```bsl
INNER JOIN Document.GoodsServicesSaling КАК GoodsServicesSaling
ON GoodsServicesSalingGoods.Reference = GoodsServicesSaling.Reference
   AND (GoodsServicesSalingGoods.Amount  > 0 
   OR GoodsServicesSalingGoods.AmountVAT > 0 
   OR GoodsServicesSalingGoods.AmountWithVAT > 0)
         
```

It is proposed to correct such constructions by placing requests in separate packages with combining:

```bsl
SELECT *
FROM
INNER JOIN Document.GoodsServicesSaling КАК GoodsServicesSaling
ON GoodsServicesSalingGoods.Reference = GoodsServicesSaling.Reference
   AND GoodsServicesSalingGoods.Amount  > 0 
   
UNION ALL 

SELECT *
FROM
INNER JOIN Document.GoodsServicesSaling КАК GoodsServicesSaling
ON GoodsServicesSalingGoods.Reference = GoodsServicesSaling.Reference 
    AND GoodsServicesSalingGoods.AmountVAT > 0 
    
UNION ALL 

SELECT *
FROM
INNER JOIN Document.GoodsServicesSaling КАК GoodsServicesSaling
ON GoodsServicesSalingGoods.Reference = GoodsServicesSaling.Reference 
    AND GoodsServicesSalingGoods.AmountWithVAT > 0       
```

3) Diagnostics will also work for nested connections using `OR` in conditions.

```bsl
Document.GoodsServicesSaling.Goods КАК GoodsServicesSalingGoods
INNER JOIN Document.GoodsServicesSaling КАК GoodsServicesSaling
ON GoodsServicesSalingGoods.Reference = GoodsServicesSaling.Reference
LEFT JOIN Catalog.Nomenclature КАК CatalogNomenclature
    LEFT JOIN Catalog.NomenclatureTypes КАК NomenclatureTypes
    ON CatalogNomenclature.NomenclatureType = NomenclatureTypes.Reference
        AND (CatalogNomenclature.ExpirationDate > 1
         OR NomenclatureTypes.SaleThroughAPatentIsProhibited = TRUE)
         
```
A fix similar to paragraph 2 is recommended by replacing the nested connection with a connection with the creation of an intermediate temporary table.
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
