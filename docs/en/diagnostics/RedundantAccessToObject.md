# Redundant access to an object (RedundantAccessToObject)

|     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |             Tags             |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------------------------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           | `standard`<br>`clumsy` |

## Parameters


|          Name          |   Type    |        Description         | Значение<br>по умолчанию |
|:----------------------:|:---------:|:--------------------------:|:------------------------------:|
|  `checkObjectModule`   | `Boolean` |   `Check object modules`   |             `true`             |
|   `checkFormModule`    | `Boolean` |    `Check form modules`    |             `true`             |
| `checkRecordSetModule` | `Boolean` | `Check record set modules` |             `true`             |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
In the forms and modules of objects, it is wrong to refer to the attributes through the property ThisObject. In common modules, it is redundant to refer to methods through their name, except for modules with Cashed.

## Examples
In the ObjectModule of the Document with the attribute `Countractor`, it is wrong to use
```bsl
ThisObject.Contractor = GetContractor();
```

correctly use the props directly
```bsl
Contractor = GetContractor();
```

In the common module `Commons`, the following method call will be incorrect
```bsl
Commons.SendMessage("en = 'Hi!'");
```

correct
```bsl
SendMessage("en = 'Hi!'");
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
