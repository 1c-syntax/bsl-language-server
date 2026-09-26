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
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.CommonAttribute;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.StandardAttribute;
import com.github._1c_syntax.bsl.mdo.support.UseMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Члены типа из реквизитов метаданных: собственных, стандартных и общих.
 * <p>
 * Одни и те же реквизиты становятся членами в нескольких раскладках — у
 * объектного и ссылочного типа объекта ({@link ConfigurationTypesProvider}) и у
 * таблицы языка запросов ({@link MetadataFieldSource}), — поэтому сборка вынесена
 * из владельцев в отдельный компонент.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class MdoMemberFactory {

  private final TypeRegistry typeRegistry;

  /**
   * Члены из реквизитов объекта метаданных.
   *
   * @param attributes           реквизиты — собственные, стандартные, а у регистров
   *                             также измерения и ресурсы.
   * @param platformDescriptions описания стандартных реквизитов из синтакс-помощника,
   *                             по ru-имени в нижнем регистре.
   * @param platformMetadata     страничные метаданные стандартных реквизитов, там же.
   * @return члены-свойства; пусто, если реквизитов нет.
   */
  List<MemberDescriptor> attributeMembers(List<? extends Attribute> attributes,
                                          Map<String, BilingualString> platformDescriptions,
                                          Map<String, PlatformMetadata> platformMetadata) {
    if (attributes.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(attributes.size());
    for (var attribute : attributes) {
      var bilingualName = attributeBilingualName(attribute);
      var returnTypes = attributeReturnTypes(attribute);
      // Стандартный реквизит платформа объявляет сама — с типом, описанием и мета.
      // mdclasses про его тип знает не всегда (у `Владелец` типа нет вовсе), и
      // бестиповый дубль отсюда только перекрыл бы объявление платформы.
      if (bilingualName.isEmpty() || (returnTypes.isEmpty() && attribute instanceof StandardAttribute)) {
        continue;
      }
      // По ru-имени матчим описания/мета платформы — словарь HBK именован по-русски.
      var lc = bilingualName.primary().toLowerCase(Locale.ROOT);
      var description = platformDescriptions.getOrDefault(lc, BilingualString.EMPTY);
      var meta = platformMetadata.getOrDefault(lc, PlatformMetadata.EMPTY);
      var descriptor = property(bilingualName, returnTypes);
      if (!description.isEmpty()) {
        descriptor = descriptor.withBilingualDescription(description);
      }
      if (!meta.isEmpty()) {
        descriptor = descriptor.withMetadata(meta);
      }
      // Стандартные реквизиты (Наименование/Код/Ссылка/…) — часть платформенной
      // объектной модели; собственные реквизиты конфигурации — нет.
      if (attribute instanceof StandardAttribute) {
        descriptor = descriptor.withStandardLibrary(true);
      }
      result.add(descriptor);
    }
    return result;
  }

  /**
   * Члены из реквизитов без подмеса платформенных описаний — там, где владельца
   * реквизитов под рукой нет (например, у колонок табличной части).
   *
   * @param attributes реквизиты.
   * @return члены-свойства; пусто, если реквизитов нет.
   */
  List<MemberDescriptor> attributeMembers(List<? extends Attribute> attributes) {
    return attributeMembers(attributes, Map.of(), Map.of());
  }

  /**
   * Члены из общих реквизитов.
   *
   * @param commonAttributes общие реквизиты, применимые к объекту.
   * @return члены-свойства; пусто, если общих реквизитов нет.
   */
  List<MemberDescriptor> commonAttributeMembers(List<CommonAttribute> commonAttributes) {
    if (commonAttributes.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(commonAttributes.size());
    for (var commonAttribute : commonAttributes) {
      var name = commonAttribute.getName();
      if (name.isBlank()) {
        continue;
      }
      result.add(property(BilingualString.of(name),
        ValueTypes.resolve(typeRegistry, commonAttribute.getValueType())));
    }
    return result;
  }

  /**
   * Описания стандартных реквизитов из синтакс-помощника: {@code имя(lower) → описание}.
   * Берутся с платформенных generic-типов {@code <fullRu>Ссылка.<…>} и
   * {@code <fullRu>Объект.<…>} — в метаданных описаний у стандартных реквизитов нет.
   * Двуязычность нужна, чтобы hover показывал описание в текущей локали.
   *
   * @param fullRu ru-имя вида объекта метаданных ({@code Справочник}).
   * @return описания по ru-имени члена в нижнем регистре.
   */
  Map<String, BilingualString> platformDescriptions(String fullRu) {
    var result = new HashMap<String, BilingualString>();
    addPlatformDescriptionsTo(result, fullRu + "Ссылка");
    addPlatformDescriptionsTo(result, fullRu + "Объект");
    return result;
  }

  /**
   * Страничные метаданные стандартных реквизитов одного семейства
   * ({@code Справочник} + {@code Ссылка}): режим доступа и версии у объекта и у
   * ссылки различаются, поэтому семейство указывается целиком.
   *
   * @param familyPrefix префикс семейства ({@code СправочникСсылка}).
   * @return метаданные по ru-имени члена в нижнем регистре.
   */
  Map<String, PlatformMetadata> platformMetadata(String familyPrefix) {
    var generic = typeRegistry.resolveGenericByPrefix(familyPrefix).orElse(null);
    if (generic == null) {
      return Map.of();
    }
    var result = new HashMap<String, PlatformMetadata>();
    for (var member : typeRegistry.getMembers(generic, FileType.BSL)) {
      var meta = member.metadata();
      if (member.generic() || meta.isEmpty()) {
        continue;
      }
      result.putIfAbsent(member.name().toLowerCase(Locale.ROOT), meta);
    }
    return result;
  }

  /**
   * Общие реквизиты, применимые к объекту метаданных. Если объект явно присутствует в
   * составе общего реквизита, используется его персональный режим, иначе —
   * {@link CommonAttribute#getAutoUse()}. Включаются режимы USE/USE_WITH_WARNINGS.
   *
   * @param md  объект метаданных.
   * @param all все общие реквизиты конфигурации.
   * @return применимые общие реквизиты; пусто, если таких нет.
   */
  static List<CommonAttribute> applicableCommonAttributes(MD md, List<CommonAttribute> all) {
    if (all.isEmpty()) {
      return List.of();
    }
    var mdoRef = md.getMdoReference();
    var result = new ArrayList<CommonAttribute>();
    for (var commonAttribute : all) {
      var effective = commonAttribute.contains(mdoRef)
        ? commonAttribute.useMode(mdoRef)
        : commonAttribute.getAutoUse();
      if (effective == UseMode.USE || effective == UseMode.USE_WITH_WARNINGS) {
        result.add(commonAttribute);
      }
    }
    return result;
  }

  /**
   * Двуязычное имя реквизита. Стандартные реквизиты (Дата/Номер/Ссылка/…) хранят оба
   * написания, поэтому член находится по любому из них, а completion показывает имя
   * в нужной локали. У собственного реквизита конфигурации имя одно.
   *
   * @param attribute реквизит.
   * @return имя; {@link BilingualString#EMPTY}, если имени нет.
   */
  static BilingualString attributeBilingualName(Attribute attribute) {
    if (attribute instanceof StandardAttribute std) {
      var fullName = std.getFullName();
      if (!fullName.isEmpty()) {
        var ru = fullName.get("ru");
        var en = fullName.get("en");
        if (!ru.isBlank() && !en.isBlank()) {
          return BilingualString.of(ru, en);
        }
        if (!ru.isBlank()) {
          return BilingualString.of(ru);
        }
        if (!en.isBlank()) {
          return BilingualString.of("", en);
        }
      }
    }
    var name = attribute.getName();
    return name.isBlank() ? BilingualString.EMPTY : BilingualString.of(name);
  }

  /**
   * Типы значения реквизита — объединение, если тип составной.
   *
   * @param attribute реквизит.
   * @return набор типов; пустой, если тип не объявлен либо не разрезолвился.
   */
  TypeSet attributeReturnTypes(Attribute attribute) {
    return ValueTypes.resolve(typeRegistry, attribute.getValueType());
  }

  /**
   * Член-свойство с двуязычным именем и набором типов.
   *
   * @param name  двуязычное имя.
   * @param types типы значения; пустой набор — тип неизвестен.
   * @return дескриптор члена.
   */
  static MemberDescriptor property(BilingualString name, TypeSet types) {
    var primaryName = name.primary();
    MemberDescriptor descriptor;
    if (types.isEmpty()) {
      descriptor = MemberDescriptor.property(primaryName);
    } else if (types.size() == 1) {
      descriptor = MemberDescriptor.property(primaryName, types.refs().iterator().next(), "");
    } else {
      descriptor = MemberDescriptor.property(primaryName, types, "");
    }
    return descriptor.withBilingualName(name);
  }

  private void addPlatformDescriptionsTo(Map<String, BilingualString> sink, String familyPrefix) {
    var generic = typeRegistry.resolveGenericByPrefix(familyPrefix).orElse(null);
    if (generic == null) {
      return;
    }
    for (var member : typeRegistry.getMembers(generic, FileType.BSL)) {
      var description = member.bilingualDescription();
      if (member.generic() || description.isEmpty()) {
        continue;
      }
      sink.putIfAbsent(member.name().toLowerCase(Locale.ROOT), description);
    }
  }
}
