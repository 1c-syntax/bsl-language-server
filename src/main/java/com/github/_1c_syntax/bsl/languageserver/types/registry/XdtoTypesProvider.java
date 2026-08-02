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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.XDTOPackage;
import com.github._1c_syntax.bsl.mdo.storage.XdtoPackageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Регистрирует тип на каждый объектный тип XDTO-пакета конфигурации.
 * <p>
 * Имя типа собирается так же, как его пишут в ссылке документирующего комментария:
 * {@code XDTOПакет.<Пакет>.<Тип>} (англоязычный псевдоним — {@code XDTOPackage.<Пакет>.<Тип>}).
 * Благодаря этому запись {@code См. XDTOПакет.КонтактнаяИнформация.Адрес} резолвится
 * реестром напрямую, без отдельной нотации.
 * <p>
 * Члены типа — свойства объектного типа из схемы пакета. Их состав считается лениво:
 * в крупных конфигурациях объектных типов десятки тысяч, и материализовать их все
 * при разборе конфигурации незачем.
 * <p>
 * Тип свойства берётся из схемы: XSD-имя отображается в примитив 1С, ссылка на тип того
 * же пакета — в его зарегистрированный тип, а свойство без типа остаётся произвольным.
 * Ссылки на типы <b>другого</b> пакета не разрешаются: в схеме они записаны префиксом
 * пространства имён, а сопоставление префикса с пространством имён mdclasses не отдаёт.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
@Slf4j
public class XdtoTypesProvider {

  /** Префикс имени типа — тот же, что в записи ссылки. */
  private static final String PACKAGE_PREFIX_RU = "XDTOПакет.";

  private static final String PACKAGE_PREFIX_EN = "XDTOPackage.";

  /** Платформенный тип объекта XDTO: от него объектные типы пакета наследуют членов. */
  private static final String XDTO_DATA_OBJECT = "ОбъектXDTO";

  /** Префикс имён XML-схемы: {@code xs:string}, {@code xs:dateTime} и т.п. */
  private static final String XSD_PREFIX = "xs:";

  private static final String STRING = "Строка";
  private static final String NUMBER = "Число";
  private static final String BOOLEAN = "Булево";
  private static final String DATE = "Дата";
  private static final String BINARY_DATA = "ДвоичныеДанные";

  /** Отображение имён XML-схемы в примитивы 1С. */
  private static final Map<String, String> XSD_TO_BSL = Map.ofEntries(
    Map.entry("string", STRING),
    Map.entry("normalizedString", STRING),
    Map.entry("token", STRING),
    Map.entry("anyURI", STRING),
    Map.entry("QName", STRING),
    Map.entry("NCName", STRING),
    Map.entry("ID", STRING),
    Map.entry("IDREF", STRING),
    Map.entry("language", STRING),
    Map.entry("boolean", BOOLEAN),
    Map.entry("decimal", NUMBER),
    Map.entry("integer", NUMBER),
    Map.entry("int", NUMBER),
    Map.entry("long", NUMBER),
    Map.entry("short", NUMBER),
    Map.entry("byte", NUMBER),
    Map.entry("float", NUMBER),
    Map.entry("double", NUMBER),
    Map.entry("nonNegativeInteger", NUMBER),
    Map.entry("positiveInteger", NUMBER),
    Map.entry("nonPositiveInteger", NUMBER),
    Map.entry("negativeInteger", NUMBER),
    Map.entry("unsignedInt", NUMBER),
    Map.entry("unsignedLong", NUMBER),
    Map.entry("unsignedShort", NUMBER),
    Map.entry("unsignedByte", NUMBER),
    Map.entry("date", DATE),
    Map.entry("dateTime", DATE),
    Map.entry("time", DATE),
    Map.entry("base64Binary", BINARY_DATA),
    Map.entry("hexBinary", BINARY_DATA)
  );

  private final TypeRegistry typeRegistry;

  /**
   * Имя пакета по его пространству имён. В документирующих комментариях пакет называют
   * именем, а в коде — URI пространства имён ({@code ФабрикаXDTO.Тип(URI, "Адрес")}),
   * поэтому нужна связка одного с другим.
   */
  private final Map<String, String> packageByNamespace = new ConcurrentHashMap<>();

  /**
   * Зарегистрировать типы объектов всех XDTO-пакетов конфигурации.
   *
   * @param children объекты метаданных конфигурации.
   */
  public void register(Iterable<MD> children) {
    var packages = 0;
    for (var md : children) {
      if (md instanceof XDTOPackage xdtoPackage) {
        registerPackage(xdtoPackage);
        packages++;
      }
    }
    LOGGER.debug("XDTO packages registered: {}", packages);
  }

  /**
   * Тип объекта пакета по пространству имён и имени типа — так, как его адресует код
   * через {@code ФабрикаXDTO.Тип(URI, Имя)}.
   *
   * @param namespaceUri пространство имён пакета.
   * @param typeName     имя объектного типа.
   * @return тип объекта; {@link Optional#empty()}, если такого пакета или типа нет.
   */
  public Optional<TypeRef> resolveObjectType(String namespaceUri, String typeName) {
    if (namespaceUri.isBlank() || typeName.isBlank()) {
      return Optional.empty();
    }
    var packageName = packageByNamespace.get(namespaceUri);
    if (packageName == null) {
      return Optional.empty();
    }
    return typeRegistry.resolve(PACKAGE_PREFIX_RU + packageName + "." + typeName);
  }

  /**
   * Типы одного пакета: сперва заводятся все объектные типы, потом — наследование
   * между ними. Порядок важен: базовый тип может быть объявлен в схеме ниже наследника.
   */
  private void registerPackage(XDTOPackage xdtoPackage) {
    var data = xdtoPackage.getData();
    var packageName = xdtoPackage.getName();
    if (packageName.isBlank() || data.objectTypes().isEmpty()) {
      return;
    }
    rememberNamespace(xdtoPackage, data, packageName);
    var refsByName = registerObjectTypes(data, packageName);
    fillObjectTypes(data, refsByName, valueTypePrimitives(data));
  }

  /**
   * Запомнить, каким пакетом адресуется пространство имён: в коде тип XDTO ищут по URI,
   * а в ссылке документирующего комментария — по имени пакета.
   *
   * @param xdtoPackage объект метаданных пакета.
   * @param data        схема пакета.
   * @param packageName имя пакета.
   */
  private void rememberNamespace(XDTOPackage xdtoPackage, XdtoPackageData data, String packageName) {
    var namespaceUri = data.targetNamespace().isBlank() ? xdtoPackage.getNamespace() : data.targetNamespace();
    if (!namespaceUri.isBlank()) {
      packageByNamespace.put(namespaceUri, packageName);
    }
  }

  /**
   * Завести типы всех объектных типов пакета — до наполнения членами: базовый тип может
   * быть объявлен в схеме ниже наследника.
   *
   * @param data        схема пакета.
   * @param packageName имя пакета.
   * @return типы пакета по именам из схемы.
   */
  private Map<String, TypeRef> registerObjectTypes(XdtoPackageData data, String packageName) {
    var refsByName = new HashMap<String, TypeRef>();
    for (var objectType : data.objectTypes()) {
      var name = objectType.name();
      if (!name.isBlank()) {
        refsByName.put(name, registerObjectType(packageName, name));
      }
    }
    return refsByName;
  }

  /**
   * Наполнить заведённые типы: наследование внутри пакета и ленивый источник членов.
   *
   * @param data       схема пакета.
   * @param refsByName типы пакета по именам.
   * @param valueTypes примитивы простых типов пакета по именам.
   */
  private void fillObjectTypes(XdtoPackageData data, Map<String, TypeRef> refsByName,
                               Map<String, String> valueTypes) {
    for (var objectType : data.objectTypes()) {
      var ref = refsByName.get(objectType.name());
      if (ref == null) {
        continue;
      }
      inheritBase(ref, objectType.base(), refsByName);
      var properties = objectType.properties();
      if (!properties.isEmpty()) {
        typeRegistry.registerMemberSource(ref,
          () -> buildMembers(properties, refsByName, valueTypes), FileType.BSL);
      }
    }
  }

  /**
   * Завести тип объекта пакета вместе с англоязычным псевдонимом и платформенной частью
   * {@code ОбъектXDTO} — методами и свойствами, общими для всех объектов XDTO.
   *
   * @param packageName имя пакета.
   * @param typeName    имя объектного типа в схеме пакета.
   * @return ссылка на зарегистрированный тип.
   */
  private TypeRef registerObjectType(String packageName, String typeName) {
    var qualifiedRu = PACKAGE_PREFIX_RU + packageName + "." + typeName;
    var qualifiedEn = PACKAGE_PREFIX_EN + packageName + "." + typeName;
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    typeRegistry.registerDisplayName(ref, BilingualString.of(qualifiedRu, qualifiedEn));
    typeRegistry.resolve(XDTO_DATA_OBJECT)
      .ifPresent(dataObject -> typeRegistry.registerExtension(ref, dataObject, FileType.BSL));
    return ref;
  }

  /**
   * Унаследовать члены базового объектного типа, если он объявлен в этом же пакете.
   *
   * @param ref       тип-наследник.
   * @param base      запись базового типа из схемы; может быть пустой.
   * @param refsByName типы пакета по именам.
   */
  private void inheritBase(TypeRef ref, String base, Map<String, TypeRef> refsByName) {
    if (base.isBlank()) {
      return;
    }
    var baseRef = refsByName.get(localName(base));
    if (baseRef != null) {
      typeRegistry.registerExtension(ref, baseRef, FileType.BSL);
    }
  }

  /**
   * Члены объектного типа — его свойства с разрешёнными типами.
   *
   * @param properties свойства объектного типа.
   * @param refsByName объектные типы пакета по именам.
   * @param valueTypes примитивы простых типов пакета по именам.
   * @return описатели свойств.
   */
  private List<MemberDescriptor> buildMembers(List<XdtoPackageData.Property> properties,
                                              Map<String, TypeRef> refsByName,
                                              Map<String, String> valueTypes) {
    var members = new ArrayList<MemberDescriptor>(properties.size());
    for (var property : properties) {
      var name = property.name();
      if (name.isBlank()) {
        continue;
      }
      members.add(MemberDescriptor.property(name, propertyType(property.type(), refsByName, valueTypes)));
    }
    return List.copyOf(members);
  }

  /**
   * Тип свойства: примитив по имени XML-схемы, объектный либо простой тип этого же
   * пакета, иначе — произвольный.
   *
   * @param type       запись типа из схемы; может быть пустой.
   * @param refsByName объектные типы пакета по именам.
   * @param valueTypes примитивы простых типов пакета по именам.
   * @return тип свойства.
   */
  private TypeRef propertyType(String type, Map<String, TypeRef> refsByName, Map<String, String> valueTypes) {
    if (type.isBlank()) {
      return TypeRef.ANY;
    }
    if (type.startsWith(XSD_PREFIX)) {
      return primitive(XSD_TO_BSL.get(type.substring(XSD_PREFIX.length())));
    }
    var name = localName(type);
    var objectRef = refsByName.get(name);
    if (objectRef != null) {
      return objectRef;
    }
    return primitive(valueTypes.get(name));
  }

  /**
   * Примитивы простых типов пакета: {@code <valueType name="ТипСуммы" base="xs:decimal"/>}
   * читается как число.
   *
   * @param data схема пакета.
   * @return имя примитива 1С по имени простого типа.
   */
  private static Map<String, String> valueTypePrimitives(XdtoPackageData data) {
    Map<String, String> result = new HashMap<>();
    for (var valueType : data.valueTypes()) {
      var name = valueType.name();
      var base = valueType.base();
      if (name.isBlank() || !base.startsWith(XSD_PREFIX)) {
        continue;
      }
      var primitive = XSD_TO_BSL.get(base.substring(XSD_PREFIX.length()));
      if (primitive != null) {
        result.put(name, primitive);
      }
    }
    return result;
  }

  /**
   * @param primitive имя примитива 1С; {@code null}, если имени нет.
   * @return тип примитива; {@link TypeRef#ANY}, если имя не задано или не резолвится.
   */
  private TypeRef primitive(@Nullable String primitive) {
    if (primitive == null) {
      return TypeRef.ANY;
    }
    return typeRegistry.resolve(primitive).orElse(TypeRef.ANY);
  }

  /**
   * @param qualified имя типа из схемы, возможно с префиксом пространства имён.
   * @return часть после префикса.
   */
  private static String localName(String qualified) {
    var colon = qualified.indexOf(':');
    return colon < 0 ? qualified : qualified.substring(colon + 1);
  }
}
