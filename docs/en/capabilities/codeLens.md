# Code lens

Informational lines above procedures: cognitive and cyclomatic complexity, test run and coverage.

**Shortcut:** `automatic`

[← All features](index.md)

## CodeLens: method complexity

The cursor sits on a method declaration in a `.bsl` module, and a CodeLens line appears above it. The lens shows the method's metrics — cognitive and cyclomatic complexity.

![codeLens-01](https://github.com/user-attachments/assets/870f28f1-6754-4e3d-aa7a-654fae822cf8)

## CodeLens: navigate to bean definition (Autumn DI)

In a OneScript class, a «go to definition» lens is shown above the `&Пластилин` Autumn DI injection point and gets clicked. The editor navigates to the bean class (`&Желудь`) that provides the injected dependency.

![codeLens-02-autumn-injection](https://github.com/user-attachments/assets/6abe1a62-387a-4be8-9e55-b444e0c38f41)

## CodeLens: bean usages (Autumn DI)

On a `&Желудь` bean class declaration (Autumn DI), a lens with the injection-points count is clicked. A peek window opens listing every place where this bean is injected.

![codeLens-03-autumn-usages](https://github.com/user-attachments/assets/ab21b5d8-feb5-429e-a265-0fe9567ed51a)

## CodeLens: run OneScript tests

Above the `&Тест` methods in a OneScript module, «Run test» and «Run all tests» lenses are shown, and one of them is clicked. A terminal opens and executes the test-runner command.

![codeLens-04-run-tests](https://github.com/user-attachments/assets/2afeda73-513a-408d-9b1b-21a4abf06c58)

## CodeLens: complexity counters on click

In `.bsl` code, the «Cognitive complexity» lens above a method is clicked. Inline complexity counters appear in the code: `+1` on branches and `+2 (nesting=1)` on nested constructs.

![codeLens-05-complexity-toggle](https://github.com/user-attachments/assets/16904a15-e131-4b9e-9025-16f0c59b2ef1)
