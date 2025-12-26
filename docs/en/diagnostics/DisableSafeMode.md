# Disable safe mode (DisableSafeMode)

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

In addition to configuration code, the application solution can execute third-party program code, which can be connected in various ways (external reports and data processing, extensions, external components, etc.). The developer cannot guarantee the reliability of this code. При этом злоумышленник может предусмотреть в нем различные деструктивные действия (как в самом внешнем коде, так и опосредовано, через запуск внешних приложений, внешних компонент, COM-объектов), которые могут нанести вред компьютерам пользователей, серверным компьютерам, а также данным в программе.

Перечисленные проблемы безопасности особенно критичны при работе конфигураций в модели сервиса. The listed security problems are especially critical when operating configurations in the service model, because Having gained access to the service, malicious code can immediately gain access to all applications of all users of the service.

It is important to control the execution of such external code in safe mode, in exceptional cases (after verification) allowing code to be executed in unsafe mode.

The rule diagnoses calls to the methods `SetSafeMode` and `SetDisableSafeMode` in the mode of disabling safe mode control

- Method call `SetDisableSafeMode(true)` is ignored
- Method call `SetDisableSafeMode(false)` is ignored

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```
    УстановитьБезопасныйРежим (Ложь); // есть замечание

    Значение = Ложь;
    УстановитьБезопасныйРежим (Значение); // есть замечание

    УстановитьБезопасныйРежим (Истина); // нет замечания

    УстановитьОтключениеБезопасногоРежима(Истина); // есть замечание

    Значение = Истина;
    УстановитьОтключениеБезопасногоРежима(Значение); // есть замечание

    УстановитьОтключениеБезопасногоРежима(Ложь); // нет замечания
```

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Developer's Guide 8.3.22: Safe operation (RU)](https://its.1c.ru/db/v8322doc#bookmark:dev:TI000000186)
- [Standard: Restriction on the execution of "external" code (RU)](https://its.1c.ru/db/v8std/content/669/hdoc)
- [Standard: Server API Security (RU)](https://its.1c.ru/db/v8std/content/678/hdoc)
- [Standard: Restrictions on the use of Execute and Eval on the server (RU)](https://its.1c.ru/db/v8std#content:770:hdoc)
- [Standard: Using Privileged Mode (RU)](https://its.1c.ru/db/v8std/content/485/hdoc)
