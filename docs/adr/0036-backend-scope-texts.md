# 0036 Scope texts of connected clients come from Backend

**Status.** Accepted (2026-10-22). Amends [0028](0028-strings-and-icons.md) for one kind of text.

**Decision.** The titles and descriptions of a connection's Widgets scopes (`sport`, `friends:read`, ...) are not in
the app's catalog. Backend sends them in every answer that lists scopes (`ScopeInfo {id, title, description}`,
Backend's `connections.md`), localized by the request's `Accept-Language`; the app sends its UI language on every
Backend request (`APP_LANGUAGE`, `BackendClient`'s `language`) and shows the texts as `UiText.Dynamic`. The app uses
`id` only to send a choice back; a scope it cannot name (`ConnectionScope.UNKNOWN`) is shown read-only. Every other
text of the feature stays in `strings_connections.xml`.

**Why.** Backend owns the scope list and what each scope allows; the agent, itmo-mcp and the web show the same
texts. A new scope then needs no app release, and an older app shows every requested scope instead of dropping the
unknown ones before the user approves a pairing.

**Consequence.** These texts are not in the `.xcstrings` tables and not checked by `checkStringCatalog`; their
wording is reviewed in Backend (`messages/scopes_ru.properties`). Only `ru` exists, and it is Backend's fallback.

**Revisit when.** The app gets a second UI language (it then sends the platform's chosen localization), or scope
texts need formatting the app cannot show as plain text.
