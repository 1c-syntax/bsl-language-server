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
import com.github._1c_syntax.bsl.languageserver.types.registry.FormPlatformTypes.FormKind;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormPlatformTypes.TableDataKind;
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

  /** Суффикс имени ссылочного типа: {@code Документ.Документ1} → {@code ДокументСсылка.Документ1}. */
  private static final String REF_SUFFIX = "Ссылка";

  /** Тип параметра, передавать в который нечего. */
  private static final TypeSet UNDEFINED = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Неопределено"));

  /** Параметр формы записи регистра сведений: ключ записи, открытой на изменение. */
  private static final String SOURCE_RECORD_KEY = "ИсходныйКлючЗаписи";

  /** Коллекция выделенных строк таблицы формы. */
  private static final BilingualString SELECTED_ROWS =
    BilingualString.of("ВыделенныеСтроки", "SelectedRows");

  private static final String ARRAY_RU = "Массив";
  private static final String ARRAY_EN = "Array";
  private static final BilingualString THIS_FORM_DESCRIPTION = BilingualString.of(
    "Форма, в модуле которой выполняется код. Устаревшее имя — используйте ЭтотОбъект.",
    "The form whose module executes the code. Obsolete name — use ThisObject instead.");

  /** Свойства таблицы, тип которых зависит от вида отображаемых данных. */
  private static final BilingualString CURRENT_ROW = BilingualString.of("ТекущаяСтрока", "CurrentRow");
  private static final BilingualString CURRENT_PARENT =
    BilingualString.of("ТекущийРодитель", "CurrentParent");

  private static final BilingualString ROW_DATA_METHOD = BilingualString.of("ДанныеСтроки", "RowData");

  /** Первый параметр обработчика события элемента — сам элемент. */
  private static final BilingualString ELEMENT_PARAMETER = BilingualString.of("Элемент", "Item");

  private final TypeRegistry typeRegistry;
  private final FormParametersResolver formParametersResolver;
  private final RecorderIndex recorderIndex;
  private final FormHandlerRoleIndex formHandlerRoleIndex;

  /** Уже обработанные формы — защита от повторной регистрации источников. */
  private final Set<TypeRef> registeredForms = new HashSet<>();

  /** Тип на вид элемента формы: база + расширение вида (см. {@link #registerItemKindTypes}). */
  private final Map<FormElementType, TypeRef> itemKindTypes = new EnumMap<>(FormElementType.class);

  /** Тип на вид данных таблицы формы (см. {@link #registerTableDataKindTypes}). */
  private final Map<FormPlatformTypes.TableDataKind, TypeRef> tableDataKindTypes =
    new EnumMap<>(FormPlatformTypes.TableDataKind.class);

  /**
   * Тип данных формы по объявленному типу реквизита (см. {@link #registerFormData}).
   * Общий на всю рабочую область: данные одного прикладного типа устроены одинаково
   * во всех формах, где он встречается.
   */
  private final Map<TypeRef, TypeRef> formDataTypes = new HashMap<>();

  /**
   * Зеркало табличной части в данных формы: {@code ДокументТабличнаяЧасть.X.Y} →
   * {@code ДанныеФормыКоллекция.…} (см. {@link #registerTabularSectionData}).
   */
  private final Map<TypeRef, TypeRef> tabularSectionData = new HashMap<>();

  /**
   * Строка коллекции данных формы: {@code ДанныеФормыКоллекция.…} →
   * {@code ДанныеФормыЭлементКоллекции.…} с её колонками. Её отдают
   * {@code ТекущиеДанные} и {@code ДанныеСтроки} таблицы над этой коллекцией.
   */
  private final Map<TypeRef, TypeRef> rowByCollection = new HashMap<>();

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
    registerItemKindTypes();
    var count = 0;
    for (var md : children) {
      if (md instanceof CommonForm commonForm) {
        count += registerForm(commonForm, null) ? 1 : 0;
      } else if (md instanceof FormOwner formOwner) {
        for (var form : formOwner.getForms()) {
          count += registerForm(form, md) ? 1 : 0;
        }
      }
    }
    LOGGER.debug("Form types registered: {}", count);
  }

  /**
   * Регистрирует по одному типу на вид элемента формы — «базовый рантайм-тип плюс
   * расширение вида» ({@code ГруппаФормы.ОбычнаяГруппа} = {@code ГруппаФормы} +
   * {@code Расширение группы формы для обычной группы}). Без расширения элемент видит
   * лишь общую часть: {@code ЦветФона} у обычной группы объявлен именно в расширении.
   * <p>
   * Типов ровно столько, сколько видов элементов, — они не зависят от конфигурации,
   * поэтому регистрируются один раз на workspace и заранее: {@link #buildItemMembers}
   * вызывается лениво изнутри {@code getMembers}, а регистрация источников там сбивала бы
   * epoch кэша членов прямо во время его пересчёта.
   * <p>
   * Отображаемое имя остаётся базовым ({@code ГруппаФормы}) — синтетическое имя нужно
   * реестру для различения видов, а пользователю показывать надо реальный тип значения.
   */
  private void registerItemKindTypes() {
    for (var elementType : FormElementType.values()) {
      var baseName = FormPlatformTypes.itemTypeName(elementType);
      var extensionName = FormPlatformTypes.itemExtensionTypeName(elementType);
      if (baseName == null || extensionName == null) {
        continue;
      }
      var baseRef = typeRegistry.resolve(baseName).orElse(null);
      var extensionRef = typeRegistry.resolve(extensionName).orElse(null);
      if (baseRef == null || extensionRef == null) {
        // Нет синтакс-помощника — расширений в JSON-фолбэке нет; элемент останется
        // с базовым типом, без свойств вида.
        continue;
      }
      var kindRef = typeRegistry.registerConfigurationType(
        baseName + "." + FormPlatformTypes.itemKindSuffix(elementType));
      typeRegistry.registerExtension(kindRef, baseRef, FileType.BSL);
      typeRegistry.registerExtension(kindRef, extensionRef, FileType.BSL);
      typeRegistry.registerDisplayName(kindRef, BilingualString.of(
        typeRegistry.displayName(baseRef, Language.RU),
        typeRegistry.displayName(baseRef, Language.EN)));
      itemKindTypes.put(elementType, kindRef);
    }
    registerTableDataKindTypes();
  }

  /**
   * Регистрирует по типу на вид данных таблицы формы ({@code ТаблицаФормы.ДинамическийСписок}).
   * Устроено как {@link #registerItemKindTypes}, но ось другая: у таблицы расширение
   * определяется не видом элемента, а тем, что она отображает.
   */
  private void registerTableDataKindTypes() {
    var baseName = FormPlatformTypes.tableTypeName();
    var baseRef = typeRegistry.resolve(baseName).orElse(null);
    if (baseRef == null) {
      return;
    }
    for (var dataKind : FormPlatformTypes.TableDataKind.values()) {
      var extensionRef = typeRegistry.resolve(dataKind.extensionName()).orElse(null);
      if (extensionRef == null) {
        continue;
      }
      var kindRef = typeRegistry.registerConfigurationType(baseName + "." + dataKind.suffix());
      typeRegistry.registerExtension(kindRef, baseRef, FileType.BSL);
      typeRegistry.registerExtension(kindRef, extensionRef, FileType.BSL);
      typeRegistry.registerDisplayName(kindRef, BilingualString.of(
        typeRegistry.displayName(baseRef, Language.RU),
        typeRegistry.displayName(baseRef, Language.EN)));
      tableDataKindTypes.put(dataKind, kindRef);
      registerRowDataKindType(dataKind, kindRef);
      registerTableDataRules(dataKind, kindRef);
    }
  }

  /**
   * Уточняет члены таблицы формы по виду отображаемых данных. Синтакс-помощник
   * описывает эти правила текстом в описании расширения, а не объявлением типов:
   * «в качестве значений для свойств {@code ТекущаяСтрока}, {@code ТекущийРодитель} …
   * используется идентификатор типа {@code ИдентификаторКомпоновкиДанных}»,
   * «{@code ТекущиеДанные} и метод {@code ДанныеСтроки} возвращают структуру,
   * заполненную копией данных». Сами члены объявлены как {@code Произвольный}, поэтому
   * без такой подстановки цепочка от них обрывается.
   * <p>
   * Описание — не истина в последней инстанции: тип {@code Структура} таблица формы
   * не отдаёт нигде — там либо строка коллекции, либо {@code ДанныеФормыСтруктура}
   * (см. {@link FormPlatformTypes.TableDataKind#currentDataTypeName}), — а тип
   * идентификатора описание не называет вовсе, хотя он известен из самих данных формы.
   */
  private void registerTableDataRules(FormPlatformTypes.TableDataKind dataKind, TypeRef tableRef) {
    var members = new ArrayList<MemberDescriptor>();
    var rowIdRef = resolveOrNull(dataKind.rowIdTypeName());
    if (rowIdRef != null) {
      members.add(platformProperty(CURRENT_ROW, rowIdRef, BilingualString.EMPTY));
      members.add(platformProperty(CURRENT_PARENT, rowIdRef, BilingualString.EMPTY));
      var selectedRowsRef = registerIdentifierArray(rowIdRef);
      if (selectedRowsRef != null) {
        members.add(platformProperty(SELECTED_ROWS, selectedRowsRef, BilingualString.EMPTY));
      }
    }
    var currentDataRef = resolveOrNull(dataKind.currentDataTypeName());
    if (currentDataRef != null) {
      members.add(platformProperty(
        BilingualString.of(FormPlatformTypes.CURRENT_DATA_RU, FormPlatformTypes.CURRENT_DATA_EN),
        currentDataRef, BilingualString.EMPTY));
      members.add(MemberDescriptor.method(ROW_DATA_METHOD.ru(), "",
          List.of(new SignatureDescriptor(
            List.of(new ParameterDescriptor(CURRENT_ROW, TypeSet.EMPTY, false,
              BilingualString.of("Идентификатор строки.", "Row identifier."), "")),
            TypeSet.of(currentDataRef), BilingualString.EMPTY)))
        .withBilingualName(ROW_DATA_METHOD));
    }
    if (!members.isEmpty()) {
      typeRegistry.registerMemberOverride(tableRef, () -> members, FileType.BSL);
    }
  }

  private @Nullable TypeRef resolveOrNull(@Nullable String typeName) {
    return typeName == null ? null : typeRegistry.resolve(typeName).orElse(null);
  }

  /**
   * Массив идентификаторов строк — то, что лежит в {@code ВыделенныеСтроки}. Платформа
   * объявляет там обычный {@code Массив}, а описание расширения говорит, чем он заполнен;
   * без специализации обход {@code Для Каждого Идентификатор Из ВыделенныеСтроки} терял
   * тип элемента.
   *
   * @param elementRef тип элемента массива.
   * @return специализация массива; {@code null}, если самого {@code Массив} в реестре нет.
   */
  private @Nullable TypeRef registerIdentifierArray(TypeRef elementRef) {
    var arrayBase = typeRegistry.resolve(ARRAY_RU).orElse(null);
    if (arrayBase == null) {
      return null;
    }
    var arrayRef = registerFormDataMirror(ARRAY_RU, ARRAY_EN, elementRef.qualifiedName(), "", arrayBase);
    typeRegistry.registerDefaultElementTypes(arrayRef, List.of(elementRef));
    typeRegistry.inheritCollectionTraits(arrayRef, arrayBase, FileType.BSL);
    return arrayRef;
  }

  /**
   * Тип строки данных таблицы формы, если платформа даёт для него расширение. Пока
   * такое расширение одно — у динамического списка («когда в списке задано условное
   * оформление»), и на управляемой форме динамический список — единственный источник
   * строк, у которого своя специфика.
   * <p>
   * Тип заводится и тогда, когда расширение пустое: он нужен как якорь для
   * {@code ТекущиеДанные} — вместо обобщённого {@code ДанныеФормыЭлементКоллекции}
   * там оказывается строка именно этого вида данных. Колонки строки лягут на него же,
   * когда mdclasses начнёт отдавать источник данных списка (mdclasses#671).
   */
  private void registerRowDataKindType(FormPlatformTypes.TableDataKind dataKind, TypeRef tableRef) {
    var extensionName = FormPlatformTypes.rowDataExtensionName(dataKind);
    if (extensionName == null) {
      return;
    }
    var rowBaseRef = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU).orElse(null);
    var extensionRef = typeRegistry.resolve(extensionName).orElse(null);
    if (rowBaseRef == null || extensionRef == null) {
      return;
    }
    var rowRef = typeRegistry.registerConfigurationType(
      FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU + "." + dataKind.suffix());
    typeRegistry.registerExtension(rowRef, rowBaseRef, FileType.BSL);
    typeRegistry.registerExtension(rowRef, extensionRef, FileType.BSL);
    typeRegistry.registerDisplayName(rowRef, BilingualString.of(
      typeRegistry.displayName(rowBaseRef, Language.RU),
      typeRegistry.displayName(rowBaseRef, Language.EN)));
    typeRegistry.registerMemberOverride(tableRef,
      () -> List.of(platformProperty(
        BilingualString.of(FormPlatformTypes.CURRENT_DATA_RU, FormPlatformTypes.CURRENT_DATA_EN),
        rowRef, BilingualString.EMPTY)),
      FileType.BSL);
  }

  private boolean registerForm(Form form, @Nullable MD owner) {
    var suffixRu = FormPlatformTypes.mdoSuffixRu(form);
    if (suffixRu.isBlank()) {
      return false;
    }
    var kind = FormKind.of(form.getFormType());
    var formRef = registerWithAlias(
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
    // Члены собираются внутри источников, а не здесь: у крупной конфигурации формы
    // считаются тысячами, а элементов на форме — сотнями. Реестр мемоизирует
    // getMembers, поэтому реально материализуются только запрошенные формы.
    // Типы реквизитов — исключение: их надо посчитать сразу, потому что под них
    // регистрируются типы данных формы, а регистрация изнутри ленивого источника
    // сбивала бы epoch кэша членов во время его же пересчёта.
    var attributeTypes = prepareAttributeTypes(data.getAttributes(), kind, suffixRu);
    typeRegistry.registerMemberSource(formRef,
      () -> buildAttributeMembers(data.getAttributes(), attributeTypes), FileType.BSL);
    // Override, а не обычный источник: если обработчик назван каноническим именем
    // события (так конфигуратор делает по умолчанию), то же имя приходит и от
    // расширения — но там тип параметра остаётся обобщённым
    // (`ДокументОбъект.<Имя документа>`), а здесь подставлен владелец формы.
    typeRegistry.registerMemberOverride(formRef,
      () -> concat(buildEventMembers(collectHandlers(form, kind, baseRef, extensionRef), ownerName(form)),
        buildCommandHandlers(data.getCommands())), FileType.BSL);
    // Роли считаются сразу: имена дешевле членов (в реестр не ходим), а знать, кем
    // объявлен обработчик, нужно снаружи системы типов — стандартным областям модуля
    // и hover'у.
    formHandlerRoleIndex.register(formRef, collectHandlerRoles(data));

    // Override, а не обычный источник: `Элементы`/`Параметры`/`Команды`/`ЭтотОбъект`
    // есть и у базового типа, но с обобщёнными типами — специализированные должны
    // выигрывать дедуп в getMembers.
    var tableTypes = prepareTableTypes(data, kind, suffixRu, attributeTypes);
    var itemsRef = registerItemsCollection(form, kind, suffixRu, tableTypes);
    var parametersRef = registerParametersStructure(form, kind, suffixRu, owner);
    var commandsRef = registerCommandsCollection(form, kind, suffixRu);
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
   * Тип структуры параметров формы ({@code ДанныеФормыСтруктура.<mdoRef>}). Наполняется
   * из двух источников: стандартные параметры платформы — общие для любой формы и
   * специфичные для вида основных данных (см. {@link FormParametersResolver}), — плюс
   * объявленные в самой форме (блок {@code <Parameters>}).
   * <p>
   * Generic-плейсхолдеры в типах параметров ({@code Ключ} у справочника объявлен как
   * {@code СправочникСсылка.<Имя справочника>}) подставляются именем владельца формы.
   */
  private @Nullable TypeRef registerParametersStructure(Form form, FormKind kind, String suffixRu,
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
    var parametersRef = registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_STRUCTURE_RU, FormPlatformTypes.FORM_DATA_STRUCTURE_EN,
      suffixRu, FormPlatformTypes.mdoSuffixEn(form), structureRef);
    var ownerName = ownerName(form);
    typeRegistry.registerMemberSource(parametersRef,
      () -> declaredFirst(buildParameterMembers(ownParameters),
        withBasisType(specializeByOwner(concat(baseParameters, extensionParameters), ownerName), owner)),
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
        .withBilingualName(neutral(name)));
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

  private static List<MemberDescriptor> concat(List<MemberDescriptor> first, List<MemberDescriptor> second) {
    if (second.isEmpty()) {
      return first;
    }
    var result = new ArrayList<MemberDescriptor>(first.size() + second.size());
    result.addAll(first);
    result.addAll(second);
    return result;
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
      .map(basis -> typeNameWithSuffix(basis.getMdoRefRu(), REF_SUFFIX))
      .map(typeRegistry::resolve)
      .flatMap(Optional::stream)
      .toList();
    return refs.isEmpty() ? UNDEFINED : TypeSet.of(refs);
  }

  /** Имя MD-объекта, которому подчинена форма; для общей формы — пусто. */
  private static String ownerName(Form form) {
    return form instanceof ObjectForm objectForm ? shortName(objectForm.getOwner().getMdoRefRu()) : "";
  }

  /** {@code Документ.Документ1} → {@code Документ1}. */
  private static String shortName(String mdoRef) {
    var dot = mdoRef.lastIndexOf('.');
    return dot < 0 ? mdoRef : mdoRef.substring(dot + 1);
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
        .map(documentRef -> shortName(documentRef.getMdoRefRu()))
        .filter(name -> !name.isBlank())
        .toList();
    }
    var ownerName = ownerName(form);
    return ownerName.isEmpty() ? List.of() : List.of(ownerName);
  }

  /**
   * Параметры формы, специфичные для вида её основных данных. Кандидаты перебираются
   * по приоритету: у типа, которого нет в синтакс-помощнике, параметров не окажется.
   */
  private List<MemberDescriptor> parameterExtensionParameters(List<FormAttribute> attributes, FormKind kind) {
    return mainAttributeTypeNames(attributes, valueTypeRu ->
      FormPlatformTypes.parameterExtensionTypeNames(valueTypeRu, kind))
      .map(formParametersResolver::parametersOf)
      .filter(parameters -> !parameters.isEmpty())
      .findFirst()
      .orElse(List.of());
  }

  /**
   * Типы таблиц формы, у которых {@code ТекущиеДанные} — строка отображаемой коллекции
   * с её колонками (табличная часть, реквизит-таблица или дерево значений). Такой тип
   * заводится на <b>конкретную таблицу</b>, а не на вид данных: колонки у каждой свои.
   * <p>
   * Считается на регистрации формы, а не в ленивом источнике членов: здесь
   * регистрируются типы, а регистрация изнутри {@code getMembers} сбивала бы epoch
   * кэша во время его же пересчёта.
   *
   * @param data           содержимое формы.
   * @param kind           вид формы.
   * @param suffixRu       суффикс имени типов этой формы.
   * @param attributeTypes типы реквизитов формы (уже переведённые в данные формы).
   * @return {@code имя элемента (lower) → тип таблицы}; пусто, если таких таблиц нет.
   */
  private Map<String, TypeRef> prepareTableTypes(FormData data, FormKind kind, String suffixRu,
                                                 Map<String, TypeSet> attributeTypes) {
    if (kind != FormKind.MANAGED) {
      return Map.of();
    }
    var declaredTypes = attributeTypesByName(data.getAttributes());
    var result = new HashMap<String, TypeRef>();
    for (var element : data.getPlainElements()) {
      if (!(element instanceof FormTable) || element.getName().isBlank()) {
        continue;
      }
      var rowRef = rowOf(element, attributeTypes, declaredTypes);
      var kindRef = tableTypeRef(element, declaredTypes);
      if (rowRef == null || kindRef == null) {
        continue;
      }
      var tableRef = typeRegistry.registerConfigurationType(
        FormPlatformTypes.tableTypeName() + "." + suffixRu + "." + element.getName());
      typeRegistry.registerExtension(tableRef, kindRef, FileType.BSL);
      typeRegistry.registerDisplayName(tableRef, BilingualString.of(
        typeRegistry.displayName(kindRef, Language.RU),
        typeRegistry.displayName(kindRef, Language.EN)));
      typeRegistry.registerMemberOverride(tableRef, () -> currentDataMembers(rowRef), FileType.BSL);
      result.put(element.getName().toLowerCase(Locale.ROOT), tableRef);
    }
    return Map.copyOf(result);
  }

  /**
   * Строка отображаемой таблицей коллекции: путь к данным ведёт либо прямо в
   * реквизит-таблицу формы, либо вглубь основного реквизита — в табличную часть объекта.
   *
   * @return тип строки; {@code null}, если у данных таблицы колонок нет.
   */
  private @Nullable TypeRef rowOf(FormElement element, Map<String, TypeSet> attributeTypes,
                                  Map<String, String> declaredTypes) {
    var dataPath = element instanceof FormDataPathOwner owner ? owner.getDataPath() : "";
    if (dataPath.isBlank()) {
      return null;
    }
    var separator = dataPath.indexOf('.');
    if (separator < 0) {
      return attributeTypes.getOrDefault(dataPath.toLowerCase(Locale.ROOT), TypeSet.EMPTY)
        .refs().stream()
        .map(rowByCollection::get)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    }
    var rootName = dataPath.substring(0, separator).toLowerCase(Locale.ROOT);
    var rootType = declaredTypes.getOrDefault(rootName, "");
    var current = rootType.isBlank() ? null : typeRegistry.resolve(rootType).orElse(null);
    for (var segment : dataPath.substring(separator + 1).split("\\.")) {
      if (current == null) {
        return null;
      }
      current = propertyTypeRef(current, segment);
    }
    // Путь идёт по прикладным типам, а строка данных формы живёт у зеркала табличной части.
    var mirror = current == null ? null : tabularSectionData.get(current);
    return mirror == null ? null : rowByCollection.get(mirror);
  }

  /**
   * {@code ТекущиеДанные} и {@code ДанныеСтроки} конкретной таблицы — строка её
   * коллекции. Описание расширения называет это «структурой, заполненной копией
   * данных», но проверка на платформе даёт
   * {@code ТипЗнч(…ТекущиеДанные) = ДанныеФормыЭлементКоллекции}: «структура» там —
   * про устройство значения, а не про тип.
   */
  private static List<MemberDescriptor> currentDataMembers(TypeRef rowRef) {
    return List.of(
      platformProperty(
        BilingualString.of(FormPlatformTypes.CURRENT_DATA_RU, FormPlatformTypes.CURRENT_DATA_EN),
        rowRef, BilingualString.EMPTY),
      MemberDescriptor.method(ROW_DATA_METHOD.ru(), "",
          List.of(new SignatureDescriptor(
            List.of(new ParameterDescriptor(CURRENT_ROW, TypeSet.EMPTY, false,
              BilingualString.of("Идентификатор строки.", "Row identifier."), "")),
            TypeSet.of(rowRef), BilingualString.EMPTY)))
        .withBilingualName(ROW_DATA_METHOD));
  }

  /**
   * Тип коллекции элементов формы ({@code ВсеЭлементыФормы.<mdoRef>}): платформенные
   * методы коллекции наследуются от базового типа, а generic-слот
   * {@code <Имя элемента управления>} заменяется реальными элементами формы с их
   * рантайм-типами.
   */
  private TypeRef registerItemsCollection(Form form, FormKind kind, String suffixRu,
                                          Map<String, TypeRef> tableTypes) {
    var itemsRef = registerWithAlias(
      kind.itemsTypeRu() + "." + suffixRu,
      kind.itemsTypeEn() + "." + FormPlatformTypes.mdoSuffixEn(form));
    var itemsBaseRef = typeRegistry.resolve(kind.itemsTypeRu()).orElse(null);
    if (itemsBaseRef != null) {
      typeRegistry.registerExtension(itemsRef, itemsBaseRef, FileType.BSL);
      typeRegistry.inheritCollectionTraits(itemsRef, itemsBaseRef, FileType.BSL);
    }
    typeRegistry.registerMemberSource(itemsRef,
      () -> buildItemMembers(form.getData().getPlainElements(), form.getData().getAttributes(), kind, tableTypes),
      FileType.BSL);
    return itemsRef;
  }

  /**
   * Тип коллекции команд формы ({@code КомандыФормы.<mdoRef>}): устроен как коллекция
   * элементов, только имена берутся из блока {@code <Commands>}, а тип у всех один —
   * {@code КомандаФормы}.
   *
   * @return тип коллекции; {@code null}, если команд у формы нет — тогда свойство
   *   {@code Команды} остаётся обобщённым.
   */
  private @Nullable TypeRef registerCommandsCollection(Form form, FormKind kind, String suffixRu) {
    var commands = form.getData().getCommands();
    if (kind != FormKind.MANAGED || commands.isEmpty()) {
      return null;
    }
    var commandsRef = registerWithAlias(
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
          .withBilingualName(neutral(name))
          .withBilingualDescription(bilingual(command.getTitle())));
    }
    return List.copyOf(byName.values());
  }

  /**
   * Процедуры-обработчики команд. Обработчик команды объявлен не событием, а действием
   * ({@code <Action>}), но по сути это то же самое: процедура модуля формы, которую
   * зовёт платформа.
   */
  private List<MemberDescriptor> buildCommandHandlers(List<FormCommand> commands) {
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
          .withBilingualName(neutral(action))
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
    members.add(platformProperty(
      BilingualString.of(kind.itemsPropertyRu(), kind.itemsPropertyEn()), itemsRef, BilingualString.EMPTY));
    if (parametersRef != null) {
      members.add(platformProperty(
        BilingualString.of(FormPlatformTypes.PARAMETERS_PROPERTY_RU, FormPlatformTypes.PARAMETERS_PROPERTY_EN),
        parametersRef, BilingualString.EMPTY));
    }
    if (commandsRef != null) {
      members.add(platformProperty(
        BilingualString.of(FormPlatformTypes.COMMANDS_PROPERTY_RU, FormPlatformTypes.COMMANDS_PROPERTY_EN),
        commandsRef, BilingualString.EMPTY));
    }
    // У обычной формы `ЭтотОбъект` не существует вовсе: он появляется только в модуле
    // формы объекта — и ведёт на сам объект, чей контекст туда инжечен
    // (см. registerModuleType). У управляемой формы это сама форма.
    if (kind == FormKind.MANAGED || injectedRef != null) {
      members.add(platformProperty(BilingualString.of(THIS_OBJECT_RU, THIS_OBJECT_EN),
        injectedRef == null ? formRef : injectedRef, BilingualString.EMPTY));
    }
    // `ЭтаФорма` платформа отдаёт в модуле формы обоих видов: у обычной это штатное
    // имя, у управляемой — устаревший синоним `ЭтотОбъект` (см. UsingThisFormDiagnostic).
    members.add(platformProperty(
      BilingualString.of(THIS_FORM_RU, THIS_FORM_EN), formRef, THIS_FORM_DESCRIPTION));
    return List.copyOf(members);
  }

  private static MemberDescriptor platformProperty(BilingualString name, TypeRef type,
                                                   BilingualString description) {
    return MemberDescriptor.property(name.primary(), type)
      .withBilingualName(name)
      .withBilingualDescription(description)
      .withStandardLibrary(true);
  }

  /**
   * Реквизиты формы как свойства её типа. Тип — результат преобразования объявленного
   * в {@code Form.xml} типа в тип данных формы (см. {@link #prepareAttributeTypes});
   * нерезолвящиеся типы дают свойство без типа — имя в автодополнении всё равно нужно.
   */
  private List<MemberDescriptor> buildAttributeMembers(List<FormAttribute> attributes,
                                                       Map<String, TypeSet> attributeTypes) {
    if (attributes.isEmpty()) {
      return List.of();
    }
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var name = attribute.getName();
      if (name.isBlank()) {
        continue;
      }
      var key = name.toLowerCase(Locale.ROOT);
      var types = attributeTypes.getOrDefault(key, TypeSet.EMPTY);
      var descriptor = MemberDescriptor.property(name, types, "")
        .withBilingualName(neutral(name))
        .withBilingualDescription(bilingual(attribute.getTitle()));
      byName.putIfAbsent(key, descriptor);
    }
    return List.copyOf(byName.values());
  }

  /**
   * Типы реквизитов формы: {@code имя реквизита (lower) → тип}. Объявленный в
   * {@code Form.xml} прикладной тип заменяется типом данных формы — на управляемой
   * форме за реквизитом стоит не сам объект, а его данные
   * ({@code ДокументОбъект.X} → {@code ДанныеФормыСтруктура}, набор записей →
   * {@code ДанныеФормыСтруктураСКоллекцией}, {@code ТаблицаЗначений} →
   * {@code ДанныеФормыКоллекция}, {@code ДеревоЗначений} → {@code ДанныеФормыДерево}).
   * Прочие типы — ссылки, примитивы, {@code СписокЗначений}, {@code ДинамическийСписок} —
   * переносятся как есть.
   * <p>
   * У обычной формы преобразования нет: там реквизит — сам прикладной объект.
   */
  private Map<String, TypeSet> prepareAttributeTypes(List<FormAttribute> attributes, FormKind kind,
                                                     String suffixRu) {
    if (attributes.isEmpty()) {
      return Map.of();
    }
    var byName = LinkedHashMap.<String, TypeSet>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var name = attribute.getName();
      if (name.isBlank()) {
        continue;
      }
      var declared = ValueTypes.resolve(typeRegistry, attribute.getValueType());
      byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
        kind == FormKind.MANAGED ? formDataTypes(declared, attribute, kind, suffixRu) : declared);
    }
    return Map.copyOf(byName);
  }

  /** Объявленные типы реквизита, переведённые в типы данных формы. */
  private TypeSet formDataTypes(TypeSet declared, FormAttribute attribute, FormKind kind, String suffixRu) {
    if (declared.isEmpty()) {
      return declared;
    }
    var converted = new ArrayList<TypeRef>(declared.refs().size());
    var changed = false;
    for (var ref : declared.refs()) {
      var formDataRef = formDataTypeRef(ref, attribute, kind, suffixRu);
      changed |= formDataRef != null;
      converted.add(formDataRef == null ? ref : formDataRef);
    }
    return changed ? TypeSet.of(converted) : declared;
  }

  /**
   * Тип данных формы для объявленного типа реквизита.
   *
   * @return тип данных формы; {@code null}, если тип переносится на форму как есть.
   */
  private @Nullable TypeRef formDataTypeRef(TypeRef declaredRef, FormAttribute attribute, FormKind kind,
                                            String suffixRu) {
    var dataKind = FormPlatformTypes.formDataKindOf(declaredRef.qualifiedName());
    if (dataKind == null) {
      return null;
    }
    if (dataKind.itemTypeRu() != null) {
      return registerAttributeCollection(attribute, dataKind, kind, suffixRu);
    }
    if (!dataKind.specializable()) {
      return typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    }
    return formDataTypes.computeIfAbsent(declaredRef, ref -> registerFormData(ref, dataKind));
  }

  /**
   * Регистрирует данные формы под реквизит-таблицу или дерево значений: колонки такого
   * реквизита объявлены в самой форме (блок {@code <Columns>}) и ложатся свойствами
   * строки — так же, как колонки табличной части ложатся в строку её зеркала.
   * <p>
   * Тип заводится на реквизит конкретной формы, а не на прикладной тип: две формы с
   * реквизитом-{@code ТаблицаЗначений} — это две разные таблицы с разными колонками.
   *
   * @return тип коллекции; базовый {@code ДанныеФормыКоллекция}/{@code ДанныеФормыДерево},
   *   если колонок нет — специализировать тогда нечем.
   */
  private @Nullable TypeRef registerAttributeCollection(FormAttribute attribute,
                                                        FormPlatformTypes.FormDataKind dataKind,
                                                        FormKind kind, String suffixRu) {
    var itemTypeRu = Objects.requireNonNullElse(dataKind.itemTypeRu(), "");
    var itemTypeEn = Objects.requireNonNullElse(dataKind.itemTypeEn(), "");
    var collectionBase = typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    var itemBase = typeRegistry.resolve(itemTypeRu).orElse(null);
    var columns = attribute.getColumns();
    if (columns.isEmpty() || collectionBase == null || itemBase == null) {
      return collectionBase;
    }
    var suffix = suffixRu + "." + attribute.getName();
    var itemRef = registerFormDataMirror(itemTypeRu, itemTypeEn, suffix, "", itemBase);
    var collectionRef = registerFormDataMirror(
      dataKind.baseTypeRu(), dataKind.baseTypeEn(), suffix, "", collectionBase);
    // Порядок важен: явный тип элемента должен быть задан до наследования, иначе
    // выиграет обобщённая строка базового типа (см. registerTabularSectionData).
    typeRegistry.registerDefaultElementTypes(collectionRef, List.of(itemRef));
    typeRegistry.inheritCollectionTraits(collectionRef, collectionBase, FileType.BSL);

    // Колонки — это те же реквизиты формы, только вложенные: у них есть и свои типы,
    // и заголовки, и собственные колонки, если колонка сама таблица.
    var columnTypes = prepareAttributeTypes(columns, kind, suffix);
    MemberSource columnMembers = () -> buildAttributeMembers(columns, columnTypes);
    typeRegistry.registerMemberSource(itemRef, columnMembers, FileType.BSL);
    rowByCollection.put(collectionRef, itemRef);
    return collectionRef;
  }

  /**
   * Регистрирует тип данных формы под конкретный прикладной тип
   * ({@code ДанныеФормыСтруктура.ДокументОбъект.Документ1}): платформенные методы
   * наследуются от базового типа данных формы, а свойства берутся у самого
   * прикладного типа.
   * <p>
   * Тип заводится на прикладной тип, а не на форму: у всех форм одного документа
   * данные устроены одинаково, и общий тип экономит и регистрацию, и память.
   * Отображаемое имя остаётся базовым ({@code ДанныеФормыСтруктура}) — синтетическое
   * имя нужно реестру, а пользователю показывать надо реальный тип значения.
   */
  private TypeRef registerFormData(TypeRef declaredRef, FormPlatformTypes.FormDataKind dataKind) {
    var baseRef = typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    var dataRef = registerFormDataMirror(dataKind.baseTypeRu(), dataKind.baseTypeEn(),
      typeRegistry.displayName(declaredRef, Language.RU),
      typeRegistry.displayName(declaredRef, Language.EN), baseRef);
    if (baseRef != null) {
      typeRegistry.inheritCollectionTraits(dataRef, baseRef, FileType.BSL);
    }
    typeRegistry.registerMemberSource(dataRef, () -> dataProperties(declaredRef), FileType.BSL);
    return dataRef;
  }

  /**
   * Регистрирует типы данных формы под табличную часть: коллекцию
   * ({@code ДанныеФормыКоллекция}) и её строку ({@code ДанныеФормыЭлементКоллекции}).
   * Колонки у них те же, что у самой табличной части, поэтому источник членов
   * переиспользуется, а не собирается заново.
   * <p>
   * Регистрация идёт при обходе метаданных, а не при регистрации формы: форма узнаёт
   * о табличных частях объекта только из членов его типа, а читать их на регистрации
   * нельзя — это преждевременно материализовало бы тип.
   *
   * @param tabularSectionRef тип табличной части ({@code ДокументТабличнаяЧасть.X.Y}).
   * @param columns           источник колонок табличной части.
   */
  public void registerTabularSectionData(TypeRef tabularSectionRef, MemberSource columns) {
    var collectionBase = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_RU).orElse(null);
    var itemBase = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU).orElse(null);
    if (collectionBase == null || itemBase == null) {
      // Нет ни синтакс-помощника, ни фолбэка — табличная часть останется со своим типом.
      return;
    }
    var suffix = tabularSectionRef.qualifiedName();
    var itemRef = registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU, FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_EN,
      suffix, "", itemBase);
    var collectionRef = registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_COLLECTION_RU, FormPlatformTypes.FORM_DATA_COLLECTION_EN,
      suffix, "", collectionBase);
    // Явные типы элементов должны быть заданы до наследования от базового типа:
    // иначе выиграет унаследованный обобщённый ДанныеФормыЭлементКоллекции и
    // `Для Каждого Строка Из Объект.Товары` потеряет колонки.
    typeRegistry.registerDefaultElementTypes(collectionRef, List.of(itemRef));
    typeRegistry.inheritCollectionTraits(collectionRef, collectionBase, FileType.BSL);

    // Колонки — только у строки: у самой ДанныеФормыКоллекция их нет, обращение
    // `Объект.Товары.Цена` на форме не работает. (Тип табличной части объекта
    // показывает колонки и на коллекции — там это осознанное послабление, здесь оно
    // ввело бы в заблуждение: у платформенного типа таких свойств действительно нет.)
    typeRegistry.registerMemberSource(itemRef, columns, FileType.BSL);
    // Типы возврата уточняются тем же помощником, что и у табличной части объекта:
    // задача одна — заменить обобщённую строку на строку этой коллекции и доопределить
    // «массив чего» у `НайтиСтроки` и «таблицу с какими колонками» у `Выгрузить`.
    var valueTableRow = typeRegistry.resolve(CollectionReturnsSpecializer.VALUE_TABLE_ROW).orElse(null);
    typeRegistry.registerMemberOverride(collectionRef,
      () -> CollectionReturnsSpecializer.specialize(
        typeRegistry.getMembers(collectionBase, FileType.BSL), itemBase, itemRef,
        CollectionReturnsSpecializer.unloadedRow(valueTableRow, columns)), FileType.BSL);
    tabularSectionData.put(tabularSectionRef, collectionRef);
    rowByCollection.put(collectionRef, itemRef);
  }

  /**
   * Тип данных формы под конкретный прикладной тип: специализация базового типа
   * данных формы с отображаемым именем самого базового типа.
   */
  private TypeRef registerFormDataMirror(String baseRu, String baseEn, String suffixRu, String suffixEn,
                                         @Nullable TypeRef baseRef) {
    var ref = typeRegistry.registerConfigurationType(baseRu + "." + suffixRu);
    var qualifiedEn = baseEn + "." + suffixEn;
    if (!suffixEn.isBlank() && !qualifiedEn.equals(baseRu + "." + suffixRu)) {
      typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    }
    // Отображаемое имя — базовое: синтетический суффикс нужен реестру, чтобы различать
    // специализации, а пользователю показывать надо реальный тип значения.
    typeRegistry.registerDisplayName(ref, BilingualString.of(baseRu, baseEn));
    if (baseRef != null && !baseRef.equals(ref)) {
      typeRegistry.registerExtension(ref, baseRef, FileType.BSL);
    }
    return ref;
  }

  /**
   * Свойства прикладного типа, видимые в данных формы. Методы и события отбрасываются:
   * на клиенте за реквизитом стоят только данные, вызвать {@code Записать()} у них нельзя.
   * Инфраструктурные свойства самого объекта ({@code ЭтотОбъект}, {@code Движения},
   * {@code ОбменДанными} …) тоже не переносятся — см.
   * {@link FormPlatformTypes#isTransferredToFormData}. Табличные части перецепляются на
   * свои зеркала: на форме за ними стоит {@code ДанныеФормыКоллекция}, а не табличная
   * часть объекта.
   */
  private List<MemberDescriptor> dataProperties(TypeRef declaredRef) {
    var members = typeRegistry.getMembers(declaredRef, FileType.BSL);
    var properties = new ArrayList<MemberDescriptor>(members.size());
    for (var member : members) {
      if (member.kind() != MemberKind.PROPERTY
        || !FormPlatformTypes.isTransferredToFormData(member)) {
        continue;
      }
      properties.add(withTabularSectionData(member));
    }
    return List.copyOf(properties);
  }

  /** Свойство с типами табличных частей, заменёнными на их зеркала в данных формы. */
  private MemberDescriptor withTabularSectionData(MemberDescriptor property) {
    if (tabularSectionData.isEmpty()) {
      return property;
    }
    var refs = property.returnTypes().refs();
    var converted = new ArrayList<TypeRef>(refs.size());
    var changed = false;
    for (var ref : refs) {
      var dataRef = tabularSectionData.get(ref);
      changed |= dataRef != null;
      converted.add(dataRef == null ? ref : dataRef);
    }
    return changed ? property.withReturnTypes(TypeSet.of(converted)) : property;
  }

  /**
   * Элементы формы как свойства коллекции. {@code getPlainElements()} уже плоский —
   * в управляемой форме коллекция {@code Элементы} тоже плоская независимо от
   * вложенности групп.
   */
  private List<MemberDescriptor> buildItemMembers(List<FormElement> items, List<FormAttribute> attributes,
                                                  FormKind formKind, Map<String, TypeRef> tableTypes) {
    if (items.isEmpty()) {
      return List.of();
    }
    var attributeTypes = attributeTypesByName(attributes);
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(items.size());
    for (var item : items) {
      var name = item.getName();
      if (name.isBlank()) {
        continue;
      }
      var types = itemTypes(item, attributeTypes, formKind, tableTypes);
      var descriptor = MemberDescriptor.property(name, types, "")
        .withBilingualName(neutral(name))
        .withBilingualDescription(bilingual(item.getTitle()));
      byName.putIfAbsent(name.toLowerCase(Locale.ROOT), descriptor);
    }
    return List.copyOf(byName.values());
  }

  /**
   * Тип элемента формы: если у вида есть расширение — специализация «база + расширение»
   * из {@link #registerItemKindTypes}, иначе просто базовый рантайм-тип. Таблица —
   * особый случай: её расширение выбирается по данным, а не по виду.
   */
  private TypeSet itemTypes(FormElement item, Map<String, String> attributeTypesByName, FormKind formKind,
                            Map<String, TypeRef> tableTypes) {
    if (formKind == FormKind.ORDINARY) {
      // У обычной формы своя иерархия элементов — `ПолеВвода`, `ТабличноеПоле`,
      // `Надпись`, `Флажок` — со своими расширениями. Она здесь не моделируется, а
      // подмешать элементу обычной формы тип управляемой (`ПолеФормы`) было бы прямой
      // ошибкой: это разные типы с разным составом. Лучше не типизировать вовсе.
      return TypeSet.EMPTY;
    }
    var elementType = item.getType();
    if (elementType == FormElementType.TABLE) {
      // Тип на конкретную таблицу заводится, только когда у её строки есть колонки
      // (см. prepareTableTypes); иначе хватает общего типа по виду данных.
      var ownRef = tableTypes.get(item.getName().toLowerCase(Locale.ROOT));
      if (ownRef != null) {
        return TypeSet.of(ownRef);
      }
      var tableRef = tableTypeRef(item, attributeTypesByName);
      if (tableRef != null) {
        return TypeSet.of(tableRef);
      }
    }
    var kindRef = itemKindTypes.get(elementType);
    if (kindRef != null) {
      return TypeSet.of(kindRef);
    }
    var baseName = FormPlatformTypes.itemTypeName(elementType);
    return baseName == null ? TypeSet.EMPTY : TypeSet.of(resolveOrIntern(baseName));
  }

  /**
   * Тип таблицы формы по её {@code ПутьКДанным}: корневой сегмент пути — это реквизит
   * формы, его тип и определяет расширение ({@code ДинамическийСписок} → расширение
   * динамического списка, {@code Объект.Товары} → расширение табличных частей).
   *
   * @return специализированный тип таблицы; {@code null}, если путь пуст, реквизит не
   *   найден или вид данных не опознан — тогда останется базовая {@code ТаблицаФормы}.
   */
  private @Nullable TypeRef tableTypeRef(FormElement item, Map<String, String> attributeTypesByName) {
    var dataPath = item instanceof FormDataPathOwner owner ? owner.getDataPath() : "";
    if (dataPath.isBlank()) {
      return null;
    }
    var separator = dataPath.indexOf('.');
    var rootName = separator < 0 ? dataPath : dataPath.substring(0, separator);
    var rootType = attributeTypesByName.getOrDefault(rootName.toLowerCase(Locale.ROOT), "");

    // Сначала пройти путь целиком: у частей компоновщика настроек значимый тип стоит в
    // конце (`Отчет.КомпоновщикНастроек.Настройки.Выбор`), а не в корне.
    var walked = walkedDataKind(dataPath, separator, rootType);
    var dataKind = walked != null
      ? walked
      : FormPlatformTypes.TableDataKind.of(rootType, separator >= 0);
    return dataKind == null ? null : tableDataKindTypes.get(dataKind);
  }

  /**
   * Вид данных по типу <b>последнего</b> сегмента {@code ПутьКДанным}. Путь идёт по
   * членам: корень — реквизит формы, дальше свойства их типов.
   * <p>
   * Опознаются только виды с известным типом данных, поэтому «не дошли» и «дошли до
   * неинтересного типа» дают один результат — {@code null}, и вызывающий откатывается
   * на разбор по корню пути (там табличная часть опознаётся по вложенности).
   *
   * @return вид данных; {@code null}, если путь односегментный, не резолвится или
   *   тип последнего сегмента не опознан.
   */
  private @Nullable TableDataKind walkedDataKind(String dataPath, int separator,
                                                                  String rootType) {
    if (separator < 0 || rootType.isBlank()) {
      return null;
    }
    var current = typeRegistry.resolve(rootType).orElse(null);
    if (current == null) {
      return null;
    }
    for (var segment : dataPath.substring(separator + 1).split("\\.")) {
      current = propertyTypeRef(current, segment);
      if (current == null) {
        return null;
      }
    }
    return FormPlatformTypes.TableDataKind.byTypeName(current.qualifiedName());
  }

  /** Тип свойства с таким именем у типа; {@code null}, если свойства нет или он без типа. */
  private @Nullable TypeRef propertyTypeRef(TypeRef ownerRef, String propertyName) {
    for (var member : typeRegistry.getMembers(ownerRef, FileType.BSL)) {
      if (member.kind() == MemberKind.PROPERTY && !member.generic() && member.matches(propertyName)) {
        return member.returnTypes().refs().stream().findFirst().orElse(null);
      }
    }
    return null;
  }

  /**
   * Карта {@code имя реквизита (lower) → ru-имя его типа} для резолва
   * {@code ПутьКДанным} элементов. Строится по реквизитам формы, а не по реестру:
   * нужно исходное объявление типа из {@code Form.xml}.
   */
  private static Map<String, String> attributeTypesByName(List<FormAttribute> attributes) {
    if (attributes.isEmpty()) {
      return Map.of();
    }
    var byName = LinkedHashMap.<String, String>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var name = attribute.getName();
      if (name.isBlank()) {
        continue;
      }
      var types = attribute.getValueType().getTypes();
      byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
        types.isEmpty() ? "" : types.get(0).fullName().getRu());
    }
    return byName;
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
    var attributeTypes = attributeTypesByName(data.getAttributes());
    for (var element : data.getPlainElements()) {
      if (!(element instanceof FormEventHandlerOwner handlerOwner)) {
        continue;
      }
      var elementTypes = itemTypes(element, attributeTypes, kind, Map.of()).refs().stream().toList();
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
      if (element instanceof FormEventHandlerOwner handlerOwner) {
        var role = ownerTable.isEmpty()
          ? FormHandlerRoleIndex.Role.HEADER_ITEM_EVENT
          : FormHandlerRoleIndex.Role.TABLE_ITEM_EVENT;
        var owner = ownerTable.isEmpty() ? element.getName() : ownerTable;
        for (var handler : handlerOwner.getEventHandlers()) {
          putRole(roles, handler.handler(), role, owner);
        }
      }
      if (element instanceof FormElementOwner elementOwner) {
        collectElementRoles(elementOwner.getElements(), ownerTable, roles);
      }
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
        descriptor.withBilingualName(neutral(handlerName)));
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
    return mainAttributeTypeNames(attributes, valueTypeRu ->
      FormPlatformTypes.extensionTypeNames(valueTypeRu, kind))
      .map(typeRegistry::resolve)
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(null);
  }

  /**
   * Имена платформенных типов, которые сопоставлены типам основного реквизита формы.
   * Реквизит объявлен составным типом, поэтому имён может быть несколько; порядок
   * сохраняется — он и задаёт приоритет.
   *
   * @param attributes реквизиты формы.
   * @param namesOf    что искать по ru-имени типа значения.
   * @return имена типов-кандидатов; пусто — основного реквизита у формы нет.
   */
  private static Stream<String> mainAttributeTypeNames(List<FormAttribute> attributes,
                                                       Function<String, List<String>> namesOf) {
    return attributes.stream()
      .filter(FormAttribute::isMainAttribute)
      .flatMap(attribute -> attribute.getValueType().getTypes().stream())
      .flatMap(valueType -> namesOf.apply(valueType.fullName().getRu()).stream());
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
    return typeNameWithSuffix(owner.getMdoReference().getMdoRefRu(), suffix);
  }

  /** Имя типа из mdoRef и суффикса вида: {@code Справочник.Х} + {@code Ссылка}. */
  private static String typeNameWithSuffix(String mdoRef, String suffix) {
    var dot = mdoRef.indexOf('.');
    return dot < 0 ? mdoRef : mdoRef.substring(0, dot) + suffix + mdoRef.substring(dot);
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

  /** Имена владельцев подчинённого справочника; пусто — справочник не подчинён. */
  private static List<String> ownerObjectNames(@Nullable MD owner) {
    if (!(owner instanceof Catalog catalog)) {
      return List.of();
    }
    return catalog.getOwners().stream()
      .map(ref -> shortName(ref.getMdoRefRu()))
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


  private TypeRef registerWithAlias(String qualifiedRu, String qualifiedEn) {
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    if (!qualifiedEn.isBlank() && !qualifiedEn.equals(qualifiedRu)) {
      typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    }
    typeRegistry.registerDisplayName(ref, BilingualString.of(qualifiedRu, qualifiedEn));
    return ref;
  }

  private TypeRef resolveOrIntern(String qualifiedName) {
    return typeRegistry.resolve(qualifiedName)
      .orElseGet(() -> typeRegistry.intern(TypeKind.PLATFORM, qualifiedName));
  }

  /**
   * Имя, одинаковое в обеих локалях: имена реквизитов, элементов и обработчиков
   * задаёт конфигурация, перевода у них нет — но в автодополнении они должны
   * появляться независимо от языка интерфейса.
   */
  private static BilingualString neutral(String name) {
    return BilingualString.of(name, name);
  }

  private static BilingualString bilingual(MultiLanguageString value) {
    if (value.isEmpty()) {
      return BilingualString.EMPTY;
    }
    return BilingualString.of(value.get("ru"), value.get("en"));
  }

  private static BilingualString unknownEventDescription(String eventName) {
    return BilingualString.of(
      "Обработчик события формы «" + eventName + "».",
      "Handler of the form event \"" + eventName + "\".");
  }
}
