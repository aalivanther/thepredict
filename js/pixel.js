/* ThePredict v4 · píxel: texturas 16×16 propias generadas por código (piedra, baldosas, hierro, bronce, tela, lámparas),
   atlas con mapa emisivo, remapeo de UV de cajas, iconos de la hotbar, corazones y sprites de partículas.
   Nada viene de fuera: todo se dibuja aquí con un RNG determinista, así cada carga pinta lo mismo. */
(function () {
  "use strict";
  var TP = window.TP;
  var THREE = window.THREE;
  var P = TP.pixel = {};
  var T = 16;

  /* ---------------------------------------------------------------------------------------- colour helpers */
  function hex(c) { return "#" + ("000000" + (c >>> 0).toString(16)).slice(-6); }
  function rgb(c) { return [(c >> 16) & 255, (c >> 8) & 255, c & 255]; }
  function fromRgb(r, g, b) { return (TP.clamp(Math.round(r), 0, 255) << 16) | (TP.clamp(Math.round(g), 0, 255) << 8) | TP.clamp(Math.round(b), 0, 255); }
  function shade(c, k) { var v = rgb(c); return fromRgb(v[0] * k, v[1] * k, v[2] * k); }
  function mix(a, b, t) { var x = rgb(a), y = rgb(b); return fromRgb(x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t); }
  P.hex = hex; P.shade = shade; P.mix = mix;

  function canvas(w, h) {
    var c = document.createElement("canvas");
    c.width = w; c.height = h;
    return c;
  }
  /* a tiny painter over a 16×16 canvas with integer pixels */
  function Painter(c, seed) {
    this.c = c;
    this.g = c.getContext("2d");
    this.w = c.width; this.h = c.height;
    this.rnd = TP.rng(seed || 1);
  }
  Painter.prototype.px = function (x, y, color) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) { return; }
    this.g.fillStyle = hex(color);
    this.g.fillRect(x, y, 1, 1);
  };
  Painter.prototype.rect = function (x, y, w, h, color) { this.g.fillStyle = hex(color); this.g.fillRect(x, y, w, h); };
  Painter.prototype.fill = function (color) { this.rect(0, 0, this.w, this.h, color); };
  Painter.prototype.noise = function (base, amount, chance) {
    for (var y = 0; y < this.h; y++) {
      for (var x = 0; x < this.w; x++) {
        if (chance !== undefined && this.rnd() > chance) { continue; }
        this.px(x, y, shade(base, 1 + (this.rnd() - 0.5) * 2 * amount));
      }
    }
  };
  Painter.prototype.speckle = function (color, chance) {
    for (var y = 0; y < this.h; y++) { for (var x = 0; x < this.w; x++) { if (this.rnd() < chance) { this.px(x, y, color); } } }
  };
  Painter.prototype.hline = function (x0, x1, y, color) { for (var x = x0; x <= x1; x++) { this.px(x, y, color); } };
  Painter.prototype.vline = function (x, y0, y1, color) { for (var y = y0; y <= y1; y++) { this.px(x, y, color); } };
  Painter.prototype.bricks = function (rows, base, mortar, variance) {
    /* rows: list of [y, height, offset] */
    var self = this;
    this.fill(mortar);
    rows.forEach(function (r) {
      var y = r[0], h = r[1], off = r[2], w = r[3] || 8;
      for (var x = -w; x < self.w + w; x += w) {
        var bx = x + off, k = 1 + (self.rnd() - 0.5) * variance;
        var col = shade(base, k);
        self.rect(bx, y, w - 1, h - 1, col);
        self.hline(bx, bx + w - 2, y, shade(col, 1.12));
        self.vline(bx, y, y + h - 2, shade(col, 1.08));
        self.hline(bx, bx + w - 2, y + h - 2, shade(col, 0.82));
        for (var n = 0; n < 4; n++) { self.px(bx + 1 + Math.floor(self.rnd() * (w - 2)), y + 1 + Math.floor(self.rnd() * (h - 3)), shade(col, 0.9 + self.rnd() * 0.2)); }
      }
    });
  };
  Painter.prototype.crack = function (x, y, len, color) {
    for (var i = 0; i < len; i++) {
      this.px(x, y, color);
      var r = this.rnd();
      if (r < 0.45) { y++; } else if (r < 0.75) { x += this.rnd() < 0.5 ? -1 : 1; } else { y++; x += this.rnd() < 0.5 ? -1 : 1; }
    }
  };
  Painter.prototype.bevel = function (light, dark) {
    this.hline(0, this.w - 1, 0, light); this.vline(0, 0, this.h - 1, light);
    this.hline(0, this.w - 1, this.h - 1, dark); this.vline(this.w - 1, 0, this.h - 1, dark);
  };

  /* ---------------------------------------------------------------------------------------- tile painters */
  var STONE = 0x8a857c, MORTAR = 0x4f4b46, DARK = 0x3a3733, TILE = 0x4a4642, POLISH = 0xa29c91, COBBLE = 0x6b6760;
  var IRON = 0x33363d, IRON_DARK = 0x1f2228, BRONZE = 0x7a5530, BRONZE_HI = 0xb98a4a, EMBER = 0xff6a2a, CYAN = 0x5ff0ff;
  var LEATHER = 0x5f6e3c, LEATHER_D = 0x3c4826, HOOD = 0x414956, SKIN = 0xd9a77a, BOOT = 0x4a3321;

  var TILES = {
    /* --------------------------------------------- world */
    brick: function (p) { p.bricks([[0, 6, 0], [6, 5, 4], [11, 5, 0]], STONE, MORTAR, 0.16); p.speckle(shade(STONE, 0.78), 0.03); },
    brick_b: function (p) { p.bricks([[0, 5, 2], [5, 6, 6], [11, 5, 1]], shade(STONE, 0.94), MORTAR, 0.18); },
    brick_cracked: function (p) { TILES.brick(p); p.crack(3 + Math.floor(p.rnd() * 6), 0, 14, shade(MORTAR, 0.7)); p.crack(10, 4, 8, shade(MORTAR, 0.75)); },
    brick_mossy: function (p) { TILES.brick_b(p); for (var i = 0; i < 26; i++) { var x = Math.floor(p.rnd() * 16), y = Math.floor(p.rnd() * 16); p.px(x, y, mix(STONE, 0x4f7a3a, 0.75 + p.rnd() * 0.25)); if (p.rnd() < 0.5) { p.px(x + 1, y, mix(STONE, 0x3f6a2f, 0.8)); } } },
    brick_dark: function (p) { p.bricks([[0, 6, 0], [6, 5, 4], [11, 5, 0]], shade(STONE, 0.62), shade(MORTAR, 0.6), 0.16); },
    brick_top: function (p) { p.bricks([[0, 8, 0], [8, 8, 8]], shade(STONE, 0.8), shade(MORTAR, 0.7), 0.1); },
    cobble: function (p) {
      p.fill(shade(COBBLE, 0.55));
      for (var i = 0; i < 14; i++) {
        var x = Math.floor(p.rnd() * 15), y = Math.floor(p.rnd() * 15), w = 2 + Math.floor(p.rnd() * 4), h = 2 + Math.floor(p.rnd() * 3);
        var c = shade(COBBLE, 0.8 + p.rnd() * 0.4);
        p.rect(x, y, w, h, c);
        p.hline(x, x + w - 1, y, shade(c, 1.15));
        p.hline(x, x + w - 1, y + h - 1, shade(c, 0.75));
      }
    },
    tile: function (p) {
      p.fill(TILE); p.noise(TILE, 0.12);
      p.bevel(shade(TILE, 1.25), shade(TILE, 0.62));
      p.hline(1, 14, 14, shade(TILE, 0.8)); p.vline(14, 1, 14, shade(TILE, 0.8));
      p.speckle(shade(TILE, 0.7), 0.04);
    },
    tile_b: function (p) { TILES.tile(p); p.crack(2, 2, 9, shade(TILE, 0.55)); },
    tile_polished: function (p) {
      p.fill(POLISH); p.noise(POLISH, 0.06);
      p.bevel(shade(POLISH, 1.14), shade(POLISH, 0.7));
      p.rect(7, 0, 1, 16, shade(POLISH, 0.86)); p.rect(0, 7, 16, 1, shade(POLISH, 0.86));
      p.rect(8, 0, 1, 16, shade(POLISH, 1.06)); p.rect(0, 8, 16, 1, shade(POLISH, 1.06));
    },
    tile_edge: function (p) { p.fill(shade(POLISH, 0.86)); p.noise(shade(POLISH, 0.86), 0.08); p.bevel(shade(POLISH, 1.05), shade(POLISH, 0.6)); p.rect(2, 2, 12, 12, shade(POLISH, 0.8)); p.rect(3, 3, 10, 10, shade(POLISH, 0.9)); },
    iron: function (p) { p.fill(IRON); p.noise(IRON, 0.1); p.bevel(shade(IRON, 1.5), shade(IRON, 0.5)); [[2, 2], [13, 2], [2, 13], [13, 13]].forEach(function (r) { p.px(r[0], r[1], shade(IRON, 1.7)); p.px(r[0] + 1, r[1] + 1, shade(IRON, 0.5)); }); },
    lamp_cyan: function (p) { p.fill(0x163c44); p.bevel(0x2b6a74, 0x0c2228); p.rect(3, 3, 10, 10, 0x1e5660); p.rect(5, 5, 6, 6, 0x5ff0ff); p.rect(6, 6, 4, 4, 0xd8ffff); },
    lamp_cyan_e: function (p) { p.fill(0x000000); p.rect(3, 3, 10, 10, 0x0e3a40); p.rect(5, 5, 6, 6, 0x4ce0ff); p.rect(6, 6, 4, 4, 0xe8ffff); },
    lamp_warm: function (p) { p.fill(0x3a2a16); p.bevel(0x6a4a24, 0x1c140a); p.rect(3, 3, 10, 10, 0x6a3b14); p.rect(5, 5, 6, 6, 0xff9a3c); p.rect(6, 6, 4, 4, 0xffe2a8); },
    lamp_warm_e: function (p) { p.fill(0x000000); p.rect(3, 3, 10, 10, 0x3a1c06); p.rect(5, 5, 6, 6, 0xff8a2a); p.rect(6, 6, 4, 4, 0xfff0c8); },
    wood: function (p) { p.fill(0x5a3b1e); for (var y = 0; y < 16; y++) { p.hline(0, 15, y, shade(0x5a3b1e, 0.85 + (y % 4 === 0 ? 0.3 : p.rnd() * 0.2))); } p.vline(5, 0, 15, 0x3e2812); p.vline(11, 0, 15, 0x3e2812); },
    door_glow: function (p) { p.fill(0x2a1a0c); for (var y = 0; y < 16; y++) { p.hline(0, 15, y, mix(0x1a1008, 0xff9a3c, 0.25 + 0.5 * (1 - y / 16))); } },
    door_glow_e: function (p) { for (var y = 0; y < 16; y++) { p.hline(0, 15, y, mix(0x1a0a00, 0xff9a3c, 0.3 + 0.5 * (1 - y / 16))); } },

    /* --------------------------------------------- the boss: El Augur */
    aug_iron: function (p) { p.fill(IRON_DARK); p.noise(IRON_DARK, 0.14); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5)); p.rect(2, 2, 12, 12, shade(IRON_DARK, 1.15)); p.noise(shade(IRON_DARK, 1.12), 0.1, 0.6); p.px(3, 3, shade(IRON_DARK, 2.2)); p.px(12, 3, shade(IRON_DARK, 2.2)); p.px(3, 12, shade(IRON_DARK, 2.2)); p.px(12, 12, shade(IRON_DARK, 2.2)); },
    aug_iron_b: function (p) { p.fill(IRON_DARK); p.noise(IRON_DARK, 0.14); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5)); p.hline(0, 15, 8, shade(IRON_DARK, 0.55)); p.hline(0, 15, 9, shade(IRON_DARK, 1.5)); },
    aug_bronze: function (p) { p.fill(BRONZE); p.noise(BRONZE, 0.14); p.bevel(BRONZE_HI, shade(BRONZE, 0.5)); p.rect(2, 2, 12, 2, shade(BRONZE_HI, 0.9)); p.rect(2, 12, 12, 2, shade(BRONZE, 0.7)); p.speckle(mix(BRONZE, 0x3f8a6a, 0.6), 0.05); },
    aug_pauldron: function (p) { p.fill(BRONZE); p.noise(BRONZE, 0.12); p.bevel(BRONZE_HI, shade(BRONZE, 0.45)); p.rect(0, 4, 16, 2, shade(BRONZE, 0.6)); p.rect(0, 6, 16, 1, BRONZE_HI); p.rect(0, 10, 16, 2, shade(BRONZE, 0.6)); p.rect(0, 12, 16, 1, BRONZE_HI); p.rect(6, 1, 4, 3, IRON_DARK); p.rect(7, 2, 2, 1, shade(IRON_DARK, 2)); },
    aug_chest: function (p) {
      p.fill(IRON_DARK); p.noise(IRON_DARK, 0.14); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5));
      p.rect(1, 1, 14, 3, shade(BRONZE, 0.9)); p.hline(1, 14, 1, BRONZE_HI);
      p.rect(5, 6, 6, 6, 0x0b0c10); p.rect(6, 7, 4, 4, 0x6a2210); p.rect(7, 8, 2, 2, EMBER);
      p.crack(4, 7, 5, 0x8a2a0a); p.crack(12, 6, 6, 0x8a2a0a); p.crack(3, 12, 4, 0x6a1e08);
    },
    aug_chest_e: function (p) { p.fill(0); p.rect(6, 7, 4, 4, 0x6a1e08); p.rect(7, 8, 2, 2, 0xffa050); p.crack(4, 7, 5, 0xd04a10); p.crack(12, 6, 6, 0xd04a10); },
    aug_back: function (p) { p.fill(IRON_DARK); p.noise(IRON_DARK, 0.14); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5)); p.vline(7, 1, 14, shade(IRON_DARK, 0.5)); p.vline(8, 1, 14, shade(IRON_DARK, 1.5)); p.rect(1, 1, 14, 3, shade(BRONZE, 0.85)); },
    aug_face: function (p) {
      p.fill(IRON_DARK); p.noise(IRON_DARK, 0.12); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5));
      p.rect(1, 1, 14, 3, shade(BRONZE, 0.95)); p.hline(1, 14, 1, BRONZE_HI);
      p.rect(2, 6, 12, 3, 0x05070a); p.rect(3, 7, 10, 1, CYAN);
      p.rect(6, 10, 4, 5, shade(IRON_DARK, 0.8)); p.vline(7, 10, 14, shade(IRON_DARK, 0.5)); p.vline(8, 10, 14, shade(IRON_DARK, 1.4));
      p.vline(4, 10, 14, shade(IRON_DARK, 0.6)); p.vline(11, 10, 14, shade(IRON_DARK, 0.6));
    },
    aug_face_e: function (p) { p.fill(0); p.rect(2, 6, 12, 3, 0x0a2a30); p.rect(3, 7, 10, 1, 0xa0ffff); p.rect(4, 7, 8, 1, 0xffffff); },
    aug_helm: function (p) { p.fill(IRON_DARK); p.noise(IRON_DARK, 0.12); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5)); p.rect(1, 1, 14, 3, shade(BRONZE, 0.95)); p.hline(1, 14, 1, BRONZE_HI); p.rect(6, 5, 4, 10, shade(IRON_DARK, 1.2)); },
    aug_horn: function (p) { p.fill(0x3a3124); p.noise(0x3a3124, 0.16); p.bevel(0x6b5a44, 0x1c1710); p.hline(0, 15, 5, 0x1c1710); p.hline(0, 15, 10, 0x1c1710); },
    aug_arm: function (p) { p.fill(IRON_DARK); p.noise(IRON_DARK, 0.14); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5)); p.rect(0, 3, 16, 2, shade(BRONZE, 0.8)); p.rect(0, 11, 16, 2, shade(BRONZE, 0.8)); p.hline(0, 15, 3, BRONZE_HI); p.hline(0, 15, 11, BRONZE_HI); },
    aug_fist: function (p) { p.fill(shade(IRON_DARK, 0.9)); p.noise(IRON_DARK, 0.14); p.bevel(shade(IRON_DARK, 1.8), shade(IRON_DARK, 0.5)); p.rect(2, 4, 12, 1, 0x05070a); p.rect(2, 8, 12, 1, 0x05070a); p.rect(2, 12, 12, 1, 0x05070a); },
    aug_leg: function (p) { p.fill(IRON_DARK); p.noise(IRON_DARK, 0.14); p.bevel(shade(IRON_DARK, 1.9), shade(IRON_DARK, 0.5)); p.rect(0, 0, 16, 3, shade(BRONZE, 0.85)); p.hline(0, 15, 0, BRONZE_HI); p.rect(3, 7, 10, 2, shade(IRON_DARK, 1.3)); },
    aug_boot: function (p) { p.fill(shade(IRON_DARK, 0.85)); p.noise(IRON_DARK, 0.12); p.bevel(shade(IRON_DARK, 1.6), shade(IRON_DARK, 0.4)); p.rect(0, 0, 16, 2, shade(BRONZE, 0.8)); },
    aug_cloth: function (p) { p.fill(0x5a1218); p.noise(0x5a1218, 0.18); for (var i = 0; i < 5; i++) { p.vline(Math.floor(p.rnd() * 16), 8 + Math.floor(p.rnd() * 4), 15, 0x2a0a0c); } p.hline(0, 15, 15, 0x2a0a0c); },
    aug_cloth_end: function (p) { p.fill(0x5a1218); p.noise(0x5a1218, 0.18); for (var x = 0; x < 16; x += 3) { p.g.clearRect(x, 11 + Math.floor(p.rnd() * 4), 2, 5); } },
    aug_shaft: function (p) { p.fill(0x2a2118); p.noise(0x2a2118, 0.16); p.hline(0, 15, 0, 0x4a3a2a); p.hline(0, 15, 15, 0x120d08); p.rect(0, 6, 16, 1, BRONZE); p.rect(0, 10, 16, 1, BRONZE); },
    aug_blade: function (p) { p.fill(0x6f7580); p.noise(0x6f7580, 0.1); p.bevel(0xb9c0cc, 0x2a2e36); p.rect(0, 0, 3, 16, 0xc9d2de); p.vline(0, 0, 15, 0xe8f6ff); p.vline(3, 0, 15, 0x40464f); p.rect(12, 2, 3, 12, 0x3f3a30); p.crack(7, 1, 9, EMBER); },
    aug_blade_e: function (p) { p.fill(0); p.vline(0, 0, 15, 0x9fe8ff); p.vline(1, 0, 15, 0x5fc8ff); p.crack(7, 1, 9, 0xff8a40); },

    /* --------------------------------------------- the player */
    pl_face: function (p) {
      p.fill(HOOD); p.noise(HOOD, 0.04);
      p.rect(0, 0, 16, 3, shade(HOOD, 0.8)); p.rect(1, 3, 14, 1, shade(HOOD, 0.9));
      p.rect(3, 4, 10, 10, SKIN); p.noise(SKIN, 0.03, 0.3);
      p.rect(3, 4, 10, 1, shade(SKIN, 0.88));
      p.rect(4, 7, 3, 2, 0xffffff); p.rect(9, 7, 3, 2, 0xffffff); p.rect(5, 7, 2, 2, 0x2b4a6a); p.rect(10, 7, 2, 2, 0x2b4a6a);
      p.rect(7, 10, 2, 1, shade(SKIN, 0.75)); p.rect(5, 12, 6, 1, 0x8a4a3a);
      p.rect(0, 14, 16, 2, shade(HOOD, 0.7)); p.vline(0, 3, 13, shade(HOOD, 0.85)); p.vline(15, 3, 13, shade(HOOD, 0.85));
    },
    pl_head: function (p) { p.fill(HOOD); p.noise(HOOD, 0.04); p.rect(0, 0, 16, 3, shade(HOOD, 0.8)); p.rect(0, 13, 16, 3, shade(HOOD, 0.7)); p.vline(5, 3, 12, shade(HOOD, 0.9)); p.vline(10, 3, 12, shade(HOOD, 0.9)); },
    pl_head_top: function (p) { p.fill(shade(HOOD, 0.85)); p.noise(HOOD, 0.04); p.rect(5, 5, 6, 6, shade(HOOD, 0.75)); },
    pl_torso: function (p) {
      p.fill(LEATHER); p.noise(LEATHER, 0.04);
      p.rect(0, 0, 16, 2, shade(LEATHER, 1.12)); p.rect(5, 0, 6, 3, shade(HOOD, 0.85));
      p.rect(0, 9, 16, 2, 0x3a2816); p.rect(6, 9, 4, 2, 0xb98a4a); p.rect(7, 9, 2, 2, 0x6a4a24);
      p.rect(3, 3, 1, 6, LEATHER_D); p.rect(12, 3, 1, 6, LEATHER_D); p.rect(0, 11, 16, 5, shade(LEATHER, 0.9));
      p.rect(2, 12, 3, 3, 0x4a3321); p.rect(11, 12, 3, 3, 0x4a3321);
    },
    pl_torso_back: function (p) { p.fill(LEATHER); p.noise(LEATHER, 0.04); p.rect(0, 0, 16, 2, shade(LEATHER, 1.12)); p.rect(4, 2, 8, 7, shade(LEATHER, 0.88)); p.rect(0, 9, 16, 2, 0x3a2816); p.rect(2, 1, 2, 9, 0x4a3321); p.rect(12, 1, 2, 9, 0x4a3321); },
    pl_torso_side: function (p) { p.fill(LEATHER); p.noise(LEATHER, 0.04); p.rect(0, 9, 16, 2, 0x3a2816); },
    pl_arm: function (p) { p.fill(LEATHER); p.noise(LEATHER, 0.04); p.rect(0, 0, 16, 3, shade(LEATHER, 1.12)); p.rect(0, 10, 16, 6, SKIN); p.noise(SKIN, 0.03, 0.3); p.rect(0, 9, 16, 1, 0x4a3321); p.rect(0, 13, 16, 3, 0x3a2816); },
    pl_leg: function (p) { p.fill(0x3b4451); p.noise(0x3b4451, 0.12); p.rect(0, 10, 16, 6, BOOT); p.rect(0, 10, 16, 1, shade(BOOT, 1.4)); p.rect(0, 0, 16, 2, 0x3a2816); p.vline(7, 2, 9, shade(0x3b4451, 0.8)); },
    pl_sole: function (p) { p.fill(shade(BOOT, 0.7)); },
    bow: function (p) { p.fill(0x5a3b1e); p.noise(0x5a3b1e, 0.15); p.vline(0, 0, 15, 0x8a6a3a); p.vline(15, 0, 15, 0x2e1c0c); },
    bow_string: function (p) { p.fill(0xd8d2c0); },
    sword: function (p) { p.fill(0xbfc8d4); p.noise(0xbfc8d4, 0.08); p.vline(0, 0, 15, 0xf0f6ff); p.vline(15, 0, 15, 0x5a6472); },
    sword_hilt: function (p) { p.fill(0x3a2816); p.rect(0, 0, 16, 4, 0xb98a4a); },
    shield: function (p) { p.fill(0x4a3321); p.noise(0x4a3321, 0.12); p.bevel(0x8a6a3a, 0x1c1208); p.rect(2, 2, 12, 12, 0x3b4a7a); p.rect(6, 2, 4, 12, 0xc9a24a); p.rect(2, 6, 12, 4, 0xc9a24a); p.rect(6, 6, 4, 4, 0xe8c86a); },
    shield_back: function (p) { p.fill(0x4a3321); p.noise(0x4a3321, 0.12); p.rect(3, 6, 10, 4, 0x2e1c0c); },
    potion: function (p) { p.fill(0xffffff); p.g.clearRect(0, 0, 16, 16); p.rect(5, 0, 6, 3, 0x3a2816); p.rect(3, 3, 10, 13, 0x9ad8ff); p.rect(4, 6, 8, 9, 0xff5aa8); p.rect(4, 6, 8, 2, 0xff9ad0); p.vline(3, 3, 15, 0xd8f0ff); },
    apple: function (p) { p.fill(0xff3b4a); p.noise(0xff3b4a, 0.1); p.rect(0, 0, 16, 2, 0x5a3b1e); p.rect(2, 2, 4, 3, 0xff9aa0); },
    blank: function (p) { p.fill(0x000000); }
  };
  P.TILES = TILES;

  var tileCache = {};
  P.tile = function (name) {
    if (tileCache[name]) { return tileCache[name]; }
    var c = canvas(T, T);
    var painter = new Painter(c, hashName(name));
    var fn = TILES[name];
    if (fn) { fn(painter); } else { painter.fill(0xff00ff); }
    tileCache[name] = c;
    return c;
  };
  function hashName(name) { var h = 7; for (var i = 0; i < name.length; i++) { h = (h * 31 + name.charCodeAt(i)) >>> 0; } return h || 1; }

  function pixelTexture(c) {
    var tex = new THREE.CanvasTexture(c);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestMipmapLinearFilter;
    tex.generateMipmaps = true;
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.anisotropy = 1;
    return tex;
  }
  var texCache = {};
  /* a single repeating tile as a texture (world blocks) */
  P.texture = function (name) {
    if (!texCache[name]) {
      var tex = pixelTexture(P.tile(name));
      tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
      texCache[name] = tex;
    }
    return texCache[name];
  };
  P.emissiveTexture = function (name) {
    var key = name + "_e";
    if (!TILES[key]) { return null; }
    return P.texture(key);
  };

  /* ---------------------------------------------------------------------------------------- atlas */
  /* an atlas of tiles for one actor: colour map plus an emissive map with the same layout */
  P.atlas = function (names) {
    var cols = Math.ceil(Math.sqrt(names.length));
    var rows = Math.ceil(names.length / cols);
    var c = canvas(cols * T, rows * T), e = canvas(cols * T, rows * T);
    var g = c.getContext("2d"), ge = e.getContext("2d");
    ge.fillStyle = "#000"; ge.fillRect(0, 0, e.width, e.height);
    var map = {};
    names.forEach(function (name, i) {
      var cx = (i % cols) * T, cy = Math.floor(i / cols) * T;
      g.drawImage(P.tile(name), cx, cy);
      if (TILES[name + "_e"]) { ge.drawImage(P.tile(name + "_e"), cx, cy); }
      map[name] = [cx / c.width, 1 - (cy + T) / c.height, (cx + T) / c.width, 1 - cy / c.height];
    });
    var tex = pixelTexture(c), tex2 = pixelTexture(e);
    tex2.colorSpace = THREE.SRGBColorSpace;
    return { texture: tex, emissive: tex2, uv: function (name) { return map[name] || map[names[0]]; }, canvas: c };
  };

  /* remap the uvs of a BoxGeometry to atlas tiles: faces in three's order +x, -x, +y, -y, +z, -z.
     tiles: a name, or {px,nx,py,ny,pz,nz} with a default in "all"; rot: quarter turns per face (optional) */
  P.boxUV = function (geo, atlas, tiles) {
    var uv = geo.attributes.uv;
    var order = ["px", "nx", "py", "ny", "pz", "nz"];
    for (var f = 0; f < 6; f++) {
      var name = typeof tiles === "string" ? tiles : (tiles[order[f]] || tiles.all);
      var r = atlas.uv(name);
      var rot = typeof tiles === "object" && tiles.rot ? (tiles.rot[order[f]] || 0) : 0;
      for (var v = 0; v < 4; v++) {
        var i = f * 4 + v;
        var u = uv.getX(i), w = uv.getY(i);
        for (var k = 0; k < rot; k++) { var t = u; u = w; w = 1 - t; }
        uv.setXY(i, r[0] + u * (r[2] - r[0]), r[1] + w * (r[3] - r[1]));
      }
    }
    uv.needsUpdate = true;
    return geo;
  };

  /* merge several BoxGeometries (same attributes) into one, so a limb is one draw call */
  P.merge = function (list) {
    var total = 0, totalIdx = 0;
    list.forEach(function (g) { total += g.attributes.position.count; totalIdx += g.index.count; });
    var pos = new Float32Array(total * 3), nor = new Float32Array(total * 3), uv = new Float32Array(total * 2), idx = new Uint16Array(totalIdx);
    var o = 0, oi = 0;
    list.forEach(function (g) {
      pos.set(g.attributes.position.array, o * 3);
      nor.set(g.attributes.normal.array, o * 3);
      uv.set(g.attributes.uv.array, o * 2);
      var ia = g.index.array;
      for (var i = 0; i < ia.length; i++) { idx[oi + i] = ia[i] + o; }
      o += g.attributes.position.count;
      oi += ia.length;
    });
    var out = new THREE.BufferGeometry();
    out.setAttribute("position", new THREE.BufferAttribute(pos, 3));
    out.setAttribute("normal", new THREE.BufferAttribute(nor, 3));
    out.setAttribute("uv", new THREE.BufferAttribute(uv, 2));
    out.setIndex(new THREE.BufferAttribute(idx, 1));
    out.computeBoundingSphere();
    return out;
  };
  /* a textured box positioned at (x, y, z) with size (w, h, d), as a geometry ready to merge */
  P.box = function (atlas, tiles, w, h, d, x, y, z) {
    var g = new THREE.BoxGeometry(w, h, d);
    P.boxUV(g, atlas, tiles);
    g.translate(x || 0, y || 0, z || 0);
    return g;
  };

  /* ---------------------------------------------------------------------------------------- particles */
  var SPRITES = {
    square: [[1, 1, 6, 6]],
    plus: [[3, 1, 2, 6], [1, 3, 6, 2]],
    diamond: [[3, 0, 2, 8], [2, 1, 4, 6], [1, 2, 6, 4], [0, 3, 8, 2]],
    dot: [[2, 2, 4, 4]],
    spark: [[3, 0, 2, 8], [0, 3, 8, 2], [2, 2, 4, 4]]
  };
  var spriteCache = {};
  P.sprite = function (kind) {
    if (spriteCache[kind]) { return spriteCache[kind]; }
    var c = canvas(8, 8), g = c.getContext("2d");
    g.fillStyle = "#fff";
    (SPRITES[kind] || SPRITES.square).forEach(function (r) { g.fillRect(r[0], r[1], r[2], r[3]); });
    var tex = new THREE.CanvasTexture(c);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.generateMipmaps = false;
    spriteCache[kind] = tex;
    return tex;
  };

  /* ---------------------------------------------------------------------------------------- HUD icons */
  /* 16×16 icons as 2-d arrays of palette indices, drawn into canvases the HUD inserts directly */
  var PAL = { ".": null, w: 0xffffff, k: 0x1a1a1a, g: 0x8a8f99, s: 0xcfd6e0, b: 0x5a3b1e, B: 0x8a6a3a, r: 0xff3b4a, R: 0x8a1420, p: 0xff5aa8, c: 0x9ad8ff, y: 0xffd25a, Y: 0xb98a4a, G: 0x4fe3a1, o: 0xff9a3c, d: 0x3b4a7a, t: 0x59e1ff, e: 0x3a2816, n: 0x6a4a24 };
  var ICONS = {
    sword: ["...............s", "..............ss", ".............ss.", "............ss..", "...........ss...", "..........ss....", ".........ss.....", "..Y.....ss......", "...Y...ss.......", "....Y.ss........", ".....Ys.........", "....YYY.........", "...Y.bY.........", "..b...bY........", ".b.......Y......", "................"],
    bow: ["......bb........", "....bb..w.......", "...b.....w......", "..b......w......", "..b......w......", ".b.......w......", ".b.......w......", ".b.......w......", ".b.......w......", "..b......w......", "..b......w......", "...b.....w......", "....bb..w.......", "......bb........", "................", "................"],
    shield: [".bbbbbbbbbbbbbb.", "bBddddddddddddBb", "bBddddyyyyddddBb", "bBddddyyyyddddBb", "bByyyyyyyyyyyyBb", "bByyyyyyyyyyyyBb", "bBddddyyyyddddBb", "bBddddyyyyddddBb", "bBddddyyyyddddBb", ".bBddddyyddddBb.", ".bBddddyyddddBb.", "..bBdddyydddBb..", "...bBddyyddBb...", "....bBdyydBb....", ".....bBBBBb.....", "......bbbb......"],
    potion: ["......eeee......", "......cwwc......", ".......cc.......", "......cwwc......", ".....cwwwwc.....", "....cwwwwwwc....", "...cwpppppwwc...", "...cpppppppwc...", "...cpppppppwc...", "...cpRpppppwc...", "...cpRpppppwc...", "...cpppppppwc...", "....cpppppwc....", ".....cwwwwc.....", "......cccc......", "................"],
    apple: [".......bb.......", "......b.........", "....rrr..GG.....", "...rrrrrrGGr....", "..rrwrrrrrrrr...", "..rwrrrrrrrrr...", "..rrrrrrrrrrr...", "..rrrrrrrrrrr...", "..rrrrrrrrrrr...", "..rrrrrrrrrrr...", "...rrrrrrrrr....", "...RrrrrrrrR....", "....RRRRRRR.....", "................", "................", "................"],
    arrow: ["...............s", "..............ss", ".............sss", "............ss..", "...........s....", "..........s.....", ".........s......", "........s.......", ".......s........", "......s.........", ".....s..........", "..ww.s..........", "..www...........", "..wwww..........", "................", "................"],
    torch: ["......oo........", ".....oyyo.......", ".....oyyo.......", "......oo........", "......kk........", ".....bbbb.......", ".....bbbb.......", "......bb........", "......bb........", "......bb........", "......bb........", "......bb........", "......bb........", "......bb........", "................", "................"],
    book: ["..bbbbbbbbbbb...", ".bssssssssssbb..", ".bssssssssssbb..", ".bsssttttsssbb..", ".bssssssssssbb..", ".bsssttttsssbb..", ".bssssssssssbb..", ".bsssttttsssbb..", ".bssssssssssbb..", ".bssssssssssbb..", ".bssssssssssbb..", ".bbbbbbbbbbbbb..", "..bbbbbbbbbbb...", "................", "................", "................"],
    totem: ["......yyyy......", ".....yGGGGy.....", ".....yGkkGy.....", ".....yGGGGy.....", "....yyyyyyyy....", "...yyGGGGGGyy...", "..yyGGyyyyGGyy..", "..yyGGyyyyGGyy..", "...yyGGGGGGyy...", "....yyyyyyyy....", ".....yGGGGy.....", ".....yGGGGy.....", ".....yyyyyy.....", "................", "................", "................"],
    heart: [".ww.....ww......", "wrrw...wrrw.....", "wrrrw.wrrrw.....", "wrrrrwrrrrw.....", "wrrrrrrrrrw.....", ".wrrrrrrrw......", "..wrrrrrw.......", "...wrrrw........", "....wrw.........", ".....w..........", "................", "................", "................", "................", "................", "................"],
    heart_empty: [".kk.....kk......", "kggk...kggk.....", "kgggk.kgggk.....", "kggggkggggk.....", "kgggggggggk.....", ".kgggggggk......", "..kgggggk.......", "...kgggk........", "....kgk.........", ".....k..........", "................", "................", "................", "................", "................", "................"],
    heart_gold: [".ww.....ww......", "wyyw...wyyw.....", "wyyyw.wyyyw.....", "wyyyywyyyyw.....", "wyyyyyyyyyw.....", ".wyyyyyyyw......", "..wyyyyyw.......", "...wyyyw........", "....wyw.........", ".....w..........", "................", "................", "................", "................", "................", "................"],
    armor: ["...kk...kk......", "..kssk.kssk.....", ".kssssksssk.....", ".kssssssssk.....", ".kssssssssk.....", ".ksskssksssk....", "..ksssssssk.....", "..ksssssssk.....", "...kssssk.......", "....kkkk........", "................", "................", "................", "................", "................", "................"],
    armor_empty: ["...kk...kk......", "..kggk.kggk.....", ".kggggkgggk.....", ".kggggggggk.....", ".kggggggggk.....", ".kggkggkgggk....", "..kgggggggk.....", "..kgggggggk.....", "...kggggk.......", "....kkkk........", "................", "................", "................", "................", "................", "................"]
  };
  P.icon = function (name, scale) {
    var rows = ICONS[name] || ICONS.sword;
    var s = scale || 1;
    var c = canvas(16 * s, 16 * s), g = c.getContext("2d");
    for (var y = 0; y < rows.length; y++) {
      for (var x = 0; x < rows[y].length; x++) {
        var col = PAL[rows[y][x]];
        if (col === null || col === undefined) { continue; }
        g.fillStyle = hex(col);
        g.fillRect(x * s, y * s, s, s);
      }
    }
    c.className = "px-icon px-" + name;
    c.setAttribute("aria-hidden", "true");
    return c;
  };
  P.ICONS = Object.keys(ICONS);
})();
