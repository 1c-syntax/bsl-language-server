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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.FormOwner;
import com.github._1c_syntax.bsl.mdo.support.DefaultFormKind;
import com.github._1c_syntax.bsl.types.MdoReference;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Резолв типа формы по её имени-строке — тому, что передают в {@code ОткрытьФорму}
 * и {@code ПолучитьФорму}.
 * <p>
 * Платформа принимает две записи имени, обе поддержаны:
 * <ul>
 *   <li><b>точное имя формы</b> — {@code "Справочник.Контрагенты.Форма.ФормаЭлемента"};
 *       совпадает с суффиксом синтетического типа формы, поэтому резолвится прямым
 *       обращением в реестр;</li>
 *   <li><b>основная форма объекта</b> — {@code "Справочник.Контрагенты.ФормаОбъекта"};
 *       вид основной формы ищется среди {@link DefaultFormKind} по его же имени из
 *       mdclasses, а конкретная форма — через {@link FormOwner#getDefaultForm}.</li>
 * </ul>
 * Имя может быть записано на любом из двух языков ({@code "Catalog.Контрагенты.Form.X"});
 * перебираются оба базовых имени типа формы.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class FormByNameResolver {

  /**
   * Базовые имена типов форм в обеих локалях: точное имя формы — это суффикс
   * синтетического типа, а базовая часть зависит и от вида формы, и от языка,
   * на котором записана ссылка.
   */
  private static final List<String> FORM_TYPE_PREFIXES = List.of(
    FormKind.MANAGED.baseTypeRu(),
    FormKind.MANAGED.baseTypeEn(),
    FormKind.ORDINARY.baseTypeRu(),
    FormKind.ORDINARY.baseTypeEn()
  );

  /**
   * Методы менеджера, отдающие основную форму объекта, и вид этой формы. Имени формы
   * они не требуют: какую именно открывать — известно из роли формы у объекта.
   * Имена проверяются по дескриптору члена, поэтому английские написания работают
   * без отдельной таблицы.
   */
  private static final Map<String, DefaultFormKind> MANAGER_FORM_METHODS = Map.of(
    "ПолучитьФормуСписка", DefaultFormKind.LIST_FORM,
    "ПолучитьФормуВыбора", DefaultFormKind.CHOICE_FORM,
    "ПолучитьФормуВыбораГруппы", DefaultFormKind.FOLDER_CHOICE_FORM,
    "ПолучитьФормуНовогоЭлемента", DefaultFormKind.OBJECT_FORM,
    "ПолучитьФормуНовойГруппы", DefaultFormKind.FOLDER_FORM,
    "ПолучитьФормуНовогоДокумента", DefaultFormKind.OBJECT_FORM,
    "ПолучитьФорму", DefaultFormKind.DEFAULT_FORM);

  private static final List<String> MANAGER_SUFFIXES = List.of("Менеджер", "Manager");

  private final TypeRegistry typeRegistry;

  /**
   * Тип формы по её имени из кода.
   *
   * @param documentContext документ, из которого идёт обращение — нужен для доступа
   *                        к конфигурации при резолве основной формы объекта.
   * @param formName        имя формы как оно записано в коде (без кавычек).
   * @return тип конкретной формы; empty, если такой формы в конфигурации нет либо
   *   типы форм ещё не зарегистрированы.
   */
  public Optional<TypeRef> resolve(DocumentContext documentContext, String formName) {
    if (formName == null || formName.isBlank()) {
      return Optional.empty();
    }
    var exact = resolveExactName(formName);
    if (exact.isPresent()) {
      return exact;
    }
    return resolveDefaultForm(documentContext, formName);
  }

  /** Точное имя формы — суффикс синтетического типа; базовое имя перебирается. */
  private Optional<TypeRef> resolveExactName(String formName) {
    for (var prefix : FORM_TYPE_PREFIXES) {
      var resolved = typeRegistry.resolve(prefix + "." + formName);
      if (resolved.isPresent()) {
        return resolved;
      }
    }
    return Optional.empty();
  }

  /**
   * Основная форма объекта: {@code <ссылка на объект>.<вид основной формы>}.
   * Виды перебираются, а не берутся по карте, потому что имя вида не уникально —
   * {@code ФормаЗаписи} принадлежит и {@link DefaultFormKind#RECORD_FORM}, и
   * {@link DefaultFormKind#SAVE_FORM}; выигрывает тот, у которого форма задана.
   */
  private Optional<TypeRef> resolveDefaultForm(DocumentContext documentContext, String formName) {
    var separator = formName.lastIndexOf('.');
    if (separator <= 0 || separator == formName.length() - 1) {
      return Optional.empty();
    }
    var ownerName = formName.substring(0, separator);
    var kindName = formName.substring(separator + 1);
    var owner = findFormOwner(documentContext, ownerName);
    if (owner.isEmpty()) {
      return Optional.empty();
    }
    for (var kind : DefaultFormKind.values()) {
      if (!matchesKind(kind, kindName)) {
        continue;
      }
      var resolved = owner.get().getDefaultForm(kind).flatMap(this::resolveForm);
      if (resolved.isPresent()) {
        return resolved;
      }
    }
    return Optional.empty();
  }

  /**
   * Тип формы, которую отдаёт метод менеджера объекта:
   * {@code Справочники.Номенклатура.ПолучитьФормуСписка()} — это форма списка именно
   * этого справочника, а не обобщённая {@code Форма}.
   * <p>
   * Имя формы у этих методов необязательно и задаётся <b>относительно объекта</b>
   * ({@code "ФормаЭлемента"}), поэтому общий резолв по полному имени тут не подходит:
   * объект берётся из типа получателя ({@code СправочникМенеджер.Номенклатура}), а
   * форма — среди его собственных.
   *
   * @param documentContext документ, из которого идёт обращение.
   * @param receiver        типы получателя вызова.
   * @param methodName      имя вызванного метода как оно записано в коде.
   * @param formName        имя формы из строкового литерала; {@code null} — не задано.
   * @param fileType        язык файла, в котором ищется член получателя.
   * @return тип конкретной формы; empty, если это не тот метод либо формы нет.
   */
  public Optional<TypeRef> resolveManagerForm(DocumentContext documentContext, TypeSet receiver,
                                              String methodName, @Nullable String formName,
                                              FileType fileType) {
    for (var ref : receiver.refs()) {
      var ownerName = managerOwnerName(ref.qualifiedName());
      if (ownerName == null) {
        continue;
      }
      var kind = managerFormKind(ref, methodName, fileType);
      if (kind == null) {
        continue;
      }
      var owner = findFormOwner(documentContext, ownerName);
      if (owner.isEmpty()) {
        continue;
      }
      var resolved = ownForm(owner.get(), formName)
        .or(() -> owner.get().getDefaultForm(kind))
        .flatMap(this::resolveForm);
      if (resolved.isPresent()) {
        return resolved;
      }
    }
    return Optional.empty();
  }

  /** Вид формы по вызванному методу; {@code null} — метод не про формы объекта. */
  private @Nullable DefaultFormKind managerFormKind(TypeRef receiverRef, String methodName, FileType fileType) {
    for (var member : typeRegistry.getMembers(receiverRef, fileType)) {
      if (member.kind() != MemberKind.METHOD || !member.matches(methodName)) {
        continue;
      }
      for (var entry : MANAGER_FORM_METHODS.entrySet()) {
        if (member.matches(entry.getKey())) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  /** {@code СправочникМенеджер.Номенклатура} → {@code Справочник.Номенклатура}. */
  private static @Nullable String managerOwnerName(String qualifiedName) {
    var dot = qualifiedName.indexOf('.');
    if (dot <= 0) {
      return null;
    }
    var group = qualifiedName.substring(0, dot);
    for (var suffix : MANAGER_SUFFIXES) {
      if (group.length() > suffix.length() && group.endsWith(suffix)) {
        return group.substring(0, group.length() - suffix.length()) + qualifiedName.substring(dot);
      }
    }
    return null;
  }

  /** Собственная форма объекта по имени: у методов менеджера оно относительное. */
  private static Optional<Form> ownForm(FormOwner owner, @Nullable String formName) {
    if (formName == null || formName.isBlank()) {
      return Optional.empty();
    }
    return owner.getForms().stream()
      .filter(form -> formName.equalsIgnoreCase(form.getName()))
      .map(Form.class::cast)
      .findFirst();
  }

  private static Optional<FormOwner> findFormOwner(DocumentContext documentContext, String ownerName) {
    return MdoReference.find(ownerName)
      .flatMap(ref -> documentContext.getServerContext().getConfiguration().findChild(ref))
      .filter(FormOwner.class::isInstance)
      .map(FormOwner.class::cast);
  }

  private Optional<TypeRef> resolveForm(Form form) {
    return typeRegistry.resolve(FormTypesProvider.selfTypeQualifiedName(form));
  }

  private static boolean matchesKind(DefaultFormKind kind, String name) {
    var fullName = kind.fullName();
    return name.equalsIgnoreCase(fullName.getRu()) || name.equalsIgnoreCase(fullName.getEn());
  }
}
