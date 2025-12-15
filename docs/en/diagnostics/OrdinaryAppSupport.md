# Ordinary application support (OrdinaryAppSupport)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
To maintain backward compatibility with various proprietary and third-party solutions, external data processes and reports developed on previous versions of the 1C: Enterprise platform 8.0 and 8.1, the configuration should also support launching in ordinary application (thick client) and external connection modes for administrators (users with full rights).

For this it is recommended:

* The configuration property "Use managed forms in a ordinary application" is set to True.
* Set the property "Use ordinary forms in managed mode" to False.
* Adhere to the general scheme for setting the attributes of common modules, and conduct the development itself in the Designer in edit mode for both launch modes - managed and ordinary applications (Service menu - Options - General tab).

When developing in EDT, properties are set through the Designer.

The refusal to support the launch of configuration in the ordinary application and external connection modes for administrators is possible only in certain justified cases.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: [Standard: General Configuration Requirements](https://its.1c.ru/db/v8std#content:467:hdoc)
