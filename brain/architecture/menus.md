# Menus

`Menu` is the glass dropdown under the top bar: a stack of `Page`s. A page is either a list of
`Row`s or a question with two buttons.

## Rows

- `Row.action(label, runnable)`, `Row.sub(label, supplier)` (pushes a page), `Row.choice(label,
  values, value, onChoice)` (cycles), `Row.note(label)` (disabled text).
- Up/Down (`move`) skip notes and roll over from the last row to the first and back.
- Right acts as OK (`right`), Left as Back (`left`); on a choice or a question they change the value
  or move between the buttons.

## Refreshing a page in place

`showing(page)` tells whether a page is on screen; `swap(page)` redraws it in place keeping the
cursor. The update row uses this to follow the updater's state.

## Who builds the pages

`LumenSettings` (Settings: system settings or Lumen's groups, Advanced, Spooky doot) and `Inputs`
(Live TV and HDMI). Pages are built fresh on each push, so they always reflect the preferences.

## Sources

- `lumen/src/com/arzaroth/lumen/Menu.java`, `LumenSettings.java`, `Inputs.java`
- `lumen/test/com/arzaroth/lumen/MenuTest.java`, `SettingsTest.java`
