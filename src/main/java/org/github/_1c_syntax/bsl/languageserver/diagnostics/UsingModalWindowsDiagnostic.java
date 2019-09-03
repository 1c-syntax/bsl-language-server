/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

@DiagnosticMetadata(
	type = DiagnosticType.CODE_SMELL,
	severity = DiagnosticSeverity.MAJOR,
	minutesToFix = 15,
	scope = DiagnosticScope.BSL,
	activatedByDefault = false
)
public class UsingModalWindowsDiagnostic extends AbstractVisitorDiagnostic {

	private Pattern modalityMethods = Pattern.compile(
		"(ВОПРОС|DOQUERYBOX|ОТКРЫТЬФОРМУМОДАЛЬНО|OPENFORMMODAL|ОТКРЫТЬЗНАЧЕНИЕ|OPENVALUE|" +
			"ПРЕДУПРЕЖДЕНИЕ|DOMESSAGEBOX|ВВЕСТИДАТУ|INPUTDATE|ВВЕСТИЗНАЧЕНИЕ|INPUTVALUE|" +
			"ВВЕСТИСТРОКУ|INPUTSTRING|ВВЕСТИЧИСЛО|INPUTNUMBER|УСТАНОВИТЬВНЕШНЮЮКОМПОНЕНТУ|INSTALLADDIN|" +
			"УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ|INSTALLFILESYSTEMEXTENSION|" +
			"УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ|INSTALLCRYPTOEXTENSION|ПОМЕСТИТЬФАЙЛ|PUTFILE)",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private HashMap<String, String> pairMethods = new HashMap<>();

	public UsingModalWindowsDiagnostic() {
		pairMethods.put("ВОПРОС", "ПоказатьВопрос");
		pairMethods.put("DOQUERYBOX", "ShowQueryBox");
		pairMethods.put("ОТКРЫТЬФОРМУМОДАЛЬНО", "ОткрытьФорму");
		pairMethods.put("OPENFORMMODAL", "OpenForm");
		pairMethods.put("ОТКРЫТЬЗНАЧЕНИЕ", "ПоказатьЗначение");
		pairMethods.put("OPENVALUE", "ShowValue");
		pairMethods.put("ПРЕДУПРЕЖДЕНИЕ", "ПоказатьПредупреждение");
		pairMethods.put("DOMESSAGEBOX", "ShowMessageBox");
		pairMethods.put("ВВЕСТИДАТУ", "ПоказатьВводДаты");
		pairMethods.put("INPUTDATE", "ShowInputDate");
		pairMethods.put("ВВЕСТИЗНАЧЕНИЕ", "ПоказатьВводЗначения");
		pairMethods.put("INPUTVALUE", "ShowInputValue");
		pairMethods.put("ВВЕСТИСТРОКУ", "ПоказатьВводСтроки");
		pairMethods.put("INPUTSTRING", "ShowInputString");
		pairMethods.put("ВВЕСТИЧИСЛО", "ПоказатьВводЧисла");
		pairMethods.put("INPUTNUMBER", "ShowInputNumber");
		pairMethods.put("УСТАНОВИТЬВНЕШНЮЮКОМПОНЕНТУ", "НачатьУстановкуВнешнейКомпоненты");
		pairMethods.put("INSTALLADDIN", "BeginInstallAddIn");
		pairMethods.put("УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ", "НачатьУстановкуРасширенияРаботыСФайлами");
		pairMethods.put("INSTALLFILESYSTEMEXTENSION", "BeginInstallFileSystemExtension");
		pairMethods.put("УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ", "НачатьУстановкуРасширенияРаботыСКриптографией");
		pairMethods.put("INSTALLCRYPTOEXTENSION", "BeginInstallCryptoExtension");
		pairMethods.put("ПОМЕСТИТЬФАЙЛ", "НачатьПомещениеФайла");
		pairMethods.put("PUTFILE", "BeginPutFile");
	}

	@Override
	public ParseTree visitFile(BSLParser.FileContext ctx) {
		Trees.findAllRuleNodes(ctx, BSLParser.RULE_globalMethodCall)
			.stream().filter(node -> modalityMethods.matcher((
			(BSLParser.GlobalMethodCallContext) node).methodName().getText()).matches())
			.forEach(node -> addDiagnostic(node));

		return ctx;
	}

	private void addDiagnostic(ParseTree node) {
		String methodName = ((BSLParser.GlobalMethodCallContext) node).methodName().getText();
		diagnosticStorage.addDiagnostic((BSLParser.GlobalMethodCallContext) node,
			getDiagnosticMessage(methodName, pairMethods.get(methodName.toUpperCase(Locale.ENGLISH))));
	}
}
