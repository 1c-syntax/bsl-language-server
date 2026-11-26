# Workspace symbols

Quickly jump to any method or object across the whole project by name.

**Shortcut:** `Ctrl+T`

[← All features](index.md)

## Workspace symbol search

The user presses `Ctrl+T` and types `КурсВалюты` to search for a symbol across the whole project. The list instantly filters down to methods and objects with that name, letting you jump straight to the right one in any configuration module.

![workspaceSymbol-01](https://github.com/user-attachments/assets/c4635e17-b209-4fbd-8ef6-dea328c0413e)

## Workspace symbol search: CamelHumps

In workspace symbol search (`Ctrl+T`) the user types just the leading letters of each word — `ОбщНазн`. CamelHumps matching maps them to word boundaries and finds `ОбщегоНазначения` and its related symbols without typing the full name.

![workspaceSymbol-02-camelhumps](https://github.com/user-attachments/assets/ad7d9b9c-66ae-43d5-b41a-e49397932ef9)

## Workspace symbol search: substring and ranking

In workspace symbol search (`Ctrl+T`) the user enters the substring `значен`. All symbols containing that fragment anywhere in the name are found, and the results are ranked by match relevance.

![workspaceSymbol-03-substring](https://github.com/user-attachments/assets/7a9ef663-761b-4bfe-90b3-19fa373ba03d)

## Workspace symbol search: fuzzy abbreviation

The abbreviation `ЗначРекО` is typed into the `Ctrl+T` palette. Fuzzy workspace-symbol search finds methods like `ЗначенийРеквизитовОбъекта` — the letters `Знач`/`Рек`/`О` are matched as a subsequence and highlighted.

![workspaceSymbol-04-fuzzy](https://github.com/user-attachments/assets/fe6f9435-f55b-4f46-9c57-ccefab4b85fa)

---

[← Back: Document symbols / Outline](documentSymbol.md)  ·  [Next: Document highlight →](documentHighlight.md)
