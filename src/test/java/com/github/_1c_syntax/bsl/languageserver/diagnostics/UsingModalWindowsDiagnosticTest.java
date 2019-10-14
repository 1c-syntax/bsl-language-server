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
import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsingModalWindowsDiagnosticTest extends AbstractDiagnosticTest<UsingModalWindowsDiagnostic>{
	UsingModalWindowsDiagnosticTest() {
		super(UsingModalWindowsDiagnostic.class);
}

	@Test
	void test() {
		List<Diagnostic> diagnostics = getDiagnostics();

		assertThat(diagnostics).hasSize(12);
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(2, 12, 3, 57))
				&& diagnostic.getMessage().matches(".*(модального|modal).*Вопрос.*ПоказатьВопрос.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(21, 4, 21, 84))
				&& diagnostic.getMessage().matches(".*(модального|modal).*Предупреждение.*ПоказатьПредупреждение.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(29, 4, 29, 26))
				&& diagnostic.getMessage().matches(".*(модального|modal).*ОткрытьЗначение.*ПоказатьЗначение.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(43, 9, 43, 58))
				&& diagnostic.getMessage().matches(".*(модального|modal).*ВвестиДату.*ПоказатьВводДаты.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(72, 9, 72, 67))
				&& diagnostic.getMessage().matches(".*(модального|modal).*ВвестиЗначение.*ПоказатьВводЗначения.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(103, 9, 103, 50))
				&& diagnostic.getMessage().matches(".*(модального|modal).*ВвестиСтроку.*ПоказатьВводСтроки.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(122, 9, 122, 61))
				&& diagnostic.getMessage().matches(".*(модального|modal).*ВвестиЧисло.*ПоказатьВводЧисла.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(138, 4, 138, 50))
				&& diagnostic.getMessage().matches(".*(модального|modal).*УстановитьВнешнююКомпоненту.*НачатьУстановкуВнешнейКомпоненты.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(148, 4, 148, 33))
				&& diagnostic.getMessage().matches(".*(модального|modal).*ОткрытьФормуМодально.*ОткрытьФорму.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(159, 20, 159, 56))
				&& diagnostic.getMessage().matches(".*(модального|modal).*УстановитьРасширениеРаботыСФайлами.*НачатьУстановкуРасширенияРаботыСФайлами.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(172, 20, 172, 62))
				&& diagnostic.getMessage().matches(".*(модального|modal).*УстановитьРасширениеРаботыСКриптографией.*НачатьУстановкуРасширенияРаботыСКриптографией.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(186, 4, 186, 88))
				&& diagnostic.getMessage().matches(".*(модального|modal).*ПоместитьФайл.*НачатьПомещениеФайла.*"));
	}
}

