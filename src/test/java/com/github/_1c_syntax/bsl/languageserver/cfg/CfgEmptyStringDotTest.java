package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CfgEmptyStringDotTest {

  @Test
  void testEmptyStringWithDotShouldNotCrash() {
    var code = """
      Процедура РаспределитьНаТовары(Команда)
      
      Если Товар.ТаможеннаяСтоимость = "". Тогда
      
      КонецЕсли;
      
      КонецПроцедуры
      """;

    var dContext = TestUtils.getDocumentContext(code);
    var parseTree = dContext.getAst().subs().sub(0).procedure().subCodeBlock().codeBlock();
    
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    builder.produceLoopIterations(false);
    builder.determineAdjacentDeadCode(false);

    // This should not crash
    var graph = builder.buildGraph(parseTree);
    
    // Basic validation that the graph was created
    assertThat(graph).isNotNull();
    assertThat(graph.getEntryPoint()).isNotNull();
    assertThat(graph.getExitPoint()).isNotNull();
  }

  @Test
  void testEmptyElsifBranchShouldNotCrash() {
    var code = """
      Процедура Тест()
      
      Если Условие1 Тогда
        Действие1();
      ИначеЕсли Условие2 = "". Тогда
      
      КонецЕсли;
      
      КонецПроцедуры
      """;

    var dContext = TestUtils.getDocumentContext(code);
    var parseTree = dContext.getAst().subs().sub(0).procedure().subCodeBlock().codeBlock();
    
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    builder.produceLoopIterations(false);
    builder.determineAdjacentDeadCode(false);

    // This should not crash
    var graph = builder.buildGraph(parseTree);
    
    // Basic validation that the graph was created
    assertThat(graph).isNotNull();
    assertThat(graph.getEntryPoint()).isNotNull();
    assertThat(graph.getExitPoint()).isNotNull();
  }

  @Test
  void testEmptyElseBranchShouldNotCrash() {
    var code = """
      Процедура Тест()
      
      Если Условие1 Тогда
        Действие1();
      Иначе
      
      КонецЕсли;
      
      КонецПроцедуры
      """;

    var dContext = TestUtils.getDocumentContext(code);
    var parseTree = dContext.getAst().subs().sub(0).procedure().subCodeBlock().codeBlock();
    
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    builder.produceLoopIterations(false);
    builder.determineAdjacentDeadCode(false);

    // This should not crash
    var graph = builder.buildGraph(parseTree);
    
    // Basic validation that the graph was created
    assertThat(graph).isNotNull();
    assertThat(graph.getEntryPoint()).isNotNull();
    assertThat(graph.getExitPoint()).isNotNull();
  }
}
