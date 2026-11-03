# Witch Hat — Artisan C (work-in-progress asset)
- `witch_hat.obj` — low-poly 3D mesh with curved crown, broad brim, ribbon, skull ornament and two non-emissive amethyst crystals.
- `witch_hat_worn.png` — distressed black-violet leather texture; no glow/emissive map.
- `witch_hat.mtl` — material binding for OBJ viewers.
The model is an editable geometry prototype, not yet a Minecraft runtime renderer or an equipped cosmetic.


Runtime prototype note:
- The client renderer currently uses a Minecraft-native cuboid approximation of the hat.
- The OBJ remains the detailed editable source asset; the OBJ mesh is not parsed at runtime.
- The worn texture is copied to assets/pvpplus/textures/cosmetic/witch_hat.png.
