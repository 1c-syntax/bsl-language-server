# Redundant access to an object (RedundantAccessToObject)

 |     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |             Tags             |
 |:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------------------------:|
 | `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           | `standard`<br>`clumsy` |

## Parameters

 |          Name          |   Type    | Description                | Default value |
 |:----------------------:|:---------:|:-------------------------- |:-------------:|
 |  `checkObjectModule`   | `Boolean` | `Check object modules`     |    `true`     |
 |   `checkFormModule`    | `Boolean` | `Check form modules`       |    `true`     |
 | `checkRecordSetModule` | `Boolean` | `Check record set modules` |    `true`     | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
В формах и модулях объектов избыточно обращаться к реквизитам через ЭтотОбъект. В общих модулях избыточно обращаться к методам через свое имя, кроме модулей с ПовтИсп.

## Examples
В модуле объекта документа с реквизитом `Контрагент` неправильно писать
```bsl
ЭтотОбъект.Контрагент = ПолучитьКонтрагента();
```

правильно будет обратиться к реквизиту напрямую
```bsl
Контрагент = ПолучитьКонтрагента();
```

В общем модуле `ОбщегоНазначения` неправильным будет такой вызов метода
```bsl
ОбщегоНазначения.СообщитьПользователю("ru = 'Привет мир!'");
```

but correct
```bsl
СообщитьПользователю("ru = 'Привет мир!'");
```

## Sources

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:RedundantAccessToObject-off
// BSLLS:RedundantAccessToObject-on
```

### Parameter for config

```json
"RedundantAccessToObject": {
    "checkObjectModule": true,
    "checkFormModule": true,
    "checkRecordSetModule": true
}
```
