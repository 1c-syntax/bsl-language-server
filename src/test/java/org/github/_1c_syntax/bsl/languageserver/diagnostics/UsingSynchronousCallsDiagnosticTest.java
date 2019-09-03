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

class UsingSynchronousCallsDiagnosticTest extends AbstractDiagnosticTest<UsingSynchronousCallsDiagnostic>{
	UsingSynchronousCallsDiagnosticTest() {
		super(UsingSynchronousCallsDiagnostic.class);
}

	@Test
	void test() {
		List<Diagnostic> diagnostics = getDiagnostics();

		assertThat(diagnostics).hasSize(28);
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(2, 12, 3, 57))
				&& diagnostic.getMessage().matches(".*синхронного.*Вопрос.*ПоказатьВопрос.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(21, 4, 21, 84))
				&& diagnostic.getMessage().matches(".*синхронного.*Предупреждение.*ПоказатьПредупреждение.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(29, 4, 29, 26))
				&& diagnostic.getMessage().matches(".*синхронного.*ОткрытьЗначение.*ПоказатьЗначение.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(43, 9, 43, 58))
				&& diagnostic.getMessage().matches(".*синхронного.*ВвестиДату.*ПоказатьВводДаты.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(72, 9, 72, 67))
				&& diagnostic.getMessage().matches(".*синхронного.*ВвестиЗначение.*ПоказатьВводЗначения.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(103, 9, 103, 50))
				&& diagnostic.getMessage().matches(".*синхронного.*ВвестиСтроку.*ПоказатьВводСтроки.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(122, 9, 122, 61))
				&& diagnostic.getMessage().matches(".*синхронного.*ВвестиЧисло.*ПоказатьВводЧисла.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(138, 4, 138, 50))
				&& diagnostic.getMessage().matches(".*синхронного.*УстановитьВнешнююКомпоненту.*НачатьУстановкуВнешнейКомпоненты.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(148, 4, 148, 33))
				&& diagnostic.getMessage().matches(".*синхронного.*ОткрытьФормуМодально.*ОткрытьФорму.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(159, 20, 159, 56))
				&& diagnostic.getMessage().matches(".*синхронного.*УстановитьРасширениеРаботыСФайлами.*НачатьУстановкуРасширенияРаботыСФайлами.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(172, 20, 172, 62))
				&& diagnostic.getMessage().matches(".*синхронного.*УстановитьРасширениеРаботыСКриптографией.*НачатьУстановкуРасширенияРаботыСКриптографией.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(184, 12, 184, 54))
				&& diagnostic.getMessage().matches(".*синхронного.*ПодключитьРасширениеРаботыСКриптографией.*НачатьПодключениеРасширенияРаботыСКриптографией.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(185, 8, 185, 129))
				&& diagnostic.getMessage().matches(".*синхронного.*Предупреждение.*ПоказатьПредупреждение.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(198, 12, 198, 48))
				&& diagnostic.getMessage().matches(".*синхронного.*ПодключитьРасширениеРаботыСФайлами.*НачатьПодключениеРасширенияРаботыСФайлами.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(199, 8, 199, 109))
				&& diagnostic.getMessage().matches(".*синхронного.*Предупреждение.*ПоказатьПредупреждение.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(214, 4, 214, 88))
				&& diagnostic.getMessage().matches(".*синхронного.*ПоместитьФайл.*НачатьПомещениеФайла.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(225, 4, 225, 68))
				&& diagnostic.getMessage().matches(".*синхронного.*КопироватьФайл.*НачатьКопированиеФайла.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(236, 4, 236, 69))
				&& diagnostic.getMessage().matches(".*синхронного.*ПереместитьФайл.*НачатьПеремещениеФайла.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(247, 21, 247, 51))
				&& diagnostic.getMessage().matches(".*синхронного.*НайтиФайлы.*НачатьПоискФайлов.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(260, 8, 260, 37))
				&& diagnostic.getMessage().matches(".*синхронного.*УдалитьФайлы.*НачатьУдалениеФайлов.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(274, 4, 274, 29))
				&& diagnostic.getMessage().matches(".*синхронного.*СоздатьКаталог.*НачатьСозданиеКаталога.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(285, 16, 285, 40))
				&& diagnostic.getMessage().matches(".*синхронного.*КаталогВременныхФайлов.*НачатьПолучениеКаталогаВременныхФайлов.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(296, 16, 296, 35))
				&& diagnostic.getMessage().matches(".*синхронного.*КаталогДокументов.*НачатьПолучениеКаталогаДокументов.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(307, 16, 307, 50))
				&& diagnostic.getMessage().matches(".*синхронного.*РабочийКаталогДанныхПользователя.*НачатьПолучениеРабочегоКаталогаДанныхПользователя.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(318, 16, 318, 89))
				&& diagnostic.getMessage().matches(".*синхронного.*ПолучитьФайлы.*НачатьПолучениеФайлов.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(344, 16, 344, 64))
				&& diagnostic.getMessage().matches(".*синхронного.*ПоместитьФайлы.*НачатьПомещениеФайлов.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(368, 12, 368, 59))
				&& diagnostic.getMessage().matches(".*синхронного.*ЗапроситьРазрешениеПользователя.*НачатьЗапросРазрешенияПользователя.*"))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(391, 4, 391, 38))
				&& diagnostic.getMessage().matches(".*синхронного.*ЗапуститьПриложение.*НачатьЗапускПриложения.*"))
		;
	}
}
