# Créditos y licencias de la landing v4

Todo lo que carga la página está en `site/`; no hace ninguna petición de red. Cada recurso de terceros se lista aquí con su licencia y su origen. Lo que no aparece es propio (hecho por código para esta página).

## Librerías

| Recurso | Versión | Licencia | Origen | Dónde |
|---|---|---|---|---|
| three.js | 0.170.0 (r170) | MIT | npm `three` (caché local; sha512 en `vendor/three/VERSION.txt`) | `vendor/three/three.iife.min.js`, `vendor/three/three-addons.iife.js` (EffectComposer, RenderPass, UnrealBloomPass, OutputPass, ShaderPass, RoomEnvironment, RoundedBoxGeometry) |

## Fuentes

| Fuente | Versión | Licencia | Origen | Dónde |
|---|---|---|---|---|
| Pixelify Sans (variable 400–700, subconjunto latin) | Google Fonts v3 (`CHylV-3HFUT7aC4iv1TxGDR9Jn0Ei0tSaJ0.woff2`) | SIL OFL 1.1 | <https://fonts.google.com/specimen/Pixelify+Sans> · proyecto <https://github.com/eifetx/Pixelify-Sans> · descargado el 2026-10-15 de `fonts.gstatic.com` (sha256 `0f2216c1b7bf13b775e7b825c15e1c8414f3899cbd4120f275cdc96001a053a0`) | `vendor/fonts/pixelify-sans-latin-wght-normal.woff2`, licencia en `vendor/fonts/PixelifySans-OFL.txt` |
| Inter (variable, latin, eje opsz) | 5.3.0 (`@fontsource-variable/inter`) | SIL OFL 1.1 | npm (caché local) | `vendor/fonts/inter-latin-opsz-normal.woff2`, `vendor/fonts/Inter-OFL.txt` |
| JetBrains Mono (variable, latin, eje wght) | 5.3.0 (`@fontsource-variable/jetbrains-mono`) | SIL OFL 1.1 | npm (caché local) | `vendor/fonts/jetbrains-mono-latin-wght-normal.woff2`, `vendor/fonts/JetBrainsMono-OFL.txt` |

## Propio (sin licencia de terceros)

- Texturas pixel 16×16 (piedra, baldosas, hierro, bronce, tela, lámparas), iconos de la hotbar, corazones, sprites de partículas: generados por código en `js/pixel.js`.
- Modelos de bloques del boss «El Augur» y del jugador, y la arena «Cripta del Augur»: `js/actors.js` y `js/world.js`.
- Efectos (tajos, chispas, fuego, columnas, telegraphs, proyectiles): `js/fx.js`.
- Interfaz del juego (boss bar, corazones, hotbar, action bar, subtítulos, tarjeta de build): `js/hud.js` y `styles.css`.
- Sonido: sintetizado por WebAudio en `js/audio.js` (apagado por defecto).

No se usa ningún recurso de Mojang (ni texturas, ni fuente, ni logos, ni personajes) ni ningún material de las capturas o del video de referencia (modelo, marca, textos o música de terceros).
