# Prévia: mobs de Dragon Ball voando

Prévia 3D aprovada pelo Stark em 2026-11-12 ("a melhor coisa que eu já vi num mod de anime").
Artifact: https://claude.ai/artifact/NPC64JgjpffzQCoWEZC2bj

- `page.html`: a página (passagens no céu + combate aéreo). A IA do combate é a que foi portada em
  `boss/AirCombat.java`; as passagens em `universe/SkyPassers.java`.
- `build.py`: embute as skins (`src/main/resources/assets/battlegroundsmod/textures/entity/<nome>.png`,
  copiadas ao lado como goku.png etc.) e gera `db-voo.html`.
- `shot.js` / `lab.js`: prints com Playwright e log da IA (`window.__set/__step/__lab`).
