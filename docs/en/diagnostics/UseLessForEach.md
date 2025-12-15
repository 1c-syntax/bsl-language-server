# Useless collection iteration (UseLessForEach)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The absence of an iterator in the loop body indicates either a useless iteration of the collection or an error in the loop body.

## Examples

Incorrect:

```Bsl

For Each Iterator From Collection Loop

    ProcessElement(Collection);

EndLoop;

```

Correct:

```Bsl

For Each Iterator From Collection Loop

    ProcessElement(Iterator);

EndLoop;

```

```bsl

ProcessCollection(Collection);

```
