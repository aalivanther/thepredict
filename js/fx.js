/* ThePredict v4 · efectos propios con estética pixel: partículas cuadradas (tajos rojos, chispas blancas, fuego naranja,
   almas cian), arcos de tajo cuantizados a celdas, telegraph en el suelo, columnas de energía, proyectiles (flecha y
   bolt), cúpula de guardia, marcadores de «la mente» (ruta prevista, X de impacto, línea de mira) y luces de impacto. */
(function () {
  "use strict";
  var TP = window.TP;
  var THREE = window.THREE;
  if (!THREE) { return; }
  var P = TP.pixel;
  var FX = TP.fx = {};
  var hdr = function (hex, gain) { return new THREE.Color(hex).multiplyScalar(gain); };
  FX.hdr = hdr;
  FX.COLORS = { red: 0xff3b4a, pink: 0xff7aa0, white: 0xffffff, orange: 0xff9a3c, cyan: 0x5ff0ff, ember: 0xff6a2a, violet: 0xb48cff, green: 0x4fe3a1, gold: 0xffd25a };

  /* ---------------------------------------------------------------------------------------- pixel particles */
  function Pixels(max, sprite, blending, seed) {
    this.max = max;
    this.pos = new Float32Array(max * 3);
    this.col = new Float32Array(max * 3);
    this.size = new Float32Array(max);
    this.alpha = new Float32Array(max);
    this.side = new Float32Array(max);
    this.vel = new Float32Array(max * 3);
    this.life = new Float32Array(max);
    this.life0 = new Float32Array(max);
    this.grav = new Float32Array(max);
    this.drag = new Float32Array(max);
    this.shrink = new Uint8Array(max);
    this.head = 0;
    this.rnd = TP.rng(seed || 77);
    var g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.BufferAttribute(this.pos, 3));
    g.setAttribute("aColor", new THREE.BufferAttribute(this.col, 3));
    g.setAttribute("aSize", new THREE.BufferAttribute(this.size, 1));
    g.setAttribute("aAlpha", new THREE.BufferAttribute(this.alpha, 1));
    g.setAttribute("aSide", new THREE.BufferAttribute(this.side, 1));
    for (var i = 0; i < max; i++) { this.pos[i * 3 + 1] = -999; }
    this.geo = g;
    var mat = new THREE.ShaderMaterial({
      uniforms: { uMap: { value: P.sprite(sprite) }, uScale: { value: 600 }, uSide: { value: 0 } },
      transparent: true, depthWrite: false, depthTest: true, blending: blending || THREE.AdditiveBlending, fog: false,
      vertexShader: [
        "attribute vec3 aColor; attribute float aSize; attribute float aAlpha; attribute float aSide;",
        "uniform float uScale; uniform float uSide; varying vec3 vColor; varying float vAlpha;",
        "void main() {",
        "  vColor = aColor; vAlpha = aAlpha;",
        "  if (aSide > 0.5 && uSide > 0.5 && abs(aSide - uSide) > 0.5) { vAlpha = 0.0; }",
        "  vec4 mv = modelViewMatrix * vec4(position, 1.0);",
        "  gl_PointSize = clamp(aSize * uScale / max(1.5, -mv.z), 1.0, 64.0);",
        "  gl_Position = projectionMatrix * mv;",
        "}"
      ].join("\n"),
      fragmentShader: [
        "uniform sampler2D uMap; varying vec3 vColor; varying float vAlpha;",
        "void main() {",
        "  float a = texture2D(uMap, gl_PointCoord).a;",
        "  if (a < 0.5 || vAlpha <= 0.0) { discard; }",
        "  gl_FragColor = vec4(vColor * vAlpha, vAlpha);",
        "}"
      ].join("\n")
    });
    this.material = mat;
    this.points = new THREE.Points(g, mat);
    this.points.frustumCulled = false;
    this.points.renderOrder = 5;
    this.active = 0;
  }
  var tmpC = new THREE.Color();
  Pixels.prototype.emit = function (o, cfg) {
    var r = this.rnd;
    var n = cfg.count || 1;
    var colors = cfg.colors || [cfg.color || 0xffffff];
    var speed = cfg.speed || [1, 3];
    var life = cfg.life || [0.4, 0.9];
    var size = cfg.size || [0.1, 0.2];
    var gain = cfg.gain === undefined ? 1.8 : cfg.gain;
    var dir = cfg.dir;
    var spread = cfg.spread === undefined ? Math.PI : cfg.spread;
    var jitter = cfg.jitter || 0;
    for (var k = 0; k < n; k++) {
      var i = this.head;
      this.head = (this.head + 1) % this.max;
      var sp = speed[0] + r() * (speed[1] - speed[0]);
      var vx, vy, vz;
      if (dir) {
        /* within a cone around dir */
        var th = r() * Math.PI * 2, ph = r() * spread;
        var ax = Math.abs(dir.x) < 0.9 ? new THREE.Vector3(1, 0, 0) : new THREE.Vector3(0, 1, 0);
        var u = new THREE.Vector3().crossVectors(dir, ax).normalize(), v = new THREE.Vector3().crossVectors(dir, u);
        vx = (dir.x * Math.cos(ph) + (u.x * Math.cos(th) + v.x * Math.sin(th)) * Math.sin(ph)) * sp;
        vy = (dir.y * Math.cos(ph) + (u.y * Math.cos(th) + v.y * Math.sin(th)) * Math.sin(ph)) * sp;
        vz = (dir.z * Math.cos(ph) + (u.z * Math.cos(th) + v.z * Math.sin(th)) * Math.sin(ph)) * sp;
      } else {
        var t2 = r() * Math.PI * 2, p2 = Math.acos(2 * r() - 1);
        vx = Math.cos(t2) * Math.sin(p2) * sp; vy = Math.cos(p2) * sp; vz = Math.sin(t2) * Math.sin(p2) * sp;
      }
      vy += cfg.up || 0;
      this.pos[i * 3] = o.x + (r() - 0.5) * jitter; this.pos[i * 3 + 1] = o.y + (r() - 0.5) * jitter; this.pos[i * 3 + 2] = o.z + (r() - 0.5) * jitter;
      this.vel[i * 3] = vx; this.vel[i * 3 + 1] = vy; this.vel[i * 3 + 2] = vz;
      tmpC.setHex(colors[Math.floor(r() * colors.length)]).multiplyScalar(gain * (0.8 + r() * 0.4));
      this.col[i * 3] = tmpC.r; this.col[i * 3 + 1] = tmpC.g; this.col[i * 3 + 2] = tmpC.b;
      this.size[i] = size[0] + r() * (size[1] - size[0]);
      this.side[i] = cfg.side || 0;
      this.life[i] = this.life0[i] = life[0] + r() * (life[1] - life[0]);
      this.alpha[i] = 1;
      this.grav[i] = cfg.gravity === undefined ? 4 : cfg.gravity;
      this.drag[i] = cfg.drag === undefined ? 1.5 : cfg.drag;
      this.shrink[i] = cfg.shrink === undefined ? 1 : (cfg.shrink ? 1 : 0);
    }
  };
  Pixels.prototype.update = function (dt) {
    var any = false;
    for (var i = 0; i < this.max; i++) {
      if (this.life[i] <= 0) { continue; }
      any = true;
      this.life[i] -= dt;
      var k = TP.clamp(this.life[i] / this.life0[i], 0, 1);
      var d = Math.exp(-this.drag[i] * dt);
      this.vel[i * 3] *= d; this.vel[i * 3 + 2] *= d;
      this.vel[i * 3 + 1] = this.vel[i * 3 + 1] * d - this.grav[i] * dt;
      this.pos[i * 3] += this.vel[i * 3] * dt;
      this.pos[i * 3 + 1] += this.vel[i * 3 + 1] * dt;
      this.pos[i * 3 + 2] += this.vel[i * 3 + 2] * dt;
      if (this.pos[i * 3 + 1] < 0.02 && this.grav[i] > 0) { this.pos[i * 3 + 1] = 0.02; this.vel[i * 3 + 1] *= -0.3; }
      this.alpha[i] = k < 0.35 ? k / 0.35 : 1;
      if (this.shrink[i]) { this.alpha[i] *= 1; }
      if (this.life[i] <= 0) { this.pos[i * 3 + 1] = -999; this.alpha[i] = 0; }
    }
    this.geo.attributes.position.needsUpdate = true;
    this.geo.attributes.aColor.needsUpdate = true;
    this.geo.attributes.aSize.needsUpdate = true;
    this.geo.attributes.aAlpha.needsUpdate = true;
    this.geo.attributes.aSide.needsUpdate = true;
    return any;
  };
  Pixels.prototype.clear = function () { for (var i = 0; i < this.max; i++) { this.life[i] = 0; this.pos[i * 3 + 1] = -999; this.alpha[i] = 0; } this.geo.attributes.position.needsUpdate = true; };

  /* ---------------------------------------------------------------------------------------- pixel shaders */
  var HASH = "float hash(vec2 p) { p = fract(p * vec2(443.897, 441.423)); p += dot(p, p.yx + 19.19); return fract((p.x + p.y) * p.x); }";
  /* a sweeping slash arc, quantised to cells so it reads as big pixels */
  function arcMaterial() {
    return new THREE.ShaderMaterial({
      uniforms: { uColor: { value: hdr(0xff3b4a, 3) }, uK: { value: 0 }, uCell: { value: 0.16 }, uSpan: { value: 1.1 } },
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, fog: false,
      vertexShader: "varying vec2 vP; void main() { vP = position.xy; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",
      fragmentShader: [
        "uniform vec3 uColor; uniform float uK; uniform float uCell; uniform float uSpan; varying vec2 vP;", HASH,
        "void main() {",
        "  vec2 cell = floor(vP / uCell);",
        "  vec2 cp = (cell + 0.5) * uCell;",
        "  float a = atan(cp.y, cp.x);",
        "  float r = length(cp);",
        "  float head = mix(-uSpan, uSpan, uK);",
        "  float trail = smoothstep(head - 1.3, head, a) * step(a, head + 0.03);",
        "  float rim = step(0.72, r) * step(r, 1.0 + 0.28 * hash(cell));",
        "  float thin = 1.0 - smoothstep(0.86, 1.0, r) * (1.0 - hash(cell + 3.0)) * 0.9;",
        "  float alpha = trail * rim * thin * (1.0 - uK * 0.55) * step(0.001, uK) * step(uK, 0.999);",
        "  alpha *= step(0.08, hash(cell + 7.0) + trail);",
        "  if (alpha < 0.08) { discard; }",
        "  gl_FragColor = vec4(uColor * alpha, alpha);",
        "}"
      ].join("\n")
    });
  }
  /* a telegraph ring on the floor: pixel ring, rotating ticks and a fill that grows with the charge */
  function ringMaterial() {
    return new THREE.ShaderMaterial({
      uniforms: { uColor: { value: hdr(0xff3b4a, 2.2) }, uOn: { value: 0 }, uFill: { value: 0 }, uTime: { value: 0 }, uCell: { value: 0.22 }, uMode: { value: 0 } },
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, fog: false,
      vertexShader: "varying vec2 vP; void main() { vP = position.xy; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",
      fragmentShader: [
        "uniform vec3 uColor; uniform float uOn; uniform float uFill; uniform float uTime; uniform float uCell; uniform float uMode; varying vec2 vP;", HASH,
        "void main() {",
        "  vec2 cell = floor(vP / uCell);",
        "  vec2 cp = (cell + 0.5) * uCell;",
        "  float r = length(cp);",
        "  float a = atan(cp.y, cp.x);",
        "  float ring = step(0.9, r) * step(r, 1.0);",
        "  float ticks = step(0.78, r) * step(r, 0.9) * step(0.5, fract(a * 2.5 / 3.1416 + uTime * 0.6));",
        "  float fill = step(r, uFill) * (0.22 + 0.1 * step(0.5, fract((cp.x + cp.y) / uCell * 0.5)));",
        "  float flicker = 0.75 + 0.25 * step(0.5, hash(cell + floor(uTime * 12.0)));",
        "  float alpha = uOn * flicker * (ring + ticks * 0.8 + fill);",
        "  if (uMode > 0.5) { alpha = uOn * step(uFill - 0.08, r) * step(r, uFill) * (1.0 - uFill) * 1.6; }",
        "  if (alpha < 0.05) { discard; }",
        "  gl_FragColor = vec4(uColor * alpha, alpha);",
        "}"
      ].join("\n")
    });
  }
  /* an energy column: pixel columns rising on crossed quads */
  function columnMaterial() {
    return new THREE.ShaderMaterial({
      uniforms: { uColor: { value: hdr(0xff3b4a, 2.5) }, uK: { value: 0 }, uTime: { value: 0 } },
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, fog: false,
      vertexShader: "varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",
      fragmentShader: [
        "uniform vec3 uColor; uniform float uK; uniform float uTime; varying vec2 vUv;", HASH,
        "void main() {",
        "  vec2 cell = floor(vUv * vec2(14.0, 40.0));",
        "  float colSeed = hash(vec2(cell.x, 1.0));",
        "  float h = (0.4 + 0.6 * colSeed) * smoothstep(0.0, 0.35, uK) * (1.0 - smoothstep(0.6, 1.0, uK));",
        "  float rise = fract(cell.y / 40.0 - uTime * 1.2 + colSeed);",
        "  float on = step(vUv.y, h) * step(0.35, hash(cell + floor(uTime * 10.0)) + 0.3 * (1.0 - vUv.y));",
        "  float side = 1.0 - smoothstep(0.2, 0.5, abs(vUv.x - 0.5));",
        "  float alpha = on * side * (0.5 + 0.5 * rise);",
        "  if (alpha < 0.08) { discard; }",
        "  gl_FragColor = vec4(uColor * alpha, alpha);",
        "}"
      ].join("\n")
    });
  }
  /* guard dome: pixel hex cells with fresnel */
  function domeMaterial() {
    return new THREE.ShaderMaterial({
      uniforms: { uColor: { value: hdr(0x5ff0ff, 2.2) }, uOn: { value: 0 }, uTime: { value: 0 }, uHit: { value: 0 } },
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, fog: false,
      vertexShader: "varying vec3 vN; varying vec3 vV; varying vec3 vL; void main() { vL = position; vec4 mv = modelViewMatrix * vec4(position, 1.0); vN = normalize(normalMatrix * normal); vV = normalize(-mv.xyz); gl_Position = projectionMatrix * mv; }",
      fragmentShader: [
        "uniform vec3 uColor; uniform float uOn; uniform float uTime; uniform float uHit; varying vec3 vN; varying vec3 vV; varying vec3 vL;", HASH,
        "void main() {",
        "  float fres = pow(1.0 - abs(dot(normalize(vN), normalize(vV))), 2.0);",
        "  vec2 p = vec2(atan(vL.z, vL.x) * 4.0, vL.y * 5.0);",
        "  vec2 cell = floor(p);",
        "  float on = step(0.45, hash(cell + floor(uTime * 6.0)));",
        "  float edge = step(0.82, max(abs(fract(p.x) - 0.5) * 2.0, abs(fract(p.y) - 0.5) * 2.0));",
        "  float a = uOn * (0.03 + 0.28 * fres + 0.16 * edge * on + uHit * 0.35);",
        "  gl_FragColor = vec4(uColor * a, a);",
        "}"
      ].join("\n")
    });
  }

  /* ---------------------------------------------------------------------------------------- the system */
  FX.create = function (scene, world, tier) {
    var high = tier !== "low";
    var fx = {};
    var rnd = TP.rng(2024);
    var px = new Pixels(high ? 2600 : 900, "square", THREE.AdditiveBlending, 11);
    var sparks = new Pixels(high ? 700 : 260, "spark", THREE.AdditiveBlending, 12);
    scene.add(px.points);
    scene.add(sparks.points);
    fx.px = px; fx.sparks = sparks;
    /* split-screen: effects emitted while fx.side is 1 or 2 only show on that half (0 = both) */
    fx.side = 0;
    var showing = 0;
    var emitPx = px.emit.bind(px), emitSparks = sparks.emit.bind(sparks);
    px.emit = function (o, cfg) { cfg.side = fx.side; emitPx(o, cfg); };
    sparks.emit = function (o, cfg) { cfg.side = fx.side; emitSparks(o, cfg); };
    function sideOk(obj) { var s = obj.userData.side || 0; return !showing || !s || s === showing; }
    function vis(obj, on) { obj.userData.on = !!on; if (on) { obj.userData.side = fx.side; } obj.visible = !!on && sideOk(obj); }
    var sided = [];
    fx.showSide = function (side) {
      showing = side || 0;
      px.material.uniforms.uSide.value = showing;
      sparks.material.uniforms.uSide.value = showing;
      for (var i = 0; i < sided.length; i++) { var o = sided[i]; o.visible = !!o.userData.on && sideOk(o); }
    };

    /* arcs */
    var arcs = [];
    var arcGeo = new THREE.RingGeometry(0.6, 1.3, 48, 1, -1.25, 2.5);
    for (var i = 0; i < 6; i++) {
      var m = new THREE.Mesh(arcGeo, arcMaterial());
      m.visible = false;
      m.frustumCulled = false;
      m.renderOrder = 6;
      scene.add(m);
      sided.push(m);
      arcs.push({ mesh: m, t: 0, dur: 0.3, on: false });
    }
    var arcHead = 0;
    /* pos: Vector3; yaw: facing; cfg: {color, radius, dur, pitch (0 horizontal sweep, 1 vertical), roll, span, cell} */
    fx.slash = function (pos, yaw, cfg) {
      cfg = cfg || {};
      var a = arcs[arcHead]; arcHead = (arcHead + 1) % arcs.length;
      a.on = true; a.t = 0; a.dur = cfg.dur || 0.32;
      vis(a.mesh, true);
      a.mesh.position.copy(pos);
      a.mesh.rotation.set(0, 0, 0);
      a.mesh.rotation.y = yaw;
      if (cfg.vertical) { a.mesh.rotateY(Math.PI / 2); a.mesh.rotateX(cfg.roll || 0); } else { a.mesh.rotateX(-Math.PI / 2 + (cfg.tilt || 0)); a.mesh.rotateZ(cfg.roll || -Math.PI / 2); }
      a.mesh.scale.setScalar(cfg.radius || 1.6);
      a.mesh.material.uniforms.uColor.value.copy(hdr(cfg.color || FX.COLORS.red, cfg.gain || 3));
      a.mesh.material.uniforms.uCell.value = (cfg.cell || 0.16);
      a.mesh.material.uniforms.uSpan.value = cfg.span || 1.1;
      a.mesh.material.uniforms.uK.value = 0.001;
      a.flip = cfg.flip ? -1 : 1;
    };

    /* rings (telegraph, shockwave) */
    var rings = [];
    var ringGeo = new THREE.CircleGeometry(1, 48);
    for (i = 0; i < 4; i++) {
      var rm = new THREE.Mesh(ringGeo, ringMaterial());
      rm.rotation.x = -Math.PI / 2;
      rm.position.y = 0.04;
      rm.visible = false;
      rm.frustumCulled = false;
      rm.renderOrder = 4;
      scene.add(rm);
      sided.push(rm);
      rings.push({ mesh: rm, on: false, t: 0, dur: 0, mode: 0 });
    }
    /* a persistent telegraph: returns a handle {set(fill), off()} */
    fx.telegraph = function (pos, radius, color) {
      var r = rings[0];
      r.on = true; r.mode = 0; r.dur = 0;
      vis(r.mesh, true);
      r.mesh.position.set(pos.x, 0.04, pos.z);
      r.mesh.scale.setScalar(radius);
      r.mesh.material.uniforms.uColor.value.copy(hdr(color || FX.COLORS.red, 2.2));
      r.mesh.material.uniforms.uOn.value = 1;
      r.mesh.material.uniforms.uMode.value = 0;
      r.mesh.material.uniforms.uFill.value = 0;
      return {
        set: function (fill, p) { r.mesh.material.uniforms.uFill.value = TP.clamp(fill, 0, 1); if (p) { r.mesh.position.set(p.x, 0.04, p.z); } },
        off: function () { r.on = false; vis(r.mesh, false); r.mesh.material.uniforms.uOn.value = 0; }
      };
    };
    var shockHead = 1;
    fx.shock = function (pos, radius, color, dur) {
      var r = rings[shockHead]; shockHead = 1 + (shockHead % 3);
      r.on = true; r.mode = 1; r.t = 0; r.dur = dur || 0.5;
      vis(r.mesh, true);
      r.mesh.position.set(pos.x, 0.05, pos.z);
      r.mesh.scale.setScalar(radius || 3);
      r.mesh.material.uniforms.uColor.value.copy(hdr(color || FX.COLORS.red, 2.6));
      r.mesh.material.uniforms.uOn.value = 1;
      r.mesh.material.uniforms.uMode.value = 1;
      r.mesh.material.uniforms.uFill.value = 0.05;
    };

    /* columns */
    var columns = [];
    for (i = 0; i < 3; i++) {
      var cg = new THREE.Group();
      var cm = columnMaterial();
      for (var k = 0; k < 2; k++) {
        var q = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), cm);
        q.position.y = 0.5;
        q.rotation.y = k * Math.PI / 2;
        q.frustumCulled = false;
        q.renderOrder = 6;
        cg.add(q);
      }
      cg.visible = false;
      scene.add(cg);
      sided.push(cg);
      columns.push({ group: cg, mat: cm, on: false, t: 0, dur: 0.8 });
    }
    var colHead = 0;
    fx.column = function (pos, cfg) {
      cfg = cfg || {};
      var c = columns[colHead]; colHead = (colHead + 1) % columns.length;
      c.on = true; c.t = 0; c.dur = cfg.dur || 0.8;
      vis(c.group, true);
      c.group.position.set(pos.x, 0, pos.z);
      c.group.scale.set(cfg.width || 1.2, cfg.height || 5, cfg.width || 1.2);
      c.mat.uniforms.uColor.value.copy(hdr(cfg.color || FX.COLORS.red, cfg.gain || 2.5));
      c.mat.uniforms.uK.value = 0.001;
    };

    /* guard dome */
    var dome = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 12, 0, Math.PI * 2, 0, Math.PI * 0.6), domeMaterial());
    dome.visible = false;
    dome.frustumCulled = false;
    dome.renderOrder = 6;
    scene.add(dome);
    sided.push(dome);
    var domeState = { on: 0, want: 0, hit: 0 };
    fx.dome = {
      set: function (on, pos, radius, color) {
        domeState.want = on ? 1 : 0;
        if (on) { dome.userData.side = fx.side; }
        if (pos) { dome.position.set(pos.x, 0.1, pos.z); }
        if (radius) { dome.scale.setScalar(radius); }
        if (color) { dome.material.uniforms.uColor.value.copy(hdr(color, 2.2)); }
      },
      hit: function () { domeState.hit = 1; }
    };

    /* projectiles */
    var projectiles = [];
    var arrowGeo = P.merge([
      P.box(P.atlas(["sword", "bow", "aug_blade"]), "sword", 0.05, 0.05, 0.5, 0, 0, 0),
      P.box(P.atlas(["sword", "bow", "aug_blade"]), "bow", 0.03, 0.14, 0.14, 0, 0, -0.2)
    ]);
    var arrowMat = new THREE.MeshStandardMaterial({ color: 0xcfd6e0, roughness: 0.6 });
    var boltGeo = new THREE.BoxGeometry(0.34, 0.34, 0.34);
    var boltMat = new THREE.MeshBasicMaterial({ color: hdr(0x5ff0ff, 4) });
    for (i = 0; i < 6; i++) {
      var pm = new THREE.Mesh(i < 3 ? arrowGeo : boltGeo, i < 3 ? arrowMat : boltMat);
      pm.visible = false;
      pm.castShadow = i < 3;
      scene.add(pm);
      sided.push(pm);
      projectiles.push({ mesh: pm, kind: i < 3 ? "arrow" : "bolt", on: false, t: 0, dur: 1, from: new THREE.Vector3(), to: new THREE.Vector3(), arc: 0, cb: null, side: 0 });
    }
    /* kind: arrow | bolt; to may be a function returning the live target position */
    fx.shoot = function (kind, from, to, dur, cb, cfg) {
      cfg = cfg || {};
      var pr = null;
      for (var j = 0; j < projectiles.length; j++) { if (projectiles[j].kind === kind && !projectiles[j].on) { pr = projectiles[j]; break; } }
      if (!pr) { pr = projectiles[kind === "arrow" ? 0 : 3]; }
      pr.on = true; pr.t = 0; pr.dur = dur || 0.5; pr.cb = cb || null;
      pr.from.copy(from); pr.toFn = typeof to === "function" ? to : null; pr.to.copy(typeof to === "function" ? to() : to);
      pr.arc = cfg.arc === undefined ? (kind === "arrow" ? 0.6 : 0.2) : cfg.arc;
      pr.color = cfg.color || null;
      pr.side = fx.side;
      vis(pr.mesh, true);
      pr.mesh.position.copy(from);
      if (kind === "bolt") { pr.mesh.material.color.copy(hdr(cfg.color || 0x5ff0ff, 4)); }
      pr.miss = !!cfg.miss;
      return pr;
    };

    /* the mind: predicted path, a cross marker, a dotted sight line */
    var markGeo = new THREE.BoxGeometry(0.36, 0.04, 0.36);
    var markMat = new THREE.MeshBasicMaterial({ color: hdr(0x59e1ff, 1.6), transparent: true, opacity: 0.9 });
    var marks = new THREE.InstancedMesh(markGeo, markMat, 24);
    marks.frustumCulled = false;
    marks.count = 0;
    scene.add(marks);
    sided.push(marks);
    var mtx = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler(), v3 = new THREE.Vector3(), s3 = new THREE.Vector3();
    fx.path = function (points, color) {
      var n = Math.min(points ? points.length : 0, 24);
      marks.count = n;
      vis(marks, n > 0);
      if (color) { markMat.color.copy(hdr(color, 1.6)); }
      for (var j = 0; j < n; j++) {
        e.set(0, Math.PI / 4, 0); q.setFromEuler(e);
        var sc = 0.6 + 0.4 * (1 - j / Math.max(1, n));
        mtx.compose(v3.set(points[j].x, 0.06 + j * 0.002, points[j].z), q, s3.set(sc, 1, sc));
        marks.setMatrixAt(j, mtx);
      }
      marks.instanceMatrix.needsUpdate = true;
    };
    var cross = new THREE.Group();
    var crossMat = new THREE.MeshBasicMaterial({ color: hdr(0xff3b4a, 2.4) });
    [[Math.PI / 4], [-Math.PI / 4]].forEach(function (r) {
      var bar = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.05, 0.22), crossMat);
      bar.rotation.y = r[0];
      cross.add(bar);
    });
    var crossRing = new THREE.Mesh(new THREE.RingGeometry(0.85, 1.0, 4), crossMat);
    crossRing.rotation.x = -Math.PI / 2;
    crossRing.rotation.z = Math.PI / 4;
    cross.add(crossRing);
    cross.visible = false;
    cross.position.y = 0.06;
    scene.add(cross);
    sided.push(cross);
    fx.cross = function (on, pos, color) {
      vis(cross, on);
      if (pos) { cross.position.set(pos.x, 0.06, pos.z); }
      if (color) { crossMat.color.copy(hdr(color, 2.4)); }
    };
    var sightGeo = new THREE.BufferGeometry();
    sightGeo.setAttribute("position", new THREE.BufferAttribute(new Float32Array(6), 3));
    var sight = new THREE.Line(sightGeo, new THREE.LineDashedMaterial({ color: hdr(0x59e1ff, 1.8), dashSize: 0.25, gapSize: 0.2, transparent: true, opacity: 0.9, depthWrite: false }));
    sight.visible = false;
    sight.frustumCulled = false;
    scene.add(sight);
    sided.push(sight);
    fx.sight = function (on, a, b) {
      vis(sight, on);
      if (on && a && b) {
        var arr = sightGeo.attributes.position.array;
        arr[0] = a.x; arr[1] = a.y; arr[2] = a.z; arr[3] = b.x; arr[4] = b.y; arr[5] = b.z;
        sightGeo.attributes.position.needsUpdate = true;
        sight.computeLineDistances();
      }
    };

    /* impact lights (borrowed from the world) */
    var spare = world.lights.spare;
    var lightJobs = [];
    fx.light = function (pos, color, intensity, dur) {
      var l = spare[lightJobs.length % spare.length];
      l.position.set(pos.x, pos.y + 0.6, pos.z);
      l.color.setHex(color);
      lightJobs.push({ l: l, t: 0, dur: dur || 0.35, i: intensity || 30 });
    };

    /* fountains: continuous emitters (brazier flames, torches, boss embers) */
    var fountains = [];
    fx.fountain = function (fn, rate, cfg, pool) { var f = { fn: fn, rate: rate, cfg: cfg, acc: 0, pool: pool || px, on: true }; fountains.push(f); return f; };
    world && TP.world.BRAZIERS.forEach(function (b) {
      var o = new THREE.Vector3(b[0], 3.55, b[1]);
      fx.fountain(function () { return o; }, high ? 26 : 10, { colors: [0x5ff0ff, 0x9af7ff, 0xffffff, 0x3cc8ff], speed: [0.3, 1.1], life: [0.5, 1.1], size: [0.1, 0.22], gravity: -1.4, drag: 1.2, up: 1.2, jitter: 0.6, gain: 2.2, spread: 0.6, dir: new THREE.Vector3(0, 1, 0) });
    });
    TP.world.TORCHES.forEach(function (t, i) {
      if (!high && i > 1) { return; }
      var o = new THREE.Vector3(t[0] + Math.sin(t[2]) * 0.65, 4.65, t[1] + Math.cos(t[2]) * 0.65);
      fx.fountain(function () { return o; }, high ? 9 : 4, { colors: [0xff9a3c, 0xffd25a, 0xff6a2a], speed: [0.2, 0.7], life: [0.4, 0.9], size: [0.08, 0.16], gravity: -1.2, drag: 1.5, up: 0.8, jitter: 0.35, gain: 2.4, spread: 0.5, dir: new THREE.Vector3(0, 1, 0) });
    });

    /* sounds ride on the effects (silent unless the user turned the sound on) */
    var snd = function (name) { if (TP.audio && TP.audio.on) { TP.audio.play(name); } };
    fx.snd = snd;
    var slash0 = fx.slash, shock0 = fx.shock, shoot0 = fx.shoot, domeHit0 = fx.dome.hit;
    fx.slash = function (pos, yaw, cfg) { slash0(pos, yaw, cfg); snd("slash"); };
    fx.shock = function (pos, radius, color, dur) { shock0(pos, radius, color, dur); snd("thud"); };
    fx.shoot = function (kind, from, to, dur, cb, cfg) { var pr = shoot0(kind, from, to, dur, cb, cfg); if (kind === "bolt") { snd("bolt"); } return pr; };
    fx.dome.hit = function () { domeHit0(); snd("parry"); };
    /* convenience bursts */
    fx.hitRed = function (pos, n) { px.emit(pos, { count: n || 40, colors: [0xff3b4a, 0xff6a7a, 0xff2030, 0xffa0a8], speed: [1.5, 5], life: [0.35, 0.8], size: [0.12, 0.26], gravity: 5, drag: 2.5, jitter: 0.4, gain: 2.6 }); if ((n || 40) >= 40) { snd("hit"); } };
    fx.sparkWhite = function (pos, n, dir) { sparks.emit(pos, { count: n || 24, colors: [0xffffff, 0xfff4d0, 0xd8f8ff], speed: [2, 6], life: [0.25, 0.6], size: [0.1, 0.2], gravity: 7, drag: 3, dir: dir, spread: dir ? 0.9 : Math.PI, gain: 3.4 }); };
    fx.fireOrange = function (pos, n) { px.emit(pos, { count: n || 30, colors: [0xff9a3c, 0xffd25a, 0xff6a2a, 0xffffff], speed: [0.3, 1.6], life: [0.4, 0.9], size: [0.12, 0.28], gravity: -2.5, drag: 1.5, up: 1.5, jitter: 0.6, gain: 2.4 }); };
    fx.soulCyan = function (pos, n) { px.emit(pos, { count: n || 20, colors: [0x5ff0ff, 0xffffff, 0x3cc8ff], speed: [0.5, 2.5], life: [0.4, 0.9], size: [0.1, 0.22], gravity: 1, drag: 2, jitter: 0.5, gain: 2.6 }); };
    fx.dust = function (pos, n) { px.emit(pos, { count: n || 22, colors: [0x8a857c, 0x6b6760, 0xa29c91], speed: [1, 3.5], life: [0.4, 0.9], size: [0.12, 0.3], gravity: 3, drag: 2.5, up: 1.5, jitter: 0.5, gain: 0.8 }); };
    fx.emberTrail = function (pos, n) { px.emit(pos, { count: n || 3, colors: [0xff6a2a, 0xffa050], speed: [0.1, 0.5], life: [0.4, 0.8], size: [0.07, 0.14], gravity: -1, drag: 1.5, jitter: 0.3, gain: 2.2 }); };

    var flash = document.getElementById("flash");
    var flashT = 0, flashDur = 0, flashColor = "#fff";
    fx.flash = function (color, dur, alpha) { flashColor = color || "#ffffff"; flashT = 0; flashDur = dur || 0.25; flashAlpha = alpha === undefined ? 0.85 : alpha; if (flash) { flash.style.background = flashColor; } };
    var flashAlpha = 0.85;
    var shake = { t: 0, dur: 0, amp: 0 };
    fx.shake = function (amp, dur) { shake.t = 0; shake.dur = dur || 0.35; shake.amp = amp || 0.15; };
    fx.shakeOffset = function (out) {
      if (shake.t >= shake.dur) { out.set(0, 0, 0); return out; }
      var k = 1 - shake.t / shake.dur;
      out.set((rnd() - 0.5) * 2 * shake.amp * k, (rnd() - 0.5) * 2 * shake.amp * k, 0);
      return out;
    };

    fx.update = function (dt, t) {
      var any = false;
      for (var j = 0; j < fountains.length; j++) {
        var f = fountains[j];
        if (!f.on) { continue; }
        f.acc += dt * f.rate;
        var n = Math.floor(f.acc);
        if (n > 0) { f.acc -= n; f.cfg.count = Math.min(n, 12); f.pool.emit(f.fn(), f.cfg); }
      }
      any = px.update(dt) || any;
      any = sparks.update(dt) || any;
      arcs.forEach(function (a) {
        if (!a.on) { return; }
        a.t += dt;
        var k = a.t / a.dur;
        a.mesh.material.uniforms.uK.value = a.flip < 0 ? 1 - k : k;
        if (k >= 1) { a.on = false; vis(a.mesh, false); }
        any = true;
      });
      rings.forEach(function (r) {
        if (!r.on) { return; }
        r.mesh.material.uniforms.uTime.value = t;
        if (r.mode === 1) {
          r.t += dt;
          var k = r.t / r.dur;
          r.mesh.material.uniforms.uFill.value = 0.05 + 0.95 * TP.easeOut(k);
          if (k >= 1) { r.on = false; vis(r.mesh, false); }
        }
        any = true;
      });
      columns.forEach(function (c) {
        if (!c.on) { return; }
        c.t += dt;
        var k = c.t / c.dur;
        c.mat.uniforms.uK.value = k;
        c.mat.uniforms.uTime.value = t;
        if (k >= 1) { c.on = false; vis(c.group, false); }
        any = true;
      });
      domeState.on = TP.damp(domeState.on, domeState.want, 14, dt);
      domeState.hit = Math.max(0, domeState.hit - dt * 5);
      dome.userData.on = domeState.on > 0.02;
      dome.visible = dome.userData.on && sideOk(dome);
      dome.material.uniforms.uOn.value = domeState.on;
      dome.material.uniforms.uHit.value = domeState.hit;
      dome.material.uniforms.uTime.value = t;
      projectiles.forEach(function (pr) {
        if (!pr.on) { return; }
        pr.t += dt;
        var k = Math.min(1, pr.t / pr.dur);
        if (pr.toFn) { pr.to.copy(pr.toFn()); }
        var x = pr.from.x + (pr.to.x - pr.from.x) * k, z = pr.from.z + (pr.to.z - pr.from.z) * k;
        var y = pr.from.y + (pr.to.y - pr.from.y) * k + Math.sin(Math.PI * k) * pr.arc;
        var prev = pr.mesh.position.clone();
        pr.mesh.position.set(x, y, z);
        var keepSide = fx.side;
        fx.side = pr.side;
        if (pr.kind === "arrow") { if (prev.distanceToSquared(pr.mesh.position) > 1e-6) { pr.mesh.lookAt(pr.mesh.position.clone().add(pr.mesh.position.clone().sub(prev))); } }
        else { pr.mesh.rotation.x += dt * 9; pr.mesh.rotation.y += dt * 7; px.emit(pr.mesh.position, { count: 2, colors: [pr.color || 0x5ff0ff, 0xffffff], speed: [0.1, 0.5], life: [0.2, 0.45], size: [0.1, 0.2], gravity: 0, drag: 2, gain: 2.5, jitter: 0.2 }); }
        if (k >= 1) {
          pr.on = false; vis(pr.mesh, false);
          if (pr.kind === "bolt") { px.emit(pr.mesh.position, { count: 36, colors: [pr.color || 0x5ff0ff, 0xffffff, 0x3cc8ff], speed: [1.5, 5], life: [0.3, 0.7], size: [0.1, 0.24], gravity: 3, drag: 2.5, gain: 2.6 }); fx.light(pr.mesh.position, pr.color || 0x5ff0ff, 30, 0.3); }
          else if (!pr.miss) { fx.sparkWhite(pr.mesh.position, 10); }
          else { fx.dust(pr.mesh.position, 8); }
          if (pr.cb) { pr.cb(pr.mesh.position); }
        }
        fx.side = keepSide;
        any = true;
      });
      for (var li = lightJobs.length - 1; li >= 0; li--) {
        var job = lightJobs[li];
        job.t += dt;
        var kk = 1 - job.t / job.dur;
        job.l.intensity = kk > 0 ? job.i * kk : 0;
        if (kk <= 0) { lightJobs.splice(li, 1); }
        any = true;
      }
      if (flash) {
        if (flashT < flashDur) { flashT += dt; var fa = Math.max(0, 1 - flashT / flashDur) * flashAlpha; flash.style.opacity = fa.toFixed(3); any = true; }
        else if (flash.style.opacity !== "0") { flash.style.opacity = "0"; }
      }
      if (shake.t < shake.dur) { shake.t += dt; any = true; }
      return any;
    };
    fx.clear = function () {
      px.clear(); sparks.clear();
      fx.side = 0;
      arcs.forEach(function (a) { a.on = false; vis(a.mesh, false); });
      rings.forEach(function (r) { r.on = false; vis(r.mesh, false); r.mesh.material.uniforms.uOn.value = 0; });
      columns.forEach(function (c) { c.on = false; vis(c.group, false); });
      projectiles.forEach(function (pr) { pr.on = false; vis(pr.mesh, false); });
      domeState.want = 0; domeState.on = 0; vis(dome, false);
      marks.count = 0; marks.instanceMatrix.needsUpdate = true; vis(marks, false);
      vis(cross, false); vis(sight, false);
      fx.showSide(0);
      lightJobs.length = 0;
      spare.forEach(function (l) { l.intensity = 0; });
      shake.t = shake.dur = 0;
      if (flash) { flash.style.opacity = "0"; }
      flashT = flashDur = 0;
    };
    fx.setPointScale = function (heightPx) { px.material.uniforms.uScale.value = heightPx * 0.7; sparks.material.uniforms.uScale.value = heightPx * 0.7; };
    return fx;
  };
})();
