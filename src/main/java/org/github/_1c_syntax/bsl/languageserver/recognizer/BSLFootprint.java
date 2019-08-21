package org.github._1c_syntax.bsl.languageserver.recognizer;

import java.util.HashSet;
import java.util.Set;

public class BSLFootprint implements LanguageFootprint {

  private final Set<Detector> detectors = new HashSet<>();

  public BSLFootprint() {
    detectors.add(new CamelCaseDetector(0.7));
    detectors.add(new ContainsDetector(0.95, "КонецПроцедуры;", "КонецФункции;", "КонецЕсли;", "КонецЦикла;",
      "Возврат;", ".НайтиСтроки(", "СтрНачинается(", "СтрНайти(", ".Выбрать(", ".Выгрузить(", ".Выполнить("));
    detectors.add(new KeywordsDetector(0.95,, "И", "ИЛИ", "НЕ", "ИначеЕсли"));
    detectors.add(new KeywordsDetector(0.7, "ВЫБРАТЬ", "РАЗРЕШЕННЫЕ", "ПЕРВЫЕ", "ГДЕ", "СОЕДИНЕНИЕ",
      "ОБЪЕДИНИТЬ", "ВЫБОР", "КАК", "ТОГДА", "КОГДА", "ИНАЧЕ", "ПОМЕСТИТЬ", "ИЗ"));
    detectors.add(new KeywordsDetector(0.3, "Если", "Тогда", "Процедура", "Функция", "Пока", "Для", "Каждого",
      "Цикл", "Возврат", "Новый"));
  }

  @Override
  public Set<Detector> getDetectors() {
    return detectors;
  }

}
