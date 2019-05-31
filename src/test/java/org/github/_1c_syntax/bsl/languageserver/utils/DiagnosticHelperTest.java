package org.github._1c_syntax.bsl.languageserver.utils;

import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.io.FileUtils;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

class DiagnosticHelperTest {

  @Test
  void testNewExpressionTypeName() throws IOException {

    File testFile = new File("./src/test/resources/utils/NewExpressionTypeName.bsl");
    String fileContent = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);
    DocumentContext documentContext = new DocumentContext("file:///fake-uri.bsl", fileContent);

    NewExpressionContextFinder newExpressionContextFinder = new NewExpressionContextFinder();
    newExpressionContextFinder.visitFile(documentContext.getAst());
    List<BSLParser.NewExpressionContext> newExpressionContexts = newExpressionContextFinder.getNewExpressionContexts();
    assertThat(newExpressionContexts).hasSize(12);

    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(0))).isEqualToIgnoringCase("Соответствие");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(1))).isEqualToIgnoringCase("ХранилищеЗначения");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(2))).isEqualToIgnoringCase("Запрос");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(3))).isEqualToIgnoringCase("Структура");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(4))).isEqualToIgnoringCase("Структура");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(5))).isEqualToIgnoringCase("СписокЗначений");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(6))).isEqualToIgnoringCase("Массив");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(7))).isEqualToIgnoringCase("ТаблицаЗначений");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(8))).isEqualToIgnoringCase("Массив");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(9))).isEqualToIgnoringCase("Структура");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(10))).isEqualToIgnoringCase("Массив");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(11))).isEmpty();

  }

  private static class NewExpressionContextFinder extends BSLParserBaseVisitor<ParseTree> {

    private List<BSLParser.NewExpressionContext> newExpressionContexts = new ArrayList<>();

    @Override
    public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
      newExpressionContexts.add(ctx);
      return super.visitNewExpression(ctx);
    }

    private List<BSLParser.NewExpressionContext> getNewExpressionContexts() {
      return newExpressionContexts;
    }

  }
}
