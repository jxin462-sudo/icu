/* ==========================================================================
   icu-native-bridge.js · 把 UI 复刻原型接入 App 原生能力
   --------------------------------------------------------------------------
   职责：
   1. 订阅原生 'icu-native-state' 事件，拉取 IcuNative.firstPhaseState()，
      把真实病例/环境/控制/会话标识映射进原型的全局数据对象 D，再触发重渲染。
   2. 在 capture 阶段拦截按钮点击，映射到 IcuNative.action(...) / login / logout 等。
   3. 把“新建样本 / 查询”弹窗里的静态占位 div 转成真实 input，并在确认时回写
      syncTempPatient / review_query。
   约束：不触碰蓝牙协议（BleManager），所有设备交互都走原生既有的 action/bleAction。
   ========================================================================== */
(function () {
  'use strict';
  var D = window.D;
  if (!D || !window.IcuApp) { return; }

  /* ---------- 原生桥小封装（容错，桌面浏览器预览时 IcuNative 不存在） ---------- */
  function N() { return window.IcuNative || null; }
  function hasNative() { var n = N(); return !!(n && n.firstPhaseState); }
  function act(a, t, p) { feLog('INFO', 'H5→native', a + (t ? ' | ' + t : '') + (p ? ' | ' + p : '')); var n = N(); try { if (n && n.action) n.action(a, t || '', p || ''); } catch (e) { feErr('act', a + ': ' + (e.message || e)); } }
  /* ★ 2026-10-08 蓝牙设备选择：走原生既有 bleAction(target,action,value) 契约（scan/stop/connect/disconnect），不触碰协议 */
  function bleAct(target, action, value) {
    feLog('INFO', 'H5→native', 'bleAction ' + target + ' ' + action + (value ? ' | ' + value : ''));
    var n = N(); try { if (n && n.bleAction) n.bleAction(target, action, value || ''); } catch (e) { feErr('bleAction', target + ' ' + action + ': ' + (e.message || e)); }
  }
  function bleChannel(target) {
    if (target === 'host') { return S.host || (S.ble && S.ble.host) || {}; }
    return (S.ble && S.ble.monitor) || {};
  }
  /* 打开设备选择弹窗：开始扫描 + 置 D.blePick（'host'|'monitor'），扫描结果随 icu-native-state 自动刷新 */
  function openBlePick(target) {
    D.blePick = target;
    bleAct(target, 'scan', '');
    IcuApp.render();
  }
  function isLoggedIn() { var n = N(); try { return !!(n && n.isLoggedIn && n.isLoggedIn()); } catch (e) { return false; } }
  function doLogin(name, pwd) { var n = N(); try { if (n && n.login) return n.login(name, pwd); } catch (e) {} return false; }
  function doLogout() { var n = N(); try { if (n && n.logout) { n.logout(); return true; } } catch (e) {} return false; }
  /* ★ 任务21：调试页密码（菜单→调试 进入前校验）。如需修改密码改这一行即可。 */
  var DEBUG_PWD = '123456';
  function syncPatient(payload) { var n = N(); try { if (n && n.syncTempPatient) n.syncTempPatient(JSON.stringify(payload)); } catch (e) {} }
  function toast(msg) {
    if (!msg) return;
    var el = document.createElement('div');
    el.style.cssText = 'position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);background:rgba(0,0,0,.78);color:#fff;padding:18px 36px;border-radius:10px;font-size:24px;z-index:9999;white-space:nowrap';
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 2000);
  }

  /* i18n 助手：window.T 未就绪时回退中文 */
  function TT(k, fb) { return window.T ? window.T(k, fb) : fb; }

  /* ★ 任务38：蓝牙连接状态边沿检测（false→true / true→false 时弹窗提示）。
     null 表示尚未初始化，首次同步不弹。 */
  var prevHostConn = null, prevMonConn = null, prevScreen = null;

  /* ★ 任务38：原生状态事件驱动的前导+尾随节流渲染。
     AM4100 连接后每帧 emit → 原生 50ms 节流推送（≈20次/秒），
     整页重渲染风暴会把 rAF 演示波形反复打回初始帧；这里收敛到 ≤8次/秒 */
  var lastRenderAt = 0, renderTimer = 0;
  function scheduleRender() {
    /* ★ 任务42：输入框聚焦（软键盘弹起）期间推迟整页重渲染——
       监护连接后原生 ≈8次/秒 推状态，若不推迟，重建 DOM 会销毁聚焦中的输入框、
       键盘被反复收起、文本无法输入。聚焦时 300ms 轮询重试，失焦后自动补渲染 */
    var ae = document.activeElement;
    if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA' || ae.tagName === 'SELECT')) {
      if (!renderTimer) renderTimer = setTimeout(function () { renderTimer = 0; scheduleRender(); }, 300);
      return;
    }
    var now = Date.now();
    if (now - lastRenderAt >= 120 && !renderTimer) { lastRenderAt = now; IcuApp.render(); return; }
    if (renderTimer) return;
    renderTimer = setTimeout(function () { renderTimer = 0; lastRenderAt = Date.now(); IcuApp.render(); }, 120);
  }

  /* ---------- ★ 2026-10-08 日志页：前端调试 / 错误记录（H5 侧捕获） ----------
     环形缓冲 + localStorage 持久化；桌面预览与真机通用，不依赖原生桥。 */
  var FE_LOG_KEY = 'icu_fe_debug_log_v1', FE_ERR_KEY = 'icu_fe_error_log_v1';
  var FE_CAP = 500, FE_ERR_CAP = 200;
  function feNow() {
    var d = new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
      + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }
  function feLoad(key) { try { var a = JSON.parse(localStorage.getItem(key) || '[]'); return Array.isArray(a) ? a : []; } catch (e) { return []; } }
  function feSave(key, arr) { try { localStorage.setItem(key, JSON.stringify(arr)); } catch (e) {} }
  var feDebugLog = feLoad(FE_LOG_KEY);
  var feErrorLog = feLoad(FE_ERR_KEY);
  function feLog(level, source, msg) {
    feDebugLog.push({ time: feNow(), level: level, source: source, msg: String(msg).slice(0, 300) });
    while (feDebugLog.length > FE_CAP) feDebugLog.shift();
    feSave(FE_LOG_KEY, feDebugLog);
  }
  function feErr(source, msg) {
    feErrorLog.push({ time: feNow(), source: source, msg: String(msg).slice(0, 1000) });
    while (feErrorLog.length > FE_ERR_CAP) feErrorLog.shift();
    feSave(FE_ERR_KEY, feErrorLog);
    feLog('ERROR', source, msg);
  }
  /* 页面级 JS 异常 / Promise 未处理拒绝 → 错误记录 */
  window.addEventListener('error', function (e) {
    feErr('window.onerror', (e.message || '') + ' @ ' + (e.filename || '') + ':' + (e.lineno || ''));
  });
  window.addEventListener('unhandledrejection', function (e) {
    var r = e.reason;
    feErr('unhandledrejection', (r && (r.stack || r.message)) || String(r));
  });
  /* console.warn / console.error → 前端调试（保留原输出） */
  (function () {
    var w0 = console.warn, e0 = console.error;
    console.warn = function () { feLog('WARN', 'console', Array.prototype.join.call(arguments, ' ')); w0.apply(console, arguments); };
    console.error = function () { feLog('ERROR', 'console', Array.prototype.join.call(arguments, ' ')); e0.apply(console, arguments); };
  })();

  /* ---------- 状态缓存与工具 ---------- */
  var S = {};
  var pendingEditor = false;   // 用户点"编辑"，打开现有病例的编辑弹窗
  var draftNewMode = false;    // 用户点"新建"，处于草稿态（未调 patient_new）
  var sendingCard = null;      // 任务9：发送数据页当前弹层 'file'(文件助手二维码) | 'ble'(蓝牙正在发送) | null
  var pendingSaveAfterCreate = null; // 新建草稿确认后暂存表单 payload，等原生回推再 sync
  var pendingNavAfterSave = null; // 保存后要跳的屏
  var splashTimer = null; // 开机动画自动跳转计时器
  var sheetPreviewCase = null; // 任务23：回顾页双击预览治疗记录单时覆盖 S.patient 的历史病例对象

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function num(txt, def) { if (txt == null) return def; var m = String(txt).replace(/[^0-9.]/g, ''); return m === '' ? def : m; }
  function text(v, def) { return (v == null || v === '') ? (def || '') : String(v); }
  /* ★ 任务33：原生波形采样数组 → 纯 number 数组（非法值丢弃，超长截尾 1500） */
  function arrNums(a) {
    if (!a || !a.length) return [];
    var out = [];
    for (var i = 0; i < a.length && out.length < 1500; i++) {
      var v = Number(a[i]);
      if (isFinite(v)) out.push(v);
    }
    return out;
  }

  function pullState() {
    if (!hasNative()) { S = {}; window.S = S; return; }
    try { S = JSON.parse(N().firstPhaseState() || '{}'); } catch (e) { S = {}; }
    window.S = S; // ★ 任务13：暴露给 screens.js（设置-连接页读 host/ble/wifi 实时状态）
  }
  function pullSettings() {
    try { D.settings = window.__icuSettings || {}; } catch (e) { D.settings = {}; }
    if (!D.settings) D.settings = {};
  }

  /* 病例 → 列表行 */
  function mapRows(arr) {
    var out = [];
    if (!arr || !arr.length) return out;
    for (var i = 0; i < arr.length; i++) {
      var p = arr[i] || {};
      out.push({
        caseId: p.caseId || '',
        no: p.caseNo || '', name: p.petName || '', sp: p.species || '',
        owner: p.ownerName || '', tel: p.ownerPhone || '', doc: p.doctor || '',
        illness: p.disease || '', time: (p.visitDate || '').replace('T', ' '),
        st: p.currentTreatment ? 'ok' : (p.transferredOut ? 'err' : 'load')
      });
    }
    return out;
  }

  /* 治疗项时长文本：无限→常开；有剩余毫秒→mm:ss；否则用原生 control 值 */
  function durText(val, unlimited, remainingMs) {
    if (unlimited) return TT('alwaysOn', '常开');
    if (remainingMs && remainingMs > 0) {
      var s = Math.floor(remainingMs / 1000);
      return pad(Math.floor(s / 60)) + ':' + pad(s % 60);
    }
    return (val && val !== '0' && val !== 0) ? String(val) : '00:00';
  }

  /* ---------- 把原生状态映射进 D ---------- */
  function syncD() {
    try {
      D.user = text(S.accountName, TT('roleAdmin', '管理员'));
      // ★ 任务13：用户管理 / 舱区权限（R67-R73 / R93）
      D.accounts = Array.isArray(S.accounts) ? S.accounts : [];
      D.accountRole = S.accountRole || '';
      D.canSwitchZone = !!S.canSwitchZone;
      D.zone = S.zone || '';
      /* ★ 任务18：蓝牙连接态（监护页波形门控）—— 未连蓝牙时默认演示波形，不再需要「演示数据」开关 */
      D.bleConnected = !!(S.ble && S.ble.host && S.ble.host.connected);
      D.users = D.accounts.map(function (a) {
        return {
          name: a.name || '',
          pwd: a.passwordMasked || '••••••',
          role: a.role || 'user',
          isCurrent: !!a.isCurrent,
          organization: a.organization || ''
        };
      });
      // ★ V1.02 任务12：语言从原生 S5 设置回推（window.__icuSettings.language）
      D.lang = (D.settings && D.settings.language) || (S.settings && S.settings.language)
        || (window.__icuSettings && window.__icuSettings.language) || 'zh';
      var now = new Date();
      D.time = pad(now.getHours()) + ':' + pad(now.getMinutes());
      /* ★ 2026-09-30：顶栏日期统一为「yyyy年MM月dd日」（D.stamp 随之生效）；英文界面用 yyyy/MM/dd */
      D.date = (D.lang === 'en')
        ? now.getFullYear() + '/' + pad(now.getMonth() + 1) + '/' + pad(now.getDate())
        : now.getFullYear() + '年' + pad(now.getMonth() + 1) + '月' + pad(now.getDate()) + '日';
      D.stamp = D.date + ' ' + D.time + ':00';

      D.session = text(S.monitorModeLabel, '');

      D.careRows = mapRows(S.careCases);
      D.reviewRows = mapRows(S.historyCases);
      /* ★ 2026-10-08 日志页：用户/操作日志（原生 userOpLog，倒序最新在前） */
      D.userLogs = Array.isArray(S.userLogs) ? S.userLogs : [];
      /* ★ 2026-10-08 日志页：前端调试（H5 侧捕获）/ 错误记录（H5 + 原生合并）
         常规信息 / 后端调试 / 通信记录 / 堆栈信息（原生侧各 store，倒序推来） */
      D.frontLogs = feDebugLog.slice().reverse();
      var feErrDesc = feErrorLog.slice().reverse();
      var nativeErr = Array.isArray(S.errorLogs) ? S.errorLogs : [];
      D.errorLogs = feErrDesc.concat(nativeErr).sort(function (a, b) {
        return String(b.time || '').localeCompare(String(a.time || ''));
      });
      D.generalLogs = Array.isArray(S.generalLogs) ? S.generalLogs : [];
      D.debugLogs = Array.isArray(S.debugLogs) ? S.debugLogs : [];
      D.commLogs = Array.isArray(S.commLogs) ? S.commLogs : [];
      D.stackLogs = Array.isArray(S.stackLogs) ? S.stackLogs : [];
      // 当前选中的病例 caseId（用于表格行 sel 类）
      D.selCaseId = (S.patient && S.patient.caseId) || '';

      var h = S.host || {}, c = h.controls || {};
      D.hospital = (S.organization && S.organization.name) || '';
      /* ★ 任务31：打印设置同步进治疗记录单预览 —— 医院名/电话/地址/医院LOGO/页脚LOGO/报告标题/报告声明。
         此前 D.sheet.foot 等字段无人赋值，预览永远显示空值与占位「LOGO」文本 */
      var orgX = S.organization || {}, pst = D.settings || {};
      D.sheet.logoUri = text(orgX.logoUri, '');
      D.sheet.footerLogoUri = text(pst.footerLogoUri, '');
      D.sheet.title = text(pst.reportTitle, '') || TT('sheetTitle', 'ICU动物舱治疗记录单');
      if (D.sheet.foot) {
        D.sheet.foot.tel = text(orgX.phone, '');
        D.sheet.foot.addr = text(orgX.address, '');
        D.sheet.foot.note = text(pst.reportDeclaration, '');
      }
      // 监护等级（一级/二级/三级 → 红/黄/绿）
      D.levelText = text(h.monitorLevel, '一级');
      D.levelIdx = ({ '一级': 0, '二级': 1, '三级': 2 })[D.levelText];
      if (typeof D.levelIdx !== 'number') D.levelIdx = 0;
      D.status = {
        temp: num(h.temp, '--'), o2: num(h.oxygen, '--'), hum: num(h.humidity, '--'),
        co2: num(h.co2, '--'), irTemp: num(h.infraredTemp, '--'), dur: text(h.treatmentTime, '--'),
        red: text(c.redTherapy, '--'), blue: text(c.blueTherapy, '--')
      };
      /* ★ 任务33：AM4100 监护宝实时体征接入。S.monitor.connected 且有数据时填真实值；
         未连接时保持全 '--'（不造假数据），波形走本地演示滚动动画
         ★ 任务38修复：真实状态里监护数据在 S.ble.monitor（顶层无 monitor 键），
         此前读 S.monitor 恒为 {} → monLive 恒 false → 实时体征/波形全死 */
      var mon = (S.ble && S.ble.monitor) || S.monitor || {};
      var monLive = !!mon.connected;
      D.monitorLive = monLive;
      if (monLive) {
        D.vitals = {
          hr: text(mon.heartRate, '--'), bp: text(mon.bloodPressure, '--/--'), map: text(mon.map, '--'),
          spo2: text(mon.spo2, '--'), pr: text(mon.pulseRate || mon.pulse, '--'),
          temp: text(mon.bodyTemp, '--'), rr: text(mon.resp, '--')
        };
        D.waveLive = {
          ecg: arrNums(mon.ecgWaveSamples), pleth: arrNums(mon.spo2WaveSamples), resp: arrNums(mon.respWaveSamples)
        };
      } else {
        D.vitals = { hr: '--', bp: '--/--', map: '--', spo2: '--', pr: '--', temp: '--', rr: '--' };
        D.waveLive = null;
      }
      /* ★ 任务38：主机/监护蓝牙连接状态边沿弹窗（"连接上给个弹窗"），断开也提示便于定位掉线 */
      var hostNow = !!((S.host && S.host.connected) || (S.ble && S.ble.host && S.ble.host.connected));
      var monNow = !!mon.connected;
      var hostDevName = text(((S.ble && S.ble.host) || S.host || {}).deviceName, '');
      var monDevName = text(mon.deviceName, '');
      if (prevHostConn !== null && hostNow !== prevHostConn) {
        toast(hostNow ? (TT('bleHostConnOk', '主机蓝牙已连接') + (hostDevName ? '：' + hostDevName : ''))
          : TT('bleHostConnLost', '主机蓝牙已断开'));
      }
      if (prevMonConn !== null && monNow !== prevMonConn) {
        toast(monNow ? (TT('bleMonConnOk', '监护蓝牙已连接') + (monDevName ? '：' + monDevName : ''))
          : TT('bleMonConnLost', '监护蓝牙已断开'));
      }
      prevHostConn = hostNow;
      prevMonConn = monNow;
      /* ★ Fix D（2026-10-08）：蓝牙连接态翻转时立即重绘当前屏，保证监护页与连接页蓝牙按钮状态即时同步 */
      scheduleRender();
      /* ★ 任务22：BPM 血压仪（监护页「实时⇄物理」）。S.bpm 由原生 buildFirstPhaseStateJson 下发；
         mode: 'manual'=物理 / 'auto'=实时，数值实时/物理同数据源（设备侧模式不同） */
      var bpm = S.bpm || {};
      D.bpm = {
        sys: text(bpm.sys, ''), dia: text(bpm.dia, ''), mean: text(bpm.mean, ''),
        pr: text(bpm.pr, ''), error: text(bpm.error, ''), mode: text(bpm.mode, '')
      };
      D.bpMode = (bpm.mode === 'manual') ? 'physical' : 'live';
      D.ctrlRow1 = [
        { tKey: 'ctrlTemp', t: '舱内温度 ℃', v: num(h.temp, '--'), p: 60, set: 1, ico: 'thermo' },
        { tKey: 'ctrlO2', t: '氧浓度 %', v: num(h.oxygen, '--'), p: 43, set: 1, ico: 'o2' },
        { tKey: 'ctrlHum', t: '湿度 %', v: num(h.humidity, '--'), p: 35, set: 1, ico: 'drop' },
        { tKey: 'ctrlCo2', t: '二氧化碳浓度 PPM', v: num(h.co2, '--'), p: 66, set: 1, ico: 'co2' },
        { tKey: 'ctrlLevel', t: '监护等级', v: '', level: 1 }
      ];
      D.ctrlRow2 = [
        { tKey: 'ctrlRed', t: '红外理疗', v: durText(c.redTherapy, c.redTherapyUnlimited, c.redTherapyRemainingMs), on: !!c.redTherapyOn, ico: 'rays' },
        { tKey: 'ctrlBlue', t: '蓝光理疗', v: durText(c.blueTherapy, c.blueTherapyUnlimited, c.blueTherapyRemainingMs), on: !!c.blueTherapyOn, ico: 'blueLight' },
        { tKey: 'ctrlUv', t: '紫外消毒', v: c.uvUnlimited ? TT('uv24h', '24h常开') : (c.uvOn ? TT('on', '开') : TT('off', '关')), on: !!c.uvOn, ico: 'uv24' },
        { tKey: 'ctrlNeb', t: '雾化器', v: durText(c.nebulizer, c.nebulizerUnlimited, c.nebulizerRemainingMs), on: !!c.nebulizerOn, ico: 'neb' },
        { tKey: 'ctrlAnion', t: '负离子', v: durText(c.anion, c.anionUnlimited, c.anionRemainingMs), on: !!c.anionOn, ico: 'neg' }
      ];
      D.ctrlRow3 = [
        { tKey: 'ctrlCold', t: '冷光照明', v: '', on: !!c.coldLightOn, ico: 'cold' },
        { tKey: 'ctrlWarm', t: '暖光照明', v: '', on: !!c.warmLightOn, ico: 'sun' },
        { tKey: 'ctrlOuter', t: '外循环', v: '', on: !!c.outerCycleOn, ico: 'loopOut' },
        { tKey: 'ctrlInner', t: '内循环', v: '', on: !!c.innerCycleOn, ico: 'loopIn' },
        { tKey: 'ctrlTime', t: '治疗时长', v: text(h.treatmentTime, '--'), on: !!(h.treatmentTime && h.treatmentTime !== '--'), ico: 'spin', big: 1 }
      ];

      /* ★ 任务23：回顾页双击行预览治疗记录单。预览历史病例（不在 S.cases 当前舱列表）时
         用该病例对象直接渲染 sheet，不用 S.patient；原生回推到同病例后清除覆盖，回到全量数据 */
      if (sheetPreviewCase && S.patient && S.patient.caseId
        && S.patient.caseId === sheetPreviewCase.caseId) { sheetPreviewCase = null; }
      var p = sheetPreviewCase || S.patient || {};
      D.sheet.animal = p.petName || ''; D.sheet.no = p.caseNo || ''; D.sheet.owner = p.ownerName || '';
      D.sheet.sp = p.species || ''; D.sheet.doc = p.doctor || '';
      D.sheet.date = fmtDT((p.visitDate || '').slice(0, 10));
      D.sheet.cage = (S.zone === 'left') ? TT('cabinA', 'A舱') : (S.zone === 'right' ? TT('cabinB', 'B舱') : '');
      D.sheet.weight = p.weight || '--'; D.sheet.dept = p.department || '--';
      D.sheet.dur = fmtDT(text(p.treatmentStartTime, '')) + (p.treatmentEndTime && p.treatmentEndTime !== '进行中' ? ' ~ ' + fmtDT(p.treatmentEndTime) : '');
      /* ★ 任务29：体征行 / 出院建议 / 治疗效果由原生 patient 回推（#26c 编辑保存后持久化在病例上） */
      D.sheet.vitalsRows = (p.vitals || []).map(function (r) {
        return [r[0] || '', r[1] || '', r[2] || '', r[3] || '', r[4] || ''];
      });
      if (D.sheet.concl) {
        D.sheet.concl.selIdx = p.conclIdx || 0;
        D.sheet.concl.advice = p.advice || '';
      }
      var tr = sheetPreviewCase ? (sheetPreviewCase.treatments || []) : (S.treatments || []);
      D.sheet.proj = tr.slice(0, 6).map(function (t) { return { on: t.enabled ? 1 : 0, lab: t.itemName || '', val: t.lastValue || '' }; });
      D.recordRows = tr.map(function (t, i) {
        // ★ 任务13：第4列「是否录入报告」读原生 TreatmentEntry.includeInReport（此前硬编码 ✗）
        return [pad(i + 1), t.itemName || '', t.enabled ? '✓' : '✗',
          (t.includeInReport === false ? '✗' : '✓'),
          (t.period || TT('openPeriod', '打开时段')), t.average || '--', t.high || '--', t.low || '--'];
      });
    } catch (e) { /* noop */ }
  }

  /* 新建（两段式）：仅打开草稿弹窗，不调 patient_new —— 避免"取消"也生成空病例 */
  function openNewSampleDraft() {
    draftNewMode = true;
    IcuApp.go('new-sample');
  }

  function refresh() {
    pullSettings(); // ★ 任务12：先拉取 S5 设置（含 language），再注入 D
    /* ★ 任务38：切屏时关闭蓝牙选择弹窗并停止扫描，避免跨页残留扫描/弹窗 */
    var curScreen = IcuApp.current();
    if (prevScreen !== null && curScreen !== prevScreen && D.blePick) {
      bleAct(D.blePick, 'stop', '');
      D.blePick = null;
    }
    prevScreen = curScreen;
    /* 任务23：离开治疗记录单页后清除预览覆盖，避免陈旧历史病例残留 */
    if (sheetPreviewCase && IcuApp.current() !== 'record-sheet') sheetPreviewCase = null;
    /* 任务26a：离开记录单页未保存的编辑一并丢弃 */
    if (D.sheetEdit && IcuApp.current() !== 'record-sheet') D.sheetEdit = null;
    pullState(); syncD();
    // 新建草稿确认后：原生新病例已生成（pendingInitialEntry）→ 把暂存 payload 写入并跳 care-data
    if (pendingSaveAfterCreate && S.patient && S.patient.pendingInitialEntry) {
      var payload = pendingSaveAfterCreate;
      pendingSaveAfterCreate = null;
      // 草稿态 form 里 caseNo/caseId 为空，用原生新分配的填上后再 sync
      if (S.patient.caseNo) payload.caseNo = S.patient.caseNo;
      if (S.patient.recordNo) payload.recordNo = S.patient.recordNo;
      if (S.patient.caseId) payload.caseId = S.patient.caseId;
      syncPatient(payload);
      pendingNavAfterSave = 'care-data';
      return;
    }
    // 保存后跳转
    if (pendingNavAfterSave) { var g = pendingNavAfterSave; pendingNavAfterSave = null; IcuApp.go(g); return; }
    // 普通刷新：重渲染当前屏（若正在表单弹窗里输入则不打断）
    var cur = IcuApp.current();
    if (cur === 'new-sample' || cur === 'query' || cur === 'login') return;
    /* ★ 任务26a：记录单编辑态中不随原生推送重渲染，避免正在输入的内容被冲掉（一致性处理见 #26d） */
    if (cur === 'record-sheet' && D.sheetEdit) return;
    /* ★ 任务38：前导+尾随节流渲染，压住 AM4100 连接后的推送风暴（保护演示波形滚动） */
    scheduleRender();
  }

  /* 在 S.cases（全量列表，按 caseId 定位）中查找点击行对应的下标；与 careCases 过滤子集无关 */
  function indexOfCase(caseId) {
    if (!caseId || !S.cases || !S.cases.length) return -1;
    for (var i = 0; i < S.cases.length; i++) {
      if (S.cases[i] && S.cases[i].caseId === caseId) return i;
    }
    return -1;
  }

  /* ★ 任务26a：记录单编辑态 —— 进入编辑：把 D.sheet 可编辑字段快照进 D.sheetEdit */
  function openSheetEdit() {
    var s = D.sheet || {};
    D.sheetEdit = {
      owner: s.owner || '', weight: s.weight || '', dept: s.dept || '', doc: s.doc || '',
      proj: (s.proj || []).map(function (p) { return { on: p.on ? 1 : 0, val: p.val || '' }; }),
      env: (s.env || []).map(function (e) { return e[1] || ''; }),
      conclIdx: (s.concl && s.concl.selIdx) || 0,
      advice: (s.concl && s.concl.advice) || '',
      /* ★ 任务29（#26b）：体征行深拷贝进编辑暂存，增删行只改暂存，保存时统一收集 */
      vitals: (s.vitalsRows || []).map(function (r) { return [r[0] || '', r[1] || '', r[2] || '', r[3] || '', r[4] || '']; })
    };
    IcuApp.render();
  }
  /* ★ 任务29（#26d）：体征行校验 —— 时间 HH:MM（可带日期前缀），心率/血氧/体温为数字，血压为 收缩压/舒张压 */
  var VR_TIME = /^(\d{4}-\d{1,2}-\d{1,2}\s+)?([01]?\d|2[0-3]):[0-5]\d$/;
  var VR_BP = /^\d{2,3}\s*\/\s*\d{2,3}$/;
  function vitalsRowError(row, idx) {
    var label = TT('vrRow', '第{n}行').replace('{n}', idx + 1);
    if (!VR_TIME.test(row[0])) return label + TT('vrErrTime', '时间格式应为 HH:MM');
    if (row[1] && !/^\d{1,3}$/.test(row[1])) return label + TT('vrErrHr', '心率应为数字');
    if (row[2] && (!/^\d{1,3}$/.test(row[2]) || +row[2] > 100)) return label + TT('vrErrSpo2', '血氧应为 0-100 的数字');
    if (row[3] && !VR_BP.test(row[3])) return label + TT('vrErrBp', '血压格式应为 收缩压/舒张压');
    if (row[4] && !/^\d{2}(\.\d)?$/.test(row[4])) return label + TT('vrErrTemp', '体温应为数字（如 38.5）');
    return '';
  }
  /* 保存：收集 DOM 输入/勾选/体征行 → #26d 校验 → 写回 D.sheet → #26c act('update_record') 落本地存储 */
  function saveSheetEdit() {
    if (!D.sheetEdit) return;
    var s = D.sheet;
    document.querySelectorAll('.sheet [data-se]').forEach(function (el) {
      var k = el.getAttribute('data-se'), v = el.value;
      if (k === 'owner') s.owner = v;
      else if (k === 'weight') s.weight = v;
      else if (k === 'dept') s.dept = v;
      else if (k === 'doc') s.doc = v;
      else if (k === 'advice') { if (s.concl) s.concl.advice = v; }
      else if (k.indexOf('proj-val-') === 0) { var i = +k.slice(9); if (s.proj[i]) s.proj[i].val = v; }
      else if (k.indexOf('env-') === 0) { var j = +k.slice(4); if (s.env[j]) s.env[j][1] = v; }
    });
    document.querySelectorAll('.tproj .p.ed').forEach(function (el) {
      var i = +el.getAttribute('data-proj-i');
      var cb = el.querySelector('.ckbox');
      if (s.proj[i] && cb) s.proj[i].on = cb.classList.contains('on') ? 1 : 0;
    });
    var sel = document.querySelector('.concl .opt.ed .ckbox.on');
    if (sel && s.concl) s.concl.selIdx = +sel.parentNode.getAttribute('data-concl-i');
    /* ★ #26b：收集体征行（整行空白剔除） */
    var rows = [];
    document.querySelectorAll('.sheet-tbl tr.vr-row').forEach(function (tr) {
      var r = ['', '', '', '', ''];
      tr.querySelectorAll('input[data-vr]').forEach(function (inp) {
        var c = +inp.getAttribute('data-vr').split('-')[1];
        if (c >= 0 && c < 5) r[c] = (inp.value || '').trim();
      });
      if (r.join('') !== '') rows.push(r);
    });
    /* ★ #26d：保存前校验，不合法则提示并中止（停留编辑态） */
    for (var vi = 0; vi < rows.length; vi++) {
      var err = vitalsRowError(rows[vi], vi);
      if (err) { toast(err + TT('vrFix', '，请修改后再保存')); return; }
    }
    s.vitalsRows = rows;
    /* ★ #26c：落本地 —— 组装 payload 发原生，Java 侧写入病例并持久化到 SharedPreferences */
    var payload = {
      caseId: (sheetPreviewCase && sheetPreviewCase.caseId) || (S.patient && S.patient.caseId) || D.selCaseId || '',
      zone: S.zone || '',
      owner: s.owner, weight: s.weight, dept: s.dept, doc: s.doc,
      advice: (s.concl && s.concl.advice) || '',
      conclIdx: (s.concl && s.concl.selIdx) || 0,
      proj: (s.proj || []).map(function (p, i) { return { i: i, on: p.on ? 1 : 0, val: p.val || '' }; }),
      vitals: rows
    };
    act('update_record', JSON.stringify(payload));
    D.sheetEdit = null;
    IcuApp.render();
  }

  /* ---------- 表单：把静态占位 div 转成真实 input ---------- */
  function fieldOfLabel(label) {
    var m = {
      '住院号': 'caseNo', '宠物名': 'petName', '年龄': 'age', '性别': 'sex',
      '物种': 'species', '宠物主人': 'ownerName', '联系电话': 'ownerPhone', '主治医生': 'doctor'
    };
    return m[label] || label;
  }
  /* ★ V1.02·任务9 字段长度限制（防止输入信息过长撑破布局）
     ★ 2026-10-08（#40b）名字类字段再缩短：宠物名/主人/医生 30→10，电话 11、年龄 3 不变 */
  var FIELD_LIMITS = {
    petName: 10, age: 3, ownerName: 10, ownerPhone: 11, doctor: 10
  };
  function fieldLimit(field) { return FIELD_LIMITS[field] || 0; }
  function fieldMaxLen(field) {
    var n = fieldLimit(field);
    return n > 0 ? String(n) : '';
  }
  function inp(val, field, type) {
    var max = fieldMaxLen(field);
    var maxAttr = max ? ' maxlength="' + max + '"' : '';
    /* 年龄额外标记 data-numonly：patchNewSample 装 input 事件过滤非数字；
       任务32：联系电话标记 data-tel，由 app-root 委托过滤非电话字符；
       2026-10-08（#39a）：联系电话改 data-mobile 纯数字过滤，长度 11（标准手机号） */
    var extra = (field === 'age') ? ' data-numonly="1"' : ((field === 'ownerPhone') ? ' data-mobile="1"' : '');
    /* ★ 任务16：字段占位符（取消后重开弹窗字段为空但有占位提示，不再是"没有任何数据"的观感） */
    var FIELD_PH = { petName: TT('phPetName', '请输入宠物名'), age: TT('phAge', '请输入年龄'), ownerName: TT('phOwner', '请输入宠物主人'), ownerPhone: TT('phPhone', '请输入联系电话'), doctor: TT('phDoctor', '请输入主治医生') };
    var ph = FIELD_PH[field] || '';
    var phAttr = ph ? ' placeholder="' + ph + '"' : '';
    return '<input class="icu-input" data-field="' + field + '"' + extra + maxAttr
      + ' type="' + (type || 'text') + '" value="' + (val || '') + '"' + phAttr + '>';
  }
  /* 任务9：把发送数据页的弹层状态（文件助手二维码 / 蓝牙正在发送）应用到 DOM。
     原生 action 后会 invalidate 重渲染，状态存 sendingCard 以便渲染后恢复 */
  function applySendingCard() {
    var qw = document.querySelector('.send-card.qrcard');
    var bw = document.querySelector('.send-card.blecard');
    if (qw) qw.hidden = sendingCard !== 'file';
    if (bw) bw.hidden = sendingCard !== 'ble';
    /* ★ 任务16(#11)：菜单项高亮跟随后选择的项 —— 未点击选择时全部不高亮 */
    document.querySelectorAll('.send-menu .mi').forEach(function (x) {
      if (x.classList) x.classList.toggle('on', x.dataset.send === sendingCard);
    });
    if (sendingCard === 'file') renderShareQr(false);
  }
  /* ★ 任务28（V1.02 R25/R42 方案A）：渲染文件助手二维码。
     数据源为原生回推的 S.shareQr {url,error,fileName}：
     - pending=true（刚点击、原生尚未回推）显示「生成中…」；
     - error 非空显示失败文案（对应验收"失败有提示"）；
     - url 非空用 qrcode-generator 渲染真实二维码（内容为局域网下载页地址）。 */
  function renderShareQr(pending) {
    var box = document.getElementById('shareQrBox');
    if (!box) return;
    var sq = S.shareQr || {};
    var msg = function (t) { return '<span class="qr-msg">' + String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</span>'; };
    if (sq.url) {
      if (box.dataset.url === sq.url) return;
      try {
        var qr = qrcode(0, 'M');
        qr.addData(sq.url);
        qr.make();
        box.innerHTML = qr.createImgTag(5, 8);
        box.dataset.url = sq.url;
      } catch (e) {
        box.innerHTML = msg(TT('qrFail', '二维码生成失败'));
        box.dataset.url = '';
      }
      return;
    }
    if (sq.error) {
      var key = '!' + sq.error;
      if (box.dataset.url === key) return;
      box.innerHTML = msg(sq.error);
      box.dataset.url = key;
      return;
    }
    box.innerHTML = msg(pending ? TT('qrPending', '二维码生成中…') : '');
    box.dataset.url = '';
  }
  /* ★ 任务16(#11)：把菜单挂进「发送数据」按钮内部，绝对定位在按钮正上方水平居中
   —— 与按钮天然对齐，不受视口/缩放影响；未点击选择时无高亮（点击后由 applySendingCard 点亮） */
  function alignSendMenu() {
    var m = document.querySelector('.send-menu');
    if (!m || !document.body.contains(m)) return;
    if (m.parentElement && m.parentElement.classList.contains('bbtn')) return; // 已挂好
    var b = document.querySelector('.bottombar .bbtn[data-key="dataSend"]');
    if (!b) {
      var sdTxt = TT('sendData', '发送数据');
      [].some.call(document.querySelectorAll('.bottombar .bbtn'), function (x) {
        var tc = x.textContent || '';
        if (tc.indexOf('发送数据') >= 0 || tc.indexOf(sdTxt) >= 0) { b = x; return true; }
        return false;
      });
    }
    if (!b) return;
    b.appendChild(m);
    m.classList.add('docked');
  }

  function patchNewSample() {
    var modal = document.querySelector('.modal.wide');
    if (!modal || modal.dataset.patched) return;
    modal.dataset.patched = '1';
    var p = S.patient || {};
    // 标题区分：草稿新建 / 待录入 → 新建样本；编辑现有病例 → 编辑样本
    var mh = modal.querySelector('.mhead');
    if (mh) mh.textContent = (draftNewMode || p.pendingInitialEntry) ? TT('newSampleTitle', '新建样本') : TT('editSampleTitle', '编辑样本');
    modal.querySelectorAll('.frow').forEach(function (fr) {
      var lab = fr.querySelector('label');
      if (!lab) return;
      // ★ 任务12：优先用稳定 data-row 键（与语言无关）；标签反查仅作兜底
      var key = fr.dataset.row
        || fieldOfLabel(lab.textContent.replace(/[*\s]/g, ''));
      if (key === 'species' || key === 'sex') return; // chips 保持
      var ctl = fr.querySelector('.ctl');
      var holder = ctl && ctl.querySelector('.inp.flex');
      if (!holder) return;
      var val = p[key] || '';
      if (key === 'caseNo') {
        if (draftNewMode) {
          // 草稿态：住院号由原生生成，UI 显示"自动生成"占位、不可编辑
          holder.outerHTML = '<div class="inp flex"><input class="icu-input" data-field="caseNo" value="" placeholder="' + TT('autoGen', '自动生成') + '" readonly style="background:#eef3fb;color:#8b95a6"></div>';
          return;
        }
        val = p.caseNo || p.recordNo || '';
      }
      if (key === 'age') {
        // ★ 任务12：年龄单位下拉（天/月/岁），编辑态预选 S.patient.ageUnit；选项随语言切换
        var auVal = (p && p.ageUnit) || '月';
        var auUnits = ['天', '月', '岁'];
        var auTKeys = { '天': 'ageDay', '月': 'ageMonth', '岁': 'ageYear' };
        var T = window.T || function (k, fb) { return fb; };
        var auOpts = auUnits.map(function (u) {
          return '<option value="' + u + '"' + (u === auVal ? ' selected' : '') + '>' + T(auTKeys[u], u) + '</option>';
        }).join('');
        holder.outerHTML = '<div class="inp flex icu-field-row">' + inp(val, 'age', 'text')
          + '<select data-ageunit class="age-unit">' + auOpts + '</select></div>';
        return;
      }
      holder.outerHTML = '<div class="inp flex">' + inp(val, key, key === 'ownerPhone' ? 'tel' : 'text') + '</div>';
    });
    /* 年龄字段：input 事件过滤非数字并截断到 3 位 */
    modal.querySelectorAll('input[data-numonly]').forEach(function (i) {
      i.addEventListener('input', function () { this.value = this.value.replace(/[^0-9]/g, '').slice(0, 3); });
    });
    /* 初始化右侧剪影：优先用 S.patient.species；没有则取当前选中的 species chip data-spec/text；都没有用 '兔' 兜底 */
    var sil = modal.querySelector('#petSil');
    var speciesMap = { 'dog': '犬', 'cat': '猫', 'rabbit': '兔', 'lizard': '蜥蜴', 'snake': '蛇', 'other': '其它' };
    function silNameOf(chip) { if (!chip) return '兔'; return speciesMap[chip.dataset.spec] || chip.textContent || '兔'; }
    if (sil) {
      var silName = (p && p.species) || '';
      if (!silName) {
        var chipsGroups = modal.querySelectorAll('.frow .chips');
        if (chipsGroups.length >= 2) {
          var onSp = chipsGroups[1].querySelector('.chip.on');
          if (onSp) silName = silNameOf(onSp);
        }
      }
      if (!silName) silName = '兔';
      sil.innerHTML = window.silSvg ? window.silSvg(silName) : '';
    }
    /* ★ 任务12：物种「其它」chip → 显示/隐藏自填输入框，并更新剪影 */
    var spGroups = modal.querySelectorAll('.frow .chips');
    if (spGroups.length >= 2) {
      var spGroup = spGroups[1];
      var otherBox = modal.querySelector('.species-other');
      spGroup.querySelectorAll('.chip').forEach(function (c) {
        c.addEventListener('click', function () {
          var on = spGroup.querySelector('.chip.on');
          var isOther = !!(on && (on.dataset.spec === 'other' || on.textContent === '其它'));
          if (otherBox) {
            otherBox.hidden = !isOther;
            if (!isOther) { var oi = otherBox.querySelector('input'); if (oi) oi.value = ''; }
          }
          var sil2 = modal.querySelector('#petSil');
          if (sil2) sil2.innerHTML = window.silSvg ? window.silSvg(silNameOf(on)) : '';
        });
      });
    }
  }
  function gatherNewSample() {
    var modal = document.querySelector('.modal.wide');
    if (!modal) return null;
    var o = {};
    modal.querySelectorAll('input[data-field]').forEach(function (i) { o[i.dataset.field] = i.value.trim(); });
    // 性别 / 物种：取选中 chip（优先 data-sex/data-spec，避免翻译后文本变化）
    var allChips = modal.querySelectorAll('.frow .chips');
    if (allChips.length >= 1) {
      var s = allChips[0].querySelector('.chip.on');
      if (s) o.sex = s.dataset.sex || s.textContent;
    }
    if (allChips.length >= 2) {
      var sp = allChips[1].querySelector('.chip.on');
      if (sp) o.species = sp.dataset.spec || sp.textContent;
    }
    // ★ 任务12：物种选「其它」时取自填输入框（用 key 判断，语言无关）
    var otherInp = modal.querySelector('input[data-other-species]');
    var otherKey = (allChips.length >= 2) ? (allChips[1].querySelector('.chip.on') || {}).dataset.spec : '';
    if ((o.species === '其它' || otherKey === 'other') && otherInp) o.species = otherInp.value.trim() || '其它';
    // ★ 任务12：年龄单位读取下拉（天/月/岁）
    var auSel = modal.querySelector('select[data-ageunit]');
    o.caseNo = o.caseNo || (S.patient && S.patient.caseNo) || '';
    o.recordNo = o.caseNo;
    o.ageUnit = auSel ? auSel.value : '月';
    o.visitDate = (S.patient && S.patient.visitDate) || '';
    return o;
  }
  /* ★ 任务12：新建样本必填 / 格式校验；返回错误文案（空=通过） */
  function validateNewSample(o) {
    var T = (typeof window !== 'undefined' && window.T) ? window.T : function (k, fb) { return fb; };
    if (!o) return T('errForm', '表单数据无效');
    var name = (o.petName || '').trim();
    if (!name) return T('errPetName', '宠物名不能为空');
    var phone = (o.ownerPhone || '').trim();
    if (!phone) return T('errPhoneEmpty', '联系电话不能为空');
    // 中国大陆手机号 11 位；放宽允许 7~12 位数字（座机/分机）
    if (!/^1[3-9]\d{9}$/.test(phone) && !/^\d{7,12}$/.test(phone)) return T('errPhoneFmt', '联系电话格式不正确');
    return '';
  }

  function patchQuery() {
    var modal = document.querySelector('.qmodal');
    if (!modal || modal.dataset.patched) return;
    modal.dataset.patched = '1';
    var rows = modal.querySelectorAll('.qrow');
    /* 标签 → 字段键映射：中文 + 当前语言文本都认（英文界面下 label 已翻译） */
    var qMap = { '护疗时间': 'time', '住院号': 'caseNo', '宠物名': 'petName', '宠物主人': 'ownerName', '联系电话': 'ownerPhone' };
    qMap[TT('qCareTime', '护疗时间')] = 'time';
    qMap[TT('fieldCaseNo', '住院号')] = 'caseNo';
    qMap[TT('fieldPetName', '宠物名')] = 'petName';
    qMap[TT('fieldOwner', '宠物主人')] = 'ownerName';
    qMap[TT('fieldPhone', '联系电话')] = 'ownerPhone';
    rows.forEach(function (r) {
      var lab = r.querySelector('label');
      var key = lab ? qMap[lab.textContent] : '';
      if (!key) return;
      var qinps = r.querySelectorAll('.qinp');
      qinps.forEach(function (q, idx) {
        var field = (key === 'time') ? (idx === 0 ? 'timeFrom' : 'timeTo') : (key === 'caseNo' ? 'caseNo' : key);
        if (key === 'petName' && idx === 1) field = 'doctor'; // 第二格是“医生”
        var span = q.querySelector('span');
        var val = span ? span.textContent : '';
        q.outerHTML = '<div class="qinp"><input class="icu-input" data-q="' + field + '" value="' + val + '"></div>';
      });
    });
  }
  function gatherQuery() {
    var modal = document.querySelector('.qmodal');
    if (!modal) return null;
    var o = {};
    modal.querySelectorAll('input[data-q]').forEach(function (i) { o[i.dataset.q] = i.value.trim(); });
    var sp = modal.querySelector('.qchips .chip.on'); if (sp) o.species = sp.dataset.v || sp.textContent;
    return o;
  }

  /* ---------- 登录 ---------- */
  function loginSubmit() {
    var box = document.querySelector('.login-box') || document.querySelector('.login-card');
    var inputs = box ? box.querySelectorAll('input') : [];
    var name = inputs[0] ? inputs[0].value.trim() : '';
    var pwd = inputs[1] ? inputs[1].value : '';
    if (!name || !pwd) { return; }
    var ok = doLogin(name, pwd);
    if (ok) { pullState(); syncD(); IcuApp.setScreen('care'); }
  }

  /* ---------- 主控开关映射 ---------- */
  function controlActionOf(title) {
    var m = {
      '红外理疗': 'control_red', '蓝光理疗': 'control_blue', '紫外消毒': 'control_uv',
      '雾化器': 'control_nebulizer', '负离子': 'control_anion',
      '冷光照明': 'control_cold_light', '暖光照明': 'control_warm_light',
      '外循环': 'control_outer', '内循环': 'control_inner', '治疗时长': 'control_time'
    };
    return m[title] || '';
  }
  function settingsActionOf(title) {
    var m = {
      '舱内温度 ℃': 'control_temp', '氧浓度 %': 'control_oxygen',
      '湿度 %': 'control_humidity', '二氧化碳浓度 PPM': 'control_co2', '治疗时长': 'control_time'
    };
    return m[title] || '';
  }
  /* ★ 任务12：主控卡动作按 data-tkey 路由（与显示语言解耦） */
  var CTRL_ACT_BY_TKEY = {
    ctrlTemp: 'control_temp', ctrlO2: 'control_oxygen', ctrlHum: 'control_humidity', ctrlCo2: 'control_co2', ctrlLevel: '',
    ctrlRed: 'control_red', ctrlBlue: 'control_blue', ctrlUv: 'control_uv', ctrlNeb: 'control_nebulizer', ctrlAnion: 'control_anion',
    ctrlCold: 'control_cold_light', ctrlWarm: 'control_warm_light', ctrlOuter: 'control_outer', ctrlInner: 'control_inner', ctrlTime: 'control_time'
  };
  var CTRL_SET_BY_TKEY = {
    ctrlTemp: 'control_temp', ctrlO2: 'control_oxygen', ctrlHum: 'control_humidity', ctrlCo2: 'control_co2', ctrlTime: 'control_time'
  };

  /* ---------- 点击拦截（capture，先于 app.js 的 bubble 处理） ---------- */
  function stop(e) { e.preventDefault(); e.stopImmediatePropagation(); }
  function onClick(e) {
    if (!hasNative()) return;
    var cur = IcuApp.current();
    var n = N();

    /* ★ 2026-10-08 日志页：「更新」按钮重新拉取原生状态（userLogs 等）并重渲染 */
    if (cur === 'log') {
      var lr = e.target.closest('[data-logrefresh]');
      if (lr) { stop(e); pullState(); syncD(); IcuApp.render(); return; }
    }

    /* ★ 2026-10-08 蓝牙设备选择弹窗：点设备=连接；重新搜索；取消=停止扫描并关闭 */
    if (D.blePick) {
      var bd = e.target.closest('[data-bledev]');
      if (bd) {
        stop(e);
        var tgt = D.blePick;
        D.blePick = null;
        bleAct(tgt, 'connect', bd.dataset.bledev || '');
        IcuApp.render();
        return;
      }
      if (e.target.closest('[data-blepick-rescan]')) {
        stop(e);
        bleAct(D.blePick, 'scan', '');
        IcuApp.render();
        return;
      }
      if (e.target.closest('[data-blepick-close]')) {
        stop(e);
        bleAct(D.blePick, 'stop', '');
        D.blePick = null;
        IcuApp.render();
        return;
      }
    }

    /* ★ 2026-10-08 监护页「蓝牙/监护宝」按钮：已连接=断开；未连接=打开设备选择（与连接页一致） */
    if (cur === 'monitor' || cur === 'monitor-unit') {
      var ma = e.target.closest('[data-act]');
      if (ma && (ma.dataset.act === 'host_ble' || ma.dataset.act === 'monitor_ble')) {
        stop(e);
        var target2 = ma.dataset.act === 'host_ble' ? 'host' : 'monitor';
        if (bleChannel(target2).connected) {
          act(ma.dataset.act + '_toggle', JSON.stringify({ enabled: false }));
        } else {
          openBlePick(target2);
        }
        return;
      }
    }

    // 登录页：登录按钮
    if (cur === 'login') {
      var lb = e.target.closest('.lb');
      if (lb) { stop(e); loginSubmit(); return; }
      return; // 其余交给 app.js（如 splash→login）
    }
    // 设置·常规：语言切换（中文/English）→ 保存到原生 S5 设置并即时重渲染
    if (cur === 'set-general') {
      var langBtn = e.target.closest('[data-lang]');
      if (langBtn) {
        stop(e);
        // ★ 任务12：先本地即时切换 D.lang 并重渲染当前屏，再通知原生持久化
        D.lang = langBtn.dataset.lang;
        window.__icuSettings = window.__icuSettings || {};
        window.__icuSettings.language = langBtn.dataset.lang;
        act('settings_save', JSON.stringify({ language: langBtn.dataset.lang }));
        IcuApp.render();
        return;
      }
    }

    // 主控页：卡片开关 / 设置按钮 / 监护等级三色点
    if (cur === 'control') {
      var lvlDot = e.target.closest('[data-level] i[data-lvl]');
      if (lvlDot) {
        stop(e); act('monitor_level_' + lvlDot.dataset.lvl);
        return;
      }
      var sw = e.target.closest('[data-sw]');
      var setbtn = e.target.closest('.setbtn');
      if (sw) {
        var card = sw.closest('.ctrl');
        var tk = card && card.dataset.tkey;
        var a = tk ? CTRL_ACT_BY_TKEY[tk] : '';
        if (a) { stop(e); act(a); }
        return;
      }
      if (setbtn) {
        var card2 = setbtn.closest('.ctrl');
        var tk2 = card2 && card2.dataset.tkey;
        var a2 = tk2 ? CTRL_SET_BY_TKEY[tk2] : '';
        if (a2) { stop(e); act(a2); }
        return;
      }
      // 模式卡（母幼/术后/心肺/自定义）
      var mode = e.target.closest('.modecard');
      if (mode) {
        var mkey = mode.dataset.mkey;
        if (mkey) { stop(e); act(mkey); }
        return;
      }
      return;
    }

    // 状态页快捷按钮（雾化器/暖光灯/内循环）+ 监护等级三色点 + 摄像头画面点击全屏
    if (cur === 'status') {
      var lvlDot2 = e.target.closest('[data-level] i[data-lvl]');
      if (lvlDot2) {
        stop(e); act('monitor_level_' + lvlDot2.dataset.lvl);
        return;
      }
      var camBig = e.target.closest('.cam.big');
      if (camBig) { stop(e); act('camera_fullscreen'); return; }
      var wb = e.target.closest('.wbtn');
      if (wb) {
        var wa = wb.dataset.act;
        if (wa) { stop(e); act(wa); }
        return;
      }
    }

    // 监护页底部操作按钮
    if (cur === 'monitor') {
      /* ★ 任务22：血压「实时⇄物理」→ 原生 bpm_mode（物理=manual 手动模式，实时=auto 自动模式）。
         不 stop：app.js 的本地高亮先生效，原生回推 S.bpm.mode 后渲染对齐 */
      var bpmBtn = e.target.closest('.bpsw button[data-bpm]');
      if (bpmBtn) {
        act('bpm_mode', bpmBtn.dataset.bpm === 'physical' ? 'manual' : 'auto');
      }
      var mb = e.target.closest('.mbtn');
      if (mb) {
        var ma = mb.dataset.act;
        if (ma) { stop(e); act(ma); }
        return;
      }
    }

    // 视频页按钮：截图/录制/全屏/视频参数(=摄像头连接配置) + 画面点击全屏
    if (cur === 'video' || cur === 'video-play') {
      var vb = e.target.closest('.vbtn');
      if (vb) {
        var va = vb.dataset.act;
        if (va) {
          if (va === 'camera_fullscreen') { stop(e); act('camera_fullscreen'); return; }
          if (va === 'camera_snapshot') { stop(e); act('camera_snapshot'); return; }
          if (va === 'camera_record') { stop(e); act('camera_record'); return; }
          if (va === 'camera_config') { stop(e); act('camera_config'); return; }
          return;
        }
      }
      var fisheye = e.target.closest('.video-view');
      if (fisheye && cur === 'video') { stop(e); act('camera_fullscreen'); return; }
    }

    // 发送数据页：菜单选择 → 二维码/蓝牙发送卡片；点击 mask 背景关闭卡片
    if (cur === 'sending') {
      var sItem = e.target.closest('.send-menu .mi');
      if (sItem) {
        stop(e);
        var kind = sItem.dataset.send;
        if (kind === 'file') {
          sendingCard = 'file';
          applySendingCard();
          renderShareQr(true);      // 任务28：先显示"生成中…"，再请原生托管报告并回推下载 URL
          act('export_qr');
        } else if (kind === 'ble') {
          sendingCard = 'ble';
          applySendingCard();
          act('bluetooth_send_report'); // 任务9：蓝牙传输走 S4 既有蓝牙发送（报告 PDF + 系统蓝牙分享）
        }
        return;
      }
      var maskBg = e.target.closest('.mask');
      if (maskBg && maskBg === e.target) {
        stop(e);
        if (sendingCard === 'file') act('export_qr_clear'); // 任务28：关闭二维码卡片时注销本次分享
        sendingCard = null;
        applySendingCard();
        return;
      }
    }

    // 会话底栏：结束 = 结束护疗 + 回护理列表
    var sess = e.target.closest('.sessbar .bbtn');
    if (sess) {
      if (sess.dataset.key === 'finish') { stop(e); act('treatment_finish'); IcuApp.go('care'); return; }
      // 其余（状态/主控/监护/视频）交给 app.js 内部跳转
      return;
    }

    // 表格行点击 → patient_select_<zone>_<index>（让"选中的样本"真实切换，编辑/开始护疗/删除作用于该行）
    if (cur === 'care' || cur === 'care-data' || cur === 'review' || cur === 'care-record') {
      var trHit = e.target.closest('tr[data-caseid]');
      if (trHit && !e.target.closest('input')) {
        var cid = trHit.dataset.caseid;
        var idx = indexOfCase(cid);
        if (idx >= 0 && S.zone) {
          act('patient_select_' + S.zone + '_' + idx);
          // ★ 任务18：护理页/护理列表点行立即记录选中样本（供「编辑」按钮门禁用，无需等原生回推）
          if (cur === 'care' || cur === 'care-data') { D.selCaseId = cid; }
          // ★ 任务16(#5)：护疗记录页点行 → 选中后跳转到「治疗记录数据表」（11-1.png 表单页）
          if (cur === 'care-record') {
            stop(e);
            D.selCaseId = cid;             // 立即高亮，不等原生回推
            IcuApp.go('record-data');
            return;
          }
          // 不 stop，让 app.js 继续切 'sel' 视觉态
        }
      }
    }

    // 护理列表 / 回顾 列表底栏按钮（按 data-key 路由，与显示语言解耦；文本作为兜底）
    var bb = e.target.closest('.bottombar .bbtn');
    if (bb && !bb.classList.contains('dis')) {
      var bspan = bb.querySelector('span');
      var key = bb.dataset.key || (bspan ? bspan.textContent : '');
      if (cur === 'care' || cur === 'care-data' || cur === 'care-record') {
        if (key === 'newSample') { stop(e); openNewSampleDraft(); return; }
        if (key === 'startCare') { stop(e); act('patient_start_treatment'); IcuApp.go('status'); return; }
        if (key === 'edit') {
          stop(e);
          var Tg = window.T || function (k, fb) { return fb; };
          var rows = D.careRows || [];
          if (!rows.length) { toast(Tg('reviewNoData', '暂无样本数据，无法执行该操作')); return; }
          var selEl = document.querySelector('tr.sel[data-caseid]');
          var selId = selEl ? selEl.getAttribute('data-caseid') : (D.selCaseId || '');
          var picked = rows.some(function (r) { return r.caseId && r.caseId === selId; });
          if (!picked) { toast(Tg('reviewNoSel', '请先选择一个样本')); return; }
          pendingEditor = true; IcuApp.go('new-sample'); return;
        } // 用 H5 弹窗就地编辑（不弹原生框）
        if (key === 'del') { stop(e); act('patient_delete'); return; }
        if (key === 'dataSend') { stop(e); sendingCard = null; IcuApp.go('sending'); return; } // 任务9：进入发送数据页（菜单里再选文件助手/蓝牙）
        if (key === 'tutorial') { stop(e); act('tutorial_operation_video'); return; }
        if (key === 'careRecord') { /* 交给 app.js 跳 care-record */ return; }
      }
      if (cur === 'review' || cur === 'printing' || cur === 'sending' || cur === 'del-confirm') {
        /* ★ 任务15(#9)：回顾页 — 无数据或未选样本时，禁止打印/导出报告/发送数据/删除 */
        var needSel = (key === 'print' || key === 'exportReport' || key === 'sendData' || key === 'del');
        if (needSel) {
          var Tg = window.T || function (k, fb) { return fb; };
          var rows = D.reviewRows || [];
          if (!rows.length) { stop(e); toast(Tg('reviewNoData', '暂无样本数据，无法执行该操作')); return; }
          var picked = rows.some(function (r) { return r.caseId && r.caseId === D.selCaseId; });
          if (!picked) { stop(e); toast(Tg('reviewNoSel', '请先选择一个样本')); return; }
        }
        if (key === 'query') { /* 交给 app.js 跳 query */ return; }
        if (key === 'print') { stop(e); act('print_report'); IcuApp.go('printing'); return; }
        if (key === 'exportReport') { stop(e); act('export_report'); IcuApp.go('sending'); return; }
        if (key === 'sendData') { stop(e); sendingCard = null; IcuApp.go('sending'); return; }
        if (key === 'careRecord') { /* 交给 app.js */ return; }
        if (key === 'del') { stop(e); act('patient_delete'); return; }
      }
    }

    // 弹窗底部按钮（按 data-foot 路由，文本兜底）
    var footBtn = e.target.closest('.mfoot .btn');
    if (footBtn) {
      var f = footBtn.dataset.foot || footBtn.textContent;
      /* 文本兜底时按当前语言文本比较（英文界面按钮文案已翻译） */
      var fConfirm = TT('confirm', '确认'), fCancel = TT('cancel', '取消'), fYes = TT('yes', '是');
      if (cur === 'new-sample') {
        if (f === 'confirm' || f === '确认' || f === fConfirm) {
          stop(e);
          var o = gatherNewSample();
          if (!o) return;
          // ★ 任务12：必填校验（宠物名 / 联系电话），不合格不保存
          var ve = validateNewSample(o);
          if (ve) { toast(ve); return; }
          if (draftNewMode) {
            // 新建草稿确认：先调 patient_new，等状态回推再 sync 写入并跳 care-data
            draftNewMode = false;
            pendingSaveAfterCreate = o;
            act('patient_new');
          } else {
            // 编辑现有病例：直接 sync
            syncPatient(o);
            pendingNavAfterSave = 'care-data';
          }
          return;
        }
        if (f === 'cancel' || f === '取消' || f === fCancel) {
          // 取消：草稿态不调 patient_new（不产生原生记录）；编辑态不保存
          stop(e);
          draftNewMode = false;
          pendingEditor = false;
          pendingSaveAfterCreate = null;
          IcuApp.go('care');
          return;
        }
      }
      if (cur === 'query') {
        if (f === 'confirm' || f === '确认' || f === fConfirm) { stop(e); var q = gatherQuery(); if (q) act('review_query', JSON.stringify(q)); IcuApp.go('review'); return; }
        if (f === 'cancel' || f === '取消' || f === fCancel) { stop(e); act('review_query_reset'); IcuApp.go('review'); return; }
      }
      if (cur === 'del-confirm') {
        if (f === 'confirm' || f === '确认' || f === fConfirm) { stop(e); act('patient_delete'); IcuApp.go('review'); return; }
      }
      if (cur === 'logout-confirm') {
        if (f === 'yes' || f === '是' || f === fYes) { stop(e); doLogout(); IcuApp.setScreen('login'); return; }
      }
      if (cur === 'exit-confirm') {
        if (f === 'yes' || f === '是' || f === fYes) { stop(e); IcuApp.go('exiting'); return; }
      }
    }

    // 记录单底栏
    if (cur === 'record-sheet') {
      /* ★ 任务26a：编辑态下的点选交互（项目勾选 / 结论三态），只改 DOM class，保存时统一收集 */
      if (D.sheetEdit) {
        /* ★ 任务29（#26b）：体征表增删行 —— 只改编辑暂存并重渲染，保存时才收集 */
        var vdel = e.target.closest('.vr-del');
        if (vdel) {
          stop(e);
          var di = +vdel.getAttribute('data-vr-del');
          if (D.sheetEdit.vitals && di >= 0 && di < D.sheetEdit.vitals.length) {
            D.sheetEdit.vitals.splice(di, 1);
          }
          IcuApp.render();
          return;
        }
        if (e.target.closest('.vr-add')) {
          stop(e);
          if (!D.sheetEdit.vitals) D.sheetEdit.vitals = [];
          if (D.sheetEdit.vitals.length >= 50) { toast(TT('maxVitalsRows', '最多 50 行体征记录')); return; }
          D.sheetEdit.vitals.push(['', '', '', '', '']);
          IcuApp.render();
          return;
        }
        if (!e.target.closest('.sheet-in')) {
          var pp = e.target.closest('.tproj .p.ed');
          if (pp) { stop(e); var pcb = pp.querySelector('.ckbox'); if (pcb) pcb.classList.toggle('on'); return; }
          var op = e.target.closest('.concl .opt.ed');
          if (op) {
            stop(e);
            document.querySelectorAll('.concl .opt.ed .ckbox').forEach(function (x) { x.classList.remove('on'); });
            var ocb = op.querySelector('.ckbox'); if (ocb) ocb.classList.add('on');
            return;
          }
        }
      }
      var rb = e.target.closest('.bottombar .bbtn');
      if (rb) {
        var rk = rb.dataset.key || (function () { var rsp = rb.querySelector('span'); return rsp ? rsp.textContent : ''; })();
        /* ★ 任务26a：编辑 / 保存 / 取消 */
        if (rk === 'recordEdit') { stop(e); openSheetEdit(); return; }
        if (rk === 'recordCancel') { stop(e); D.sheetEdit = null; IcuApp.render(); return; }
        if (rk === 'recordSave') { stop(e); saveSheetEdit(); return; }
        if (rk === 'print' || rk === '打印' || rk === TT('print', '打印')) { stop(e); act('print_report'); IcuApp.go('printing'); return; }
        if (rk === 'exportReport' || rk === '导出' || rk === TT('exportReport', '导出')) { stop(e); act('export_report'); IcuApp.go('sending'); return; }
        /* 上一样本/下一样本：在 S.cases 全量列表中定位当前样本，切换选中后停留本页并刷新 */
        var rkPrev = TT('recordPrev', '上一样本'), rkNext = TT('recordNext', '下一样本');
        if (rk === 'recordPrev' || rk === 'recordNext' || rk === 'p' || rk === '上一样本' || rk === 'n' || rk === '下一样本' || rk === rkPrev || rk === rkNext) {
          stop(e);
          var Tg3 = window.T || function (k, fb) { return fb; };
          var curId = (S.patient && S.patient.caseId) || D.selCaseId || '';
          var curIdx = indexOfCase(curId);
          if (curIdx < 0 || !S.cases || S.cases.length < 2) { toast(Tg3('recordNoSwitch', '暂无可切换的样本')); return; }
          var dir = (rk === 'recordPrev' || rk === 'p' || rk === '上一样本' || rk === rkPrev) ? -1 : 1;
          var nextIdx = curIdx + dir;
          if (nextIdx < 0) { toast(Tg3('recordFirstSample', '已经是第一个样本')); return; }
          if (nextIdx >= S.cases.length) { toast(Tg3('recordLastSample', '已经是最后一个样本')); return; }
          var nc = S.cases[nextIdx];
          if (nc && nc.caseId) D.selCaseId = nc.caseId;   // 立即更新，不等原生回推
          sheetPreviewCase = null;                        // 任务23：切换样本后回到真实病例数据
          act('patient_select_' + S.zone + '_' + nextIdx);
          return; // 原生状态回推后 refresh() 会重绘本页
        }
      }
    }

    // 菜单：注销/关闭/调试 → 按 data-go 路由（与显示语言解耦）
    if (cur === 'menu') {
      var tile = e.target.closest('.tile');
      if (tile) {
        var tg = tile.dataset.go;
        if (tg === 'logout-confirm') { stop(e); IcuApp.go('logout-confirm'); return; }
        if (tg === 'exit-confirm') { stop(e); IcuApp.go('exit-confirm'); return; }
        if (tg === 'x') { stop(e); IcuApp.go('comp'); return; } // 调试→补偿
        // set-general / about / log 交给 app.js 的 data-go
      }
    }

    /* ★ 任务13（R93）：菜单页舱区切换 —— 2026-09-30 用户确认：所有角色均可切换左/右舱 */
    if (cur === 'menu') {
      var zb = e.target.closest('[data-zone]');
      if (zb) {
        stop(e);
        act(zb.dataset.zone === 'right' ? 'tablet_zone_right' : 'tablet_zone_left');
        return;
      }
    }

    /* ★ 任务16(#3) → 2026-09-30 用户确认：调试页 —— 工程师直接进入；管理员/用户输密码进（不再有角色拦截） */
    if (cur === 'menu') {
      var dbg = e.target.closest('[data-go="comp"]');
      if (dbg) {
        stop(e);
        if ((D.accountRole || '') === 'service') { IcuApp.go('comp'); return; }
        /* ★ 任务21：非工程师 → 拦截默认跳转，弹出密码验证 */
        window.__debugPwdPending = true;
        IcuApp.render();
        return;
      }
      /* ★ 任务21：密码弹层里的「确认 / 取消」按钮 */
      if (window.__debugPwdPending) {
        var okBtn = e.target.closest('[data-action="debug-pwd-ok"]');
        if (okBtn) {
          stop(e);
          var inp = document.querySelector('.debug-pwd-input');
          var val = inp ? inp.value : '';
          if (val === DEBUG_PWD) {
            window.__debugPwdPending = false;
            IcuApp.go('comp');
          } else {
            toast((window.T || function (k, f) { return f; })('debugPwdErr', '调试密码错误，请重试'));
            /* 重渲染保持弹层挂载（不丢失输入焦点上下文），输入框随之清空 */
            IcuApp.render();
          }
          return;
        }
        var cancelBtn = e.target.closest('[data-action="debug-pwd-cancel"]');
        if (cancelBtn) {
          stop(e);
          window.__debugPwdPending = false;
          IcuApp.render();
          return;
        }
      }
    }

    /* ★ 任务18：常规页「演示数据」开关已移除（未连蓝牙时默认演示波形，见 syncD + monitorBody） */

    /* ★ 任务15(#12)：调试页第 6 个模块 —— 恢复出厂设置（二次确认）
       ★ 2026-09-30 用户确认：仅工程师可用，其余角色拦截 */
    if (cur === 'comp') {
      var facBtn = e.target.closest('[data-factory]');
      if (facBtn) {
        stop(e);
        if ((D.accountRole || '') !== 'service') { toast(TT('factoryNoPerm', '无权限：恢复出厂设置仅工程师可用')); return; }
        window.__factoryConfirm = true; IcuApp.render(); return;
      }
      var fyBtn = e.target.closest('[data-factory-confirm]');
      if (fyBtn) {
        stop(e);
        window.__factoryConfirm = false;
        if (fyBtn.dataset.factoryConfirm === 'yes') act('factory_reset');
        IcuApp.render();
        return;
      }
    }

    /* ★ 任务14（R58-R66）：设置-打印 —— 扫描/添加打印机/打印测试页/医院信息/LOGO/报告标题与声明 */
    if (cur === 'set-print') {
      var pb = e.target.closest('[data-print]');
      if (pb) {
        stop(e);
        var pk = pb.dataset.print;
        var T2 = window.T || function (k, fb) { return fb; };
        var val = function (sel) {
          var el = document.querySelector(sel);
          return el ? (el.value || '').trim() : '';
        };
        if (pk === 'scan') {
          // 原生无自动扫描能力：如实提示，避免"点了没反应"
          toast(T2('scanUnsupported', '暂不支持自动扫描，请手动填写打印机地址后添加'));
          return;
        }
        if (pk === 'addPrinter') {
          var addr = val('input[data-print-addr]');
          if (!addr) { toast(T2('errPrinterAddr', '请输入打印机地址')); return; }
          /* ★ 任务32：打印机地址格式校验（IPv4，可带端口） */
          if (!/^(\d{1,3}\.){3}\d{1,3}(:\d{1,5})?$/.test(addr)) { toast(T2('errPrinterAddrFmt', '打印机地址格式不正确，示例 192.168.1.10:9100')); return; }
          act('settings_save', JSON.stringify({ printerAddress: addr }));
          return;
        }
        if (pk === 'test') { act('print_test'); return; }
        /* ★ 任务32：医院信息保存前校验电话格式（可空；非空须为电话字符 5~20 位） */
        if (pk === 'org') {
          var oph = val('input[data-org="phone"]');
          if (oph && !/^[\d+][\d\-+ ]{4,19}$/.test(oph)) { toast(T2('errHospPhone', '医院电话格式不正确')); return; }
          act('organization_save'); return;
        }
        if (pk === 'logo') { act('organization_logo_pick'); return; }
        /* ★ 任务31：页脚LOGO 独立 action，此前与医院LOGO 同发 organization_logo_pick，
           原生无法区分，页脚LOGO 实际没有上传入口 */
        if (pk === 'footerLogo') { act('organization_footer_logo_pick'); return; }
        if (pk === 'reportTitle') {
          act('settings_save', JSON.stringify({ reportTitle: val('input[data-print-title]') }));
          return;
        }
        if (pk === 'reportDecl') {
          act('settings_save', JSON.stringify({ reportDeclaration: val('input[data-print-decl]') }));
          return;
        }
      }
    }

    /* ★ 任务13（R53-R55）：设置-连接 三个开关实际控制
       ★ 2026-10-08：蓝牙开关「开」改为弹出设备选择（主机/监护分开），「关」=断开 */
    if (cur === 'set-conn') {
      var ct = e.target.closest('[data-conn]');
      if (ct) {
        stop(e);
        var kind = ct.dataset.conn;
        var want = !ct.classList.contains('on');
        if (kind === 'wifi') { act('wifi_toggle', JSON.stringify({ enabled: want })); return; }
        var target = kind === 'host_ble' ? 'host' : 'monitor';
        if (want) {
          /* 未连接 → 打开设备选择弹窗（已连接时开关本应为 on，走不到这里） */
          openBlePick(target);
          return;
        }
        act(kind === 'host_ble' ? 'host_ble_toggle' : 'monitor_ble_toggle', JSON.stringify({ enabled: false }));
        return;
      }
    }

    /* ★ 任务13（R67-R73）：设置-用户 —— 行选择 / 新增 / 编辑 / 删除（二次确认）/ 取消 */
    if (cur === 'set-user' || cur === 'set-user-edit') {
      // 1) 选中某一行（点按钮或弹窗时不改变选中）
      var urow = e.target.closest('tr[data-uname]');
      if (urow && !e.target.closest('.btn') && !e.target.closest('.mfoot')) {
        stop(e);
        window.__selUser = urow.dataset.uname;
        IcuApp.render();
        return;
      }
      // 2) 删除二次确认弹窗
      var udel = e.target.closest('[data-udel]');
      if (udel) {
        stop(e);
        if (udel.dataset.udel === 'yes') {
          var delName = window.__userDelConfirm;
          window.__userDelConfirm = null;
          window.__selUser = null;
          act('account_delete', JSON.stringify({ name: delName }));
        } else {
          window.__userDelConfirm = null;
          IcuApp.render();
        }
        return;
      }
      // 3) 新增/编辑弹窗的 确认 / 取消
      var ufoot = e.target.closest('[data-ufoot]');
      if (ufoot) {
        stop(e);
        if (ufoot.dataset.ufoot === 'cancel') {
          window.__userEdit = null;
          IcuApp.go('set-user');
          return;
        }
        var uModal = document.querySelector('.modal');
        var nEl = uModal && uModal.querySelector('input[data-uname]');
        var pEl = uModal && uModal.querySelector('input[data-upwd]');
        var rEl = uModal && uModal.querySelector('select[data-urole]');
        var inName = nEl ? nEl.value.trim() : '';
        var inPwd = pEl ? pEl.value : '';
        var inRole = rEl ? rEl.value : '';
        var ed = window.__userEdit || { mode: 'add' };
        if (!inName) { toast(TT('errUserName', '请输入用户名')); return; }
        if (ed.mode === 'add' && !inPwd) { toast(TT('errPwdReq', '密码必填')); return; }
        /* ★ 任务32：密码长度下限（编辑态留空=不修改，不校验） */
        if (inPwd && inPwd.length < 4) { toast(TT('errPwdLen', '密码至少 4 位')); return; }
        if (!inRole) { toast(TT('errRoleReq', '权限必选')); return; }
        act('account_save', JSON.stringify({
          originalName: ed.mode === 'edit' ? ed.name : '',
          name: inName, password: inPwd, role: inRole, organization: ''
        }));
        window.__userEdit = null;
        IcuApp.go('set-user');
        return;
      }
      // 4) 底部按钮：新增 / 编辑/修改 / 删除 / 取消
      var ub = e.target.closest('[data-uact]');
      if (ub) {
        stop(e);
        var ua = ub.dataset.uact;
        /* ★ 2026-09-30 用户确认：账号管理（增/删/改）仅工程师/管理员可做，普通用户拦截 */
        var uRole = D.accountRole || '';
        if (ua !== 'cancel' && ua !== undefined && uRole !== 'service' && uRole !== 'admin') {
          toast(TT('userNoPerm', '无权限：账号管理仅工程师/管理员可用'));
          return;
        }
        var selName = window.__selUser;
        var selUser = (D.users || []).filter(function (x) { return x.name === selName; })[0];
        if (ua === 'add') {
          window.__userEdit = { mode: 'add', name: '', role: 'user' };
          IcuApp.go('set-user-edit');
          return;
        }
        if (ua === 'edit') {
          if (!selName) { toast(TT('errSelEditUser', '请先选择要编辑的用户')); return; }
          window.__userEdit = { mode: 'edit', name: selName, role: (selUser && selUser.role) || 'user' };
          IcuApp.go('set-user-edit');
          return;
        }
        if (ua === 'del') {
          if (!selName) { toast(TT('errSelDelUser', '请先选择要删除的用户')); return; }
          if (selUser && selUser.isCurrent) { toast(TT('errDelCurUser', '不能删除当前登录用户')); return; }
          window.__userDelConfirm = selName;
          IcuApp.render();
          return;
        }
        // 取消：清空选中与待确认状态
        window.__selUser = null;
        window.__userDelConfirm = null;
        IcuApp.render();
        return;
      }
    }

    // 新建样本弹窗：物种 chip 点击 → 切换右侧剪影（不拦截，让 app.js 先切 'on'，再 setTimeout 0 读 on）
    if (cur === 'new-sample') {
      var nsModal = e.target.closest('.modal.wide');
      if (nsModal) {
        var nsHit = e.target.closest('.chip');
        if (nsHit) {
          var nsGroup = nsHit.closest('.frow .chips');
          var nsGroups = nsModal.querySelectorAll('.frow .chips');
          // 仅处理物种组（第二个 chips 组）。不 stop，让 app.js 继续切 'on'
          if (nsGroup && nsGroups.length >= 2 && nsGroup === nsGroups[1]) {
            setTimeout(function () {
              var sil = nsModal.querySelector('#petSil');
              if (!sil) return;
              var onSp = nsGroups[1].querySelector('.chip.on');
              /* 剪影名优先按 data-spec 映射（silSvg 只认中文名；英文界面 chip 文案已翻译，textContent 会落空） */
              var spMap = { dog: '犬', cat: '猫', rabbit: '兔', lizard: '蜥蜴', snake: '蛇', other: '其它' };
              var nm = onSp ? (spMap[onSp.dataset.spec] || onSp.textContent) : '';
              sil.innerHTML = window.silSvg ? window.silSvg(nm) : '';
            }, 0);
          }
        }
      }
    }

    // 启动页：点击进入登录
    if (cur === 'splash') { /* 交给 app.js data-go=login */ }
  }

  /* ---------- 摄像头实时画面：向原生上报画面区域，原生 RTSP 流覆盖其上 ---------- */
  function reportCamera(cur) {
    var n = N();
    if (!n || !n.cameraPreview) return;
    var sel = (cur === 'video') ? '.video-view .fisheye' : (cur === 'status') ? '.cam.big .fisheye' : null;
    var el = sel ? document.querySelector(sel) : null;
    if (!el) { try { n.cameraPreview(0, 0, 0, 0, 0, 0, false); } catch (e) {} return; }
    var r = el.getBoundingClientRect();
    try {
      n.cameraPreview(r.left, r.top, r.width, r.height,
        window.innerWidth || 1280, window.innerHeight || 800, true);
    } catch (e) {}
  }

  /* ---------- 渲染后回调：注入实时数据 + 表单补丁 ---------- */
  var lastScreenLogged = '';
  function onRender(cur) {
    if (cur !== lastScreenLogged) { lastScreenLogged = cur; feLog('INFO', 'screen', '→ ' + cur); }
    // 离开启动页时清理自动跳转计时器（用户点击跳过）
    if (cur !== 'splash' && splashTimer) { clearTimeout(splashTimer); splashTimer = null; }
    // 把当前屏的会话标识同步显示
    document.querySelectorAll('.si-val').forEach(function (e) { e.textContent = D.session || ''; });
    // 摄像头实时画面：视频/状态页上报区域，其它页隐藏
    reportCamera(cur);
    // 退出中屏：1.5s 后真正调 exit_app（任务8：exit-confirm 点"是"只跳 exiting，延时再退）
    if (cur === 'exiting') {
      setTimeout(function () { try { act('exit_app'); } catch (e) {} }, 1500);
    }
    // 护疗列表为空 → 显示空态 care；有数据 → care-data
    if (cur === 'care' && D.careRows.length > 0) { IcuApp.setScreen('care-data'); return; }
    if (cur === 'care-data' && D.careRows.length === 0) { IcuApp.setScreen('care'); return; }
    if (cur === 'new-sample') patchNewSample();
    if (cur === 'query') patchQuery();
    if (cur === 'comp') bindComp();
    if (cur === 'sending') { applySendingCard(); alignSendMenu(); } // 任务9：原生 invalidate 重渲染后恢复弹层状态
  }

  /* ---------- 补偿页 · +/- 步进（本地状态，初始 0 = 中性默认值，非虚构数据） ---------- */
  function bindComp() {
    var grid = document.querySelector('.comp-grid');
    if (!grid || grid.dataset.bound === '1') return;
    grid.dataset.bound = '1';
    grid.addEventListener('click', function (e) {
      var step = e.target.closest('[data-comp-step]');
      if (!step) return;
      var i = parseInt(step.dataset.compI, 10);
      if (isNaN(i) || i < 0 || i > 4) return;
      if (!D.compVals || D.compVals.length < 5) D.compVals = [0, 0, 0, 0, 0];
      var delta = (step.dataset.compStep === 'p') ? 1 : -1;
      D.compVals[i] = (D.compVals[i] || 0) + delta;
      var val = grid.querySelector('[data-comp-val="' + i + '"]');
      if (val) val.textContent = String(D.compVals[i]);
    });
  }

  /* ---------- 病症手动填写（列表内联编辑，失焦/回车即保存到当前病例） ---------- */
  function onDiseaseChange(e) {
    var t = e.target;
    if (!t || !t.dataset || t.dataset.disease === undefined) return;
    var caseId = t.dataset.caseid;
    if (!caseId) return;
    syncPatient({ caseId: caseId, disease: t.value.trim() });
  }

  /* ★ 任务23：回顾页连续点击（双击）数据行 → 预览该样本的治疗记录单（record-sheet）。
     病例在当前舱 S.cases 里 → patient_select 让原生回推全量数据（sheetPreviewCase 由 syncD 自动清除）；
     纯历史病例（不在 S.cases）→ 用 historyCases 里的对象直接渲染，缺的字段按约定显示 -- */
  function onDblClick(e) {
    var cur = IcuApp.current();
    if (cur !== 'review') return;
    var trHit = e.target.closest('tr[data-caseid]');
    if (!trHit || e.target.closest('input')) return;
    var cid = trHit.dataset.caseid;
    if (!cid) return;
    stop(e);
    var idx = indexOfCase(cid);
    if (idx >= 0 && S.zone) {
      sheetPreviewCase = null;
      act('patient_select_' + S.zone + '_' + idx);
    } else {
      var hc = null;
      var hist = S.historyCases || [];
      for (var i = 0; i < hist.length; i++) {
        if (hist[i] && hist[i].caseId === cid) { hc = hist[i]; break; }
      }
      sheetPreviewCase = hc || null;
    }
    syncD();
    D.selCaseId = cid; // 立即记录选中，不等原生回推（syncD 会从 S.patient 重置，故在其后再赋值）
    IcuApp.go('record-sheet');
  }

  /* ---------- 启动 ---------- */
  function boot() {
    document.getElementById('app-root').addEventListener('click', onClick, true);
    document.getElementById('app-root').addEventListener('dblclick', onDblClick, true); // 任务23：回顾双击预览治疗记录单
    document.getElementById('app-root').addEventListener('change', onDiseaseChange, true);
    /* ★ 2026-10-08 用户日志：日期过滤（空=全部） */
    document.getElementById('app-root').addEventListener('change', function (e) {
      var ld = e.target.closest ? e.target.closest('[data-logdate]') : null;
      if (!ld) return;
      D.logDate = ld.value || '';
      D.logPage = 1;
      IcuApp.render();
    }, true);
    /* ★ 任务15(#4)：语言选择列表（下拉）的 change 事件 */
    document.getElementById('app-root').addEventListener('change', function (e) {
      var sel = e.target.closest ? e.target.closest('[data-langsel]') : null;
      if (!sel) return;
      var v = sel.value === 'en' ? 'en' : 'zh';
      D.lang = v;                                  // 即时生效，不等原生回推
      act('settings_save', JSON.stringify({ language: v }));
      IcuApp.render();
    }, true);
    /* ★ 任务32：输入框字符过滤（委托到 app-root，弹窗/页面重渲染后仍生效）
       data-tel=电话字符（数字/+/-） data-addr=打印机地址（数字/.//:) data-num2=数字+小数点 */
    document.getElementById('app-root').addEventListener('input', function (e) {
      var t = e.target;
      if (!t || !t.getAttribute) return;
      if (t.hasAttribute('data-tel')) t.value = t.value.replace(/[^\d+\- ]/g, '');
      else if (t.hasAttribute('data-mobile')) t.value = t.value.replace(/\D/g, '');
      else if (t.hasAttribute('data-addr')) t.value = t.value.replace(/[^\d.:]/g, '');
      else if (t.hasAttribute('data-num2')) {
        var v = t.value.replace(/[^\d.]/g, '');
        var p = v.split('.');
        t.value = p.length > 2 ? p[0] + '.' + p.slice(1).join('') : v;
      }
    }, true);
    window.addEventListener('icu-native-state', refresh);
    window.addEventListener('icu-settings-loaded', refresh);

    if (hasNative()) {
      pullState(); syncD();
      // 先播放开机动画，约 3.5s 后自动进入登录/护理；点击 splash 可跳过（data-go=login）
      IcuApp.setScreen('splash');
      splashTimer = setTimeout(function () {
        splashTimer = null;
        if (IcuApp.current() === 'splash') {
          IcuApp.setScreen(isLoggedIn() ? 'care' : 'login');
        }
      }, 3500);
      // 让顶栏时钟动起来（每 30s 刷一次时间显示）
      setInterval(function () { if (!pendingEditor && !pendingNavAfterSave) { syncD(); var c = IcuApp.current(); if (c !== 'new-sample' && c !== 'query' && c !== 'login' && c !== 'splash') IcuApp.render(); } }, 30000);
    } else {
      // 桌面浏览器预览：保持原型 splash
    }

    /* ★ 2026-09-30：设置·常规页时间自动显示 —— 每秒刷新 [data-sysclock]（不触发整页重渲染，避免打断输入） */
    function tickSysClock() {
      var els = document.querySelectorAll('[data-sysclock]');
      if (!els.length) return;
      var d = new Date();
      function p2(n) { return (n < 10 ? '0' : '') + n; }
      var en = (window.D && window.D.lang) === 'en';
      var datePart = en
        ? d.getFullYear() + '/' + p2(d.getMonth() + 1) + '/' + p2(d.getDate())
        : d.getFullYear() + '年' + p2(d.getMonth() + 1) + '月' + p2(d.getDate()) + '日';
      var txt = datePart
        + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes()) + ':' + p2(d.getSeconds());
      els.forEach(function (el) { if (el.textContent !== txt) el.textContent = txt; });
    }
    setInterval(tickSysClock, 1000);
  }

  window.IcuOnRender = onRender;
  if (document.readyState === 'complete' || document.readyState === 'interactive') { setTimeout(boot, 0); }
  else { window.addEventListener('DOMContentLoaded', function () { setTimeout(boot, 0); }); }
})();
