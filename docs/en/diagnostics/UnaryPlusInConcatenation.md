# Unary Plus sign in string concatenation

When concatenating string developer may accidentally write something like "String1 + + String2" in which platform will recognize second "+" as unary and try to convert string to number - and in most cases it will lead to runtime error. 
