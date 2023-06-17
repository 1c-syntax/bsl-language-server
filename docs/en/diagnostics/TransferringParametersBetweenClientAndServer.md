# Transferring parameters between client and server (TransferringParametersBetweenClientAndServer)

<!-- Blocks above are included automatically, do not touch -->
## Diagnostic description
<!-- Diagnostic description is filled in manually. It is necessary to describe the meaning an understandable language -->

When transferring control from the client to the server (and vice versa), copies of the parameters are always transferred.

- When a server procedure or function is called from the client, a copy of the actual parameter is created and this copy is passed to the server side.

- When control is returned from the server to the client, a copy of the formal parameter (which was handled in the called procedure or function) is also created for transfer back to the client.

If a formal parameter is specified with the Val modifier, then the value of the parameter will only be passed when the procedure or function is called and will not be passed back when control returns to the client.

Possible scenarios:

- If a structure with nested structures is passed from the client method to the server method without the Val modifier, and the parameter does not change inside the server method, then a copy of this structure with all its attachments will be transferred from the server when control returns.
- In the case of transferring a flat collection that does not change, for example, an array, a copy of this collection will also be returned from the server to the client in vain.

As a result, the absence of the Val modifier in client-server interaction can lead to performance degradation and unnecessary/unnecessary load both by the client and the server.

The current rule finds server methods that execute from client methods and throws remarks on parameters without the Val modifier that are not set to a value.

## Examples 
<!-- This section contains examples for which the diagnostics work, and you can also give an example of how to fix the situation -->
 
1. An example with passing parameters from the client to the server without "Val" and with "Val"
```bsl
&AtServerNoContext
Procedure TransferParametersToServer(Param1, Val ParamWithVal, Collection, Val CollectionWithVal)

Param1 = "Changed1";
ParamWithVal = "Changed2";
Collection.Insert("Key1", "Changed1");
CollectionWithVal.Insert("Key2", "Changed2");

EndProcedure

&AtClient
Procedure PassingParameters(Command)

Param1 = "Initial1";
ParamWithVal = "Initial2";
Collection = New Structure("Key1", "Source1");
CollectionWithVal = New Structure("Key2", "Source2");

PassingParametersToServer(Param1, ParamWithVal, Collection, CollectionWithVal);

Template = "after server %1 = <%2>";

Message(StrTemplate(Template, "Param1", Param1));
Message(StrTemplate(Template, "ParamWithVal", ParamWithVal));
Message(StrTemplate(Template, "Collection.Key1", Collection.Key1));
Message(StrTemplate(Template, "CollectionWithVal.Key2", CollectionWithVal.Key2));

EndProcedure

```
This code, when executed, will show the following result
```
after server Param1= <Changed1>
after server ParamWithVal= <Initial2>
after server Collection.Key1 =  <Changed1>
after server CollectionWithVal.Key2= <Initial2>
```
It can be seen that all parameters passed through Value do not change their values after execution, incl. and values within collections.

2. An example of inaccurate parameter transfer
```bsl
&AtClient
Procedure UserGroupsDragNDropFinish(Response, AdditionalParameters)

If Response = DialogReturnCode.No Then
Return;
EndIf;

MessageToUser = MoveUserToNewGroup(
AdditionalOptions.DragNDropOptions,
AdditionalParameters.String,
AdditionalParameters.Move
);

EndProcedure

// input parameters ArrayUsers and other parameters do not change
// and therefore there is no point in additionally returning them from the server
&AtServer
Function MoveUserToNewGroup(ArrayUsers, NewGroupOwner, Move)

If NewGroupOwner = Undefined Then
Return Undefined;
EndIf;

CurrentGroupOwner = Items.UserGroups.CurrentRow;
MessageToUser = UsersService.MovingUserToNewGroup(
ArrayUsers, CurrentGroupOwner, NewGroupOwner, Move);

Items.UsersList.Update();
Items.UserGroups.Refresh();

Return MessageToUser;

EndFunction
```
## Sources 
<!-- It is necessary to provide links to all sources from which information was obtained to create diagnostics -->
<!-- Sample sources

* Source: [Standard: Module texts](https://its.1c.ru/db/v8std#content:456:hdoc) 
* Useful information: [Refusal to use modal windows](https://its.1c.ru/db/metod8dev#content:5272:hdoc) 
* Source: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Article on 1C:ITS - Call with transfer of control from client to server](https://its.1c.ru/db/v8318doc#bookmark:dev:TI000000153)
