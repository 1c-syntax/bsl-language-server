/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.platform.EnAttachments;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сопоставляет user-метод модуля с платформенным событием типа-владельца
 * модуля. Для object/manager/recordset-модулей конфигурации, для глобальных
 * прикладных модулей и для OScript-классов платформа дёргает обработчики
 * строго по имени, совпадающему с именем события, — поэтому сопоставление
 * идёт по имени.
 * <p>
 * Результат кэшируется в {@link com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex}.
 * <p>
 * Формы — отдельный путь: имя обработчика декларируется в {@code Form.xml}
 * блоке {@code <Events>}, и сопоставление идёт XML → method-by-name. Эта
 * ветка реализуется отдельно.
 */
@Component
@WorkspaceScope
@Slf4j
public class EventHandlerResolver {

  /**
   * Модули, чей owner-тип — специализация по имени MDO:
   * {@code <MdoFullName.ru><Суффикс>.<Имя>}. Например,
   * {@code ДокументОбъект.Покупатели}, {@code РегистрСведенийНаборЗаписей.Курсы}.
   */
  private static final Map<ModuleType, String> MODULE_TYPE_TO_WRAPPER_RU = Map.of(
    ModuleType.ManagerModule, "Менеджер",
    ModuleType.ObjectModule, "Объект",
    ModuleType.RecordSetModule, "НаборЗаписей",
    ModuleType.ValueManagerModule, "МенеджерЗначения"
  );

  /**
   * Модули, чей owner-тип — фиксированный платформенный тип, не зависящий
   * от имени MDO (модуль команды, модуль HTTP/Web/Bot/Integration сервиса).
   * Если такого типа в реестре HBK нет — события просто не найдутся,
   * lookup отдаст {@link Optional#empty()}.
   */
  private static final Map<ModuleType, String> MODULE_TYPE_TO_FIXED_OWNER_RU = Map.of(
    ModuleType.CommandModule, "Модуль команды",
    ModuleType.HTTPServiceModule, "Модуль HTTP-сервиса",
    ModuleType.WEBServiceModule, "Модуль Web-сервиса",
    ModuleType.IntegrationServiceModule, "Модуль сервиса интеграции",
    ModuleType.BotModule, "Модуль бота"
  );

  /**
   * Глобальные модули прикладного решения. Их события — события объекта
   * {@code ГлобальныйКонтекст} платформы; ищем их в типе-обёртке самого
   * приложения, чьё qualifiedName совпадает с именем модуля.
   */
  private static final Set<ModuleType> GLOBAL_HOST_MODULES = EnumSet.of(
    ModuleType.ManagedApplicationModule,
    ModuleType.OrdinaryApplicationModule,
    ModuleType.SessionModule,
    ModuleType.ExternalConnectionModule
  );

  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef BOOLEAN = new TypeRef(TypeKind.PRIMITIVE, "Булево");

  /**
   * Встроенные события OneScript-класса. {@code ПриСозданииОбъекта} —
   * конструктор с переменным числом параметров; {@code ОбработкаПолученияПредставления} —
   * callback для переопределения представления объекта при касте к строке.
   */
  private static final List<MemberDescriptor> OSCRIPT_CLASS_EVENTS = List.of(
    MemberDescriptor.event(
      "ПриСозданииОбъекта",
      "Конструктор класса. Вызывается при инициализации экземпляра. "
        + "Принимает переменное число параметров.",
      List.of(new SignatureDescriptor(
        List.of(new ParameterDescriptor(
          BilingualString.of("Значение", "Value"),
          TypeSet.EMPTY, true, BilingualString.EMPTY, "", true)),
        TypeSet.EMPTY, ""))
    ),
    MemberDescriptor.event(
      "ОбработкаПолученияПредставления",
      "Вызывается при приведении объекта к строке и позволяет переопределить "
        + "его представление. Установка {@code СтандартнаяОбработка = Ложь} отменяет "
        + "стандартное поведение.",
      List.of(new SignatureDescriptor(
        List.of(
          new ParameterDescriptor(
            BilingualString.of("Представление", "Presentation"),
            TypeSet.of(STRING), false, BilingualString.EMPTY, ""),
          new ParameterDescriptor(
            BilingualString.of("СтандартнаяОбработка", "StandardProcessing"),
            TypeSet.of(BOOLEAN), false, BilingualString.EMPTY, "Истина")),
        TypeSet.EMPTY, ""))
    )
  );

  private static final Map<String, MemberDescriptor> OSCRIPT_CLASS_EVENTS_BY_NAME =
    OSCRIPT_CLASS_EVENTS.stream()
      .collect(Collectors.toMap(
        m -> m.name().toLowerCase(Locale.ROOT),
        Function.identity()));

  private static final Map<String, String> OSCRIPT_CLASS_EVENT_ALIASES_EN_TO_RU = Map.of(
    "onobjectcreate", "присозданииобъекта",
    "presentationgetprocessing", "обработкаполученияпредставления"
  );

  private final TypeRegistry typeRegistry;
  private final BslContextHolder bslContextHolder;

  /**
   * Лениво построенная карта глобальных событий: {@link ModuleType} →
   * {@code lc(имя события)} → контракт. Источник —
   * {@code ContextProvider.getGlobalContext()} с четырьмя списками
   * (managed/ordinary/session/external). Если HBK недоступен — карта пуста.
   */
  private final Lazy<Map<ModuleType, Map<String, MemberDescriptor>>> globalEvents
    = new Lazy<>(this::buildGlobalEvents);

  public EventHandlerResolver(TypeRegistry typeRegistry, BslContextHolder bslContextHolder) {
    this.typeRegistry = typeRegistry;
    this.bslContextHolder = bslContextHolder;
  }

  /**
   * Возвращает контракт платформенного события для пары
   * {@code (документ, имя метода)} либо {@link Optional#empty()}, если
   * метод не является обработчиком события owner-типа модуля.
   * <p>
   * Безопасно вызывать когда конфигурационные типы ещё не зарегистрированы —
   * вернёт {@link Optional#empty()}.
   */
  public Optional<MemberDescriptor> lookupContract(DocumentContext documentContext, String methodName) {
    var moduleType = documentContext.getModuleType();
    if (moduleType == ModuleType.OScriptClass) {
      return lookupOScriptClassEvent(methodName);
    }
    if (GLOBAL_HOST_MODULES.contains(moduleType)) {
      return lookupGlobalEvent(moduleType, methodName);
    }
    var ownerTypeRef = resolveOwnerType(documentContext, moduleType);
    if (ownerTypeRef.isEmpty()) {
      return Optional.empty();
    }
    var key = methodName.toLowerCase(Locale.ROOT);
    // events наследуются от generic-типа (например, ДокументОбъект.<Имя документа>)
    // автоматически через MemberSource, который TypeRegistry.registerSpecialization
    // регистрирует на специализированный TypeRef.
    return typeRegistry.getMembers(ownerTypeRef.get()).stream()
      .filter(m -> m.kind() == MemberKind.EVENT)
      .filter(m -> m.name().toLowerCase(Locale.ROOT).equals(key))
      .findFirst();
  }

  private static Optional<MemberDescriptor> lookupOScriptClassEvent(String methodName) {
    var key = methodName.toLowerCase(Locale.ROOT);
    var ruKey = OSCRIPT_CLASS_EVENT_ALIASES_EN_TO_RU.getOrDefault(key, key);
    return Optional.ofNullable(OSCRIPT_CLASS_EVENTS_BY_NAME.get(ruKey));
  }

  private Optional<MemberDescriptor> lookupGlobalEvent(ModuleType moduleType, String methodName) {
    var byName = globalEvents.getOrCompute().get(moduleType);
    if (byName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(byName.get(methodName.toLowerCase(Locale.ROOT)));
  }

  private Map<ModuleType, Map<String, MemberDescriptor>> buildGlobalEvents() {
    var providerOpt = bslContextHolder.get();
    if (providerOpt.isEmpty()) {
      return Map.of();
    }
    var provider = providerOpt.get();
    var globalContext = provider.getGlobalContext();
    var enLookup = BslContextPlatformTypesProvider.enLookupOf(provider);
    Map<ModuleType, Map<String, MemberDescriptor>> byModule = new EnumMap<>(ModuleType.class);
    putEvents(byModule, ModuleType.ManagedApplicationModule, globalContext.applicationEvents(), enLookup);
    putEvents(byModule, ModuleType.OrdinaryApplicationModule, globalContext.ordinaryApplicationEvents(), enLookup);
    putEvents(byModule, ModuleType.SessionModule, globalContext.sessionModuleEvents(), enLookup);
    putEvents(byModule, ModuleType.ExternalConnectionModule, globalContext.externalConnectionModuleEvents(), enLookup);
    return Map.copyOf(byModule);
  }

  private static void putEvents(Map<ModuleType, Map<String, MemberDescriptor>> sink,
                                ModuleType moduleType,
                                List<ContextEvent> events,
                                Function<Object, EnAttachments> enLookup) {
    if (events.isEmpty()) {
      return;
    }
    // Каждое событие может добавиться дважды (ru-имя и en-алиас), поэтому
    // capacity на оба варианта.
    Map<String, MemberDescriptor> byName = HashMap.newHashMap(events.size() * NAME_ALIASES_PER_EVENT);
    for (var event : events) {
      var descriptor = BslContextPlatformTypesProvider.toMemberDescriptor(event, enLookup);
      byName.put(descriptor.name().toLowerCase(Locale.ROOT), descriptor);
      // En-имя из bilingualName тоже как alias, чтобы lookup по English-имени работал.
      var en = descriptor.bilingualName().en();
      if (!en.isBlank()) {
        byName.putIfAbsent(en.toLowerCase(Locale.ROOT), descriptor);
      }
    }
    sink.put(moduleType, Map.copyOf(byName));
  }

  /** Ru-имя + en-алиас — каждое событие может занять до двух ключей в карте. */
  private static final int NAME_ALIASES_PER_EVENT = 2;

  /**
   * Owner-тип модуля: {@code ДокументОбъект.Покупатели},
   * {@code СправочникМенеджер.Контрагенты}, {@code РегистрСведенийНаборЗаписей.Курсы},
   * {@code КонстантаМенеджерЗначения.КодВалюты} — для MDO-specific обёрток.
   * Для модулей с фиксированным типом (команды, HTTP/Web/Bot/Integration сервисы)
   * — прямой qualifiedName типа из HBK без специализации по имени.
   */
  private Optional<TypeRef> resolveOwnerType(DocumentContext documentContext, ModuleType moduleType) {
    var fixed = MODULE_TYPE_TO_FIXED_OWNER_RU.get(moduleType);
    if (fixed != null) {
      return typeRegistry.resolve(fixed);
    }
    var wrapperRu = MODULE_TYPE_TO_WRAPPER_RU.get(moduleType);
    if (wrapperRu == null) {
      return Optional.empty();
    }
    return documentContext.getMdObject()
      .flatMap(md -> mdoSpecificQualifiedName(md, wrapperRu))
      .flatMap(typeRegistry::resolve);
  }

  /** Сборка {@code <FullName.ru><Суффикс>.<Имя>} для MDO; пусто, если части не заполнены. */
  private static Optional<String> mdoSpecificQualifiedName(MD mdObject, String wrapperRu) {
    var fullName = mdObject.getMdoType().fullName().getRu();
    var name = mdObject.getName();
    if (fullName.isBlank() || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(fullName + wrapperRu + "." + name);
  }
}
