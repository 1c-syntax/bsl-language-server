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
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UsingHardcodeSecretInformationDiagnosticTest extends AbstractDiagnosticTest<UsingHardcodeSecretInformationDiagnostic> {
	UsingHardcodeSecretInformationDiagnosticTest() {
		super(UsingHardcodeSecretInformationDiagnostic.class);
	}

	@Test
	public void test() {

		// when
		List<Diagnostic> diagnostics = getDiagnostics();

		// then
		assertThat(diagnostics).hasSize(7);

		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(8, 4, 8, 49)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(12, 4, 12, 80)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(16, 4, 16, 23)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(17, 4, 17, 23)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(27, 4, 27, 35)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(32, 4, 32, 27)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(33, 4, 33, 31)));

	}
}
