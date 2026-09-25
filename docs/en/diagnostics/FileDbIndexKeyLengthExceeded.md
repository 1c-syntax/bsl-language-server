# Index key length limit exceeded (FileDbIndexKeyLengthExceeded)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
The rule checks the physical size of indexes in information registers (`InformationRegister`)
and calculation registers (`CalculationRegister`).
If the total size of fields in the index exceeds the DBMS limits, the 1C platform will not be able to perform restructuring,
and the configuration update will fail with an error.

The check is performed in two areas:
1. Main register index (`ByDims`): consists of all register dimensions.
If the register is periodic, 8 bytes are automatically added to the size for the system `Period` field.
2. Individual field indexes: checked for any dimensions or attributes
whose `Index` property is set to a value other than `Don't index` (`None`/`DONT_INDEX`).

The size of each field type is calculated by the analyzer code as follows:

* String (`String`): `Length` * 3 + 2 bytes (unlimited string is considered as 9999 bytes).
* Number (`Number`): (`Total digits` / 2) + 1 bytes.
* Date (`Date`): 8 bytes.
* Boolean (`Boolean`): 1 byte.
* Any reference (`Single Ref`): 16 bytes.
* Composite type (`Composite`): 1 byte (marker)
      + sum of maximum lengths of the selected primitive types
      + 20 bytes (if at least one reference is included in the composite type).

Configuration parameters: `checkMode` — limit validation mode.

Allowed values:
* `ALL` (check all limits, default)
* `FILE` (check only the file database limit — 1920 bytes)
* `MSSQL` (check only the MS SQL limit — 900 bytes)

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Incorrect:
A periodic information register is created. The check mode is set to MSSQL (900 bytes limit) or ALL.
* Dimension `Company` — type `CatalogRef.Companies` (16 bytes)
* Dimension `RecordComment` — type `String(300)` (300 * 3 + 2 = 902 bytes)

```bsl
// ByDims index calculation for this register:
// "Period" field (8) + Company (16) + RecordComment (902) = 926 bytes.
// Result: The rule will report an error exceeding the MSSQL limit (926 > 900 bytes).
```
Similarly, an error will occur if the Index property is enabled for a regular register attribute of type `String(400)`:
```bsl
// Individual attribute index: 400 * 3 + 2 = 1202 bytes.
// Result: Error exceeding the MSSQL limit (1202 > 900 bytes).
```
Correct:

Long string data is moved from dimensions to resources or attributes without indexing,
and key string lengths are optimized.
* Dimension `Company` — type `CatalogRef.Companies` (16 bytes)
* Dimension `IdentifierCode` — type `String(50)` (50 * 3 + 2 = 152 bytes)
* Resource `RecordComment` — type `String(300)` (not included in the index)

```bsl
// Total ByDims key size: 8 (Period) + 16 (Company) + 152 (ShortCode) = 176 bytes.
// The key size is within the norm for any DBMS. No warnings.
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
Sources:
* [Methodological support: Influence of index key length limitations on designing metadata objects](https://its.1c.ru/db/metod8dev/content/1828/hdoc)
* [Methodological support: Database table indexes](https://its.1c.ru/db/content/metod8dev/src/admins/i8101798.htm)
* [Standard: Mismatch between indexes and query conditions](https://its.1c.ru/db/content/v8std/src/300/200/i8100652.htm)

Useful information:

* [V oblachke: Index key length exceeds the maximum allowable limit](https://voblachke.ru/blog/dlina-kljucha-indeksa-prevyshaet-maksimalno-dopustimuju/)
* [Mista: Index key length exceeds the maximum allowable limit](https://www.mista.ru/topic/866818)
* [Infostart: Maximum index key length exceeded error](https://forum.infostart.ru/forum9/topic122084/)
* [BSL LS Documentation: Diagnostic structure, purpose and file contents](https://1c-syntax.github.io/bsl-language-server/contributing/DiagnosticStructure/)
 
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
