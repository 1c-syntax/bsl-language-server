# Use of a method\attribute unavailable in the target platform version (UnavailableMemberCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The diagnostic triggers on a method call or property access of a platform type that is available only since a platform version newer than the project compatibility mode. Using such a member will fail in the environment matching the selected compatibility mode.

The version a member becomes available in is taken from the syntax assistant of the installed 1C platform (via `bsl-context`) or from the bundled reference. Triggering respects the target platform version: a member is treated as unavailable when the target version is lower than the version the member was introduced in. The target version is resolved by priority: the `platform.targetVersion` setting, then the configuration compatibility mode, and if neither is set the newest platform is assumed and the check does not trigger.

When the object type may be inferred as one of several types (for example, a variable assigned values of different types in different code branches), the diagnostic triggers if the member is unavailable for at least one of the possible types.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

The target platform version is `8.3.10` (`platform.targetVersion` or the compatibility mode), while the `ВопросАсинх` (`DoQueryBoxAsync`) method is available only since `8.3.18`:

```bsl
// Triggers: the method is available only since version 8.3.18
Оповещение = Новый ОписаниеОповещения("ПослеВопроса", ЭтотОбъект);
ВопросАсинх(Оповещение, "Продолжить?", РежимДиалогаВопрос.ДаНет);
```

Fix it by raising the target platform version to a supported one, or by using a member available in the target version (for example, the synchronous `Вопрос` / `DoQueryBox`):

```bsl
Ответ = Вопрос("Продолжить?", РежимДиалогаВопрос.ДаНет);
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: 1C:Enterprise 8 platform syntax assistant
