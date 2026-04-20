package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@SpringBootTest
class DebugQueryComputerTest {

  @Test
  void debugTokens() throws IOException {
    DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/QueryComputerDateTimeTest.bsl");

    var sb = new StringBuilder();
    var visitor = new BSLParserBaseVisitor<ParseTree>() {
      @Override
      public ParseTree visitString(BSLParser.StringContext ctx) {
        sb.append("=== StringContext: text length=" + ctx.getText().length() + "\n");
        List<Token> tokens = ctx.getTokens();
        sb.append("  Token count: " + tokens.size() + "\n");
        for (int i = 0; i < tokens.size(); i++) {
          Token token = tokens.get(i);
          sb.append("  Token[" + i + "]: line=" + token.getLine() 
            + " charPos=" + token.getCharPositionInLine() 
            + " type=" + token.getType()
            + " text='" + token.getText().replace("\n", "\\n").replace("\r", "\\r") + "'\n");
        }
        return ctx;
      }
    };
    visitor.visitFile(documentContext.getAst());
    
    try (FileWriter fw = new FileWriter("/tmp/debug_tokens.txt")) {
      fw.write(sb.toString());
    }
  }
}
