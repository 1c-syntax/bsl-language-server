# Unary Plus sign in string concatenation (UnaryPlusInConcatenation)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When concatenating strings, a developer may mistakenly write code String + + String2, i.e. the second plus, the platform recognizes as unary and tries to cast to a number, which in most cases will lead to a runtime exception
