# Unused local variable (UnusedLocalVariable)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Unused local variables should be removed

If a local variable is declared but not used, it is dead code and should be removed.
Doing so will improve maintainability because developers will not wonder what the variable is used for.

In object modules, the diagnostic uses name resolution. Object attributes, including standard attributes and tabular sections, are excluded. An explicitly declared local variable with the same name is still checked.
