# Использование метода РольДоступна (IsInRoleMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Для проверки прав доступа в коде следует использовать метод ПравоДоступа.

В тех случаях, где роль не дает никаких прав на объекты метаданных, а служит только для определения того или иного дополнительного права, 
следует использовать метод РольДоступна. 

При использовании в конфигурации Библиотеки стандартных подсистем (БСП) следует 
использовать функцию РолиДоступны общего модуля Пользователи. 
Если в конфигурации не используется БСП, следует обязательно совмещать вызов РольДоступна() с проверкой на ПривилегированныйРежим().
## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Неправильно:
```bsl
Если РольДоступна("ДобавлениеИзменениеСтранМира") Тогда ...
Если РольДоступна("ПросмотрОтчетаПопулярныеСтраны") Тогда ...
```
Правильно:
```bsl
Если ПравоДоступа("Редактирование", Метаданные.Справочники.СтраныМира) Тогда ...
Если ПравоДоступа("Просмотр", Метаданные.Отчеты.ПопулярныеСтраны) Тогда ...
```
Неправильно:
```bsl
Если РольДоступна("Казначей") Тогда ...
```
Правильно:
```bsl
Если РольДоступна("Казначей") ИЛИ ПривилегированныйРежим() Тогда ...
```
## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Источник: [Стандарт: Проверка прав доступа](https://its.1c.ru/db/v8std#content:737:hdoc)
