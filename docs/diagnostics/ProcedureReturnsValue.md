# Процедура не должна возвращать значение (ProcedureReturnsValue)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

`Процедура`, в отличие от `Функции` не может возвращать значения. Эта диагностика находит процедуры, где есть `Возврат` со значением.