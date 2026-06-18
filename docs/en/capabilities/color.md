# Colors: preview and picker

Color preview for `Новый Цвет(...)` and `WebЦвета.*`. Clicking the swatch opens the picker — choosing a color updates the code. Web colors convert to/from the RGB constructor representation.

**Shortcut:** `automatic (click the swatch to open the picker)`

[← All features](index.md)

## Color decorator and picker

The cursor moves to a line with `Новый Цвет(...)` in `demo.bsl`. To the left of the constructor the editor draws a swatch showing the actual color of the value.

![color-01](https://github.com/user-attachments/assets/b6705e67-f039-4ad4-bf1f-e4e9b57179b1)

## Interactive color picker updates the code

In `colorweb.bsl` the user clicks the swatch next to a color definition, opening the interactive color picker. Picking a different color in the palette immediately updates the color arguments right in the code.

![color-02-picker](https://github.com/user-attachments/assets/44e7279d-b927-4144-9961-f6a61bd817f2)

## Convert a web color to its RGB representation

The cursor is on a web color (`WebЦвета.*`) in `colorweb.bsl`, and the user converts its representation via the picker. The web color turns into the `Новый Цвет(...)` RGB constructor while keeping the exact same color, and the swatch stays unchanged.

![color-03-webcolor](https://github.com/user-attachments/assets/44bb2a13-6d67-4ca2-94bc-70866d5c7b90)
