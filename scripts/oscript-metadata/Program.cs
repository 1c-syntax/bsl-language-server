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
using System.Globalization;
using System.Reflection;
using System.Runtime.Loader;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Xml.Linq;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

if (args.Length is < 2 or > 3)
    throw new ArgumentException("Usage: MetadataExporter <OneScript bin directory> <output.json> [source directory]");

var documentation = args.Length == 3 ? Documentation(args[2]) : new Dictionary<string, XElement>();
var sourceEnumValues = args.Length == 3 ? SourceEnumValues(args[2]) : new Dictionary<string, Name[]>();
Console.Error.WriteLine($"Loaded {documentation.Count} source documentation entries");

var directory = Path.GetFullPath(args[0]);
AssemblyLoadContext.Default.Resolving += (_, name) =>
{
    var file = Path.Combine(directory, name.Name + ".dll");
    return File.Exists(file) ? AssemblyLoadContext.Default.LoadFromAssemblyPath(file) : null;
};

var assemblies = Directory.GetFiles(directory, "*.dll")
    .Where(file => Path.GetFileName(file).StartsWith("OneScript.", StringComparison.Ordinal)
        || Path.GetFileName(file).StartsWith("ScriptEngine", StringComparison.Ordinal)
        || Path.GetFileName(file).Equals("oscript.dll", StringComparison.OrdinalIgnoreCase))
    .Where(IsManaged)
    .OrderBy(file => file, StringComparer.Ordinal).Select(AssemblyLoadContext.Default.LoadFromAssemblyPath).ToArray();
var types = assemblies.SelectMany(assembly => assembly.GetTypes()).ToArray();
var names = new Dictionary<Type, Name>();
foreach (var type in types)
{
    var attribute = Attribute(type, "ContextClassAttribute", "SystemEnumAttribute", "EnumerationTypeAttribute");
    if (attribute != null) names[type] = AttributeName(attribute, type.Name);
}

var output = new List<object>();
foreach (var type in types.OrderBy(type => type.FullName, StringComparer.Ordinal))
{
    var global = Attribute(type, "GlobalContextAttribute") != null;
    var enumeration = Attribute(type, "SystemEnumAttribute", "EnumerationTypeAttribute") != null;
    if (!global && !names.ContainsKey(type)) continue;
    var methods = new List<object>();
    var constructors = new List<object>();
    foreach (var method in type.GetMethods(BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static))
    {
        var constructor = Attribute(method, "ScriptConstructorAttribute");
        var attribute = Attribute(method, "ContextMethodAttribute");
        if (attribute == null && constructor == null) continue;
        var doc = documentation.GetValueOrDefault(DocumentationKey(method.DeclaringType!.FullName!, method.Name,
            method.GetParameters().Select(parameter => parameter.Name ?? "")));
        var parameters = method.GetParameters()
            .Where(parameter => parameter.ParameterType.Name is not "IBslProcess" and not "TypeActivationContext")
            .Select(parameter => new
            {
                name = parameter.Name,
                clrType = parameter.ParameterType.Name,
                description = Text(doc?.Elements("param")
                    .FirstOrDefault(element => (string?)element.Attribute("name") == parameter.Name)),
                type = TypeName(parameter.ParameterType.IsByRef || IsVariadic(parameter, constructor != null)
                    ? parameter.ParameterType.GetElementType()! : parameter.ParameterType),
                optional = parameter.IsOptional || IsVariadic(parameter, constructor != null),
                variadic = IsVariadic(parameter, constructor != null),
                defaultValue = parameter.HasDefaultValue ? DefaultValue(parameter.DefaultValue) : null
            }).ToArray();
        var entry = new
        {
            name = AttributeName(attribute ?? constructor!, method.Name),
            clrName = method.Name,
            deprecatedNames = method.GetCustomAttributesData()
                .Where(item => item.AttributeType.Name == "DeprecatedNameAttribute")
                .SelectMany(item => item.ConstructorArguments
                    .Where(argument => argument.ArgumentType == typeof(string))
                    .Select(argument => (string)argument.Value!)).ToArray(),
            description = Text(doc?.Element("summary")),
            returnDescription = Text(doc?.Element("returns")),
            documentation = documentation.GetValueOrDefault(DocumentationKey(method.DeclaringType!.FullName!, method.Name,
                method.GetParameters().Select(parameter => parameter.Name ?? "")))?.ToString(),
            returnType = method.ReturnType == typeof(void) ? null : TypeName(method.ReturnType),
            parameters
        };
        (constructor != null ? constructors : methods).Add(entry);
    }
    var properties = new List<object>();
    foreach (var property in type.GetProperties(BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static))
    {
        var attribute = Attribute(property, "ContextPropertyAttribute", "EnumValueAttribute");
        if (attribute == null) continue;
        properties.Add(new
        {
            name = AttributeName(attribute, property.Name),
            clrName = property.Name,
            description = Text(documentation.GetValueOrDefault(property.DeclaringType!.FullName + "." + property.Name)
                ?.Element("summary")),
            documentation = documentation.GetValueOrDefault(property.DeclaringType!.FullName + "." + property.Name)?.ToString(),
            type = TypeName(property.PropertyType),
            canRead = property.GetMethod?.IsPublic == true && AttributeBoolean(attribute, "CanRead", true),
            canWrite = property.SetMethod?.IsPublic == true && AttributeBoolean(attribute, "CanWrite", true)
        });
    }
    var values = type.GetFields(BindingFlags.Public | BindingFlags.Static)
        .Select(field => new { field, attribute = Attribute(field, "EnumValueAttribute") })
        .Where(item => item.attribute != null)
        .Select(item => AttributeName(item.attribute!, item.field.Name)).ToArray();
    if (enumeration && values.Length == 0 && sourceEnumValues.TryGetValue(type.FullName!, out var declaredValues))
        values = declaredValues;
    output.Add(new
    {
        name = names.GetValueOrDefault(type, new Name(type.Name, "")),
        clrName = type.FullName,
        description = Text(documentation.GetValueOrDefault(type.FullName!)?.Element("summary")),
        documentation = documentation.GetValueOrDefault(type.FullName!)?.ToString(),
        assembly = type.Assembly.GetName().Name,
        global,
        enumeration,
        methods,
        properties,
        constructors,
        values
    });
}

if (args.Length == 3)
{
    var language = Path.Combine(args[2], "src", "OneScript.Language", "LanguageDef.cs");
    var intrinsicNames = CSharpSyntaxTree.ParseText(File.ReadAllText(language)).GetRoot()
        .DescendantNodes().OfType<InvocationExpressionSyntax>()
        .Where(call => call.Expression is IdentifierNameSyntax name && name.Identifier.ValueText == "AddToken")
        .SelectMany(call => call.ArgumentList.Arguments.Skip(1))
        .Where(argument => argument.Expression is LiteralExpressionSyntax literal && literal.Token.Value is string)
        .Select(argument => ((LiteralExpressionSyntax)argument.Expression).Token.ValueText).Distinct().ToArray();
    output.Add(new { name = new Name("$compiler", ""), global = true, enumeration = false,
        methods = Array.Empty<object>(), properties = Array.Empty<object>(), values = Array.Empty<Name>(), intrinsicNames });
}

File.WriteAllText(args[1], JsonSerializer.Serialize(output, new JsonSerializerOptions
{
    WriteIndented = true,
    Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
}) + "\n");

CustomAttributeData? Attribute(MemberInfo member, params string[] candidates) =>
    member.GetCustomAttributesData().FirstOrDefault(item => candidates.Contains(item.AttributeType.Name));

Name AttributeName(CustomAttributeData attribute, string fallback)
{
    var strings = attribute.ConstructorArguments.Where(argument => argument.ArgumentType == typeof(string))
        .Select(argument => argument.Value as string ?? "").ToArray();
    var named = attribute.NamedArguments.FirstOrDefault(argument => argument.MemberName == "Name").TypedValue.Value as string;
    return new Name(strings.FirstOrDefault() ?? named ?? fallback, strings.Skip(1).FirstOrDefault() ?? "");
}

static bool AttributeBoolean(CustomAttributeData attribute, string name, bool fallback) =>
    attribute.NamedArguments.FirstOrDefault(argument => argument.MemberName == name).TypedValue.Value
        is bool value ? value : fallback;

static bool IsVariadic(ParameterInfo parameter, bool constructor) =>
    constructor && parameter.ParameterType.IsArray
    || parameter.GetCustomAttributesData().Any(item => item.AttributeType == typeof(ParamArrayAttribute));

string TypeName(Type type)
{
    type = Nullable.GetUnderlyingType(type) ?? type;
    if (names.TryGetValue(type, out var name)) return name.Ru;
    if (type == typeof(string)) return "Строка";
    if (type == typeof(bool)) return "Булево";
    if (type == typeof(DateTime)) return "Дата";
    if (type.IsPrimitive || type == typeof(decimal)) return "Число";
    if (type.IsArray) return "Массив";
    if (type.Name is "TypeDescriptor" or "BslTypeValue") return "Тип";
    return "Произвольный";
}

static string? DefaultValue(object? value) => value switch
{
    null => "Неопределено",
    string text => "\"" + text.Replace("\"", "\"\"") + "\"",
    bool flag => flag ? "Истина" : "Ложь",
    DBNull or Missing => null,
    IFormattable formatted => formatted.ToString(null, CultureInfo.InvariantCulture),
    _ => value.ToString()
};

static bool IsManaged(string file)
{
    try
    {
        AssemblyName.GetAssemblyName(file);
        return true;
    }
    catch (BadImageFormatException)
    {
        return false;
    }
}

static string DocumentationKey(string type, string name, IEnumerable<string> parameters) =>
    type + "." + name + "(" + string.Join(",", parameters) + ")";

// Legacy EnumerationContext values are registered in constructors rather than attributes.
// Read literal WrapClrValue names from matching source without executing those constructors.
static Dictionary<string, Name[]> SourceEnumValues(string directory)
{
    var result = new Dictionary<string, Name[]>();
    foreach (var file in Directory.EnumerateFiles(directory, "*.cs", SearchOption.AllDirectories))
    {
        var root = CSharpSyntaxTree.ParseText(File.ReadAllText(file)).GetRoot();
        foreach (var declaration in root.DescendantNodes().OfType<ClassDeclarationSyntax>())
        {
            var values = declaration.DescendantNodes().OfType<InvocationExpressionSyntax>()
                .Where(call => call.Expression is MemberAccessExpressionSyntax member
                        && member.Name.Identifier.ValueText == "WrapClrValue"
                    || call.Expression is IdentifierNameSyntax name && name.Identifier.ValueText == "MakeValue")
                .Select(call => call.ArgumentList.Arguments)
                .Where(arguments => arguments.Count >= 2
                    && arguments[0].Expression is LiteralExpressionSyntax literal && literal.Token.Value is string)
                .Select(arguments => new Name(((LiteralExpressionSyntax)arguments[0].Expression).Token.ValueText,
                    arguments[1].Expression is LiteralExpressionSyntax alias && alias.Token.Value is string
                        ? alias.Token.ValueText : ""))
                .Distinct().ToArray();
            if (values.Length == 0) continue;
            var namespaces = declaration.Ancestors().OfType<BaseNamespaceDeclarationSyntax>().Reverse()
                .Select(value => value.Name.ToString());
            var containers = declaration.Ancestors().OfType<BaseTypeDeclarationSyntax>().Reverse()
                .Select(value => value.Identifier.ValueText).Append(declaration.Identifier.ValueText);
            result[string.Join(".", namespaces) + "." + string.Join("+", containers)] = values;
        }
    }
    return result;
}

static Dictionary<string, XElement> Documentation(string directory)
{
    var result = new Dictionary<string, XElement>();
    foreach (var file in Directory.EnumerateFiles(directory, "*.cs", SearchOption.AllDirectories))
    {
        var root = CSharpSyntaxTree.ParseText(File.ReadAllText(file)).GetRoot();
        foreach (var node in root.DescendantNodes().OfType<MemberDeclarationSyntax>())
        {
            var doc = node.GetLeadingTrivia().Select(trivia => trivia.GetStructure())
                .OfType<DocumentationCommentTriviaSyntax>().FirstOrDefault();
            if (doc == null) continue;
            var namespaces = node.Ancestors().OfType<BaseNamespaceDeclarationSyntax>().Reverse()
                .Select(value => value.Name.ToString());
            var containers = node.Ancestors().OfType<BaseTypeDeclarationSyntax>().Reverse()
                .Select(value => value.Identifier.ValueText);
            var type = string.Join(".", namespaces) + "." + string.Join("+", containers);
            var key = node switch
            {
                BaseTypeDeclarationSyntax declaration => type
                    + (containers.Any() ? "+" : "") + declaration.Identifier.ValueText,
                MethodDeclarationSyntax method => DocumentationKey(type, method.Identifier.ValueText,
                    method.ParameterList.Parameters.Select(parameter => parameter.Identifier.ValueText)),
                PropertyDeclarationSyntax property => type + "." + property.Identifier.ValueText,
                _ => null
            };
            if (key == null) continue;
            var text = string.Join("\n", doc.ToFullString().Split('\n')
                .Select(line => line.TrimStart().StartsWith("///") ? line.TrimStart()[3..] : line));
            try { result[key] = XElement.Parse("<doc>" + text + "</doc>"); }
            catch (System.Xml.XmlException) { /* Incomplete XML is left undocumented. */ }
        }
    }
    return result;
}

static string Text(XElement? element) => element == null ? "" : Regex.Replace(element.Value, @"\s+", " ").Trim();

record Name(string Ru, string En);
