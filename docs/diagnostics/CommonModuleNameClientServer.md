# Пропущен постфикс "КлиентСервер" (CommonModuleNameClientServer)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Для того чтобы избежать дублирования кода, рекомендуется создавать клиент-серверные общие модули с теми процедурами и функциями, содержание которых одинаково на сервере и на клиенте. Такие процедуры и функции размещаются в общих модулях с признаками:

* Клиент (управляемое приложение)
* Сервер (флажок Вызов сервера сброшен)
* Клиент (обычное приложение)
* Внешнее соединение

Общие модули этого вида именуются с постфиксом "КлиентСервер" (англ. "ClientServer").

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

РаботаСФайламиКлиентСервер, ОбщегоНазначенияКлиентСервер, UsersClientServer

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


[Стандарт: Правила создания общих модулей](https://its.1c.ru/db/v8std#content:469:hdoc:2.4)