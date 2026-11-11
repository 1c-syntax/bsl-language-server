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
using System.Text.Json.Nodes;
using Xunit;

public sealed class MetadataUpdaterTests
{
    [Fact]
    public void SourceFreeExportIsRejectedWithoutChangingEitherResource()
    {
        var directory = Directory.CreateTempSubdirectory("bsl-oscript-metadata-").FullName;
        var typesFile = Path.Combine(directory, "builtin-oscript-platform-types.json");
        var globalsFile = Path.Combine(directory, "builtin-oscript-globals.json");
        var input = Path.Combine(directory, "runtime.json");
        var basic = Path.Combine(directory, "basic.json");
        try
        {
            File.WriteAllText(typesFile, "[]");
            File.WriteAllText(globalsFile, """{"functions":[{"name":"Intrinsic","signatures":[]}],"variables":[]}""");
            File.WriteAllText(input, "[]");
            File.WriteAllText(basic, "{}");
            var beforeTypes = File.ReadAllBytes(typesFile);
            var beforeGlobals = File.ReadAllBytes(globalsFile);

            var error = Assert.Throws<InvalidOperationException>(() => MetadataUpdater.Update(input, directory, basic));

            Assert.Contains("matching OneScript source directory", error.Message);
            Assert.Equal(beforeTypes, File.ReadAllBytes(typesFile));
            Assert.Equal(beforeGlobals, File.ReadAllBytes(globalsFile));
        }
        finally
        {
            Assert.Equal(Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar),
                Path.GetDirectoryName(Path.GetFullPath(directory)));
            Assert.StartsWith("bsl-oscript-metadata-", Path.GetFileName(directory));
            Directory.Delete(directory, true);
        }
    }

    [Fact]
    public void EnumNamesKeepCuratedDescriptionsAndCorrectEnglishPairs()
    {
        Regenerate("""
            [{"kind":"ENUM","name":"Выбор","members":[
              {"name":"Все","nameRu":"Все","nameEn":"Choice","kind":"PROPERTY",
               "description":"Все элементы без требования порядка","descriptionEn":"Описание другого элемента"},
              {"name":"Один","nameRu":"Один","nameEn":"Sequence","kind":"PROPERTY","description":"Один элемент"}]}]
            """, """
            [{"name":{"Ru":"Выбор","En":"ChoiceKind"},"enumeration":true,
              "values":[{"Ru":"Все","En":"All"},{"Ru":"Один","En":"Choice"}]}]
            """, (types, _) =>
        {
            var members = types[0]!["members"]!.AsArray();
            Assert.Equal("All", Text(members[0]!["nameEn"]));
            Assert.Equal("Все элементы без требования порядка", Text(members[0]!["description"]));
            Assert.Equal("Choice", Text(members[1]!["nameEn"]));
            Assert.Equal("Один элемент", Text(members[1]!["description"]));
            Assert.Null(members[0]!["descriptionEn"]);
        });
    }

    [Fact]
    public void DeprecatedMethodAndGlobalAliasesRemainSeparateFromActiveNames()
    {
        Regenerate("[]", """
            [{"name":{"Ru":"Объект","En":"Object"},"methods":[
              {"name":{"Ru":"Получить","En":"Get"},"deprecatedNames":["OldGet"],"parameters":[]}]},
             {"name":{"Ru":"Глобальный","En":"Global"},"global":true,"methods":[
              {"name":{"Ru":"Функция","En":"Function"},"deprecatedNames":["OldFunction"],"parameters":[]}]}]
            """, (types, globals) =>
        {
            var members = types[0]!["members"]!.AsArray();
            var active = Find(members, "Получить");
            var alias = Find(members, "OldGet");
            Assert.Null(active["deprecatedSinceVersion"]);
            Assert.Equal("*", Text(alias["deprecatedSinceVersion"]));
            Assert.Equal("Получить", Text(alias["recommendedReplacements"]![0]));
            Assert.True(JsonNode.DeepEquals(active["signatures"], alias["signatures"]));
            var globalAlias = Find(globals["functions"]!.AsArray(), "OldFunction");
            Assert.Equal("*", Text(globalAlias["deprecatedSinceVersion"]));
            Assert.Equal("Функция", Text(globalAlias["recommendedReplacements"]![0]));
        });
    }

    [Fact]
    public void BilingualEnumNamesRetainEnglishOnlyLegacyAnnotations()
    {
        Regenerate("""
            [{"kind":"ENUM","name":"Кодировка","members":[
              {"name":"UTF8БезBOM","nameRu":"UTF8БезBOM","nameEn":"UTF8NoBOM","kind":"PROPERTY"},
              {"name":"UTF8NoBOM","nameEn":"UTF8NoBOM","kind":"PROPERTY","description":"UTF-8 без BOM"}]}]
            """, """
            [{"name":{"Ru":"Кодировка","En":"Encoding"},"enumeration":true,
              "values":[{"Ru":"UTF8БезBOM","En":"UTF8NoBOM"}]}]
            """, (types, _) =>
        {
            var member = types[0]!["members"]![0]!;
            Assert.Equal("UTF8БезBOM", Text(member["nameRu"]));
            Assert.Equal("UTF-8 без BOM", Text(member["description"]));
        });
    }

    [Fact]
    public void SameArityConstructorsKeepAnnotationsForTheirOwnParameters()
    {
        Regenerate("""
            [{"kind":"TYPE","name":"Объект","members":[],"constructors":[
              {"parameters":[{"name":"copy","type":"Объект","description":"Копируемый объект"}],"description":"Копия"},
              {"parameters":[{"name":"size","type":"Число","description":"Начальный размер"}],"description":"Размер"}]}]
            """, """
            [{"name":{"Ru":"Объект","En":"Object"},"constructors":[
              {"parameters":[{"name":"size","type":"Число","optional":false}]},
              {"parameters":[{"name":"copy","type":"Объект","optional":false}]}]}]
            """, (types, _) =>
        {
            var constructors = types[0]!["constructors"]!.AsArray();
            Assert.Equal("Размер", Text(constructors[0]!["description"]));
            Assert.Equal("Начальный размер", Text(constructors[0]!["parameters"]![0]!["description"]));
            Assert.Equal("Копия", Text(constructors[1]!["description"]));
        });
    }

    [Fact]
    public void CurrentConsoleDocumentationKeepsTheGlobalObjectUsageRule()
    {
        Regenerate("""[{"kind":"TYPE","name":"Консоль","members":[],"constructors":[]}]""", """
            [{"name":{"Ru":"Консоль","En":"Console"},"description":"Обработка Ctrl+C."}]
            """, (types, _) =>
        {
            Assert.Contains("Ctrl+C", Text(types[0]!["description"]));
            Assert.Contains("Используйте глобальный объект Консоль", Text(types[0]!["description"]));
            Assert.Null(types[0]!["constructors"]);
        });
    }

    [Fact]
    public void MethodsAndGlobalsRetainReturnValueSemantics()
    {
        Regenerate("[]", """
            [{"name":{"Ru":"Объект","En":"Object"},"methods":[
              {"name":{"Ru":"Найти","En":"Find"},"description":"Ищет подстроку.",
               "returnDescription":"Возвращает 0, если подстрока не найдена."}]},
             {"name":{"Ru":"Глобальный","En":"Global"},"global":true,"methods":[
              {"name":{"Ru":"Найти","En":"Find"},"description":"Ищет подстроку.",
               "returnDescription":"Возвращает 0, если подстрока не найдена."}]}]
            """, (types, globals) =>
        {
            const string expected = "Ищет подстроку.\n\nВозвращаемое значение: Возвращает 0, если подстрока не найдена.";
            Assert.Equal(expected, Text(types[0]!["members"]![0]!["description"]));
            Assert.Equal(expected, Text(globals["functions"]![0]!["description"]));
        });
    }

    [Fact]
    public void Base64BufferReturnDescriptionsPreserveTheBinaryDataDistinction()
    {
        Regenerate("[]", """
            [{"name":{"Ru":"Глобальный","En":"Global"},"global":true,"methods":[
              {"name":{"Ru":"ИзБуфера","En":"GetBinaryDataBufferFromBase64BinaryDataBuffer"},
               "returnType":"БуферДвоичныхДанных","returnDescription":"Тип: ДвоичныеДанные."},
              {"name":{"Ru":"ИзСтроки","En":"GetBinaryDataBufferFromBase64String"},
               "returnType":"БуферДвоичныхДанных","returnDescription":"Тип: ДвоичныеДанные."},
              {"name":{"Ru":"Данные","En":"GetBinaryDataFromBase64String"},
               "returnType":"ДвоичныеДанные","returnDescription":"Тип: ДвоичныеДанные."}]}]
            """, (_, globals) =>
        {
            var functions = globals["functions"]!.AsArray();
            Assert.Equal("Возвращаемое значение: Тип: БуферДвоичныхДанных.", Text(Find(functions, "ИзБуфера")["description"]));
            Assert.Equal("Возвращаемое значение: Тип: БуферДвоичныхДанных.", Text(Find(functions, "ИзСтроки")["description"]));
            Assert.Equal("Возвращаемое значение: Тип: ДвоичныеДанные.", Text(Find(functions, "Данные")["description"]));
        });
    }

    [Fact]
    public void CurrentEnumDocumentationRefinesArgumentsWithoutNarrowingArbitraryObjects()
    {
        Regenerate("[]", """
            [{"name":{"Ru":"Направление","En":"Direction"},"enumeration":true},
             {"name":{"Ru":"Объект","En":"Object"},"methods":[
              {"name":{"Ru":"Проверить","En":"Check"},"parameters":[
                {"name":"direction","type":"Произвольный","description":"Направление (необязательный) — порядок.","optional":true},
                {"name":"target","type":"Произвольный","description":"Объект, который нужно проверить.","optional":false}]}]}]
            """, (types, _) =>
        {
            var parameters = Find(types, "Объект")["members"]![0]!["signatures"]![0]!["parameters"]!;
            Assert.Equal("Направление", Text(parameters[0]!["type"]));
            Assert.Equal("Произвольный", Text(parameters[1]!["type"]));
        });
    }

    [Fact]
    public void GenericClrArgumentsKeepCuratedEnumsAndDiscardUnprovenObjectTypes()
    {
        Regenerate("""
            [{"kind":"ENUM","name":"Направление","members":[]},
             {"kind":"TYPE","name":"Объект","members":[{"name":"Проверить","kind":"METHOD","signatures":[
              {"parameters":[{"name":"direction","type":"Направление"},{"name":"target","type":"ИнформацияОСценарии"}]}]}]}]
            """, """
            [{"name":{"Ru":"Направление","En":"Direction"},"enumeration":true},
             {"name":{"Ru":"Объект","En":"Object"},"methods":[
              {"name":{"Ru":"Проверить","En":"Check"},"parameters":[
                {"name":"direction","type":"Произвольный","description":"Порядок обхода.","optional":false},
                {"name":"target","type":"Произвольный","description":"Проверяемый объект.","optional":false}]}]}]
            """, (types, _) =>
        {
            var parameters = Find(types, "Объект")["members"]![0]!["signatures"]![0]!["parameters"]!;
            Assert.Equal("Направление", Text(parameters[0]!["type"]));
            Assert.Equal("Произвольный", Text(parameters[1]!["type"]));
        });
    }

    [Fact]
    public void StructureConstructorRetainsItsUnionAndVariadicTail()
    {
        Regenerate("""
            [{"kind":"TYPE","name":"Структура","members":[],"constructors":[{"parameters":[
              {"name":"КлючиИлиСтруктура","nameRu":"КлючиИлиСтруктура","nameEn":"param1"},
              {"name":"Параметры","nameRu":"Параметры","nameEn":"args"}]}]}]
            """, """
            [{"name":{"Ru":"Структура","En":"Structure"},"constructors":[{"parameters":[
              {"name":"param1","type":"Произвольный","optional":false},
              {"name":"args","type":"Произвольный","optional":true,"variadic":true,"defaultValue":"Неопределено"}]}]}]
            """, (types, _) =>
        {
            var parameters = types[0]!["constructors"]![0]!["parameters"]!;
            Assert.Equal("КлючиИлиСтруктура", Text(parameters[0]!["nameRu"]));
            Assert.Equal(new[] { "Строка", "ФиксированнаяСтруктура" },
                parameters[0]!["types"]!.AsArray().Select(Text));
            Assert.Equal("Параметры", Text(parameters[1]!["nameRu"]));
            Assert.True(parameters[1]!["variadic"]!.GetValue<bool>());
            Assert.True(parameters[1]!["optional"]!.GetValue<bool>());
            Assert.Equal("Неопределено", Text(parameters[1]!["defaultValue"]));
        });
    }

    [Fact]
    public void CompilerIntrinsicsKeepBilingualNamesAndNormalizeLegacyUnionTails()
    {
        Regenerate("[]", """
            [{"name":{"Ru":"$compiler","En":""},"global":true,"intrinsicNames":["Функция"]}]
            """, (_, globals) =>
        {
            var function = globals["functions"]![0]!;
            Assert.Equal("Function", Text(function["nameEn"]));
            Assert.Equal(new[] { "Строка", "Число" }, function["returnTypes"]!.AsArray().Select(Text));
            var parameters = function["signatures"]![0]!["parameters"]!.AsArray();
            var parameter = Assert.Single(parameters)!;
            Assert.Equal("Значения", Text(parameter["nameRu"]));
            Assert.Equal("Value1", Text(parameter["nameEn"]));
            Assert.Equal(new[] { "Строка", "Число" }, parameter["types"]!.AsArray().Select(Text));
            Assert.True(parameter["variadic"]!.GetValue<bool>());
            Assert.True(parameter["optional"]!.GetValue<bool>());
        }, """
            {"functions":[{"name":"Функция","nameRu":"Функция","nameEn":"Function","returnType":"Строка|Число",
              "signatures":[{"returnType":"Строка|Число","parameters":[
                {"name":"Значение1","nameRu":"Значение1","nameEn":"Value1","type":"Строка|Число"},
                {"name":"..."}]}]}],"variables":[],"classes":[],"keywords":[]}
            """);
    }

    [Fact]
    public void CollectionTraitsKeepTheirTypesAndDocumentation()
    {
        Regenerate("""
            [{"kind":"TYPE","name":"Коллекция","members":[],"supportsForEach":true,"supportsIndexAccess":true,
              "elementTypes":["КлючИЗначение"],"forEachDescription":"Пары ключей и значений",
              "indexAccessDescription":"Доступ по ключу","typeParameters":["T"]}]
            """, """[{"name":{"Ru":"Коллекция","En":"Collection"}}]""", (types, _) =>
        {
            var type = types[0]!;
            Assert.True(type["supportsForEach"]!.GetValue<bool>());
            Assert.True(type["supportsIndexAccess"]!.GetValue<bool>());
            Assert.Equal("КлючИЗначение", Text(type["elementTypes"]![0]));
            Assert.Equal("Пары ключей и значений", Text(type["forEachDescription"]));
            Assert.Equal("Доступ по ключу", Text(type["indexAccessDescription"]));
            Assert.Equal("T", Text(type["typeParameters"]![0]));
        });
    }

    [Fact]
    public void ExistingRussianParameterLabelsFollowTheMatchingRuntimeName()
    {
        Regenerate("""
            [{"kind":"TYPE","name":"Объект","members":[{"name":"Открыть","kind":"METHOD","signatures":[
              {"parameters":[{"name":"Путь","nameRu":"Путь","nameEn":"path","type":"Строка"}]}]}]}]
            """, """
            [{"name":{"Ru":"Объект","En":"Object"},"methods":[
              {"name":{"Ru":"Открыть","En":"Open"},"parameters":[{"name":"path","type":"Строка"}]}]}]
            """, (types, _) =>
        {
            var parameter = types[0]!["members"]![0]!["signatures"]![0]!["parameters"]![0]!;
            Assert.Equal("Путь", Text(parameter["nameRu"]));
            Assert.Equal("path", Text(parameter["nameEn"]));
        });
    }

    [Fact]
    public void RenamedRuntimeParametersDiscardThePreviousLabelAndDescription()
    {
        Regenerate("""
            [{"kind":"TYPE","name":"Объект","members":[{"name":"Открыть","kind":"METHOD","signatures":[
              {"parameters":[{"name":"Путь","nameRu":"Путь","nameEn":"path","type":"Строка","description":"Старый путь"}]}]}]}]
            """, """
            [{"name":{"Ru":"Объект","En":"Object"},"methods":[
              {"name":{"Ru":"Открыть","En":"Open"},"parameters":[{"name":"pattern","type":"Строка"}]}]}]
            """, (types, _) =>
        {
            var parameter = types[0]!["members"]![0]!["signatures"]![0]!["parameters"]![0]!;
            Assert.Equal("pattern", Text(parameter["name"]));
            Assert.Equal("", Text(parameter["nameRu"]));
            Assert.Equal("pattern", Text(parameter["nameEn"]));
            Assert.Equal("", Text(parameter["description"]));
        });
    }

    [Fact]
    public void NewParametersUseTheirDeclaredNameWithoutAGlobalTranslationTable()
    {
        Regenerate("[]", """
            [{"name":{"Ru":"Объект","En":"Object"},"methods":[
              {"name":{"Ru":"Открыть","En":"Open"},"parameters":[{"name":"path","type":"Строка"}]}]}]
            """, (types, _) =>
        {
            var parameter = types[0]!["members"]![0]!["signatures"]![0]!["parameters"]![0]!;
            Assert.Equal("path", Text(parameter["name"]));
            Assert.Equal("", Text(parameter["nameRu"]));
        });
    }

    private static void Regenerate(string types, string runtime, Action<JsonArray, JsonObject> verify,
        string globals = """{"functions":[],"variables":[],"classes":[],"keywords":[]}""")
    {
        var directory = Directory.CreateTempSubdirectory("bsl-oscript-metadata-").FullName;
        var typesFile = Path.Combine(directory, "builtin-oscript-platform-types.json");
        var globalsFile = Path.Combine(directory, "builtin-oscript-globals.json");
        var input = Path.Combine(directory, "runtime.json");
        var basic = Path.Combine(directory, "basic.json");
        try
        {
            File.WriteAllText(typesFile, types);
            File.WriteAllText(globalsFile, globals);
            var contexts = JsonNode.Parse(runtime)!.AsArray();
            if (!contexts.OfType<JsonObject>().Any(context => Text(context["name"]?["Ru"]) == "$compiler"))
                contexts.Add(JsonNode.Parse("""{"name":{"Ru":"$compiler","En":""},"global":true,"intrinsicNames":[]}"""));
            File.WriteAllText(input, contexts.ToJsonString());
            File.WriteAllText(basic, "{}");

            MetadataUpdater.Update(input, directory, basic);

            verify(JsonNode.Parse(File.ReadAllText(typesFile))!.AsArray(),
                JsonNode.Parse(File.ReadAllText(globalsFile))!.AsObject());
            var firstTypes = File.ReadAllBytes(typesFile);
            var firstGlobals = File.ReadAllBytes(globalsFile);
            Assert.DoesNotContain("\r", File.ReadAllText(typesFile));
            Assert.DoesNotContain("\r", File.ReadAllText(globalsFile));
            MetadataUpdater.Update(input, directory, basic);
            Assert.Equal(firstTypes, File.ReadAllBytes(typesFile));
            Assert.Equal(firstGlobals, File.ReadAllBytes(globalsFile));
        }
        finally
        {
            Assert.Equal(Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar),
                Path.GetDirectoryName(Path.GetFullPath(directory)));
            Assert.StartsWith("bsl-oscript-metadata-", Path.GetFileName(directory));
            Directory.Delete(directory, true);
        }
    }

    private static JsonObject Find(JsonArray values, string name) =>
        values.OfType<JsonObject>().Single(value => Text(value["name"]) == name);

    private static string Text(JsonNode? value) => value?.GetValue<string>() ?? "";
}
