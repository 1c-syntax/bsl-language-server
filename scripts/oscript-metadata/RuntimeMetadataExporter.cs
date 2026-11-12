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

/// <summary>Reads the script API declared by the managed assemblies of one OneScript distribution.</summary>
internal sealed class RuntimeMetadataExporter
{
    private readonly SourceDocumentation documentation;
    private readonly Dictionary<Type, MetadataName> names;

    /// <summary>Indexes script-visible type names before resolving member and parameter types.</summary>
    private RuntimeMetadataExporter(IEnumerable<Type> types, SourceDocumentation documentation)
    {
        this.documentation = documentation;
        names = new Dictionary<Type, MetadataName>();
        foreach (var type in types)
        {
            var attribute = Attribute(type, "ContextClassAttribute", "SystemEnumAttribute", "EnumerationTypeAttribute");
            if (attribute != null) names[type] = AttributeName(attribute, type.Name);
        }
    }

    /// <summary>Reads a distribution in an isolated, collectible assembly context.</summary>
    /// <param name="directory">Directory containing the matching OneScript distribution DLLs.</param>
    /// <param name="documentation">XML comments, enum literals and compiler names from the matching source release.</param>
    /// <param name="includeCompiler">Whether to append the compiler-name record required by resource updates.</param>
    /// <returns>Serializable context records that do not retain reflected CLR types.</returns>
    /// <remarks>Requests assembly-context unloading after export or when reflection fails.</remarks>
    internal static IReadOnlyList<object> Export(string directory, SourceDocumentation documentation, bool includeCompiler)
    {
        var context = new DistributionLoadContext(Path.GetFullPath(directory));
        try
        {
            var types = Directory.EnumerateFiles(context.Directory, "*.dll")
                .Where(IsRuntimeAssembly).Where(IsManaged).Order(StringComparer.Ordinal)
                .Select(context.LoadFromAssemblyPath).SelectMany(assembly => assembly.GetTypes()).ToArray();
            var exporter = new RuntimeMetadataExporter(types, documentation);
            var output = types.OrderBy(type => type.FullName, StringComparer.Ordinal)
                .Where(exporter.IsScriptContext).Select(exporter.ExportContext).Cast<object>().ToList();
            if (includeCompiler)
            {
                output.Add(new
                {
                    name = new MetadataName("$compiler", ""), global = true, enumeration = false,
                    methods = Array.Empty<RuntimeMethod>(), properties = Array.Empty<RuntimeProperty>(),
                    values = Array.Empty<MetadataName>(), intrinsicNames = documentation.IntrinsicNames
                });
            }
            return output;
        }
        finally
        {
            context.Unload();
        }
    }

    /// <summary>Includes named script types and unnamed classes explicitly marked as global contexts.</summary>
    private bool IsScriptContext(Type type) =>
        names.ContainsKey(type) || Attribute(type, "GlobalContextAttribute") != null;

    /// <summary>
    /// Exports script members and constructors, using source enum literals when no attributed fields expose the values.
    /// </summary>
    private RuntimeContext ExportContext(Type type)
    {
        var enumeration = Attribute(type, "SystemEnumAttribute", "EnumerationTypeAttribute") != null;
        var methods = new List<RuntimeMethod>();
        var constructors = new List<RuntimeMethod>();
        foreach (var method in type.GetMethods(BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static))
        {
            var constructor = Attribute(method, "ScriptConstructorAttribute");
            var attribute = Attribute(method, "ContextMethodAttribute");
            if (attribute == null && constructor == null) continue;
            (constructor != null ? constructors : methods).Add(ExportMethod(method, attribute ?? constructor!, constructor != null));
        }

        var properties = type.GetProperties(BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static)
            .Select(property => (Property: property, Attribute: Attribute(property, "ContextPropertyAttribute", "EnumValueAttribute")))
            .Where(entry => entry.Attribute != null)
            .Select(entry => ExportProperty(entry.Property, entry.Attribute!)).ToArray();
        var values = type.GetFields(BindingFlags.Public | BindingFlags.Static)
            .Select(field => (Field: field, Attribute: Attribute(field, "EnumValueAttribute")))
            .Where(entry => entry.Attribute != null)
            .Select(entry => AttributeName(entry.Attribute!, entry.Field.Name)).ToArray();
        if (enumeration && values.Length == 0) values = documentation.EnumValues(type.FullName!);
        var doc = documentation.Get(type.FullName!);
        return new RuntimeContext(names.GetValueOrDefault(type, new MetadataName(type.Name, "")), type.FullName,
            SourceDocumentation.Text(doc?.Element("summary")), doc?.ToString(), type.Assembly.GetName().Name,
            Attribute(type, "GlobalContextAttribute") != null, enumeration, methods.ToArray(), properties,
            constructors.ToArray(), values);
    }

    /// <summary>
    /// Combines reflected names and signatures with XML comments, omitting injected process/activation arguments.
    /// </summary>
    private RuntimeMethod ExportMethod(MethodInfo method, CustomAttributeData attribute, bool constructor)
    {
        var parameters = method.GetParameters();
        var doc = documentation.Get(SourceDocumentation.MethodKey(method.DeclaringType!.FullName!, method.Name,
            parameters.Select(parameter => parameter.Name ?? "")));
        var exported = parameters.Where(parameter => parameter.ParameterType.Name is not "IBslProcess" and not "TypeActivationContext")
            .Select(parameter => ExportParameter(parameter, constructor, doc)).ToArray();
        var deprecated = method.GetCustomAttributesData().Where(item => item.AttributeType.Name == "DeprecatedNameAttribute")
            .SelectMany(item => item.ConstructorArguments.Where(argument => argument.ArgumentType == typeof(string))
                .Select(argument => (string)argument.Value!)).ToArray();
        return new RuntimeMethod(AttributeName(attribute, method.Name), method.Name, deprecated,
            SourceDocumentation.Text(doc?.Element("summary")), SourceDocumentation.Text(doc?.Element("returns")),
            doc?.ToString(), method.ReturnType == typeof(void) ? null : TypeName(method.ReturnType), exported);
    }

    /// <summary>
    /// Exports defaults and element types; constructor arrays and params arrays become optional variadic arguments.
    /// </summary>
    private RuntimeParameter ExportParameter(ParameterInfo parameter, bool constructor, System.Xml.Linq.XElement? doc)
    {
        var variadic = constructor && parameter.ParameterType.IsArray
            || parameter.GetCustomAttributesData().Any(attribute => attribute.AttributeType == typeof(ParamArrayAttribute));
        var type = parameter.ParameterType;
        if (type.IsByRef || variadic) type = type.GetElementType()!;
        var description = doc?.Elements("param").FirstOrDefault(element => (string?)element.Attribute("name") == parameter.Name);
        return new RuntimeParameter(parameter.Name, parameter.ParameterType.Name, SourceDocumentation.Text(description),
            TypeName(type), parameter.IsOptional || variadic, variadic,
            parameter.HasDefaultValue ? DefaultValue(parameter.DefaultValue) : null);
    }

    /// <summary>Combines public accessor visibility with explicit script read/write permissions.</summary>
    private RuntimeProperty ExportProperty(PropertyInfo property, CustomAttributeData attribute)
    {
        var doc = documentation.Get(property.DeclaringType!.FullName + "." + property.Name);
        return new RuntimeProperty(AttributeName(attribute, property.Name), property.Name,
            SourceDocumentation.Text(doc?.Element("summary")), doc?.ToString(), TypeName(property.PropertyType),
            property.GetMethod?.IsPublic == true && AttributeBoolean(attribute, "CanRead", true),
            property.SetMethod?.IsPublic == true && AttributeBoolean(attribute, "CanWrite", true));
    }

    /// <summary>Resolves nullable CLR types to script names, falling back to the arbitrary-value type.</summary>
    private string TypeName(Type type)
    {
        type = Nullable.GetUnderlyingType(type) ?? type;
        if (names.TryGetValue(type, out var name)) return name.Ru;
        if (type == typeof(string)) return "Строка";
        if (type == typeof(bool)) return "Булево";
        if (type == typeof(DateTime)) return "Дата";
        if (type.IsPrimitive || type == typeof(decimal)) return "Число";
        if (type.IsArray) return "Массив";
        return type.Name is "TypeDescriptor" or "BslTypeValue" ? "Тип" : "Произвольный";
    }

    /// <summary>Matches attribute names without requiring attribute types from the isolated load context.</summary>
    private static CustomAttributeData? Attribute(MemberInfo member, params string[] candidates) =>
        member.GetCustomAttributesData().FirstOrDefault(attribute => candidates.Contains(attribute.AttributeType.Name));

    /// <summary>Reads the declared bilingual name, retaining an empty English alias when none is supplied.</summary>
    private static MetadataName AttributeName(CustomAttributeData attribute, string fallback)
    {
        var strings = attribute.ConstructorArguments.Where(argument => argument.ArgumentType == typeof(string))
            .Select(argument => argument.Value as string ?? "").ToArray();
        var named = attribute.NamedArguments.FirstOrDefault(argument => argument.MemberName == "Name").TypedValue.Value as string;
        return new MetadataName(strings.FirstOrDefault() ?? named ?? fallback, strings.Skip(1).FirstOrDefault() ?? "");
    }

    /// <summary>Reads a named Boolean argument, using the fallback for an absent or non-Boolean value.</summary>
    private static bool AttributeBoolean(CustomAttributeData attribute, string name, bool fallback) =>
        attribute.NamedArguments.FirstOrDefault(argument => argument.MemberName == name).TypedValue.Value is bool value ? value : fallback;

    /// <summary>Formats metadata defaults invariantly; missing CLR defaults remain absent rather than undefined.</summary>
    private static string? DefaultValue(object? value) => value switch
    {
        null => "Неопределено",
        string text => "\"" + text.Replace("\"", "\"\"") + "\"",
        bool flag => flag ? "Истина" : "Ложь",
        DBNull or Missing => null,
        IFormattable formatted => formatted.ToString(null, CultureInfo.InvariantCulture),
        _ => value.ToString()
    };

    /// <summary>Limits reflection to OneScript, ScriptEngine and oscript assemblies from the chosen distribution.</summary>
    private static bool IsRuntimeAssembly(string file)
    {
        var name = Path.GetFileName(file);
        return name.StartsWith("OneScript.", StringComparison.Ordinal)
            || name.StartsWith("ScriptEngine", StringComparison.Ordinal)
            || name.Equals("oscript.dll", StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>Rejects native DLLs before reflection loading; other file-access failures propagate to the caller.</summary>
    private static bool IsManaged(string file)
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

    /// <summary>Resolves distribution dependencies without changing the process-wide resolver.</summary>
    private sealed class DistributionLoadContext(string directory) : AssemblyLoadContext(isCollectible: true)
    {
        internal string Directory { get; } = directory;

        /// <summary>Loads a dependency from the distribution directory, returning null for normal resolver fallback.</summary>
        protected override Assembly? Load(AssemblyName name)
        {
            var file = Path.Combine(Directory, name.Name + ".dll");
            return File.Exists(file) ? LoadFromAssemblyPath(file) : null;
        }
    }
}
