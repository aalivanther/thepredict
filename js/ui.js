/* ThePredict v4 · interfaz plana: el libro/editor que escribe las líneas reales (de TP_CONTENT, nunca a mano), el
   recuento 24 → 0, las cifras medidas, la leyenda y la tabla de capacidades con su evidencia, el pie, el botón de
   sonido y el arranque. Los datos que solo usan las specs (summary.js, capabilities.js) llegan después del primer
   fotograma 3D. */
(function () {
  "use strict";
  var TP = window.TP;
  var C = TP.C || {};

  TP.addText({
    footer: "Página local generada el {d} a partir de {s}. No llama a Jev ni a TypeSafe, no carga nada de la red y funciona sin conexión. Boss, arena, texturas, efectos e interfaz propios; three.js r170 y las fuentes Pixelify Sans, Inter y JetBrains Mono (OFL) incluidas. Créditos en CREDITOS.md.",
    edMob: "ThePredictGuardian.yml", edSkills: "ThePredictGuardianSkills.yml"
  }, {
    footer: "Local page generated on {d} from {s}. It never calls Jev or TypeSafe, loads nothing from the network and works offline. Own boss, arena, textures, effects and interface; three.js r170 and the Pixelify Sans, Inter and JetBrains Mono fonts (OFL) are bundled. Credits in CREDITOS.md.",
    edMob: "ThePredictGuardian.yml", edSkills: "ThePredictGuardianSkills.yml"
  });

  /* ---------------------------------------------------------------------------------------- figures */
  function figures() {
    var f = {};
    var D = TP.D;
    if (D && D.sim) {
      var A = D.sim.matrix.none.A, pairs = D.sim.pairs, bench = (D.jev && D.jev.bench) || {};
      f.aWins = A.redWins + TP.t("of") + A.rounds;
      f.aWilson = TP.interval(A.wilson);
      f.diffAO = TP.secs(pairs.AO_minus_D.meanTicks, 1);
      f.diffAOCi = TP.interval(pairs.AO_minus_D.tCiTicks, function (v) { return TP.num(v / 20, 1); }) + " s";
      f.p50 = TP.ms(bench.p50ms || 0);
      f.p95 = TP.ms(bench.p95ms || 0);
      f.benchCalls = TP.num(bench.calls || 0, 0);
    }
    return f;
  }
  function applyFigures() {
    var f = figures();
    var nodes = document.querySelectorAll("[data-fig]");
    for (var i = 0; i < nodes.length; i++) {
      var v = f[nodes[i].getAttribute("data-fig")];
      if (v !== undefined && v !== null && v !== "") { nodes[i].textContent = v; }
    }
  }

  /* ---------------------------------------------------------------------------------------- the editor */
  function tokenize(line, host) {
    var m = /^(\s*)(#.*)$/.exec(line);
    if (m) { host.appendChild(document.createTextNode(m[1])); TP.el("span", { "class": "tok-com" }, host, m[2]); return; }
    var kv = /^(\s*-?\s*)([A-Za-z_][\w]*)(:)(.*)$/.exec(line);
    if (kv && !/\{/.test(kv[2])) {
      host.appendChild(document.createTextNode(kv[1]));
      TP.el("span", { "class": /^(Predictor|PredictorDifficulty|PredictorEntropy|Intention|PredictValues)$/.test(kv[2]) ? "tok-int" : "tok-key" }, host, kv[2]);
      host.appendChild(document.createTextNode(kv[3]));
      value(kv[4], host);
      return;
    }
    value(line, host);
  }
  function value(text, host) {
    var re = /('[^']*'|"[^"]*")|(\b\d+(?:\.\d+)?\b)|([a-zA-Z]+\{[^}]*\}(?:\s*@\w+)?)/g;
    var last = 0, m;
    while ((m = re.exec(text))) {
      if (m.index > last) { host.appendChild(document.createTextNode(text.slice(last, m.index))); }
      TP.el("span", { "class": m[1] ? "tok-str" : m[2] ? "tok-num" : "tok-mech" }, host, m[0]);
      last = m.index + m[0].length;
    }
    if (last < text.length) { host.appendChild(document.createTextNode(text.slice(last))); }
  }
  function editorScript() {
    var S = C.snippets || {};
    var mob = (S.tpMob && S.tpMob.code || "").split("\n").filter(Boolean);
    var mobLines = (S.mobLines && S.mobLines.code || "").split("\n").filter(Boolean);
    var melee = (S.tpMelee && S.tpMelee.code || "").split("\n").filter(Boolean);
    var lines = [];
    lines.push({ file: TP.t("edMob") });
    mob.slice(0, 5).forEach(function (l) { lines.push({ text: l }); });
    var predictorLine = mob.filter(function (l) { return /^\s*Predictor:/.test(l); })[0] || mobLines[0];
    lines.push({ text: predictorLine, hot: true });
    var difficulty = mobLines.filter(function (l) { return /PredictorDifficulty/.test(l); })[0];
    if (difficulty) { lines.push({ text: "  " + difficulty.replace(/^\s+/, ""), hot: true }); }
    lines.push({ gap: true });
    lines.push({ file: TP.t("edSkills") });
    melee.forEach(function (l) {
      if (/^\s*Intention:/.test(l)) { lines.push({ text: l, hot: true }); } else if (!/PredictValues/.test(l)) { lines.push({ text: l }); }
    });
    return lines;
  }
  function setupEditor() {
    var body = document.getElementById("editor-body");
    var fileEl = document.getElementById("editor-file");
    if (!body) { return; }
    var timer = 0, started = false;
    function render(progress) {
      TP.clear(body);
      var lines = editorScript();
      var n = 0, budget = progress, caretPlaced = false;
      lines.forEach(function (line) {
        if (line.gap) { TP.el("div", { "class": "ed-gap" }, body); return; }
        if (line.file) { var f = TP.el("div", { "class": "ed-line ed-file" }, body); TP.el("span", { "class": "ed-ln" }, f, ""); TP.el("span", { "class": "ed-code" }, f, "# " + line.file); return; }
        n++;
        var row = TP.el("div", { "class": "ed-line" + (line.hot ? " is-hot" : "") }, body);
        TP.el("span", { "class": "ed-ln" }, row, String(n));
        var code = TP.el("span", { "class": "ed-code" }, row);
        if (!line.hot) { tokenize(line.text, code); return; }
        if (budget >= line.text.length) { tokenize(line.text, code); budget -= line.text.length; return; }
        if (budget > 0 || !caretPlaced) {
          var typed = line.text.slice(0, Math.max(0, budget));
          code.appendChild(document.createTextNode(typed));
          if (!caretPlaced) { TP.el("span", { "class": "ed-caret", "aria-hidden": "true" }, code); caretPlaced = true; }
          row.classList.toggle("is-hot", budget > 0);
          budget = 0;
        }
      });
      if (fileEl) { TP.setText(fileEl, TP.t("edMob") + " · " + TP.t("edSkills")); }
    }
    function total() { return editorScript().filter(function (l) { return l.hot; }).reduce(function (a, l) { return a + l.text.length; }, 0); }
    var rnd = TP.rng(5);
    function start() {
      if (started) { return; }
      started = true;
      if (TP.reducedMotion()) { render(Infinity); return; }
      var count = 0, max = total();
      render(0);
      function tick() {
        count += 1 + (rnd() < 0.18 ? 1 : 0);
        render(count);
        if (count < max) { timer = window.setTimeout(tick, 38 + rnd() * 46); } else { window.setTimeout(function () { render(Infinity); }, 500); }
      }
      timer = window.setTimeout(tick, 400);
    }
    render(TP.reducedMotion() ? Infinity : 0);
    TP.on("lang", function () { render(started ? Infinity : 0); });
    if (window.IntersectionObserver) {
      var io = new IntersectionObserver(function (entries) { entries.forEach(function (e) { if (e.isIntersecting) { start(); io.disconnect(); } }); }, { threshold: 0.35 });
      io.observe(body);
    } else { start(); }
    window.addEventListener("beforeunload", function () { window.clearTimeout(timer); });
    TP.startEditor = start;
  }
  function setupCounts() {
    var counts = C.counts || {};
    TP.setText(document.getElementById("when-count"), String(counts.before ? counts.before.whenLines : 24));
    TP.setText(document.getElementById("when-after"), String(counts.after ? counts.after.timerRules : 0));
  }

  /* ---------------------------------------------------------------------------------------- footer and sound */
  function renderFooter() {
    var node = document.getElementById("footer-meta");
    if (!node) { return; }
    var date = (C.generated || (TP.CAPS && TP.CAPS.generated) || "").slice(0, 10);
    node.textContent = TP.t("footer", { d: date, s: C.source || "ThePredict-candidato" });
  }
  function setupSound() {
    var btn = document.getElementById("sound");
    if (!btn || !TP.audio) { return; }
    btn.addEventListener("click", function () {
      var on = btn.getAttribute("aria-pressed") !== "true";
      btn.setAttribute("aria-pressed", on ? "true" : "false");
      TP.audio.enable(on);
    });
  }
  function renderAll() {
    applyFigures();
    renderFooter();
    TP.renderLegend(document.getElementById("legend"));
    TP.renderCapsTable(document.getElementById("caps-table"));
  }

  /* ---------------------------------------------------------------------------------------- late data */
  /* each file and the global it defines */
  var LATE = [["data/summary.js", "TP_DATA"], ["data/capabilities.js", "TP_CAPS"]];
  var lateAsked = false;
  function loadLate() {
    if (lateAsked) { return; }
    lateAsked = true;
    var left = 0;
    var done = function () { left--; if (left === 0) { TP.takeData(); } };
    LATE.forEach(function (item) {
      if (window[item[1]]) { return; }
      left++;
      /* index.html may have asked for it already (links to #setup or #specs) */
      var script = document.querySelector('script[data-tp-late][src="' + item[0] + '"]');
      if (!script) {
        script = document.createElement("script");
        script.src = item[0];
        script.async = false;
        script.setAttribute("data-tp-late", "");
        document.head.appendChild(script);
      }
      script.addEventListener("load", done);
      script.addEventListener("error", done);
    });
    if (left === 0) { TP.takeData(); }
  }
  function setupLate() {
    var E = TP.engine;
    if (/^#(setup|specs)\b/.test(window.location.hash) || !E || E.failReason) { loadLate(); }
    else {
      E.on("drawn", function () { window.setTimeout(loadLate, 0); });
      E.on("fail", loadLate);
      /* without a first frame (hidden tab, no WebGL) the specs still get their data */
      window.addEventListener("load", function () { window.setTimeout(loadLate, 4000); });
    }
    window.addEventListener("hashchange", function () { if (/^#(setup|specs)\b/.test(window.location.hash)) { loadLate(); } });
  }

  TP.module("ui", function () {
    setupEditor();
    setupCounts();
    setupSound();
    TP.on("lang", renderAll);
    TP.on("data", renderAll);
    setupLate();
  });

  TP.boot();
})();
