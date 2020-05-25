package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
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

  @Test
  void testConfigure() {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxObjectNameLength", 10);
    diagnosticInstance.configure(configuration);

    getDocumentContextFromFile("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl");

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void testConfigureNegative() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxObjectNameLength", 90);
    diagnosticInstance.configure(configuration);

    getDocumentContextFromFile("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl");

    // given
    when(module.getName()).thenReturn("ОченьДлинноеИмяОбъектаКотороеВызываетПроблемыВРаботеАТакжеОшибкиВыгрузкиКонфигурации");

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Catalogs/Справочник1/Ext/ObjectModule.bsl",
    "Catalogs/Справочник1/Forms/ФормаВыбора/Ext/Form/Module.bsl",
    "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl"
  })
  void test(String modulePath) {

    getDocumentContextFromFile(modulePath);

    // given
    when(module.getName()).thenReturn("ОченьДлинноеИмяОбъектаКотороеВызываетПроблемыВРаботеАТакжеОшибкиВыгрузкиКонфигурации");

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void testNegative() {

    getDocumentContextFromFile("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl");

    // given
    when(module.getName()).thenReturn("Short");

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(0);
  }

  @SneakyThrows
  void getDocumentContextFromFile(String modulePath) {

    Path path = Absolute.path(PATH_TO_METADATA);
    Path testFile = Paths.get(PATH_TO_METADATA, modulePath).toAbsolutePath();

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
