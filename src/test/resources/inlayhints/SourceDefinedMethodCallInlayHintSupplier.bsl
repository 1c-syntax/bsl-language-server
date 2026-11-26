
&AtClient
Procedure Player1HealthPlus1(Command)
	ChangeHealth(Player1, Health1, 1);
EndProcedure

&AtClient
Procedure Player1HealthPlus5(Command)
	ChangeHealth(Player1, Health1, 5);
EndProcedure

&AtClient
Procedure Player1HealthMinus1(Command)
	ChangeHealth(Player1, Health1, -1);
EndProcedure

&AtClient
Procedure Player1HealthMinus5(Command)
	ChangeHealth(Player1, Health1, -5);
EndProcedure

&AtClient
Procedure Player2HealthPlus1(Command)
	ChangeHealth(Player2, Health2, 1);
EndProcedure

&AtClient
Procedure Player2HealthPlus5(Command)
	ChangeHealth(Player2, Health2, 5);
EndProcedure

&AtClient
Procedure Player2HealthMinus1(Command)
	ChangeHealth(Player2, Health2, -1);
EndProcedure

&AtClient
Procedure Player2HealthMinus5(Command)
	ChangeHealth(Player2, Health2, -5);
EndProcedure

&AtServer
Procedure OnCreateAtServer(Cancel, StandardProcessing)
	NewGameAtServer(True);
EndProcedure

&AtClient
Procedure NewGame(Command)
	NewGameAtServer();
EndProcedure

&AtServer
Procedure NewGameAtServer(FirstTime = False)

	Health1 = 20;
	Health2 = 20;

	If FirstTime Then
		Player1 = Catalogs.Players.Player1;
		Player2 = Catalogs.Players.Player2;

		Deck1 = Catalogs.Decks.DefaultDeck;
		Deck2 = Catalogs.Decks.DefaultDeck;
	EndIf;

	// TODO: Move GUI from logic
	ThisObject.ChildItems.PlayerWon.Visible = False;
	ThisObject.ChildItems.Buttons.Enabled = True;

EndProcedure

&AtClient
Procedure ChangeHealth(Player, PlayersHealth, Amount)

	PlayersHealth = PlayersHealth + Amount;

	If PlayersHealth <= 0 Then

		// TODO: rewrite
		PlayerWon = ?(Player = Player1, Player2, Player1);

		// TODO: Move GUI from logic
		ThisObject.ChildItems.PlayerWon.Visible = True;
		ThisObject.ChildItems.PlayerWon.Title = String(PlayerWon) + " won!";
		ThisObject.ChildItems.Buttons.Enabled = False;

		SaveGameStat(PlayerWon);

	EndIf;

EndProcedure

&AtServer
Procedure SaveGameStat(PlayerWon)

	NewRecord = InformationRegisters.GameStats.CreateRecordManager();
	NewRecord.Date = CurrentDate();
	NewRecord.Player1 = Player1;
	NewRecord.Player2 = Player2;
	NewRecord.Deck1 = Deck1;
	NewRecord.Deck2 = Deck2;
	NewRecord.Won = PlayerWon;

	NewRecord.Write();

EndProcedure
