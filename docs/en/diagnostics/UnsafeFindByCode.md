# Unsafe FindByCode() method usage (UnsafeFindByCode)

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The diagnostic detects the use of the `FindByCode()` method (or `НайтиПоКоду()`) for catalogs that have:

- code uniqueness control disabled (the **Check unique** property is set to `False`)
- or code series enabled not for the entire catalog (the **Code series** property is not equal to `Whole catalog`)

In such cases, using the `FindByCode()` method can lead to unexpected behavior, as the code may not be unique within the entire catalog or there may be multiple elements with the same code in different series.

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:

```bsl
// Catalog without uniqueness control
CatalogWithoutControl = Catalogs.CatalogWithoutUniquenessControl.FindByCode("001");
```

```bsl
// Catalog with code series "Within subordination"
CatalogWithSeries = Catalogs.CatalogWithSubordinationSeries.FindByCode("001");
```

Correct:

```bsl
// Using FindByCode() method for catalog with uniqueness control
// and code series for the entire catalog
CatalogWithControl = Catalogs.CatalogWithUniquenessControl.FindByCode("001");
```

```bsl
// Alternative option - use FindByName() method or other search methods
Catalog = Catalogs.CatalogWithoutUniquenessControl.FindByName("Element");
```

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- Source: [FindByCode() method](https://its.1c.ru/db/v8std#content:456:hdoc)
