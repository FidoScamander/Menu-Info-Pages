# MenuInfoPages Free 1.3.0

**MenuInfoPages Free** is a lightweight JSON-driven information-page system for Hytale Server. Create guides, rules, onboarding screens and information windows without writing Java code.

Version **1.3.0** keeps the compact Free-edition page model while adding **ItemCommands** and the server-side UI performance improvements developed for the Full edition.

## Compatibility

- Hytale Server `0.6.x`
- Manifest range: `>=0.6.0 <0.7.0`

## Free edition features

- Native Hytale-style information window
- One direct player command for every JSON page
- Optional per-page `permission`
- Rich text: bold, italic, monospace and hex colors
- Up to **5 top images**
- Up to **5 sections**, each with title, text and one optional image
- Up to **5 bottom action buttons**
- Commands executed as player or console
- `{player}` and `{uuid}` command placeholders
- `/infopages newpage <name> <command>`
- `/infopages reload`
- AutoOpen modes: `once`, `always`, `dismissible`
- Local PNG images and cached remote HTTP/HTTPS PNG images
- Optional live image reload through `reload.yml`
- **ItemCommands** for turning vanilla or modded items into command/menu items
- Shared server-side image-size and rich-text caches with startup prewarming

## New in 1.3.0 - ItemCommands

A new file is created at:

```text
mods/MenuInfoPages/config.json
```

On a fresh installation it contains a working self-contained **Demo Pages** item. The demo item opens `/guide` with both left and right click.

Example:

```json
{
  "itemCommands": {
    "enabled": true,
    "items": [
      {
        "id": "demo",
        "sourceItem": "MenuInfoPages_Demo_Grimoire_Source",
        "name": "Demo Pages",
        "quality": "",
        "leftClickCommand": "guide",
        "rightClickCommand": "guide"
      }
    ]
  }
}
```

### ItemCommands fields

- `id` - generated item suffix. The public item ID is `MenuInfoPages_<ID>`.
- `sourceItem` - vanilla Hytale item, item from another installed asset pack, or the bundled demo source.
- `name` - optional display-name override. Leave empty to preserve the source item name.
- `quality` - optional quality override. Leave empty to preserve the source item quality.
- `leftClickCommand` - command executed by the player on left click.
- `rightClickCommand` - command executed by the player on right click.

Commands may be written with or without `/`. If one click should do nothing, leave that command empty.

MenuInfoPages copies the source item into its own generated item and automatically brings over required quality assets, referenced Common resources and localization dependencies when available. Generated item assets are tracked and obsolete managed files are cleaned automatically.

**Structural ItemCommands changes require a server restart.** This includes adding/removing items, changing `sourceItem`, item IDs, model/quality dependencies or click interactions.

## Performance improvements in 1.3.0

The Free edition now uses shared **server-side** caches instead of repeating avoidable work for every player opening a page:

- image dimensions are cached instead of decoding the same PNG repeatedly;
- rich-text output is cached by source text;
- configured page text and image sizes are prewarmed at startup;
- `/infopages reload` clears and rebuilds the UI caches so old page data does not accumulate.

The cache is shared by all players and lives in the server JVM. It is not a per-player cache.

## Installation / update

1. Stop the server.
2. Remove the previous MenuInfoPages Free JAR.
3. Place `MenuInfoPages-Free-1.3.0.jar` in `mods/`.
4. Start the server.
5. Existing pages, AutoOpen state and `reload.yml` are preserved.
6. `config.json` is created/migrated only when the `itemCommands` block is missing; existing ItemCommands settings are not overwritten.

MenuInfoPages uses:

```text
mods/MenuInfoPages/
├── README.txt
├── config.json
├── reload.yml
├── autoopen-state.json        # created when needed
├── pages/
│   └── example.json
├── images/
└── cache/
    ├── itemcommands-managed-files.txt
    └── remote-assets/
```

## Demo page / AutoOpen reminder

On a clean Free installation, `pages/example.json` intentionally has AutoOpen enabled so the feature is immediately testable. After testing, set:

```json
"autoOpen": {
  "enabled": false
}
```

if you no longer want the example page to open automatically.

The `/guide` demo remains permission-protected with:

```text
newt.infopages.newpage
```

## Commands and permissions

```text
/infopages reload
```
Permission: `newt.infopages.reload`

```text
/infopages newpage <name> <command>
```
Permission: `newt.infopages.newpage`

## Page format

The Free edition intentionally keeps its compact 1.x page format and limits. Existing page JSON remains compatible.

```json
{
  "autoOpen": {
    "enabled": false,
    "mode": "dismissible",
    "version": 1,
    "worlds": [],
    "delayMs": 1500
  },
  "command": "guide",
  "permission": "newt.infopages.newpage",
  "description": "Open the MenuInfoPages example guide",
  "title": "MENU INFO PAGES",
  "intro": "Create clean JSON-driven information pages for your server.",
  "images": [
    "UI/Custom/Pages/Images/menuinfopages-example.png"
  ],
  "sections": [
    {
      "title": "YOUR SECTION",
      "text": "Add your information here.",
      "image": ""
    }
  ],
  "buttons": [
    {
      "text": "CLOSE",
      "command": "",
      "runAs": "player",
      "close": true,
      "style": "danger"
    }
  ],
  "footer": "Press Esc to close."
}
```

## Rich text

Supported in title, intro, section text and footer:

```text
{b}bold{/}
{i}italic{/}
{m}monospace{/}
{#54daf4}hex color{/}
```

## Images

Top-level and section images may use:

- a PNG in `mods/MenuInfoPages/images/`, referenced by filename;
- an existing UI asset path;
- a direct HTTP/HTTPS PNG URL, cached server-side.

## Full edition

The Free edition deliberately keeps the compact **5-image / 5-section / 5-button** model. The Full edition adds the larger multilingual menu system, lateral navigation, expanded sections/buttons, interactive images, advanced text alignment, additional placeholders and other extended menu features.
