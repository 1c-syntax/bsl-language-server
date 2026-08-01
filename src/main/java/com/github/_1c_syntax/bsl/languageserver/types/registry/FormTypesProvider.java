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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormKind;
import com.github._1c_syntax.bsl.languageserver.types.registry.TableDataKind;
import com.github._1c_syntax.bsl.context.api.Placeholder;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.CommonForm;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.FormOwner;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.ObjectForm;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormCommand;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;
import com.github._1c_syntax.bsl.mdo.storage.form.FormParameter;
import com.github._1c_syntax.bsl.mdo.storage.form.FormTable;
import com.github._1c_syntax.bsl.mdo.storage.form.FormEventHandler;
import com.github._1c_syntax.bsl.mdo.storage.form.FormEventHandlerOwner;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDataPathOwner;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElement;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElementOwner;
import com.github._1c_syntax.bsl.mdo.storage.FormData;
import com.github._1c_syntax.bsl.types.MultiLanguageString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Регистрирует синтетический тип на каждую форму конфигурации — тот тип, который
 * в модуле формы стоит за {@code ЭтотОбъект}/{@code ЭтаФорма} и за неквалифицированными
 * именами реквизитов и элементов.
 *
 * <h2>Что даёт mdclasses и что — синтакс-помощник</h2>
 * Разделение источников такое же, как у прочих конфигурационных типов, только
 * «дженерик» здесь не один, а составной:
 * <ul>
 *   <li><b>Синтакс-помощник (HBK)</b> — платформенная часть: базовый тип формы
 *       ({@code ФормаКлиентскогоПриложения} / {@code Форма}), тип-расширение по основному
 *       реквизиту ({@code Расширение формы клиентского приложения для документа} …),
 *       типы элементов ({@code ПолеФормы}, {@code ТаблицаФормы} …) вместе с расширениями
 *       по виду элемента и <b>контракты событий</b> с их сигнатурами.</li>
 *   <li><b>mdclasses</b> ({@code Form.xml}) — конфигурационная часть: реквизиты формы
 *       с их типами, элементы с их видами и, главное, <b>связка «событие → имя
 *       обработчика»</b>.</li>
 * </ul>
 *
 * <h2>Почему события формы — отдельный путь</h2>
 * У остальных модулей платформа зовёт обработчик по каноническому имени события,
 * поэтому {@link EventHandlerResolver} сопоставляет метод с событием owner-типа
 * напрямую по имени. У форм имя обработчика произвольно и объявлено в
 * {@code Form.xml} (блок {@code <Events>}), поэтому здесь HBK-контракт события
 * <i>переименовывается</i> в объявленное имя обработчика и вешается на тип формы
 * как {@link MemberKind#EVENT}. После этого общий путь резолвера работает без
 * изменений: он ищет EVENT-член owner-типа по имени метода.
 * <p>
 * События базового типа и расширения остаются на типе формы и под своими каноническими
 * именами — их приносит наследование членов. Это осознанно: 1С по умолчанию называет
 * обработчик именем события, а для ещё не объявленного обработчика такой член — источник
 * заготовки в автодополнении (см. {@code EventContractsIndex#getAllContracts}).
 *
 * <h2>Известные ограничения</h2>
 * <ul>
 *   <li>Служебные элементы ({@code ContextMenu}, {@code ExtendedTooltip},
 *       {@code AutoCommandBar}) в {@code getPlainElements()} не приходят и в коллекцию
 *       элементов не попадают — вместе с их содержимым, то есть и кнопки
 *       автоматической командной панели формы.</li>
 *   <li>Расширение <b>таблицы</b> формы выбирается по корню её {@code ПутьКДанным}, то есть
 *       по типу реквизита. Если реквизит не найден или его тип не опознан, таблица
 *       остаётся с базовой {@code ТаблицаФормы} — без свойств вида данных.</li>
 *   <li>У <b>обычной</b> формы типизируется только сама форма. Её элементы —
 *       {@code ПолеВвода}, {@code ТабличноеПоле}, {@code Надпись} — живут в отдельной
 *       иерархии, которая здесь не моделируется, поэтому остаются без типа: подмешать
 *       им типы управляемой формы было бы ошибкой.</li>
 *   <li>Контракта у обработчика <b>команды</b> нет: платформа передаёт в него параметр
 *       {@code Команда}, но в синтакс-помощнике этого не объявляет.</li>
 * </ul>
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
@Slf4j
public class FormTypesProvider {

  private static final String THIS_OBJECT_RU = "ЭтотОбъект";
  private static final String THIS_OBJECT_EN = "ThisObject";
  private static final String THIS_FORM_RU = "ЭтаФорма";
  private static final String THIS_FORM_EN = "ThisForm";

  /**
   * Суффикс синтетического имени типа модуля обычной формы. Наружу не показывается:
   * отображаемое имя у него — имя самой формы.
   */
  private static final String MODULE_SUFFIX = " (модуль)";

  private static final BilingualString THIS_FORM_DESCRIPTION = BilingualString.of(
    "Форма, в модуле которой выполняется код. Устаревшее имя — используйте ЭтотОбъект.",
    "The form whose module executes the code. Obsolete name — use ThisObject instead.");

  /** Свойства таблицы, тип которых зависит от вида отображаемых данных. */

  /** Первый параметр обработчика события элемента — сам элемент. */
  private static final BilingualString ELEMENT_PARAMETER = BilingualString.of("Элемент", "Item");

  private final TypeRegistry typeRegistry;
  private final FormParametersResolver formParametersResolver;
  private final RecorderIndex recorderIndex;
  private final FormHandlerRoleIndex formHandlerRoleIndex;
  private final FormAttributeTypeIndex formAttributeTypeIndex;
  private final FormDataTypesRegistrar formDataTypes;
  private final FormItemTypesRegistrar formItemTypes;
  private final FormTypeFactory typeFactory;
  private final FormParametersRegistrar formParameters;

  /** Уже обработанные формы — защита от повторной регистрации источников. */
  private final Set<TypeRef> registeredForms = new HashSet<>();

  /** Тип на вид элемента формы: база + расширение вида (см. {@link #registerItemKindTypes}). */

  /**
   * RU-qualifiedName синтетического типа формы: {@code ФормаКлиентскогоПриложения.<mdoRef>}
   * для управляемой, {@code Форма.<mdoRef>} — для обычной. Чистая функция: нужна и до
   * регистрации типов, чтобы резолвить self-тип модуля формы прямо из метаданных
   * (см. {@code ConfigurationModuleMembersProvider#selfTypeQualifiedName}).
   *
   * @param form форма из метаданных.
   * @return qualifiedName типа формы.
   */
  public static String selfTypeQualifiedName(Form form) {
    return FormKind.of(form.getFormType()).baseTypeRu() + "." + FormPlatformTypes.mdoSuffixRu(form);
  }

  /**
   * Регистрирует типы всех форм конфигурации: общих ({@link CommonForm}) и
   * подчинённых объектам ({@link FormOwner#getForms()}).
   *
   * @param children объекты метаданных конфигурации.
   */
  public void register(Iterable<MD> children) {
    formItemTypes.registerItemKindTypes();
    var count = 0;
    for (var md : children) {
      count += registerFormsOf(md);
    }
    LOGGER.debug("Form types registered: {}", count);
  }

  /**
   * Формы одного объекта метаданных: общая форма сама себе владелец, у прочих формы
   * лежат внутри. Объект, форм не имеющий, даёт ноль.
   */
  private int registerFormsOf(MD md) {
    if (md instanceof CommonForm commonForm) {
      return registerForm(commonForm, null) ? 1 : 0;
    }
    if (!(md instanceof FormOwner formOwner)) {
      return 0;
    }
    var count = 0;
    for (var form : formOwner.getForms()) {
      count += registerForm(form, md) ? 1 : 0;
    }
    return count;
  }

  private boolean registerForm(Form form, @Nullable MD owner) {
    var suffixRu = FormPlatformTypes.mdoSuffixRu(form);
    if (suffixRu.isBlank()) {
      return false;
    }
    var kind = FormKind.of(form.getFormType());
    var formRef = typeFactory.registerWithAlias(
      kind.baseTypeRu() + "." + suffixRu,
      kind.baseTypeEn() + "." + FormPlatformTypes.mdoSuffixEn(form));
    if (!registeredForms.add(formRef)) {
      return false;
    }

    var data = form.getData();
    var baseRef = typeRegistry.resolve(kind.baseTypeRu()).orElse(null);
    var extensionRef = resolveExtension(form, owner, data.getAttributes(), kind);

    if (baseRef != null) {
      typeRegistry.registerExtension(formRef, baseRef, FileType.BSL);
    }
    if (extensionRef != null) {
      typeRegistry.registerExtension(formRef, extensionRef, FileType.BSL);
      if (kind == FormKind.ORDINARY) {
        formParameters.registerOrdinaryParameters(formRef, extensionRef, form, owner);
      }
    }
    // Члены собираются внутри источников, а не здесь: у крупной конфигурации формы
    // считаются тысячами, а элементов на форме — сотнями. Реестр мемоизирует
    // getMembers, поэтому реально материализуются только запрошенные формы.
    // Типы реквизитов — исключение: их надо посчитать сразу, потому что под них
    // регистрируются типы данных формы, а регистрация изнутри ленивого источника
    // сбивала бы epoch кэша членов во время его же пересчёта.
    var attributeTypes = formDataTypes.prepareAttributeTypes(data.getAttributes(), kind, suffixRu);
    typeRegistry.registerMemberSource(formRef,
      () -> formDataTypes.buildAttributeMembers(data.getAttributes(), attributeTypes), FileType.BSL);
    if (kind == FormKind.MANAGED) {
      // Объявленный тип реквизита свойством формы не выставишь — там данные формы, —
      // но именно его отдаёт обратное преобразование `РеквизитФормыВЗначение`.
      formAttributeTypeIndex.register(formRef, formDataTypes.declaredAttributeTypes(data.getAttributes()));
    }
    // Override, а не обычный источник: если обработчик назван каноническим именем
    // события (так конфигуратор делает по умолчанию), то же имя приходит и от
    // расширения — но там тип параметра остаётся обобщённым
    // (`ДокументОбъект.<Имя документа>`), а здесь подставлен владелец формы.
    typeRegistry.registerMemberOverride(formRef,
      () -> FormPlatformTypes.concat(
        buildEventMembers(collectHandlers(form, kind, baseRef, extensionRef), FormPlatformTypes.ownerName(form)),
        formParameters.buildCommandHandlers(data.getCommands())), FileType.BSL);
    // Роли считаются сразу: имена дешевле членов (в реестр не ходим), а знать, кем
    // объявлен обработчик, нужно снаружи системы типов — стандартным областям модуля
    // и hover'у.
    var handlerRoles = collectHandlerRoles(data);
    formHandlerRoleIndex.register(formRef, handlerRoles);
    if (kind == FormKind.ORDINARY) {
      // За модулем обычной формы стоит отдельный тип (см. registerModuleType), и именно
      // его резолвит EventHandlerResolver — под именем формы роли оттуда не видны.
      formHandlerRoleIndex.register(
        typeRegistry.registerConfigurationType(moduleTypeQualifiedName(form)), handlerRoles);
    }

    // Override, а не обычный источник: `Элементы`/`Параметры`/`Команды`/`ЭтотОбъект`
    // есть и у базового типа, но с обобщёнными типами — специализированные должны
    // выигрывать дедуп в getMembers.
    var tableTypes = formItemTypes.prepareTableTypes(data, kind, suffixRu, attributeTypes);
    var itemsRef = formItemTypes.registerItemsCollection(form, kind, suffixRu, tableTypes);
    var parametersRef = formParameters.registerParametersStructure(form, kind, suffixRu, owner);
    var commandsRef = formParameters.registerCommandsCollection(form, kind, suffixRu);
    typeRegistry.registerMemberOverride(formRef,
      () -> buildSelfMembers(kind, formRef, itemsRef, parametersRef, commandsRef, null), FileType.BSL);
    registerModuleType(form, kind, formRef, itemsRef, parametersRef, commandsRef, owner);
    return true;
  }

  /**
   * Тип, который стоит за модулем формы. У управляемой формы он совпадает с типом самой
   * формы, а у обычной — отдельный: в её модуль платформа инжектит контекст объекта
   * (реквизиты доступны неквалифицированно, {@code ЭтотОбъект} ведёт на объект), но
   * <b>только внутри модуля</b>. Снаружи — у значения, полученного через
   * {@code ПолучитьФорму()} и подобные, — контекста объекта нет, и вешать его на тип
   * формы нельзя: тип один на всех.
   * <p>
   * Имя синтетическое и наружу не показывается: отображаемым остаётся имя самой формы.
   */
  private void registerModuleType(Form form, FormKind kind, TypeRef formRef, @Nullable TypeRef itemsRef,
                                  @Nullable TypeRef parametersRef, @Nullable TypeRef commandsRef,
                                  @Nullable MD owner) {
    if (kind != FormKind.ORDINARY) {
      return;
    }
    // Тип модуля заводится у любой обычной формы, даже когда инжектить нечего: имя
    // модульного типа выводится из формы, и модуль привязывается к нему в любом случае
    // (см. ConfigurationModuleMembersProvider). Без этой регистрации модуль формы
    // списка остался бы с пустым типом — без элементов и платформенных членов.
    var moduleRef = typeRegistry.registerConfigurationType(moduleTypeQualifiedName(form));
    typeRegistry.registerDisplayName(moduleRef, BilingualString.of(
      kind.baseTypeRu() + "." + FormPlatformTypes.mdoSuffixRu(form),
      kind.baseTypeEn() + "." + FormPlatformTypes.mdoSuffixEn(form)));
    typeRegistry.registerExtension(moduleRef, formRef, FileType.BSL);

    var injectedRef = ordinaryFormObject(form, owner);
    if (injectedRef == null) {
      return;
    }
    typeRegistry.registerExtension(moduleRef, injectedRef, FileType.BSL);
    typeRegistry.registerMemberOverride(moduleRef,
      () -> buildSelfMembers(kind, formRef, itemsRef, parametersRef, commandsRef, injectedRef), FileType.BSL);
  }

  /**
   * Имя типа, который стоит за модулем формы (см. {@link #registerModuleType}).
   * Выводится из самой формы, без обращения к реестру: тем же именем модуль
   * связывается со своим типом в {@code ConfigurationModuleMembersProvider}.
   *
   * @param form форма конфигурации.
   * @return qualifiedName типа модуля; для управляемой формы — имя самой формы.
   */
  public static String moduleTypeQualifiedName(Form form) {
    var formName = selfTypeQualifiedName(form);
    return FormKind.of(form.getFormType()) == FormKind.ORDINARY ? formName + MODULE_SUFFIX : formName;
  }

  /**
   * Подставляет имя объекта-владельца в generic-плейсхолдеры типов параметров.
   * Плейсхолдер у параметра ровно один ({@code <Имя справочника>}), кроме таблиц
   * внешних источников данных — там их два, и однозначной подстановки нет,
   * поэтому такие типы остаются обобщёнными.
   */

  /**
   * Свойства, указывающие на саму форму, её коллекцию элементов и коллекцию команд.
   * У базового типа они тоже есть, но ведут на обобщённые
   * {@code ФормаКлиентскогоПриложения} и {@code ВсеЭлементыФормы} — без этой замены
   * разыменование {@code ЭтотОбъект.Реквизит} и {@code Элементы.Кнопка} не работало бы.
   */
  private static List<MemberDescriptor> buildSelfMembers(FormKind kind, TypeRef formRef, TypeRef itemsRef,
                                                         @Nullable TypeRef parametersRef,
                                                         @Nullable TypeRef commandsRef,
                                                         @Nullable TypeRef injectedRef) {
    var members = new ArrayList<MemberDescriptor>();
    members.add(FormPlatformTypes.platformProperty(
      BilingualString.of(kind.itemsPropertyRu(), kind.itemsPropertyEn()), itemsRef, BilingualString.EMPTY));
    if (parametersRef != null) {
      members.add(FormPlatformTypes.platformProperty(
        BilingualString.of(FormPlatformTypes.PARAMETERS_PROPERTY_RU, FormPlatformTypes.PARAMETERS_PROPERTY_EN),
        parametersRef, BilingualString.EMPTY));
    }
    if (commandsRef != null) {
      members.add(FormPlatformTypes.platformProperty(
        BilingualString.of(FormPlatformTypes.COMMANDS_PROPERTY_RU, FormPlatformTypes.COMMANDS_PROPERTY_EN),
        commandsRef, BilingualString.EMPTY));
    }
    // У обычной формы `ЭтотОбъект` не существует вовсе: он появляется только в модуле
    // формы объекта — и ведёт на сам объект, чей контекст туда инжечен
    // (см. registerModuleType). У управляемой формы это сама форма.
    if (kind == FormKind.MANAGED || injectedRef != null) {
      members.add(FormPlatformTypes.platformProperty(BilingualString.of(THIS_OBJECT_RU, THIS_OBJECT_EN),
        injectedRef == null ? formRef : injectedRef, BilingualString.EMPTY));
    }
    // `ЭтаФорма` платформа отдаёт в модуле формы обоих видов: у обычной это штатное
    // имя, у управляемой — устаревший синоним `ЭтотОбъект` (см. UsingThisFormDiagnostic).
    members.add(FormPlatformTypes.platformProperty(
      BilingualString.of(THIS_FORM_RU, THIS_FORM_EN), formRef, THIS_FORM_DESCRIPTION));
    return List.copyOf(members);
  }


  /**
   * Объявленный в {@code Form.xml} обработчик вместе с типами, где искать контракт
   * его события. У обработчика формы это её расширение и базовый тип, у обработчика
   * элемента — тип самого элемента: {@code Нажатие} объявлено у {@code КнопкаФормы},
   * {@code ПриИзменении} — у {@code ПолеФормы}.
   *
   * @param handler       пара «событие → имя процедуры модуля».
   * @param contractTypes типы-источники контракта, в порядке приоритета.
   */
  private record HandlerSource(FormEventHandler handler, List<TypeRef> contractTypes, boolean ofElement) {
  }

  /**
   * Объявленные в {@code Form.xml} обработчики — и самой формы, и её элементов.
   * Элементы перебираются плоским списком, поэтому вложенные в группы тоже попадают.
   */
  private List<HandlerSource> collectHandlers(Form form, FormKind kind, @Nullable TypeRef baseRef,
                                              @Nullable TypeRef extensionRef) {
    var data = form.getData();
    var formTypes = Stream.of(extensionRef, baseRef).filter(Objects::nonNull).toList();
    var result = new ArrayList<HandlerSource>(data.getEventHandlers().size());
    for (var handler : data.getEventHandlers()) {
      result.add(new HandlerSource(handler, formTypes, false));
    }
    var attributeTypes = FormItemTypesRegistrar.attributeTypesByName(data.getAttributes());
    for (var element : data.getPlainElements()) {
      if (!(element instanceof FormEventHandlerOwner handlerOwner)) {
        continue;
      }
      var elementTypes = formItemTypes.itemTypes(element, attributeTypes, kind, Map.of()).refs().stream().toList();
      for (var handler : handlerOwner.getEventHandlers()) {
        result.add(new HandlerSource(handler, elementTypes, true));
      }
    }
    return result;
  }

  /**
   * Кем объявлен каждый обработчик формы: ею самой, элементом шапки, элементом таблицы
   * или командой. Нужно снаружи системы типов — стандартной области модуля и hover'у
   * (см. {@link FormHandlerRoleIndex}).
   *
   * @param data содержимое формы.
   * @return роли по имени процедуры-обработчика в нижнем регистре.
   */
  private static Map<String, FormHandlerRoleIndex.Handler> collectHandlerRoles(FormData data) {
    var roles = new HashMap<String, FormHandlerRoleIndex.Handler>();
    for (var handler : data.getEventHandlers()) {
      putRole(roles, handler.handler(), FormHandlerRoleIndex.Role.FORM_EVENT, "");
    }
    collectElementRoles(data.getElements(), "", roles);
    for (var command : data.getCommands()) {
      putRole(roles, command.getAction(), FormHandlerRoleIndex.Role.COMMAND, command.getName());
    }
    return roles;
  }

  /**
   * Роли обработчиков элементов. Обход идёт по дереву, а не по плоскому списку:
   * область стандартного модуля у события элемента таблицы своя
   * ({@code ОбработчикиСобытийЭлементовТаблицыФормы<Имя таблицы>}), и узнать таблицу
   * можно только по тому, внутри какого элемента лежит вложенный.
   *
   * @param elements элементы одного уровня.
   * @param tableName таблица, внутри которой они лежат; пусто — шапка формы.
   * @param roles     накопитель.
   */
  private static void collectElementRoles(List<FormElement> elements, String tableName,
                                          Map<String, FormHandlerRoleIndex.Handler> roles) {
    for (var element : elements) {
      // Сама таблица «своя» для своей же области: её события лежат там же, где события
      // её колонок. Имя области — не константа, а шаблон: суффиксом идёт имя того
      // самого элемента-таблицы, в котором лежит обработчик.
      var ownerTable = element instanceof FormTable ? element.getName() : tableName;
      collectOwnRoles(element, ownerTable, roles);
      if (element instanceof FormElementOwner elementOwner) {
        collectElementRoles(elementOwner.getElements(), ownerTable, roles);
      }
    }
  }

  /** Роли обработчиков, объявленных самим элементом (без вложенных). */
  private static void collectOwnRoles(FormElement element, String ownerTable,
                                      Map<String, FormHandlerRoleIndex.Handler> roles) {
    if (!(element instanceof FormEventHandlerOwner handlerOwner)) {
      return;
    }
    var role = ownerTable.isEmpty()
      ? FormHandlerRoleIndex.Role.HEADER_ITEM_EVENT
      : FormHandlerRoleIndex.Role.TABLE_ITEM_EVENT;
    var owner = ownerTable.isEmpty() ? element.getName() : ownerTable;
    for (var handler : handlerOwner.getEventHandlers()) {
      putRole(roles, handler.handler(), role, owner);
    }
  }

  private static void putRole(Map<String, FormHandlerRoleIndex.Handler> roles, String handlerName,
                              FormHandlerRoleIndex.Role role, String owner) {
    if (handlerName.isBlank()) {
      return;
    }
    roles.putIfAbsent(handlerName.toLowerCase(Locale.ROOT), new FormHandlerRoleIndex.Handler(role, owner));
  }

  /**
   * EVENT-члены типа формы: имя — объявленный в {@code Form.xml} обработчик, контракт
   * (сигнатура, описание, метаданные) — платформенное событие с тем же именем.
   * Если контракт не нашёлся (нет HBK либо событие неизвестно), член всё равно
   * регистрируется: факт «этот метод — обработчик события» важен сам по себе.
   */
  private List<MemberDescriptor> buildEventMembers(List<HandlerSource> handlers, String ownerName) {
    if (handlers.isEmpty()) {
      return List.of();
    }
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(handlers.size());
    for (var source : handlers) {
      var handler = source.handler();
      var handlerName = handler.handler();
      if (handlerName.isBlank()) {
        continue;
      }
      var contract = findEventContract(handler.event(), source.contractTypes());
      var descriptor = contract == null
        ? MemberDescriptor.event(handlerName, "", List.of())
        .withBilingualDescription(unknownEventDescription(handler.event()))
        : specializeByOwner(contract, ownerName);
      if (source.ofElement()) {
        descriptor = withElementParameter(descriptor, source.contractTypes());
      }
      byName.putIfAbsent(handlerName.toLowerCase(Locale.ROOT),
        descriptor.withBilingualName(FormPlatformTypes.neutral(handlerName)));
    }
    return List.copyOf(byName.values());
  }

  /**
   * Подставляет имя владельца формы в generic-плейсхолдеры контракта события. Тип
   * параметра объявлен обобщённо ({@code ТекущийОбъект} у {@code ПриЗаписиНаСервере} —
   * это {@code ДокументОбъект.<Имя документа>}), а на конкретной форме известно, какой
   * именно объект туда придёт.
   */
  private static MemberDescriptor specializeByOwner(MemberDescriptor contract, String ownerName) {
    if (ownerName.isEmpty()) {
      return contract;
    }
    var placeholder = PlaceholderBinder.singlePlaceholder(contract);
    return placeholder == null ? contract : contract.specialize(Map.of(placeholder, ownerName));
  }

  /**
   * Дописывает в контракт события элемента первый параметр — сам элемент. Платформа
   * передаёт его в каждый обработчик события элемента формы, но в синтакс-помощнике
   * не объявляет: там у события элемента объявлены только «свои» параметры, а то и
   * вовсе ни одного. Остальные параметры сдвигаются.
   *
   * @param contract     контракт события (возможно, без сигнатур вовсе).
   * @param elementTypes типы элемента-владельца события.
   * @return контракт, у которого в каждой сигнатуре первым идёт {@code Элемент}.
   */
  private static MemberDescriptor withElementParameter(MemberDescriptor contract, List<TypeRef> elementTypes) {
    var element = new ParameterDescriptor(ELEMENT_PARAMETER, TypeSet.of(elementTypes), false,
      BilingualString.of("Элемент формы, событие которого обрабатывается.",
        "The form item whose event is being handled."), "");
    if (contract.signatures().isEmpty()) {
      return contract.withSignatures(List.of(
        new SignatureDescriptor(List.of(element), TypeSet.EMPTY, BilingualString.EMPTY)));
    }
    var signatures = contract.signatures().stream()
      .map(signature -> withFirstParameter(signature, element))
      .toList();
    return contract.withSignatures(signatures);
  }

  /** Сигнатура с добавленным первым параметром; если он там уже есть — она же. */
  private static SignatureDescriptor withFirstParameter(SignatureDescriptor signature,
                                                        ParameterDescriptor first) {
    var parameters = signature.parameters();
    if (!parameters.isEmpty() && parameters.get(0).bilingualName().equals(first.bilingualName())) {
      return signature;
    }
    var shifted = new ArrayList<ParameterDescriptor>(parameters.size() + 1);
    shifted.add(first);
    shifted.addAll(parameters);
    return new SignatureDescriptor(shifted, signature.returnTypes(), signature.bilingualDescription(),
      signature.metadata());
  }

  /**
   * Контракт события по его каноническому имени из {@code Form.xml} (имя приходит в
   * английском написании). Типы просматриваются по порядку: у формы расширение идёт
   * первым — события работы с данными объявлены именно на нём.
   */
  private @Nullable MemberDescriptor findEventContract(String eventName, List<TypeRef> contractTypes) {
    if (eventName.isBlank()) {
      return null;
    }
    for (var typeRef : contractTypes) {
      var found = eventOn(typeRef, eventName);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private @Nullable MemberDescriptor eventOn(@Nullable TypeRef typeRef, String eventName) {
    if (typeRef == null) {
      return null;
    }
    for (var member : typeRegistry.getMembers(typeRef, FileType.BSL)) {
      if (member.kind() == MemberKind.EVENT && member.matches(eventName)) {
        return member;
      }
    }
    return null;
  }

  /**
   * Тип-расширение формы по её основному реквизиту — тому, что несёт основные данные
   * формы. Именно он решает, каким расширением дополняется форма: реквизит
   * {@code ДокументОбъект.Х} даёт «Расширение управляемой формы для документа».
   */
  private @Nullable TypeRef resolveExtension(Form form, @Nullable MD owner,
                                            List<FormAttribute> attributes, FormKind kind) {
    if (kind == FormKind.ORDINARY) {
      return resolveOrdinaryExtension(form, owner);
    }
    return FormPlatformTypes.mainAttributeTypeNames(attributes, valueTypeRu ->
      FormPlatformTypes.extensionTypeNames(valueTypeRu, kind))
      .map(typeRegistry::resolve)
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(null);
  }

  /**
   * Тип-расширение обычной формы по её роли у объекта-владельца: форма, назначенная
   * основной формой элемента справочника, и есть форма элемента справочника.
   * <p>
   * Обходной путь: у обычной формы состав реквизитов из mdclasses не приходит, и
   * основной реквизит — обычный признак выбора расширения — определить нечем.
   * Роль же известна из карты основных форм владельца.
   *
   * @return расширение; {@code null}, если форма не общая, не назначена основной
   *   либо для такой пары «владелец + роль» расширения нет.
   */
  private @Nullable TypeRef resolveOrdinaryExtension(Form form, @Nullable MD owner) {
    if (!(owner instanceof FormOwner formOwner)) {
      return null;
    }
    var formRef = form.getMdoReference();
    for (var entry : formOwner.getDefaultFormMap().entrySet()) {
      if (!entry.getValue().equals(formRef)) {
        continue;
      }
      var name = FormPlatformTypes.ordinaryExtensionTypeName(owner.getMdoType(), entry.getKey());
      if (name != null) {
        return typeRegistry.resolve(name).orElse(null);
      }
    }
    return null;
  }

  /**
   * Прикладной тип, контекст которого платформа инжектит в модуль обычной формы:
   * для формы объекта — сам объект, для формы записи регистра — запись, для формы
   * набора записей — набор.
   * <p>
   * Имя типа собирается из mdoRef владельца ({@code Документ.Документ1} →
   * {@code ДокументОбъект.Документ1}) и проверяется по реестру: какой суффикс
   * применим, решает не таблица соответствий, а наличие типа. Списочные формы
   * объектного контекста не получают — там показывается выборка, а не один объект.
   *
   * @return прикладной тип; {@code null}, если форма не подчинена объекту, не
   *   назначена основной либо это форма списка.
   */
  private @Nullable TypeRef ordinaryFormObject(Form form, @Nullable MD owner) {
    if (!(owner instanceof FormOwner formOwner)) {
      return null;
    }
    var formRef = form.getMdoReference();
    for (var entry : formOwner.getDefaultFormMap().entrySet()) {
      if (entry.getValue().equals(formRef)) {
        return FormPlatformTypes.injectedObjectSuffixes(entry.getKey()).stream()
          .map(suffix -> objectTypeName(owner, suffix))
          .flatMap(name -> typeRegistry.resolve(name).stream())
          .findFirst()
          .orElse(null);
      }
    }
    return null;
  }

  /** {@code Документ.Документ1} + {@code Объект} → {@code ДокументОбъект.Документ1}. */
  private static String objectTypeName(MD owner, String suffix) {
    return FormPlatformTypes.typeNameWithSuffix(owner.getMdoReference().getMdoRefRu(), suffix);
  }



  private static BilingualString unknownEventDescription(String eventName) {
    return BilingualString.of(
      "Обработчик события формы «" + eventName + "».",
      "Handler of the form event \"" + eventName + "\".");
  }
}
