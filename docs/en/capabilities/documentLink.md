# Document links (hyperlinks)

Clickable links right in the module text: `См.`/`See` references in doc comments jump to the mentioned method or object; URLs in comments open in the browser; and optionally (**off by default**, `documentLink.showDiagnosticDescription`) the diagnosed range itself becomes a link to the diagnostic's documentation.

**Shortcut:** `Ctrl+Click the link`

[← All features](index.md)

## documentLink: link to diagnostic documentation

In a diagnostic message, the rule code (`256`) is highlighted as a clickable link. Clicking it opens the documentation for that diagnostic.

![documentLink-02-diagnostic-code](https://github.com/user-attachments/assets/755b32fb-0221-4df2-a14f-2b6ae4349922)

## documentLink: clickable «See» reference to a method

In a doc comment, the `См.` (See) reference to method `ВычислитьИтог` is highlighted as clickable. `Ctrl+Click` navigates the editor to the referenced method's declaration.

![seeReference-01](https://github.com/user-attachments/assets/038f33f2-a6bd-43b5-a3af-998794bcc66c)
