# dandanator-mini
ROM assembler for the [Dandanator Mini](http://www.dandare.es/Proyectos_Dandare/ZX_Dandanator!_Mini.html). A Spectrum ZX peripheral with many features.

## Requirements

* Git
* Maven 3.6+
* Java 11 or later (Java 21 recommended; OpenJDK is fully supported)

## Cloning the repository

```sh
git clone https://github.com/teiram/dandanator-mini.git
```

## Building

```sh
cd dandanator-mini
mvn install
```

The fat JAR (all dependencies bundled) will be produced at:

```
target/dandanator-mini-<version>.jar
```

### Running the tests

```sh
mvn test
```

> Audio-hardware tests are excluded in CI environments with
> `-Dsurefire.excludes="**/fft/SoundTests.java"`.

### Generating native packages

Native installers (`.deb`, `.rpm`, `.exe`, `.dmg`, app-image) can be built with
[jpackage](https://docs.oracle.com/en/java/application-bundlers/jpackage.html)
via the `jpackage` Maven profile:

```sh
# Build the default package type for the current platform:
mvn verify -Pjpackage -DskipTests

# Build a specific type (Linux examples):
mvn verify -Pjpackage -DskipTests -Djpackage.type=deb
mvn verify -Pjpackage -DskipTests -Djpackage.type=rpm
mvn verify -Pjpackage -DskipTests -Djpackage.type=app-image
```

Packages are written to `target/dist/`.

## Graphical interface

Launch the GUI with the fat JAR:

```sh
java -jar target/dandanator-mini-<version>.jar
```

Or use a native installer if you built one.

## Command-line interface (CLI)

The fat JAR includes a headless CLI mode suitable for scripting, CI/CD pipelines
and automated workflows. **No display or JavaFX runtime is required** in CLI mode.

### Synopsis

```
java -jar dandanator-mini-<version>.jar --cli [OPTIONS] <game1> [game2 ...]
```

### Options

| Flag | Description |
|------|-------------|
| `-o <file>`, `--output <file>` | Output ROM set file **(required)** |
| `-h`, `--help` | Print usage information and exit |
| `-V`, `--version` | Print version and exit |

### Supported input formats

| Extension | Format |
|-----------|--------|
| `.sna` | ZX Spectrum 48K/128K snapshot |
| `.z80` | Z80 emulator snapshot |
| `.rom` | Raw ROM / cartridge image |
| `.tap` | ZX Spectrum tape image |
| `.mld` | Multi-Load format (48K/128K MLD games and DAN-SNAP) |
| `.daad` / `.zip` | DAAD adventure-game archive |
| `.dantap` | DAN-TAP format |

### Examples

```sh
# Generate a ROM set from three snapshots
java -jar dandanator-mini-10.4.3.jar --cli -o my-cartridge.rom game1.sna game2.z80 game3.mld

# Mix formats
java -jar dandanator-mini-10.4.3.jar --cli -o cartridge.rom \
    adventure.daad snapshot1.sna loader.tap

# Print help
java -jar dandanator-mini-10.4.3.jar --cli --help
```

### Notes

* Games are added to the ROM set **in the order they are listed** on the command line.
* A **maximum of 25 games** can be packed into a single ROM set.
* When a file format does not embed a game name, the **file name (without extension)**
  is used as the game name on the cartridge menu.
* Exit codes: **0** = success, **1** = argument/usage error, **2** = I/O or generation error.

### Integration example (shell script)

```sh
#!/bin/sh
JAR=/opt/dandanator/dandanator-mini.jar
OUTPUT=/tmp/cartridge.rom

java -jar "$JAR" --cli \
    --output "$OUTPUT" \
    games/game1.sna \
    games/game2.z80 \
    games/game3.mld

if [ $? -eq 0 ]; then
    echo "Cartridge written to $OUTPUT"
    # Flash to device, copy to SD card, etc.
fi
```

## Architecture overview

```
src/main/java/com/grelobites/romgenerator/
├── MainShade.java          Bootstrap entry point (routes CLI vs GUI)
├── MainApp.java            JavaFX GUI application
├── MainCLI.java            Command-line entry point
├── ApplicationContext.java GUI application state (game list, ROM handler, etc.)
├── Configuration.java      Persistent application settings
├── handlers/
│   └── dandanatormini/
│       ├── v9/DandanatorMiniV9RomSetHandler.java  Core ROM generation logic
│       ├── v10/DandanatorMiniV10RomSetHandler.java Latest ROM format
│       └── v10/CliRomSetHandler.java              Headless handler for CLI use
├── model/                  Game data models (Game, SnapshotGame, MLDGame, …)
└── util/
    ├── gameloader/         Input-format loaders (SNA, Z80, ROM, TAP, MLD, DAAD, DAN-TAP)
    ├── compress/           Compression utilities (Z80, ZX7)
    └── romsethandler/      ROM set handler infrastructure
```

### Key extension points

| Class / interface | Purpose |
|-------------------|---------|
| `GameImageLoader` | Implement to add a new input format |
| `GameImageType` | Register a new loader by extension |
| `RomSetHandler` | Implement to support a new ROM version or output target |
| `CliRomSetHandler` | Extend for custom headless export workflows |

## Continuous integration

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `ci.yml` | Every push / PR | Build + test (excludes audio tests) |
| `package-release-action.yml` | Tag `v*` push | Deploy to GitHub Packages; build native installers (.deb, .rpm, .exe, .dmg, app-image); attach fat JAR and installers to the GitHub Release |

 
