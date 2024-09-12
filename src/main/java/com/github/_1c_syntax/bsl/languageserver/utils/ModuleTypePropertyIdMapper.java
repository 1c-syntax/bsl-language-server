package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.types.ModuleType;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Map.entry;

/**
 * Преобразователь типов модулей в захардкоженные внутренние идентификаторы и обратно
 */
public final class ModuleTypePropertyIdMapper {

  private final static Map<ModuleType, String> items = Map.ofEntries(
    entry(ModuleType.CommandModule, "078a6af8-d22c-4248-9c33-7e90075a3d2c"),
    entry(ModuleType.CommonModule, "d5963243-262e-4398-b4d7-fb16d06484f6"),
    entry(ModuleType.ObjectModule, "a637f77f-3840-441d-a1c3-699c8c5cb7e0"),
    entry(ModuleType.ManagerModule, "d1b64a2c-8078-4982-8190-8f81aefda192"),
    entry(ModuleType.FormModule, "32e087ab-1491-49b6-aba7-43571b41ac2b"),
    entry(ModuleType.RecordSetModule, "9f36fd70-4bf4-47f6-b235-935f73aab43f"),
    entry(ModuleType.ValueManagerModule, "3e58c91f-9aaa-4f42-8999-4baf33907b75"),
    entry(ModuleType.ManagedApplicationModule, "d22e852a-cf8a-4f77-8ccb-3548e7792bea"),
    entry(ModuleType.SessionModule, "9b7bbbae-9771-46f2-9e4d-2489e0ffc702"),
    entry(ModuleType.ExternalConnectionModule, "a4a9c1e2-1e54-4c7f-af06-4ca341198fac"),
    entry(ModuleType.OrdinaryApplicationModule, "a78d9ce3-4e0c-48d5-9863-ae7342eedf94"),
    entry(ModuleType.HTTPServiceModule, "d5963243-262e-4398-b4d7-fb16d06484f6"),
    entry(ModuleType.WEBServiceModule, "d5963243-262e-4398-b4d7-fb16d06484f6")
  );

  /**
   * @param moduleType Тип модуля.
   * @return Идентификатор типа модуля.
   */
  public static Optional<String> getPropertyId(ModuleType moduleType) {
    return Optional.ofNullable(items.getOrDefault(moduleType, null));
  }

  /**
   * @param propertyId Идентификатор типа модуля.
   * @return Тип модуля, соответствующий идентификатору.
   */
  public static Optional<ModuleType> getModuleType(String propertyId)
  {
    return items
      .entrySet()
      .stream()
      .filter(c -> Objects.equals(c.getValue(), propertyId))
      .findFirst()
      .map(Map.Entry::getKey);
  }
}
