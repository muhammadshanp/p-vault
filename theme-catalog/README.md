# Theme catalogue

New looks for P Vault, without a new app build.

**Where it lives:** the file and its pictures go at the top of the website repository
(`muhammadshanp/p-vault-website`), so they are served as
`https://muhammadshanp.github.io/p-vault-website/themes.json`.
The app downloads that address when it starts and when **New themes → Check** is tapped.

**Adding a look**

1. Add an entry to `themes` in `themes.json` (see the fields below). Keep `id` unique and
   simple: letters, digits, dashes. The id is also the picture's file name.
2. If you want a background picture, put it next to `themes.json` (any size; the phone
   scales it, but about 700 × 1500 and under 200 KB keeps the download quick) and name it
   `<id>.webp` or `<id>.jpg`. Set `"picture": "https://muhammadshanp.github.io/p-vault-website/<id>.webp"`
   and give the picture an address in the app with `"scrim"` (0.1–0.9): higher means the
   picture is darker behind the text.
3. Publish the change. Anyone with special access taps **New themes → Check** and the look
   appears with a **New** badge. No release, no APK.

**Fields** (colours are `#RRGGBB`; anything left out is worked out from the rest)

| Field | Meaning |
| --- | --- |
| `id`, `label` | Saved name and the name shown in the list (both required) |
| `dark` | `true` for a dark look (light status-bar icons) |
| `primary`, `onPrimary` | Accent, and the colour of text on it |
| `primaryContainer`, `onPrimaryContainer` | Tinted boxes (selection bar, cards) |
| `secondary` | Second accent |
| `background`, `surface`, `surfaceVariant` | Page, cards, quiet fills |
| `onBackground`, `onSurfaceVariant` | Text and quiet text |
| `outline`, `error` | Hairlines and error red |
| `picture`, `scrim` | Optional background picture and how dark it sits |
| `heroArt` | `true` to put the picture inside the big Home card, on its right side |
| `glass` | `true` for slightly see-through cards (lets the picture show through) |
| `tileTints` | `true` to give the four Home tiles a wash of their own colour |
| `allowed` | Optional list of email addresses. Only those accounts see the look; leave it out for everyone with special access. |

`minVersion` is the lowest P Vault version that can show the file. Raise it only if you use
something newer builds understand; older builds then keep their last copy.

**Rules of thumb**

- A look is only shown to people with special access, and a look with `allowed` only to
  those addresses. It is not strong protection: the file is public.
- Deleting an entry removes the look at the next check; anyone using it drops back to Dark.
- One picture per look, and only pictures — the app does not download video or fonts.
