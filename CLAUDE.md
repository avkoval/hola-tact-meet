# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Backend
- Clojure/Ring

## Templates (Selmer)
- Location: resources/templates/*.html
- Please make sure to understand the Selmer syntax and its differences from Django templates or Jinja.
  - For example variable comparision in `if` tags must have spaces around `=`, example: `{% if user.access-level = "admin" %}Yes{% endif %}`
  - Selmer template `{%if` does not have **Not Eqeual** operator like `!=` - please use `{% ifequal` or `{% ifnotequal` conditions instead
  - Selmer template does not support `in` for `{% if` statements, for example this won't work: if team.id in user-team-ids `{% if team.id in user-team-ids %}`

## Git
- Please do not make any commits, I will do it myself

## Editor
- I am using emacs and emacsclient so in general case you can call emacsclient to show diffs for example (although
  it is not necessary for most cases)

## Frontend
- Bulma CSS framework
- You can use free version of Font Awesome for icons
- Project uses https://data-star.dev for Frontend. If you can - provide code samples in Datastar syntax (data-*)
  using SSE and connect it to core.clj (there are already a few working examples of handling SSE calls), if you
  can't - skip it.

## Database
- **Schema** Schema in "src/ok/hola_tact_meet/schema.clj"
- **Datomic Client** (NOT Peer). Most of access functions are in "src/ok/hola_tact_meet/db.clj"
- **CRITICAL**: This project uses Datomic Client, which has different query syntax than Datomic Peer:
  - ✅ **CORRECT**: `[:find ?e]` (tuple queries)
  - ✅ **CORRECT**: `[:find ?e ?name]` (multiple tuple queries)
  - ❌ **INCORRECT**: `[:find ?e .]` (scalar queries - NOT supported in Client)
  - ❌ **INCORRECT**: `[:find [?e ...]]` (collection queries - NOT supported in Client)
  - **Always extract results**: Use `ffirst`, `mapv first`, etc. to extract from tuple results
  - **Example queries**:
    ```clojure
    ;; Get single value
    (ffirst (d/q '[:find ?e :where [?e :user/name "John"]] db))
    
    ;; Get multiple values
    (mapv first (d/q '[:find ?e :where [?e :user/name _]] db))
    ```
