# Native visual refresh — Stitch-inspired

Reference: https://stitch.withgoogle.com/projects/11043770835758050762

The native app retains its shared Kotlin game rules and local saves. The artwork is newly generated for this project, inspired by the existing Stitch art direction; it is not a pixel-identical export of the Stitch screens.

## Artwork

Generated using the built-in image generation tool (not API/CLI fallback). Final project assets:

- `composeApp/src/commonMain/composeResources/drawable/farm_backdrop.png`
- `composeApp/src/commonMain/composeResources/drawable/village_backdrop.png`

All gameplay controls, crop states, inventory and coins are drawn by Compose over the artwork, not baked into screenshots.

## Typography and resources

Noto Sans Thai Regular and SemiBold, from [the Noto Thai project](https://notofonts.github.io/thai/), with the SIL Open Font License bundled under `composeResources/files/licenses/NotoSansThai-OFL.txt`.

Resource configuration follows [Compose Multiplatform resources setup](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html). iOS, Android and the JVM verification renderer consume the same resource directory.

## Verification and preview

- All 9 native pages and seed/growth/harvest/reset overlays use the new design. The farm and village art is generated; crop beds and UI icons are original code-native Canvas drawings.
- `./scripts/gradle.sh -p verification smokeUi renderScreens jvmTest` passed. 25 unit tests plus an actual shared-Compose semantics interaction flow (navigation, planting, watering, harvest, selling, seed purchase and order delivery, with save-state assertions).
- 24 actual Compose PNG renders: 12 screens/states at 393×852 and 320×640 in `verification/build/screenshots/`. Gallery: `preview/index.html`.
- Visual review fixed harvest-button clipping, low-contrast text on grass and narrow shop-price wrapping. One planted seed is illustrated as one crop, matching the game's yield.
- New iOS Simulator Kotlin source/resource-accessor compilation passed. The full `.app` has NOT been relinked after this refresh because available storage is around 1.0 GiB. The previous `.app` contains the old UI. No iOS runtime launch or Android build is claimed.
- UI screenshots run on the JVM rendering backend with in-memory test saves. They are not evidence of iPhone touch/device QA, and the gallery is not a playable web conversion.

## Final image prompts

### Farm background

```
Use case: stylized-concept
Asset type: production mobile cozy farming game environment background, no UI.
Primary request: Original warm 2.5D clay-and-timber cartoon diorama for the Thai game Little Farm / after-hours garden. Portrait 1024x1536 composition. A charming cream farmhouse with chunky terracotta roof in the UPPER THIRD, rounded rolling green hills and pale buttery sky at top, tiny wooden fences at side edges, orange chubby cat wearing sage bandana on a little crate at upper left, tiny friendly adult farmer in straw hat and denim overalls at upper right near cottage. Small pond with lily pads along far right edge. Main central and lower area MUST be a broad uninterrupted gently shaded fresh green lawn, reserved for real interactive planting beds rendered by the app: no vegetable beds, no vegetables, no buildings or objects across this empty central gameplay region from 40% to 90% of image height. A few small flowers along the extreme perimeter only. Inviting high quality mobile game art, charming toy-like proportions, soft matte painted clay, beveled chunky forms, delicate ambient shadows, hand-painted texture, crisp beautifully detailed render, warm golden afternoon sunshine. Palette moss/leaf green #3D6B45, lush grass, cream #FFF4D8, butter #F4C867, terracotta #DC8A62, subtle sky #A8D7DF. Viewpoint slightly elevated 3/4 scene, mostly front-facing so UI planting grid fits comfortably. Full bleed. No text, no letters, no logos, no watermarks, no UI panels or buttons. Not photorealistic, not flat geometric clipart.
```

### Village background

```
Use case: stylized-concept
Asset type: mobile cozy farming game village map background, no UI.
Primary request: Original 2.5D clay-and-timber cartoon countryside village matching a warm Thai cozy farming game. Portrait 1024x1536 full bleed. A winding honey-colored footpath connects four distinct charming tiny buildings: a seed stall with green striped awning and sacks near upper left, produce market with cream and terracotta awning and wooden vegetable crates at upper right, a wooden community noticeboard under a soft round tree at middle-left, and a small carpenter cottage with timber stacks and saw at middle-right. Small pond and lily pads near bottom left, rolling green hills and buttery sky at very top. Friendly orange cat with sage neckerchief near lower-right path. Charming cream walls and rounded terracotta roofs, lush fresh moss green grass, chubby trees, tactile matte clay toy forms, warm painterly texture, polished premium casual mobile game art, lovely ambient shadows and golden late afternoon sunshine. Leave generous paths and lawn around buildings for overlaying actual native clickable text plaques, but DO NOT paint any labels or UI. Slightly elevated front 3/4 camera. Palette leaf #3D6B45, cream #FFF4D8, butter #F4C867, terracotta #DC8A62, sky #A8D7DF. No text, letters, numbers, watermark, logos or interface. No photorealism, no existing game characters.
```
