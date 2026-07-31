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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.StandardAttribute;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Достраивает типы регистров тем, что известно только из конфигурации.
 * <p>
 * Специализация generic'ов по имени регистра ({@code РегистрСведенийЗапись.<Имя>} →
 * {@code РегистрСведенийЗапись.Курсы}) сама по себе оставляет три пробела, и все три
 * закрываются здесь:
 * <ul>
 *   <li>измерения, ресурсы и реквизиты регистра — expansion generic-property на типе
 *       записи;</li>
 *   <li>набор записей — коллекция <b>своих</b> записей: обход и индексатор дают запись
 *       этого регистра, а {@code Выгрузить()} — таблицу с его колонками;</li>
 *   <li>члены, у которых после подстановки имени регистра остался плейсхолдер, —
 *       {@code Регистратор} и ссылки на чужое семейство регистров.</li>
 * </ul>
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class RegisterTypesRegistrar {

  private final TypeRegistry typeRegistry;
  private final RecorderIndex recorderIndex;

  /**
   * Триплет имён детей регистра (измерения/ресурсы/реквизиты), полученный
   * из конкретного MD-класса регистра. {@code null} — для не-регистров.
   */
  record RegisterChildren(List<? extends Attribute> dimensions,
                          List<? extends Attribute> resources,
                          List<? extends Attribute> attributes) {
  }

  static @Nullable RegisterChildren registerChildrenOf(MD md) {
    return switch (md) {
      case InformationRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), customAttributesOf(r.getAttributes()));
      case AccumulationRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), customAttributesOf(r.getAttributes()));
      case AccountingRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), customAttributesOf(r.getAttributes()));
      case CalculationRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), customAttributesOf(r.getAttributes()));
      default -> null;
    };
  }

  /**
   * Отфильтровывает {@link StandardAttribute} (Период/Регистратор/Активность/…):
   * {@code getAttributes()} регистра возвращает их вперемешку с собственными
   * реквизитами, но они уже приходят как обычные bilingual-члены generic-типа
   * записи ({@code РегистрХХХЗапись.<Имя>}) из bsl-context — без фильтра
   * плейсхолдер {@code <Имя реквизита>} материализовал бы их второй раз,
   * одноязычными (под английским написанием).
   */
  private static List<? extends Attribute> customAttributesOf(List<? extends Attribute> attributes) {
    return attributes.stream().filter(a -> !(a instanceof StandardAttribute)).toList();
  }

  /** Кладёт в expansion-map имена непустых атрибутов под ключом-placeholder'ом. */
  static void putAttributeNames(Map<String, List<String>> sink, String placeholder,
                                List<? extends Attribute> attributes) {
    var names = attributes.stream()
      .map(Attribute::getName)
      .filter(n -> !n.isBlank())
      .toList();
    if (!names.isEmpty()) {
      sink.put(placeholder, names);
    }
  }

  /**
   * Регистрирует expansion generic-property {@code <Имя измерения>/<Имя ресурса>/
   * <Имя реквизита>} на типе записи регистра ({@code РегистрСведенийЗапись.<Имя>}
   * и аналоги для других семейств регистров). Имена детей берутся из mdclasses;
   * мета — наследуется от HBK-template'ов.
   *
   * @param familyCore ru-часть имени семейства ({@code "РегистрСведений"} и т.п.)
   * @param regName    имя регистра в конфигурации
   * @param children   измерения/ресурсы/реквизиты регистра
   */
  void registerRecordExpansion(String familyCore, String regName, RegisterChildren children) {
    var generic = typeRegistry.findAllGenericsByFamilyCore(familyCore + "Запись").stream()
      .findFirst()
      .orElse(null);
    if (generic == null) {
      return;
    }
    var parameters = typeRegistry.getTypeParameters(generic);
    if (parameters.size() != 1) {
      return;
    }
    var typeBindings = Map.of(parameters.get(0), regName);
    var specializedName = TypeRef.specialize(generic, typeBindings).qualifiedName();
    var specialized = typeRegistry.resolve(specializedName).orElse(null);
    if (specialized == null) {
      return;
    }
    var expansions = new LinkedHashMap<String, List<String>>();
    putAttributeNames(expansions, "Имя измерения", children.dimensions());
    putAttributeNames(expansions, "Имя ресурса", children.resources());
    putAttributeNames(expansions, "Имя реквизита", children.attributes());
    if (expansions.isEmpty()) {
      return;
    }
    typeRegistry.registerMemberExpansion(specialized, generic, typeBindings, expansions, FileType.BSL);
  }

  /**
   * Делает набор записей регистра полноценной коллекцией своих записей: обход
   * {@code Для Каждого} и индексатор дают запись <b>этого</b> регистра, а
   * {@code Выгрузить()} — таблицу значений с его колонками.
   * <p>
   * Платформа описывает набор обобщённо: {@code Выгрузить()} объявлен возвращающим
   * просто {@code ТаблицаЗначений}, хотя колонки у выгруженной таблицы те же, что у
   * записи. Приём тот же, что у табличных частей (см.
   * {@link CollectionReturnsSpecializer}); колонками служат свойства записи —
   * измерения, ресурсы, реквизиты и стандартные реквизиты вроде {@code Регистратор}.
   *
   * @param familyCore ru-часть имени семейства ({@code "РегистрНакопления"} и т.п.).
   * @param regName    имя регистра в конфигурации.
   */
  void registerRecordSetCollectionMembers(String familyCore, String regName) {
    var genericSet = singleGenericOf(familyCore + "НаборЗаписей");
    var genericRecord = singleGenericOf(familyCore + "Запись");
    if (genericSet == null || genericRecord == null) {
      return;
    }
    var setRef = specializedByName(genericSet, regName);
    var recordRef = specializedByName(genericRecord, regName);
    if (setRef == null || recordRef == null) {
      return;
    }
    // Тип элемента — до наследования коллекционных свойств: иначе выиграла бы
    // унаследованная обобщённая запись и обход потерял бы колонки.
    typeRegistry.registerDefaultElementTypes(setRef, List.of(recordRef));
    typeRegistry.inheritCollectionTraits(setRef, genericSet, FileType.BSL);

    var valueTableRow = typeRegistry.resolve(CollectionReturnsSpecializer.VALUE_TABLE_ROW).orElse(null);
    MemberSource columns = () -> typeRegistry.getMembers(recordRef, FileType.BSL).stream()
      .filter(member -> member.kind() == MemberKind.PROPERTY && !member.generic())
      .toList();
    typeRegistry.registerMemberOverride(setRef, () -> CollectionReturnsSpecializer.specialize(
      typeRegistry.getMembers(genericSet, FileType.BSL), genericRecord, recordRef,
      CollectionReturnsSpecializer.unloadedRow(valueTableRow, columns)), FileType.BSL);
  }

  /**
   * Достраивает типы членов регистра, которые специализация по имени регистра не
   * покрывает. Оба случая видны по одному признаку — оставшемуся generic-плейсхолдеру:
   * <ul>
   *   <li>{@code Регистратор} объявлен как {@code ДокументСсылка.<Имя документа>}:
   *       имя регистра к документу отношения не имеет, подставляются
   *       документы-регистраторы (см. {@link RecorderIndex});</li>
   *   <li>член ссылается на <b>чужое</b> семейство регистров — ошибка синтакс-помощника,
   *       семейство заменяется на своё (см. {@link RegisterFamilies}).</li>
   * </ul>
   * Обе правки принимаются, только если получившийся тип есть в реестре.
   *
   * @param md         MD-объект регистра.
   * @param familyCore ru-часть имени семейства ({@code "РегистрБухгалтерии"} и т.п.).
   * @param mdName     имя регистра в конфигурации.
   */
  void registerFamilyFixups(MD md, String familyCore, String mdName) {
    if (!RegisterFamilies.isRegisterFamily(familyCore)) {
      return;
    }
    // TODO mdclasses#670: так же типизировать Счет/СчетДт/СчетКт записи регистра
    //  бухгалтерии (объявлены как ПланСчетовСсылка.<Имя плана счетов>) и ВидРасчета
    //  записи регистра расчёта. План указан у регистра в метаданных (<ChartOfAccounts>,
    //  <ChartOfCalculationTypes>), но mdclasses 0.20.0 его не разбирает. Как появится —
    //  добавить имя плана вторым набором подстановки рядом с recorders: подходящий
    //  выберется по тому же признаку «тип есть в реестре».
    var recorders = recorderIndex.recordersOf(md.getMdoReference().getMdoRefRu());
    for (var generic : typeRegistry.findAllGenericsByFamilyCore(familyCore)) {
      registerFixupsOn(generic, familyCore, mdName, recorders);
    }
  }

  /** Регистрирует достроенные члены на специализации одного generic'а семейства. */
  private void registerFixupsOn(TypeRef generic, String familyCore, String mdName, List<String> recorders) {
    var parameters = typeRegistry.getTypeParameters(generic);
    if (parameters.size() != 1) {
      return;
    }
    var bindings = Map.of(parameters.get(0), mdName);
    var specialized = typeRegistry.resolve(TypeRef.specialize(generic, bindings).qualifiedName())
      .orElse(null);
    if (specialized == null || specialized.equals(generic)) {
      return;
    }
    typeRegistry.registerMemberOverride(specialized,
      () -> fixedUpRegisterMembers(generic, familyCore, bindings, mdName, recorders), FileType.BSL);
  }

  /**
   * Члены generic-типа, у которых после подстановки имени регистра остался плейсхолдер,
   * — с достроенными типами. Остальные не отдаются вовсе: они приходят обычной
   * специализацией, и дублировать их незачем.
   * <p>
   * Читаются у generic'а, а не у специализированного типа: источник регистрируется как
   * раз на специализированный, и обращение к его же {@code getMembers} зациклилось бы.
   */
  private List<MemberDescriptor> fixedUpRegisterMembers(TypeRef generic, String familyCore,
                                                        Map<String, String> bindings, String mdName,
                                                        List<String> recorders) {
    var result = new ArrayList<MemberDescriptor>();
    for (var member : typeRegistry.getMembers(generic, FileType.BSL)) {
      if (member.generic()) {
        continue;
      }
      var specialized = member.specialize(bindings);
      var fixed = RegisterFamilies.ownFamilyMember(typeRegistry, specialized, familyCore, mdName);
      if (fixed == null) {
        fixed = PlaceholderBinder.bind(typeRegistry, specialized, recorders);
      }
      if (fixed != null) {
        result.add(fixed);
      }
    }
    return List.copyOf(result);
  }

  /** Единственный generic семейства с ровно одним параметром; {@code null} — такого нет. */
  private @Nullable TypeRef singleGenericOf(String familyCore) {
    return typeRegistry.findAllGenericsByFamilyCore(familyCore).stream()
      .filter(generic -> typeRegistry.getTypeParameters(generic).size() == 1)
      .findFirst()
      .orElse(null);
  }

  /** Специализация generic'а по имени MD-объекта; {@code null} — не зарегистрирована. */
  private @Nullable TypeRef specializedByName(TypeRef generic, String mdName) {
    var bindings = Map.of(typeRegistry.getTypeParameters(generic).get(0), mdName);
    return typeRegistry.resolve(TypeRef.specialize(generic, bindings).qualifiedName()).orElse(null);
  }
}
