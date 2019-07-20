#Using of the deprecated method "Find"
Method "Find" is deprecated. Use "StrFind" instead.

####Noncompliant
```BSL
If Find(Collaborator.Name, "Boris") > 0 Then
    
EndIf; 
```


####Compliant
```BSL
If StrFind(Collaborator.Name, "Boris") > 0 Then
    
EndIf; 
```

