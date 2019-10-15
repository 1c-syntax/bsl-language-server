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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;

import java.util.List;
import java.util.stream.Collectors;

@DiagnosticMetadata(
	type = DiagnosticType.CODE_SMELL,
	severity = DiagnosticSeverity.MAJOR,
	minutesToFix = 1,
	activatedByDefault = true
)
public class UnusedLocalMethodDiagnostic extends AbstractVisitorDiagnostic {

	@Override
	public ParseTree visitFile(BSLParser.FileContext ctx) {

		List<String> collect = Trees.findAllRuleNodes(ctx, BSLParser.RULE_globalMethodCall)
	 		.stream()
			.map(parseTree -> ((BSLParser.GlobalMethodCallContext) parseTree).methodName().getText().toLowerCase())
			.collect(Collectors.toList());

		List<BSLParser.SubNameContext> notUsedInMethods = Trees.findAllRuleNodes(ctx, BSLParser.RULE_subName)
			.stream()
			.map(parseTree -> ((BSLParser.SubNameContext) parseTree))
			.filter(subName -> Trees.findAllTokenNodes(subName.getParent(), BSLLexer.EXPORT_KEYWORD).isEmpty())
			.filter(subNameContext -> !collect.contains(subNameContext.getText().toLowerCase()))
			.collect(Collectors.toList());

		notUsedInMethods.forEach(node -> diagnosticStorage.addDiagnostic(node, getDiagnosticMessage(node.getText())));

		return ctx;
	}

}
