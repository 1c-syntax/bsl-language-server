package com.github._1c_syntax.bsl.languageserver.context.callee;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CalleeStorageFillerTest {

  @Autowired
  private CalleeStorageFiller calleeStorageFiller;
  @Autowired
  private CalleeStorage calleeStorage;

  @Test
  void testFindCalledMethod() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/CalleeStorageFillerTest.bsl");
    calleeStorageFiller.fill(documentContext);

    // when
    Optional<Pair<MethodSymbol, Range>> calledMethodSymbol = calleeStorage.getCalledMethodSymbol(documentContext.getUri(), new Position(4, 0));

    // then
    assertThat(calledMethodSymbol).isPresent();

    assertThat(calledMethodSymbol).get()
      .extracting(Pair::getKey)
      .extracting(MethodSymbol::getName)
      .isEqualTo("Локальная");

    assertThat(calledMethodSymbol).get()
      .extracting(Pair::getValue)
      .isEqualTo(Ranges.create(4, 0, 4, 9));
  }

  @Test
  void testRebuildClearCallees() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/CalleeStorageFillerTest.bsl");
    MethodSymbol methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Локальная").get();

    // when
    calleeStorageFiller.fill(documentContext);
    List<Location> calleesOf = calleeStorage.getCalleesOf(MdoRefBuilder.getMdoRef(documentContext), documentContext.getModuleType(), methodSymbol);

    // then
    assertThat(calleesOf).hasSize(1);

    // when
    // recalculate
    calleeStorageFiller.fill(documentContext);
    calleesOf = calleeStorage.getCalleesOf(MdoRefBuilder.getMdoRef(documentContext), documentContext.getModuleType(), methodSymbol);

    // then
    assertThat(calleesOf).hasSize(1);
  }
}