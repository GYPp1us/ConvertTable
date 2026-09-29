# Minecraft 26.3 upgrade verification

- Date: 2026-09-22 00:09:17 +08:00
- Target instance: `D:/Desktop/Document/MC/MC/.minecraft/versions/26.3-CRAFT`
- Installed mod: `mods/convert-table-1.3.1.jar`
- JAR SHA-256: `53FD6BF217A6BB1911CED889BF2C8EBF7B09B8AB86690953067924D12E5C8281`
- Active recipe config: `config/convert_table/recipes.json`
- Config SHA-256: `BC3CB66500D47413F71A3CDEADFC72CB73C00A344BE7D257931F35AE811E2C15`
- Backup: `none; target had no previous ConvertTable JAR or config`

## Runtime evidence

- Minecraft 26.3 + Fabric Loader 0.19.5 + Fabric API 0.161.0+26.3.
- Sodium 0.9.2+mc26.3, Iris 1.11.6+mc26.3, Continuity 3.0.1+26.3, and 3D Skin Layers 1.11.3-mc26.3 loaded.
- Vulkan client game test passed: recipe config, viewer sync, execution, continuous automation, UI/inventory sync, connected containers, XY projection.
- Test log: `build/test-26.3-min.log`.
- Optional JEI/REI 26.3 builds were not installed or runtime-tested in this migration.
