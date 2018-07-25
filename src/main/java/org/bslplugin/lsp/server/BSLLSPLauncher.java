package org.bslplugin.lsp.server;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.InputStream;
import java.io.OutputStream;

public class BSLLSPLauncher {

  public static void main(String[] args) {

    InputStream in = System.in;
    OutputStream out = System.out;

    LanguageServer server = new BSLLanguageServer();
    Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);

    LanguageClient client = launcher.getRemoteProxy();
    ((LanguageClientAware)server).connect(client);

    launcher.startListening();
  }
}
