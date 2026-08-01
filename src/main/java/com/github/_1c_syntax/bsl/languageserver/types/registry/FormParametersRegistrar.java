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

import com.github._1c_syntax.bsl.context.api.Placeholder;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.ObjectForm;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormCommand;
import com.github._1c_syntax.bsl.mdo.storage.form.FormParameter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Параметры и команды формы.
 * <p>
 * Параметры управляемой формы лежат в отдельной структуре ({@code Форма.Параметры}):
 * стандартные приходят из синтакс-помощника с плейсхолдером в типе, объявленные —
 * из блока {@code <Parameters>} самой формы. У обычной формы структуры нет: параметры
 * там свойства расширения, и часть из них у конкретного владельца не существует вовсе
 * (см. {@link FormPlatformTypes#absentParameters}).
 * <p>
 * Команды — коллекция {@code Форма.Команды} по образцу коллекции элементов; процедура
 * из {@code <Action>} вешается на форму отдельным членом-событием, а её сигнатуру
 * (один параметр {@code Команда}) приходится собирать самим: контракта у обработчика
 * команды в синтакс-помощнике нет.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class FormParametersRegistrar {

  private final TypeRegistry typeRegistry;
  private final FormParametersResolver formParametersResolver;
  private final FormDataTypesRegistrar formDataTypes;
  private final FormTypeFactory typeFactory;
  private final RecorderIndex recorderIndex;

  /** Суффикс имени ссылочного типа: {@code Документ.Документ1} → {@code ДокументСсылка.Документ1}. */
  private static final String REF_SUFFIX = "Ссылка";

  /** Тип параметра, передавать в который нечего. */
  private static final TypeSet UNDEFINED = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Неопределено"));

  /** Параметр формы записи регистра сведений: ключ записи, открытой на изменение. */
  private static final String SOURCE_RECORD_KEY = "ИсходныйКлючЗаписи";

  /**
   * Что подставлять в плейсхолдеры параметров обычной формы.
   *
   * @param mdoRef     mdoRef объекта-владельца формы.
   * @param names      имена для параметров про сам объект (у журнала — его документы).
   * @param ownerNames имена для параметров про <b>владельца</b> объекта: у подчинённого
   *                   справочника {@code ПараметрОтборПоВладельцу} — это ссылка на
   *                   справочник-владелец, а не на сам справочник.
   */
  private record OrdinaryFormOwner(String mdoRef, List<String> names, List<String> ownerNames) {
  }

  /**
   * Подставляет имя объекта-владельца в generic-плейсхолдеры типов параметров.
   * Плейсхолдер у параметра ровно один ({@code <Имя справочника>}), кроме таблиц
   * внешних источников данных — там их два, и однозначной подстановки нет,
   * поэтому такие типы остаются обобщёнными.
   */
  private static List<MemberDescriptor> specializeByOwner(List<MemberDescriptor> parameters, String ownerName) {
    if (ownerName.isEmpty()) {
      return parameters;
    }
    var result = new ArrayList<MemberDescriptor>(parameters.size());
    for (var parameter : parameters) {
      var placeholder = PlaceholderBinder.singlePlaceholder(parameter);
      result.add(placeholder == null
        ? parameter
        : parameter.specialize(Map.of(placeholder, ownerName)));
    }
    return List.copyOf(result);
  }

  /**
   * Тип структуры параметров формы ({@code ДанныеФормыСтруктура.<mdoRef>}). Наполняется
   * из двух источников: стандартные параметры платформы — общие для любой формы и
   * специфичные для вида основных данных (см. {@link FormParametersResolver}), — плюс
   * объявленные в самой форме (блок {@code <Parameters>}).
   * <p>
   * Generic-плейсхолдеры в типах параметров ({@code Ключ} у справочника объявлен как
   * {@code СправочникСсылка.<Имя справочника>}) подставляются именем владельца формы.
   */
  @Nullable TypeRef registerParametersStructure(Form form, FormKind kind, String suffixRu,
                                                        @Nullable MD owner) {
    if (kind != FormKind.MANAGED) {
      // У обычной формы отдельные параметры платформа отдаёт свойствами расширения
      // (`ПараметрОснование`, `ПараметрОбъектКопирования`), структуры `Параметры` нет.
      return null;
    }
    var baseParameters = formParametersResolver.parametersOf(kind.baseTypeRu());
    var extensionParameters = parameterExtensionParameters(form.getData().getAttributes(), kind);
    var ownParameters = form.getData().getParameters();
    if (baseParameters.isEmpty() && extensionParameters.isEmpty() && ownParameters.isEmpty()) {
      return null;
    }
    var structureRef = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_STRUCTURE_RU).orElse(null);
    var parametersRef = formDataTypes.registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_STRUCTURE_RU, FormPlatformTypes.FORM_DATA_STRUCTURE_EN,
      suffixRu, FormPlatformTypes.mdoSuffixEn(form), structureRef);
    var ownerName = FormPlatformTypes.ownerName(form);
    typeRegistry.registerMemberSource(parametersRef,
      () -> declaredFirst(buildParameterMembers(ownParameters),
        withBasisType(specializeByOwner(FormPlatformTypes.concat(baseParameters, extensionParameters), ownerName), owner)),
      FileType.BSL);
    // Параметров у формы единицы, и знать их состав важнее, чем имя типа: показываем
    // список целиком. Данные основного реквизита так не помечаем — свойств там сотни.
    if (structureRef != null) {
      typeRegistry.registerOpenStructure(parametersRef, structureRef);
    }
    if (!(owner instanceof InformationRegister)) {
      // `ИсходныйКлючЗаписи` объявлен у самой ДанныеФормыСтруктура и потому достаётся
      // по наследству любой форме. Смысл он имеет только у формы записи регистра
      // сведений: платформа кладёт туда ключ записи, которую открыли на изменение.
      typeRegistry.registerMemberSuppression(parametersRef, List.of(SOURCE_RECORD_KEY), FileType.BSL);
    }
    return parametersRef;
  }

  /**
   * Параметры, объявленные в самой форме. Собираются как реквизиты — имя и объявленный
   * тип, — но в типы данных формы не переводятся: параметром передают значение как есть.
   *
   * @param parameters блок {@code <Parameters>} формы.
   * @return дескрипторы параметров как свойств структуры.
   */
  private List<MemberDescriptor> buildParameterMembers(List<FormParameter> parameters) {
    if (parameters.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(parameters.size());
    for (var parameter : parameters) {
      var name = parameter.getName();
      if (name.isBlank()) {
        continue;
      }
      result.add(MemberDescriptor.property(name, ValueTypes.resolve(typeRegistry, parameter.getValueType()), "")
        .withBilingualName(FormPlatformTypes.neutral(name)));
    }
    return List.copyOf(result);
  }

  /**
   * Объединяет объявленные в форме параметры со стандартными: при совпадении имён
   * выигрывает объявление формы — оно и есть то, что реально приходит в параметр.
   */
  private static List<MemberDescriptor> declaredFirst(List<MemberDescriptor> declared,
                                                      List<MemberDescriptor> standard) {
    if (declared.isEmpty()) {
      return standard;
    }
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(declared.size() + standard.size());
    for (var member : declared) {
      byName.putIfAbsent(member.name().toLowerCase(Locale.ROOT), member);
    }
    for (var member : standard) {
      byName.putIfAbsent(member.name().toLowerCase(Locale.ROOT), member);
    }
    return List.copyOf(byName.values());
  }

  /**
   * Проставляет тип параметру-основанию. Платформа объявляет его без типа: какие объекты
   * допустимы, известно только из метаданных владельца формы ({@code ВводитсяНаОсновании}).
   *
   * @param parameters параметры формы.
   * @param owner      объект, которому подчинена форма.
   * @return они же; у параметра-основания проставлен тип.
   */
  private List<MemberDescriptor> withBasisType(List<MemberDescriptor> parameters, @Nullable MD owner) {
    if (parameters.stream().noneMatch(FormPlatformTypes::parameterOfBasis)) {
      return parameters;
    }
    var basisTypes = basisTypes(owner);
    return parameters.stream()
      .map(parameter -> FormPlatformTypes.parameterOfBasis(parameter)
        ? parameter.withReturnTypes(basisTypes)
        : parameter)
      .toList();
  }

  /**
   * Типы объектов, которые допустимо передать основанием. Свойство
   * {@code ВводитсяНаОсновании} перечисляет сами объекты, а основанием передаётся ссылка
   * на объект — поэтому имена типов ссылочные.
   *
   * @param owner объект, которому подчинена форма.
   * @return объединение ссылочных типов; {@code Неопределено}, если объект ни на чём
   *   не вводится — тогда передавать в параметр нечего.
   */
  private TypeSet basisTypes(@Nullable MD owner) {
    if (owner == null) {
      return UNDEFINED;
    }
    var refs = MdoPropertyAccessors.basedOn(owner).stream()
      .map(basis -> FormPlatformTypes.typeNameWithSuffix(basis.getMdoRefRu(), REF_SUFFIX))
      .map(typeRegistry::resolve)
      .flatMap(Optional::stream)
      .toList();
    return refs.isEmpty() ? UNDEFINED : TypeSet.of(refs);
  }

  /**
   * Имена объектов, которыми специализируются типы параметров обычной формы. Обычно
   * это один владелец формы, но у журнала документов «текущая строка» — любой из
   * зарегистрированных в нём документов: собственного ссылочного типа у журнала нет,
   * и платформа объявляет параметр как {@code ДокументСсылка.<Имя документа>}.
   *
   * @return имена для подстановки; пусто — специализировать нечем.
   */
  private static List<String> parameterOwnerNames(Form form, @Nullable MD owner) {
    if (owner instanceof DocumentJournal journal) {
      return journal.getRegisteredDocuments().stream()
        .map(documentRef -> FormPlatformTypes.shortName(documentRef.getMdoRefRu()))
        .filter(name -> !name.isBlank())
        .toList();
    }
    var ownerName = FormPlatformTypes.ownerName(form);
    return ownerName.isEmpty() ? List.of() : List.of(ownerName);
  }

  /**
   * Параметры формы, специфичные для вида её основных данных. Кандидаты перебираются
   * по приоритету: у типа, которого нет в синтакс-помощнике, параметров не окажется.
   */
  private List<MemberDescriptor> parameterExtensionParameters(List<FormAttribute> attributes, FormKind kind) {
    return FormPlatformTypes.mainAttributeTypeNames(attributes, valueTypeRu ->
      FormPlatformTypes.parameterExtensionTypeNames(valueTypeRu, kind))
      .map(formParametersResolver::parametersOf)
      .filter(parameters -> !parameters.isEmpty())
      .findFirst()
      .orElse(List.of());
  }

  /**
   * Тип коллекции команд формы ({@code КомандыФормы.<mdoRef>}): устроен как коллекция
   * элементов, только имена берутся из блока {@code <Commands>}, а тип у всех один —
   * {@code КомандаФормы}.
   *
   * @return тип коллекции; {@code null}, если команд у формы нет — тогда свойство
   *   {@code Команды} остаётся обобщённым.
   */
  @Nullable TypeRef registerCommandsCollection(Form form, FormKind kind, String suffixRu) {
    var commands = form.getData().getCommands();
    if (kind != FormKind.MANAGED || commands.isEmpty()) {
      return null;
    }
    var commandsRef = typeFactory.registerWithAlias(
      FormPlatformTypes.FORM_COMMANDS_RU + "." + suffixRu,
      FormPlatformTypes.FORM_COMMANDS_EN + "." + FormPlatformTypes.mdoSuffixEn(form));
    var baseRef = typeRegistry.resolve(FormPlatformTypes.FORM_COMMANDS_RU).orElse(null);
    if (baseRef != null) {
      typeRegistry.registerExtension(commandsRef, baseRef, FileType.BSL);
      typeRegistry.inheritCollectionTraits(commandsRef, baseRef, FileType.BSL);
    }
    var commandRef = typeRegistry.resolve(FormPlatformTypes.FORM_COMMAND_RU).orElse(null);
    typeRegistry.registerMemberSource(commandsRef, () -> buildCommandMembers(commands, commandRef), FileType.BSL);
    return commandsRef;
  }

  /** Команды формы как свойства коллекции: имя из {@code Form.xml}, тип у всех общий. */
  private static List<MemberDescriptor> buildCommandMembers(List<FormCommand> commands,
                                                            @Nullable TypeRef commandRef) {
    var types = commandRef == null ? TypeSet.EMPTY : TypeSet.of(commandRef);
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(commands.size());
    for (var command : commands) {
      var name = command.getName();
      if (name.isBlank()) {
        continue;
      }
      byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
        MemberDescriptor.property(name, types, "")
          .withBilingualName(FormPlatformTypes.neutral(name))
          .withBilingualDescription(FormPlatformTypes.bilingual(command.getTitle())));
    }
    return List.copyOf(byName.values());
  }

  /**
   * Процедуры-обработчики команд. Обработчик команды объявлен не событием, а действием
   * ({@code <Action>}), но по сути это то же самое: процедура модуля формы, которую
   * зовёт платформа.
   */
  List<MemberDescriptor> buildCommandHandlers(List<FormCommand> commands) {
    if (commands.isEmpty()) {
      return List.of();
    }
    var signatures = List.of(commandHandlerSignature());
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(commands.size());
    for (var command : commands) {
      var action = command.getAction();
      if (action.isBlank()) {
        continue;
      }
      byName.putIfAbsent(action.toLowerCase(Locale.ROOT),
        MemberDescriptor.event(action, "", signatures)
          .withBilingualName(FormPlatformTypes.neutral(action))
          .withBilingualDescription(BilingualString.of(
            "Обработчик команды формы «" + command.getName() + "».",
            "Handler of the form command \"" + command.getName() + "\".")));
    }
    return List.copyOf(byName.values());
  }

  /**
   * Контракт обработчика команды. В синтакс-помощнике его нет: платформа передаёт
   * в обработчик саму команду, но нигде этого не объявляет — сигнатура собирается здесь.
   */
  private SignatureDescriptor commandHandlerSignature() {
    var commandTypes = typeRegistry.resolve(FormPlatformTypes.FORM_COMMAND_RU)
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
    return new SignatureDescriptor(
      List.of(new ParameterDescriptor(
        BilingualString.of("Команда", "Command"), commandTypes, false,
        BilingualString.of("Команда формы, которой вызван обработчик.",
          "The form command the handler was invoked by."),
        "")),
      TypeSet.EMPTY, BilingualString.EMPTY);
  }

  /**
   * Параметры обычной формы с подставленным именем объекта-владельца. Платформа
   * объявляет их через плейсхолдер: у формы списка справочника
   * {@code ПараметрТекущаяСтрока} — это {@code СправочникСсылка.<Имя справочника>},
   * у формы списка записей регистра — {@code РегистрСведенийКлючЗаписи.<Имя регистра
   * сведений>}. Какой именно тип получится, решает синтакс-помощник, а не таблица
   * соответствий здесь: подставляется только имя.
   * <p>
   * Override, а не обычный источник: те же члены приходят из расширения формы с
   * необработанным плейсхолдером и должны проиграть дедуп в {@code getMembers}.
   */
  private List<MemberDescriptor> ordinaryParameters(TypeRef extensionRef, OrdinaryFormOwner owner,
                                                    @Nullable MD ownerMd) {
    var result = new ArrayList<MemberDescriptor>();
    for (var member : typeRegistry.getMembers(extensionRef, FileType.BSL)) {
      if (FormPlatformTypes.parameterOfBasis(member)) {
        // Плейсхолдера в нём нет — типа не объявлено вовсе, он берётся из метаданных.
        result.add(member.withReturnTypes(basisTypes(ownerMd)));
        continue;
      }
      var bound = PlaceholderBinder.bind(typeRegistry, member, specializationNames(member, owner));
      if (bound == null) {
        // Не подставилось — возможно, объявление ссылается на чужое семейство
        // регистров: у формы списка регистра расчёта `ПараметрТекущаяСтрока` объявлен
        // как `РегистрНакопленияКлючЗаписи.<Имя регистра накопления>`.
        bound = RegisterFamilies.ownFamilyMember(typeRegistry, member, owner.mdoRef());
      }
      if (bound != null) {
        result.add(bound);
      }
    }
    return List.copyOf(result);
  }

  /** Имена владельцев подчинённого справочника; пусто — справочник не подчинён. */
  private static List<String> ownerObjectNames(@Nullable MD owner) {
    if (!(owner instanceof Catalog catalog)) {
      return List.of();
    }
    return catalog.getOwners().stream()
      .map(ref -> FormPlatformTypes.shortName(ref.getMdoRefRu()))
      .filter(name -> !name.isBlank())
      .toList();
  }

  /**
   * Чьи имена подставлять в тип конкретного параметра. У большинства — объекта,
   * которому подчинена форма; у отбора по регистратору — документов, пишущих движения
   * в этот регистр; у параметров про посторонний объект (владелец подчинённого
   * справочника) подставлять нечего, и они остаются обобщёнными.
   */
  private List<String> specializationNames(MemberDescriptor member, OrdinaryFormOwner owner) {
    if (FormPlatformTypes.parameterOfRecorder(member)) {
      return recorderIndex.recordersOf(owner.mdoRef());
    }
    if (FormPlatformTypes.parameterOfAnotherObject(member)) {
      return owner.ownerNames();
    }
    return owner.names();
  }



  /**
   * Параметры обычной формы: подставляет в плейсхолдеры имя объекта-владельца и
   * убирает те, которых у этой формы не существует.
   *
   * @param formRef      тип формы.
   * @param extensionRef тип-расширение обычной формы.
   * @param form         форма.
   * @param owner        объект, которому подчинена форма; {@code null} — общая форма.
   */
  void registerOrdinaryParameters(TypeRef formRef, TypeRef extensionRef, Form form, @Nullable MD owner) {
    var parameterOwner = new OrdinaryFormOwner(
      owner == null ? "" : owner.getMdoReference().getMdoRefRu(),
      parameterOwnerNames(form, owner),
      ownerObjectNames(owner));
    typeRegistry.registerMemberOverride(formRef,
      () -> ordinaryParameters(extensionRef, parameterOwner, owner), FileType.BSL);
    // Подавление — сразу, а не в ленивом источнике: реестр хранит его именами, и
    // список надо знать на регистрации. Индекс регистраторов к этому моменту уже
    // наполнен (ConfigurationTypesProvider индексирует их до регистрации форм).
    typeRegistry.registerMemberSuppression(formRef,
      FormPlatformTypes.absentParameters(parameterOwner.names(), parameterOwner.ownerNames(),
        recorderIndex.recordersOf(parameterOwner.mdoRef())),
      FileType.BSL);
  }
}
