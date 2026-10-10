/* ==========================================================================
   app.js · 路由 / 缩放 / 事件 / 抽屉
   ========================================================================== */
(function () {
  'use strict';
  const SCREENS = window.SCREENS;
  const root = document.getElementById('app-root');
  const stage = document.getElementById('stage');
  const drawer = document.getElementById('drawer');
  const hudPage = document.getElementById('hud-page');

  /* 页面清单抽屉 ---------------------------------------------------------- */
  (function buildDrawer() {
    const groups = {};
    const order = [];
    Object.keys(SCREENS).forEach(function (id) {
      const g = SCREENS[id].group || '其他';
      if (!groups[g]) { groups[g] = []; order.push(g); }
      groups[g].push(id);
    });
    drawer.innerHTML = order.sort().map(function (g) {
      return '<div class="dt">' + g + '</div>' + groups[g].map(function (id) {
        return '<a data-id="' + id + '" data-go="' + id + '">' + SCREENS[id].name + '</a>';
      }).join('');
    }).join('');
  })();

  /* 自适应缩放（设计稿 1920x1200） ---------------------------------------- */
  /* ★ 改用 CSS zoom：布局盒真实缩放，#viewport 的 flex 居中作用于真实盒子，
     彻底避免 transform:scale 在 WebView 上"缩放盒与布局盒不一致"导致的
     偏移/裁切（此前主控页右侧整列被切、左侧留大块底色就是这个原因）。 */
  function fit() {
    const w = window.innerWidth || document.documentElement.clientWidth || 1280;
    const h = window.innerHeight || document.documentElement.clientHeight || 800;
    const k = Math.min(w / 1920, h / 1200) || 1;
    stage.style.transform = 'none';
    stage.style.zoom = k;
  }
  window.addEventListener('resize', fit);
  window.addEventListener('load', fit);
  window.addEventListener('orientationchange', fit);
  [0, 100, 400, 1000, 2500].forEach(function (t) { setTimeout(fit, t); });
  if (window.ResizeObserver && window.ResizeObserver.prototype && window.ResizeObserver.prototype.observe) {
    try { new ResizeObserver(fit).observe(document.getElementById('viewport')); } catch (e) { /* noop */ }
  }
  fit();

  /* 路由 ------------------------------------------------------------------ */
  let current = 'splash';
  const hist = [];
  function render() {
    const sc = SCREENS[current];
    if (!sc) { current = 'splash'; return render(); }
    root.innerHTML = sc.render();
    hudPage.textContent = sc.name;
    drawer.querySelectorAll('a').forEach(function (a) { a.classList.toggle('on', a.dataset.id === current); });
    /* ★ 嵌入 App 后：渲染完成回调，桥接层据此注入实时数据 / 表单 */
    if (window.IcuOnRender) { try { window.IcuOnRender(current); } catch (e) { /* noop */ } }
    /* ★ 任务19：渲染含监护波形时启动滚动动画（无 .wave 时循环自动休眠） */
    if (window.__waveAnimKick) { try { window.__waveAnimKick(); } catch (e) { /* noop */ } }
  }
  function go(id) {
    if (!SCREENS[id] || id === current) return;
    hist.push(current); current = id;
    if (location.hash.slice(1) !== id) location.hash = id;
    render();
  }
  function back() {
    current = hist.pop() || 'splash';
    if (location.hash.slice(1) !== current) location.hash = current;
    render();
  }
  function setScreen(id) { /* 供桥接层强制切屏（不入栈） */
    if (!SCREENS[id]) return;
    current = id; hist.length = 0;
    if (location.hash.slice(1) !== id) location.hash = id;
    render();
  }

  /* ★ 暴露给桥接层（icu-native-bridge.js） */
  window.IcuApp = { go: go, back: back, render: render, setScreen: setScreen, current: function () { return current; } };

  /* 事件委托 -------------------------------------------------------------- */
  /* capture 阶段先于桥接层判定是否拦截；桥接层若调用 e.stopImmediatePropagation 则不进入此处 */
  function ageSelCloseFloat() {
    document.querySelectorAll('.age-sel.open').forEach(function (s) { s.classList.remove('open'); });
    document.querySelectorAll('.age-sel-list.float').forEach(function (l) { l.remove(); });
  }
  root.addEventListener('click', function (e) {
    /* ★ 2026-10-09 自定义年龄单位下拉 v2：展开时把列表克隆挂到 .modal 下定位。
       原因：stage 用 CSS zoom 缩放时，溢出小父框(.age-sel 仅 110x50)的绝对定位列表
       在 WebView 里"画在上层但点不到"（命中测试穿透到底层 chip）。挂到大容器内即正常。 */
    const aopt = e.target.closest('.age-sel-list.float .age-sel-opt');
    if (aopt) {
      const host = document.querySelector('.age-sel.open');
      if (host) {
        host.dataset.value = aopt.dataset.v;
        host.querySelector('.age-sel-v').textContent = aopt.textContent;
        host.querySelectorAll('.age-sel-opt').forEach(function (o) { o.classList.toggle('on', o.dataset.v === aopt.dataset.v); });
      }
      ageSelCloseFloat();
      return;
    }
    const asel = e.target.closest('.age-sel');
    if (asel) {
      if (asel.classList.contains('open')) { ageSelCloseFloat(); return; }
      ageSelCloseFloat();
      asel.classList.add('open');
      const modal = asel.closest('.modal');
      const src = asel.querySelector('.age-sel-list');
      if (modal && src) {
        /* 注意：getBoundingClientRect 在该 WebView 的 CSS zoom 下返回的是"布局像素"
           （未乘 zoom），与内联 style 长度同坐标系，无需再换算 */
        const r = asel.getBoundingClientRect(), mr = modal.getBoundingClientRect();
        const fl = src.cloneNode(true);
        fl.classList.add('float');
        fl.style.left = (r.left - mr.left - 1) + 'px';
        fl.style.top = (r.bottom - mr.top + 4) + 'px';
        fl.style.width = (r.width + 2) + 'px';
        modal.appendChild(fl);
      }
      return;
    }
    ageSelCloseFloat();
    const sw = e.target.closest('[data-sw]');
    if (sw) { sw.classList.toggle('on'); const l = sw.querySelector('.l'); if (l) { const TT = window.T || function (k, f) { return f; }; l.textContent = sw.classList.contains('on') ? TT('on', '开') : TT('off', '关'); } return; }
    const ck = e.target.closest('.ckbox');
    if (ck) { ck.classList.toggle('on'); return; }
    const bp = e.target.closest('.bpsw button');
    if (bp) { bp.parentNode.querySelectorAll('button').forEach(function (b) { b.classList.remove('on'); }); bp.classList.add('on'); return; }
    const chip = e.target.closest('.chip');
    if (chip) { chip.parentNode.querySelectorAll('.chip').forEach(function (c) { c.classList.remove('on'); }); chip.classList.add('on'); return; }
    const tr = e.target.closest('.tbl tbody tr, .tbl tr');
    if (tr) { tr.parentNode.querySelectorAll('tr').forEach(function (r) { r.classList.remove('sel'); }); tr.classList.add('sel'); }
    const el = e.target.closest('[data-go]');
    if (el) { e.stopPropagation(); go(el.dataset.go); }
    const lc = e.target.closest('[data-log]');
    if (lc) { D.logCat = lc.dataset.log; D.logPage = 1; render(); return; }
    /* ★ 2026-10-08 用户日志：翻页 / 清除日期过滤（更新按钮由桥接层 data-logrefresh 处理） */
    const lp = e.target.closest('[data-logpage]');
    if (lp) { D.logPage = (D.logPage || 1) + parseInt(lp.dataset.logpage, 10); render(); return; }
    const ldc = e.target.closest('[data-logdate-clear]');
    if (ldc) { D.logDate = ''; D.logPage = 1; render(); return; }
  });

  /* HUD 外壳（演示辅助） -------------------------------------------------- */
  document.getElementById('btn-drawer').addEventListener('click', function () { drawer.classList.toggle('open'); });
  document.getElementById('btn-notes').addEventListener('click', function () {
    document.body.classList.toggle('notes');
    this.textContent = document.body.classList.contains('notes') ? '隐藏改版标注' : '显示改版标注';
  });
  document.getElementById('btn-back').addEventListener('click', back);

  /* hash 路由 ------------------------------------------------------------- */
  function fromHash() {
    const id = decodeURIComponent(location.hash.slice(1));
    current = SCREENS[id] ? id : 'splash';
    render();
  }
  window.addEventListener('hashchange', fromHash);
  fromHash();
})();
