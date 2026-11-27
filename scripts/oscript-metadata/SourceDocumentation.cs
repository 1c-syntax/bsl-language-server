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
using System.Text.RegularExpressions;
using System.Xml.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

/// <summary>Indexes XML comments, literal enum names and compiler tokens from the matching source release.</summary>
internal sealed class SourceDocumentation
{
    internal static SourceDocumentation Empty => new();
    private readonly Dictionary<string, XElement> entries = new();
    private readonly Dictionary<string, MetadataName[]> enums = new();
    internal int Count => entries.Count;
    internal string[] IntrinsicNames { get; private set; } = Array.Empty<string>();

    /// <summary>Returns indexed member XML, or null when the member is absent or its XML could not be parsed.</summary>
    internal XElement? Get(string key) => entries.GetValueOrDefault(key);

    /// <summary>Returns source-declared bilingual enum values, or an empty array when none were found.</summary>
    internal MetadataName[] EnumValues(string type) => enums.GetValueOrDefault(type, Array.Empty<MetadataName>());

    /// <summary>Indexes source comments, literal enum values and compiler spellings from one matching release.</summary>
    /// <param name="directory">OneScript source root containing src/OneScript.Language/LanguageDef.cs.</param>
    /// <returns>An index keyed by CLR type and member identities.</returns>
    /// <exception cref="FileNotFoundException">The compiler token declarations are absent.</exception>
    internal static SourceDocumentation Read(string directory)
    {
        var result = new SourceDocumentation();
        var language = Path.GetFullPath(Path.Combine(directory, "src", "OneScript.Language", "LanguageDef.cs"));
        if (!File.Exists(language)) throw new FileNotFoundException("OneScript compiler token declarations are missing", language);
        foreach (var file in Directory.EnumerateFiles(directory, "*.cs", SearchOption.AllDirectories))
        {
            var root = CSharpSyntaxTree.ParseText(File.ReadAllText(file)).GetRoot();
            result.ReadComments(root);
            result.ReadEnumValues(root);
            if (Path.GetFullPath(file) == language) result.ReadCompilerNames(root);
        }
        return result;
    }

    /// <summary>Builds the shared lookup key from the declaring CLR type, method name and ordered parameter names.</summary>
    internal static string MethodKey(string type, string name, IEnumerable<string> parameters) =>
        type + "." + name + "(" + string.Join(",", parameters) + ")";

    /// <summary>Flattens XML text into a trimmed, whitespace-normalized description; missing elements yield empty text.</summary>
    internal static string Text(XElement? element) => element == null ? "" : Regex.Replace(element.Value, @"\s+", " ").Trim();

    /// <summary>Indexes supported member comments while isolating malformed XML to the affected member.</summary>
    private void ReadComments(SyntaxNode root)
    {
        foreach (var node in root.DescendantNodes().OfType<MemberDeclarationSyntax>())
        {
            var comment = node.GetLeadingTrivia().Select(trivia => trivia.GetStructure())
                .OfType<DocumentationCommentTriviaSyntax>().FirstOrDefault();
            if (comment == null) continue;
            var type = ContainingType(node);
            var key = node switch
            {
                BaseTypeDeclarationSyntax declaration => type
                    + (node.Ancestors().OfType<BaseTypeDeclarationSyntax>().Any() ? "+" : "") + declaration.Identifier.ValueText,
                MethodDeclarationSyntax method => MethodKey(type, method.Identifier.ValueText,
                    method.ParameterList.Parameters.Select(parameter => parameter.Identifier.ValueText)),
                PropertyDeclarationSyntax property => type + "." + property.Identifier.ValueText,
                _ => null
            };
            if (key == null) continue;
            var text = string.Join("\n", comment.ToFullString().Split('\n')
                .Select(line => line.TrimStart().StartsWith("///") ? line.TrimStart()[3..] : line));
            try
            {
                entries[key] = XElement.Parse("<doc>" + text + "</doc>");
            }
            catch (System.Xml.XmlException)
            {
                // Partial upstream XML comments leave this member undocumented.
            }
        }
    }

    /// <summary>
    /// Extracts literal WrapClrValue and MakeValue name pairs without executing constructors or guessing translated aliases.
    /// </summary>
    private void ReadEnumValues(SyntaxNode root)
    {
        foreach (var declaration in root.DescendantNodes().OfType<ClassDeclarationSyntax>())
        {
            var values = declaration.DescendantNodes().OfType<InvocationExpressionSyntax>()
                .Where(call => call.Expression is MemberAccessExpressionSyntax member && member.Name.Identifier.ValueText == "WrapClrValue"
                    || call.Expression is IdentifierNameSyntax name && name.Identifier.ValueText == "MakeValue")
                .Select(call => call.ArgumentList.Arguments)
                .Where(arguments => arguments.Count >= 2 && arguments[0].Expression is LiteralExpressionSyntax literal
                    && literal.IsKind(SyntaxKind.StringLiteralExpression))
                .Select(arguments =>
                {
                    var name = ((LiteralExpressionSyntax)arguments[0].Expression).Token.ValueText;
                    var alias = arguments[1].Expression is LiteralExpressionSyntax literal && literal.IsKind(SyntaxKind.StringLiteralExpression)
                        ? literal.Token.ValueText : name.All(character => character < 128) ? name : "";
                    return new MetadataName(name, alias);
                }).Distinct().ToArray();
            if (values.Length == 0) continue;
            var container = ContainingType(declaration);
            var nested = declaration.Ancestors().OfType<BaseTypeDeclarationSyntax>().Any();
            enums[container + (nested ? "+" : "") + declaration.Identifier.ValueText] = values;
        }
    }

    /// <summary>Records distinct literal spellings following the token argument of AddToken declarations.</summary>
    private void ReadCompilerNames(SyntaxNode root)
    {
        IntrinsicNames = root.DescendantNodes().OfType<InvocationExpressionSyntax>()
            .Where(call => call.Expression is IdentifierNameSyntax name && name.Identifier.ValueText == "AddToken")
            .SelectMany(call => call.ArgumentList.Arguments.Skip(1))
            .Where(argument => argument.Expression is LiteralExpressionSyntax literal && literal.Token.Value is string)
            .Select(argument => ((LiteralExpressionSyntax)argument.Expression).Token.ValueText).Distinct().ToArray();
    }

    /// <summary>Builds the CLR container name with dotted namespaces and plus-separated nested types.</summary>
    private static string ContainingType(MemberDeclarationSyntax declaration)
    {
        var namespaces = declaration.Ancestors().OfType<BaseNamespaceDeclarationSyntax>().Reverse()
            .Select(value => value.Name.ToString());
        var containers = declaration.Ancestors().OfType<BaseTypeDeclarationSyntax>().Reverse()
            .Select(value => value.Identifier.ValueText);
        return string.Join(".", namespaces) + "." + string.Join("+", containers);
    }
}
