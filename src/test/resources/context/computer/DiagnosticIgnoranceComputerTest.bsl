// BSLLS-off

Если Истина Тогда
	//а
	А = 0
КонецЕсли;

// BSLLS-on

// BSLLS:SemicolonPresence-выкл Проверка
Если Истина Тогда
	//а
	А = 0
КонецЕсли;
// BSLLS:SemicolonPresence-вкл

// BSLLS:SemicolonPresence-выкл
// BSLLS:SpaceAtStartComment-выкл Проверка вложенности
Если Истина Тогда
	//а
	А = 0
КонецЕсли;
// BSLLS:SpaceAtStartComment-вкл
// BSLLS:SemicolonPresence-вкл

Если Истина Тогда
	А = 0 // BSLLS:SemicolonPresence-выкл Проверка висячего комментария
КонецЕсли;
А = 0

// BSLLS:SpaceAtStartComment-выкл
//а;
