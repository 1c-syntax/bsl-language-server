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

1. The error will not be fixed when using `OR` over variants of a single field.

```bsl
LEFT JOIN Catalog.NomenclatureTypes КАК NomenclatureTypes
    ON CatalogNomenclature.NomenclatureType = NomenclatureTypes.Reference
        AND (CatalogNomenclature.ExpirationDate > 1
     OR CatalogNomenclature.ExpirationDate < 10)
```

2. When using the `OR` operator over various fields, the error will be fixed for each occurrence of the operator.

```bsl
ВНУТРЕННЕЕ СОЕДИНЕНИЕ Документ.РеализацияТоваровУслуг КАК РеализацияТоваровУслуг
ПО РеализацияТоваровУслугТовары.Ссылка = РеализацияТоваровУслуг.Ссылка
   И (РеализацияТоваровУслугТовары.Сумма > 0 
   ИЛИ РеализацияТоваровУслугТовары.СуммаНДС > 0 
   ИЛИ РеализацияТоваровУслугТовары.СуммаСНДС > 0)
         
```

It is proposed to correct such constructions by placing requests in separate packages with combining:

```bsl
ВЫБРАТЬ *
ИЗ
ВНУТРЕННЕЕ СОЕДИНЕНИЕ Документ.РеализацияТоваровУслуг КАК РеализацияТоваровУслуг
ПО РеализацияТоваровУслугТовары.Ссылка = РеализацияТоваровУслуг.Ссылка
   И РеализацияТоваровУслугТовары.Сумма > 0 
   
ОБЪЕДИНИТЬ ВСЕ 

ВЫБРАТЬ *
ИЗ
ВНУТРЕННЕЕ СОЕДИНЕНИЕ Документ.РеализацияТоваровУслуг КАК РеализацияТоваровУслуг
ПО РеализацияТоваровУслугТовары.Ссылка = РеализацияТоваровУслуг.Ссылка 
    И РеализацияТоваровУслугТовары.СуммаНДС > 0 
    
ОБЪЕДИНИТЬ ВСЕ 

ВЫБРАТЬ *
ИЗ
ВНУТРЕННЕЕ СОЕДИНЕНИЕ Документ.РеализацияТоваровУслуг КАК РеализацияТоваровУслуг
ПО РеализацияТоваровУслугТовары.Ссылка = РеализацияТоваровУслуг.Ссылка 
    И РеализацияТоваровУслугТовары.СуммаСНДС > 0       
```

3. Diagnostics will also work for nested connections using `OR` in conditions.

```bsl
Документ.РеализацияТоваровУслуг.Товары КАК РеализацияТоваровУслугТовары
ВНУТРЕННЕЕ СОЕДИНЕНИЕ Документ.РеализацияТоваровУслуг КАК РеализацияТоваровУслуг
ПО РеализацияТоваровУслугТовары.Ссылка = РеализацияТоваровУслуг.Ссылка
ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Номенклатура КАК СправочникНоменклатура
    ЛЕВОЕ СОЕДИНЕНИЕ Справочник.ВидыНоменклатуры КАК ВидыНоменклатуры //Тест работы на вложенном соединении
    ПО СправочникНоменклатура.ВидНоменклатуры = ВидыНоменклатуры.Ссылка
        И (СправочникНоменклатура.СрокГодности > 1
         ИЛИ ВидыНоменклатуры.ЗапрещенаПродажаЧерезПатент = ИСТИНА)
         
```

A fix similar to paragraph 2 is recommended by replacing the nested connection with a connection with the creation of an intermediate temporary table.

## Sources

- [Standard: Effective Query Conditions, Clause 2 (RU)](https://its.1c.ru/db/v8std/content/658/hdoc)
- [Typical Causes of Suboptimal Query Performance and Optimization Techniques: Using Logical OR in Conditions (RU)](https://its.1c.ru/db/content/metod8dev/src/developers/scalability/standards/i8105842.htm#or)
