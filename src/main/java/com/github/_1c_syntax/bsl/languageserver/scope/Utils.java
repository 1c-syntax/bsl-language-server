package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.mdo.ModuleOwner;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class Utils {

  public static Collection<MethodSymbol> getMethods(MDCommonModule module, ServerContext serverContext) {
    return getMethods(module.getModules().get(0).getUri(), serverContext);
  }

  public static Collection<MethodSymbol> getMethods(URI moduleUri, ServerContext serverContext) {
    var document = serverContext.getDocument(moduleUri);
    if (document == null) {
      return Collections.emptyList();
    }

    return document.getSymbolTree().getMethods();
  }

  public static Optional<MethodSymbol> getMethod(MDCommonModule module, ServerContext serverContext, String name) {
    return getMethod(module.getModules().get(0).getUri(), serverContext, name);
  }

  public static Optional<MethodSymbol> getMethod(URI moduleUri, ServerContext serverContext, String name) {
    var document = serverContext.getDocument(moduleUri);
    if (document == null) {
      return Optional.empty();
    }
    return document.getSymbolTree().getMethodSymbol(name);
  }

  public static URI getManagerModuleURI(ModuleOwner object, ServerContext serverContext) {
    var modules = serverContext.getConfiguration().getModulesByMDORef(object.getMdoReference().getMdoRef());
    return modules.get(ModuleType.ManagerModule);
  }
}
