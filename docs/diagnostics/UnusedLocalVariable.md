# Неиспользуемая локальная переменная (UnusedLocalVariable)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
Программные модули не должны иметь неиспользуемых переменных.

Если локальная переменная объявлена, но не используется, это мертвый код, который следует удалить.
Это улучшит удобство обслуживания, поскольку разработчики не будут удивляться, для чего используется переменная.
