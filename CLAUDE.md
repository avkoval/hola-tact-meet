# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Backend
- Clojure/Ring

## Templates (Selmer)
- Location: resources/templates/*.html
- Please make sure to understand the Selmer syntax and its differences from Django templates or Jinja.
  - For example variable comparision in `if` tags must have spaces around `=`, example: `{% if user.access-level = "admin" %}Yes{% endif %}`
  - Selmer template `{%if` does not have **Not Eqeual** operator like `!=` - please use `{% ifequal` or `{% ifnotequal` conditions instead

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
- Datomic. Most of access functions and schemas are in "src/ok/hola_tact_meet/db.clj"
