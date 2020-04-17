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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class CommonModuleNameClientServerDiagnosticTest extends AbstractDiagnosticTest<CommonModuleNameClientServerDiagnostic> {
  CommonModuleNameClientServerDiagnosticTest() {
    super(CommonModuleNameClientServerDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";


  @SneakyThrows
  @Test
  void test() {

    Path path = Absolute.path(PATH_TO_METADATA);
    var serverContext = spy(new ServerContext(path));
    var configuration = spy(serverContext.getConfiguration());

    Path testFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();

    var documentContext = spy(new DocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      serverContext
    ));

    when(documentContext.getServerContext()).thenReturn(serverContext);

    when(serverContext.getConfiguration()).thenReturn(configuration);
    var modules = spy(configuration.getModulesByURI());

    when(configuration.getModulesByURI()).thenReturn(modules);

    CommonModule myModule = (CommonModule) modules.get(documentContext.getUri());

    var spymodule = spy(myModule);
    when(spymodule.isServer()).thenReturn(Boolean.TRUE);
    when(spymodule.isClientManagedApplication()).thenReturn(Boolean.TRUE);
    when(modules.get(documentContext.getUri())).thenReturn(spymodule);


    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 0, 1);

  }

}
