# Adding "Quick Fixes"

"Quick fix" is an action on the code that modifies the text in the editor according to the selected menu item. For example: “Delete commented-out code” allows one-click removal of the code found by the diagnostics “Modules should not contain commented-out code”.

## Developing

Quick fixes are provided by classes that implement the QuickFixProvider interface. Currently, only diagnostic classes can be such classes.

To add a quick fix to any diagnostics, implement the QuickFixProvider interface in it. 
