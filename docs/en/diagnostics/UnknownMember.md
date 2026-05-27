# Reference to an unknown method or property (UnknownMember)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The diagnostic detects references to methods and properties that do not exist — likely typos or calls to a non-existent API:

* `Receiver.Member` — the receiver type is inferred and concrete, but it has no member with that name;
* a bare `Name(...)` call — the name resolves neither to a global function/property of the platform or the configuration, nor to a method/variable of the current module.

The diagnostic relies on type inference and member data from the 1C platform syntax assistant (via `bsl-context`) or the bundled reference. When the receiver type cannot be inferred or is arbitrary/undefined, the diagnostic stays silent to avoid false positives.

**The diagnostic is disabled by default** — it is heuristic and needs validation on real configurations (reference completeness, dynamic typing, etc.).

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
Массив = Новый Массив;
Массив.Добвить(1); // Triggers: a typo, type Массив has no method "Добвить"
```

Fix it by using an existing member:

```bsl
Массив.Добавить(1);
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: 1C:Enterprise 8 platform syntax assistant
