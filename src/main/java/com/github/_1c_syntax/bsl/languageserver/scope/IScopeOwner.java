package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.mdo.MDObject;
import lombok.RequiredArgsConstructor;

public interface IScopeOwner {

  static IScopeOwner create(MDObject object, IScope scope) {
    return new MDObjectScopeOwner(object, scope);
  }

  IScope getScope();

  @RequiredArgsConstructor
  class MDObjectScopeOwner implements IScopeOwner {
    private final MDObject owner;
    private final IScope scope;

    @Override
    public IScope getScope() {
      return scope;
    }
  }
}
