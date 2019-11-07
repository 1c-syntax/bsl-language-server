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

import org.eclipse.lsp4j.Diagnostic;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MultilingualStringHasAllDeclaredLanguagesDiagnosticTest
	extends AbstractDiagnosticTest<MultilingualStringHasAllDeclaredLanguagesDiagnostic> {

	MultilingualStringHasAllDeclaredLanguagesDiagnosticTest() {
		super(MultilingualStringHasAllDeclaredLanguagesDiagnostic.class);
	}

	@Test
	void testOnlyRU() {
		List<Diagnostic> diagnostics = getDiagnostics();
		assertThat(diagnostics).hasSize(3);
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(12, 16, 12, 22)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(13, 30, 13, 86)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(16, 30, 16, 66)));
	}

	@Test
	void testRuAndEn() {
		Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(diagnosticInstance);
		configuration.put("declaredLanguages", "ru,en");
		diagnosticInstance.configure(configuration);

		List<Diagnostic> diagnostics = getDiagnostics();
		assertThat(diagnostics).hasSize(5);
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(12, 16, 12, 22)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(13, 30, 13, 86)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(15, 27, 15, 65)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(16, 30, 16, 66)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(27, 37, 27, 75)))
		;
	}
}
