/* ThePredict v4 · sonido sintetizado por WebAudio (nada grabado, nada de fuera): un ambiente de cripta (drone grave,
   viento filtrado, goteo) y SFX cortos (tajo, parry, bolt, impacto, salto). Apagado por defecto; el botón de la barra
   lo enciende. Las demos lo llaman con TP.audio.play(nombre) y no hace nada si está apagado. */
(function () {
  "use strict";
  var TP = window.TP;
  var AU = TP.audio = { on: false, ctx: null };
  var master = null, ambient = null;

  function ensure() {
    if (AU.ctx) { return AU.ctx; }
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) { return null; }
    var ctx = new Ctx();
    master = ctx.createGain();
    master.gain.value = 0;
    var comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -18; comp.ratio.value = 4;
    master.connect(comp);
    comp.connect(ctx.destination);
    AU.ctx = ctx;
    return ctx;
  }
  function noiseBuffer(ctx, seconds) {
    var n = Math.floor(ctx.sampleRate * seconds);
    var buf = ctx.createBuffer(1, n, ctx.sampleRate);
    var d = buf.getChannelData(0);
    var rnd = TP.rng(9);
    for (var i = 0; i < n; i++) { d[i] = rnd() * 2 - 1; }
    return buf;
  }
  function startAmbient(ctx) {
    if (ambient) { return; }
    ambient = ctx.createGain();
    ambient.gain.value = 0.5;
    ambient.connect(master);
    /* drone: two detuned saws through a low-pass with a slow wobble */
    [55, 55.6, 110.4].forEach(function (f, i) {
      var osc = ctx.createOscillator();
      osc.type = i === 2 ? "triangle" : "sawtooth";
      osc.frequency.value = f;
      var lp = ctx.createBiquadFilter();
      lp.type = "lowpass"; lp.frequency.value = 180; lp.Q.value = 0.8;
      var g = ctx.createGain(); g.gain.value = i === 2 ? 0.08 : 0.12;
      var lfo = ctx.createOscillator(); lfo.frequency.value = 0.07 + i * 0.03;
      var lfoG = ctx.createGain(); lfoG.gain.value = 60;
      lfo.connect(lfoG); lfoG.connect(lp.frequency);
      osc.connect(lp); lp.connect(g); g.connect(ambient);
      osc.start(); lfo.start();
    });
    /* wind: filtered noise with a slow swell */
    var noise = ctx.createBufferSource();
    noise.buffer = noiseBuffer(ctx, 4); noise.loop = true;
    var bp = ctx.createBiquadFilter(); bp.type = "bandpass"; bp.frequency.value = 420; bp.Q.value = 0.6;
    var wg = ctx.createGain(); wg.gain.value = 0.05;
    var wl = ctx.createOscillator(); wl.frequency.value = 0.05;
    var wlg = ctx.createGain(); wlg.gain.value = 0.03;
    wl.connect(wlg); wlg.connect(wg.gain);
    noise.connect(bp); bp.connect(wg); wg.connect(ambient);
    noise.start(); wl.start();
    /* drips every few seconds */
    function drip() {
      if (!AU.on) { return; }
      var o = ctx.createOscillator(); o.type = "sine";
      var t = ctx.currentTime;
      o.frequency.setValueAtTime(1800 + Math.random() * 800, t);
      o.frequency.exponentialRampToValueAtTime(600, t + 0.12);
      var g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.06, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.25);
      o.connect(g); g.connect(ambient); o.start(t); o.stop(t + 0.3);
      window.setTimeout(drip, 2500 + Math.random() * 5000);
    }
    window.setTimeout(drip, 1500);
  }

  AU.enable = function (on) {
    AU.on = !!on;
    var ctx = ensure();
    if (!ctx) { return; }
    if (on) {
      if (ctx.state === "suspended") { ctx.resume(); }
      startAmbient(ctx);
      master.gain.cancelScheduledValues(ctx.currentTime);
      master.gain.setTargetAtTime(0.7, ctx.currentTime, 0.4);
    } else {
      master.gain.setTargetAtTime(0, ctx.currentTime, 0.2);
    }
  };

  /* short synthesized effects */
  var SFX = {
    slash: function (ctx, t) {
      var n = ctx.createBufferSource(); n.buffer = noiseBuffer(ctx, 0.4);
      var f = ctx.createBiquadFilter(); f.type = "bandpass"; f.frequency.setValueAtTime(2400, t); f.frequency.exponentialRampToValueAtTime(500, t + 0.22); f.Q.value = 1.2;
      var g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.5, t + 0.015); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.28);
      n.connect(f); f.connect(g); g.connect(master); n.start(t); n.stop(t + 0.3);
    },
    parry: function (ctx, t) {
      [1320, 1980, 2640, 3520].forEach(function (fr, i) {
        var o = ctx.createOscillator(); o.type = "sine"; o.frequency.value = fr;
        var g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.18 / (i + 1), t + 0.005); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.6 - i * 0.08);
        o.connect(g); g.connect(master); o.start(t); o.stop(t + 0.7);
      });
      SFX.slash(ctx, t);
    },
    bolt: function (ctx, t) {
      var o = ctx.createOscillator(); o.type = "sawtooth";
      o.frequency.setValueAtTime(220, t); o.frequency.exponentialRampToValueAtTime(1400, t + 0.3);
      var f = ctx.createBiquadFilter(); f.type = "lowpass"; f.frequency.value = 2200;
      var g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.22, t + 0.03); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.45);
      o.connect(f); f.connect(g); g.connect(master); o.start(t); o.stop(t + 0.5);
    },
    hit: function (ctx, t) {
      var o = ctx.createOscillator(); o.type = "square";
      o.frequency.setValueAtTime(160, t); o.frequency.exponentialRampToValueAtTime(50, t + 0.15);
      var g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.4, t + 0.008); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.22);
      o.connect(g); g.connect(master); o.start(t); o.stop(t + 0.25);
      SFX.slash(ctx, t);
    },
    thud: function (ctx, t) {
      var o = ctx.createOscillator(); o.type = "sine";
      o.frequency.setValueAtTime(90, t); o.frequency.exponentialRampToValueAtTime(30, t + 0.4);
      var g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.7, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.5);
      o.connect(g); g.connect(master); o.start(t); o.stop(t + 0.55);
      var n = ctx.createBufferSource(); n.buffer = noiseBuffer(ctx, 0.5);
      var f = ctx.createBiquadFilter(); f.type = "lowpass"; f.frequency.value = 400;
      var ng = ctx.createGain(); ng.gain.setValueAtTime(0.3, t); ng.gain.exponentialRampToValueAtTime(0.0001, t + 0.4);
      n.connect(f); f.connect(ng); ng.connect(master); n.start(t); n.stop(t + 0.45);
    },
    roar: function (ctx, t) {
      var o = ctx.createOscillator(); o.type = "sawtooth";
      o.frequency.setValueAtTime(70, t); o.frequency.linearRampToValueAtTime(110, t + 0.6); o.frequency.exponentialRampToValueAtTime(45, t + 1.6);
      var f = ctx.createBiquadFilter(); f.type = "lowpass"; f.frequency.setValueAtTime(300, t); f.frequency.linearRampToValueAtTime(1200, t + 0.5); f.frequency.exponentialRampToValueAtTime(200, t + 1.6);
      var g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.5, t + 0.1); g.gain.exponentialRampToValueAtTime(0.0001, t + 1.7);
      o.connect(f); f.connect(g); g.connect(master); o.start(t); o.stop(t + 1.8);
      SFX.thud(ctx, t + 0.05);
    }
  };
  AU.play = function (name) {
    if (!AU.on || !AU.ctx || !SFX[name]) { return; }
    try { SFX[name](AU.ctx, AU.ctx.currentTime); } catch (error) { /* audio is optional */ }
  };
})();
