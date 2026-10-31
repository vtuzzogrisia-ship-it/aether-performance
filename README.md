# Aether Performance (Fabric, Minecraft 1.21.11)

Mod client-side di ottimizzazione leggera e sicura.

## Funzioni
- **Culling entità**: non disegna le entità oltre una certa distanza (giocatori, boss, entità con nome e passeggeri/veicoli sono sempre esclusi dal culling).
- **Culling block entity**: non disegna casse, cartelli, banner ecc. oltre la distanza impostata.
- **Throttling particelle**: scarta le particelle lontane e dirada quelle a media distanza.
- **Preset**: `BALANCED`, `PERFORMANCE`, `EXTREME`, `CUSTOM` (in CUSTOM valgono i tuoi numeri).
- **Overlay diagnostico** (`showOverlay`): FPS medi, 1% low, frame time, heap e peso del GC.

## Configurazione
`config/aether_performance.json`

| Chiave | Significato |
|---|---|
| `mode` | BALANCED / PERFORMANCE / EXTREME / CUSTOM |
| `beCulling`, `entityCulling`, `particleThrottling` | attiva/disattiva ogni funzione |
| `showOverlay` | mostra l'overlay (default `false`) |
| `beCullingDistance`, `entityCullingDistance`, `particleDistance`, `particleThinning` | valori usati solo con `CUSTOM` |

Preset (blocchi): BALANCED 64/128/48, PERFORMANCE 48/96/32, EXTREME 32/64/24 (block entity / entità / particelle).

## Compilare
1. Genera il wrapper una volta: `gradle wrapper --gradle-version 8.14`
2. Controlla versioni di `yarn_mappings`, `loader_version` e Loom su https://fabricmc.net/develop e aggiorna `gradle.properties` / `build.gradle`.
3. `./gradlew build` -> il jar è in `build/libs/`.

## Cosa è stato rimosso rispetto alla versione originale
- `VertexBufferPool` + `SectionBuilderMixin`: condividevano il buffer del `Tessellator` tra thread diversi (crash/corruzione) e il metodo `beginBufferBuilding` non esiste.
- `FrustumMixin`: non faceva nulla.
