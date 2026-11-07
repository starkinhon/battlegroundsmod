// Shared math for the sword animations: same transforms as Minecraft (model space: pixels, y down, front = -z).
// Works in node (generator) and in the browser (preview). Needs THREE.
(function (root) {
  const THREE = root.THREE || require('three');
  const D = Math.PI / 180;

  // Right arm frame -> where ItemInHandLayer calls renderItem (the wrist: our extra rotation goes here).
  function handFromArm() {
    return new THREE.Matrix4().makeRotationX(-90 * D)
      .multiply(new THREE.Matrix4().makeRotationY(180 * D))
      .multiply(new THREE.Matrix4().makeTranslation(1 / 16, 0.125, -0.625));
  }
  // Wrist -> the sword's own space (display thirdperson_righthand + AizenSwordRenderer third person).
  function swordFromHand() {
    const m = new THREE.Matrix4();
    const mul = (x) => m.multiply(x);
    mul(new THREE.Matrix4().makeTranslation(0, 4 / 16, 0.5 / 16));
    mul(new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(0, -90 * D, 55 * D, 'XYZ')));
    mul(new THREE.Matrix4().makeScale(0.85, 0.85, 0.85));
    mul(new THREE.Matrix4().makeTranslation(-0.5, -0.5, -0.5));
    mul(new THREE.Matrix4().makeTranslation(3 / 16, 3 / 16, 0.5));
    mul(new THREE.Matrix4().makeRotationZ(-0.62));
    mul(new THREE.Matrix4().makeRotationZ(-Math.PI / 4));
    mul(new THREE.Matrix4().makeRotationY(Math.PI));
    mul(new THREE.Matrix4().makeScale(1.15 / 16, 1.15 / 16, 1.15 / 16));
    mul(new THREE.Matrix4().makeTranslation(0, 2.2, 0));
    return m;
  }
  function wristMatrix(w) {
    return new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(w[0] * D, w[1] * D, w[2] * D, 'ZYX'));
  }
  function swordFromArm(w) {
    return handFromArm().multiply(wristMatrix(w || [0, 0, 0])).multiply(swordFromHand());
  }

  function ease(kind, u) {
    switch (kind) {
      case 'in': return u * u * u;
      case 'out': return 1 - Math.pow(1 - u, 3);
      case 'snap': return 1 - Math.pow(1 - u, 5);
      case 'linear': return u;
      default: return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    }
  }

  const ZERO = { lean: 0, twist: 0, drop: 0, fwd: 0, side: 0, head: { yaw: 0, pitch: 0 },
    r: { yaw: 0, pitch: 0, roll: 0, tilt: 0 }, l: { yaw: 0, pitch: 0, roll: 0, tilt: 0 }, arm: null, larm: null, blade: null, grip: 0,
    rl: { pitch: 0, out: 0 }, ll: { pitch: 0, out: 0 } };

  function fill(p) {
    const o = JSON.parse(JSON.stringify(ZERO));
    for (const k in p) {
      if (p[k] && typeof p[k] === 'object' && !Array.isArray(p[k]) && o[k]) Object.assign(o[k], p[k]); else o[k] = p[k];
    }
    return o;
  }

  function lerpPose(a, b, u) {
    const out = {};
    for (const k in a) {
      if (a[k] === null || b[k] === null) { out[k] = u < 0.5 ? a[k] : b[k]; }
      else if (typeof a[k] === 'object') { out[k] = {}; for (const j in a[k]) out[k][j] = a[k][j] + (b[k][j] - a[k][j]) * u; }
      else out[k] = a[k] + (b[k] - a[k]) * u;
    }
    return out;
  }

  // keys: [{t (ticks), ease (into this key), ...pose}]
  function poseAt(keys, t) {
    const ks = keys.map((k) => ({ t: k.t, ease: k.ease, p: fill(k) }));
    if (t <= ks[0].t) return ks[0].p;
    for (let i = 0; i < ks.length - 1; i++) {
      const a = ks[i], b = ks[i + 1];
      if (t <= b.t) return lerpPose(a.p, b.p, ease(b.ease, (t - a.t) / Math.max(1e-6, b.t - a.t)));
    }
    return ks[ks.length - 1].p;
  }

  function q(yaw, pitch, roll, tilt) {
    // arm hanging down (+y); roll about its own axis, then raise (pitch, -90 = forward), then swing round (yaw, + = right)
    return new THREE.Quaternion().setFromEuler(new THREE.Euler(0, yaw * D, 0))
      .multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(0, 0, (tilt || 0) * D)))
      .multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(pitch * D, 0, 0)))
      .multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(0, roll * D, 0)));
  }

  // Direction in model space: yaw + = right, pitch + = up, 0/0 = forward.
  function dir(yaw, pitch) {
    return new THREE.Vector3(-Math.sin(yaw * D) * Math.cos(pitch * D), -Math.sin(pitch * D), -Math.cos(yaw * D) * Math.cos(pitch * D));
  }
  const HAND_ROT = () => new THREE.Quaternion().setFromRotationMatrix(handFromArm());
  let BL = null, EL = null;
  // The blade's direction (and its edge) in the wrist frame, before any wrist turn.
  function restBlade() {
    if (!BL) {
      const m = swordFromHand();
      const o = new THREE.Vector3(0, 1.2, 0).applyMatrix4(m);
      BL = new THREE.Vector3(0, 21, 0).applyMatrix4(m).sub(o).normalize();
      EL = new THREE.Vector3(1, 1.2, 0).applyMatrix4(m).sub(o).normalize();
    }
  }
  // Wrist turn so the blade points along want (model space) for an arm turned armQ (model space); edge: turn about
  // the blade, degrees.
  function wrist(armQ, want, edge) {
    restBlade();
    const frame = armQ.clone().multiply(HAND_ROT());
    const local = want.clone().normalize().applyQuaternion(frame.clone().invert());
    const w = new THREE.Quaternion().setFromUnitVectors(BL, local);
    if (edge) w.premultiply(new THREE.Quaternion().setFromAxisAngle(local, edge * D));
    const e = new THREE.Euler().setFromQuaternion(w, 'ZYX');
    return [e.x / D, e.y / D, e.z / D];
  }

  // Full model pose (parts in Minecraft's terms: pivot in px, rotation as ZYX euler radians).
  function solve(p) {
    const twist = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, p.twist * D, 0));
    const lean = new THREE.Quaternion().setFromEuler(new THREE.Euler(p.lean * D, 0, 0));
    const rb = twist.clone().multiply(lean);
    const hip = new THREE.Vector3(p.side, 12 + p.drop, -p.fwd);
    const B = (v) => v.clone().sub(new THREE.Vector3(0, 12, 0)).applyQuaternion(rb).add(hip);
    const parts = {};
    const put = (name, pos, quat) => {
      const e = new THREE.Euler().setFromQuaternion(quat, 'ZYX');
      parts[name] = { pos: pos, rot: [e.x, e.y, e.z], quat: quat };
    };
    const neck = B(new THREE.Vector3(0, 0, 0));
    put('body', neck, rb.clone());
    // the head keeps looking ahead (undoes the twist) plus its own look
    const head = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, p.head.yaw * D, 0))
      .multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(-p.head.pitch * D, 0, 0)));
    put('head', neck, head);
    const rs = B(new THREE.Vector3(-5, 2, 0));
    const ls = B(new THREE.Vector3(5, 2, 0));
    const qr = rb.clone().multiply(p.arm
      ? new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir(p.arm.yaw, p.arm.pitch).normalize())
        .multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(0, (p.arm.roll || 0) * D, 0)))
      : q(p.r.yaw, p.r.pitch, p.r.roll, p.r.tilt));
    parts.item = { wrist: p.blade ? wrist(qr, dir(p.blade.yaw, p.blade.pitch).applyQuaternion(rb), p.blade.edge || 0) : [0, 0, 0] };
    put('rightArm', rs, qr);
    // larm: like arm, for the left arm, mirrored (yaw + = out to his left, pitch + = up, roll mirrored too)
    let ql = rb.clone().multiply(p.larm
      ? new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir(-p.larm.yaw, p.larm.pitch).normalize())
        .multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(0, -(p.larm.roll || 0) * D, 0)))
      : q(-p.l.yaw, p.l.pitch, -p.l.roll, -p.l.tilt));
    if (p.grip > 0) {
      // two hands: the left arm reaches for the right fist (just under the right hand on the grip)
      const fist = new THREE.Vector3(0, 9.5, 0).applyQuaternion(qr).add(rs);
      const dir = fist.clone().sub(ls).normalize();
      const reach = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir);
      ql = ql.clone().slerp(reach, p.grip);
    }
    put('leftArm', ls, ql);
    const leg = (L, s) => new THREE.Quaternion().setFromEuler(new THREE.Euler(-L.pitch * D, 0, s * L.out * D, 'ZYX'));
    // legs only turn half with the chest
    for (const [n, L, s, x] of [['rightLeg', p.rl, 1, -1.9], ['leftLeg', p.ll, -1, 1.9]]) {
      const half = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, p.twist * 0.35 * D, 0));
      const pos = new THREE.Vector3(x, 0, 0).applyQuaternion(half).add(new THREE.Vector3(p.side, 12 + p.drop, -p.fwd));
      put(n, pos, half.multiply(leg(L, s)));
    }
    return parts;
  }

  const DEFAULT_POS = { head: [0, 0, 0], body: [0, 0, 0], rightArm: [-5, 2, 0], leftArm: [5, 2, 0], rightLeg: [-1.9, 12, 0], leftLeg: [1.9, 12, 0] };
  const BONE = { item: 'Item', head: 'Head', body: 'Body', rightArm: 'Right Arm', leftArm: 'Left Arm', rightLeg: 'Right Leg', leftLeg: 'Left Leg' };

  // Bakes high level keys into the editor's JSON (x and y flipped, as KeyframeAnimation reads it).
  function bake(keys, step) {
    const end = keys[keys.length - 1].t;
    const chans = {};
    const last = {};
    for (let t = 0; t <= end + 1e-6; t += step) {
      const parts = solve(poseAt(keys, t));
      {
        let r = parts.item.wrist.slice();
        if (last.item) r = r.map((v, i) => { let x = v; while (x - last.item[i] > 180) x -= 360; while (x - last.item[i] < -180) x += 360; return x; });
        last.item = r;
        (chans['item:rotation'] = chans['item:rotation'] || []).push({ timestamp: +(t / 20).toFixed(4), target: r.map((v) => +v.toFixed(2)), interpolation: 'linear' });
      }
      for (const n in parts) {
        if (n === 'item') continue;
        const P = parts[n];
        let r = [-P.rot[0] / D, -P.rot[1] / D, P.rot[2] / D];
        if (last[n]) r = r.map((v, i) => { let x = v; while (x - last[n][i] > 180) x -= 360; while (x - last[n][i] < -180) x += 360; return x; });
        last[n] = r;
        const d = DEFAULT_POS[n];
        const pos = [-(P.pos.x - d[0]), -(P.pos.y - d[1]), P.pos.z - d[2]];
        (chans[n + ':rotation'] = chans[n + ':rotation'] || []).push({ timestamp: +(t / 20).toFixed(4), target: r.map((v) => +v.toFixed(2)), interpolation: 'linear' });
        (chans[n + ':position'] = chans[n + ':position'] || []).push({ timestamp: +(t / 20).toFixed(4), target: pos.map((v) => +v.toFixed(3)), interpolation: 'linear' });
      }
    }
    const animations = Object.keys(chans).map((k) => ({ bone: BONE[k.split(':')[0]], target: k.split(':')[1], keyframes: chans[k] }));
    return { length: +(end / 20).toFixed(4), animations };
  }

  // Reads a baked JSON back like KeyframeAnimation.apply after standStill: parts with pos (px) and ZYX euler.
  function sampleJson(json, seconds) {
    const parts = {};
    for (const n in DEFAULT_POS) parts[n] = { pos: new THREE.Vector3(...DEFAULT_POS[n]), rot: [0, 0, 0] };
    parts.item = { wrist: [0, 0, 0] };
    const inv = {}; for (const k in BONE) inv[BONE[k]] = k;
    for (const a of json.animations) {
      const ks = a.keyframes; let v;
      if (seconds <= ks[0].timestamp) v = ks[0].target;
      else if (seconds >= ks[ks.length - 1].timestamp) v = ks[ks.length - 1].target;
      else { let i = 0; while (i < ks.length - 2 && ks[i + 1].timestamp <= seconds) i++; const u = (seconds - ks[i].timestamp) / (ks[i + 1].timestamp - ks[i].timestamp); v = ks[i].target.map((x, j) => x + (ks[i + 1].target[j] - x) * u); }
      if (a.bone === 'Item') { parts.item.wrist = v.slice(); continue; }
      const P = parts[inv[a.bone]];
      if (a.target === 'position') { P.pos.x -= v[0]; P.pos.y -= v[1]; P.pos.z += v[2]; }
      else { P.rot[0] -= v[0] * D; P.rot[1] -= v[1] * D; P.rot[2] += v[2] * D; }
    }
    return parts;
  }

  // World (blocks, player at origin facing +z) <- model space, as LivingEntityRenderer/PlayerRenderer do it.
  function rootMatrix() {
    return new THREE.Matrix4().makeRotationY(Math.PI)
      .multiply(new THREE.Matrix4().makeScale(-1, -1, 1))
      .multiply(new THREE.Matrix4().makeScale(0.9375, 0.9375, 0.9375))
      .multiply(new THREE.Matrix4().makeTranslation(0, -1.501, 0));
  }

  function partMatrix(P) {
    return new THREE.Matrix4().makeTranslation(P.pos.x / 16, P.pos.y / 16, P.pos.z / 16)
      .multiply(new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(P.rot[0], P.rot[1], P.rot[2], 'ZYX')));
  }

  // Blade base and tip in world space for a sampled pose.
  function blade(parts) {
    const m = rootMatrix().multiply(partMatrix(parts.rightArm)).multiply(swordFromArm(parts.item.wrist));
    return { base: new THREE.Vector3(0, 1.2, 0).applyMatrix4(m), tip: new THREE.Vector3(0, 21, 0).applyMatrix4(m), m };
  }

  const api = { dir, wrist, wristMatrix, THREE, D, swordFromArm, poseAt, solve, bake, sampleJson, rootMatrix, partMatrix, blade, fill };
  if (typeof module !== 'undefined') module.exports = api; else root.Rig = api;
})(typeof window !== 'undefined' ? window : globalThis);
