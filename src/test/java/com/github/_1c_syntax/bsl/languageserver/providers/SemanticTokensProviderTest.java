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

import java.util.ArrayList;
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
    // given: sample BSL with annotation, macro, method, parameter, string, number, comment, operators
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
    assertPresent(presentTypes, SemanticTokenTypes.Operator);
  }

  @Test
  void emitsMacroForAllPreprocTokens() {
    // given: preprocessor variety to cover PREPROC_* tokens including regions
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
    List<Token> defaultTokens = dc.getTokensFromDefaultChannel();

    long totalPreproc = defaultTokens.stream()
      .map(Token::getType)
      .map(BSLLexer.VOCABULARY::getSymbolicName)
      .filter(Objects::nonNull)
      .filter(sym -> sym.equals("HASH") || sym.startsWith("PREPROC_"))
      .count();

    // count region directives and names
    long regionDirectives = 0;
    long regionNames = 0;
    for (int i = 0; i + 1 < defaultTokens.size(); i++) {
      Token t = defaultTokens.get(i);
      Token n = defaultTokens.get(i + 1);
      if (t.getType() == BSLLexer.HASH && n.getType() == BSLLexer.PREPROC_REGION) {
        regionDirectives++;
        // if name token follows, it is included into Namespace span and not counted as Macro
        if (i + 2 < defaultTokens.size() && defaultTokens.get(i + 2).getType() == BSLLexer.PREPROC_IDENTIFIER) {
          regionNames++;
        }
      } else if (t.getType() == BSLLexer.HASH && n.getType() == BSLLexer.PREPROC_END_REGION) {
        regionDirectives++;
      }
    }

    // expected macro tokens exclude region directives (HASH + PREPROC_*) and region names after PREPROC_REGION
    long expectedMacro = totalPreproc - (regionDirectives * 2) - regionNames;

    int macroIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    int nsIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    assertThat(macroIdx).isGreaterThanOrEqualTo(0);
    assertThat(nsIdx).isGreaterThanOrEqualTo(0);

    long macroCount = countOfType(tokens.getData(), macroIdx);
    long nsCount = countOfType(tokens.getData(), nsIdx);

    // macros match non-region preproc tokens; namespace tokens match number of region directives
    assertThat(macroCount).isEqualTo(expectedMacro);
    assertThat(nsCount).isEqualTo(regionDirectives);
  }

  @Test
  void emitsOperatorsForPunctuators() {
    // given: code with many punctuators and operators
    String bsl = String.join("\n",
      "Процедура Опер()",
      "  Массив = Новый Массив();",
      "  Массив.Добавить(1 + 2);",
      "  Значение = Массив[0]?;",
      "  Если 1 <> 2 Тогда КонецЕсли;",
      "КонецПроцедуры"
    );

    DocumentContext dc = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier id = TestUtils.getTextDocumentIdentifier(dc.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(dc, new SemanticTokensParams(id));

    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);

    // count lexer operator/punctuator tokens
    Set<Integer> opTypes = Set.of(
      BSLLexer.LPAREN,
      BSLLexer.RPAREN,
      BSLLexer.LBRACK,
      BSLLexer.RBRACK,
      BSLLexer.COMMA,
      BSLLexer.SEMICOLON,
      BSLLexer.COLON,
      BSLLexer.DOT,
      BSLLexer.PLUS,
      BSLLexer.MINUS,
      BSLLexer.MUL,
      BSLLexer.QUOTIENT,
      BSLLexer.MODULO,
      BSLLexer.ASSIGN,
      BSLLexer.NOT_EQUAL,
      BSLLexer.LESS,
      BSLLexer.LESS_OR_EQUAL,
      BSLLexer.GREATER,
      BSLLexer.GREATER_OR_EQUAL,
      BSLLexer.QUESTION,
      BSLLexer.TILDA
    );

    long lexerOpCount = dc.getTokensFromDefaultChannel().stream()
      .map(Token::getType)
      .filter(opTypes::contains)
      .count();

    long operatorCount = countOfType(tokens.getData(), operatorIdx);

    // 1:1 mapping of lexer operator tokens to semantic Operator tokens
    assertThat(operatorCount).isEqualTo(lexerOpCount);
  }

  @Test
  void annotationWithoutParams_isDecoratorOnly() {
    // given
    String annotation = "&НаКлиенте";
    String bsl = String.join("\n",
      annotation,
      "Процедура Тест()",
      "КонецПроцедуры"
    );

    DocumentContext dc = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier id = TestUtils.getTextDocumentIdentifier(dc.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(dc, new SemanticTokensParams(id));

    int decoratorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    assertThat(decoratorIdx).isGreaterThanOrEqualTo(0);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> decoded = decode(tokens.getData());

    // then: on line 0 we should have exactly one Decorator token: merged '&НаКлиенте'
    List<DecodedToken> line0 = decoded.stream().filter(t -> t.line == 0).toList();
    long decoratorsOnLine0 = line0.stream().filter(t -> t.type == decoratorIdx).count();
    assertThat(decoratorsOnLine0).isEqualTo(1);

    // and no operators or strings on that line
    long operatorsOnLine0 = line0.stream().filter(t -> t.type == operatorIdx).count();
    assertThat(operatorsOnLine0).isEqualTo(0);
  }

  @Test
  void annotationWithStringParam_tokenizesNameParenAndString() {
    // given
    String bsl = String.join("\n",
      "&Перед(\"Строка\")",
      "Процедура Тест()",
      "КонецПроцедуры"
    );

    DocumentContext dc = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier id = TestUtils.getTextDocumentIdentifier(dc.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(dc, new SemanticTokensParams(id));

    int decoratorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    assertThat(decoratorIdx).isGreaterThanOrEqualTo(0);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);
    assertThat(stringIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> line0 = decode(tokens.getData()).stream().filter(t -> t.line == 0).toList();

    // one decorator on line 0: merged '&Перед'
    assertThat(line0.stream().filter(t -> t.type == decoratorIdx).count()).isEqualTo(1);

    // operators present for parentheses
    assertThat(line0.stream().filter(t -> t.type == operatorIdx).count()).isGreaterThanOrEqualTo(2);

    // string present
    assertThat(line0.stream().filter(t -> t.type == stringIdx).count()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void customAnnotationWithNamedStringParam_marksIdentifierAsParameter() {
    // given
    String bsl = String.join("\n",
      "&КастомнаяАннотация(Значение = \"Параметр\")",
      "Процедура Тест()",
      "КонецПроцедуры"
    );

    DocumentContext dc = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier id = TestUtils.getTextDocumentIdentifier(dc.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(dc, new SemanticTokensParams(id));

    int decoratorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    int paramIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);

    assertThat(decoratorIdx).isGreaterThanOrEqualTo(0);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);
    assertThat(stringIdx).isGreaterThanOrEqualTo(0);
    assertThat(paramIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> line0 = decode(tokens.getData()).stream().filter(t -> t.line == 0).toList();

    // one decorator: merged '&КастомнаяАннотация'
    assertThat(line0.stream().filter(t -> t.type == decoratorIdx).count()).isEqualTo(1);

    // operators for '(' ')' and '='
    assertThat(line0.stream().filter(t -> t.type == operatorIdx).count()).isGreaterThanOrEqualTo(3);

    // parameter identifier 'Значение'
    assertThat(line0.stream().filter(t -> t.type == paramIdx).count()).isGreaterThanOrEqualTo(1);

    // string literal
    assertThat(line0.stream().filter(t -> t.type == stringIdx).count()).isGreaterThanOrEqualTo(1);
  }

  // helpers
  private record DecodedToken(int line, int start, int length, int type, int modifiers) {}

  private List<DecodedToken> decode(List<Integer> data) {
    List<DecodedToken> out = new ArrayList<>();
    int line = 0;
    int start = 0;
    for (int i = 0; i + 4 < data.size(); i += 5) {
      int dLine = data.get(i);
      int dStart = data.get(i + 1);
      int length = data.get(i + 2);
      int type = data.get(i + 3);
      int mods = data.get(i + 4);
      line = line + dLine;
      start = (dLine == 0) ? start + dStart : dStart;
      out.add(new DecodedToken(line, start, length, type, mods));
    }
    return out;
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
