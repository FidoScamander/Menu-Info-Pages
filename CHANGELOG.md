# Changelog

## 1.3.0

- Added **ItemCommands** to the Free edition, allowing vanilla or modded Hytale items to execute separate left/right-click commands as the player.
- Added a self-contained **Demo Pages** ItemCommand on fresh installations.
- Added automatic source-item cloning with quality, Common resource and localization dependency copying plus managed cleanup of obsolete generated assets.
- Added shared server-side image-size and rich-text caches with startup prewarming.
- `/infopages reload` now clears and rebuilds the Free UI caches.
- Kept the existing Free-edition page format and limits unchanged: up to 5 top images, 5 sections and 5 bottom buttons.
- Removed temporary UI timing/profiling output used during 1.3.0 testing.
- Updated bundled/runtime documentation, generated example text, HTTP user agent and runtime asset-pack metadata to 1.3.0.
- Hytale compatibility remains `>=0.6.0 <0.7.0`.

## 1.2.6

- Updated the plugin and bundled runtime image-pack compatibility range to Hytale Server `>=0.6.0 <0.7.0`, including Hytale 0.6.1.
- Updated release metadata, generated example text, bundled/runtime documentation and internal version references from 1.2.5 to 1.2.6.
- Updated the remote-image HTTP user agent to `MenuInfoPages/1.2.6`.
- No gameplay, page, permission, AutoOpen, UI-limit or image-handling logic was changed from 1.2.5.

## 1.2.5

- Added optional per-page permissions through the `permission` JSON field; missing or empty values remain public for full backward compatibility.
- Enforced page permissions for direct page commands and AutoOpen opening.
- Kept AutoOpen conflict protection intentionally independent from permissions, matching the Full edition: multiple eligible AutoOpen pages for the same destination conflict even when they use different permissions.
- Rechecks the selected page permission after `delayMs` before the AutoOpen page is actually opened.
- Added localized permission-denied messages across all 25 bundled languages with regional client-language fallback.
- Protected the bundled `/guide` demo with `newt.infopages.newpage`, the same permission required to create new pages.
- Older generated `example.json` demo files without a permission field are migrated automatically without changing their current AutoOpen setting.
- `/infopages newpage` now creates templates with `"permission": ""`, keeping newly created custom pages public until an administrator assigns a permission.
- Updated the bundled example, README, runtime documentation and version references for this release.

## 1.2.3

- Fixed the top page-image distribution introduced in 1.2.2: visible header images now use equal flexible slots across the full available row instead of being grouped together in the center.
- The corrected layout works dynamically with 1 to 5 visible top images and preserves each image's original size/aspect handling.
- Kept the 1.2.2 regional-language fallbacks, main-content centering, section limits and button limits unchanged.
- Updated bundled/runtime version references to 1.2.3.

## 1.2.2

- Added robust regional-language fallback for the localized dismissible **Do not show again** label across all 25 bundled languages (for example `it-CH`/`it_IT` -> `it-IT`, `de-CH` -> `de-DE`, `nb-NO`/`nn-NO` -> `no-NO`, and Traditional Chinese variants -> `zh-TW`).
- Unsupported client languages now fall back safely to `en-US`.
- Applied the final FULL-edition main-content centering adjustment: `Padding: (Left: 9, Right: 0);`.
- Kept the top page-image row on the same centered flexible-spacer layout used by the FULL edition.
- Updated bundled/runtime version references to 1.2.2.

## 1.2.1

- Added a prominent README warning for the Free-edition `example.json`: after testing the demo, set `autoOpen.enabled` to `false` if the example should no longer open automatically.
- Clarified that the demo auto-open should be disabled before enabling another page that is eligible in the same destination, otherwise the intentional multi-page auto-open conflict protection prevents either page from opening automatically.
- Clarified that disabling the demo auto-open does not disable its manual `/guide` command.
- Confirmed and documented that `mods/MenuInfoPages/README.txt` is rewritten from the bundled README on every server startup, so the server-side documentation automatically updates with the installed JAR.
- Updated bundled/runtime version references to 1.2.1.

## 1.2.0

- Localized the dismissible auto-open **Do not show again** checkbox across all 25 bundled publication languages, with English fallback.
- Reworked the internal local/remote image asset pack so it remains a valid persistent disk-backed pack when Hytale asset/editor tools refresh their asset state.
- Fixed Trigger Volume / Asset Editor interactions that could previously unregister MenuInfoPages image resources, break page images, and disturb unrelated Common UI/HUD images.
- Kept the simple `mods/MenuInfoPages/images/` workflow and direct HTTP/HTTPS PNG support unchanged for administrators.
- Improved the generated example page with clearer auto-open conflict guidance, command-restart guidance, and PNG recommendations.
- Live image reload remains optional and experimental: changing Common images while players are connected can temporarily disturb existing HUD/UI images until reconnect.

## 1.1.6

- Added `mods/MenuInfoPages/images/` as a simple administrator-managed local PNG folder.
- Local images can be referenced as `logo.png` or `images/logo.png` in both top-level images and section images.
- MenuInfoPages mirrors referenced local PNG files internally into the Common UI asset structure required by Hytale, so custom local images no longer require opening or modifying the JAR.
- Existing bundled/UI asset paths and direct HTTP/HTTPS PNG URLs remain fully supported.
- Local image additions and content changes follow `reload-images`: with `false` they activate on server restart; with `true`, `/infopages reload` can activate them live.
- Unchanged local images are reused without unnecessary rewrites during reload.
- The administrator `images/` folder is never cleaned or modified by MenuInfoPages.
- Only local images actually referenced by loaded pages are mirrored into the internal runtime cache.
- Obsolete files in the internal `Local` mirror are cleaned safely at server startup, while the original PNG files remain untouched in `mods/MenuInfoPages/images/`.

## 1.1.5

- Added `/infopages reload` with strict JSON validation, live page-command refresh and automatic cleanup of obsolete auto-open state.
- Added `/infopages newpage <name> <command>` to create a ready-to-edit JSON page and register its command immediately.
- Added optional per-page `autoOpen` support with `once`, `always` and `dismissible` modes, destination filters, delay, versioning and per-player dismissal state.
- Existing valid pages missing `autoOpen` are upgraded automatically with a disabled block while preserving their existing content.
- Increased bottom action-button capacity from 3 to **5** while retaining `primary`, `secondary` and `danger` styles.
- Added direct HTTP/HTTPS **PNG-only** page images with server-side caching and an 8 MiB per-image safety limit.
- Remote images keep their original URL filename in one flat cache folder; duplicate filenames from different URLs are rejected instead of overwritten.
- Remote images are never resized or recompressed. Widths above 700 px and files above 1 MiB generate warnings.
- Added `mods/MenuInfoPages/reload.yml`. `reload-images: false` is the safe default; `true` enables experimental but functional live remote-PNG refresh for page editing and maintenance.
- Live image refresh uses the stable non-force-rebuild Common-asset path.
- Remote image refresh is non-blocking for page rendering and no longer holds a WorldThread while downloading assets.
- Obsolete remote cache files are cleaned safely at server startup.
- Added a more complete generated `example.json` demonstrating auto-open, live reload, remote-image behavior, rich text, sections and buttons.
- Added an up-to-date `README.txt` directly in `mods/MenuInfoPages/`, refreshed on startup so administrators do not need to open the JAR to read the documentation.

## 1.1.4

- Standardized the generated example page as `example.json` and made generated/runtime text English.
- Added the bundled 700x300 example banner.
- Added SPAWN, HELP and CLOSE example buttons and close-only button support.
- Updated the interface to the native Hytale-style presentation.
- Kept support for up to 5 top images and 5 sections.
- Standardized the public data directory as `mods/MenuInfoPages/pages/`.

## 1.1.3

- Previous 1.1.x release line.
