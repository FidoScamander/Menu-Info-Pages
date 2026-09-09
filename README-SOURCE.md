# Source layout

The release JAR is split into three deliberately small layers:

- `newt.infopages` contains the server plugin, page model, commands, UI binding and runtime services.
- `src/main/resources/Common` contains the Hytale UI and bundled visual assets.
- `src/main/resources/defaults` contains files copied to `mods/MenuInfoPages/` on a clean install.

The project targets Java 21. Put the Hytale server API JAR at `libs/HytaleServer.jar`, then run `gradle build`.
The generated JAR is written to `build/libs/`.

`README.md` and `CHANGELOG.md` are bundled into the release artifact because the plugin writes the matching runtime documentation into its data directory.
