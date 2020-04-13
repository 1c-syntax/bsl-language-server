package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodSymbolComputerTest {

  @Test
  void testMethodSymbolComputer() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.size()).isEqualTo(3);

    assertThat(methods.get(0).getName()).isEqualTo("Один");
    assertThat(methods.get(0).getDescription().orElse(null)).isNull();
    assertThat(methods.get(0).getRange()).isEqualTo(Ranges.create(1, 0, 3, 14));
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(1, 10, 1, 14));

    assertThat(methods.get(1).getDescription()).isNotEmpty();
    assertThat(methods.get(1).getRegion().orElse(null).getName()).isEqualTo("ИмяОбласти");
    assertThat(methods.get(1).getDescription().orElse(null).getDescription()).isNotEmpty();

  }

  @Test
  void testParameters() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    List<ParameterDefinition> parameters = methods.get(2).getParameters();
    assertThat(parameters.size()).isEqualTo(4);
    assertThat(parameters.get(0).getName()).isEqualTo("Парам");
    assertThat(parameters.get(0).isByValue()).isFalse();
    assertThat(parameters.get(0).isOptional()).isFalse();

    assertThat(parameters.get(1).getName()).isEqualTo("Парам2");
    assertThat(parameters.get(1).isByValue()).isTrue();
    assertThat(parameters.get(1).isOptional()).isFalse();

    assertThat(parameters.get(2).getName()).isEqualTo("Парам3");
    assertThat(parameters.get(2).isByValue()).isFalse();
    assertThat(parameters.get(2).isOptional()).isTrue();

    assertThat(parameters.get(3).getName()).isEqualTo("Парам4");
    assertThat(parameters.get(3).isByValue()).isTrue();
    assertThat(parameters.get(3).isOptional()).isTrue();

  }

  @Test
  void testParseError() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTestParseError.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.get(0).getName()).isEqualTo("Выполнить");
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(0, 10, 0, 19));

  }
}