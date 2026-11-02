# Aether Performance (Fabric, Minecraft 1.21.11)

Mod client-side di ottimizzazione leggera e sicura. Richiede **Fabric Loader** e **Fabric API**.

## Come si usa
- **K**: apre la schermata impostazioni (tasto cambiabile in Opzioni > Comandi > Varie).
- **J**: mostra/nasconde l'overlay diagnostico.

### Schermata impostazioni
Barra laterale con 6 schede e il selettore del profilo in basso a sinistra:
- **Generale**: distanza di rendering, distanza di simulazione, limite FPS, V-Sync + 4 preset grafici rapidi (Qualita / Bilanciato / Prestazioni / Massimi FPS).
- **Grafica**: illuminazione fluida, ombre delle entita, particelle, nuvole, fusione dei biomi, distanza entita (tutte opzioni di Minecraft).
- **Boost FPS**: culling di entita / block entity / particelle, oggetti e sfere XP lontani, distanza di rendering dinamica (solo profilo Auto).
- **Server**: stato del collegamento (nome server, ping), profilo dedicato ai server multigiocatore, culling dei giocatori lontani, ping nell'overlay.
- **Distanze**: valori precisi usati dal profilo Personalizzato (block entity, entita, particelle, diradamento, oggetti/XP, FPS obiettivo).
- **Diagnostica**: FPS, frame time, RAM/GC, ping, oggetti tagliati al secondo e grafico dei frame in tempo reale.

Pulsanti in basso: **Ripristina valori** (valori iniziali della mod), **Annulla** (torna a com'era all'apertura o all'ultimo Applica), **Applica modifiche** (salva, comprese le opzioni di Minecraft).

## Funzioni
- **Culling entita**: non disegna le entita oltre una certa distanza (boss, entita con nome e passeggeri/veicoli sono sempre esclusi).
- **Culling block entity**: non disegna casse, cartelli, banner ecc. oltre la distanza impostata (tramite `getRenderState`, sicuro anche con la build dei chunk in un altro thread).
- **Throttling particelle**: scarta le particelle lontane e dirada quelle a media distanza.
- **Culling oggetti e XP**: non disegna drop a terra e sfere di esperienza oltre una certa distanza.
- **Culling giocatori (server)**: puoi nascondere i giocatori lontani sui server affollati; chi cavalchi o che ti cavalca non sparisce mai.
- **Profilo server**: puoi usare un profilo diverso (es. Prestazioni) solo quando sei connesso a un server, lasciando quello normale in giocatore singolo.
- **Distanza di rendering dinamica**: in modalita Auto puo abbassare e rialzare anche i chunk visibili (minimo 8) quando le sole distanze di culling non bastano; torna al valore scelto da te quando esci o chiudi il gioco.
- **Modalita**: `BALANCED`, `PERFORMANCE`, `EXTREME`, `AUTO`, `CUSTOM`.
  - `AUTO` parte dai valori di BALANCED e li restringe/allarga da sola per mantenere gli FPS obiettivo (minimo 35% delle distanze), con una pausa di qualche secondo dopo essere entrati nel mondo e nessuna modifica quando la finestra e in background.
  - `CUSTOM` usa i valori dei cursori.
- **Overlay**: FPS a colori, 1% low, frame time, RAM, GC, ping (sui server), grafico dei frame e contatore di quanto viene tagliato al secondo (E = entita, B = block entity, P = particelle).

## Configurazione
`config/aether_performance.json` (si modifica anche dal menu in gioco)

| Chiave | Significato |
|---|---|
| `mode` | profilo usato in giocatore singolo (o sempre, se il profilo server e spento) |
| `serverMode`, `useServerProfile` | profilo usato sui server multigiocatore e se attivarlo |
| `beCulling`, `entityCulling`, `particleThrottling`, `smallEntityCulling` | attiva/disattiva ogni funzione di culling |
| `distantPlayerCulling`, `playerCullingDistance` | culling dei giocatori lontani sui server |
| `showOverlay`, `overlayGraph`, `showPing` | overlay, grafico dei frame e ping |
| `targetFps`, `dynamicViewDistance` | obiettivo e distanza di rendering dinamica della modalita AUTO |
| `beCullingDistance`, `entityCullingDistance`, `particleDistance`, `particleThinning`, `smallEntityDistance` | valori usati solo con `CUSTOM` |

Preset (blocchi: block entity / entita / particelle / diradamento / oggetti-XP): BALANCED 64/128/48/1/48, PERFORMANCE 48/96/32/2/32, EXTREME 32/64/24/3/24.

## Compilare
La build parte da sola su GitHub Actions a ogni push: il jar e' tra gli artefatti della build (`aether-performance-<versione>.jar`, non quello `-sources`).

In locale: `gradle build` (Gradle 9.2, Java 21) -> `build/libs/`.

## Sicurezza anti-crash
Ogni funzione che tocca il rendering del gioco (culling di entità/block entity/particelle, overlay, modalità Auto, schermata impostazioni) gira dentro un controllo che intercetta eventuali errori: se qualcosa va storto, quella singola funzione si disattiva da sola per il resto della sessione (si vede un avviso in `logs/latest.log`) invece di far crashare Minecraft. Le altre funzioni continuano a funzionare normalmente.

## Rimosso rispetto alla versione originale
- `VertexBufferPool` + `SectionBuilderMixin`: condividevano il buffer del `Tessellator` tra thread diversi (crash/corruzione) e il metodo `beginBufferBuilding` non esiste.
- `FrustumMixin`: non faceva nulla.
