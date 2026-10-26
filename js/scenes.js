/* ThePredict v4 · la cripta y las demos. Una sola escena 3D (la Cripta del Augur) con el boss, el jugador y una segunda
   pareja para el split-screen. Cada demo es una coreografía determinista por tiempo (pistas de posición, acciones por
   fase, eventos, cámara y HUD) que corre en bucle mientras su sección está en pantalla. Las conductas que se ven aquí
   (gap close, parry y castigo, punish, disengage y zoning, predicción) son las que decide ThePredict; los efectos son
   las skills del autor: ThePredict decide cuándo y cómo usarlas. Con movimiento reducido, cada demo queda en su
   fotograma clave. */
(function () {
  "use strict";
  var TP = window.TP;
  var THREE = window.THREE;
  if (!THREE) { return; }
  var E = TP.engine;
  var W = TP.world;
  var A = TP.actors;
  var FX = TP.fx;
  var H = TP.hud;
  var FOV = 62;

  TP.addText({
    bossName: "EL AUGUR", bossNameN: "EL AUGUR · IA nativa", you: "tú",
    f3: "{fps} fps · {mind} · tick {tick}",
    act_wait: "esperando tu entrada…", act_strafe: "leyendo tu strafe…", act_gap: "gap close: aterrizo donde vas a estar",
    act_windup: "windup detectado: guardia arriba", act_punishWin: "castigo en la ventana", act_whiff: "swing fallado: recovery de 12 ticks",
    act_heal: "te estás curando: punish", act_shieldFull: "escudo arriba y vida llena: no entro", act_zone: "zoning: bolt y área hasta que bajes el escudo",
    act_shieldDown: "escudo abajo: entro", act_learn: "aprendiendo tu patrón… {n}/3", act_pred: "patrón leído: apunto {k} jugadas por delante",
    act_timer: "timer: skill al azar", act_timer2: "timer: slam (sin objetivo en rango)", act_timer3: "timer: bolt (sin línea de vista)", act_stuck: "bloqueado por el entorno", act_cd: "cooldown: esperando",
    act_press: "presión: te sigo", act_awake: "despierta…", act_reset: "reset: vuelvo a leerte",
    ann_gap: "¡GAP CLOSE!", ann_parry: "¡PARRY!", ann_punish: "¡PUNISH!", ann_pred: "¡PREDICHO!", ann_zone: "¡ZONING!", ann_ko: "KO", ann_whiff: "AL AIRE", ann_miss: "FALLO",
    sub_hit: "Golpe recibido", sub_hitBoss: "El Augur herido", sub_arrow: "Flecha", sub_bolt: "Bolt", sub_leap: "El Augur salta", sub_slam: "Slam", sub_block: "Escudo bloquea",
    sub_parry: "Parry", sub_drink: "Bebiendo", sub_whiff: "Golpe al aire", sub_steps: "Pasos", sub_guard: "Guardia", sub_awake: "El Augur despierta", sub_roar: "Rugido", sub_dash: "Dash",
    sub_area: "Área de ascua", sub_stuck: "Golpe contra piedra", sub_heal: "Curación interrumpida",
    card_archer: "Arquero", card_archerLine: "Arco + escudo · 10 ♥", card_bruiser: "Guerrero", card_bruiserLine: "Espada + escudo · 10 ♥", card_mind: "mind: ThePredict · skills: las tuyas",
    card_mindN: "mind: IA nativa · skills: las mismas", card_boss: "Boss", card_bossLine: "El Augur · Cripta",
    split_l: "IA NATIVA", split_l2: "timers y random", split_r: "THEPREDICT", split_r2: "lee, decide, castiga",
    mind_title: "LA MENTE · predicción", mind_foot: "predictor de orden 2 sobre tu patrón · p = probabilidad", mind_you: "tú", mind_boss: "El Augur",
    m_STRAFE_LEFT: "strafe izq", m_STRAFE_RIGHT: "strafe der", m_STEP_OUT: "atrás", m_STEP_IN: "entras", m_SHIELD_UP: "escudo", m_STRIKE: "golpe", m_RELEASE: "disparas", m_CONSUME: "te curas", m_JUMP: "salto", m_IDLE: "esperas",
    r_STRAFE_LEFT: "bolt adelantado", r_STRAFE_RIGHT: "bolt adelantado", r_STEP_OUT: "leap", r_STEP_IN: "guardia", r_SHIELD_UP: "zoning", r_STRIKE: "parry", r_RELEASE: "gap close", r_CONSUME: "punish", r_JUMP: "sweep", r_IDLE: "presión",
    hud_mind: "mind: ThePredict", hud_mindN: "mind: IA nativa"
  }, {
    bossName: "THE AUGUR", bossNameN: "THE AUGUR · native AI", you: "you",
    f3: "{fps} fps · {mind} · tick {tick}",
    act_wait: "waiting for your entry…", act_strafe: "reading your strafe…", act_gap: "gap close: landing where you'll be",
    act_windup: "windup detected: guard up", act_punishWin: "punishing in the window", act_whiff: "whiffed swing: 12-tick recovery",
    act_heal: "you're healing: punish", act_shieldFull: "shield up, full HP: not engaging", act_zone: "zoning: bolt and area until the shield drops",
    act_shieldDown: "shield down: engaging", act_learn: "learning your pattern… {n}/3", act_pred: "pattern read: aiming {k} moves ahead",
    act_timer: "timer: random skill", act_timer2: "timer: slam (no target in range)", act_timer3: "timer: bolt (no line of sight)", act_stuck: "blocked by the environment", act_cd: "cooldown: waiting",
    act_press: "pressure: following you", act_awake: "awakening…", act_reset: "reset: reading you again",
    ann_gap: "GAP CLOSE!", ann_parry: "PARRY!", ann_punish: "PUNISH!", ann_pred: "PREDICTED!", ann_zone: "ZONING!", ann_ko: "KO", ann_whiff: "WHIFF", ann_miss: "MISS",
    sub_hit: "Hit taken", sub_hitBoss: "The Augur hurt", sub_arrow: "Arrow", sub_bolt: "Bolt", sub_leap: "The Augur leaps", sub_slam: "Slam", sub_block: "Shield blocks",
    sub_parry: "Parry", sub_drink: "Drinking", sub_whiff: "Whiff", sub_steps: "Footsteps", sub_guard: "Guard", sub_awake: "The Augur awakens", sub_roar: "Roar", sub_dash: "Dash",
    sub_area: "Ember area", sub_stuck: "Hits stone", sub_heal: "Healing interrupted",
    card_archer: "Archer", card_archerLine: "Bow + shield · 10 ♥", card_bruiser: "Bruiser", card_bruiserLine: "Sword + shield · 10 ♥", card_mind: "mind: ThePredict · skills: yours",
    card_mindN: "mind: native AI · skills: the same", card_boss: "Boss", card_bossLine: "The Augur · Crypt",
    split_l: "NATIVE AI", split_l2: "timers and random", split_r: "THEPREDICT", split_r2: "reads, decides, punishes",
    mind_title: "THE MIND · prediction", mind_foot: "order-2 predictor over your pattern · p = probability", mind_you: "you", mind_boss: "The Augur",
    m_STRAFE_LEFT: "strafe left", m_STRAFE_RIGHT: "strafe right", m_STEP_OUT: "back off", m_STEP_IN: "step in", m_SHIELD_UP: "shield", m_STRIKE: "strike", m_RELEASE: "shoot", m_CONSUME: "heal", m_JUMP: "jump", m_IDLE: "wait",
    r_STRAFE_LEFT: "lead bolt", r_STRAFE_RIGHT: "lead bolt", r_STEP_OUT: "leap", r_STEP_IN: "guard", r_SHIELD_UP: "zoning", r_STRIKE: "parry", r_RELEASE: "gap close", r_CONSUME: "punish", r_JUMP: "sweep", r_IDLE: "pressure",
    hud_mind: "mind: ThePredict", hud_mindN: "mind: native AI"
  });

  /* ---------------------------------------------------------------------------------------- script helpers */
  /* a position track: keys [t, x, z, ease?]; ease "lin" (default) or "smooth" */
  function track(keys) {
    return function (t, out) {
      out = out || {};
      if (t <= keys[0][0]) { out.x = keys[0][1]; out.z = keys[0][2]; return out; }
      for (var i = 1; i < keys.length; i++) {
        if (t <= keys[i][0]) {
          var k = (t - keys[i - 1][0]) / Math.max(1e-6, keys[i][0] - keys[i - 1][0]);
          if ((keys[i][3] || "lin") === "smooth") { k = TP.smooth(k); } else if (keys[i][3] === "out") { k = TP.easeOut(k); } else if (keys[i][3] === "in") { k = TP.easeIn(k); }
          out.x = keys[i - 1][1] + (keys[i][1] - keys[i - 1][1]) * k;
          out.z = keys[i - 1][2] + (keys[i][2] - keys[i - 1][2]) * k;
          return out;
        }
      }
      var l = keys[keys.length - 1];
      out.x = l[1]; out.z = l[2];
      return out;
    };
  }
  /* an arc track around a centre: keys [t, angle, radius] */
  function orbitTrack(cx, cz, keys) {
    var tr = track(keys.map(function (k) { return [k[0], k[1], k[2], k[3]]; }));
    var tmp = {};
    return function (t, out) {
      tr(t, tmp);
      out = out || {};
      out.x = cx + Math.sin(tmp.x) * tmp.z;
      out.z = cz + Math.cos(tmp.x) * tmp.z;
      return out;
    };
  }
  function between(t, a, b) { return TP.clamp((t - a) / Math.max(1e-6, b - a), 0, 1); }
  var V = function (x, y, z) { return new THREE.Vector3(x, y, z); };
  function yawTo(from, to) { return Math.atan2(to.x - from.x, to.z - from.z); }

  /* ---------------------------------------------------------------------------------------- the scene */
  E.define("crypt", function (E) {
    var tier = E.tier;
    var high = tier === "high";
    var scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x100c0a, high ? 0.03 : 0.034);
    scene.environment = E.env;
    scene.environmentIntensity = 0.18;
    var camera = new THREE.PerspectiveCamera(FOV, E.width / E.height, 0.1, 120);
    var camL = new THREE.PerspectiveCamera(FOV, E.width / 2 / E.height, 0.1, 120);
    var camR = new THREE.PerspectiveCamera(FOV, E.width / 2 / E.height, 0.1, 120);
    var world = W.build({ tier: tier });
    scene.add(world.group);
    var fx = FX.create(scene, world, tier);
    var boss = A.boss({ name: "augur" });
    var player = A.player({ name: "you" });
    var bossN = A.boss({ name: "augurN" });
    var playerN = A.player({ name: "youN", hud: false });
    [boss, player, bossN, playerN].forEach(function (a) { scene.add(a.root); });
    bossN.root.visible = playerN.root.visible = false;
    var bossEmbers = fx.fountain(function () { return boss.pivots.torso.getWorldPosition(new THREE.Vector3()).add(new THREE.Vector3(0, 0.8, 0.3)); }, high ? 6 : 2, { colors: [0xff6a2a, 0xffa050, 0xff3b2a], speed: [0.1, 0.6], life: [0.5, 1.1], size: [0.06, 0.14], gravity: -0.8, drag: 1.2, jitter: 0.5, gain: 2.2 });

    /* camera rig: damped position and target, hand-held jitter, shake from the effects */
    var cam = { pos: V(0, 2, 8), tgt: V(0, 1, 0), wantPos: V(0, 2, 8), wantTgt: V(0, 1, 0), fov: FOV, wantFov: FOV, snap: true, hand: 1 };
    var shakeV = new THREE.Vector3();
    function setCam(p, tg, fov, snap) { cam.wantPos.copy(p); cam.wantTgt.copy(tg); cam.wantFov = fov || FOV; if (snap) { cam.snap = true; } }
    function updateCam(dt, t, camera, lambda) {
      if (cam.snap || E.reduced) { cam.pos.copy(cam.wantPos); cam.tgt.copy(cam.wantTgt); cam.fov = cam.wantFov; cam.snap = false; }
      else {
        var k = 1 - Math.exp(-(lambda || 6) * dt);
        cam.pos.lerp(cam.wantPos, k); cam.tgt.lerp(cam.wantTgt, k); cam.fov += (cam.wantFov - cam.fov) * k;
      }
      fx.shakeOffset(shakeV);
      var hx = Math.sin(t * 0.7) * 0.03 + Math.sin(t * 1.9) * 0.015, hy = Math.cos(t * 0.9) * 0.025;
      camera.position.set(cam.pos.x + shakeV.x + hx * cam.hand, cam.pos.y + shakeV.y + hy * cam.hand, cam.pos.z);
      camera.lookAt(cam.tgt);
      if (Math.abs(camera.fov - cam.fov) > 0.01) { camera.fov = cam.fov; camera.updateProjectionMatrix(); }
    }
    /* third-person over-the-shoulder behind an actor, looking at another */
    var tmpA = V(), tmpB = V(), tmpC = V();
    function thirdPerson(follow, lookAt, dist, height, side, lookH) {
      var dx = lookAt.pos.x - follow.pos.x, dz = lookAt.pos.z - follow.pos.z;
      var len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
      dx /= len; dz /= len;
      tmpA.set(follow.pos.x - dx * dist + dz * (side || 0), height, follow.pos.z - dz * dist - dx * (side || 0));
      tmpB.set((follow.pos.x + lookAt.pos.x) / 2, lookH === undefined ? 1.4 : lookH, (follow.pos.z + lookAt.pos.z) / 2);
      setCam(tmpA, tmpB);
    }
    function sideView(a, b, height, dist, swing, lookH) {
      var mx = (a.pos.x + b.pos.x) / 2, mz = (a.pos.z + b.pos.z) / 2;
      var dx = b.pos.x - a.pos.x, dz = b.pos.z - a.pos.z;
      var len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
      var nx = -dz / len, nz = dx / len;
      var sw = swing || 0;
      tmpA.set(mx + (nx * Math.cos(sw) - dx / len * Math.sin(sw)) * dist, height, mz + (nz * Math.cos(sw) - dz / len * Math.sin(sw)) * dist);
      tmpB.set(mx, lookH === undefined ? 1.5 : lookH, mz);
      setCam(tmpA, tmpB);
    }

    /* lights helper for the hero (dark start) */
    var L = world.lights;
    var lightBase = { key: L.key.intensity, fill: L.fill.intensity, hemi: L.hemi.intensity, door: L.door.intensity };
    function lightsK(k) {
      L.key.intensity = lightBase.key * k; L.fill.intensity = lightBase.fill * k; L.hemi.intensity = lightBase.hemi * (0.15 + 0.85 * k); L.door.intensity = lightBase.door * k;
      world.shafts.uniforms.uOn.value = k;
      world.setGlow(k);
      L.cyan.forEach(function (l) { l.userData.k = 0.2 + 0.8 * k; });
      L.warm.forEach(function (l) { l.userData.k = 0.1 + 0.9 * k; });
    }
    lightsK(1);

    /* ---------------------------------------------------------------- demo runtime */
    var ctx = { boss: boss, player: player, bossN: bossN, playerN: playerN, fx: fx, hud: H, cam: setCam, third: thirdPerson, side: sideView, lights: lightsK, V: V, tier: tier };
    var st = { key: "", demo: null, t: 0, prevT: -1, fired: {}, loops: 0, hp: 20, hpN: 20, bossHp: 1, bossHpN: 1, tick: 0, split: false, lastHudLang: "" };
    var demos = {};
    ctx.once = function (name, time) {
      if (st.prevT < time && st.t >= time && !st.fired[name]) { st.fired[name] = true; return true; }
      return false;
    };
    ctx.hit = function (who, dmg, side) {
      /* who: "player" | "boss"; side "N" for the native pair */
      if (who === "player") { if (side === "N") { st.hpN = Math.max(0, st.hpN - dmg); } else { st.hp = Math.max(0, st.hp - dmg); } }
      else { if (side === "N") { st.bossHpN = Math.max(0, st.bossHpN - dmg); } else { st.bossHp = Math.max(0, st.bossHp - dmg); } }
    };
    ctx.state = st;
    var enters = 0;
    function enterDemo(key) {
      var d = demos[key];
      enters++;
      fx.clear();
      H.reset();
      H.clearSubs();
      st.demo = d || null; st.key = key; st.t = 0; st.prevT = -1; st.fired = {}; st.hp = 20; st.hpN = 20; st.bossHp = 1; st.bossHpN = 1;
      st.act = ""; st.actN = ""; st.actT = ""; st.backFrom = null; st.dashFrom = null; st.from = null; st.tele = null;
      boss.act("idle"); player.act("idle"); bossN.act("idle"); playerN.act("idle");
      boss.held = player.held = bossN.held = playerN.held = null;
      boss.air = player.air = bossN.air = playerN.air = 0;
      boss.lookTarget = player.lookTarget = bossN.lookTarget = playerN.lookTarget = null;
      boss.faceVel = player.faceVel = bossN.faceVel = playerN.faceVel = true;
      boss.setEyes(1); bossN.setEyes(1);
      bossEmbers.on = true;
      lightsK(1);
      cam.snap = true; cam.hand = 1;
      bossN.root.visible = playerN.root.visible = false;
      self.split.on = false;
      if (d && d.enter) { d.enter(ctx); }
      /* settle poses so the first frame is not a pop */
      [boss, player, bossN, playerN].forEach(function (a) { a.prev.copy(a.pos); a.update(1 / 60, 0); a.update(1 / 60, 0); });
    }
    function simulate(dt) {
      st.prevT = st.t;
      st.t += dt;
      st.tick += 1;
      var d = st.demo;
      if (!d) { return; }
      if (st.t >= d.period) { st.loops++; var k = st.key; enterDemo(k); st.prevT = -1; st.t = 0; }
      d.update(ctx, st.t, dt);
      [boss, player].forEach(function (a) { W.clampXZ(a.pos, 1); a.update(dt, st.t); });
      if (self.split.on) { [bossN, playerN].forEach(function (a) { W.clampXZ(a.pos, 1); a.update(dt, st.t); }); }
      fx.update(dt, st.t);
      world.update(st.t, dt);
      L.cyan.forEach(function (l) { l.intensity *= (l.userData.k === undefined ? 1 : l.userData.k); });
      L.warm.forEach(function (l) { l.intensity *= (l.userData.k === undefined ? 1 : l.userData.k); });
    }
    var self = {
      scene: scene, camera: camera, exposure: 1.08, bloom: { strength: 0.75, radius: 0.55, threshold: 0.82 },
      split: { on: false, left: camL, right: camR, before: function (side) {
        if (side === "left") { boss.root.visible = player.root.visible = false; bossN.root.visible = playerN.root.visible = true; fx.showSide(1); }
        else if (side === "right") { boss.root.visible = player.root.visible = true; bossN.root.visible = playerN.root.visible = false; fx.showSide(2); }
        else { boss.root.visible = player.root.visible = true; bossN.root.visible = playerN.root.visible = false; fx.showSide(0); }
      } },
      resize: function (w, h) {
        camera.aspect = w / h; camera.updateProjectionMatrix();
        camL.aspect = camR.aspect = w < h ? w / (h / 2) : (w / 2) / h; camL.updateProjectionMatrix(); camR.updateProjectionMatrix();
        fx.setPointScale(h * E.dpr);
      },
      activate: function () { H.show(true); },
      deactivate: function () { H.show(false); },
      shadowsChanged: function () { world.setShadows(E.renderer.shadowMap.enabled); },
      /* compile the variant with the spare lights on, so a hit light never compiles in the middle of a demo; then the
         normal one again (cached, instant): it leaves the renderer's light state and the unlit materials as before */
      warm: function (compile) {
        var spare = world.lights.spare, on = spare[0].visible;
        spare.forEach(function (l) { l.visible = true; });
        compile();
        spare.forEach(function (l) { l.visible = false; });
        compile();
        spare.forEach(function (l) { l.visible = on; });
      },
      update: function (dt, view) {
        if (view.key !== st.key) {
          enterDemo(view.key);
          if (E.reduced && st.demo) { self.seekDemo(st.demo.still || 0); }
        }
        if (!st.demo) { return false; }
        if (!E.reduced) { simulate(dt); }
        else if (st.demo && st.demo.updateStill) { st.demo.updateStill(ctx, st.t); }
        /* camera and HUD after the demo has placed everything */
        if (st.demo.camera) { st.demo.camera(ctx, st.t); }
        updateCam(E.reduced ? 1 : dt, st.t, camera, st.demo.camLambda || 6);
        if (self.split.on) { camL.position.copy(camera.position); camL.quaternion.copy(camera.quaternion); camL.fov = camera.fov; camL.updateProjectionMatrix(); camR.position.copy(camera.position); camR.quaternion.copy(camera.quaternion); camR.fov = camera.fov; camR.updateProjectionMatrix(); }
        if (st.demo.hud) { st.demo.hud(ctx, st.t, view); }
        H.f3(TP.t("f3", { fps: E.reduced ? "—" : "60", mind: TP.t(st.demo.native ? "hud_mindN" : "hud_mind"), tick: st.tick }));
        return !E.reduced;
      },
      seekDemo: function (t) {
        enterDemo(st.key);
        var steps = Math.max(1, Math.round(t * 30));
        for (var i = 0; i < steps; i++) { simulate(1 / 30); }
        if (st.demo && st.demo.camera) { st.demo.camera(ctx, st.t); }
        cam.snap = true;
      },
      demoInfo: function () { return { key: st.key, t: Math.round(st.t * 100) / 100, loops: st.loops, hp: st.hp, bossHp: st.bossHp, split: self.split.on, enters: enters }; }
    };
    E.on("section", function () { E.invalidate(); });
    TP.on("lang", function () { if (st.demo && st.demo.hud) { st.demo.hud(ctx, st.t, { p: 0 }); } });

    /* ================================================================ demos */
    var pv = {}, bv = {}, tmpP = V(), tmpQ = V();
    function cardArcher(native) { H.card({ tag: TP.t("hudBuild"), title: TP.t("card_archer"), line: TP.t("card_archerLine"), mind: TP.t(native ? "card_mindN" : "card_mind"), icon: "bow" }); }
    function cardBruiser(native) { H.card({ tag: TP.t("hudBuild"), title: TP.t("card_bruiser"), line: TP.t("card_bruiserLine"), mind: TP.t(native ? "card_mindN" : "card_mind"), icon: "sword" }); }
    function baseHud(bossOn, hp, bossHp, hurt) {
      H.setBoss(TP.t("bossName"), bossHp === undefined ? st.bossHp : bossHp, hurt);
      H.setHearts(hp === undefined ? st.hp : hp, 0);
      H.setArmor(st.hp > 0 ? 8 : 0);
      void bossOn;
    }

    /* ---------------------------------------------------------------- 0 · hero: the boss awakens */
    demos.hero = {
      period: 15, still: 9.5,
      enter: function (c) {
        boss.setPos(0, -5); boss.yaw = 0; boss.setEyes(0); boss.hold("hold");
        player.setPos(0.9, 4.2); player.yaw = Math.PI; player.equip("sword", "shield"); player.look(boss.pos);
        c.lights(0.04);
        bossEmbers.on = false;
        cam.hand = 0.4;
      },
      update: function (c, t, dt) {
        /* it stands dormant in the dark; the eyes flicker on, then it roars awake */
        if (c.once("awaken", 2.6)) { boss.release(); boss.act("awaken", 2.4); H.sub(TP.t("sub_awake")); }
        var eyes = t < 1.1 ? 0 : t < 2.6 ? (0.5 + 0.5 * Math.sin(t * 40) > 0.4 ? between(t, 1.1, 2.2) * 2.6 : 0.3) : 2.6 + Math.sin(t * 3) * 0.3;
        boss.setEyes(eyes);
        if (c.once("roar", 4.2)) { fx.snd("roar"); fx.flash("#ffd9a8", 0.35, 0.55); fx.shock(boss.pos, 5, 0xff6a2a, 0.7); fx.fireOrange(V(boss.pos.x, 1.2, boss.pos.z), 90); fx.soulCyan(V(boss.pos.x, 2.6, boss.pos.z), 50); fx.shake(0.22, 0.6); fx.light(boss.pos, 0xff6a2a, 60, 0.6); H.sub(TP.t("sub_roar")); bossEmbers.on = true; }
        c.lights(t < 4.2 ? 0.04 + 0.08 * between(t, 2.6, 4.2) : 0.12 + 0.88 * between(t, 4.2, 5.6));
        if (t > 5.2) {
          /* it walks toward you; you hold your stance */
          var k = between(t, 5.4, 11.5);
          boss.setPos(0, -5 + 5.2 * k);
          boss.look(player.pos);
          if (t > 12.5) { boss.hold("hold"); }
          if (c.once("steps1", 7)) { H.sub(TP.t("sub_steps")); }
          if (c.once("steps2", 9.5)) { H.sub(TP.t("sub_steps")); }
        }
        player.hold("block");
        if (t > 11) { player.release(); player.hold("point"); }
      },
      camera: function (c, t) {
        if (t < 2.6) { var k0 = between(t, 0, 2.6); setCam(V(3.4 - 0.7 * k0, 3.0, 3.6 - 1.4 * k0), V(0, 3.2, -5), 30, t < 0.1); }
        else if (t < 5.0) { var k = between(t, 2.6, 5.0); setCam(V(2.6 - 0.8 * k, 2.6 + 0.6 * k, 1.4 + 0.8 * k), V(0, 2.6 + 0.2 * k, -5), 46); }
        else if (t < 8.5) { k = between(t, 5.0, 8.5); setCam(V(-2.5 + 1.2 * k, 1.3 + 0.6 * k, 3.0 + 2.2 * k), V(0, 1.7, -3), 62); }
        else { thirdPerson(player, boss, 3.2, 2.1, 0.9, 1.6); }
      },
      hud: function (c, t) {
        var bossIn = between(t, 4.4, 6.4);
        H.layout({ boss: t > 4.4, bottom: t > 5.6, card: t > 6.2, subs: true, f3: t > 5.6 });
        H.setBoss(TP.t("bossName"), bossIn, false);
        H.setHearts(20, 0); H.setArmor(8);
        cardBruiser(false);
        H.action(t > 6.5 && t < 12.5 ? TP.t("act_press") : t > 12.5 ? TP.t("act_wait") : "", t > 6.5 ? "tp" : "");
      }
    };

    /* ---------------------------------------------------------------- 1 · split: native vs ThePredict, same build */
    (function () {
      var C = { x: 0, z: -3 };
      var pTrack = orbitTrack(C.x, C.z, [[0, 0, 11], [2, 0, 5.5, "out"], [2.3, 0, 5.5], [5, 1.3, 4.5], [7, 1.6, 8.5, "smooth"], [8.4, 1.0, 2.1, "in"], [9, 1.0, 2.1], [11, 1.0, 2.1], [12.2, 0.4, 7.5, "out"], [13.2, 0.4, 7.5], [14.6, 0.2, 3.2, "smooth"], [16, 0.2, 3.2]]);
      function playerScript(pl, t, c, side, bs) {
        fx.side = side === "N" ? 1 : 2;
        pTrack(t, pv);
        pl.setPos(pv.x, pv.z);
        pl.look(bs.pos);
        if (t > 2) { pl.faceVel = false; pl.yaw = A.dampAngle(pl.yaw, yawTo(pl.pos, bs.pos), 10, 1 / 60); }
        if (c.once("eq" + side, 5)) { pl.equip("sword", "bow"); pl.hold("draw"); }
        if (c.once("shoot" + side, 6.3)) { pl.act("release", 0.5); var from = pl.handWorld(); fx.shoot("arrow", from, function () { return bs.pivots.torso.getWorldPosition(tmpQ).add(V(0, 0.6, 0)); }, 0.35, function () { c.hit("boss", 0.08, side); fx.sparkWhite(bs.pivots.torso.getWorldPosition(tmpQ).add(V(0, 0.8, 0)), 14); }); }
        if (c.once("sw" + side, 7)) { pl.equip("sword", "shield"); }
        if (c.once("swing" + side, 8.3)) { pl.act("swing", 0.7); }
        if (c.once("blk" + side, 9.2)) { pl.hold("block"); }
        if (c.once("unblk" + side, 11)) { pl.release(); }
        if (c.once("drink" + side, 12.4)) { pl.equip("potion", "shield"); pl.act("drink", 1.6); }
        if (c.once("sw2" + side, 14)) { pl.equip("sword", "shield"); }
      }
      demos.split = {
        period: 16, still: 8.7, native: false,
        enter: function (c) {
          self.split.on = true;
          boss.setPos(C.x, C.z); bossN.setPos(C.x, C.z);
          boss.yaw = bossN.yaw = 0;
          player.setPos(0, 8); playerN.setPos(0, 8);
          player.equip("sword", "shield"); playerN.equip("sword", "shield");
          boss.faceVel = bossN.faceVel = false;
          st.hp = 20; st.hpN = 20; st.actN = ""; st.actT = "";
        },
        update: function (c, t, dt) {
          playerScript(player, t, c, "", boss);
          playerScript(playerN, t, c, "N", bossN);
          var pN = playerN, pT = player;
          /* ------ left: native AI on timers */
          fx.side = 1;
          bossN.look(pN.pos);
          bossN.yaw = A.dampAngle(bossN.yaw, yawTo(bossN.pos, pN.pos), 6, dt);
          if (c.once("n1", 1.0)) { bossN.act("windup", 1.5); st.actN = TP.t("act_timer2"); }
          if (c.once("n1h", 2.17)) { fx.shock(bossN.pos, 3, 0xff6a2a, 0.5); fx.dust(V(bossN.pos.x, 0.3, bossN.pos.z), 30); H.sub(TP.t("sub_slam"), "left"); H.announce(TP.t("ann_whiff"), "miss", "L"); }
          if (c.once("n2", 3.2)) { bossN.act("cast", 0.9); st.actN = TP.t("act_timer3"); }
          if (c.once("n2b", 3.7)) { var tgt = V(pN.pos.x, 0.5, pN.pos.z); var dir = tgt.clone().sub(bossN.pos).setY(0).normalize(); fx.shoot("bolt", bossN.handWorld(), V(bossN.pos.x + dir.x * 14, 0.3, bossN.pos.z + dir.z * 14), 0.9, null, { miss: true }); H.sub(TP.t("sub_bolt"), "left"); }
          if (t > 4.8 && t < 7.2) {
            /* walks straight at you and bumps into the broken pillar */
            var k = between(t, 4.8, 5.6);
            bossN.setPos(C.x - 2.2 * k, C.z + 3.6 * k);
            if (t > 5.6) { bossN.hold("stuck"); st.actN = TP.t("act_stuck"); if (c.once("n3", 5.9)) { H.sub(TP.t("sub_stuck"), "left"); fx.dust(V(-2.6, 1, 0.9), 16); } if (c.once("n3b", 6.7)) { fx.dust(V(-2.6, 1, 0.9), 16); } }
            else { st.actN = TP.t("act_timer"); }
          }
          if (c.once("n4", 7.2)) { bossN.release(); bossN.setPos(C.x - 2.2, C.z + 3.6); }
          if (c.once("n5", 8.0)) { bossN.act("windup", 1.5); st.actN = TP.t("act_timer2"); }
          if (c.once("n5h", 9.17)) { fx.shock(bossN.pos, 3, 0xff6a2a, 0.5); fx.dust(V(bossN.pos.x, 0.3, bossN.pos.z), 30); H.sub(TP.t("sub_slam"), "left"); }
          if (c.once("pnh", 8.65)) { /* your swing lands on the native boss */ c.hit("boss", 0.12, "N"); fx.slash(V(bossN.pos.x, 1.6, bossN.pos.z), pN.yaw, { color: 0xffffff, gain: 2.2, radius: 1.2, dur: 0.25, cell: 0.12 }); fx.hitRed(V(bossN.pos.x, 1.8, bossN.pos.z), 30); fx.sparkWhite(V(bossN.pos.x, 1.8, bossN.pos.z), 10); H.sub(TP.t("sub_hitBoss"), "left"); }
          if (t > 9.5 && t < 11) { st.actN = TP.t("act_timer"); if (c.once("n6", 9.8)) { bossN.act("sweep", 0.7); } if (c.once("n6h", 10.15)) { fx.sparkWhite(V(pN.pos.x, 1.2, pN.pos.z), 20); H.sub(TP.t("sub_block"), "left"); } }
          if (t > 11 && t < 13.5) { st.actN = TP.t("act_cd"); }
          if (c.once("n7", 13.6)) { bossN.act("cast", 0.9); st.actN = TP.t("act_timer3"); }
          if (c.once("n7b", 14.1)) { var d2 = V(pN.pos.x, 0.5, pN.pos.z).sub(bossN.pos).setY(0).normalize(); fx.shoot("bolt", bossN.handWorld(), V(bossN.pos.x + d2.x * 14, 0.3, bossN.pos.z + d2.z * 14), 0.9, null, { miss: true }); H.sub(TP.t("sub_bolt"), "left"); }
          /* ------ right: ThePredict */
          fx.side = 2;
          boss.look(pT.pos);
          boss.yaw = A.dampAngle(boss.yaw, yawTo(boss.pos, pT.pos), 10, dt);
          if (t < 2.2) { st.actT = TP.t("act_wait"); boss.hold("hold"); }
          if (c.once("t1", 2.2)) { boss.release(); st.actT = TP.t("act_strafe"); }
          if (c.once("t2", 3.6)) { boss.act("cast", 0.9); }
          if (c.once("t2b", 4.1)) { pTrack(4.75, bv); fx.shoot("bolt", boss.handWorld(), function () { return V(pT.pos.x, 1.0, pT.pos.z); }, 0.55, function (p) { c.hit("player", 4); pT.act("hurt", 0.5); fx.hitRed(p, 30); H.sub(TP.t("sub_hit"), "right"); }); H.sub(TP.t("sub_bolt"), "right"); }
          if (c.once("t3", 5.4)) { boss.act("leap", 1.25); st.actT = TP.t("act_gap"); H.sub(TP.t("sub_leap"), "right"); fx.dust(V(boss.pos.x, 0.2, boss.pos.z), 20); }
          if (t >= 5.4 && t < 6.65) {
            var kk = between(t, 5.55, 6.5);
            pTrack(6.6, bv);
            var dx = bv.x - C.x, dz = bv.z - C.z, len = Math.sqrt(dx * dx + dz * dz);
            var ex = bv.x - dx / len * 2.0, ez = bv.z - dz / len * 2.0;
            boss.setPos(C.x + (ex - C.x) * kk, C.z + (ez - C.z) * kk, 0);
            boss.air = Math.sin(Math.PI * kk) * 2.6;
          }
          if (c.once("t3h", 6.55)) { boss.air = 0; fx.shock(boss.pos, 3.5, 0xff6a2a, 0.5); fx.dust(V(boss.pos.x, 0.2, boss.pos.z), 40); fx.shake(0.12, 0.3); }
          if (c.once("t4", 6.75)) { boss.act("sweep", 0.7); }
          if (c.once("t4h", 7.12)) { c.hit("player", 5); pT.act("stagger", 0.7); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.3 }); fx.hitRed(V(pT.pos.x, 1.2, pT.pos.z), 50); fx.light(pT.pos, 0xff3b4a, 30, 0.3); H.sub(TP.t("sub_hit"), "right"); }
          if (c.once("t5", 8.05)) { boss.hold("guard"); fx.dome.set(true, boss.pos, 2.2, 0x5ff0ff); st.actT = TP.t("act_windup"); }
          if (c.once("t5p", 8.65)) { fx.dome.hit(); fx.sparkWhite(V(pT.pos.x + (boss.pos.x - pT.pos.x) * 0.5, 1.6, pT.pos.z + (boss.pos.z - pT.pos.z) * 0.5), 40); fx.slash(V(boss.pos.x, 1.6, boss.pos.z), pT.yaw, { color: 0xffffff, gain: 2.2, radius: 1.2, dur: 0.22, cell: 0.12 }); pT.act("stagger", 0.9); H.announce(TP.t("ann_parry"), "parry", "R"); H.sub(TP.t("sub_parry"), "right"); }
          if (c.once("t6", 8.95)) { boss.release(); fx.dome.set(false); boss.act("sweep", 0.65); st.actT = TP.t("act_punishWin"); }
          if (c.once("t6h", 9.3)) { c.hit("player", 5); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.3, flip: true }); fx.hitRed(V(pT.pos.x, 1.2, pT.pos.z), 50); H.sub(TP.t("sub_hit"), "right"); }
          if (t > 9.6 && t < 11.2) {
            st.actT = TP.t("act_shieldFull");
            var kb = between(t, 9.7, 10.6);
            var bx = pT.pos.x - boss.pos.x, bz = pT.pos.z - boss.pos.z, bl = Math.sqrt(bx * bx + bz * bz) || 1;
            if (!st.backFrom) { st.backFrom = boss.pos.clone(); }
            boss.setPos(st.backFrom.x - bx / bl * 3.2 * kb, st.backFrom.z - bz / bl * 3.2 * kb);
          }
          if (c.once("t7", 11.9)) { st.backFrom = null; boss.act("dash", 0.9); st.actT = TP.t("act_heal"); H.sub(TP.t("sub_dash"), "right"); }
          if (t >= 11.9 && t < 12.7) {
            var kd = between(t, 11.9, 12.6);
            if (!st.dashFrom) { st.dashFrom = boss.pos.clone(); }
            var tx = pT.pos.x - (pT.pos.x - st.dashFrom.x) * (1.9 / Math.max(1.9, pT.pos.distanceTo(st.dashFrom))), tz = pT.pos.z - (pT.pos.z - st.dashFrom.z) * (1.9 / Math.max(1.9, pT.pos.distanceTo(st.dashFrom)));
            boss.setPos(st.dashFrom.x + (tx - st.dashFrom.x) * TP.easeOut(kd), st.dashFrom.z + (tz - st.dashFrom.z) * TP.easeOut(kd));
            if (kd > 0.3) { fx.emberTrail(V(boss.pos.x, 1.2, boss.pos.z), 2); }
          }
          if (c.once("t8", 12.7)) { st.dashFrom = null; boss.act("sweep", 0.65); }
          if (c.once("t8h", 13.05)) { c.hit("player", 5); pT.equip("sword", "shield"); pT.act("stagger", 0.8); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.3 }); fx.hitRed(V(pT.pos.x, 1.2, pT.pos.z), 50); H.announce(TP.t("ann_punish"), "punish", "R"); H.sub(TP.t("sub_heal"), "right"); st.actT = TP.t("act_punishWin"); }
          if (c.once("t9", 14.2)) { boss.act("windup", 1.5); st.actT = TP.t("act_press"); }
          if (c.once("t9h", 15.37)) { c.hit("player", 6); fx.shock(boss.pos, 4, 0xff3b4a, 0.6); fx.column(pT.pos, { color: 0xff3b4a, height: 5, width: 1.4, dur: 0.8 }); fx.hitRed(V(pT.pos.x, 1, pT.pos.z), 60); fx.shake(0.2, 0.4); pT.act("stagger", 1.2); H.announce(TP.t("ann_ko"), "ko", "R"); H.sub(TP.t("sub_slam"), "right"); fx.flash("#ff3b4a", 0.4, 0.35); }
          fx.side = 0;
        },
        camera: function (c, t) {
          /* both halves share the camera: high behind you, so both fighters fit a narrow half */
          thirdPerson(player, boss, 6.4, 3.8, 1.1, 1.3);
          cam.wantFov = 58;
        },
        hud: function (c, t) {
          H.layout({ split: true, subs: true, f3: false });
          H.split(TP.t("split_l") + "\n" + TP.t("split_l2") + "\n" + (st.actN ? TP.t("hudNativeTag") + " " + st.actN : "") + "\n♥ " + st.hpN + "/20 · boss " + Math.round(st.bossHpN * 100) + "%",
            TP.t("split_r") + "\n" + TP.t("split_r2") + "\n" + (st.actT ? TP.t("hudPredict") + " " + st.actT : "") + "\n♥ " + st.hp + "/20 · boss " + Math.round(st.bossHp * 100) + "%");
        }
      };
    })();

    /* ---------------------------------------------------------------- 2 · gap close */
    (function () {
      var C = { x: 0, z: 0 };
      var pTrack = orbitTrack(C.x, C.z, [[0, 2.2, 9], [5.0, 3.6, 9], [5.6, 3.7, 9], [6.1, 3.7, 9], [8.5, 4.6, 8.5, "smooth"], [12, 5.1, 9]]);
      var LAND = 5.0;
      demos.gap = {
        period: 12, still: 5.15,
        enter: function (c) {
          boss.setPos(0, 0); boss.yaw = Math.PI; boss.faceVel = false;
          player.equip("sword", "bow"); player.faceVel = false;
          pTrack(0, pv); player.setPos(pv.x, pv.z);
          st.land = null;
        },
        update: function (c, t, dt) {
          if (t < 5.0 || t > 6.4) { pTrack(t, pv); player.setPos(pv.x, pv.z); }
          player.look(boss.pos);
          player.yaw = A.dampAngle(player.yaw, yawTo(player.pos, boss.pos), 8, dt);
          boss.look(player.pos);
          if (t < 3.8 || t > 6.4) { boss.yaw = A.dampAngle(boss.yaw, yawTo(boss.pos, player.pos), 8, dt); }
          /* you kite: draw and shoot every 2.4 s */
          if (t < 5 || t > 7) {
            var cycle = t % 2.4;
            if (cycle < 1.4) { player.hold("draw"); }
            else if (c.once("shot" + Math.floor(t / 2.4), Math.floor(t / 2.4) * 2.4 + 1.4)) { player.release(); player.act("release", 0.5); fx.shoot("arrow", player.handWorld(), function () { return boss.pivots.torso.getWorldPosition(tmpQ).add(V(0, 0.7, 0)); }, 0.45, function (p) { c.hit("boss", 0.05); fx.sparkWhite(p, 12); H.sub(TP.t("sub_hitBoss")); }); H.sub(TP.t("sub_arrow")); }
          }
          /* the boss walks in while reading you */
          if (t < 3.8) { var k = between(t, 0.4, 3.8); boss.setPos(C.x + Math.sin(2.9) * 2.2 * k, C.z + Math.cos(2.9) * 2.2 * k); }
          if (c.once("read", 1.0)) { st.act = TP.t("act_strafe"); }
          if (t > 2.2 && t < 5.1) {
            /* the mind: where you will be */
            var pts = [];
            for (var i = 1; i <= 6; i++) { pTrack(Math.min(LAND, t + i * 0.45), bv); pts.push({ x: bv.x, z: bv.z }); }
            fx.path(pts, 0x59e1ff);
            pTrack(LAND, bv);
            fx.cross(true, bv, t > 3.6 ? 0xff3b4a : 0x59e1ff);
            fx.sight(true, boss.eyeWorld(tmpP), V(bv.x, 0.1, bv.z));
          } else { fx.path([]); fx.cross(false); fx.sight(false); }
          if (c.once("leap", 3.75)) { boss.act("leap", 1.3); st.act = TP.t("act_gap"); H.sub(TP.t("sub_leap")); fx.dust(V(boss.pos.x, 0.2, boss.pos.z), 24); st.leapFrom = boss.pos.clone(); }
          if (t >= 3.75 && t < 5.05) {
            var kk = between(t, 3.9, 5.0);
            pTrack(LAND, bv);
            var dx = bv.x - st.leapFrom.x, dz = bv.z - st.leapFrom.z, len = Math.sqrt(dx * dx + dz * dz);
            var ex = bv.x - dx / len * 1.9, ez = bv.z - dz / len * 1.9;
            boss.setPos(st.leapFrom.x + (ex - st.leapFrom.x) * kk, st.leapFrom.z + (ez - st.leapFrom.z) * kk);
            boss.air = Math.sin(Math.PI * kk) * 3.2;
            boss.yaw = Math.atan2(dx, dz);
            if (kk > 0.1 && kk < 0.9) { fx.emberTrail(V(boss.pos.x, 1.5 + boss.air, boss.pos.z), 2); }
          }
          if (c.once("land", 5.02)) { boss.air = 0; fx.shock(boss.pos, 4, 0xff6a2a, 0.55); fx.dust(V(boss.pos.x, 0.2, boss.pos.z), 50); fx.fireOrange(V(boss.pos.x, 0.4, boss.pos.z), 30); fx.shake(0.16, 0.35); fx.light(boss.pos, 0xff6a2a, 40, 0.4); H.announce(TP.t("ann_gap"), "gap"); player.release(); player.act("stagger", 0.8); c.hit("player", 4); fx.hitRed(V(player.pos.x, 1, player.pos.z), 20); H.sub(TP.t("sub_hit")); }
          if (c.once("sweep", 5.35)) { boss.act("sweep", 0.7); }
          if (c.once("sweeph", 5.72)) { c.hit("player", 5); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.3 }); fx.hitRed(V(player.pos.x, 1.2, player.pos.z), 50); fx.light(player.pos, 0xff3b4a, 30, 0.3); player.act("stagger", 0.8); H.sub(TP.t("sub_hit")); st.act = TP.t("act_punishWin"); }
          if (t > 6.4 && t < 8.6) { /* you sprint away; it follows at a walk */ var kf = between(t, 6.6, 8.6); boss.setPos(boss.pos.x + (player.pos.x - boss.pos.x) * 0.012, boss.pos.z + (player.pos.z - boss.pos.z) * 0.012); void kf; if (c.once("reset", 7.5)) { st.act = TP.t("act_reset"); } }
          if (t > 8.6) { boss.setPos(boss.pos.x + (C.x - boss.pos.x) * 0.02, boss.pos.z + (C.z - boss.pos.z) * 0.02); if (t > 10) { st.act = TP.t("act_strafe"); } }
        },
        camera: function (c, t) {
          if (t < 3.5) { thirdPerson(player, boss, 3.4, 2.2, 0.9, 1.5); cam.wantFov = 64; }
          else if (t < 6.3) { sideView(player, boss, 3.4, 9.5, 0.5, 1.6); cam.wantFov = 56; }
          else { thirdPerson(player, boss, 3.6, 2.3, 0.9, 1.5); cam.wantFov = 64; }
        },
        camLambda: 5,
        hud: function (c, t) {
          H.layout({ boss: true, bottom: true, card: true, subs: true, f3: true, crosshair: t < 3.5 });
          baseHud(true, st.hp, st.bossHp, false);
          cardArcher(false);
          H.action(st.act || "", "tp");
        }
      };
    })();

    /* ---------------------------------------------------------------- 3 · parry and punish */
    demos.parry = {
      period: 11, still: 3.25,
      enter: function (c) {
        boss.setPos(0, -1.2); boss.yaw = Math.PI; boss.faceVel = false;
        player.setPos(0.2, 1.3); player.yaw = Math.PI; player.equip("sword", "shield"); player.faceVel = false;
        st.act = TP.t("act_wait");
      },
      update: function (c, t, dt) {
        /* you circle a little, then charge a heavy; it reads the windup */
        if (t < 1.6) { player.setPos(0.2 + Math.sin(t * 1.5) * 0.8, 1.3 + Math.sin(t * 0.8) * 0.2); }
        if (t > 7.4) { var kr = between(t, 7.4, 9.5); player.setPos(2.6 - 2.4 * kr, 4.0 - 2.7 * kr); }
        player.look(boss.pos); player.yaw = A.dampAngle(player.yaw, yawTo(player.pos, boss.pos), 10, dt);
        boss.look(player.pos); boss.yaw = A.dampAngle(boss.yaw, yawTo(boss.pos, player.pos), 10, dt);
        if (c.once("heavy", 1.6)) { player.act("heavy", 2.0); }
        if (c.once("read", 1.95)) { st.act = TP.t("act_windup"); }
        if (c.once("guard", 2.2)) { boss.hold("guard"); fx.dome.set(true, boss.pos, 2.3, 0x5ff0ff); H.sub(TP.t("sub_guard")); }
        if (c.once("clash", 3.22)) {
          var mid = V((player.pos.x + boss.pos.x) / 2, 1.7, (player.pos.z + boss.pos.z) / 2);
          fx.dome.hit(); fx.sparkWhite(mid, 60); fx.sparkWhite(mid, 20, V(0, 1, 0)); fx.slash(V(boss.pos.x, 1.6, boss.pos.z), player.yaw, { color: 0xffffff, gain: 2.4, radius: 1.3, dur: 0.22, cell: 0.12, vertical: true });
          fx.light(mid, 0x9af7ff, 30, 0.3); fx.shake(0.14, 0.3); fx.flash("#d8f8ff", 0.18, 0.12);
          player.act("stagger", 1.0); H.announce(TP.t("ann_parry"), "parry"); H.sub(TP.t("sub_parry"));
        }
        if (c.once("punish", 3.55)) { boss.release(); fx.dome.set(false); boss.act("sweep", 0.7); st.act = TP.t("act_punishWin"); }
        if (c.once("punishh", 3.92)) { c.hit("player", 7); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.4, gain: 3.4 }); fx.hitRed(V(player.pos.x, 1.2, player.pos.z), 70); fx.light(player.pos, 0xff3b4a, 40, 0.35); player.act("stagger", 1.1); H.sub(TP.t("sub_hit")); fx.shake(0.1, 0.25); }
        if (t > 4.3 && t < 7.4) { var kb = between(t, 4.3, 5.2); player.setPos(0.2 + 2.4 * kb, 1.3 + 2.7 * kb); if (c.once("reset", 6.0)) { st.act = TP.t("act_reset"); boss.hold("hold"); } }
        if (c.once("rel", 9.6)) { boss.release(); st.act = TP.t("act_wait"); }
      },
      camera: function (c, t) {
        var sw = -0.35 + Math.sin(t * 0.35) * 0.25;
        sideView(player, boss, 2.3, 7.6, sw, 1.5);
        cam.wantFov = t > 1.8 && t < 4.2 ? 48 : 54;
      },
      camLambda: 5,
      hud: function (c, t) {
        H.layout({ boss: true, bottom: true, card: true, subs: true, f3: true });
        baseHud(true, st.hp, st.bossHp, false);
        cardBruiser(false);
        H.action(st.act || "", "tp");
      }
    };

    /* ---------------------------------------------------------------- 4 · punish (whiff, then healing) */
    demos.punish = {
      period: 12, still: 6.05,
      enter: function (c) {
        boss.setPos(0, -1.5); boss.yaw = Math.PI; boss.faceVel = false;
        player.setPos(0.3, 5.5); player.equip("sword", "shield"); player.faceVel = false;
        st.act = TP.t("act_wait"); st.from = null;
      },
      update: function (c, t, dt) {
        if (t < 2.0) { var k = between(t, 0, 1.8); player.setPos(0.3, 5.5 - 4.6 * TP.easeOut(k)); }
        player.look(boss.pos); player.yaw = A.dampAngle(player.yaw, yawTo(player.pos, boss.pos), 10, dt);
        boss.look(player.pos); boss.yaw = A.dampAngle(boss.yaw, yawTo(boss.pos, player.pos), 10, dt);
        if (c.once("swing", 2.0)) { player.act("swing", 0.7); }
        if (t >= 2.05 && t < 2.6) { var kb = between(t, 2.05, 2.5); boss.setPos(0 - 0.9 * TP.easeOut(kb), -1.5 - 1.6 * TP.easeOut(kb)); }
        if (c.once("whiff", 2.35)) { fx.slash(V(player.pos.x, 1.3, player.pos.z), player.yaw, { color: 0xc8d0dc, gain: 1.6, radius: 1.1, dur: 0.22, cell: 0.1 }); H.announce(TP.t("ann_whiff"), "miss"); H.sub(TP.t("sub_whiff")); st.act = TP.t("act_whiff"); }
        if (c.once("p1", 2.75)) { boss.act("sweep", 0.65); }
        if (c.once("p1h", 3.1)) { c.hit("player", 6); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.3 }); fx.hitRed(V(player.pos.x, 1.2, player.pos.z), 50); fx.light(player.pos, 0xff3b4a, 36, 0.3); player.act("stagger", 0.9); H.sub(TP.t("sub_hit")); }
        if (t > 3.6 && t < 5.1) { var kr = between(t, 3.6, 5.0); player.setPos(0.3 + 1.6 * kr, 0.9 + 4.6 * TP.easeOut(kr)); if (c.once("reset", 4.4)) { st.act = TP.t("act_press"); } }
        if (c.once("drink", 5.0)) { player.equip("potion", "shield"); player.act("drink", 1.8); H.sub(TP.t("sub_drink")); }
        if (c.once("read", 5.15)) { st.act = TP.t("act_heal"); }
        if (c.once("dash", 5.3)) { boss.act("dash", 0.8); st.from = boss.pos.clone(); H.sub(TP.t("sub_dash")); }
        if (t >= 5.3 && t < 6.0) {
          var kd = TP.easeOut(between(t, 5.3, 5.95));
          var dx = player.pos.x - st.from.x, dz = player.pos.z - st.from.z, len = Math.sqrt(dx * dx + dz * dz);
          boss.setPos(st.from.x + (dx - dx / len * 2.0) * kd, st.from.z + (dz - dz / len * 2.0) * kd);
          if (kd > 0.2) { fx.emberTrail(V(boss.pos.x, 1.2, boss.pos.z), 3); }
        }
        if (c.once("p2", 6.0)) { boss.act("sweep", 0.65); }
        if (c.once("p2h", 6.35)) { c.hit("player", 7); player.equip("sword", "shield"); player.act("stagger", 1.1); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.4, gain: 3.4, flip: true }); fx.hitRed(V(player.pos.x, 1.2, player.pos.z), 70); fx.light(player.pos, 0xff3b4a, 40, 0.35); fx.shake(0.12, 0.3); H.announce(TP.t("ann_punish"), "punish"); H.sub(TP.t("sub_heal")); st.act = TP.t("act_punishWin"); }
        if (t > 7.5 && t < 12) { var kk = between(t, 7.5, 9.5); player.setPos(1.9 - 1.6 * kk, 5.5); boss.setPos(boss.pos.x + (0 - boss.pos.x) * 0.02, boss.pos.z + (-1.5 - boss.pos.z) * 0.02); if (c.once("r2", 8.5)) { st.act = TP.t("act_reset"); } if (c.once("r3", 10.5)) { st.act = TP.t("act_wait"); } }
      },
      camera: function (c, t) {
        if (t < 3.5) { sideView(player, boss, 2.2, 6.4, 0.3, 1.4); cam.wantFov = 54; }
        else { sideView(player, boss, 2.6, 8.2, -0.5 + Math.sin(t * 0.4) * 0.2, 1.3); cam.wantFov = 54; }
      },
      camLambda: 5,
      hud: function (c, t) {
        H.layout({ boss: true, bottom: true, card: true, subs: true, f3: true });
        baseHud(true, st.hp, st.bossHp, false);
        cardBruiser(false);
        H.action(st.act || "", "tp");
      }
    };

    /* ---------------------------------------------------------------- 5 · disengage and zoning */
    demos.zoning = {
      period: 14, still: 5.5,
      enter: function (c) {
        boss.setPos(0, -2); boss.yaw = Math.PI; boss.faceVel = false;
        player.setPos(0.4, 7.5); player.equip("sword", "shield"); player.hold("block"); player.faceVel = false;
        st.act = TP.t("act_wait"); st.tele = null; st.from = null;
      },
      update: function (c, t, dt) {
        if (t < 2.6) { var k = between(t, 0, 2.6); player.setPos(0.4, 7.5 - 5.0 * k); }
        player.look(boss.pos); player.yaw = A.dampAngle(player.yaw, yawTo(player.pos, boss.pos), 10, dt);
        boss.look(player.pos); boss.yaw = A.dampAngle(boss.yaw, yawTo(boss.pos, player.pos), 10, dt);
        if (c.once("read", 0.9)) { st.act = TP.t("act_shieldFull"); }
        if (t >= 2.0 && t < 3.3) { var kb = between(t, 2.0, 3.2); boss.setPos(-1.6 * TP.smooth(kb), -2 - 4.5 * TP.smooth(kb)); if (c.once("zone", 2.4)) { st.act = TP.t("act_zone"); } }
        if (t > 2.6 && t < 5.0) { player.setPos(0.4, 2.5 - 1.2 * between(t, 2.6, 5.0)); }
        if (c.once("cast1", 3.3)) { boss.act("cast", 0.9); }
        if (c.once("cast1b", 3.8)) { fx.shoot("bolt", boss.handWorld(), function () { return V(player.pos.x, 1.1, player.pos.z); }, 0.55, function (p) { fx.sparkWhite(p, 30); fx.soulCyan(p, 20); H.sub(TP.t("sub_block")); }); H.sub(TP.t("sub_bolt")); }
        if (c.once("tele", 4.3)) { st.tele = fx.telegraph(V(player.pos.x, 0, player.pos.z), 2.4, 0xff3b4a); boss.act("cast", 1.3); }
        if (st.tele) { st.tele.set(between(t, 4.3, 5.5)); }
        if (c.once("roll", 5.0)) { player.release(); player.act("roll", 0.6); st.rollFrom = player.pos.clone(); }
        if (t >= 5.0 && t < 5.7) { var kr = TP.easeOut(between(t, 5.0, 5.6)); player.setPos(st.rollFrom.x + 3.2 * kr, st.rollFrom.z + 0.6 * kr); }
        if (c.once("area", 5.5)) { var p = V(st.tele ? 0.4 : 0, 0, 1.3); st.tele.off(); st.tele = null; fx.column(V(0.4, 0, 1.3), { color: 0xff3b4a, height: 6, width: 2.4, dur: 0.9 }); fx.shock(V(0.4, 0, 1.3), 3, 0xff3b4a, 0.5); fx.hitRed(V(0.4, 0.8, 1.3), 60); fx.light(V(0.4, 0, 1.3), 0xff3b4a, 50, 0.4); fx.shake(0.12, 0.3); H.sub(TP.t("sub_area")); H.announce(TP.t("ann_zone"), "zone"); void p; }
        if (c.once("down", 5.8)) { st.act = TP.t("act_shieldDown"); }
        if (c.once("leap", 6.0)) { boss.act("leap", 1.2); st.from = boss.pos.clone(); H.sub(TP.t("sub_leap")); }
        if (t >= 6.0 && t < 7.2) {
          var kk = between(t, 6.15, 7.1);
          var dx = player.pos.x - st.from.x, dz = player.pos.z - st.from.z, len = Math.sqrt(dx * dx + dz * dz);
          boss.setPos(st.from.x + (dx - dx / len * 2.0) * kk, st.from.z + (dz - dz / len * 2.0) * kk);
          boss.air = Math.sin(Math.PI * kk) * 2.8;
        }
        if (c.once("landh", 7.12)) { boss.air = 0; fx.shock(boss.pos, 3.5, 0xff6a2a, 0.5); fx.dust(V(boss.pos.x, 0.2, boss.pos.z), 40); fx.shake(0.14, 0.3); }
        if (c.once("sw", 7.3)) { boss.act("sweep", 0.65); }
        if (c.once("swh", 7.65)) { c.hit("player", 7); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.4, gain: 3.4 }); fx.hitRed(V(player.pos.x, 1.2, player.pos.z), 70); fx.light(player.pos, 0xff3b4a, 40, 0.35); player.act("stagger", 1.0); H.sub(TP.t("sub_hit")); st.act = TP.t("act_punishWin"); }
        if (t > 8.6 && t < 14) {
          var kq = between(t, 8.6, 10.6);
          player.setPos(player.pos.x + (0.4 - player.pos.x) * 0.03, player.pos.z + (7.5 - player.pos.z) * 0.03);
          boss.setPos(boss.pos.x + (0 - boss.pos.x) * 0.02, boss.pos.z + (-2 - boss.pos.z) * 0.02);
          if (c.once("reblock", 9.6)) { player.hold("block"); st.act = TP.t("act_reset"); }
          if (c.once("rw", 12)) { st.act = TP.t("act_wait"); }
          void kq;
        }
      },
      camera: function (c, t) {
        if (t < 2.2) { thirdPerson(player, boss, 3.2, 2.1, 0.9, 1.5); cam.wantFov = 64; }
        else if (t < 8.6) { sideView(player, boss, 3.6, 10.5, 0.9, 1.4); cam.wantFov = 52; }
        else { thirdPerson(player, boss, 3.4, 2.2, 0.9, 1.5); cam.wantFov = 64; }
      },
      camLambda: 5,
      hud: function (c, t) {
        H.layout({ boss: true, bottom: true, card: true, subs: true, f3: true });
        baseHud(true, st.hp, st.bossHp, false);
        cardBruiser(false);
        H.action(st.act || "", "tp");
      }
    };

    /* ---------------------------------------------------------------- 6 · chess-like prediction, with the real predictor */
    (function () {
      var SEQ = ["STRAFE_LEFT", "STRAFE_LEFT", "STEP_OUT"];
      var C = { x: 0, z: -2 };
      /* each action lasts STEP seconds; the predictor (forgetting 0.98, MIN_COUNT 2) trusts a context after 3 looks, so it learns over 4 cycles (12 actions) and then acts ahead */
      var STEP = 0.72, LEARN = 12, T0 = STEP * LEARN;
      /* you start at angle 0.2, radius 6.5; strafes turn by 0.5 rad, step out adds radius then comes back in over the next strafes */
      function stepPos(i, k, out) {
        /* i: action index (0..), k: 0..1 within the action */
        var a = 0.2, r = 6.5;
        for (var j = 0; j < i; j++) { var s = SEQ[j % 3]; if (s === "STRAFE_LEFT") { a += 0.5; r -= 0.4; } else { r += 1.4; a -= 0.2; } }
        var s2 = SEQ[i % 3];
        var a2 = a, r2 = r;
        if (s2 === "STRAFE_LEFT") { a2 = a + 0.5 * k; r2 = r - 0.4 * k; } else { r2 = r + 1.4 * TP.easeOut(k); a2 = a - 0.2 * k; }
        out.x = C.x + Math.sin(a2) * r2; out.z = C.z + Math.cos(a2) * r2;
        return out;
      }
      var predictor = null;
      function lines(n) {
        /* the top forecasts of your next move from the real predictor, and the answer prepared for each */
        var dist = predictor.distribution();
        var out = [];
        var probs = dist.probs.slice(0, 3);
        probs.forEach(function (p, i) { var name = TP.ACTIONS[p.action]; out.push({ n: String(i + 1), you: TP.t("m_" + name), boss: TP.t("r_" + name), p: "p " + TP.num(p.p, 2), best: i === 0, live: false }); });
        if (!probs.length) { for (var j = 0; j < Math.min(3, n); j++) { out.push({ n: String(j + 1), you: "…", boss: "…", p: "p —", best: false, live: false }); } }
        /* look-ahead line: what follows the most likely move, with a copy of the predictor */
        if (probs.length) {
          var copy = predictor.clone();
          copy.update(probs[0].action, copy.predict(), TP.FORGETTING);
          var d2 = copy.distribution();
          if (d2.probs.length) { var nm = TP.ACTIONS[d2.probs[0].action]; out.push({ n: "+1", you: TP.t("m_" + nm), boss: TP.t("r_" + nm), p: "p " + TP.num(d2.probs[0].p, 2), best: false, live: true }); }
        }
        return out;
      }
      demos.chess = {
        period: 15, still: T0 + 2.6,
        enter: function (c) {
          predictor = new TP.ActionPredictor();
          boss.setPos(C.x, C.z); boss.yaw = 0; boss.faceVel = false;
          player.equip("sword", "shield"); player.faceVel = false;
          stepPos(0, 0, pv); player.setPos(pv.x, pv.z);
          st.act = TP.t("act_learn", { n: 0 }); st.learned = 0; st.lines = [];
        },
        update: function (c, t, dt) {
          var i = Math.floor(t / STEP), k = t / STEP - i;
          var tLeap = T0 + 2 * STEP, tLand = tLeap + 0.95;
          if (t < tLand) { stepPos(i, k, pv); player.setPos(pv.x, pv.z); }
          player.look(boss.pos); player.yaw = A.dampAngle(player.yaw, yawTo(player.pos, boss.pos), 10, dt);
          boss.look(player.pos);
          if (!(t >= tLeap && t < tLand)) { boss.yaw = A.dampAngle(boss.yaw, yawTo(boss.pos, player.pos), 8, dt); }
          /* every completed action feeds the real predictor (copy of ActionPredictor) */
          for (var j = 1; j <= LEARN + 2; j++) {
            if (c.once("learn" + j, j * STEP)) {
              var name = SEQ[(j - 1) % 3];
              predictor.update(TP.actionIndex(name), predictor.predict(), TP.FORGETTING);
              st.learned = j;
              st.lines = lines(3);
              st.act = j < LEARN ? TP.t("act_learn", { n: Math.min(3, Math.ceil(j / 4)) }) : TP.t("act_pred", { k: 2 });
            }
          }
          /* the mind draws your next two positions */
          if (t > 2 && t < tLand) {
            var pts = [];
            for (var q = 1; q <= 2; q++) { stepPos(i + q, 1, bv); pts.push({ x: bv.x, z: bv.z }); }
            fx.path(pts, t >= T0 ? 0xff3b4a : 0x59e1ff);
            fx.sight(t >= T0 - 2 * STEP, boss.eyeWorld(tmpP), V(pts[0].x, 0.1, pts[0].z));
          } else { fx.path([]); fx.sight(false); }
          /* after the learning, the boss acts ahead of you: lead bolts on the strafes, a leap on the step back */
          if (c.once("b1", T0)) { boss.act("cast", 0.7); }
          if (c.once("b1b", T0 + 0.3)) { stepPos(LEARN, 1, bv); var tgt1 = V(bv.x, 1.1, bv.z); fx.shoot("bolt", boss.handWorld(), tgt1, STEP - 0.3, function (p) { c.hit("player", 4); player.act("hurt", 0.5); fx.hitRed(p, 30); H.sub(TP.t("sub_hit")); H.announce(TP.t("ann_pred"), "pred"); }); fx.cross(true, bv, 0xff3b4a); H.sub(TP.t("sub_bolt")); }
          if (c.once("b2", T0 + STEP)) { boss.act("cast", 0.7); }
          if (c.once("b2b", T0 + STEP + 0.3)) { stepPos(LEARN + 1, 1, bv); var tgt2 = V(bv.x, 1.1, bv.z); fx.shoot("bolt", boss.handWorld(), tgt2, STEP - 0.3, function (p) { c.hit("player", 4); player.act("hurt", 0.5); fx.hitRed(p, 30); H.sub(TP.t("sub_hit")); }); fx.cross(true, bv, 0xff3b4a); H.sub(TP.t("sub_bolt")); }
          if (c.once("b3", tLeap)) { boss.act("leap", 1.0); st.from = boss.pos.clone(); H.sub(TP.t("sub_leap")); stepPos(LEARN + 2, 1, bv); st.landAt = { x: bv.x, z: bv.z }; fx.cross(true, bv, 0xff3b4a); }
          if (t >= tLeap && t < tLand) {
            var kk = between(t, tLeap + 0.1, tLand - 0.05);
            var dx = st.landAt.x - st.from.x, dz = st.landAt.z - st.from.z, len = Math.sqrt(dx * dx + dz * dz);
            boss.setPos(st.from.x + (dx - dx / len * 1.9) * kk, st.from.z + (dz - dz / len * 1.9) * kk);
            boss.air = Math.sin(Math.PI * kk) * 3.0;
            boss.yaw = Math.atan2(dx, dz);
          }
          if (c.once("land", tLand)) { boss.air = 0; fx.shock(boss.pos, 3.5, 0xff6a2a, 0.5); fx.dust(V(boss.pos.x, 0.2, boss.pos.z), 40); fx.shake(0.14, 0.3); fx.cross(false); }
          if (c.once("sw", tLand + 0.2)) { boss.act("sweep", 0.65); }
          if (c.once("swh", tLand + 0.55)) { c.hit("player", 7); fx.slash(V(boss.pos.x, 1.7, boss.pos.z), boss.yaw, { radius: 2.4, gain: 3.4 }); fx.hitRed(V(player.pos.x, 1.2, player.pos.z), 70); fx.light(player.pos, 0xff3b4a, 40, 0.35); player.act("stagger", 1.1); H.announce(TP.t("ann_pred"), "pred"); H.sub(TP.t("sub_hit")); }
          if (t > tLand + 1.5) { boss.setPos(boss.pos.x + (C.x - boss.pos.x) * 0.02, boss.pos.z + (C.z - boss.pos.z) * 0.02); stepPos(0, 0, bv); player.setPos(player.pos.x + (bv.x - player.pos.x) * 0.03, player.pos.z + (bv.z - player.pos.z) * 0.03); if (c.once("reset", tLand + 2.5)) { st.act = TP.t("act_reset"); } }
        },
        camera: function (c, t) {
          /* high behind the boss, looking over its shoulder at you and the markers on the floor */
          var sw = 0.35 + Math.sin(t * 0.22) * 0.12;
          if (t < T0 - 1.5 || t > T0 + 5.2) { sideView(player, boss, 3.4, 9.0, sw, 1.2); cam.wantFov = 52; }
          else { sideView(player, boss, 3.0, 8.0, sw, 1.3); cam.wantFov = 54; }
        },
        camLambda: 4.5,
        hud: function (c, t) {
          H.layout({ boss: true, bottom: true, card: true, subs: true, f3: true, mind: t > 1.2 });
          baseHud(true, st.hp, st.bossHp, false);
          cardBruiser(false);
          H.action(st.act || "", "tp");
          if (st.lastLines !== st.lines || st.lastLang !== TP.lang) { st.lastLines = st.lines; st.lastLang = TP.lang; H.mind(TP.t("mind_title"), st.lines.length ? st.lines : lines(3), TP.t("mind_foot")); }
        }
      };
    })();

    /* ---------------------------------------------------------------- 7 · finale: close-up */
    demos.finale = {
      period: 12, still: 6,
      enter: function (c) {
        boss.setPos(0, -9); boss.yaw = 0; boss.faceVel = false;
        player.setPos(0, 30); player.root.visible = false;
        cam.hand = 0.5;
      },
      update: function (c, t, dt) {
        var k = between(t, 0, 10);
        boss.setPos(0, -9 + 6.5 * k);
        boss.look(V(0, 1.6, 8));
        boss.setEyes(2.4 + Math.sin(t * 2.5) * 0.4);
        if (t > 10) { boss.hold("hold"); }
        if (c.once("emb", 1)) { fx.fireOrange(V(0, 0.3, -8.5), 20); }
      },
      camera: function (c, t) {
        var k = between(t, 0, 12);
        setCam(V(1.4 - 0.9 * k, 3.1 - 0.2 * k, boss.pos.z + 5.2 - 1.6 * k), V(boss.pos.x, 2.95, boss.pos.z), 44 - 8 * k);
      },
      camLambda: 3,
      hud: function (c, t) {
        H.layout({});
      }
    };

    /* restore the player when leaving the finale */
    E.on("section", function (s) { if (s && s.key !== "finale") { player.root.visible = true; } });
    return self;
  });
})();
