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

import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.CommentTypeResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Расширяет платформенные типы менеджеров/объектов/наборов записей
 * (например, {@code СправочникМенеджер.Контрагенты},
 * {@code СправочникОбъект.Контрагенты}) методами, экспортированными из
 * соответствующих модулей конфигурации (ManagerModule.bsl, ObjectModule.bsl,
 * RecordSetModule.bsl).
 * <p>
 * Реестр сам не знает, что эти методы существуют — их даёт только AST модуля.
 * Поэтому источник членов прибит к {@link DocumentContext}: запрос членов
 * будет каждый раз идти в актуальный SymbolTree, что даёт hot-reload без
 * ручной инвалидации.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
@Slf4j
public class ConfigurationModuleMembersProvider {

  /** ModuleType → префикс qualifiedName платформенного типа-обёртки. */
  private static final Map<ModuleType, String> MODULE_TYPE_TO_WRAPPER_RU = Map.of(
    ModuleType.ManagerModule, "Менеджер",
    ModuleType.ObjectModule, "Объект",
    ModuleType.RecordSetModule, "НаборЗаписей",
    ModuleType.ValueManagerModule, "МенеджерЗначения"
  );

  /**
   * Платформенный тип общего модуля синтакс-помощника — единственный,
   * неспециализируемый по имени (в отличие от {@code СправочникОбъект.<Имя>}
   * и т.п.): все общие модули структурно одинаковы, различаются только
   * значением.
   */
  private static final String COMMON_MODULE_PLATFORM_TYPE_NAME = "ОбщийМодуль";

  /** Начало пояснения после имени типа: «Массив из …», «Массив&lt;…&gt;», «Массив[…]». */
  private static final Pattern TYPE_NAME_TAIL = Pattern.compile("[\\s<\\[]");

  /** Делим имя надвое: само имя и всё, что за ним. */
  private static final int TYPE_NAME_AND_TAIL = 2;

  private static final Map<ModuleType, String> MODULE_TYPE_TO_WRAPPER_EN = Map.of(
    ModuleType.ManagerModule, "Manager",
    ModuleType.ObjectModule, "Object",
    ModuleType.RecordSetModule, "RecordSet",
    ModuleType.ValueManagerModule, "ValueManager"
  );

  private final TypeRegistry typeRegistry;
  private final GlobalScopeProvider globalScopeProvider;
  private final CommentTypeResolver commentTypeResolver;

  /** Уже зарегистрированные источники (по URI документа), чтобы избежать дублей. */
  private final Map<URI, TypeRef> registeredByUri = new ConcurrentHashMap<>();

  // Раньше ReferenceIndexFiller (@Order 200): register() наполняет moduleTypeRefByUri,
  // от которого зависит self-member проход индексатора. Без явного порядка filler мог
  // отработать раньше и пропустить self-члены (см. ReferenceIndexFiller).
  @Order(100)
  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    register(documentContext);
  }

  /**
   * RU-qualifiedName self-типа модуля по его метаданным — та же специализация, что
   * регистрирует {@link #register} ({@code СправочникОбъект.Контрагенты},
   * {@code ДокументНаборЗаписей.…}; для общего модуля — имя модуля). Чистая функция
   * без побочных эффектов и без опоры на {@code moduleTypeRefByUri}.
   * <p>
   * Нужна, чтобы резолвить self-тип <b>из метаданных напрямую</b> на первой сборке
   * дерева символов — до того как {@link #register} (слушатель
   * {@code DocumentContextContentChangedEvent}) наполнит {@code moduleTypeRefByUri}:
   * дерево строится внутри {@code DocumentContext.rebuild}, а событие публикуется
   * AOP уже после возврата из него, поэтому кэш на момент классификации self-членов
   * ещё пуст (см. {@code TypeService#findSelfMember}, аналогично
   * {@code EventHandlerResolver#resolveOwnerType}).
   *
   * @param moduleType вид модуля документа.
   * @param mdObject   MD-объект документа.
   * @return RU-qualifiedName self-типа; empty, если модуль не несёт self-тип или
   *   части имени не заполнены.
   */
  public static Optional<String> selfTypeQualifiedName(ModuleType moduleType, MD mdObject) {
    if (moduleType == ModuleType.CommonModule) {
      var name = mdObject.getName();
      return name.isBlank() ? Optional.empty() : Optional.of(name);
    }
    if (moduleType == ModuleType.FormModule) {
      // Форма — не обёртка над MD-объектом, а самостоятельный тип с реквизитами и
      // элементами; его имя строит FormTypesProvider (см. его javadoc).
      return mdObject instanceof Form form
        ? Optional.of(FormTypesProvider.moduleTypeQualifiedName(form))
        : Optional.empty();
    }
    var wrapperSuffix = MODULE_TYPE_TO_WRAPPER_RU.get(moduleType);
    if (wrapperSuffix == null) {
      return Optional.empty();
    }
    var groupNameRu = mdObject.getMdoType().fullName().getRu();
    var name = mdObject.getName();
    if (groupNameRu.isBlank() || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(groupNameRu + wrapperSuffix + "." + name);
  }

  private void register(DocumentContext documentContext) {
    var moduleType = documentContext.getModuleType();
    if (moduleType == ModuleType.CommonModule) {
      registerCommonModule(documentContext);
      return;
    }
    if (moduleType == ModuleType.FormModule) {
      registerFormModule(documentContext);
      return;
    }
    if (!MODULE_TYPE_TO_WRAPPER_RU.containsKey(moduleType)) {
      return;
    }
    var mdObjectOpt = documentContext.getMdObject();
    if (mdObjectOpt.isEmpty()) {
      return;
    }
    var mdObject = mdObjectOpt.get();
    var mdoType = mdObject.getMdoType();
    var fullName = mdoType.fullName();
    if (fullName == null) {
      return;
    }
    var groupNameRu = fullName.getRu(); // "Справочник", "Документ", ...
    var groupNameEn = fullName.getEn(); // "Catalog", "Document", ...

    var wrapperRu = groupNameRu + MODULE_TYPE_TO_WRAPPER_RU.get(moduleType);
    var wrapperEn = groupNameEn == null ? null : groupNameEn + MODULE_TYPE_TO_WRAPPER_EN.get(moduleType);
    var name = mdObject.getName();
    if (name == null || name.isBlank()) {
      return;
    }

    var qualifiedRu = wrapperRu + "." + name;
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    if (wrapperEn != null && !wrapperEn.equals(wrapperRu)) {
      typeRegistry.registerConfigurationTypeAlias(wrapperEn + "." + name, ref);
    }

    var prev = registeredByUri.put(documentContext.getUri(), ref);
    globalScopeProvider.indexModuleType(documentContext.getUri(), ref);
    if (prev != null && prev.equals(ref)) {
      // источник уже зарегистрирован, но содержимое изменилось (rebuild) — его member-source
      // лениво читает символьное дерево, поэтому точечно сбрасываем memo членов только этого
      // типа; кэши прочих типов остаются валидными (без сдвига глобальной эпохи).
      typeRegistry.invalidateMembers(ref);
      return;
    }

    typeRegistry.registerMemberSource(ref, () -> collectModuleMembers(documentContext), FileType.BSL);
    LOGGER.debug("Registered module-as-member-source for {} -> {}", documentContext.getUri(), qualifiedRu);
  }

  /**
   * Модуль формы: связывает URI документа с типом формы (его состав —
   * реквизиты/элементы/события — регистрирует {@link FormTypesProvider} из метаданных)
   * и добавляет к нему экспортные методы и переменные самого модуля. Именно эта связь
   * даёт в модуле формы разыменование {@code ЭтотОбъект.} и резолв неквалифицированных
   * имён реквизитов и элементов.
   */
  private void registerFormModule(DocumentContext documentContext) {
    var mdObjectOpt = documentContext.getMdObject();
    if (mdObjectOpt.isEmpty() || !(mdObjectOpt.get() instanceof Form form)) {
      return;
    }
    var ref = typeRegistry.registerConfigurationType(FormTypesProvider.moduleTypeQualifiedName(form));

    var prev = registeredByUri.put(documentContext.getUri(), ref);
    globalScopeProvider.indexModuleType(documentContext.getUri(), ref);
    if (prev != null && prev.equals(ref)) {
      // Источник уже зарегистрирован, но содержимое изменилось (rebuild): он лениво
      // читает символьное дерево, и без сброса memo в модуле формы остались бы
      // экспортные методы и переменные предыдущей редакции — как и в общем пути выше.
      typeRegistry.invalidateMembers(ref);
      return;
    }

    typeRegistry.registerMemberSource(ref, () -> collectModuleMembers(documentContext), FileType.BSL);
    LOGGER.debug("Registered form module as member source for {} -> {}",
      documentContext.getUri(), ref.qualifiedName());
  }

  private void registerCommonModule(DocumentContext documentContext) {
    var mdObjectOpt = documentContext.getMdObject();
    if (mdObjectOpt.isEmpty()) {
      return;
    }
    var name = mdObjectOpt.get().getName();
    if (name == null || name.isBlank()) {
      return;
    }

    var ref = typeRegistry.registerConfigurationType(name);

    var prev = registeredByUri.put(documentContext.getUri(), ref);
    globalScopeProvider.indexModuleType(documentContext.getUri(), ref);

    // общий модуль — глобальное свойство (value-type = тип модуля,
    // sourceSymbol = ModuleSymbol для навигации/раскраски). Помечаем на каждом
    // изменении — символ освежается из актуального SymbolTree.
    typeRegistry.registerGlobalPropertyType(ref, FileType.BSL,
      documentContext.getSymbolTree().getModule());

    if (prev != null && prev.equals(ref)) {
      // содержимое изменилось (rebuild): точечно пересобрать memo членов самого модуля
      // и члена GLOBAL_CONTEXT (в него вошёл обновлённый symbol-источник модуля) — без
      // сдвига глобальной эпохи членов. Name-индекс глобальной области отдельно сбрасывать
      // не нужно: он кэширован по набору-источнику и пересоберётся сам, увидев новый набор.
      typeRegistry.invalidateMembers(ref);
      typeRegistry.invalidateMembers(TypeRegistry.GLOBAL_CONTEXT);
      return;
    }

    typeRegistry.registerMemberSource(ref, () -> collectModuleMembers(documentContext), FileType.BSL);
    typeRegistry.registerMemberSource(ref, () -> commonModulePlatformMembers(ref), FileType.BSL);
    LOGGER.debug("Registered common module as global property {} -> {}", documentContext.getUri(), name);
  }

  /**
   * Платформенные члены общего модуля (сейчас — только {@code ЭтотОбъект}) из
   * реального типа {@value #COMMON_MODULE_PLATFORM_TYPE_NAME} синтакс-помощника
   * (HBK либо JSON-фолбек — обычный {@link TypeRegistry#getMembers}). Если тип
   * не зарегистрирован ни там, ни там — возвращает пусто.
   * <p>
   * {@code ЭтотОбъект} специализируется на {@code selfRef} (тип конкретного
   * общего модуля), а не остаётся с обобщённым возвращаемым типом
   * {@code ОбщийМодуль}: иначе dot-completion после {@code ЭтотОбъект.} не
   * показывал бы собственные экспортные методы этого модуля (их даёт
   * {@link #collectModuleMembers}, зарегистрированный на тот же {@code selfRef}).
   * <p>
   * Generic-плейсхолдер {@code <Имя процедуры или функции>} отфильтровывается
   * по тексту имени, а не по флагу {@code generic} — bsl-context его методам
   * не проставляет (в отличие от свойств).
   */
  private List<MemberDescriptor> commonModulePlatformMembers(TypeRef selfRef) {
    var genericRefOpt = typeRegistry.resolve(COMMON_MODULE_PLATFORM_TYPE_NAME, FileType.BSL);
    if (genericRefOpt.isEmpty()) {
      return List.of();
    }
    var genericRef = genericRefOpt.get();
    // Общий модуль, буквально названный "ОбщийМодуль", резолвится в сам платформенный
    // generic-тип: selfRef == genericRef. Специализировать тип на самом себе нельзя, а
    // getMembers(genericRef) здесь ушёл бы обратно в этот же источник → бесконечная
    // рекурсия (StackOverflowError). Платформенные члены у него и так уже есть — выходим.
    if (genericRef.equals(selfRef)) {
      return List.of();
    }
    var members = typeRegistry.getMembers(genericRef, FileType.BSL);
    var result = new ArrayList<MemberDescriptor>(members.size());
    for (var member : members) {
      if (!ContextNames.placeholders(member.name()).isEmpty()) {
        continue;
      }
      if (member.returnTypes().refs().contains(genericRef)) {
        member = member.withReturnTypes(TypeSet.of(selfRef));
      }
      result.add(member);
    }
    return result;
  }

  /**
   * Члены типа-обёртки из модуля: экспортные методы и экспортные переменные.
   * Экспортные {@code Перем X Экспорт} модулей объекта/набора записей и т.п.
   * становятся свойствами соответствующего типа ({@code СправочникОбъект.X}),
   * тип свойства выводится из висячего комментария декларации через общий
   * {@link CommentTypeResolver}.
   */
  private List<MemberDescriptor> collectModuleMembers(DocumentContext documentContext) {
    var methodMembers = documentContext.getSymbolTree().getMethods().stream()
      .filter(MethodSymbol::isExport)
      .map(this::toMethodMember);
    var variableMembers = documentContext.getSymbolTree().getVariables().stream()
      .filter(VariableSymbol::isExport)
      .map(this::toVariableMember);
    return Stream.concat(methodMembers, variableMembers).toList();
  }

  private MemberDescriptor toVariableMember(VariableSymbol variable) {
    var types = commentTypeResolver.resolve(variable, FileType.BSL);
    var description = variable.getDescription()
      .map(d -> d.getDescription() == null ? "" : d.getDescription().trim())
      .orElse("");
    return MemberDescriptor.property(variable.getName(), types, description)
      .withSourceSymbol(variable);
  }

  private MemberDescriptor toMethodMember(MethodSymbol method) {
    var params = method.getParameters().stream()
      .map(p -> new ParameterDescriptor(
        p.getName(),
        declaredParameterTypes(p),
        p.isOptional(),
        ""
      ))
      .toList();
    var description = method.getDescription()
      .map(d -> d.getDescription() == null ? "" : d.getDescription().trim())
      .orElse("");
    var returnType = method.getDescription()
      .map(d -> resolveReturnType(d.getReturnedValue()))
      .orElse(TypeRef.UNKNOWN);
    var signature = new SignatureDescriptor(params, returnType, description);
    return MemberDescriptor
      .method(method.getName(), description, List.of(signature))
      .withSourceSymbol(method);
  }

  /**
   * Типы параметра, объявленные в описании метода: имена разрешаются так же, как имя
   * типа возвращаемого значения ({@link #resolveReturnType}).
   * <p>
   * Без них ссылка {@code См. Модуль.Метод.Параметр} упирается в пустую сигнатуру: тип
   * возврата у члена есть, а типы параметров — нет.
   *
   * @param parameter параметр метода.
   * @return объявленные типы параметра; {@link TypeSet#EMPTY}, если их не объявлено.
   */
  private TypeSet declaredParameterTypes(ParameterDefinition parameter) {
    return parameter.getDescription()
      .map(description -> description.types().stream()
        .map(this::resolveTypeName)
        .filter(ref -> ref.kind() != TypeKind.UNKNOWN)
        .reduce(TypeSet.EMPTY, (acc, ref) -> acc.union(TypeSet.of(ref)), TypeSet::union))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Разрешает имя типа из описания: у коллекционной записи ({@code Массив из Строка})
   * берётся её головное имя.
   *
   * @param type описание типа.
   * @return тип по имени; {@link TypeRef#UNKNOWN}, если имя пустое или не резолвится.
   */
  private TypeRef resolveTypeName(TypeDescription type) {
    return resolveHeadName(type.name());
  }

  /**
   * Разрешает имя типа, отбрасывая пояснения после него: «Массив из Произвольный»,
   * «Массив&lt;Произвольный&gt;» и «Массив[Произвольный]» — это {@code Массив}.
   *
   * @param raw имя типа из описания.
   * @return тип по имени; {@link TypeRef#UNKNOWN}, если имя пустое или не резолвится.
   */
  private TypeRef resolveHeadName(String raw) {
    if (raw.isBlank()) {
      return TypeRef.UNKNOWN;
    }
    var head = TYPE_NAME_TAIL.split(raw.trim(), TYPE_NAME_AND_TAIL)[0];
    return typeRegistry.resolve(head).orElse(TypeRef.UNKNOWN);
  }

  /**
   * Парсит первый элемент {@code returnedValue} JavaDoc-описания BSL-метода
   * (например, "Массив из Произвольный" → "Массив") и резолвит через
   * {@link TypeRegistry}.
   */
  private TypeRef resolveReturnType(
    List<TypeDescription> returnedValue
  ) {
    if (returnedValue == null || returnedValue.isEmpty()) {
      return TypeRef.UNKNOWN;
    }
    return resolveHeadName(returnedValue.get(0).name());
  }
}
