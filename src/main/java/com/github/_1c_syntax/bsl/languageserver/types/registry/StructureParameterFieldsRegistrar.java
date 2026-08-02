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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LocalField;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Проставляет известный состав структурам, которые платформа передаёт в параметры
 * своих обработчиков (см. {@link FormPlatformTypes#PREDEFINED_STRUCTURE_PARAMETERS}).
 * <p>
 * Устроено как доопределение типа у бестиповых свойств элементов формы: платформенное
 * объявление берётся целиком, меняется в нём ровно одно — набор типов параметра, к
 * которому прикрепляются поля. Описание, двуязычное имя, доступность и версии остаются
 * платформенными.
 * <p>
 * Правка ложится на тип, где параметр <b>объявлен</b> (расширение формы, расширение
 * элемента), а не на каждую форму: состав зависит от вида владельца формы, а вид
 * владельца — это и есть выбор расширения. Формы наследуют объявление через
 * {@code registerExtension}, поэтому и обработчик, названный по имени события, и
 * переименованный по {@code Form.xml} видят один и тот же контракт.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class StructureParameterFieldsRegistrar {

  private final TypeRegistry typeRegistry;

  /**
   * Регистрирует состав по всем записям словаря. Запись, которой в реестре не нашлось
   * соответствия (нет синтакс-помощника — расширений в JSON-фолбэке почти нет), молча
   * пропускается: словарь сверяется с платформой HBK-тестом, а не падением на пользователе.
   */
  void register() {
    var structureRef = typeRegistry.resolve(FormPlatformTypes.STRUCTURE_RU).orElse(null);
    if (structureRef == null) {
      return;
    }
    for (var parameter : FormPlatformTypes.PREDEFINED_STRUCTURE_PARAMETERS) {
      registerParameter(parameter, structureRef);
    }
  }

  private void registerParameter(FormPlatformTypes.StructureParameter parameter, TypeRef structureRef) {
    var ownerRef = typeRegistry.resolve(parameter.ownerTypeName()).orElse(null);
    if (ownerRef == null) {
      return;
    }
    var fields = fieldsOf(parameter);
    var rebuilt = new ArrayList<MemberDescriptor>();
    // Члены владельца читаются здесь же, на регистрации, а в источник уходит готовый
    // список: источник живёт на том же типе, чьи члены читает, и ленивое чтение
    // зациклило бы getMembers.
    for (var member : typeRegistry.getMembers(ownerRef, FileType.BSL)) {
      var withFields = withStructureFields(member, parameter.parameterName(), structureRef, fields);
      if (withFields != null) {
        rebuilt.add(withFields);
      }
    }
    if (!rebuilt.isEmpty()) {
      typeRegistry.registerMemberOverride(ownerRef, () -> rebuilt, FileType.BSL);
    }
  }

  /**
   * Поля структуры по описанию из словаря: оба написания имени — отдельными ключами.
   * Ключ структуры это строка, а не идентификатор, и сопоставляется по себе самой,
   * поэтому написание, которого нет в наборе, просто не резолвится.
   * <p>
   * Тип значения, которого в реестре не оказалось, поле не отменяет: имя ключа само по
   * себе снимает ложное срабатывание {@code UnknownMember} и попадает в автодополнение.
   */
  private Map<String, LocalField> fieldsOf(FormPlatformTypes.StructureParameter parameter) {
    var fields = LinkedHashMap.<String, LocalField>newLinkedHashMap(parameter.fields().size() * 2);
    for (var field : parameter.fields()) {
      var types = TypeSet.EMPTY;
      for (var typeName : field.typeNames()) {
        var typeRef = typeRegistry.resolve(typeName).orElse(null);
        if (typeRef != null) {
          types = types.add(typeRef);
        }
      }
      var value = LocalField.of(types);
      fields.put(field.name().ru(), value);
      fields.putIfAbsent(field.name().en(), value);
    }
    return fields;
  }

  /**
   * Копия члена, у которого одноимённому параметру прикреплены поля структуры.
   *
   * @param member        платформенное объявление члена.
   * @param parameterName ru-имя параметра из словаря; сравнивается по обоим написаниям.
   * @param structureRef  тип {@code Структура} — носитель полей. Если платформа тип у
   *                      параметра не объявила (так у форм задачи и бизнес-процесса), он
   *                      этой же правкой и появляется: {@code withFields} добавляет
   *                      носителя в набор.
   * @param fields        поля структуры.
   * @return копия члена; {@code null}, если параметра с таким именем у члена нет.
   */
  private static @Nullable MemberDescriptor withStructureFields(MemberDescriptor member, String parameterName,
                                                                TypeRef structureRef,
                                                                Map<String, LocalField> fields) {
    var signatures = new ArrayList<SignatureDescriptor>(member.signatures().size());
    var changed = false;
    for (var signature : member.signatures()) {
      var parameters = new ArrayList<ParameterDescriptor>(signature.parameters().size());
      var signatureChanged = false;
      for (var parameter : signature.parameters()) {
        if (parameter.matches(parameterName)) {
          parameters.add(new ParameterDescriptor(parameter.bilingualName(),
            parameter.types().withFields(structureRef, fields), parameter.optional(),
            parameter.bilingualDescription(), parameter.defaultValue(), parameter.variadic()));
          signatureChanged = true;
        } else {
          parameters.add(parameter);
        }
      }
      signatures.add(signatureChanged
        ? new SignatureDescriptor(parameters, signature.returnTypes(),
        signature.bilingualDescription(), signature.metadata())
        : signature);
      changed |= signatureChanged;
    }
    return changed ? member.withSignatures(List.copyOf(signatures)) : null;
  }
}
