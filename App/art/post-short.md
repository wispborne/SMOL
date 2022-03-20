**Starsector Mod Organizer and Launcher (SMOL) Beta 2**

**Windows only**
Features: mod browser, version checking + updating, mod profiles, change Starsector RAM, switch to JRE 8, VRAM estimation, themes, mod dependency warnings, see readme for more.
```
Fixed
- Starsector no longer crashes when launched from SMOL.
- Version comparison incorrectly said that two versions with unequals lengths are the same, eg "2.7" and "2.7b".
- If SMOL is unable to start because it cannot create settings files, it will show an error instead of crashing silently.
Added
- Added updater for updater. yo dawg.
Changed
- Launching Starsector from SMOL now bypasses the launcher (I want to reintroduce this at a later point).
- Changed disabled extension to .disabled-by-SMOL to prevent some confusion.
- Added a timeout of 10s to most notifications.
```
**Known issues**: <https://github.com/davidwhitman/SMOL/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc+label%3Abug>
- Doesn't start at all. Fix: move it out of your Starsector folder or put it in another folder.
- Browser is on top of everything, I don't have a way to fix this.

Bug or suggestion? ping @Wisp#0302 or create a thing here: <https://github.com/davidwhitman/SMOL/issues/new/choose>

download:
**BETA. It may crash. Some things may not work.**
  If you already have SMOL, launch it and it should show an update prompt.
  7z, click to extract (150MB): <https://github.com/davidwhitman/SMOL_Dist/releases/download/beta02-rc01/SMOL_Dist_7z.exe>
source code & readme: <https://github.com/davidwhitman/SMOL/tree/dev>