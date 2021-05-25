package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ParentProcessWatcherTest {

  @InjectMocks
  private ParentProcessWatcher parentProcessWatcher;

  @Mock
  private LanguageServer languageServer;

  @Test
  void testParentProcessIsDead() {
    // given
    var params = new InitializeParams();
    params.setProcessId(-1);

    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);
    parentProcessWatcher.handleEvent(event);

    // when
    parentProcessWatcher.watch();

    // then
    verify(languageServer, times(1)).exit();
  }

  @Test
  void testParentProcessIsAlive() {
    // given
    var params = new InitializeParams();
    params.setProcessId((int) ProcessHandle.current().pid());

    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);
    parentProcessWatcher.handleEvent(event);

    // when
    parentProcessWatcher.watch();

    // then
    verify(languageServer, never()).exit();
  }

}