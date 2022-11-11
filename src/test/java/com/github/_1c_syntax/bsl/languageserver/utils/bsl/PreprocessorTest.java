package com.github._1c_syntax.bsl.languageserver.utils.bsl;

import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorConstraints;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionParseTreeRewriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PreprocessorTest {

  @Test
  void calculatePreprocessorConstraints() {
    var variants = Map.ofEntries(
      Map.entry("ТонкийКлиент", EnumSet.of(PreprocessorConstraints.THIN_CLIENT)),
      Map.entry("Клиент", EnumSet.of(
        PreprocessorConstraints.ORDINARY_THICK_CLIENT,
        PreprocessorConstraints.MANAGED_THICK_CLIENT,
        PreprocessorConstraints.MOBILE_CLIENT,
        PreprocessorConstraints.THIN_CLIENT,
        PreprocessorConstraints.WEB_CLIENT)),
      Map.entry("ТонкийКлиент ИЛИ ВебКлиент", EnumSet.of(
        PreprocessorConstraints.THIN_CLIENT,
        PreprocessorConstraints.WEB_CLIENT)),
      Map.entry("Клиент И (ТонкийКлиент ИЛИ ВебКлиент)", EnumSet.of(
        PreprocessorConstraints.THIN_CLIENT,
        PreprocessorConstraints.WEB_CLIENT)),
      Map.entry("НЕ Сервер", without(PreprocessorConstraints.DEFAULT_CONSTRAINTS, PreprocessorConstraints.SERVER)),
      Map.entry("НЕ (Сервер ИЛИ ТонкийКлиент)", without(PreprocessorConstraints.DEFAULT_CONSTRAINTS, PreprocessorConstraints.SERVER, PreprocessorConstraints.THIN_CLIENT)),
      Map.entry("НЕ (Сервер ИЛИ Клиент)", EnumSet.of(
        PreprocessorConstraints.MOBILE_APP_CLIENT,
        PreprocessorConstraints.MOBILE_STANDALONE_SERVER,
        PreprocessorConstraints.MOBILE_APP_SERVER,
        PreprocessorConstraints.EXTERNAL_CONNECTION))
    );

    for (var variant : variants.entrySet()) {
      var expression = getPreprocessorExpressionTree(variant.getKey());
      var result = Preprocessor.calculatePreprocessorConstraints(expression);
      assertThat(result).describedAs("Условие прероцессора: %s", variant.getKey()).isEqualTo(variant.getValue());
    }
  }

  BslExpression getPreprocessorExpressionTree(String code) {
    var preprocessorPredicate = String.format("#Если %s Тогда\n#КонецЕсли", code);
    var dContext = TestUtils.getDocumentContext(preprocessorPredicate);
    var expression = dContext.getAst().preprocessor(0).preproc_if().preproc_expression();
    return ExpressionParseTreeRewriter.buildExpressionTree(expression);
  }

  @SafeVarargs
  final <T extends Enum<T>> EnumSet<T> without(Set<T> set, T... value) {
    EnumSet<T> result;
    if(set instanceof EnumSet){
      result = ((EnumSet<T>)set).clone();
    }else {
      result = EnumSet.copyOf(set);
    }
    Arrays.asList(value).forEach(result::remove);
    return result;
  }
}