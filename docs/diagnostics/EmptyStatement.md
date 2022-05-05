# Пустой оператор (EmptyStatement)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

Пустой оператор - это оператор, состоящий только из точки с запятой (";"). Появляется он в обычно

- при рефакторинге, когда разработчик удалил часть кода, но забыл удалить последнюю ";"
- при "копипасте", когда разработчик вставил скопированный код, содержащий конечный символ ";"
- при невнимательности, когда разработчик дважды (а то и больше) раз напечатал символ ";"

Пустой оператор не приводит к ошибкам работы кода, но захламляет его, снижая восприятие.