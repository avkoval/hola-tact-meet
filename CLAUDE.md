# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Backend
- Clojure/Ring

## Templates (Selmer)
- Please make sure to understand the Selmer syntax and its differences from Django templates or Jinja. For example
  variable comparision in `if` tags must have spaces around `=`, example: `{% if user.access-level = "admin" %}Yes{% endif %}`

## Git
- Please do not make any commits, I will do it myself

## Editor
- I am using emacs and emacsclient so in general case you can call emacsclient to show diffs for example (although 
  it is not necessary for most cases)

## Frontend
- Bulma CSS framework
- You can use free version of Font Awesome for icons
- Project uses https://data-star.dev for Frontend. If your knoweledge database does not include this site, please
  make code samples in plain Javascript, which I will later rewrite as data-star tags.

## Database
- Datomic. Most of access functions and schemas are in hola-tact-meet/src/ok/hola_tact_meet/db.clj
