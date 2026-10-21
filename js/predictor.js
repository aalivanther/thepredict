/* Copia exacta en JavaScript de akyrion.thepredict.domain.ActionPredictor (M26.3): predictor de orden variable, como
   mucho de orden 2. Las filas son Float32Array, igual que los float[] de Java: el producto por el olvido se redondea a
   float al guardarse y la suma de 1 también, así que los números coinciden con los del motor. Las constantes
   (MIN_COUNT, ACCURACY_ALPHA, olvido, precisión mínima y contextos de orden 2) llegan de data/content.js, que las
   lee del código y de config.yml; los valores de reserva son los mismos. */
(function () {
  "use strict";
  var TP = window.TP;
  var P = (TP.C && TP.C.predictor) || {};
  var ACTIONS = P.actions || ["IDLE", "STRIKE", "STRIKE_EARLY", "CRIT_STRIKE", "SPRINT_STRIKE", "SHIELD_UP", "SHIELD_DOWN",
    "STRAFE_LEFT", "STRAFE_RIGHT", "STEP_IN", "STEP_OUT", "JUMP", "SPRINT_RESET", "DRAW", "RELEASE", "CONSUME"];
  var COUNT = ACTIONS.length;
  var MIN_COUNT = P.MIN_COUNT || 2.0;
  var ACCURACY_ALPHA = P.ACCURACY_ALPHA || 0.05;

  function ActionPredictor(order2Contexts) {
    var capacity = order2Contexts === undefined ? (P.order2Contexts || 32) : order2Contexts;
    if (capacity < 0 || capacity > 64) { throw new Error("order2Contexts must be within [0, 64]"); }
    this.capacity = capacity;
    this.order1 = [];
    for (var a = 0; a < COUNT; a++) { this.order1.push(new Float32Array(COUNT)); }
    this.contextKey = new Int32Array(capacity);
    this.order2 = [];
    for (var s = 0; s < capacity; s++) { this.contextKey[s] = -1; this.order2.push(new Float32Array(COUNT)); }
    this.previous = -1;
    this.before = -1;
    this.evictions = 0;
    this.accuracyValue = 0;
  }

  function key(older, newer) { return older | (newer << 4); }
  function total(row) { var sum = 0; for (var i = 0; i < row.length; i++) { sum += row[i]; } return sum; }
  function argMax(row) { var best = 0; for (var i = 1; i < row.length; i++) { if (row[i] > row[best]) { best = i; } } return best; }
  function decayAndAdd(row, action, forgetting) {
    if (forgetting < 1.0) { for (var i = 0; i < row.length; i++) { row[i] = row[i] * forgetting; } }
    row[action] = row[action] + 1.0;
  }

  ActionPredictor.prototype.find = function (k) {
    for (var slot = 0; slot < this.capacity; slot++) { if (this.contextKey[slot] === k) { return slot; } }
    return -1;
  };
  ActionPredictor.prototype.freeOrEvict = function () {
    var lightest = 0, light = Number.MAX_VALUE;
    for (var slot = 0; slot < this.capacity; slot++) {
      if (this.contextKey[slot] < 0) { return slot; }
      var mass = total(this.order2[slot]);
      if (mass < light) { light = mass; lightest = slot; }
    }
    return lightest;
  };
  /* The row predict() and forecast() read, and which order it is: 2, 1 or 0 (no trusted row). */
  ActionPredictor.prototype.row = function () {
    if (this.previous >= 0 && this.before >= 0) {
      var slot = this.find(key(this.before, this.previous));
      if (slot >= 0 && total(this.order2[slot]) >= MIN_COUNT) { return { row: this.order2[slot], order: 2 }; }
    }
    if (this.previous >= 0 && total(this.order1[this.previous]) >= MIN_COUNT) { return { row: this.order1[this.previous], order: 1 }; }
    return { row: null, order: 0 };
  };
  ActionPredictor.prototype.predict = function () {
    var r = this.row();
    return r.row ? argMax(r.row) : 0;
  };
  ActionPredictor.prototype.update = function (action, predicted, forgetting) {
    var hit = action === predicted ? 1.0 : 0.0;
    this.accuracyValue = (1.0 - ACCURACY_ALPHA) * this.accuracyValue + ACCURACY_ALPHA * hit;
    if (this.previous >= 0) { decayAndAdd(this.order1[this.previous], action, forgetting); }
    if (this.previous >= 0 && this.before >= 0 && this.capacity > 0) { this.learnOrder2(key(this.before, this.previous), action, forgetting); }
    this.before = this.previous;
    this.previous = action;
  };
  ActionPredictor.prototype.learnOrder2 = function (k, action, forgetting) {
    var slot = this.find(k);
    if (slot < 0) {
      slot = this.freeOrEvict();
      if (this.contextKey[slot] >= 0) { this.evictions++; }
      this.contextKey[slot] = k;
    }
    decayAndAdd(this.order2[slot], action, forgetting);
  };
  ActionPredictor.prototype.accuracy = function () { return this.accuracyValue; };
  /* Same rule as ActionPredictor.forecast: the order-2 row when trusted, else the order-1 row; permille >= 500. */
  ActionPredictor.prototype.forecast = function (minAccuracy) {
    if (this.accuracyValue < minAccuracy || this.previous < 0) { return { action: 0, permille: 0 }; }
    var row = this.order1[this.previous];
    if (this.before >= 0) {
      var slot = this.find(key(this.before, this.previous));
      if (slot >= 0 && total(this.order2[slot]) >= MIN_COUNT) { row = this.order2[slot]; }
    }
    var mass = total(row);
    if (mass < MIN_COUNT) { return { action: 0, permille: 0 }; }
    var choice = argMax(row);
    var permille = Math.floor(1000.0 * row[choice] / mass);
    return permille >= 500 ? { action: choice, permille: permille } : { action: 0, permille: 0 };
  };
  /* Page helpers, not part of the engine: the distribution of the row in use, and a copy for look-ahead. */
  ActionPredictor.prototype.distribution = function () {
    var r = this.row();
    if (!r.row) { return { order: 0, mass: 0, probs: [] }; }
    var mass = total(r.row);
    var probs = [];
    for (var i = 0; i < COUNT; i++) { if (r.row[i] > 0) { probs.push({ action: i, p: r.row[i] / mass }); } }
    probs.sort(function (a, b) { return b.p - a.p || a.action - b.action; });
    return { order: r.order, mass: mass, probs: probs };
  };
  ActionPredictor.prototype.clone = function () {
    var copy = new ActionPredictor(this.capacity);
    for (var a = 0; a < COUNT; a++) { copy.order1[a].set(this.order1[a]); }
    for (var s = 0; s < this.capacity; s++) { copy.order2[s].set(this.order2[s]); copy.contextKey[s] = this.contextKey[s]; }
    copy.previous = this.previous;
    copy.before = this.before;
    copy.evictions = this.evictions;
    copy.accuracyValue = this.accuracyValue;
    return copy;
  };

  TP.ActionPredictor = ActionPredictor;
  TP.ACTIONS = ACTIONS;
  TP.actionIndex = function (name) { return ACTIONS.indexOf(name); };
  TP.FORGETTING = P.forgetting || 0.98;
  TP.MIN_ACCURACY = P.minAccuracy || 0.4;

  TP.addText({
    actIDLE: "esperar", actSTRIKE: "golpe", actSTRIKE_EARLY: "golpe sin recarga", actCRIT_STRIKE: "crítico", actSPRINT_STRIKE: "golpe a la carrera",
    actSHIELD_UP: "subir escudo", actSHIELD_DOWN: "bajar escudo", actSTRAFE_LEFT: "lateral izquierdo", actSTRAFE_RIGHT: "lateral derecho",
    actSTEP_IN: "acercarse", actSTEP_OUT: "alejarse", actJUMP: "salto", actSPRINT_RESET: "volver a correr", actDRAW: "tensar el arco",
    actRELEASE: "disparar", actCONSUME: "beber o comer"
  }, {
    actIDLE: "wait", actSTRIKE: "strike", actSTRIKE_EARLY: "early strike", actCRIT_STRIKE: "critical", actSPRINT_STRIKE: "sprint strike",
    actSHIELD_UP: "shield up", actSHIELD_DOWN: "shield down", actSTRAFE_LEFT: "left strafe", actSTRAFE_RIGHT: "right strafe",
    actSTEP_IN: "step in", actSTEP_OUT: "step out", actJUMP: "jump", actSPRINT_RESET: "sprint again", actDRAW: "draw bow",
    actRELEASE: "release", actCONSUME: "eat or drink"
  });
  TP.actionName = function (index) { return TP.t("act" + ACTIONS[index]); };
})();
