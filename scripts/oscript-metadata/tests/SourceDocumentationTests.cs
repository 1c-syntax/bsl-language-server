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
using Xunit;

public sealed class SourceDocumentationTests : IDisposable
{
    private readonly string directory = Directory.CreateTempSubdirectory("bsl-oscript-source-").FullName;

    [Fact]
    public void SourceIndexKeepsMethodParametersAndLiteralEnumAliases()
    {
        WriteLanguage("class LanguageDef { void Register() { AddToken(Token.Number, \"Число\", \"Number\"); } }");
        File.WriteAllText(Path.Combine(directory, "Example.cs"), """
            namespace Sample;
            /// <summary>Тип примера.</summary>
            class Example
            {
                /// <summary>Возвращает значение.</summary>
                /// <param name="value">Входное значение.</param>
                /// <returns>Исходная строка.</returns>
                public string Echo(string value) => value;

                public Example()
                {
                    this.WrapClrValue("Default", default, 0);
                    this.WrapClrValue("Юникод", "Unicode", 1);
                }
            }
            """);

        var documentation = SourceDocumentation.Read(directory);

        var method = documentation.Get(SourceDocumentation.MethodKey("Sample.Example", "Echo", new[] { "value" }));
        Assert.Equal("Входное значение.", SourceDocumentation.Text(method!.Element("param")));
        Assert.Equal("Исходная строка.", SourceDocumentation.Text(method.Element("returns")));
        Assert.Equal(new[] { new MetadataName("Default", "Default"), new MetadataName("Юникод", "Unicode") },
            documentation.EnumValues("Sample.Example"));
        Assert.Equal(new[] { "Число", "Number" }, documentation.IntrinsicNames);
    }

    [Fact]
    public void MalformedXmlLeavesOtherMemberDocumentationAvailable()
    {
        WriteLanguage("class LanguageDef {}");
        File.WriteAllText(Path.Combine(directory, "Example.cs"), """
            namespace Sample;
            /// <summary>Тип.</summary>
            class Example
            {
                /// <summary>Незакрытый комментарий
                public void Broken() {}
                /// <summary>Корректный комментарий.</summary>
                public void Valid() {}
            }
            """);

        var documentation = SourceDocumentation.Read(directory);

        Assert.Null(documentation.Get("Sample.Example.Broken()"));
        Assert.Equal("Корректный комментарий.",
            SourceDocumentation.Text(documentation.Get("Sample.Example.Valid()")!.Element("summary")));
    }

    [Fact]
    public void MissingCompilerDeclarationsFailBeforeExportingAnIncompleteApi()
    {
        Assert.Throws<FileNotFoundException>(() => SourceDocumentation.Read(directory));
    }

    private void WriteLanguage(string source)
    {
        var languageDirectory = Path.Combine(directory, "src", "OneScript.Language");
        Directory.CreateDirectory(languageDirectory);
        File.WriteAllText(Path.Combine(languageDirectory, "LanguageDef.cs"), source);
    }

    public void Dispose()
    {
        Assert.Equal(Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar),
            Path.GetDirectoryName(Path.GetFullPath(directory)));
        Assert.StartsWith("bsl-oscript-source-", Path.GetFileName(directory));
        Directory.Delete(directory, true);
    }
}
