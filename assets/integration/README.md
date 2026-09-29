# 1.1.1 in-game compatibility results

Tested on 2026-09-15 with the user's exact Sodium 0.9.2+mc26.2 and Iris 1.11.4+mc26.2 JARs, Fabric API 0.159.0+26.2 and Loader 0.19.5. Continuity, Mod Menu, Placeholder API and 3D Skin Layers from the same instance were also loaded. Test worlds are generated under the project's build directory; user saves are not involved.

- `opengl/`: successful OpenGL test using the selected `photon-voxy-support.zip` and its existing options, including `SPECULAR_MAPPING=true`. Iris loaded the block `_n` and `_s` atlases. Screenshots include two phases of each animation and a resource reload.
- `vulkan/`: successful test explicitly asserting the active Vulkan backend. All three variants render and animate, with another successful resource reload. Photon is not active on this path in this Iris version.
- `no-shaders/`: intermediate OpenGL run with shader integration inactive; useful clear color/geometry close-ups. These are not Vulkan results.

All successful runs check entity registration, four facing states, pickaxe loot, loaded recipes, block entity save/load and removal, and explicitly invoke the production renderer. The runtime failure in 1.1.0 was a wrong atlas key, not a Sodium dependency/version requirement: `getAtlasOrThrow` takes a definition ID, while `TextureAtlas.LOCATION_BLOCKS` is a texture location. Version 1.1.1 uses the sprite lookup with `SpriteId` instead.

Source asset checks pass for 34 material sets and 4,836 combined exported block/item faces. Minecraft's model parser confirms all six models and their exact UV orientation. The JAR excludes the client test mod and its graphics-backend mixin.

To repeat either backend, use `assets/tools/test_compat.ps1 -Instance <instance-path> -Backend opengl` (or `vulkan`). Logs in each directory record the tested versions, graphics backend and `CONVERSION_TABLE_GAME_TEST_PASS` marker. Screenshots are direct game captures, not Blender renders or generated artwork.
