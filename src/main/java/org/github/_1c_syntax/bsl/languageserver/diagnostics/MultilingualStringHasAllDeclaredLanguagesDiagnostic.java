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
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.MultilingualStringParser;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.Map;

@DiagnosticMetadata(
	type = DiagnosticType.ERROR,
	severity = DiagnosticSeverity.MINOR,
	scope = DiagnosticScope.BSL,
	minutesToFix = 2
)
public class MultilingualStringHasAllDeclaredLanguagesDiagnostic extends AbstractVisitorDiagnostic {

	private static final String DECLARED_LANGUAGES_DEFAULT = "ru";

	@DiagnosticParameter(
		type = String.class,
		defaultValue = "" + DECLARED_LANGUAGES_DEFAULT,
		description = "Заявленные языки"
	)
	private String declaredLanguages = DECLARED_LANGUAGES_DEFAULT;

	@Override
	public void configure(Map<String, Object> configuration) {
		if (configuration == null) {
			return;
		}

		declaredLanguages = (String) configuration.get("declaredLanguages");
	}

	@Override
	public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
		if(MultilingualStringParser.isNotMultilingualString(ctx)) {
			return super.visitGlobalMethodCall(ctx);
		}

		MultilingualStringParser parser = new MultilingualStringParser(ctx);
		if(!parser.hasAllDeclaredLanguages(declaredLanguages) && !parser.isParentTemplate()) {
			diagnosticStorage.addDiagnostic(ctx, getDiagnosticMessage(parser.getMissingLanguages()));
		}
		return super.visitGlobalMethodCall(ctx);
	}

}
