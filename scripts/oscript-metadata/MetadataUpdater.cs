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
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

/// <summary>Merges reflected OneScript metadata with the annotations in the server resources.</summary>
internal sealed class MetadataUpdater
{
    private readonly JsonArray runtime;
    private readonly JsonArray previousTypes;
    private readonly JsonObject previousGlobals;
    private readonly HashSet<string> intrinsicNames = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, string> documentedEnums = new(StringComparer.OrdinalIgnoreCase);
    private readonly HashSet<string> missingRussianNames = new(StringComparer.Ordinal);
    private int parameterCount;
    private int russianCount;
    private int variadicCount;

    private MetadataUpdater(JsonArray runtime, JsonArray previousTypes, JsonObject previousGlobals,
        JsonObject basicMethods)
    {
        this.runtime = runtime;
        this.previousTypes = previousTypes;
        this.previousGlobals = previousGlobals;

        foreach (var group in basicMethods.Select(entry => entry.Value).OfType<JsonObject>())
            foreach (var method in Object(group["methods"]))
                intrinsicNames.Add(method.Key);

        foreach (var context in runtime.OfType<JsonObject>())
            foreach (var name in Array(context["intrinsicNames"]))
                intrinsicNames.Add(Text(name));

        foreach (var type in previousTypes.OfType<JsonObject>().Where(type => Text(type["kind"]) == "ENUM"))
            foreach (var name in Names(type))
                documentedEnums[name] = Text(type["name"]);

        foreach (var context in runtime.OfType<JsonObject>().Where(context => Flag(context["enumeration"])))
            foreach (var name in Object(context["name"]).Select(entry => Text(entry.Value)).Where(name => name.Length > 0))
                documentedEnums[name] = Text(context["name"]?["Ru"]);
    }

    /// <summary>Updates both checked-in resources using a runtime export and the matching compiler metadata.</summary>
    internal static void Update(string input, string resourceDirectory, string basicMethodsFile)
    {
        var runtime = JsonNode.Parse(File.ReadAllText(input))!.AsArray();
        if (!runtime.OfType<JsonObject>().Any(context => Text(context["name"]?["Ru"]) == "$compiler"
            && context["intrinsicNames"] is JsonArray))
            throw new InvalidOperationException("Runtime export lacks compiler metadata; rerun export with the matching OneScript source directory.");
        var typesFile = Path.Combine(resourceDirectory, "builtin-oscript-platform-types.json");
        var globalsFile = Path.Combine(resourceDirectory, "builtin-oscript-globals.json");
        var updater = new MetadataUpdater(runtime,
            JsonNode.Parse(File.ReadAllText(typesFile))!.AsArray(),
            JsonNode.Parse(File.ReadAllText(globalsFile))!.AsObject(),
            JsonNode.Parse(File.ReadAllText(basicMethodsFile))!.AsObject());
        var (types, globals) = updater.Build();
        var options = new JsonSerializerOptions
        {
            WriteIndented = true,
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
        };
        File.WriteAllText(typesFile, types.ToJsonString(options).Replace("\r\n", "\n") + "\n");
        File.WriteAllText(globalsFile, globals.ToJsonString(options).Replace("\r\n", "\n") + "\n");
        Console.WriteLine(JsonSerializer.Serialize(new
        {
            types = types.Count,
            functions = Array(globals["functions"]).Count,
            variables = Array(globals["variables"]).Count,
            updater.parameterCount,
            updater.russianCount,
            updater.variadicCount,
            missingRussianAliases = updater.missingRussianNames.Order(StringComparer.Ordinal).ToArray()
        }, options));
    }

    private (JsonArray Types, JsonObject Globals) Build()
    {
        var types = runtime.OfType<JsonObject>().Where(context => !Flag(context["global"]))
            .Select(BuildType).ToList();
        foreach (var previous in previousTypes.OfType<JsonObject>()
            .Where(type => Text(type["kind"]) == "PRIMITIVE_TYPE"))
        {
            if (Find(types, Text(previous["name"])) == null)
                types.Add(previous.DeepClone().AsObject());
        }

        var functions = new Dictionary<string, JsonObject>(StringComparer.OrdinalIgnoreCase);
        var variables = new Dictionary<string, JsonObject>(StringComparer.OrdinalIgnoreCase);
        var previousMembers = Array(previousGlobals["functions"]).Concat(Array(previousGlobals["variables"]))
            .OfType<JsonObject>().ToArray();
        foreach (var context in runtime.OfType<JsonObject>().Where(context => Flag(context["global"])))
        {
            foreach (var member in Members(context, previousMembers))
            {
                var name = Text(member["name"]);
                var english = Text(member["nameEn"]);
                member["aliases"] = Strings(english.Length > 0 ? new[] { english } : System.Array.Empty<string>());
                if (Text(member["kind"]) == "METHOD")
                    functions[name] = member;
                else
                {
                    if (member["returnType"] != null)
                        member["type"] = member["returnType"]!.DeepClone();
                    variables[name] = member;
                }
            }
        }

        foreach (var previous in Array(previousGlobals["functions"]).OfType<JsonObject>())
        {
            var name = Text(previous["name"]);
            if (Find(functions.Values, name) == null && intrinsicNames.Contains(name))
                functions[name] = Intrinsic(previous);
        }

        foreach (var context in runtime.OfType<JsonObject>().Where(context => Flag(context["enumeration"])))
        {
            var value = Named(Object(context["name"]));
            var english = Text(value["nameEn"]);
            value["aliases"] = Strings(english.Length > 0 ? new[] { english } : System.Array.Empty<string>());
            value["type"] = Text(context["name"]?["Ru"]);
            value["description"] = Text(context["description"]);
            variables[Text(value["name"])] = value;
        }

        return (Sorted(types), new JsonObject
        {
            ["functions"] = Sorted(functions.Values),
            ["variables"] = Sorted(variables.Values),
            ["classes"] = Strings(types.Where(type => Text(type["kind"]) != "ENUM")
                .Select(type => Text(type["name"])).Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal)),
            ["keywords"] = previousGlobals["keywords"]?.DeepClone() ?? new JsonArray()
        });
    }

    private JsonObject BuildType(JsonObject context)
    {
        var name = Text(context["name"]?["Ru"]);
        var previous = Find(previousTypes.OfType<JsonObject>(), name) ?? new JsonObject();
        var description = First(Text(context["description"]), Text(previous["description"]));
        const string consoleNote = "Используйте глобальный объект Консоль: конструктор этого типа не предоставляется.";
        if (name == "Консоль" && !description.Contains(consoleNote, StringComparison.Ordinal))
            description = (description + " " + consoleNote).Trim();

        var result = new JsonObject { ["kind"] = Flag(context["enumeration"]) ? "ENUM" : "TYPE" };
        Copy(result, Named(Object(context["name"])));
        result["description"] = description;
        foreach (var flag in new[] { "supportsForEach", "supportsIndexAccess" })
            if (Flag(previous[flag])) result[flag] = true;
        foreach (var field in new[] { "elementTypes", "forEachDescription", "indexAccessDescription", "typeParameters" })
            if (previous[field] != null) result[field] = previous[field]!.DeepClone();

        var constructors = Array(context["constructors"]).OfType<JsonObject>()
            .Select(method => Signature(method, new JsonObject
            {
                ["signatures"] = previous["constructors"]?.DeepClone() ?? new JsonArray()
            }, name)).ToArray();
        if (constructors.Length > 0)
            result["constructors"] = Objects(constructors);
        result["members"] = Objects(Members(context, Array(previous["members"]).OfType<JsonObject>()));
        return result;
    }

    private IEnumerable<JsonObject> Members(JsonObject context, IEnumerable<JsonObject> previousMembers)
    {
        var previous = previousMembers.ToArray();
        var result = new List<JsonObject>();
        foreach (var property in Array(context["properties"]).OfType<JsonObject>())
        {
            var old = Find(previous, Text(property["name"]?["Ru"])) ?? new JsonObject();
            var member = Named(Object(property["name"]));
            member["kind"] = "PROPERTY";
            AddType(member, RefinedReturnType(property["type"], old), "returnType");
            member["description"] = First(Text(property["description"]), Text(old["description"]));
            member["accessMode"] = Flag(property["canWrite"])
                ? Flag(property["canRead"]) ? "READ_WRITE" : "WRITE" : "READ";
            result.Add(member);
        }

        var methods = new Dictionary<string, JsonObject>(StringComparer.OrdinalIgnoreCase);
        var deprecated = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var method in Array(context["methods"]).OfType<JsonObject>())
        {
            var name = Text(method["name"]?["Ru"]);
            var old = Find(previous, name) ?? new JsonObject();
            if (!methods.TryGetValue(name, out var member))
            {
                member = Named(Object(method["name"]));
                member["kind"] = "METHOD";
                member["description"] = MethodDescription(method, old);
                foreach (var field in new[] { "deprecatedSinceVersion", "recommendedReplacements" })
                    if (old[field] != null) member[field] = old[field]!.DeepClone();
                if (Flag(old["async"])) member["async"] = true;
                AddType(member, RefinedReturnType(method["returnType"], old), "returnType");
                member["signatures"] = new JsonArray();
                methods[name] = member;
            }
            member["signatures"]!.AsArray().Add(Signature(method, old));
            foreach (var alias in Array(method["deprecatedNames"]))
                deprecated[Text(alias)] = name;
        }
        result.AddRange(methods.Values);

        foreach (var (alias, primaryName) in deprecated)
        {
            if (Find(result, alias) != null) continue;
            var primary = methods[primaryName];
            var old = Find(previous, alias) ?? new JsonObject();
            var member = primary.DeepClone().AsObject();
            member["name"] = alias;
            member["nameRu"] = "";
            member["nameEn"] = alias;
            member["description"] = First(Text(old["description"]), Text(primary["description"]));
            member["deprecatedSinceVersion"] = "*";
            member["recommendedReplacements"] = old["recommendedReplacements"]?.DeepClone() ?? Strings(new[] { primaryName });
            result.Add(member);
        }

        foreach (var value in Array(context["values"]).OfType<JsonObject>())
        {
            var old = Find(previous, Text(value["Ru"]));
            var english = previous.FirstOrDefault(member => !Cyrillic(First(Text(member["nameRu"]), Text(member["name"])))
                && Matches(member, Text(value["En"])));
            var member = Named(value);
            member["kind"] = "PROPERTY";
            member["returnType"] = Text(context["name"]?["Ru"]);
            var description = First(Text(old?["description"]), Text(english?["description"]));
            if (description.Length > 0) member["description"] = description;
            result.Add(member);
        }
        return result.OrderBy(member => Text(member["name"]), StringComparer.Ordinal);
    }

    private JsonObject Signature(JsonObject method, JsonObject previous, string? constructorType = null)
    {
        var parameters = Array(method["parameters"]).OfType<JsonObject>().ToArray();
        var candidates = Array(previous["signatures"]).OfType<JsonObject>()
            .Where(signature => Array(signature["parameters"]).Count == parameters.Length).ToArray();
        var matching = candidates.Where(signature => Array(signature["parameters"]).OfType<JsonObject>()
            .Select((parameter, index) => SameParameter(parameter, parameters[index])).All(value => value)).ToArray();
        var old = matching.Length == 1 ? matching[0] : candidates.Length == 1 ? candidates[0] : null;
        var oldParameters = Array(old?["parameters"]).OfType<JsonObject>().ToArray();
        var result = new JsonObject
        {
            ["parameters"] = Objects(parameters.Select((parameter, index) => Parameter(parameter,
                oldParameters.FirstOrDefault(value => SameParameter(value, parameter)) ?? oldParameters.ElementAtOrDefault(index),
                constructorType)))
        };
        AddType(result, constructorType ?? RefinedReturnType(method["returnType"], previous), "returnType");
        var description = First(Text(method["description"]), Text(old?["description"]));
        if (description.Length > 0) result["description"] = description;
        return result;
    }

    /// <summary>Retains a curated label only when the reflected parameter still has the same name.</summary>
    private JsonObject Parameter(JsonObject parameter, JsonObject? previous, string? constructorType)
    {
        previous ??= new JsonObject();
        if (!SameParameter(previous, parameter)) previous = new JsonObject();
        parameterCount++;
        var name = Text(parameter["name"]);
        var russian = Text(parameter["nameRu"]);
        var type = Text(parameter["type"]);
        if (name == "param1" && constructorType is "Структура" or "ФиксированнаяСтруктура")
        {
            russian = "КлючиИлиСтруктура";
            type = "Строка|" + (constructorType == "Структура" ? "ФиксированнаяСтруктура" : "Структура");
        }
        if (russian.Length == 0 && Cyrillic(name)) russian = name;
        if (russian.Length == 0 && SameParameter(previous, parameter)) russian = Text(previous["nameRu"]);
        if (russian.Length > 0) russianCount++; else missingRussianNames.Add(name);
        if (Flag(parameter["variadic"])) variadicCount++;

        var result = new JsonObject { ["name"] = First(russian, name), ["nameRu"] = russian, ["nameEn"] = name };
        AddType(result, ParameterType(parameter, previous, type));
        result["optional"] = Flag(parameter["optional"]);
        result["description"] = First(Text(parameter["description"]), Text(previous["description"]));
        if (parameter["defaultValue"] != null) result["defaultValue"] = parameter["defaultValue"]!.DeepClone();
        if (Flag(parameter["variadic"])) result["variadic"] = true;
        return result;
    }

    private string ParameterType(JsonObject parameter, JsonObject previous, string type)
    {
        if (type != "Произвольный" || Flag(parameter["variadic"])) return type;
        var description = Regex.Replace(Text(parameter["description"]), @"^Тип:\s*", "", RegexOptions.IgnoreCase);
        var first = Regex.Match(description, @"^[^\s.,;(]+").Value;
        if (documentedEnums.TryGetValue(first, out var documented)) return documented;
        return SameParameter(previous, parameter) && documentedEnums.TryGetValue(Text(previous["type"]), out documented)
            ? documented : type;
    }

    private static string MethodDescription(JsonObject method, JsonObject previous)
    {
        var description = First(Text(method["description"]), Text(previous["description"]));
        var returns = Text(method["returnDescription"]);
        // OneScript 2.2.0 uses BinaryData in these two BinaryDataBuffer return descriptions.
        if (Text(method["name"]?["En"]) is "GetBinaryDataBufferFromBase64BinaryDataBuffer" or "GetBinaryDataBufferFromBase64String"
            && Text(method["returnType"]) == "БуферДвоичныхДанных")
            returns = Regex.Replace(returns, @"^(Тип:\s*)?ДвоичныеДанные(?=[\s.,;:]|$)", "$1БуферДвоичныхДанных");
        var normalized = Regex.Replace(description, @"\s+", " ").Trim();
        return returns.Length > 0 && !normalized.Contains(Regex.Replace(returns, @"\s+", " ").Trim(), StringComparison.Ordinal)
            ? (description + "\n\nВозвращаемое значение: " + returns).Trim() : description;
    }

    private static JsonObject Intrinsic(JsonObject previous)
    {
        var result = previous.DeepClone().AsObject();
        result["nameRu"] = First(Text(previous["nameRu"]), Text(previous["name"]));
        result["nameEn"] = First(Text(previous["nameEn"]), Text(Array(previous["aliases"]).FirstOrDefault()));
        NormalizeUnion(result, "returnType");
        var signatures = new JsonArray();
        foreach (var signature in Array(previous["signatures"]).OfType<JsonObject>())
        {
            var updated = signature.DeepClone().AsObject();
            var hasTail = Array(signature["parameters"]).Any(parameter => Text(parameter?["name"]) == "...");
            var parameters = Array(signature["parameters"]).OfType<JsonObject>()
                .Where(parameter => Text(parameter["name"]) != "...").ToArray();
            var values = new JsonArray();
            for (var index = 0; index < parameters.Length; index++)
            {
                var parameter = parameters[index].DeepClone().AsObject();
                var variadic = hasTail && index == parameters.Length - 1;
                var russian = variadic ? "Значения" : First(Text(parameter["nameRu"]), Text(parameter["name"]));
                var english = First(Text(parameter["nameEn"]), variadic ? "Values" : russian);
                parameter["name"] = russian;
                parameter["nameRu"] = russian;
                parameter["nameEn"] = english;
                NormalizeUnion(parameter, "type");
                if (variadic)
                {
                    parameter["variadic"] = true;
                    parameter["optional"] = true;
                }
                values.Add(parameter);
            }
            updated["parameters"] = values;
            NormalizeUnion(updated, "returnType");
            signatures.Add(updated);
        }
        result["signatures"] = signatures;
        return result;
    }

    private static void NormalizeUnion(JsonObject target, string field)
    {
        var value = Text(target[field]);
        if (!value.Contains('|')) return;
        target.Remove(field);
        AddType(target, value, field);
    }

    private static void AddType(JsonObject target, string value, string field = "type")
    {
        if (value.Length == 0) return;
        if (value.Contains('|')) target[field == "type" ? "types" : "returnTypes"] = Strings(value.Split('|').Select(name => name.Trim()));
        else target[field] = value;
    }

    private static string RefinedReturnType(JsonNode? value, JsonObject previous) =>
        Text(value) == "Произвольный" ? First(Text(previous["returnType"]), Text(value)) : Text(value);

    private static bool SameParameter(JsonObject previous, JsonObject current) =>
        string.Equals(First(Text(previous["nameEn"]), Text(previous["name"])), Text(current["name"]),
            StringComparison.OrdinalIgnoreCase);

    private static JsonObject Named(JsonObject name) => new()
    {
        ["name"] = Text(name["Ru"]), ["nameRu"] = Text(name["Ru"]), ["nameEn"] = Text(name["En"])
    };

    private static IEnumerable<string> Names(JsonObject value) =>
        new[] { Text(value["name"]), Text(value["nameRu"]), Text(value["nameEn"]) }
            .Concat(Array(value["aliases"]).Select(Text)).Where(name => name.Length > 0);

    private static bool Matches(JsonObject value, string name) =>
        name.Length > 0 && Names(value).Contains(name, StringComparer.OrdinalIgnoreCase);

    private static JsonObject? Find(IEnumerable<JsonObject> values, string name) =>
        values.FirstOrDefault(value => Matches(value, name));

    private static JsonArray Sorted(IEnumerable<JsonObject> values) =>
        Objects(values.OrderBy(value => Text(value["name"]), StringComparer.Ordinal));

    private static void Copy(JsonObject target, JsonObject source)
    {
        foreach (var entry in source) target[entry.Key] = entry.Value?.DeepClone();
    }

    private static JsonArray Objects(IEnumerable<JsonObject> values) => new(values.Cast<JsonNode?>().ToArray());
    private static JsonArray Strings(IEnumerable<string> values) => new(values.Select(value => (JsonNode?)JsonValue.Create(value)).ToArray());
    private static JsonArray Array(JsonNode? value) => value as JsonArray ?? new JsonArray();
    private static JsonObject Object(JsonNode? value) => value as JsonObject ?? new JsonObject();
    private static string Text(JsonNode? value) => value?.GetValue<string>() ?? "";
    private static bool Flag(JsonNode? value) => value?.GetValue<bool>() == true;
    private static bool Cyrillic(string value) => Regex.IsMatch(value, "[А-Яа-яЁё]");
    private static string First(string value, string fallback) => value.Length > 0 ? value : fallback;
}
