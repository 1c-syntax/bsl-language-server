# "goto" statement should not be used

```goto``` is an unstructured control flow statement. It makes code less readable and maintainable.
Structured control flow statements such as ```if```, ```for```, ```while```, ```continue``` or ```break```
 should be used instead.
 
 Bad
 ```bsl
 i = 0;
  ~loop: Message("i = " + i);
  i = i + 1;
  
  If i < 10 Then
  
       Goto ~Loop;
  
  EndIf;
 ```
 
Good
```bsl
For i = 0 to 10 Do
 
    Message("i = " + i);
 
EndDo;
```
