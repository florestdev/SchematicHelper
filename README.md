# SchematicHelper

**Developer:** FlorestDev
**License:** [Unlicense](https://unlicense.org/)
**Minecraft Version:** 1.21+
**API:** Bukkit / Spigot / Paper

---

## Description

SchematicHelper is a **vanilla-friendly WorldEdit-based plugin** for Minecraft servers. It allows you to paste `.schem` files **directly into your world**, preserving **all blocks, NBT data, and TileEntities** (like chests, signs, furnaces, banners).

Designed to be **lag-friendly**, it pastes large structures **layer by layer** to prevent TPS drops, making it perfect for big builds on vanilla or low-end servers.

---

## Features

* Paste local `.schem` files or download via URL (`/paste <name/URL> [x] [y] [z]`)
* Optional coordinates (default: player's location)
* TPS-safe **layered insertion**
* Preserves all **NBT data**
* ActionBar progress during pasting
* Configurable messages via `config.yml`
* Automatic creation of `schematics/` folder in the server root
* Permission-based usage: `schematichelper.paste`

---

## Commands

```txt
/paste <schematic_name_or_URL> [x] [y] [z]
```

**Usage Examples:**

* `/paste house` → pastes `house.schem` at player location
* `/paste castle 100 64 200` → pastes `castle.schem` at X=100, Y=64, Z=200
* `/paste http://example.com/scheme.schem` → downloads and pastes from URL

---

## Config.yml Example

```yaml
prefix: "&6SchematicHelper"
messages:
  schematic_not_found: "&cSchematic file not found!"
  no_permission: "&cYou do not have permission to use this command!"
  paste_success: "&aSchematic successfully pasted at coordinates: %x%, %y%, %z%"
  paste_error: "&cError while pasting the schematic!"
  folder_created: "&aSchematic folder successfully created!"
  folder_failed: "&cFailed to create schematics folder!"
```

---

## Installation

1. Place `SchematicHelper.jar` in your `plugins` folder.
2. Restart your server.
3. A `schematics/` folder will be automatically created in your **server root**.
4. Place your `.schem` files in the `schematics/` folder or use a URL.
5. Use `/paste <name>` to insert them.

---

## Permissions

* `schematichelper.paste` — allows player to paste schematics.

---

## Notes

* For best performance, **large builds are pasted layer by layer**.
* All blocks, including TileEntities and NBT data, are preserved.
* The plugin works on **vanilla clients**, no mods required.

---

## License

This project is licensed under the **[Unlicense](https://unlicense.org/)** — do whatever you want with it.

---

**Author:** FlorestDev
