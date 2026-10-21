/* ThePredict v3 · núcleo: idioma, formato de números, insignias de estado, utilidades y arranque.
   Sin dependencias y sin fetch: los datos llegan como globales desde data/*.js, así funciona también desde file://. */
(function () {
  "use strict";

  var root = document.documentElement;
  var TP = window.TP = {
    D: window.TP_DATA || null,
    CAPS: window.TP_CAPS || null,
    C: window.TP_CONTENT || null,
    BENCH: window.TP_BENCH === undefined ? null : window.TP_BENCH,
    lang: root.lang === "en" ? "en" : "es",
    SVGNS: "http://www.w3.org/2000/svg"
  };
  var motionQuery = window.matchMedia ? window.matchMedia("(prefers-reduced-motion: reduce)") : null;

  TP.reducedMotion = function () { return !!(motionQuery && motionQuery.matches); };
  TP.storage = function (key, value) {
    try {
      if (value === undefined) { return window.localStorage.getItem(key); }
      window.localStorage.setItem(key, value);
    } catch (error) { return null; }
    return null;
  };

  /* ---------------------------------------------------------------------------------------- text */
  var TEXT = {
    es: {
      of: " de ", evidence: "Evidencia",
      capCol: "Capacidad", statusCol: "Estado", evidenceCol: "Evidencia",
      groupfacility: "Facilidad", grouprepertoire: "Repertorio", groupadvanced: "Avanzado"
    },
    en: {
      of: " of ", evidence: "Evidence",
      capCol: "Capability", statusCol: "Status", evidenceCol: "Evidence",
      groupfacility: "Setup", grouprepertoire: "Repertoire", groupadvanced: "Advanced"
    }
  };
  TP.addText = function (es, en) {
    Object.keys(es).forEach(function (k) { TEXT.es[k] = es[k]; });
    Object.keys(en).forEach(function (k) { TEXT.en[k] = en[k]; });
  };
  TP.t = function (key, vars) {
    var table = TEXT[TP.lang] || TEXT.es;
    var text = table[key];
    if (text === undefined) { text = TEXT.es[key]; }
    if (text === undefined) { return key; }
    if (typeof text !== "string") { return text; }
    if (vars) {
      text = text.replace(/\{(\w+)\}/g, function (match, name) { return vars[name] !== undefined ? vars[name] : match; });
    }
    return text;
  };
  TP.pick = function (pair) {
    if (!pair) { return ""; }
    if (typeof pair === "string") { return pair; }
    return pair[TP.lang] || pair.es || "";
  };

  /* ---------------------------------------------------------------------------------------- numbers */
  var NBSP = " ";
  TP.NBSP = NBSP;
  TP.num = function (value, digits) {
    var d = digits === undefined ? 0 : digits;
    var negative = value < 0;
    var fixed = Math.abs(value).toFixed(d);
    var parts = fixed.split(".");
    var whole = parts[0];
    if (whole.length > 3) {
      whole = whole.replace(/\B(?=(\d{3})+(?!\d))/g, TP.lang === "es" ? NBSP : ",");
    }
    var text = parts.length > 1 ? whole + (TP.lang === "es" ? "," : ".") + parts[1] : whole;
    if (negative && Number(fixed) !== 0) { text = "−" + text; }
    return text;
  };
  TP.pct = function (value, digits) { return TP.num(value * 100, digits === undefined ? 1 : digits) + (TP.lang === "es" ? NBSP + "%" : "%"); };
  TP.secs = function (ticks, digits) { return TP.num(ticks / 20, digits === undefined ? 1 : digits) + NBSP + "s"; };
  TP.ms = function (value) { return TP.num(value, 0) + NBSP + "ms"; };
  TP.interval = function (pair, formatter) {
    var f = formatter || function (v) { return TP.pct(v, 1); };
    return TP.lang === "es" ? "[" + f(pair[0]) + "; " + f(pair[1]) + "]" : "[" + f(pair[0]) + ", " + f(pair[1]) + "]";
  };

  /* ---------------------------------------------------------------------------------------- math */
  TP.clamp = function (v, a, b) { return v < a ? a : v > b ? b : v; };
  TP.lerp = function (a, b, t) { return a + (b - a) * t; };
  TP.inv = function (a, b, v) { return b === a ? 0 : TP.clamp((v - a) / (b - a), 0, 1); };
  TP.smooth = function (t) { t = TP.clamp(t, 0, 1); return t * t * (3 - 2 * t); };
  TP.smoother = function (t) { t = TP.clamp(t, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); };
  TP.easeOut = function (t) { t = TP.clamp(t, 0, 1); return 1 - Math.pow(1 - t, 3); };
  TP.easeIn = function (t) { t = TP.clamp(t, 0, 1); return t * t * t; };
  TP.easeInOut = function (t) { t = TP.clamp(t, 0, 1); return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; };
  TP.easeOutBack = function (t) { t = TP.clamp(t, 0, 1); var c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); };
  TP.damp = function (current, target, lambda, dt) { return TP.lerp(current, target, 1 - Math.exp(-lambda * dt)); };
  /* window helper: 0 before a, ramps to 1 by b, stays 1 until c, ramps down to 0 by d */
  TP.win = function (v, a, b, c, d) {
    if (v <= a || v >= d) { return 0; }
    if (v < b) { return TP.smooth((v - a) / (b - a)); }
    if (v <= c) { return 1; }
    return 1 - TP.smooth((v - c) / (d - c));
  };
  /* deterministic pseudo-random numbers, so every load (and every QA run) draws the same thing */
  TP.rng = function (seed) {
    var s = seed >>> 0;
    return function () {
      s = (s + 0x6d2b79f5) >>> 0;
      var t = s;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  };

  /* ---------------------------------------------------------------------------------------- dom helpers */
  TP.el = function (tag, attrs, parent, text) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        if (attrs[k] === null || attrs[k] === undefined || attrs[k] === false) { return; }
        if (k === "class") { node.className = attrs[k]; } else { node.setAttribute(k, attrs[k] === true ? "" : attrs[k]); }
      });
    }
    if (text !== undefined && text !== null) { node.textContent = text; }
    if (parent) { parent.appendChild(node); }
    return node;
  };
  TP.svg = function (tag, attrs, parent) {
    var node = document.createElementNS(TP.SVGNS, tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        if (attrs[k] !== null && attrs[k] !== undefined) { node.setAttribute(k, attrs[k]); }
      });
    }
    if (parent) { parent.appendChild(node); }
    return node;
  };
  TP.clear = function (node) { while (node && node.firstChild) { node.removeChild(node.firstChild); } };
  TP.setText = function (node, text) { if (node && node.textContent !== text) { node.textContent = text; } };
  TP.toggle = function (node, cls, on) { if (node && node.classList.contains(cls) !== !!on) { node.classList.toggle(cls, !!on); } };

  /* ---------------------------------------------------------------------------------------- events */
  var listeners = { lang: [], motion: [] };
  TP.on = function (kind, fn) { (listeners[kind] = listeners[kind] || []).push(fn); };
  TP.fire = function (kind, payload) {
    (listeners[kind] || []).forEach(function (fn) {
      try { fn(payload); } catch (error) { if (window.console) { console.error(error); } }
    });
  };
  if (motionQuery) {
    var motionChange = function () { root.classList.toggle("reduced", TP.reducedMotion()); TP.fire("motion"); };
    if (motionQuery.addEventListener) { motionQuery.addEventListener("change", motionChange); } else if (motionQuery.addListener) { motionQuery.addListener(motionChange); }
  }

  /* ---------------------------------------------------------------------------------------- language */
  function applyLanguage(next) {
    TP.lang = next === "en" ? "en" : "es";
    root.setAttribute("lang", TP.lang);
    var buttons = document.querySelectorAll("[data-lang]");
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].setAttribute("aria-pressed", buttons[i].getAttribute("data-lang") === TP.lang ? "true" : "false");
    }
    TP.fire("lang");
  }
  TP.applyLanguage = applyLanguage;

  /* ---------------------------------------------------------------------------------------- capabilities */
  var CAP_INDEX = {};
  if (TP.CAPS) { TP.CAPS.items.forEach(function (item) { CAP_INDEX[item.id] = item; }); }
  TP.cap = function (id) { return CAP_INDEX[id] || null; };
  TP.statusLabel = function (status) {
    return TP.CAPS && TP.CAPS.statuses[status] ? TP.pick(TP.CAPS.statuses[status]) : status;
  };
  /* a status chip; with withTitle, the capability name goes first ("Posturas · Disponible") */
  TP.badge = function (status, note, title) {
    var span = TP.el("span", { "class": "badge", "data-status": status });
    TP.el("span", { "class": "badge-ico", "aria-hidden": "true" }, span);
    if (title) {
      TP.el("span", { "class": "badge-cap" }, span, title);
      TP.el("span", { "class": "badge-sep", "aria-hidden": "true" }, span, "·");
    }
    TP.el("span", { "class": "badge-text" }, span, TP.statusLabel(status));
    if (note) { TP.el("span", { "class": "badge-note" }, span, note); }
    return span;
  };
  TP.capBadge = function (id, withTitle, note) {
    var item = TP.cap(id);
    if (!item) { return null; }
    var badge = TP.badge(item.status, note, withTitle ? TP.pick(item.title) : null);
    badge.setAttribute("title", TP.pick(item.title) + ": " + TP.pick(item.text));
    badge.setAttribute("data-cap", id);
    return badge;
  };
  function renderBadges() {
    var nodes = document.querySelectorAll("[data-badge]");
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      TP.clear(node);
      var badge = TP.capBadge(node.getAttribute("data-badge"), true, node.getAttribute("data-badge-note"));
      if (badge) { node.appendChild(badge); }
    }
  }
  TP.renderCapsTable = function (container) {
    if (!container || !TP.CAPS) { return; }
    TP.clear(container);
    var table = TP.el("table", null, container);
    var head = TP.el("tr", null, TP.el("thead", null, table));
    [TP.t("capCol"), TP.t("statusCol"), TP.t("evidenceCol")].forEach(function (h) { TP.el("th", { scope: "col" }, head, h); });
    var body = TP.el("tbody", null, table);
    TP.CAPS.items.forEach(function (item) {
      var tr = TP.el("tr", { "data-cap": item.id }, body);
      var first = TP.el("td", null, tr);
      TP.el("strong", null, first, TP.pick(item.title));
      TP.el("div", { "class": "cap-body" }, first, TP.pick(item.text));
      TP.el("td", null, tr).appendChild(TP.badge(item.status));
      var list = TP.el("ul", null, TP.el("td", null, tr));
      item.evidence.forEach(function (ev) { TP.el("li", null, list, ev.detail); });
    });
  };
  TP.renderLegend = function (container) {
    if (!container || !TP.CAPS) { return; }
    TP.clear(container);
    var list = TP.el("ul", { "class": "legend-list" }, container);
    var counts = {};
    TP.CAPS.items.forEach(function (item) { counts[item.status] = (counts[item.status] || 0) + 1; });
    ["available", "available_off", "testing", "roadmap"].forEach(function (status) {
      var li = TP.el("li", null, list);
      TP.el("span", { "class": "legend-n" }, li, String(counts[status] || 0));
      li.appendChild(TP.badge(status));
      TP.el("p", null, li, TP.pick(TP.CAPS.meaning[status]));
    });
  };

  /* ---------------------------------------------------------------------------------------- boot */
  var modules = [];
  TP.module = function (name, init) { modules.push({ name: name, init: init }); };
  TP.boot = function () {
    root.classList.toggle("reduced", TP.reducedMotion());
    var storedLang = TP.storage("tp-lang");
    var buttons = document.querySelectorAll("[data-lang]");
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener("click", function (event) {
        var next = event.currentTarget.getAttribute("data-lang");
        TP.storage("tp-lang", next);
        applyLanguage(next);
      });
    }
    TP.on("lang", renderBadges);
    modules.forEach(function (m) {
      try { m.init(); } catch (error) { if (window.console) { console.error("module " + m.name, error); } }
    });
    applyLanguage(storedLang === "en" ? "en" : (storedLang === "es" ? "es" : TP.lang));
  };
})();
