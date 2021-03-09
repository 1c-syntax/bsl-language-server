package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class ReferenceIndexReferenceFinderTest {

  @Autowired
  private ReferenceIndexReferenceFinder referenceFinder;

  @SpyBean
  private ReferenceIndex referenceIndex;

  private static final String PATH_TO_FILE = "./src/test/resources/references/ReferenceIndexReferenceFinder.bsl";

  @Test
  void testMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var method = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(2, 10);
    var location = new Location(uri.toString(), Ranges.create(1, 4, 16));

    var expectedReference = Reference.of(method, method, location);
    when(referenceIndex.getReference(uri, position)).thenReturn(Optional.of(expectedReference));

    // when
    var reference = referenceFinder.findReference(uri, position).orElseThrow();

    // then
    assertThat(reference).isEqualTo(expectedReference);

    // when
    var optionalReference = referenceFinder.findReference(uri, new Position());

    // then
    assertThat(optionalReference).isEmpty();
  }

}