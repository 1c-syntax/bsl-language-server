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

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
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
		assertThat(diagnostics).hasSize(1);

	}

	@Test
	void testRuAndEn() {

		Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
		configuration.put("declaredLanguages", "ru,en");
		getDiagnosticInstance().configure(configuration);

		List<Diagnostic> diagnostics = getDiagnostics();
		assertThat(diagnostics).hasSize(3);
	}
}
