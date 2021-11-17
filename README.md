# SMOL

Starsector Mod Organizer & Launcher

## Features

* Quickly **enable/disable** different mod versions.
* Easily assign more (or less) **RAM** to the game.
* Warns and provides fixes for missing mod **dependencies**.
  * eg. if LazyLib is disabled, a button is shown to enable it. If it is not found, a button appears to search for it online.
* **VRAM impact** estimates for each mod.
* **Mod Profiles** allow swapping between different groups of mods/versions easily.
  * Make a new profile for a new save, or keep a barebones profile for mod development.
* **Version checker** support.
* Drag'n'drop or file browser **mod installation**.
  * Detects and fixes incorrect mod folder nesting.
* Works alongside manual mod management; you are **not locked into using SMOL**.

## Under the Hood

Mod folders are named using the mod version and a unique id generated from the mod id and the version, which allows different versions of the same mod to be placed in the same folder.

SMOL primarily utilizes three folders.

### ~/archives

In the user's home folder. Contains compressed archives (.zip, .7z, etc) of mods that have an associated archive. Mods may be reset to default, which replaces any existing version of them with the archived version, thus wiping any changes the user might have made.

### ~/staging

In the user's home folder. Mods are placed here, then hardlinked to the game's /mods folder.

### /mods

In the game folder. SMOL does not put files here, but rather puts them into /staging and then creates hardlinks to them here.

<br>
Hardlinks are used to avoid having a mod folder existing in two places, which would require double the storage space. They are also much faster to create than copying files, which means mods may be enabled faster.

Symlinks require administrator permission to create in Windows, which is why they were not used.
