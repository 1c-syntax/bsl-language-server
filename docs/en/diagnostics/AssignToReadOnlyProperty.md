# Assignment to a read-only property (AssignToReadOnlyProperty)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Detects an attempt to assign a value to a platform property declared as "read-only" in the syntax helper.
Such an assignment causes a runtime error. Access mode information is taken from the syntax helper of the installed 1C platform (via `bsl-context`) or from the bundled JSON fallback.

The diagnostic only covers `X.Property = …` chains via dot access. Indexer writes (`coll[0] = …`) and procedure parameter passing modes are out of scope.

## Examples

```bsl
// bad: the Metadata property of a reference type is read-only
Document.Metadata = "anything";

// good
FreelyMutableObject.Date = CurrentDate();
```
