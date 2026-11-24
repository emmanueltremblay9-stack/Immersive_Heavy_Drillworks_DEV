# IHD Cargo Hopper R61 — independent draft backend

**Status: SOURCE_DRAFT / RUNTIME_NOT_YET_VERIFIED / NOT_RELEASE_READY**

## Purpose and physical flow

- Standalone Immersive Heavy Drillworks Cargo Hopper, separate from the Industrial Driller Garage and the Factory Hopper of IEDCTR.
- Requested route: cargo hopper, integrated R61 belt (geometry/animation art stage), then one real adjacent standard Immersive Engineering BasicConveyor aligned with the output orientation.
- Backend emits through the standard IE ItemHandler BLOCK capability of an actual BasicConveyor; no special connector, custom IE renderer, duplicated belt or third-party asset adaptation.
- Fail-closed when the neighbor is missing, wrong-facing, unloaded or blocked. Input capability is insert-only; storage saved in the new block entity, not the Garage.

## Original R61 source

- Artist production model R61_Giant_Cargo_Hopper_IE_Transfer_Fit.bbmodel; user-reported SHA-256 fc436c4019417b44851e3233de65d2c08a238ec4746f7020298bb79dd3dd8a4a.
- Artist package R61_Giant_Cargo_Hopper_IE_Transfer_Fit_Package.zip; user-reported SHA-256 c00427cf940cfd68427da3baef193e1e713f8d212588ee032e3428e711ae17b4.
- Source held in specialist Library folder Spécialiste artiste 3D BBModel. Raw materialization returned: This Project file does not have an authorized raw-byte materialization path.
- Artist's reported 33 static PASS do not prove in-game rendering, item transfer or redistributability.
- The QA_ONLY adjacent conveyor witness, assets with unclear rights, and 02_Undercarriage_Service_Platform.bbmodel are excluded from the public DEV repository. No R61 model, texture, mesh, UV or animation is replaced with a lookalike.

## Engineering limitations

- The nine-slot test buffer and one item per eight ticks are PROVISIONAL, not approved final R61 cargo balance.
- No crafting, GUI, player-facing texture/model or accurate R61 renderer; no Minecraft screenshots are claimed.
- Normal inventory NBT save/reload has a GameTest. Missing/invalid older payload handling, item retention on block destruction, cross-crash transaction durability and item entity despawn are not qualified; no full-losslessness claim beyond tested cases.
- No new mining, item production from mining, native Tunnel Digger GUI/renderer/fluid transfer, mixins, Garage architecture edits, publication, PR merge or release.
- Check an actual build and GameTestServer tied to the exact draft commit before marking this code validated.

## Git boundary

Only feat/ihd-r61-cargo-hopper-20261124. The Digger/HUD PR #6 and Docking Controller PR #7 stay independent. The branch is based on the separately verified remote main d5ec9ad9bea31f950306755bc9368c56a381bbe1, which is not proof of the newest unknown local workspace.
