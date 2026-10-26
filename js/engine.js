/* ThePredict v4 · motor: renderer WebGL con posproceso (bloom, viñeta, grano, aberración leve), pase de split-screen
   (dos cámaras con scissor y un callback que alterna qué actores se ven), director de scroll (progreso por sección,
   [data-show], corte con fundido entre demos), bucle que se pausa fuera de pantalla, niveles de calidad, medidor de fps
   por sección y ganchos de QA (window.TP_QA). three.js r170 vendorizado como IIFE: window.THREE y window.THREE_ADDONS.
   El arranque va por pasos con una pausa entre ellos (la página pinta y responde mientras arranca el 3D) y los shaders
   se compilan para el destino real del composer, sin bloquear, antes del primer fotograma. */
(function () {
  "use strict";
  var TP = window.TP;
  var THREE = window.THREE;
  var ADD = window.THREE_ADDONS;
  var root = document.documentElement;

  var E = TP.engine = {
    ok: false, tier: "high", reduced: false, gpu: "", renderer: null, composer: null,
    factories: {}, scenes: {}, current: null, currentName: "", sections: [], active: null,
    time: 0, width: 1, height: 1, dpr: 1, dprScale: 1, dirty: true, visible: true, frozen: false,
    stats: {}, events: {}, initMs: {}, downgrades: [], layers: {}, split: null, ready: false, drawn: false
  };

  function emit(kind, a, b) {
    var list = E.events[kind];
    if (!list) { return; }
    for (var i = 0; i < list.length; i++) {
      try { list[i](a, b); } catch (error) { if (window.console) { console.error(error); } }
    }
  }
  E.on = function (kind, fn) { (E.events[kind] = E.events[kind] || []).push(fn); };
  E.define = function (name, factory) { E.factories[name] = factory; };

  E.layer = function (name) {
    if (!E.layers[name]) {
      var host = document.getElementById("labels");
      var div = TP.el("div", { "class": "layer layer-" + name }, host);
      div.style.display = "none";
      E.layers[name] = div;
    }
    return E.layers[name];
  };
  E.place = function (node, x, y, extra) {
    var value = "translate3d(" + Math.round(x) + "px," + Math.round(y) + "px,0)" + (extra || "");
    if (node.__tf !== value) { node.__tf = value; node.style.transform = value; }
  };
  var tmpV = null;
  E.project = function (vec, camera, out) {
    tmpV = tmpV || new THREE.Vector3();
    tmpV.copy(vec).project(camera);
    out = out || {};
    out.x = (tmpV.x + 1) * 0.5 * E.width;
    out.y = (1 - tmpV.y) * 0.5 * E.height;
    out.z = tmpV.z;
    out.behind = tmpV.z > 1 || tmpV.z < -1;
    return out;
  };

  E.ensure = function (name) {
    if (!E.ok) { return null; }
    if (!E.scenes[name] && E.factories[name]) {
      var t0 = performance.now();
      var s = E.factories[name](E);
      s.name = name;
      E.scenes[name] = s;
      if (s.resize) { s.resize(E.width, E.height); }
      /* during start-up, init compiles it without blocking (see start) */
      if (E.ready) { compileScene(s, false); }
      E.initMs[name] = Math.round(performance.now() - t0);
    }
    return E.scenes[name] || null;
  };
  /* compile for the target the scene really renders into (the composer's buffer: linear, no tone mapping); compiling
     for the screen builds sRGB + ACES variants that are never used and leaves the real ones for the first frames */
  function compileScene(s, async) {
    var r = E.renderer, prev = r.getRenderTarget(), done = null;
    r.setRenderTarget(E.composer.readBuffer);
    try { done = async && r.compileAsync ? r.compileAsync(s.scene, s.camera) : r.compile(s.scene, s.camera); } catch (error) { done = null; /* compiled lazily on first render */ }
    r.setRenderTarget(prev);
    return async ? done : null;
  }
  /* the post-processing materials as well, each for its own target (bloom into its half-float buffers, linear; the
     finish to the screen, with ACES and sRGB), so the first frame does not compile them */
  function compilePost() {
    var r = E.renderer, prev = r.getRenderTarget(), b = E.bloom, done = [];
    var plane = new THREE.PlaneGeometry(2, 2), cam = new THREE.PerspectiveCamera();
    var bloomScene = new THREE.Scene(), finishScene = new THREE.Scene();
    [b.materialHighPassFilter, b.compositeMaterial].concat(b.separableBlurMaterials).forEach(function (m) { bloomScene.add(new THREE.Mesh(plane, m)); });
    finishScene.add(new THREE.Mesh(plane, E.finish.material));
    try {
      r.setRenderTarget(b.renderTargetBright);
      done.push(r.compileAsync(bloomScene, cam));
      r.setRenderTarget(null);
      done.push(r.compileAsync(finishScene, cam));
    } catch (error) { /* compiled on first use */ }
    r.setRenderTarget(prev);
    return Promise.all(done);
  }

  /* ---------------------------------------------------------------------------------------- quality */
  function pickTier() {
    var forced = /[?&]tier=(low|high)\b/.exec(window.location.search);
    if (forced) { return forced[1]; }
    var coarse = window.matchMedia && window.matchMedia("(pointer: coarse)").matches;
    var narrow = Math.min(window.innerWidth, window.innerHeight) < 600 || window.innerWidth < 760;
    var memory = navigator.deviceMemory || 8;
    var cores = navigator.hardwareConcurrency || 8;
    if ((coarse && narrow) || memory <= 3 || cores <= 3) { return "low"; }
    return "high";
  }

  /* the last pass, straight to the screen: adds the bloom (what UnrealBloomPass's own additive blend did), then
     aberration, vignette and grain, then ACES tone mapping and sRGB (what OutputPass did). One full-screen pass instead
     of three, and the second buffer of the composer is never allocated */
  var FinishShader = {
    uniforms: { tDiffuse: { value: null }, tBloom: { value: null }, uTime: { value: 0 }, uRes: { value: null }, uVig: { value: 0.5 }, uGrain: { value: 0.014 }, uCA: { value: 0.002 }, uTint: { value: new THREE.Vector3(1, 1, 1) } },
    vertexShader: "varying vec2 vUv;\nvoid main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",
    fragmentShader: [
      "uniform sampler2D tDiffuse; uniform sampler2D tBloom;",
      "uniform float uTime; uniform vec2 uRes; uniform float uVig; uniform float uGrain; uniform float uCA; uniform vec3 uTint;",
      "varying vec2 vUv;",
      "float hash(vec2 p) { p = fract(p * vec2(443.897, 441.423)); p += dot(p, p.yx + 19.19); return fract((p.x + p.y) * p.x); }",
      /* the scene plus its bloom, as the additive blend (SRC_ALPHA, ONE) left it */
      "vec3 lit(vec2 uv) { vec4 b = texture2D(tBloom, uv); return texture2D(tDiffuse, uv).rgb + b.rgb * b.a; }",
      "void main() {",
      "  vec2 d = vUv - 0.5;",
      "  float r2 = dot(d, d);",
      "  vec2 off = d * uCA * (0.35 + 2.2 * r2);",
      "  vec3 c = lit(vUv);",
      "  vec3 col = vec3(lit(vUv + off).r, c.g, lit(vUv - off).b) * uTint;",
      "  float v = 1.0 - smoothstep(0.12, 0.66, r2);",
      "  col *= mix(1.0 - uVig, 1.0, v);",
      "  float g = hash(vUv * uRes + floor(uTime * 24.0) * 1.37) - 0.5;",
      "  col += g * uGrain * (0.25 + col);",
      "  gl_FragColor = vec4(max(col, 0.0), 1.0);",
      "  #include <tonemapping_fragment>",
      "  #include <colorspace_fragment>",
      "}"
    ].join("\n")
  };

  /* UnrealBloomPass.render of r170 without its last step (the additive blend over the whole frame): the blurred mips
     stay in renderTargetsHorizontal[0] and the finish pass adds them */
  class BloomPass extends ADD.UnrealBloomPass {}
  BloomPass.prototype.render = function (renderer, writeBuffer, readBuffer) {
    renderer.getClearColor(this._oldClearColor);
    this.oldClearAlpha = renderer.getClearAlpha();
    var oldAutoClear = renderer.autoClear;
    renderer.autoClear = false;
    renderer.setClearColor(this.clearColor, 0);
    /* 1. bright areas */
    this.highPassUniforms.tDiffuse.value = readBuffer.texture;
    this.highPassUniforms.luminosityThreshold.value = this.threshold;
    this.fsQuad.material = this.materialHighPassFilter;
    renderer.setRenderTarget(this.renderTargetBright);
    renderer.clear();
    this.fsQuad.render(renderer);
    /* 2. blur every mip */
    var input = this.renderTargetBright;
    for (var i = 0; i < this.nMips; i++) {
      var blur = this.separableBlurMaterials[i];
      this.fsQuad.material = blur;
      blur.uniforms.colorTexture.value = input.texture;
      blur.uniforms.direction.value = ADD.UnrealBloomPass.BlurDirectionX;
      renderer.setRenderTarget(this.renderTargetsHorizontal[i]);
      renderer.clear();
      this.fsQuad.render(renderer);
      blur.uniforms.colorTexture.value = this.renderTargetsHorizontal[i].texture;
      blur.uniforms.direction.value = ADD.UnrealBloomPass.BlurDirectionY;
      renderer.setRenderTarget(this.renderTargetsVertical[i]);
      renderer.clear();
      this.fsQuad.render(renderer);
      input = this.renderTargetsVertical[i];
    }
    /* 3. composite the mips */
    this.fsQuad.material = this.compositeMaterial;
    this.compositeMaterial.uniforms.bloomStrength.value = this.strength;
    this.compositeMaterial.uniforms.bloomRadius.value = this.radius;
    this.compositeMaterial.uniforms.bloomTintColors.value = this.bloomTintColors;
    renderer.setRenderTarget(this.renderTargetsHorizontal[0]);
    renderer.clear();
    this.fsQuad.render(renderer);
    renderer.setClearColor(this._oldClearColor, this.oldClearAlpha);
    renderer.autoClear = oldAutoClear;
  };

  /* ---------------------------------------------------------------------------------------- split pass */
  /* renders the same scene twice, left and right, with two cameras; before each half it calls the scene's
     split.before(side) so the scene can show the actors of that side only */
  class SplitPass extends ADD.Pass {
    constructor() {
      super();
      this.needsSwap = false;
      this.clear = true;
      this.scene = null;
      this.left = null;
      this.right = null;
      this.before = null;
      this.empty = new THREE.Scene();
    }
  }
  /* the viewport and scissor live on the render target itself: the shadow pass calls setRenderTarget again and
     would otherwise reset them to the full buffer between the two halves */
  SplitPass.prototype.render = function (renderer, writeBuffer, readBuffer) {
    var target = readBuffer;
    var w = target.width, h = target.height;
    var portrait = w < h;
    var half = Math.floor((portrait ? h : w) / 2), gap = Math.max(1, Math.round((portrait ? h : w) * 0.002));
    target.viewport.set(0, 0, w, h); target.scissor.set(0, 0, w, h); target.scissorTest = false;
    renderer.setRenderTarget(target);
    renderer.clear();
    if (this.before) { this.before("left"); }
    /* "left" is the top half in portrait */
    if (portrait) { target.viewport.set(0, half + gap, w, h - half - gap); target.scissor.set(0, half + gap, w, h - half - gap); }
    else { target.viewport.set(0, 0, half - gap, h); target.scissor.set(0, 0, half - gap, h); }
    target.scissorTest = true;
    renderer.setRenderTarget(target);
    renderer.render(this.scene, this.left);
    if (this.before) { this.before("right"); }
    if (portrait) { target.viewport.set(0, 0, w, half - gap); target.scissor.set(0, 0, w, half - gap); }
    else { target.viewport.set(half + gap, 0, w - half - gap, h); target.scissor.set(half + gap, 0, w - half - gap, h); }
    target.scissorTest = true;
    renderer.setRenderTarget(target);
    renderer.render(this.scene, this.right);
    if (this.before) { this.before(null); }
    /* with MSAA each render resolves into the texture only inside its scissor: resolve the gap between the halves too
       (an empty render, no clear), or that strip of the texture keeps an older frame */
    if (target.samples > 0) {
      if (portrait) { target.viewport.set(0, half - gap, w, 2 * gap); target.scissor.set(0, half - gap, w, 2 * gap); }
      else { target.viewport.set(half - gap, 0, 2 * gap, h); target.scissor.set(half - gap, 0, 2 * gap, h); }
      target.scissorTest = true;
      renderer.setRenderTarget(target);
      var autoClear = renderer.autoClear;
      renderer.autoClear = false;
      renderer.render(this.empty, this.left);
      renderer.autoClear = autoClear;
    }
    target.viewport.set(0, 0, w, h); target.scissor.set(0, 0, w, h); target.scissorTest = false;
    renderer.setRenderTarget(target);
  };

  /* ---------------------------------------------------------------------------------------- size */
  function measureSize() {
    var canvas = document.getElementById("stage");
    E.width = Math.max(1, Math.round(canvas.clientWidth || window.innerWidth));
    E.height = Math.max(1, Math.round(canvas.clientHeight || window.innerHeight));
    var cap = E.tier === "high" ? 1.75 : 1.25;
    E.dpr = Math.max(0.6, Math.min(window.devicePixelRatio || 1, cap) * E.dprScale);
  }
  function applySize() {
    E.renderer.setPixelRatio(E.dpr);
    E.renderer.setSize(E.width, E.height, false);
    E.composer.setPixelRatio(E.dpr);
    E.composer.setSize(E.width, E.height);
    E.finish.uniforms.uRes.value.set(E.width * E.dpr, E.height * E.dpr);
    Object.keys(E.scenes).forEach(function (name) {
      if (E.scenes[name].resize) { E.scenes[name].resize(E.width, E.height); }
    });
    measureSections();
    E.dirty = true;
  }

  /* ---------------------------------------------------------------------------------------- sections */
  function collectSections() {
    var nodes = document.querySelectorAll(".stage[data-scene]");
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var shows = [];
      var list = el.querySelectorAll("[data-show]");
      for (var j = 0; j < list.length; j++) {
        var parts = list[j].getAttribute("data-show").split(/\s+/).map(Number);
        shows.push({ el: list[j], a: parts[0], b: parts[1], on: false });
      }
      E.sections.push({
        el: el, id: el.id, key: el.getAttribute("data-key"), scene: el.getAttribute("data-scene"),
        pin: el.querySelector(".pin"), top: 0, height: 1, pinH: 1, raw: 0, p: 0, shows: shows,
        seen: false, wasActive: false, inView: false
      });
    }
  }
  function measureSections() {
    var y = window.scrollY || window.pageYOffset || 0;
    E.sections.forEach(function (s) {
      var rect = s.el.getBoundingClientRect();
      s.top = rect.top + y;
      s.height = rect.height;
      s.pinH = s.pin ? s.pin.clientHeight : window.innerHeight;
    });
  }
  E.section = function (idOrKey) {
    for (var i = 0; i < E.sections.length; i++) {
      if (E.sections[i].id === idOrKey || E.sections[i].key === idOrKey) { return E.sections[i]; }
    }
    return null;
  };

  /* ---------------------------------------------------------------------------------------- director */
  var veil = { el: null, value: 0, target: 0, pending: "", dip: false };
  var lastY = -1;
  E.snapNext = true;
  function direct(dt) {
    var y = window.scrollY || window.pageYOffset || 0;
    var vh = window.innerHeight;
    if (y !== lastY) { E.dirty = true; lastY = y; }
    var center = y + vh * 0.5;
    var nearest = null, best = 1e12, visible = false, i, s;
    for (i = 0; i < E.sections.length; i++) {
      s = E.sections[i];
      var range = Math.max(1, s.height - s.pinH);
      s.raw = TP.clamp((y - s.top) / range, 0, 1);
      s.inView = s.top < y + vh && s.top + s.height > y;
      if (s.inView) {
        visible = true;
        var dist = center < s.top ? s.top - center : center > s.top + s.height ? center - s.top - s.height : 0;
        if (dist < best) { best = dist; nearest = s; }
      }
    }
    E.visible = visible;
    var active = nearest;
    for (i = 0; i < E.sections.length; i++) {
      s = E.sections[i];
      if (s === active) {
        if (E.snapNext || E.reduced || !s.seen || (!s.wasActive && Math.abs(s.raw - s.p) > 0.25)) { s.p = s.raw; }
        else {
          s.p = TP.damp(s.p, s.raw, 6.5, dt);
          if (Math.abs(s.p - s.raw) < 2e-4) { s.p = s.raw; } else { E.dirty = true; }
        }
        s.seen = true;
        s.wasActive = true;
      } else {
        s.wasActive = false;
        if (E.snapNext) { s.p = s.raw; }
      }
      for (var j = 0; j < s.shows.length; j++) {
        var sh = s.shows[j];
        var on = s === active && s.p >= sh.a && s.p <= sh.b;
        if (on !== sh.on) { sh.on = on; sh.el.classList.toggle("is-on", on); }
      }
    }
    var snapped = E.snapNext;
    E.snapNext = false;
    if (active !== E.active) {
      var previous = E.active;
      E.active = active;
      /* a quick dip to black between two demos of the same scene, like a cut */
      if (previous && active && previous.scene === active.scene && !snapped && !E.reduced) { veil.dip = true; }
      emit("section", active, previous);
    }
    var want = active ? active.scene : E.currentName;
    chooseScene(want, dt);
  }
  function chooseScene(want, dt) {
    if (!want) { return; }
    if (!E.currentName) { setScene(want); return; }
    if (want !== E.currentName) { veil.pending = want; veil.target = 1; }
    else if (veil.dip) { veil.dip = false; veil.target = 1; veil.pending = ""; veil.dipping = true; }
    else if (veil.target === 1 && !veil.dipping) { veil.target = 0; veil.pending = ""; }
    var speed = E.reduced ? 14 : (veil.dipping ? 11 : 7.5);
    if (veil.target === 1) {
      veil.value = Math.min(1, veil.value + Math.max(dt, 1 / 60) * speed);
      if (veil.value >= 1) {
        if (veil.pending && veil.pending !== E.currentName) { setScene(veil.pending); }
        veil.pending = "";
        veil.target = 0;
        veil.dipping = false;
      }
    } else if (veil.value > 0) {
      veil.value = Math.max(0, veil.value - Math.max(dt, 1 / 60) * speed * 0.7);
    }
    var op = veil.value > 0 ? veil.value.toFixed(3) : "0";
    if (veil.el.__op !== op) { veil.el.__op = op; veil.el.style.opacity = op; }
  }
  function veilBusy() { return veil.value > 0 || veil.target === 1; }
  function setScene(name) {
    var s = E.ensure(name);
    if (!s) { return; }
    if (E.current && E.current.deactivate) { E.current.deactivate(); }
    Object.keys(E.layers).forEach(function (k) { E.layers[k].style.display = k === name ? "" : "none"; });
    E.current = s;
    E.currentName = name;
    E.renderPass.scene = s.scene;
    E.renderPass.camera = s.camera;
    E.splitPass.scene = s.scene;
    var b = s.bloom || {};
    E.bloom.strength = b.strength !== undefined ? b.strength : 0.8;
    E.bloom.radius = b.radius !== undefined ? b.radius : 0.5;
    E.bloom.threshold = b.threshold !== undefined ? b.threshold : 0.8;
    E.renderer.toneMappingExposure = s.exposure || 1;
    if (s.activate) { s.activate(); }
    E.dirty = true;
    emit("scene", name);
  }

  /* ---------------------------------------------------------------------------------------- loop */
  var raf = 0, last = 0, adaptFrames = 0, adaptTime = 0, looping = false;
  /* one loop only: a wake() fired from inside step() (section change, language, resize) must not start a second one */
  function wake() {
    if (!raf && !looping && E.ok && E.ready && !E.frozen && document.visibilityState !== "hidden") { raf = requestAnimationFrame(loop); }
  }
  E.wake = wake;
  E.invalidate = function () { E.dirty = true; wake(); };
  function loop(now) {
    raf = 0;
    looping = true;
    var dt = last ? (now - last) / 1000 : 1 / 60;
    last = now;
    if (dt > 0.1) { dt = 0.1; }
    var keep = step(dt, true);
    looping = false;
    if ((keep || E.dirty) && !E.frozen && document.visibilityState !== "hidden") { raf = requestAnimationFrame(loop); } else { last = 0; }
  }
  function view() {
    var a = E.active;
    return {
      key: a ? a.key : "", id: a ? a.id : "", scene: a ? a.scene : "", p: a ? a.p : 0, raw: a ? a.raw : 0,
      reduced: E.reduced, time: E.time, w: E.width, h: E.height, tier: E.tier, veil: veil.value
    };
  }
  function step(dt, measure) {
    if (E.resizePending) {
      E.resizePending = false;
      var w = E.width, h = E.height, d = E.dpr;
      measureSize();
      if (w !== E.width || h !== E.height || d !== E.dpr) { applySize(); } else { measureSections(); }
    }
    direct(dt);
    var animating = !E.reduced;
    if (animating) { E.time += dt; }
    if (!E.current) { return false; }
    if (!E.visible) { return veilBusy(); }
    var t0 = performance.now();
    var wants = E.current.update(dt, view()) === true;
    var busy = veilBusy();
    if (E.dirty || animating || wants || busy) {
      var split = E.current.split && E.current.split.on;
      E.renderPass.enabled = !split;
      E.splitPass.enabled = !!split;
      if (split) { E.splitPass.left = E.current.split.left; E.splitPass.right = E.current.split.right; E.splitPass.before = E.current.split.before; }
      E.finish.uniforms.uTime.value = E.time;
      E.renderer.info.reset();
      E.composer.render(dt);
      E.dirty = false;
      if (!E.drawn) { E.drawn = true; emit("drawn"); }
      if (measure) { record(E.active ? E.active.key : E.currentName, dt, performance.now() - t0); adapt(dt); }
    }
    return animating || wants || busy || E.dirty;
  }
  function record(key, dt, cpu) {
    var st = E.stats[key] || (E.stats[key] = { frames: 0, time: 0, cpu: 0, worst: 0, hist: [] });
    st.frames++;
    st.time += dt;
    st.cpu += cpu;
    if (dt > st.worst) { st.worst = dt; }
    if (st.hist.length < 4000) { st.hist.push(dt); }
  }
  function adapt(dt) {
    if (E.frozen || E.noAdapt) { return; }
    adaptFrames++;
    adaptTime += dt;
    if (adaptFrames < 150) { return; }
    var avg = adaptTime / adaptFrames;
    adaptFrames = 0;
    adaptTime = 0;
    if (avg > 1 / 38 && E.downgrades.length < 3) {
      if (E.dprScale > 0.7) {
        E.dprScale = Math.max(0.6, E.dprScale * 0.8);
        E.downgrades.push("dpr " + E.dprScale.toFixed(2));
        E.resizePending = true;
      } else if (E.renderer.shadowMap.enabled) {
        E.renderer.shadowMap.enabled = false;
        E.downgrades.push("shadows off");
        Object.keys(E.scenes).forEach(function (k) { if (E.scenes[k].shadowsChanged) { E.scenes[k].shadowsChanged(); } });
      }
    }
  }

  function fail(reason) {
    E.ok = false;
    E.failReason = String(reason && reason.message ? reason.message : reason);
    root.classList.add("no-webgl");
    if (raf) { cancelAnimationFrame(raf); raf = 0; }
    emit("fail");
  }

  /* ---------------------------------------------------------------------------------------- init */
  /* give the main thread back between start-up steps: the page paints its text and answers input meanwhile */
  function pause() {
    return new Promise(function (resolve) {
      if (window.scheduler && typeof window.scheduler.yield === "function") { window.scheduler.yield().then(resolve, resolve); }
      else { window.setTimeout(resolve, 0); }
    });
  }
  /* waits for the programs (KHR_parallel_shader_compile compiles them off the main thread), at most a few seconds */
  function settle(promise) {
    if (!promise || typeof promise.then !== "function") { return Promise.resolve(); }
    return Promise.race([promise, new Promise(function (resolve) { window.setTimeout(resolve, 3000); })]);
  }
  function alive() { return !E.failReason; }
  function init() {
    collectSections();
    veil.el = document.getElementById("veil");
    E.reduced = TP.reducedMotion();
    TP.on("motion", function () { E.reduced = TP.reducedMotion(); E.snapNext = true; E.invalidate(); });
    var canvas = document.getElementById("stage");
    if (!THREE || !ADD || !canvas) { fail("three.js missing"); return; }
    E.tier = pickTier();
    root.classList.toggle("tier-low", E.tier === "low");
    var renderer;
    try {
      renderer = new THREE.WebGLRenderer({ canvas: canvas, antialias: false, alpha: false, depth: true, stencil: false, powerPreference: "high-performance" });
    } catch (error) { fail(error); return; }
    E.renderer = renderer;
    var gl = renderer.getContext();
    var dbg = gl.getExtension("WEBGL_debug_renderer_info");
    E.gpu = String(dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER));
    renderer.info.autoReset = false;
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1;
    renderer.setClearColor(0x050403, 1);
    renderer.shadowMap.enabled = E.tier === "high";
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    canvas.addEventListener("webglcontextlost", function (event) { event.preventDefault(); fail("context lost"); }, false);
    pause().then(function () {
      if (!alive()) { return null; }
      var pmrem = new THREE.PMREMGenerator(renderer);
      E.env = pmrem.fromScene(new ADD.RoomEnvironment(), 0.04).texture;
      pmrem.dispose();
      return pause();
    }).then(function () {
      if (!alive()) { return null; }
      start();
      return pause();
    }).then(function () {
      if (!alive() || !E.current) { return null; }
      return settle(Promise.all([compileScene(E.current, true), compilePost()]));
    }).then(function () {
      if (!alive()) { return; }
      E.ready = true;
      E.dirty = true;
      wake();
    }).catch(function (error) { fail(error); });
  }
  function start() {
    var renderer = E.renderer;
    measureSize();
    var target = new THREE.WebGLRenderTarget(Math.max(1, Math.round(E.width * E.dpr)), Math.max(1, Math.round(E.height * E.dpr)), {
      type: THREE.HalfFloatType,
      samples: E.tier === "high" ? 4 : 0
    });
    var composer = new ADD.EffectComposer(renderer, target);
    E.renderPass = new ADD.RenderPass(new THREE.Scene(), new THREE.PerspectiveCamera());
    E.splitPass = new SplitPass();
    E.splitPass.enabled = false;
    E.bloom = new BloomPass(new THREE.Vector2(E.width, E.height), 0.8, 0.5, 0.8);
    if (E.tier === "low") {
      var bloomSize = E.bloom.setSize.bind(E.bloom);
      E.bloom.setSize = function (w, h) { bloomSize(Math.max(4, Math.round(w * 0.5)), Math.max(4, Math.round(h * 0.5))); };
    }
    FinishShader.uniforms.uRes.value = new THREE.Vector2(1, 1);
    E.finish = new ADD.ShaderPass(FinishShader);
    /* render targets keep their texture across setSize, so this stays valid after a resize */
    E.finish.uniforms.tBloom.value = E.bloom.renderTargetsHorizontal[0].texture;
    /* last pass, to the screen: no swap, so every frame reuses the same buffer */
    E.finish.needsSwap = false;
    composer.addPass(E.renderPass);
    composer.addPass(E.splitPass);
    composer.addPass(E.bloom);
    composer.addPass(E.finish);
    E.composer = composer;
    E.ok = true;
    root.classList.add("webgl");
    applySize();

    window.addEventListener("scroll", wake, { passive: true });
    window.addEventListener("resize", function () { E.resizePending = true; wake(); });
    document.addEventListener("visibilitychange", function () { if (document.visibilityState !== "hidden") { last = 0; wake(); } });
    if (window.ResizeObserver) {
      new ResizeObserver(function () { measureSections(); E.dirty = true; wake(); }).observe(document.getElementById("main"));
    }
    if (document.fonts && document.fonts.ready) { document.fonts.ready.then(function () { measureSections(); E.invalidate(); }); }
    TP.on("lang", function () { E.invalidate(); });
    /* once the first frame is up, warm the program variants the scene can switch to later (spare lights on); the
       scene's warm() compiles its normal state again at the end, so the renderer is left as it was */
    E.on("drawn", function () {
      window.setTimeout(function () {
        var s = E.current;
        if (s && s.warm && alive()) { s.warm(function () { return compileScene(s, true); }); }
      }, 0);
    });

    measureSections();
    direct(0);
    var first = E.active ? E.active.scene : "crypt";
    setScene(first);
  }
  TP.module("engine", init);

  /* ---------------------------------------------------------------------------------------- QA hooks */
  function percentile(list, q) {
    if (!list.length) { return 0; }
    var sorted = list.slice().sort(function (a, b) { return a - b; });
    return sorted[Math.min(sorted.length - 1, Math.floor(q * sorted.length))];
  }
  window.TP_QA = {
    info: function () {
      return {
        ok: E.ok, fail: E.failReason || "", tier: E.tier, gpu: E.gpu, dpr: E.dpr, size: [E.width, E.height],
        scenes: Object.keys(E.scenes), initMs: E.initMs, reduced: E.reduced, current: E.currentName, ready: E.ready,
        active: E.active ? E.active.key : "", downgrades: E.downgrades.slice(),
        calls: E.renderer ? E.renderer.info.render.calls : 0, triangles: E.renderer ? E.renderer.info.render.triangles : 0,
        demo: E.current && E.current.demoInfo ? E.current.demoInfo() : null
      };
    },
    seek: function (idOrKey, p) {
      measureSections();
      var s = E.section(idOrKey);
      if (!s) { return -1; }
      var y = s.top + TP.clamp(p, 0, 1) * Math.max(0, s.height - s.pinH);
      window.scrollTo(0, Math.round(y));
      E.snapNext = true;
      E.dirty = true;
      wake();
      return Math.round(y);
    },
    /* jump the active demo to a time (seconds since its start), simulating deterministically */
    demo: function (t) { if (E.current && E.current.seekDemo) { E.current.seekDemo(t); E.dirty = true; return true; } return false; },
    freeze: function (on) {
      E.frozen = !!on;
      if (on && raf) { cancelAnimationFrame(raf); raf = 0; }
      last = 0;
      if (!on) { wake(); }
      return E.frozen;
    },
    advance: function (seconds, fps) {
      var rate = fps || 60;
      var n = Math.max(1, Math.round(seconds * rate));
      for (var i = 0; i < n; i++) { E.dirty = true; step(1 / rate, false); }
      return E.time;
    },
    stats: function (reset) {
      var out = {};
      Object.keys(E.stats).forEach(function (key) {
        var st = E.stats[key];
        out[key] = {
          frames: st.frames,
          fps: st.time > 0 ? Math.round(10 * st.frames / st.time) / 10 : 0,
          p95ms: Math.round(1000 * percentile(st.hist, 0.95) * 10) / 10,
          worstMs: Math.round(1000 * st.worst * 10) / 10,
          cpuMs: st.frames ? Math.round(10 * st.cpu / st.frames) / 10 : 0
        };
      });
      if (reset) { E.stats = {}; }
      return out;
    },
    clean: function (on) { root.classList.toggle("qa-clean", !!on); E.invalidate(); return true; },
    grain: function (on) { if (E.finish) { E.finish.uniforms.uGrain.value = on ? 0.014 : 0; E.invalidate(); } return !!on; }
  };
})();
