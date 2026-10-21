/* ThePredict v4 · interfaz del juego de diseño propio, en DOM: boss bar, línea de debug, corazones, armadura, XP,
   hotbar con iconos pixel propios, action bar con la intención del boss, subtítulos, tarjeta de build y avisos.
   La escena activa la rellena cada fotograma; nada de esto viene de un juego real. */
(function () {
  "use strict";
  var TP = window.TP;
  var P = TP.pixel;
  var H = TP.hud = {};

  TP.addText({
    hudMind: "mind: ThePredict", hudNative: "mind: IA nativa", hudSkills: "skills: las del autor",
    hudBuild: "BUILD", hudPredict: "ThePredict ›", hudNativeTag: "IA nativa ›",
    subHit: "Golpe", subParry: "Parry", subBolt: "Bolt lanzado", subLeap: "Dash", subSlam: "Slam", subArrow: "Flecha",
    subBlock: "Escudo", subDrink: "Poción", subStep: "Pasos", subGuard: "Guardia", subWhiff: "Golpe al aire",
    subAwake: "El Augur despierta", subHeal: "Curación", subBreak: "Guardia rota"
  }, {
    hudMind: "mind: ThePredict", hudNative: "mind: native AI", hudSkills: "skills: the author's",
    hudBuild: "BUILD", hudPredict: "ThePredict ›", hudNativeTag: "Native AI ›",
    subHit: "Hit", subParry: "Parry", subBolt: "Bolt cast", subLeap: "Dash", subSlam: "Slam", subArrow: "Arrow",
    subBlock: "Shield", subDrink: "Potion", subStep: "Footsteps", subGuard: "Guard", subWhiff: "Whiff",
    subAwake: "The Augur awakens", subHeal: "Healing", subBreak: "Guard broken"
  });

  var root = null, els = {};
  var state = { hearts: 20, armor: 10, absorb: 0, xp: 0.4, slot: 0, bossHp: 1, bossName: "", bossOn: false, hotbar: [] };
  var SLOTS = ["sword", "bow", "shield", "potion", "apple", "arrow", "torch", "book", "totem"];

  function build(host) {
    root = host;
    TP.clear(root);
    /* top: debug line and the boss bar */
    els.f3 = TP.el("div", { "class": "hud-f3", "aria-hidden": "true" }, root);
    var boss = TP.el("div", { "class": "hud-boss" }, root);
    els.bossName = TP.el("div", { "class": "hud-boss-name" }, boss);
    var bar = TP.el("div", { "class": "hud-boss-bar" }, boss);
    els.bossFill = TP.el("div", { "class": "hud-boss-fill" }, bar);
    els.bossFlash = TP.el("div", { "class": "hud-boss-flash" }, bar);
    for (var i = 1; i < 10; i++) { TP.el("i", { style: "left:" + (i * 10) + "%" }, bar); }
    els.boss = boss;
    /* centre: announcements */
    els.announce = TP.el("div", { "class": "hud-announce", "aria-live": "polite" }, root);
    els.crosshair = TP.el("div", { "class": "hud-crosshair", "aria-hidden": "true" }, root);
    /* bottom-right subtitles */
    els.subs = TP.el("div", { "class": "hud-subs", "aria-hidden": "true" }, root);
    /* bottom-left build card */
    var card = TP.el("div", { "class": "hud-card" }, root);
    var icon = TP.el("div", { "class": "hud-card-icon" }, card);
    icon.appendChild(P.icon("sword", 3));
    var body = TP.el("div", { "class": "hud-card-body" }, card);
    els.cardTag = TP.el("div", { "class": "hud-card-tag" }, body);
    els.cardTitle = TP.el("div", { "class": "hud-card-title" }, body);
    els.cardLine = TP.el("div", { "class": "hud-card-line" }, body);
    els.cardMind = TP.el("div", { "class": "hud-card-mind" }, body);
    els.card = card;
    els.cardIcon = icon;
    /* bottom centre: action bar, status rows, hotbar */
    var bottom = TP.el("div", { "class": "hud-bottom" }, root);
    els.action = TP.el("div", { "class": "hud-action" }, bottom);
    els.actionTag = TP.el("span", { "class": "hud-action-tag" }, els.action);
    els.actionText = TP.el("span", { "class": "hud-action-text" }, els.action);
    var rows = TP.el("div", { "class": "hud-rows" }, bottom);
    els.armor = TP.el("div", { "class": "hud-row hud-armor" }, rows);
    els.hearts = TP.el("div", { "class": "hud-row hud-hearts" }, rows);
    for (i = 0; i < 10; i++) { els.armor.appendChild(P.icon("armor", 2)); }
    els.heartIcons = [];
    for (i = 0; i < 10; i++) { var h = TP.el("span", { "class": "hud-heart" }, els.hearts); els.heartIcons.push(h); }
    var xp = TP.el("div", { "class": "hud-xp" }, bottom);
    els.xpFill = TP.el("div", { "class": "hud-xp-fill" }, xp);
    els.xpNum = TP.el("div", { "class": "hud-xp-num" }, xp);
    els.hotbar = TP.el("div", { "class": "hud-hotbar" }, bottom);
    els.slots = [];
    SLOTS.forEach(function (name, k) {
      var slot = TP.el("div", { "class": "hud-slot" }, els.hotbar);
      slot.appendChild(P.icon(name, 2));
      if (name === "arrow" || name === "apple" || name === "torch") { TP.el("span", { "class": "hud-count" }, slot, name === "arrow" ? "64" : name === "apple" ? "12" : "7"); }
      els.slots.push(slot);
    });
    els.bottom = bottom;
    /* split captions */
    els.splitL = TP.el("div", { "class": "hud-split hud-split-l" }, root);
    els.splitR = TP.el("div", { "class": "hud-split hud-split-r" }, root);
    els.splitLine = TP.el("div", { "class": "hud-split-line" }, root);
    /* the mind panel (chess lines) */
    els.mind = TP.el("div", { "class": "hud-mind" }, root);
    els.mindTitle = TP.el("div", { "class": "hud-mind-title" }, els.mind);
    els.mindLines = TP.el("div", { "class": "hud-mind-lines" }, els.mind);
    els.mindFoot = TP.el("div", { "class": "hud-mind-foot" }, els.mind);
    H.setHearts(20, 0);
    H.setArmor(10);
    H.setXp(0.4, 37);
    H.selectSlot(0);
    H.reset();
  }

  /* ------------------------------------------------------------------------------------------ api */
  H.init = function () { if (root) { return; } var host = document.getElementById("hud"); if (host) { build(host); } };
  TP.module("hud", H.init);
  H.show = function (on) { if (root) { TP.toggle(root, "is-on", on); } };
  H.layout = function (parts) {
    /* which parts are visible: {boss, bottom, card, subs, f3, crosshair, split, mind} */
    var all = { boss: els.boss, bottom: els.bottom, card: els.card, subs: els.subs, f3: els.f3, crosshair: els.crosshair, mind: els.mind };
    Object.keys(all).forEach(function (k) { TP.toggle(all[k], "is-on", !!parts[k]); });
    TP.toggle(els.splitL, "is-on", !!parts.split);
    TP.toggle(els.splitR, "is-on", !!parts.split);
    TP.toggle(els.splitLine, "is-on", !!parts.split);
  };
  H.reset = function () {
    H.setBoss("", 1);
    H.action("", "");
    H.announce("");
    TP.clear(els.subs);
    subsList = [];
    H.layout({});
  };
  H.setBoss = function (name, hp, hurt) {
    if (state.bossName !== name) { state.bossName = name; TP.setText(els.bossName, name); }
    var pct = Math.round(TP.clamp(hp, 0, 1) * 1000) / 10;
    if (state.bossHp !== pct) { state.bossHp = pct; els.bossFill.style.width = pct + "%"; }
    TP.toggle(els.boss, "is-hurt", !!hurt);
  };
  H.setHearts = function (hp, absorb) {
    hp = TP.clamp(hp, 0, 20);
    if (state.hearts === hp && state.absorb === (absorb || 0)) { return; }
    state.hearts = hp; state.absorb = absorb || 0;
    for (var i = 0; i < 10; i++) {
      var full = hp >= (i + 1) * 2, half = !full && hp > i * 2;
      var icon = els.heartIcons[i];
      var want = state.absorb > i * 2 ? "heart_gold" : full ? "heart" : half ? "heart_half" : "heart_empty";
      if (icon.getAttribute("data-k") !== want) {
        icon.setAttribute("data-k", want);
        TP.clear(icon);
        if (want === "heart_half") { icon.appendChild(P.icon("heart_empty", 2)); var top = P.icon("heart", 2); top.className += " px-half"; icon.appendChild(top); }
        else { icon.appendChild(P.icon(want, 2)); }
      }
    }
    TP.toggle(els.hearts, "is-low", hp <= 6);
  };
  H.setArmor = function (n) {
    if (state.armor === n) { return; }
    state.armor = n;
    var icons = els.armor.children;
    for (var i = 0; i < icons.length; i++) { icons[i].style.opacity = n > i ? "1" : "0.25"; }
  };
  H.setXp = function (k, level) { els.xpFill.style.width = Math.round(k * 100) + "%"; TP.setText(els.xpNum, String(level)); };
  H.selectSlot = function (i) {
    if (!els.slots) { return; }
    if (state.slot === i && els.slots[i].classList.contains("is-sel")) { return; }
    state.slot = i;
    els.slots.forEach(function (s, k) { TP.toggle(s, "is-sel", k === i); });
  };
  H.slotByName = function (name) { var i = SLOTS.indexOf(name); if (i >= 0) { H.selectSlot(i); } };
  /* the action bar: the boss's intention, tagged with the mind that decided it */
  H.action = function (text, kind) {
    var tag = kind === "native" ? TP.t("hudNativeTag") : kind ? TP.t("hudPredict") : "";
    TP.setText(els.actionTag, tag);
    TP.setText(els.actionText, text || "");
    els.action.setAttribute("data-kind", kind || "");
    TP.toggle(els.action, "is-on", !!text);
  };
  var subsList = [];
  H.sub = function (text, side) {
    if (!text) { return; }
    var row = TP.el("div", { "class": "hud-sub" }, els.subs);
    TP.el("span", { "class": "hud-sub-arrow" }, row, side === "left" ? "<" : "");
    TP.el("span", null, row, text);
    TP.el("span", { "class": "hud-sub-arrow" }, row, side === "right" || !side ? ">" : "");
    subsList.push(row);
    while (subsList.length > 6) { var old = subsList.shift(); if (old.parentNode) { old.parentNode.removeChild(old); } }
    subsList.forEach(function (r, i) { r.style.opacity = String(0.35 + 0.65 * (i + 1) / subsList.length); });
  };
  H.clearSubs = function () { TP.clear(els.subs); subsList = []; };
  var announceTimer = 0;
  H.announce = function (text, kind, side) {
    TP.setText(els.announce, text || "");
    els.announce.setAttribute("data-kind", kind || "");
    els.announce.setAttribute("data-side", side || "");
    els.announce.classList.remove("is-on");
    if (text) {
      void els.announce.offsetWidth;
      els.announce.classList.add("is-on");
      window.clearTimeout(announceTimer);
      announceTimer = window.setTimeout(function () { els.announce.classList.remove("is-on"); }, 1400);
    }
  };
  H.card = function (opts) {
    if (!opts) { return; }
    TP.setText(els.cardTag, opts.tag || TP.t("hudBuild"));
    TP.setText(els.cardTitle, opts.title || "");
    TP.setText(els.cardLine, opts.line || "");
    TP.setText(els.cardMind, opts.mind || "");
    if (opts.icon && els.cardIcon.getAttribute("data-icon") !== opts.icon) {
      els.cardIcon.setAttribute("data-icon", opts.icon);
      TP.clear(els.cardIcon);
      els.cardIcon.appendChild(P.icon(opts.icon, 3));
    }
    els.card.setAttribute("data-kind", opts.kind || "");
  };
  H.f3 = function (text) { TP.setText(els.f3, text || ""); };
  H.split = function (left, right) { TP.setText(els.splitL, left || ""); TP.setText(els.splitR, right || ""); };
  H.mind = function (title, lines, foot) {
    TP.setText(els.mindTitle, title || "");
    TP.clear(els.mindLines);
    (lines || []).forEach(function (line) {
      var row = TP.el("div", { "class": "hud-mind-line" + (line.best ? " is-best" : "") + (line.live ? " is-live" : "") }, els.mindLines);
      TP.el("span", { "class": "hud-mind-n" }, row, line.n);
      TP.el("span", { "class": "hud-mind-you" }, row, line.you);
      TP.el("span", { "class": "hud-mind-arrow" }, row, "→");
      TP.el("span", { "class": "hud-mind-boss" }, row, line.boss);
      TP.el("span", { "class": "hud-mind-p" }, row, line.p);
    });
    TP.setText(els.mindFoot, foot || "");
  };
  H.els = els;
})();
