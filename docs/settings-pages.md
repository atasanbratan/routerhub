# Settings pages

The router equivalent of Tachiyomi's `ConfigurableSource`. An extension that lists `Capability.SETTINGS`
implements three calls on `RouterFirmware`:

| Call | Purpose |
|---|---|
| `listSettingsPages()` | Titles, groups and summaries; the app shows them on the router screen |
| `readSettings(pageId)` | A `SettingsPage`: typed fields holding the router's current values |
| `writeSettings(pageId, changes)` | Applies only the keys the user changed |

The app renders every page as a form, validates ranges and lengths locally, asks for confirmation (showing
the page's `applyWarning`) and sends back **only what changed**. A new page therefore needs no change in
the app at all.

## Field types

Values travel as strings to keep the binary contract simple: `"true"` / `"false"` for toggles, the option
value for choices, decimal digits for numbers.

| Field | What it is |
|---|---|
| `Info` | A read-only value, with an optional summary |
| `Toggle` | An on/off switch |
| `Text` | A line of text; optional `maxLength` and keyboard (`TEXT`, `NUMBER`, `IP_ADDRESS`) |
| `Secret` | A write-only password. It starts empty, an empty submission means "keep the current one", and the app can ask the user to type it twice |
| `Number` | A whole number with `min`, `max` and a unit |
| `Choice` | One of several `ChoiceOption(value, label)` |
| `Table` | An editable (or read-only) list of rows |

## Lists

A `Table` has typed columns (`TableColumn`) and rows of strings. The app lets the user delete rows and add
new ones from a form built from the columns, and submits the **complete wanted list**, encoded with
`TableCodec`. The extension compares it with what the router currently holds and issues the router's own
add and remove requests, so the app never needs to know how a particular router edits its lists. Unchanged
rows are matched by value, which works whatever normalisation the extension applies, because the app echoes
back exactly what a read returned.

Column kinds are `TEXT`, `NUMBER`, `CHOICE`, `IP_ADDRESS`, `MAC_ADDRESS` and `SECRET`. A `SECRET` column is
write-only: the add form asks for it, masked, and a row never shows it.

## Rules the contract enforces

- **Secrets never travel to the app.** A `Secret` field has no value property. An extension may read a
  stored secret internally, only when it has to resend it, and must not return it.
- **Writes are never replayed.** Reads retry once after a lost session; a write that may have applied is
  reported and re-read instead.
- **Rejections come before any request.** Throw `InvalidSettingException(key, message)` and the app shows
  the message under the field with that key (switches and choices included). Validate everything before
  the first request, so a bad value can never leave a page half changed.
- A page can name `loginPasswordKey`: after that `Secret` is saved, the app updates the login password it
  stores for the router.

## Tips from a real router

The [TP-Link extension](https://github.com/atasanbratan/routerhub-extensions) taught us a few things worth
copying:

- **Many firmwares report a refusal as an ordinary page** (HTTP 200 with an "Error" title). Without a check,
  a rejected save looks exactly like a successful one. Detect it and throw `CommandFailedException` with the
  router's reason.
- **Prefer safeguards to surprises.** Refuse changes that would lock the user out (an "allow only" filter with
  an empty list), expose the router (remote administration with a default password) or leave a network open.
- **Read back after writing.** The app reloads the page after a save, so the user sees what the router really
  holds, not what was requested.
