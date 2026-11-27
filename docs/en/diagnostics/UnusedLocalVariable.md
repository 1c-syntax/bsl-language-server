# Unused local variable (UnusedLocalVariable)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Unused local variables should be removed

If a local variable is declared but not used, it is dead code and should be removed.
Doing so will improve maintainability because developers will not wonder what the variable is used for.

In form modules, the diagnostic respects scope. Form attributes and properties accessible
by name in a method with form context are excluded. An explicit local declaration can
shadow a form attribute. In a method without form context, assignment to an unqualified
name can declare a local variable with the same name as a form attribute.
