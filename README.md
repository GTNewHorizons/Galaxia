# Galaxia
**Galaxia** is a space exploration mod made purely for **GT: New Horizons** inspired by realistic modern aerospace and rocket construction. It adds many new features like large interstellar freight cargo ships, sci-fi station thrusters and unique rocket fuels, modular spaceship assembly, and a new simplified orbital dynamics simulation minigame that allows players to pilot their rocket in a 2D GUI! No more boring planet selection screens!

Galaxia has a modular system for constructing and assembling rockets, and has various multiblocks to automate construction of rocket parts, refueling, and rocket assembly. You will get to design the structure of each rocket from an assortment of parts. Galaxia also features space race elements - make your own flag, track your team's progress, and race each other to the moon! Currently 3 multiblocks are planned:
-Rocket Silo: Assembles and launches rockets
-Module Assembler: Constructs rocket parts
-Research Outpost: Gives research missions to unlock new items and modules

 

[This document](<https://docs.google.com/document/d/1I0_qtxRzEGk6KiOuNXg74zO9CaGzLLQCIhR4LTER1zo/edit?pli=1&tab=t.0>) covers all mechanics and features that are added by this mod, and planned to be added in the near future. It also contains some of the developers plannings, and thoughts, so check it out if you want some more info! 

## Optional second screen

Offscreen adds a keybinding for opening the star map in a separate window. The map, station management and module picker use client-side screens so building in that window can leave a chest open in the main window. Gameplay commands are still validated by the server. Galaxia does not require Offscreen for ordinary client or server runs.

The development profile uses `libs/offscreen-0.3-alpha-dev.jar`, lwjgl3ify 3.0.25 and Java 25:

```sh
./gradlew runClient17 -PoffscreenRuntime=true
```

Despite the task name, this opt-in profile selects Java 25. It uses Galaxia's native Java 25 classes. The Java 21 downgrade currently fails when encoding a facility command containing a pattern switch.

The Horizon QA scenario `galaxia:OffscreenGuiGameTests.buildModuleWhileChestRemainsOpen` tests map navigation, paid construction, independent resize and closing the second window while preserving a real server chest container. Use Horizon QA with the `ClientWindow` API and its bounded `scripts/client-tests` launcher. Select `:runClient17`, supply the HQA dev JAR with `--horizon-qa-jar`, and forward `--gradle-argument=-PoffscreenRuntime=true` and `--gradle-argument=--no-configuration-cache`. Use `gradlew.bat` on Windows.
