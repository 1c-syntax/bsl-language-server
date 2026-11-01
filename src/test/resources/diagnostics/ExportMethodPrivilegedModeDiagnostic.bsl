
Procedure DangerousEnglish() Export
    SetPrivilegedMode(True);
EndProcedure

Procedure SafeLocal()
    SetPrivilegedMode(True);
EndProcedure

Procedure SafeFalse() Export
    SetPrivilegedMode(False);
EndProcedure

Procedure DangerousRussian() Export
    УстановитьПривилегированныйРежим(Истина);
EndProcedure

Procedure SafeVariable() Export
    Value = True;
    SetPrivilegedMode(Value);
EndProcedure

Procedure DangerousParentheses() Export
    SetPrivilegedMode((True));
EndProcedure

Procedure SafeParenthesizedFalse() Export
    SetPrivilegedMode((False));
EndProcedure
