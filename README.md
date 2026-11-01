# Aether Performance (Fabric, Minecraft 1.21.11)

Mod client-side di ottimizzazione leggera e sicura. Richiede **Fabric Loader** e **Fabric API**.

## Come si usa
- **K**: apre la schermata impostazioni (tasto cambiabile in Opzioni > Comandi > Varie).
- **J**: mostra/nasconde l'overlay diagnostico.

### Schermata impostazioni
Barra laterale con 4 schede e il selettore del profilo in basso a sinistra:
- **Generale**: distanza di rendering, distanza di simulazione, limite FPS, V-Sync (opzioni di Minecraft).
- **Grafica**: illuminazione fluida, particelle, ombre delle entita (opzioni di Minecraft).
- **Prestazioni**: culling di entita / block entity / particelle, distanze (profilo Personalizzato) e FPS obiettivo (profilo Auto).
- **Diagnostica**: FPS, frame time, RAM, oggetti tagliati al secondo e grafico dei frame in tempo reale.

Pulsanti in basso: **Ripristina valori** (valori iniziali della mod), **Annulla** (torna a com'era all'apertura o all'ultimo Applica), **Applica modifiche** (salva, comprese le opzioni di Minecraft).

## Funzioni
- **Culling entita**: non disegna le entita oltre una certa distanza (giocatori, boss, entita con nome e passeggeri/veicoli sono sempre esclusi).
- **Culling block entity**: non disegna casse, cartelli, banner ecc. oltre la distanza impostata.
- **Throttling particelle**: scarta le particelle lontane e dirada quelle a media distanza.
- **Modalita**: `BALANCED`, `PERFORMANCE`, `EXTREME`, `AUTO`, `CUSTOM`.
  - `AUTO` parte dai valori di BALANCED e li restringe/allarga da sola per mantenere gli FPS obiettivo (minimo 35% delle distanze).
  - `CUSTOM` usa i valori dei cursori.
- **Overlay**: FPS a colori, 1% low, frame time, RAM, GC, grafico dei frame e contatore di quanto viene tagliato al secondo (E = entita, B = block entity, P = particelle).

## Configurazione
`config/aether_performance.json` (si modifica anche dal menu in gioco)

| Chiave | Significato |
|---|---|
| `mode` | BALANCED / PERFORMANCE / EXTREME / AUTO / CUSTOM |
| `beCulling`, `entityCulling`, `particleThrottling` | attiva/disattiva ogni funzione |
| `showOverlay`, `overlayGraph` | overlay e grafico dei frame |
| `targetFps` | FPS obiettivo della modalita AUTO |
| `beCullingDistance`, `entityCullingDistance`, `particleDistance`, `particleThinning` | valori usati solo con `CUSTOM` |

Preset (blocchi): BALANCED 64/128/48, PERFORMANCE 48/96/32, EXTREME 32/64/24 (block entity / entita / particelle).

## Compilare
La build parte da sola su GitHub Actions a ogni push: il jar e' tra gli artefatti della build (`aether-performance-<versione>.jar`, non quello `-sources`).

In locale: `gradle build` (Gradle 9.2, Java 21) -> `build/libs/`.

## Rimosso rispetto alla versione originale
- `VertexBufferPool` + `SectionBuilderMixin`: condividevano il buffer del `Tessellator` tra thread diversi (crash/corruzione) e il metodo `beginBufferBuilding` non esiste.
- `FrustumMixin`: non faceva nulla.
