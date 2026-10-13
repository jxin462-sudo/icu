/* ==========================================================================
   thermal.js · 红外热图区（状态页 · 摄像头下方）
   --------------------------------------------------------------------------
   链路：LSM164X 探头 →(串口460800)→ PC 端 lsm_ble_bridge →(BLE GATT)→ 平板
        → MainActivity.onThermalFrame → evaluateJavascript → window.IcuThermal

   对外暴露：
     window.IcuThermal          原生回调入口（receiveFrame / setConnectionState / addLog）
     window.renderThermalArea() 供 screens.js 状态页拼接 HTML

   原生接口（MainActivity @JavascriptInterface）：
     thermalState()                   → String  连接状态文本
     thermalDevices()                 → JSON    [{id,name,rssi}]
     thermalAction('scan'|'connect'|'disconnect', value)
     thermalFullscreen(bool)
   ========================================================================== */
(function () {
  'use strict';

  var N = function () { return window.IcuNative || null; };
  var IDLE_TEXT = '点击连接红外测温';
  var FRESH_MS = 5000;      // 超过该时间没新帧 → 视为已断流
  var SCAN_MS = 10000;      // 搜索提示持续时间

  var st = {
    connText: '',
    lastFrameAtMs: 0,
    scanAtMs: 0,
    frames: 0,
    fps: 0,
    lastTs: 0,
    w: 0, h: 0,
    centi: null,
    devices: [],
    pickerOpen: false,
    logs: []
  };

  /* ---------- 工具 ---------- */
  function T(k, fb) {
    try {
      if (window.T) return window.T(k, fb);
    } catch (e) { /* noop */ }
    return fb;
  }
  function toText(v) { return v === null || v === undefined ? '' : String(v); }
  function fmtC(v) { return (v / 100).toFixed(1) + '℃'; }

  function nativeCall(action, value) {
    var n = N();
    if (!n || !n.thermalAction) {
      st.connText = '当前环境不支持红外测温';
      paintStatus();
      return false;
    }
    try { n.thermalAction(action, value || ''); return true; }
    catch (e) { st.connText = '调用失败: ' + (e && e.message || e); paintStatus(); return false; }
  }

  /* ---------- Base64 → Int16LE（百分之一度） ---------- */
  function decodeFrame(base64, w, h) {
    var bin;
    if (typeof window.atob === 'function') {
      bin = window.atob(base64);
    } else {
      // 极老 WebView 兜底
      var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
      var out = '', i = 0, c1, c2, c3, e1, e2, e3, e4;
      base64 = String(base64).replace(/[^A-Za-z0-9+/=]/g, '');
      while (i < base64.length) {
        e1 = chars.indexOf(base64.charAt(i++)); e2 = chars.indexOf(base64.charAt(i++));
        e3 = chars.indexOf(base64.charAt(i++)); e4 = chars.indexOf(base64.charAt(i++));
        c1 = (e1 << 2) | (e2 >> 4); c2 = ((e2 & 15) << 4) | (e3 >> 2); c3 = ((e3 & 3) << 6) | e4;
        out += String.fromCharCode(c1);
        if (e3 !== 64) out += String.fromCharCode(c2);
        if (e4 !== 64) out += String.fromCharCode(c3);
      }
      bin = out;
    }
    var bytes = new Uint8Array(bin.length);
    for (var k = 0; k < bin.length; k++) bytes[k] = bin.charCodeAt(k) & 0xff;
    var need = w * h * 2;
    if (bytes.length < need) return null;
    var view = new DataView(bytes.buffer, 0, need);
    var out16 = new Int16Array(w * h);
    for (var j = 0; j < out16.length; j++) out16[j] = view.getInt16(j * 2, true); // little-endian
    return out16;
  }

  /* ---------- 伪彩映射（ironbow） ---------- */
  var STOPS = [
    [0.00, 0, 0, 30], [0.15, 40, 0, 120], [0.35, 150, 0, 160],
    [0.55, 220, 60, 60], [0.75, 255, 150, 0], [0.90, 255, 230, 80], [1.00, 255, 255, 255]
  ];
  function ramp(t) {
    if (t < 0) t = 0; if (t > 1) t = 1;
    for (var i = 0; i < STOPS.length - 1; i++) {
      var a = STOPS[i], b = STOPS[i + 1];
      if (t >= a[0] && t <= b[0]) {
        var k = (t - a[0]) / (b[0] - a[0] || 1);
        return [a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k];
      }
    }
    return [255, 255, 255];
  }

  /* ---------- 绘制 ---------- */
  var offCanvas = null, offCtx = null;
  function ensureOff(w, h) {
    if (offCanvas && offCanvas.width === w && offCanvas.height === h) return;
    offCanvas = document.createElement('canvas');
    offCanvas.width = w; offCanvas.height = h;
    offCtx = offCanvas.getContext('2d');
  }
  var lastMaxIdx = -1;

  function draw() {
    var cv = document.querySelector('.th-canvas');
    if (!cv) return;
    var ctx = cv.getContext('2d');
    if (!ctx) return;
    if (!st.centi || !st.w || !st.h) { ctx.clearRect(0, 0, cv.width, cv.height); return; }

    /* ★ V26：铺满模式下画布分辨率跟随可视区，避免 CSS 拉伸糊掉 */
    var vw = cv.parentElement ? cv.parentElement.clientWidth : 0;
    var vh = cv.parentElement ? cv.parentElement.clientHeight : 0;
    if (vw > 0 && vh > 0 && (cv.width !== vw || cv.height !== vh)) { cv.width = vw; cv.height = vh; }

    var w = st.w, h = st.h, d = st.centi;
    var mn = Infinity, mx = -Infinity, maxIdx = 0;
    for (var i = 0; i < d.length; i++) {
      var v = d[i];
      if (v < mn) mn = v;
      if (v > mx) { mx = v; maxIdx = i; }
    }
    var span = mx - mn;
    if (!isFinite(span) || span < 200) { span = 200; }   // 至少 2℃ 跨度，避免平坦帧全屏单色

    ensureOff(w, h);
    var img = offCtx.createImageData(w, h);
    for (var p = 0; p < d.length; p++) {
      var c = ramp((d[p] - mn) / span);
      img.data[p * 4] = c[0] | 0;
      img.data[p * 4 + 1] = c[1] | 0;
      img.data[p * 4 + 2] = c[2] | 0;
      img.data[p * 4 + 3] = 255;
    }
    offCtx.putImageData(img, 0, 0);

    ctx.imageSmoothingEnabled = true;
    ctx.clearRect(0, 0, cv.width, cv.height);
    ctx.drawImage(offCanvas, 0, 0, cv.width, cv.height);

    // 最高温位置标记
    lastMaxIdx = maxIdx;
    var px = ((maxIdx % w) + 0.5) * cv.width / w;
    var py = (Math.floor(maxIdx / w) + 0.5) * cv.height / h;
    ctx.strokeStyle = 'rgba(255,255,255,.9)';
    ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(px, py, Math.max(6, cv.width / 42), 0, Math.PI * 2); ctx.stroke();

    setText('[data-th-maxv]', fmtC(mx));
    setText('[data-th-minv]', fmtC(mn));
    setText('[data-th-badge]', fmtC(mx));

    /* ★ V26：状态页「红外体温」卡数值 = 热图最高温（同步进 D.status，重渲染不丢） */
    var maxC = (mx / 100).toFixed(1);
    try { if (window.D && D.status) D.status.irTemp = maxC; } catch (e) { /* noop */ }
    var irMv = document.querySelector('[data-ir] .mv');
    if (irMv && irMv.textContent !== maxC) irMv.textContent = maxC;
  }

  /* ---------- 状态文案刷新 ---------- */
  function setText(sel, txt) {
    var el = document.querySelector(sel);
    if (el && el.textContent !== txt) el.textContent = txt;
  }
  function isLive() { return st.centi && st.lastFrameAtMs && (Date.now() - st.lastFrameAtMs) < FRESH_MS; }

  function paintStatus() {
    var box = document.querySelector('[data-therm]');
    if (!box) return;
    var live = isLive();
    /* ★ V26：连接出图后整区铺满（CSS 切为上下结构：图像铺满 + 按钮条在最底部） */
    box.classList.toggle('on', !!live);
    var scanning = st.scanAtMs && (Date.now() - st.scanAtMs) < SCAN_MS;
    var empty = box.querySelector('[data-th-empty]');
    if (empty) {
      var msg = live ? '' : (st.connText || (scanning ? '正在搜索红外测温桥…' : IDLE_TEXT));
      empty.textContent = msg;
      empty.hidden = !!live;
    }
    setText('[data-th-state]', st.connText || (live ? '已连接 · 数据接收中' : '未连接'));
    setText('[data-th-fps]', st.fps ? st.fps.toFixed(1) + ' fps' : '--');
    var btn = box.querySelector('[data-th-act="disconnect"]');
    if (btn) btn.disabled = !live && !st.connText;
  }

  /* ---------- 设备选择浮层 ---------- */
  function closePicker() {
    st.pickerOpen = false;
    var p = document.querySelector('[data-th-picker]');
    if (p) p.hidden = true;
  }
  function openPicker() {
    var p = document.querySelector('[data-th-picker]');
    if (!p) return;
    var list = p.querySelector('[data-th-list]');
    if (list) {
      var html = st.devices.map(function (d, i) {
        return '<div class="th-row-dev" data-th-dev="' + i + '">'
          + '<span>' + esc(d.name || '未知设备') + '</span>'
          + '<i>RSSI ' + (d.rssi === undefined ? '--' : d.rssi) + '</i></div>';
      }).join('') || '<div class="th-row-dev disabled">未搜索到设备</div>';
      /* ★ V27：扫描结果流式推送时只在列表内容真正变化时才重建，根治弹窗闪烁 */
      if (list.getAttribute('data-th-html') !== html) {
        list.innerHTML = html;
        list.setAttribute('data-th-html', html);
      }
    }
    if (!p.hidden) { st.pickerOpen = true; return; }  // 已开着就别重复触发显示
    p.hidden = false;
    st.pickerOpen = true;
  }
  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c];
    });
  }
  function refreshDevices() {
    var n = N();
    if (!n || !n.thermalDevices) return;
    var raw = '';
    try { raw = n.thermalDevices() || ''; } catch (e) { return; }
    if (!raw) { st.devices = []; return; }
    try {
      var arr = JSON.parse(raw);
      st.devices = Array.isArray(arr) ? arr : [];
    } catch (e) { st.devices = []; }
    if (st.devices.length && document.querySelector('[data-therm]')) openPicker();
  }

  /* ---------- 原生回调入口 ---------- */
  window.IcuThermal = {
    receiveFrame: function (base64, w, h, ts) {
      var d = decodeFrame(base64, w, h);
      if (!d) return;
      st.w = w; st.h = h; st.centi = d;
      var now = Date.now();
      st.lastFrameAtMs = now;
      st.frames++;
      if (st.lastTs && ts > st.lastTs) {
        var f = 1000 / (ts - st.lastTs);
        st.fps = st.fps ? st.fps * 0.7 + f * 0.3 : f;
      }
      st.lastTs = ts || 0;
      draw();
      paintStatus();
    },
    setConnectionState: function (text) {
      st.connText = toText(text);
      if (/未连接|断开|超时|失败/.test(st.connText)) { st.scanAtMs = 0; closePicker(); }
      paintStatus();
    },
    addLog: function (msg) {
      st.logs.push(toText(msg));
      if (st.logs.length > 60) st.logs.shift();
    },
    state: st,
    /* 无真机时的自演示帧（供 headless 验证样式/渲染，不自动调用） */
    demoFrame: function () {
      var w = 32, h = 24, d = new Int16Array(w * h);
      for (var y = 0; y < h; y++) {
        for (var x = 0; x < w; x++) {
          var dx = x - 21, dy = y - 11;
          var v = 2700 + 900 * Math.exp(-(dx * dx + dy * dy) / 26);
          d[y * w + x] = Math.round(v);
        }
      }
      st.w = w; st.h = h; st.centi = d; st.lastFrameAtMs = Date.now(); st.fps = 2;
      draw(); paintStatus();
    }
  };

  /* ---------- 状态页 HTML ---------- */
  window.renderThermalArea = function () {
    return '<div class="therm" data-therm>'
      + '<div class="th-view">'
      + '<canvas class="th-canvas" width="316" height="237"></canvas>'
      + '<div class="th-badge" data-th-badge>--</div>'
      + '<div class="th-empty" data-th-empty>' + IDLE_TEXT + '</div>'
      + '</div>'
      + '<div class="th-side">'
      + '<div class="th-title">' + T('thermalTitle', '红外测温') + '</div>'
      + '<div class="th-row"><span>' + T('thState', '状态') + '</span><b data-th-state>' + T('thIdle', '未连接') + '</b></div>'
      + '<div class="th-row"><span>' + T('thMax', '最高温') + '</span><b data-th-maxv>--</b></div>'
      + '<div class="th-row"><span>' + T('thMin', '最低温') + '</span><b data-th-minv>--</b></div>'
      + '<div class="th-row"><span>' + T('thFps', '帧率') + '</span><b data-th-fps>--</b></div>'
      + '<div class="th-btns">'
      + '<button class="thbtn primary" data-th-act="scan">' + T('thScan', '搜索连接') + '</button>'
      + '<button class="thbtn" data-th-act="disconnect">' + T('thDisconnect', '断开') + '</button>'
      + '</div></div>'
      + '<div class="th-picker" data-th-picker hidden><div class="th-picker-box">'
      + '<div class="th-picker-hd">' + T('thPickDev', '选择红外测温桥') + '</div>'
      + '<div class="th-list" data-th-list></div>'
      + '<div class="th-btns">'
      + '<button class="thbtn primary" data-th-act="rescan">' + T('thRescan', '重新搜索') + '</button>'
      + '<button class="thbtn" data-th-act="closepick">' + T('cancel', '取消') + '</button>'
      + '</div></div></div>'
      + '</div>';
  };

  /* ---------- 交互绑定 ---------- */
  function doScan() {
    st.scanAtMs = Date.now();
    st.connText = '正在搜索红外测温桥…';
    nativeCall('scan');
    paintStatus();
  }
  function bindOnce() {
    var box = document.querySelector('[data-therm]');
    if (!box) return false;
    if (box.getAttribute('data-th-bound')) return true;
    box.setAttribute('data-th-bound', '1');

    box.addEventListener('click', function (e) {
      var act = e.target.closest('[data-th-act]');
      if (act) {
        var a = act.getAttribute('data-th-act');
        if (a === 'scan' || a === 'rescan') doScan();
        else if (a === 'disconnect') {
          nativeCall('disconnect');
          st.connText = '红外测温已手动断开';
          st.centi = null; draw(); paintStatus();
        } else if (a === 'closepick') closePicker();
        e.stopPropagation();
        return;
      }
      var dev = e.target.closest('[data-th-dev]');
      if (dev) {
        var idx = parseInt(dev.getAttribute('data-th-dev'), 10);
        var d = st.devices[idx];
        if (d) { nativeCall('connect', d.id); st.connText = '正在连接…'; closePicker(); paintStatus(); }
        e.stopPropagation();
        return;
      }
      // 点热图画面本身：未连接 → 搜索；已连接 → 放大到全屏
      if (e.target.closest('.th-view')) {
        if (!isLive()) {
          doScan();
        } else {
          var n2 = N();
          if (n2 && n2.thermalFullscreen) { try { n2.thermalFullscreen(true); } catch (e2) { /* noop */ } }
        }
        e.stopPropagation();
      }
    });
    draw();
    paintStatus();
    /* ★ V27：状态页整体重渲染会把 picker 一起重建，之前开着就在新 DOM 上恢复，避免一闪一闪 */
    if (st.pickerOpen && st.devices.length) openPicker();
    return true;
  }

  /* 扫描结果 / 重渲染 → 重新挂点 */
  function tryBind() {
    if (document.querySelector('[data-therm]')) bindOnce();
  }
  if (window.MutationObserver) {
    var appRoot = document.getElementById('app-root');
    if (appRoot) new MutationObserver(tryBind).observe(appRoot, { childList: true });
  }
  window.addEventListener('icu-thermal-devices', function () { refreshDevices(); paintStatus(); });
  window.addEventListener('DOMContentLoaded', tryBind);
  setTimeout(tryBind, 0);
  setInterval(function () { if (document.querySelector('[data-therm]')) paintStatus(); }, 1000);
})();
