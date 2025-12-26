# Cached public methods (CachedPublic)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

You should not create a programming interface in modules that reuse return values.

3.6. Another example of hiding library implementation details from a consumer. Suppose: in the first version of the library, consumers were provided with an export function of a common module with repeated use of return values; But in the next version of the library, this design decision was revised in favor of the “usual” general module, where this function was transferred (similarly, if in the opposite direction). In this example, in order to save the library user from additional efforts to replace calls of the "old" function with a new one, it is recommended to immediately place the export function in the "regular" module, in its section "program interface". Then this function, depending on the current design decision, can call the utility function from the module with repeated use of the returned values or from any other module, or directly contain the implementation. However, for the consumer, its location will no longer change in future versions of the library.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: [Standard: Ensuring Library Compatibility](https://its.1c.ru/db/v8std#content:644:hdoc:3.6)
