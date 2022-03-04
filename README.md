# SMOL

Starsector Mod Organizer & Launcher

For Windows only. Unix users, see if [AtlanticAccent's MOSS](https://fractalsoftworks.com/forum/index.php?topic=21995.msg332186#msg332186) will work for you.

## Why?

### Why is it so huge? 400 MB?!

Two main reasons.

- First, because the embedded browser, JCEF, is 219 MB on its own. You can delete this (remove the `jcef` folder from `libs`) and SMOL will work, but you won't be able to use the Mod Browser.
- Second, because the UI framework, Compose for Desktop (by JetBrains), is about 100 MB on its own.

Add in other dependencies (7zip to extract mods, json/xml parsing, Kotlin's standard libs, etc) and it ends up being...not very smol. The actual code I wrote is less than 3 MB.

For a much, much smaller application, try [AtlanticAccent's MOSS](https://fractalsoftworks.com/forum/index.php?topic=21995.msg332186#msg332186).

### Why make this?

Starsector is already incredibly easy to mod, and there are two [mod managers](https://fractalsoftworks.com/forum/index.php?topic=21995.0) already [out there](https://www.nexusmods.com/site/mods/179).

- SMOL is more than a mod manager; it has VRAM Estimator built-in, as well as Version Checker, **and** a an online mod browser.
- SMOL supports multiple versions of the same mod.
- I wanted to build something with an intuitive, pretty UI.
- I wanted to learn Compose for Desktop, which is the (brand new) UI framework behind this.

SMOL works alongside manual mod management, too!

Did this really need to be built? Absolutely not, but it has been fun to work on and I've learned a lot, and isn't that what hobbies are about? :)

## Main Features

* **Mod Browser** to view and install mods from the Forums and #mod_updates without leaving SMOL.
* Quickly **switch between mod versions**.
* **Mod Profiles** allow swapping between different groups of mods/versions easily.
  * Make a new profile for a new save, or keep a barebones profile for mod development.
* Easily assign more (or less) **RAM** to the game.
* **Auto-install JRE 8** for better performance and **switch JREs** with a click.
* **VRAM impact** estimates for each mod.
* **Version checker** support.
  * Supports Direct Download, for mods that have added it.
* Drag'n'drop or file browser **mod installation**.
  * Detects and fixes incorrect mod folder nesting.
* Warns and provides fixes for missing mod **dependencies**.
  * eg. if LazyLib is disabled, a button is shown to enable it. If it is not found, a button appears to search for it online.
* Works alongside manual mod management; you are **not locked into using SMOL**.
* Built-in and easily configurable **themes**.

## Smoller Features

* **Favorite** a mod to pin it to the top.
* In-app **log viewer** with selectable logging level.
* **Shortcuts** to your game folder, mods, saves, and log file.

### Supported File Formats

SMOL uses 7zip, so anything 7zip supports: AR, ARJ, CAB, CHM, CPIO, CramFS, DMG, EXT, FAT, GPT, HFS, IHEX, ISO, LZH, LZMA, MBR, MSI, NSIS, NTFS, QCOW2, RAR, RPM, SquashFS, UDF, UEFI, VDI, VHD, VMDK, WIM, XAR and Z

## Known Issues

- The browser is always on top of the rest of the application - anything that intersects it cuts cut off.
  - This is a known bug in the UI framework. See <https://github.com/JetBrains/compose-jb/issues/1087> and <https://github.com/JetBrains/compose-jb/issues/221>.
- JCEF, the Chromium Embedded Framework that's used in the Mod Browser, sometimes doesn't shut down with SMOL (despite being told to), and sits in the background, using CPU.

## Under the Hood

Mod folders are named using the mod version and a unique id generated from the mod id and the version, which allows different versions of the same mod to be placed in the same folder.

SMOL disables mods by renaming the `mod_info.json` file to `mod_info.json.disabled`, preventing the game from reading it as a valid mod.

## Building

1. Unzip `./App/libs/jcef-v1.0.18.7z` to `./App/libs/jcef-v1.0.18`.
2. Run `./gradlew run`.

## Credits

* **Fractal Softworks** for making Starsector and for permission to scrape the forum periodically.
* **MesoTroniK** for consulting and brainstorming the whole way through.
* **AtlanticAccent** for open-sourcing his Mod Manager, allowing me to peek under the hood (I copied almost nothing, I swear!) and being a great competitor :)
* **rubi/CeruleanPancake** for feedback, QA, and morale/moral support.
* **Soren/Harmful Mechanic** for feedback.
* **ruddygreat** for feedback and QA.
* **Tartiflette** for the idea to disable mods by renaming the mod_info.json file, and other feedback.
* **The rest of the USC moderator team** for feedback.