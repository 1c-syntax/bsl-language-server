package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ObjectNameLengthDiagnosticTest extends AbstractDiagnosticTest<ObjectNameLengthDiagnostic> {
  private MDObjectBase module;
  private DocumentContext documentContext;

  ObjectNameLengthDiagnosticTest() {
    super(ObjectNameLengthDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @Test
  void test() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ОченьДлинноеИмяСправочникиКотороеВызываетПроблемыВРаботеАТакжеОшибкиВыгрузкиКонфигурации");

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 1);


  }

  @Test
  void testClientServer() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ShortName");

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(0);

  }

  @SneakyThrows
  void getDocumentContextFromFile() {

    Path path = Absolute.path(PATH_TO_METADATA);
    Path testFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();

    ServerContext serverContext = new ServerContext(path);
    var configuration = serverContext.getConfiguration();
    documentContext = spy(new DocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      serverContext
    ));


    module = spy(configuration.getModulesByObject().get(documentContext.getUri()));

  }
}
