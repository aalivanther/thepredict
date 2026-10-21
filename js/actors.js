/* ThePredict v4 · actores propios de bloques: el boss «El Augur» (yelmo con cuernos y visor cian, hombreras de bronce,
   peto de hierro con núcleo de ascua, capa a jirones, alabarda) y el jugador (humanoide de vóxeles con skin propia,
   arco, espada, escudo y poción). Rig procedural: ciclo de andar por velocidad y acciones por fase con poses clave. */
(function () {
  "use strict";
  var TP = window.TP;
  var THREE = window.THREE;
  if (!THREE) { return; }
  var P = TP.pixel;
  var A = TP.actors = {};

  var ZERO = { aLx: 0, aLy: 0, aLz: 0, aRx: 0, aRy: 0, aRz: 0, lean: 0, twist: 0, headX: 0, headY: 0, crouch: 0, legs: 1, lLx: 0, lRx: 0 };
  var KEYS = Object.keys(ZERO);
  function pose(p) { var out = {}; KEYS.forEach(function (k) { out[k] = p[k] !== undefined ? p[k] : ZERO[k]; }); return out; }
  function lerpPose(a, b, t, out) { KEYS.forEach(function (k) { out[k] = a[k] + (b[k] - a[k]) * t; }); return out; }
  /* sample a keyframe list [[phase, pose], ...] with smooth easing between keys */
  function sample(keys, phase, out) {
    if (phase <= keys[0][0]) { return lerpPose(keys[0][1], keys[0][1], 0, out); }
    for (var i = 1; i < keys.length; i++) {
      if (phase <= keys[i][0]) {
        var t = (phase - keys[i - 1][0]) / Math.max(1e-6, keys[i][0] - keys[i - 1][0]);
        return lerpPose(keys[i - 1][1], keys[i][1], TP.smooth(t), out);
      }
    }
    return lerpPose(keys[keys.length - 1][1], keys[keys.length - 1][1], 0, out);
  }

  /* ---------------------------------------------------------------------------------------- pose tables */
  var PLAYER_ACTIONS = {
    idle: [[0, pose({})], [1, pose({})]],
    draw: [[0, pose({ aLx: -0.8, aRx: -0.6 })], [0.3, pose({ aLx: -1.55, aLy: 0.1, aRx: -1.35, aRz: 0.35, twist: 0.45, headY: -0.3 })], [1, pose({ aLx: -1.55, aLy: 0.1, aRx: -1.35, aRz: 0.35, twist: 0.45, headY: -0.3 })]],
    release: [[0, pose({ aLx: -1.55, aRx: -1.35, aRz: 0.35, twist: 0.45 })], [0.12, pose({ aLx: -1.5, aRx: -1.0, aRz: 0.8, twist: 0.3 })], [0.6, pose({ aLx: -1.2, aRx: -0.5 })], [1, pose({})]],
    swing: [[0, pose({ aRx: -2.6, aRz: -0.3, lean: -0.12, twist: -0.35 })], [0.35, pose({ aRx: -2.7, aRz: -0.3, lean: -0.15, twist: -0.4 })], [0.5, pose({ aRx: -0.2, aRz: 0.1, lean: 0.28, twist: 0.35, crouch: -0.06 })], [0.75, pose({ aRx: 0.3, lean: 0.2, twist: 0.3 })], [1, pose({})]],
    heavy: [[0, pose({ aRx: -2.4, aRz: -0.5, aLx: -1.2, lean: -0.2 })], [0.72, pose({ aRx: -3.0, aRz: -0.6, aLx: -1.5, lean: -0.35, crouch: -0.08, headX: -0.2 })], [0.8, pose({ aRx: 0.5, aRz: 0.1, aLx: 0.3, lean: 0.45, crouch: -0.2, headX: 0.3 })], [1, pose({ aRx: 0.3, lean: 0.3, crouch: -0.1 })]],
    block: [[0, pose({ aLx: -0.6 })], [0.2, pose({ aLx: -1.25, aLz: 0.45, lean: 0.12, crouch: -0.06, twist: 0.2 })], [1, pose({ aLx: -1.25, aLz: 0.45, lean: 0.12, crouch: -0.06, twist: 0.2 })]],
    drink: [[0, pose({ aRx: -1.0 })], [0.25, pose({ aRx: -2.3, aRz: -0.55, headX: -0.35 })], [0.8, pose({ aRx: -2.35, aRz: -0.55, headX: -0.4 })], [1, pose({})]],
    stagger: [[0, pose({ lean: -0.3, aLx: -0.5, aRx: -0.5, aLz: 0.7, aRz: -0.7, headX: -0.25 })], [0.6, pose({ lean: -0.4, aLx: -0.6, aRx: -0.6, aLz: 0.8, aRz: -0.8, headX: -0.3 })], [1, pose({})]],
    roll: [[0, pose({ crouch: -0.25, lean: 0.5 })], [0.5, pose({ crouch: -0.5, lean: 1.2, aLx: -1.0, aRx: -1.0 })], [1, pose({})]],
    hurt: [[0, pose({ lean: -0.2, headX: -0.2, aLz: 0.4, aRz: -0.4 })], [1, pose({})]],
    point: [[0, pose({ aRx: -1.5 })], [1, pose({ aRx: -1.5 })]]
  };
  var BOSS_ACTIONS = {
    idle: [[0, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })], [1, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })]],
    windup: [[0, pose({ aRx: -2.4, aRz: -0.3, aLx: -1.8, aLz: 0.3, lean: -0.2 })], [0.68, pose({ aRx: -3.0, aRz: -0.35, aLx: -2.3, aLz: 0.3, lean: -0.32, crouch: -0.12, headX: -0.25 })], [0.78, pose({ aRx: 0.7, aRz: -0.1, aLx: 0.3, aLz: 0.3, lean: 0.5, crouch: -0.42, headX: 0.35 })], [0.92, pose({ aRx: 0.6, aLx: 0.3, lean: 0.4, crouch: -0.32 })], [1, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })]],
    sweep: [[0, pose({ aRx: -1.3, aRz: -1.1, aLx: -0.5, aLz: 0.5, twist: -0.75, lean: -0.05 })], [0.4, pose({ aRx: -1.4, aRz: -1.2, aLx: -0.5, aLz: 0.5, twist: -0.85, lean: -0.08 })], [0.55, pose({ aRx: -1.4, aRz: 1.1, aLx: -0.3, aLz: 0.3, twist: 0.85, lean: 0.15, crouch: -0.06 })], [0.75, pose({ aRx: -1.1, aRz: 1.0, twist: 0.7, lean: 0.1 })], [1, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })]],
    guard: [[0, pose({ aRx: -0.8, aRz: 0.3, aLx: -0.8 })], [0.18, pose({ aRx: -1.25, aRz: 1.15, aLx: -1.2, aLz: -0.5, lean: -0.08, crouch: -0.12, twist: 0.25 })], [1, pose({ aRx: -1.25, aRz: 1.15, aLx: -1.2, aLz: -0.5, lean: -0.08, crouch: -0.12, twist: 0.25 })]],
    cast: [[0, pose({ aLx: -1.2, aLz: 0.2, aRx: -0.3 })], [0.45, pose({ aLx: -2.2, aLz: 0.15, twist: 0.35, lean: -0.1, aRx: -0.4, aRz: -0.2 })], [0.58, pose({ aLx: -1.55, aLz: 0.1, twist: -0.1, lean: 0.18, aRx: -0.2 })], [1, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })]],
    leap: [[0, pose({ crouch: -0.45, lean: 0.35, aLx: 0.6, aRx: 0.4 })], [0.18, pose({ crouch: 0.05, lean: -0.15, aLx: -1.4, aLz: 0.6, aRx: -1.6, aRz: -0.3, legs: 0 })], [0.75, pose({ crouch: 0, lean: 0.1, aLx: -1.0, aLz: 0.6, aRx: -2.4, aRz: -0.3, legs: 0 })], [0.86, pose({ crouch: -0.4, lean: 0.45, aLx: 0.2, aRx: 0.5, headX: 0.2 })], [1, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })]],
    dash: [[0, pose({ lean: 0.4, aLx: 0.7, aRx: 0.5, aRz: -0.2, headX: -0.2 })], [1, pose({ lean: 0.4, aLx: 0.7, aRx: 0.5, aRz: -0.2, headX: -0.2 })]],
    stagger: [[0, pose({ lean: -0.4, aLx: -0.4, aLz: 0.9, aRx: -0.3, aRz: -0.9, headX: -0.35 })], [0.5, pose({ lean: -0.5, aLx: -0.5, aLz: 1.0, aRx: -0.4, aRz: -1.0, headX: -0.4 })], [1, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })]],
    awaken: [[0, pose({ aRx: -0.4, aRz: -0.1, aLx: -0.5, aLz: 0.4, lean: 0.05 })], [0.35, pose({ crouch: -0.35, lean: 0.4, headX: 0.35, aLx: 0.3, aRx: 0.2 })], [0.6, pose({ crouch: 0.0, lean: -0.3, headX: -0.4, aLx: -2.3, aLz: 0.8, aRx: -2.2, aRz: -0.7 })], [0.86, pose({ crouch: 0, lean: -0.25, headX: -0.35, aLx: -2.2, aLz: 0.8, aRx: -2.1, aRz: -0.7 })], [1, pose({ aRx: -0.1, aRz: -0.08, aLx: -0.15, aLz: 0.25 })]],
    point: [[0, pose({ aLx: -1.2 })], [0.3, pose({ aLx: -1.7, aLz: 0.1, twist: 0.2, aRx: -0.1, aRz: -0.08 })], [1, pose({ aLx: -1.7, aLz: 0.1, twist: 0.2, aRx: -0.1, aRz: -0.08 })]],
    hold: [[0, pose({ aRx: -0.4, aRz: -0.1, aLx: -0.5, aLz: 0.4, lean: 0.05 })], [1, pose({ aRx: -0.4, aRz: -0.1, aLx: -0.5, aLz: 0.4, lean: 0.05 })]],
    stuck: [[0, pose({ lean: 0.15, aLx: 0.3, aRx: 0.2 })], [0.5, pose({ lean: 0.2, aLx: 0.5, aRx: 0.3 })], [1, pose({ lean: 0.15, aLx: 0.3, aRx: 0.2 })]]
  };

  /* ---------------------------------------------------------------------------------------- the rig */
  function Rig(opts) {
    this.root = new THREE.Group();
    this.body = new THREE.Group();
    this.root.add(this.body);
    this.pos = new THREE.Vector3();
    this.prev = new THREE.Vector3();
    this.vel = new THREE.Vector3();
    this.yaw = 0;
    this.air = 0;
    this.scale = opts.scale || 1;
    this.hip = opts.hip;
    this.strideK = opts.stride || 1;
    this.actions = opts.actions;
    this.cur = pose(this.actions.idle[0][1]);
    this.target = pose({});
    this.action = "idle";
    this.phase = 0;
    this.duration = 1;
    this.held = null;
    this.cycle = 0;
    this.speed = 0;
    this.lookTarget = null;
    this.lookK = 0;
    this.time = 0;
    this.pivots = {};
    this.bob = 0;
    this.faceVel = true;
    this.hp = 1;
  }
  Rig.prototype.pivot = function (name, x, y, z, parent) {
    var g = new THREE.Group();
    g.position.set(x, y, z);
    (parent || this.body).add(g);
    this.pivots[name] = g;
    return g;
  };
  Rig.prototype.act = function (name, duration) {
    if (!this.actions[name]) { return; }
    this.action = name;
    this.duration = duration || 1;
    this.phase = 0;
    this.held = null;
  };
  Rig.prototype.hold = function (name) { this.held = this.actions[name] ? name : null; if (this.held) { this.action = name; this.phase = 0; } };
  Rig.prototype.release = function () { this.held = null; this.action = "idle"; this.phase = 0; };
  Rig.prototype.setPos = function (x, z, y) { this.pos.set(x, y || 0, z); };
  Rig.prototype.look = function (target, k) { this.lookTarget = target; this.lookK = k === undefined ? 1 : k; };
  Rig.prototype.update = function (dt, t) {
    this.time = t;
    /* velocity from the position the script wrote */
    if (dt > 0) {
      this.vel.subVectors(this.pos, this.prev).multiplyScalar(1 / dt);
      var sp = Math.sqrt(this.vel.x * this.vel.x + this.vel.z * this.vel.z);
      this.speed = TP.damp(this.speed, sp, 12, dt);
      if (sp > 0.4 && this.faceVel) { this.yaw = dampAngle(this.yaw, Math.atan2(this.vel.x, this.vel.z), 14, dt); }
    }
    this.prev.copy(this.pos);
    this.root.position.set(this.pos.x, this.pos.y + this.air, this.pos.z);
    this.root.rotation.y = this.yaw;
    /* action phase */
    if (this.held) { this.phase = Math.min(1, this.phase + dt / 0.35); }
    else if (this.action !== "idle") {
      this.phase += dt / this.duration;
      if (this.phase >= 1) { this.action = "idle"; this.phase = 0; }
    }
    sample(this.actions[this.action], this.phase, this.target);
    var lambda = this.action === "idle" ? 8 : 22;
    var k = 1 - Math.exp(-lambda * dt);
    var c = this.cur, tg = this.target;
    for (var i = 0; i < KEYS.length; i++) { var key = KEYS[i]; c[key] += (tg[key] - c[key]) * k; }
    /* walk cycle */
    var stride = TP.clamp(this.speed / 4.5, 0, 1);
    this.cycle += dt * (2.2 + this.speed * 1.9) * this.strideK;
    var swing = Math.sin(this.cycle) * 0.95 * stride * c.legs;
    var p = this.pivots;
    p.legL.rotation.x = swing + c.lLx;
    p.legR.rotation.x = -swing + c.lRx;
    var armSwing = Math.sin(this.cycle) * 0.6 * stride * c.legs;
    var breathe = Math.sin(t * 1.6) * 0.03;
    p.armL.rotation.set(c.aLx - armSwing, c.aLy, c.aLz + 0.04 + breathe);
    p.armR.rotation.set(c.aRx + armSwing, c.aRy, c.aRz - 0.04 - breathe);
    p.torso.rotation.set(c.lean + Math.sin(t * 1.6) * 0.012, c.twist, 0);
    var hy = 0, hx = 0;
    if (this.lookTarget) {
      var dx = this.lookTarget.x - this.pos.x, dz = this.lookTarget.z - this.pos.z;
      var want = Math.atan2(dx, dz) - this.yaw;
      want = Math.atan2(Math.sin(want), Math.cos(want));
      hy = TP.clamp(want, -1.1, 1.1) * this.lookK;
      var dy = (this.lookTarget.y || 0) - (this.pos.y + this.hip);
      hx = TP.clamp(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 0.6, -0.5, 0.5) * this.lookK;
    }
    p.head.rotation.set(c.headX + hx, c.headY + hy - c.twist, 0);
    this.bob = Math.abs(Math.sin(this.cycle)) * 0.06 * stride * c.legs;
    this.body.position.y = c.crouch + this.bob;
    this.body.rotation.y = 0;
    if (this.extra) { this.extra(dt, t, c, stride); }
  };
  function dampAngle(a, b, lambda, dt) {
    var d = Math.atan2(Math.sin(b - a), Math.cos(b - a));
    return a + d * (1 - Math.exp(-lambda * dt));
  }
  A.dampAngle = dampAngle;

  function material(atlas, opts) {
    opts = opts || {};
    var m = new THREE.MeshStandardMaterial({ map: atlas.texture, roughness: opts.roughness || 0.78, metalness: opts.metalness || 0.12, envMapIntensity: 0.35 });
    if (opts.emissive) { m.emissiveMap = atlas.emissive; m.emissive = new THREE.Color(0xffffff); m.emissiveIntensity = opts.emissive; }
    if (opts.alpha) { m.alphaTest = 0.5; m.side = THREE.DoubleSide; }
    return m;
  }
  function mesh(geo, mat, parent) {
    var m = new THREE.Mesh(geo, mat);
    m.castShadow = true;
    m.receiveShadow = true;
    parent.add(m);
    return m;
  }

  /* ---------------------------------------------------------------------------------------- El Augur */
  A.boss = function (opts) {
    opts = opts || {};
    var atlas = P.atlas(["aug_iron", "aug_iron_b", "aug_bronze", "aug_pauldron", "aug_chest", "aug_back", "aug_face", "aug_helm", "aug_horn", "aug_arm", "aug_fist", "aug_leg", "aug_boot", "aug_shaft", "aug_blade", "aug_cloth", "aug_cloth_end"]);
    var mat = material(atlas, { emissive: 1.0, metalness: 0.25, roughness: 0.72 });
    var clothMat = material(atlas, { alpha: true, roughness: 0.95, metalness: 0 });
    var rig = new Rig({ hip: 1.45, actions: BOSS_ACTIONS, stride: 0.75 });
    rig.kind = "boss";
    var B = rig.body;
    /* legs */
    ["legL", "legR"].forEach(function (name, i) {
      var s = i === 0 ? -1 : 1;
      var pv = rig.pivot(name, 0.42 * s, 1.45, 0);
      mesh(P.merge([
        P.box(atlas, "aug_leg", 0.5, 0.75, 0.55, 0, -0.38, 0),
        P.box(atlas, "aug_iron_b", 0.46, 0.68, 0.5, 0, -1.06, 0.02),
        P.box(atlas, "aug_boot", 0.56, 0.26, 0.72, 0, -1.32, 0.1)
      ]), mat, pv);
    });
    /* torso */
    var torso = rig.pivot("torso", 0, 1.45, 0);
    mesh(P.merge([
      P.box(atlas, "aug_iron_b", 1.2, 0.32, 0.78, 0, 0.12, 0),
      P.box(atlas, { pz: "aug_chest", nz: "aug_back", all: "aug_iron_b" }, 1.42, 1.02, 0.88, 0, 0.78, 0),
      P.box(atlas, "aug_bronze", 0.92, 0.22, 0.72, 0, 1.38, 0),
      P.box(atlas, "aug_pauldron", 0.86, 0.5, 0.98, -0.98, 1.1, 0),
      P.box(atlas, "aug_pauldron", 0.86, 0.5, 0.98, 0.98, 1.1, 0),
      P.box(atlas, "aug_iron", 0.3, 0.3, 0.3, -1.0, 1.4, 0),
      P.box(atlas, "aug_iron", 0.3, 0.3, 0.3, 1.0, 1.4, 0)
    ]), mat, torso);
    /* head */
    var head = rig.pivot("head", 0, 2.95, 0.02, torso);
    head.position.set(0, 1.5, 0.02);
    mesh(P.merge([
      P.box(atlas, { pz: "aug_face", all: "aug_helm" }, 0.86, 0.86, 0.86, 0, 0.44, 0),
      P.box(atlas, "aug_horn", 0.16, 0.6, 0.16, -0.5, 0.95, 0).rotateZ(0),
      P.box(atlas, "aug_horn", 0.16, 0.6, 0.16, 0.5, 0.95, 0),
      P.box(atlas, "aug_horn", 0.14, 0.3, 0.14, -0.62, 1.3, 0),
      P.box(atlas, "aug_horn", 0.14, 0.3, 0.14, 0.62, 1.3, 0),
      P.box(atlas, "aug_bronze", 0.9, 0.12, 0.9, 0, 0.9, 0)
    ]), mat, head);
    /* arms, with the halberd in the right fist */
    ["armL", "armR"].forEach(function (name, i) {
      var s = i === 0 ? -1 : 1;
      var pv = rig.pivot(name, 0.98 * s, 2.45, 0, torso);
      pv.position.set(0.98 * s, 1.0, 0);
      var m = mesh(P.merge([
        P.box(atlas, "aug_arm", 0.5, 0.78, 0.5, 0, -0.42, 0),
        P.box(atlas, "aug_iron_b", 0.54, 0.7, 0.54, 0, -1.12, 0.02),
        P.box(atlas, "aug_fist", 0.58, 0.46, 0.58, 0, -1.66, 0.06)
      ]), mat, pv);
      if (s === 1) {
        var weapon = new THREE.Group();
        weapon.position.set(0, -1.66, 0.06);
        weapon.rotation.x = -0.1;
        pv.add(weapon);
        mesh(P.merge([
          P.box(atlas, "aug_shaft", 0.13, 3.3, 0.13, 0, 0.3, 0),
          P.box(atlas, "aug_blade", 0.62, 0.95, 0.1, 0.36, 1.55, 0),
          P.box(atlas, "aug_blade", 0.26, 0.4, 0.1, -0.18, 1.7, 0),
          P.box(atlas, "aug_iron", 0.2, 0.28, 0.2, 0, 2.08, 0),
          P.box(atlas, "aug_iron", 0.22, 0.22, 0.22, 0, -1.3, 0)
        ]), mat, weapon);
        rig.weapon = weapon;
      }
      void m;
    });
    /* cape: three chained tatters */
    var cape = [];
    var parent = torso, y = 1.3;
    for (var k = 0; k < 3; k++) {
      var seg = new THREE.Group();
      seg.position.set(0, k === 0 ? y : -0.58, k === 0 ? -0.5 : 0);
      parent.add(seg);
      var g = P.box(atlas, k === 2 ? "aug_cloth_end" : "aug_cloth", 1.3 - k * 0.1, 0.62, 0.06, 0, -0.3, 0);
      var cm = mesh(g, clothMat, seg);
      cm.castShadow = k < 2;
      cape.push(seg);
      parent = seg;
    }
    rig.cape = cape;
    rig.material = mat;
    rig.setEyes = function (k) { mat.emissiveIntensity = TP.clamp(k, 0, 4); };
    rig.extra = function (dt, t, c, stride) {
      /* the cape trails the motion and sways */
      var back = -(rig.vel.x * Math.sin(rig.yaw) + rig.vel.z * Math.cos(rig.yaw));
      var lift = TP.clamp(-back * 0.12, -0.2, 1.1);
      for (var i = 0; i < cape.length; i++) {
        var want = 0.18 + lift * (1 - i * 0.2) + Math.sin(t * 2.3 + i * 0.9) * 0.05 + (i === 0 ? -c.lean * 0.6 : 0.1);
        cape[i].rotation.x = TP.damp(cape[i].rotation.x, want, 6, dt);
      }
      void stride;
    };
    rig.root.scale.setScalar(opts.scale || 1);
    rig.eyeWorld = function (out) { return head.getWorldPosition(out || new THREE.Vector3()).add(new THREE.Vector3(0, 0.45, 0)); };
    rig.handWorld = function (out) { return rig.pivots.armL.getWorldPosition(out || new THREE.Vector3()).add(new THREE.Vector3(0, -1.5, 0)); };
    rig.bladeWorld = function (out) { out = out || new THREE.Vector3(); out.set(0.36, 1.55, 0); return rig.weapon.localToWorld(out); };
    rig.name = opts.name || "augur";
    return rig;
  };

  /* ---------------------------------------------------------------------------------------- the player */
  A.player = function (opts) {
    opts = opts || {};
    var atlas = P.atlas(["pl_face", "pl_head", "pl_head_top", "pl_torso", "pl_torso_back", "pl_torso_side", "pl_arm", "pl_leg", "pl_sole", "bow", "bow_string", "sword", "sword_hilt", "shield", "shield_back", "potion", "apple", "aug_iron"]);
    var mat = material(atlas, { roughness: 0.86, metalness: 0.02 });
    var rig = new Rig({ hip: 0.75, actions: PLAYER_ACTIONS, stride: 1.25 });
    rig.kind = "player";
    ["legL", "legR"].forEach(function (name, i) {
      var s = i === 0 ? -1 : 1;
      var pv = rig.pivot(name, 0.125 * s, 0.75, 0);
      mesh(P.box(atlas, { ny: "pl_sole", all: "pl_leg" }, 0.25, 0.75, 0.25, 0, -0.375, 0), mat, pv);
    });
    var torso = rig.pivot("torso", 0, 0.75, 0);
    mesh(P.box(atlas, { pz: "pl_torso", nz: "pl_torso_back", px: "pl_torso_side", nx: "pl_torso_side", all: "pl_torso_side" }, 0.5, 0.75, 0.26, 0, 0.375, 0), mat, torso);
    var head = rig.pivot("head", 0, 0.75, 0, torso);
    mesh(P.box(atlas, { pz: "pl_face", py: "pl_head_top", all: "pl_head" }, 0.5, 0.5, 0.5, 0, 0.25, 0), mat, head);
    /* hood brim */
    mesh(P.box(atlas, "pl_head_top", 0.56, 0.1, 0.56, 0, 0.46, 0), mat, head);
    var props = {};
    ["armL", "armR"].forEach(function (name, i) {
      var s = i === 0 ? -1 : 1;
      var pv = rig.pivot(name, 0.375 * s, 0.7, 0, torso);
      mesh(P.box(atlas, "pl_arm", 0.25, 0.75, 0.25, 0, -0.33, 0), mat, pv);
      var hand = new THREE.Group();
      hand.position.set(0, -0.68, 0);
      pv.add(hand);
      if (s === 1) {
        /* main hand: sword, potion, apple */
        var sword = new THREE.Group();
        mesh(P.merge([
          P.box(atlas, "sword", 0.07, 0.8, 0.16, 0, 0.62, 0.08),
          P.box(atlas, "sword_hilt", 0.26, 0.07, 0.08, 0, 0.2, 0.08),
          P.box(atlas, "sword_hilt", 0.08, 0.2, 0.08, 0, 0.05, 0.08)
        ]), mat, sword);
        sword.rotation.x = -Math.PI / 2;
        hand.add(sword);
        props.sword = sword;
        var potion = new THREE.Group();
        mesh(P.box(atlas, "potion", 0.2, 0.26, 0.2, 0, 0.0, 0.16), mat, potion);
        hand.add(potion);
        props.potion = potion;
        var apple = new THREE.Group();
        mesh(P.box(atlas, "apple", 0.2, 0.2, 0.2, 0, 0, 0.16), mat, apple);
        hand.add(apple);
        props.apple = apple;
      } else {
        /* off hand: bow or shield */
        var bow = new THREE.Group();
        mesh(P.merge([
          P.box(atlas, "bow", 0.06, 0.34, 0.1, 0, 0.42, 0.1).rotateX(0),
          P.box(atlas, "bow", 0.06, 0.34, 0.1, 0, -0.42, 0.1),
          P.box(atlas, "bow", 0.06, 0.5, 0.1, 0, 0, 0.16),
          P.box(atlas, "bow", 0.06, 0.2, 0.1, 0, 0.55, 0.02),
          P.box(atlas, "bow", 0.06, 0.2, 0.1, 0, -0.55, 0.02),
          P.box(atlas, "bow_string", 0.02, 1.2, 0.02, 0, 0, -0.04)
        ]), mat, bow);
        bow.rotation.x = -Math.PI / 2;
        bow.rotation.z = 0;
        hand.add(bow);
        props.bow = bow;
        var shield = new THREE.Group();
        mesh(P.box(atlas, { pz: "shield", nz: "shield_back", all: "shield_back" }, 0.56, 0.68, 0.08, 0.08, 0.1, 0.22), mat, shield);
        hand.add(shield);
        props.shield = shield;
      }
    });
    rig.props = props;
    rig.equip = function (main, off) {
      props.sword.visible = main === "sword";
      props.potion.visible = main === "potion";
      props.apple.visible = main === "apple";
      props.bow.visible = off === "bow";
      props.shield.visible = off === "shield";
      rig.main = main; rig.off = off;
      var hud = TP.hud;
      if (hud && hud.slotByName && opts.hud !== false) { hud.slotByName(main === "sword" ? "sword" : main === "potion" ? "potion" : main === "apple" ? "apple" : off === "bow" ? "bow" : "sword"); }
    };
    rig.equip("sword", "shield");
    rig.material = mat;
    rig.root.scale.setScalar(opts.scale || 1);
    rig.eyeWorld = function (out) { return head.getWorldPosition(out || new THREE.Vector3()).add(new THREE.Vector3(0, 0.3, 0)); };
    rig.handWorld = function (out) { return rig.pivots.armR.getWorldPosition(out || new THREE.Vector3()).add(new THREE.Vector3(0, -0.7, 0)); };
    rig.name = opts.name || "player";
    return rig;
  };
})();
