package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class SemanticTokensProviderTest {

  @Autowired
  private SemanticTokensProvider provider;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void emitsExpectedTokenTypes() {
    // given: sample BSL with annotation, macro, method, parameter, string, number, comment
    String bsl = String.join("\n",
      "&НаКлиенте",
      "#Если Истина Тогда",
      "Процедура Тест(Парам) Экспорт",
      "  // комментарий",
      "  Сообщить(\"строка\" + 123);",
      "КонецПроцедуры",
      "#КонецЕсли"
    );

    DocumentContext dc = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier id = TestUtils.getTextDocumentIdentifier(dc.getUri());

    // when
    var params = new SemanticTokensParams(id);
    SemanticTokens tokens = provider.getSemanticTokensFull(dc, params);

    // then: collect type indexes present
    List<Integer> data = tokens.getData();
    assertThat(data).isNotEmpty();

    Set<Integer> presentTypes = indexesOfTypes(data);

    // map desired types to indices and assert they're present
    assertPresent(presentTypes, SemanticTokenTypes.Decorator);
    assertPresent(presentTypes, SemanticTokenTypes.Macro);
    assertPresent(presentTypes, SemanticTokenTypes.Method);
    assertPresent(presentTypes, SemanticTokenTypes.Parameter);
    assertPresent(presentTypes, SemanticTokenTypes.Keyword);
    assertPresent(presentTypes, SemanticTokenTypes.String);
    assertPresent(presentTypes, SemanticTokenTypes.Number);
    assertPresent(presentTypes, SemanticTokenTypes.Comment);
  }

  @Test
  void emitsMacroForAllPreprocTokens() {
    // given: preprocessor variety to cover PREPROC_* tokens
    String bsl = String.join("\n",
      "#Область Region1",
      "#Если Сервер И НЕ Клиент Тогда",
      "Процедура Пусто()",
      "КонецПроцедуры",
      "#ИначеЕсли Клиент Тогда",
      "#Иначе",
      "#КонецЕсли",
      "#КонецОбласти"
    );

    DocumentContext dc = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier id = TestUtils.getTextDocumentIdentifier(dc.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(dc, new SemanticTokensParams(id));

    // then: count how many lexer tokens are PREPROC_* (or HASH) on default channel
    long preprocCount = dc.getTokensFromDefaultChannel().stream()
      .map(Token::getType)
      .map(BSLLexer.VOCABULARY::getSymbolicName)
      .filter(Objects::nonNull)
      .filter(sym -> sym.equals("HASH") || sym.startsWith("PREPROC_"))
      .count();

    // count how many semantic tokens are Macro
    int macroIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    assertThat(macroIdx).isGreaterThanOrEqualTo(0);

    long macroCount = countOfType(tokens.getData(), macroIdx);

    // must map one-by-one
    assertThat(macroCount).isEqualTo(preprocCount);
  }

  private Set<Integer> indexesOfTypes(List<Integer> data) {
    // data: [deltaLine, deltaStart, length, tokenType, tokenModifiers] per token
    Set<Integer> res = new HashSet<>();
    for (int i = 0; i + 3 < data.size(); i += 5) {
      res.add(data.get(i + 3));
    }
    return res;
  }

  private long countOfType(List<Integer> data, int typeIdx) {
    long cnt = 0;
    for (int i = 0; i + 3 < data.size(); i += 5) {
      if (data.get(i + 3) == typeIdx) cnt++;
    }
    return cnt;
  }

  private void assertPresent(Set<Integer> presentTypes, String tokenType) {
    int idx = legend.getTokenTypes().indexOf(tokenType);
    assertThat(idx).isGreaterThanOrEqualTo(0);
    assertThat(presentTypes).contains(idx);
  }
}
