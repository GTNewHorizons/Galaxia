# Galaxia GUI preview

Use the previewer to render and click Galaxia's real ModularUI2 builders without starting Minecraft

It provides local client-visible state only. It doesn't simulate a server, networking, saves, or authority

## First run

Requirements: JDK 25 and an internet connection for the first download

Windows:

```bat
preview-gui.bat doctor
preview-gui.bat list
preview-gui.bat open starmap/default
```

Linux or macOS:

```sh
./preview-gui.sh doctor
./preview-gui.sh list
./preview-gui.sh open starmap/default
```

The first command downloads the pinned ModularUI2-Preview release, verifies its SHA-256 checksum, and caches it for later runs

## Commands

Use `preview-gui.bat` below on Windows and `./preview-gui.sh` on Linux or macOS

```text
preview-gui.bat help
preview-gui.bat doctor
preview-gui.bat list
preview-gui.bat open <scenario>
preview-gui.bat watch <scenario>
preview-gui.bat render <scenario>
preview-gui.bat verify
preview-gui.bat verify <family-or-scenario>
preview-gui.bat verify --full
preview-gui.bat verify --failed
```

- `open` starts a window you can click
- `watch` keeps that window open and reloads after a successful source change. A failed rebuild leaves the last working preview visible
- `render` writes one PNG and its diagnostics for agent or human inspection
- `verify` renders every canonical GUI root in isolated workers
- `verify --full` also runs additional states and action scripts
- `verify --failed` reruns only failures from the last report

You can run the launcher from any directory. It resolves the Galaxia checkout from the launcher's own location

## Outputs and failures

Verification writes to `tools/gui-preview/output/verify`

For scenario `<family>/<name>`, inspect:

```text
tools/gui-preview/output/verify/<family>/<name>/preview.png
tools/gui-preview/output/verify/<family>/<name>/bounds.json
tools/gui-preview/output/verify/<family>/<name>/actions.json
tools/gui-preview/output/verify/<family>/<name>/diagnostic.json
tools/gui-preview/output/verify/<family>/<name>/error.log
```

Rerun one failure directly:

```bat
preview-gui.bat verify <family>/<name>
```

Use `bounds.json` to find widget paths for repeatable action scripts. Captures from those scripts appear below the scenario's `captures` directory

## Add or repair a scenario

1. Add representative local state under `tools/gui-preview/src/preview/java`
2. Call the real Galaxia production builder and declare its class with `PreviewEntrypoint.of(...)`
3. Register the scenario in `GalaxiaPreviewCatalog`
4. Add a short file under `tools/gui-preview/actions` when a local interaction is worth preserving
5. Run `preview-gui.bat verify <scenario>` and inspect the PNG, bounds, actions, diagnostics, and warnings

Keep production builders as the only source of layout. Don't copy a GUI into the preview code and don't add server or packet simulation. Reusable rendering or input gaps belong in [ModularUI2-Preview](https://github.com/Pxx500/ModularUI2-Preview)

## Troubleshooting

| Problem | What to do |
|---|---|
| JDK error | Set `JAVA_HOME` to JDK 25 or put its `java` on `PATH` |
| Download error | Check the network, then run the same command again |
| Checksum error | Remove the named corrupt archive from the reported preview cache and retry |
| Gradle wrapper missing | Run the launcher from a complete Galaxia checkout |
| One scenario fails | Read its `diagnostic.json` and `error.log`, then rerun that scenario |
| A preview differs from Minecraft | Check whether it uses an unsupported renderer or input path, then fix the reusable gap in ModularUI2-Preview |
