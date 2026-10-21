/* ThePredict v4 · la Cripta del Augur: arena propia de bloques con texturas pixel propias. Suelo de baldosas con la cruz
   pulida, muros de ladrillo con pilastras y arcos ciegos, torres en las esquinas, braseros de fuego azul, antorchas,
   una puerta iluminada, haces de luz y polvo. Bloques en InstancedMesh por textura: pocos draw calls. */
(function () {
  "use strict";
  var TP = window.TP;
  var THREE = window.THREE;
  if (!THREE) { return; }
  var P = TP.pixel;
  var W = TP.world = {};

  W.HALF = 14;             /* floor spans -14..14 */
  W.WALL = 15;             /* wall blocks sit at |x| = 15 or |z| = 15 */
  W.HEIGHT = 10;
  W.BRAZIERS = [[-10, -10], [10, -10], [-10, 10], [10, 10]];
  W.TORCHES = [[-6, -14.5, 0], [6, -14.5, 0], [-6, 14.5, Math.PI], [6, 14.5, Math.PI], [-14.5, -6, Math.PI / 2], [14.5, 6, -Math.PI / 2]];
  W.PILLARS = [[-10, -10, 1.6], [10, -10, 1.6], [-10, 10, 1.6], [10, 10, 1.6]];
  W.DOOR = { x: 0, z: -15 };

  W.build = function (opts) {
    opts = opts || {};
    var tier = opts.tier || "high";
    var high = tier === "high";
    var rnd = TP.rng(1337);
    var group = new THREE.Group();
    var H = W.HALF, WL = W.WALL, HT = W.HEIGHT;

    /* ------------------------------------------------------------------ block lists per tile */
    var lists = {};
    function put(tile, x, y, z, tint, rot) {
      (lists[tile] = lists[tile] || []).push([x, y, z, tint === undefined ? 1 : tint, rot || 0]);
    }
    function pick(list) { return list[Math.floor(rnd() * list.length)]; }

    /* floor: y = -1 (top face at 0) */
    for (var x = -H; x <= H; x++) {
      for (var z = -H; z <= H; z++) {
        var m = Math.max(Math.abs(x), Math.abs(z));
        var tile;
        if (m === H) { tile = "cobble"; }
        else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) { tile = (x === 0 && z === 0) ? "tile_edge" : "tile_polished"; }
        else if (m === 8) { tile = "tile_polished"; }
        else if (Math.abs(x) === 8 && Math.abs(z) === 8) { tile = "tile_edge"; }
        else { tile = rnd() < 0.14 ? "tile_b" : "tile"; }
        var shadeK = 0.9 + rnd() * 0.2;
        put(tile, x, -1, z, shadeK);
      }
    }
    /* door opening on the -z wall: x -1..1, y 0..4; behind it a glowing passage */
    function isDoor(x, y, z) { return z === -WL && Math.abs(x) <= 1 && y <= 4; }
    /* walls */
    function wallTile(y) {
      var r = rnd();
      if (y >= HT - 1) { return "brick_top"; }
      if (y <= 1 && r < 0.3) { return "brick_mossy"; }
      if (r < 0.62) { return "brick"; }
      if (r < 0.82) { return "brick_b"; }
      if (r < 0.92) { return "brick_cracked"; }
      return "brick_mossy";
    }
    var pilasterAt = [-12, -6, 0, 6, 12];
    function archPanel(u, y) {
      /* a dark blind arch between pilasters: 3 wide, 4 tall, rounded top */
      var mid = -9, found = false;
      [-9, -3, 3, 9].forEach(function (c) { if (Math.abs(u - c) <= 1) { mid = c; found = true; } });
      if (!found) { return false; }
      if (y < 3 || y > 7) { return false; }
      if (y === 7 && Math.abs(u - mid) === 1) { return false; }
      return true;
    }
    for (var s = -WL; s <= WL; s++) {
      for (var y = 0; y < HT; y++) {
        var tint = 0.78 + 0.3 * Math.min(1, y / 6) + (rnd() - 0.5) * 0.12;
        /* four walls; the wall blocks only need their inner faces, but cubes are cheap */
        [[s, y, -WL], [s, y, WL], [-WL, y, s], [WL, y, s]].forEach(function (b, k) {
          if (isDoor(b[0], b[1], b[2])) { return; }
          var u = k < 2 ? b[0] : b[2];
          var isArch = archPanel(u, y);
          var t = isArch ? "brick_dark" : wallTile(y);
          if (isArch && y === 5 && (u === -3 || u === 9 || u === 3) && k !== 1) { t = "lamp_cyan"; }
          put(t, b[0], b[1], b[2], tint);
        });
      }
    }
    /* pilasters: one block proud, height 8, on all four walls; plus corner towers 2×2 to full height */
    pilasterAt.forEach(function (u) {
      for (var y = 0; y < 8; y++) {
        var t = y === 7 ? "iron" : "brick_b";
        var tint = 0.82 + 0.25 * Math.min(1, y / 6);
        if (!(Math.abs(u) <= 1)) { put(t, u, y, -H, tint); }
        put(t, u, y, H, tint);
        put(t, -H, y, u, tint);
        put(t, H, y, u, tint);
      }
    });
    [[-H, -H], [H, -H], [-H, H], [H, H]].forEach(function (c) {
      for (var y = 0; y < HT; y++) {
        var tint = 0.8 + 0.25 * Math.min(1, y / 6);
        put(y === HT - 1 ? "iron" : wallTile(y), c[0], y, c[1], tint);
        put(y === HT - 1 ? "iron" : wallTile(y), c[0] + (c[0] < 0 ? 1 : -1), y, c[1], tint);
        put(y === HT - 1 ? "iron" : wallTile(y), c[0], y, c[1] + (c[1] < 0 ? 1 : -1), tint);
      }
    });
    /* torches: a warm lamp block set into the wall at height 4 by the pilasters */
    W.TORCHES.forEach(function (t) {
      var x = Math.round(t[0]), z = Math.round(t[1]);
      put("lamp_warm", x, 4, z, 1);
    });
    /* braziers: a 2-block pedestal, an iron cap and the cyan lamp on top */
    W.BRAZIERS.forEach(function (b) {
      put("brick_b", b[0], 0, b[1], 0.95);
      put("brick_dark", b[0], 1, b[1], 0.95);
      put("iron", b[0], 2, b[1], 1);
      put("lamp_cyan", b[0], 3, b[1], 1);
    });
    /* the glowing passage behind the door, and the lintel */
    for (var dy = 0; dy <= 4; dy++) { for (var dx = -1; dx <= 1; dx++) { put("door_glow", dx, dy, -WL - 1, 1); } }
    put("iron", -2, 5, -WL, 1); put("iron", 2, 5, -WL, 1);
    for (var lx = -1; lx <= 1; lx++) { put("iron", lx, 5, -WL, 1); }
    /* a broken pillar near the centre-left: the native AI bumps into it in the split demo */
    put("brick_cracked", -3, 0, 1, 0.95); put("brick_dark", -3, 1, 1, 0.9); put("cobble", -4, 0, 1, 0.9, 1);
    /* rubble and low blocks near the walls for depth */
    for (var i = 0; i < (high ? 26 : 12); i++) {
      var rx = (rnd() < 0.5 ? -1 : 1) * (9 + Math.floor(rnd() * 5)), rz = (rnd() < 0.5 ? -1 : 1) * (9 + Math.floor(rnd() * 5));
      if (Math.abs(rx) === 10 && Math.abs(rz) === 10) { continue; }
      if (rnd() < 0.5) { rx = Math.round((rnd() - 0.5) * 26); } else { rz = Math.round((rnd() - 0.5) * 26); }
      if (Math.abs(rx) < 8 && Math.abs(rz) < 8) { continue; }
      put(rnd() < 0.5 ? "cobble" : "brick_cracked", rx, 0, rz, 0.85 + rnd() * 0.2, Math.floor(rnd() * 4));
    }

    /* ------------------------------------------------------------------ instanced meshes */
    var cube = new THREE.BoxGeometry(1, 1, 1);
    var mtx = new THREE.Matrix4(), col = new THREE.Color(), quat = new THREE.Quaternion(), euler = new THREE.Euler(), pos = new THREE.Vector3(), one = new THREE.Vector3(1, 1, 1);
    var meshes = {};
    Object.keys(lists).forEach(function (tile) {
      var list = lists[tile];
      var emissive = P.emissiveTexture(tile);
      var mat = new THREE.MeshStandardMaterial({ map: P.texture(tile), roughness: 0.94, metalness: tile === "iron" ? 0.35 : 0.0, envMapIntensity: 0.25 });
      if (emissive) { mat.emissiveMap = emissive; mat.emissive = new THREE.Color(tile === "lamp_cyan" ? 0x9af7ff : 0xffe0b0); mat.emissiveIntensity = tile === "lamp_cyan" ? 1.5 : tile === "lamp_warm" ? 1.6 : 1.2; }
      var mesh = new THREE.InstancedMesh(cube, mat, list.length);
      list.forEach(function (b, k) {
        euler.set(0, b[4] * Math.PI / 2, 0);
        quat.setFromEuler(euler);
        pos.set(b[0], b[1] + 0.5, b[2]);
        mtx.compose(pos, quat, one);
        mesh.setMatrixAt(k, mtx);
        col.setScalar(b[3]);
        mesh.setColorAt(k, col);
      });
      mesh.instanceMatrix.needsUpdate = true;
      if (mesh.instanceColor) { mesh.instanceColor.needsUpdate = true; }
      mesh.castShadow = high && tile !== "lamp_cyan" && tile !== "lamp_warm";
      mesh.receiveShadow = true;
      mesh.frustumCulled = false;
      group.add(mesh);
      meshes[tile] = mesh;
    });

    /* ceiling: one dark plane with the repeating texture (the fog hides most of it) */
    var ceilTex = P.texture("brick_dark").clone();
    ceilTex.repeat.set(32, 32);
    ceilTex.needsUpdate = true;
    var ceiling = new THREE.Mesh(new THREE.PlaneGeometry(32, 32), new THREE.MeshStandardMaterial({ map: ceilTex, roughness: 1, metalness: 0, color: 0x6a665f }));
    ceiling.rotation.x = Math.PI / 2;
    ceiling.position.set(0, HT, 0);
    ceiling.receiveShadow = false;
    group.add(ceiling);

    /* ------------------------------------------------------------------ lights */
    var lights = {};
    var hemi = new THREE.HemisphereLight(0x4a4238, 0x120f0c, 0.55);
    group.add(hemi);
    var key = new THREE.DirectionalLight(0xffd9b0, 1.35);
    key.position.set(6, 16, 4);
    key.target.position.set(0, 0, 0);
    key.castShadow = high;
    key.shadow.mapSize.set(high ? 2048 : 1024, high ? 2048 : 1024);
    key.shadow.camera.left = -18; key.shadow.camera.right = 18; key.shadow.camera.top = 18; key.shadow.camera.bottom = -18;
    key.shadow.camera.near = 2; key.shadow.camera.far = 50;
    key.shadow.bias = -0.0006;
    key.shadow.normalBias = 0.04;
    key.shadow.radius = 3;
    group.add(key); group.add(key.target);
    var fill = new THREE.DirectionalLight(0x6a7a8a, 0.22);
    fill.position.set(-8, 10, -6);
    group.add(fill);
    lights.key = key; lights.fill = fill; lights.hemi = hemi;
    lights.cyan = [];
    W.BRAZIERS.forEach(function (b, i) {
      if (!high && i % 2 === 1) { return; }
      var l = new THREE.PointLight(0x5ff0ff, 22, 17, 2);
      l.position.set(b[0], 4.2, b[1]);
      group.add(l);
      lights.cyan.push(l);
    });
    lights.warm = [];
    W.TORCHES.forEach(function (t, i) {
      if (!high && i > 1) { return; }
      var l = new THREE.PointLight(0xff9a3c, 16, 13, 2);
      l.position.set(t[0] + Math.sin(t[2]) * 0.8, 4.6, t[1] + Math.cos(t[2]) * 0.8);
      group.add(l);
      lights.warm.push(l);
    });
    var door = new THREE.PointLight(0xffb060, 26, 16, 2);
    door.position.set(0, 2.5, -WL + 1.5);
    group.add(door);
    lights.door = door;
    /* two spare lights the effects borrow (hits, bolts) */
    lights.spare = [new THREE.PointLight(0xff3b4a, 0, 9, 2), new THREE.PointLight(0x5ff0ff, 0, 9, 2)];
    lights.spare.forEach(function (l) { group.add(l); });

    /* ------------------------------------------------------------------ light shafts and dust */
    var shaftMat = new THREE.ShaderMaterial({
      uniforms: { uTime: { value: 0 }, uColor: { value: new THREE.Color(0xffd9a8).multiplyScalar(0.22) }, uOn: { value: 1 } },
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, fog: false,
      vertexShader: "varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",
      fragmentShader: [
        "uniform float uTime; uniform vec3 uColor; uniform float uOn; varying vec2 vUv;",
        "float h(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }",
        "float n(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f); return mix(mix(h(i), h(i + vec2(1, 0)), f.x), mix(h(i + vec2(0, 1)), h(i + vec2(1, 1)), f.x), f.y); }",
        "void main() {",
        "  float x = abs(vUv.x - 0.5) * 2.0;",
        "  float side = 1.0 - smoothstep(0.35, 1.0, x);",
        "  float top = smoothstep(0.0, 0.25, vUv.y) * (1.0 - smoothstep(0.55, 1.0, vUv.y));",
        "  float motes = 0.75 + 0.35 * n(vec2(vUv.x * 6.0, vUv.y * 3.0 - uTime * 0.05));",
        "  float a = uOn * side * top * motes * 0.55;",
        "  gl_FragColor = vec4(uColor * a, a);",
        "}"
      ].join("\n")
    });
    var shafts = new THREE.Group();
    [[-5, -4, 0.3], [6, 3, -0.2], [0, -10, 0.1]].forEach(function (s) {
      for (var k = 0; k < 2; k++) {
        var q = new THREE.Mesh(new THREE.PlaneGeometry(2.2, 12), shaftMat);
        q.position.set(s[0], 4.5, s[1]);
        q.rotation.y = s[2] + k * Math.PI / 2;
        q.rotation.z = 0.08;
        q.frustumCulled = false;
        shafts.add(q);
      }
    });
    group.add(shafts);
    var dustCount = high ? 420 : 140;
    var dpos = new Float32Array(dustCount * 3), dseed = new Float32Array(dustCount);
    for (var d = 0; d < dustCount; d++) {
      dpos.set([(rnd() - 0.5) * 28, rnd() * 8, (rnd() - 0.5) * 28], d * 3);
      dseed[d] = rnd();
    }
    var dustGeo = new THREE.BufferGeometry();
    dustGeo.setAttribute("position", new THREE.BufferAttribute(dpos, 3));
    var dustMat = new THREE.PointsMaterial({ color: 0xffd9a8, size: 0.06, map: P.sprite("dot"), transparent: true, opacity: 0.5, depthWrite: false, blending: THREE.AdditiveBlending, sizeAttenuation: true });
    dustMat.onBeforeCompile = function (shader) {
      shader.vertexShader = shader.vertexShader.replace("gl_PointSize *= ( scale / - mvPosition.z );", "gl_PointSize *= ( scale / max( 2.0, - mvPosition.z ) ); gl_PointSize = min( gl_PointSize, 6.0 );");
    };
    dustMat.customProgramCacheKey = function () { return "tp-dust"; };
    var dust = new THREE.Points(dustGeo, dustMat);
    dust.frustumCulled = false;
    group.add(dust);
    var dustBase = dpos.slice();

    return {
      group: group, lights: lights, meshes: meshes, shafts: shaftMat,
      update: function (t, dt) {
        shaftMat.uniforms.uTime.value = t;
        var arr = dustGeo.attributes.position.array;
        for (var i = 0; i < dustCount; i++) {
          var sd = dseed[i];
          arr[i * 3] = dustBase[i * 3] + Math.sin(t * 0.17 + sd * 40) * 0.5;
          arr[i * 3 + 1] = ((dustBase[i * 3 + 1] + t * (0.04 + sd * 0.06)) % 8);
          arr[i * 3 + 2] = dustBase[i * 3 + 2] + Math.cos(t * 0.13 + sd * 30) * 0.5;
        }
        dustGeo.attributes.position.needsUpdate = true;
        /* flames flicker */
        for (i = 0; i < lights.cyan.length; i++) { lights.cyan[i].intensity = 20 + Math.sin(t * 9 + i * 1.7) * 2.5 + Math.sin(t * 23 + i) * 1.2; }
        for (i = 0; i < lights.warm.length; i++) { lights.warm[i].intensity = 14 + Math.sin(t * 11 + i * 2.1) * 2.2 + Math.sin(t * 29 + i * 0.7) * 1.4; }
      },
      /* dim the lamps and the door glow with the lights (the hero starts in the dark) */
      setGlow: function (k) {
        var base = { lamp_cyan: 1.5, lamp_warm: 1.6, door_glow: 1.2 };
        Object.keys(base).forEach(function (tile) { if (meshes[tile]) { meshes[tile].material.emissiveIntensity = base[tile] * (0.06 + 0.94 * k); } });
      },
      setShadows: function (on) {
        key.castShadow = on;
        Object.keys(meshes).forEach(function (k) { meshes[k].castShadow = on && k !== "lamp_cyan" && k !== "lamp_warm"; });
      }
    };
  };

  /* keep an actor inside the room, away from the walls and the braziers */
  W.clampXZ = function (v, margin) {
    var m = W.HALF - (margin || 0.8);
    v.x = TP.clamp(v.x, -m, m);
    v.z = TP.clamp(v.z, -m, m);
    return v;
  };
})();
