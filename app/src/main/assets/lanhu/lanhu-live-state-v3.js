(function () {
  var timer = 0;
  // ★ 左右舱隔离：所有跨 render 的缓存（监护等级、倒计时、pending）必须按舱命名。
  //   一个蓝牙主机同时管左右两舱，切舱后沿用另一舱的缓存就是串舱。
  var DEFAULT_ZONE = 'left';
  var lastHostMonitorLevelState = {
    left: { color: "yellow", label: "二级" },
    right: { color: "yellow", label: "二级" }
  };

  function normalizeZone(value) {
    var text = clean(value).toLowerCase();
    return text === 'right' ? 'right' : DEFAULT_ZONE;
  }

  function zoneOf(state) {
    return normalizeZone(state && state.host ? state.host.zone : '');
  }

  // 当前舱由 native state 的 host.zone 驱动；bridge/fit 的乐观写入都跟随它，
  // 保证"操作时所在的舱"和"回包归属的舱"一致。
  function currentZone() {
    return normalizeZone(window.__icuCurrentZone);
  }

  function setCurrentZone(zone) {
    window.__icuCurrentZone = normalizeZone(zone);
  }

  // 扁平缓存的按舱 key：把 endAt / pending 等表拆成 "left|xxx" / "right|xxx"。
  function zoneKey(zone, key) {
    return normalizeZone(zone) + '|' + key;
  }

  function nativeReady() {
    return !!(window.IcuNative && typeof window.IcuNative.bleState === "function");
  }

  function readState() {
    if (!nativeReady()) {
      return {};
    }
    try {
      var state = JSON.parse(window.IcuNative.bleState() || "{}") || {};
      var phase = readFirstPhaseState();
      // bleState 是环境参数、开关及倒计时的唯一实时来源；历史治疗记录
      // 由历史页面单独渲染，不得覆盖 ICU 状态页和主机控制页的实时值。
      if (phase && phase.hostModes) {
        state.hostModes = phase.hostModes;
      }
      return state;
    } catch (error) {
      return {};
    }
  }

  function readFirstPhaseState() {
    if (!window.IcuNative || typeof window.IcuNative.firstPhaseState !== "function") {
      return {};
    }
    try {
      return JSON.parse(window.IcuNative.firstPhaseState() || "{}") || {};
    } catch (error) {
      return {};
    }
  }

  function readPatient() {
    if (!window.IcuNative || typeof window.IcuNative.patientState !== "function") {
      return {};
    }
    try {
      return JSON.parse(window.IcuNative.patientState() || "{}") || {};
    } catch (error) {
      return {};
    }
  }

  function clean(value) {
    if (value === undefined || value === null) {
      return "";
    }
    value = String(value).replace(/\s+/g, " ").trim();
    if (!value || value === "null" || value === "undefined" || value === "-" || value === "未连接" || value === "尚未收到响应") {
      return "";
    }
    return value;
  }

  function display(value) {
    return clean(value) || "待录入";
  }

  function pageName() {
    var path = window.location.pathname || "";
    var match = path.match(/src\/views\/([^/]+)\//);
    return match ? match[1] : "";
  }

  function stripMetricUnits(value) {
    return String(value || "").replace(/\s*(℃|%|PPM)\s*$/ig, "").trim();
  }

  function formatHourText(value) {
    var match = String(value || "").match(/^(\d+)\s*h\s*(\d+)\s*m$/i);
    if (!match) {
      return "";
    }
    var hours = parseInt(match[1], 10);
    var minutes = parseInt(match[2], 10);
    if (!isFinite(hours) || !isFinite(minutes)) {
      return "";
    }
    // ★ ≤ 1 小时（小时数 = 0）：显示为分钟数或空
    if (hours === 0) {
      if (minutes === 0) {
        return "00:00";
      }
      return minutes + "分钟";
    }
    // ★ > 1 小时：显示为 "X 小时 Y 分钟" 格式
    // 例：1h2m → "1小时2分钟"
    if (minutes === 0) {
      return hours + "小时";
    }
    return hours + "小时" + minutes + "分钟";
  }

  function compactTimedValue(value) {
    var text = String(value || "");
    var clock = text.match(/\d{1,2}:\d{2}(?::\d{2})?/);
    if (clock) {
      return clock[0];
    }
    var alwaysOn = text.match(/\d+\s*h\s*常开/i);
    if (alwaysOn) {
      return alwaysOn[0].replace(/\s+/g, "");
    }
    var number = text.match(/-?\d+(?:\.\d+)?/);
    return number ? number[0] : "";
  }

  function compactSwitchValue(value) {
    var text = clean(value);
    var state = text.match(/^(开|关|on|off|true|false|1|0)/i);
    return state ? state[1] : "";
  }

  function hostMonitorLevelState(root, zone) {
    var z = normalizeZone(zone);
    var host = root && root.host ? root.host : {};
    var controls = host.controls || {};
    var color = clean(host.statusLightColor).toLowerCase();
    var label = clean(host.monitorLevel);
    if (color !== "red" && color !== "yellow" && color !== "green") {
      color = "";
    }
    if (!label) {
      if (color === "red") {
        label = "一级";
      } else if (color === "green") {
        label = "三级";
      } else if (color === "yellow") {
        label = "二级";
      }
    }
    if (color && label) {
      lastHostMonitorLevelState[z] = {
        color: color,
        label: label
      };
      return lastHostMonitorLevelState[z];
    }
    // ★ 未取到新值时只回退本舱上一次结果，绝不回退另一舱的监护等级。
    return lastHostMonitorLevelState[z] || lastHostMonitorLevelState[DEFAULT_ZONE];
  }

  function formatCountdownClock(remainingMs) {
    if (typeof remainingMs !== "number" || remainingMs <= 0) {
      return "";
    }
    var totalSeconds = Math.ceil(remainingMs / 1000);
    var hours = Math.floor(totalSeconds / 3600);
    var minutes = Math.floor((totalSeconds % 3600) / 60);
    var seconds = totalSeconds % 60;
    var pad = function (n) { return n < 10 ? "0" + n : "" + n; };
    if (hours > 0) {
      return hours + ":" + pad(minutes) + ":" + pad(seconds);
    }
    return pad(minutes) + ":" + pad(seconds);
  }

  // ★ 问题8:ICU 状态页治疗时长/红外理疗/蓝光理疗的值是大号数字。
  //   倒计时超过 10 分钟（10:00、120:00…）或“1小时2分钟”等文字会超出卡片，
  //   这里按可用宽度逐级缩小字号，保证完整显示、不压右侧图标。
  var statusFitCanvas = null;
  function statusFitFont(node) {
    if (!node || !node.getAttribute) {
      return;
    }
    var live = node.getAttribute("data-icu-live") || "";
    if (live !== "host.treatmentTime" &&
        live !== "host.controls.redTherapy" &&
        live !== "host.controls.blueTherapy") {
      return;
    }
    if (pageName() !== "lanhu_1icuzhuangtaikaobei") {
      return;
    }
    var text = clean(node.textContent);
    if (!text || text === "--" || text === "关") {
      node.style.removeProperty("font-size");
      return;
    }
    if (!statusFitCanvas) {
      statusFitCanvas = document.createElement("canvas");
    }
    var ctx = statusFitCanvas.getContext ? statusFitCanvas.getContext("2d") : null;
    if (!ctx) {
      return;
    }
    var family = window.getComputedStyle(node).fontFamily || "sans-serif";
    // 卡片 230px − 左缩进 22px − 右侧图标(约44px+23px) ≈ 140px 可用；
    // 测量留 ~12px 余量，避免个别字体宽度估算偏差造成轻微溢出。
    var available = 128;
    var sizes = [55, 50, 46, 42, 38, 34, 30, 27, 24, 22, 20];
    var chosen = 55;
    for (var i = 0; i < sizes.length; i += 1) {
      ctx.font = sizes[i] + "px " + family;
      if (ctx.measureText(text).width <= available) {
        chosen = sizes[i];
        break;
      }
    }
    node.style.setProperty("font-size", chosen + "px", "important");
  }

  function normalizeLiveValue(path, value) {
    var text = clean(value);
    if (!text) {
      return "";
    }
    // ★ P2-BUG#8: 舱内温度和红外体温显示优化 - 保留1位小数 + 单位
    if (/^host(\.controls)?\.(temp|infraredTemp)$/.test(path)) {
      var numStr = stripMetricUnits(text);
      var num = parseFloat(numStr);
      if (isFinite(num)) {
        return num.toFixed(1) + "℃";
      }
      return numStr;
    }
    if (/^host(\.controls)?\.(oxygen|humidity|co2)$/.test(path)) {
      return stripMetricUnits(text);
    }
    if (path === "host.treatmentTime") {
      return formatHourText(text) || compactTimedValue(text) || stripMetricUnits(text);
    }
    if (/^host\.controls\.(redTherapy|blueTherapy|uv|nebulizer|anion)$/.test(path)) {
      return compactTimedValue(text) || compactSwitchValue(text) || text;
    }
    return text;
  }

  function applyLiveMetricStyle(node, path) {
    var page = pageName();
    if (page !== "lanhu_1icuzhuangtaikaobei" && page !== "lanhu_2zhujikongzhi") {
      return;
    }
    if (!/^host(\.controls)?\./.test(path)) {
      return;
    }
  }

  function shouldBlankMissingLiveValue(path) {
    return path === "host.controls.nebulizer" ||
      path === "host.controls.warmLight" ||
      path === "host.controls.innerCycle";
  }

  function missingLiveFallbackText(path) {
    if (shouldBlankMissingLiveValue(path)) {
      return "关";
    }
    return "";
  }

  function overlaySelectedHistory(state, phase) {
    if (!phase || !phase.patient || phase.patient.currentTreatment !== false) {
      return state;
    }
    state = state || {};
    state.host = state.host || {};
    state.host.controls = state.host.controls || {};
    state.monitor = state.monitor || {};
    setIfValue(state.host, "temp", treatmentValue(phase, ["舱内温度"]));
    setIfValue(state.host, "oxygen", treatmentValue(phase, ["氧气浓度", "氧浓度"]));
    setIfValue(state.host, "humidity", treatmentValue(phase, ["湿度"]));
    setIfValue(state.host, "co2", treatmentValue(phase, ["CO2", "二氧化碳"]));
    setIfValue(state.host, "infraredTemp", treatmentValue(phase, ["红外体温", "体温"]));
    setIfValue(state.host, "treatmentTime", treatmentValue(phase, ["治疗时长"]));
    setIfValue(state.host.controls, "redTherapy", treatmentValue(phase, ["红外理疗"]));
    setIfValue(state.host.controls, "blueTherapy", treatmentValue(phase, ["蓝光理疗"]));
    setIfValue(state.host.controls, "nebulizer", treatmentValue(phase, ["雾化"]));
    setIfValue(state.monitor, "heartRate", treatmentValue(phase, ["心率"]));
    setIfValue(state.monitor, "bloodPressure", treatmentValue(phase, ["血压"]));
    setIfValue(state.monitor, "spo2", treatmentValue(phase, ["血氧"]));
    setIfValue(state.monitor, "bodyTemp", treatmentValue(phase, ["体温"]));
    setIfValue(state.monitor, "resp", treatmentValue(phase, ["呼吸"]));
    return state;
  }

  function setIfValue(target, key, value) {
    value = clean(value);
    if (value) {
      target[key] = value;
    }
  }

  function treatmentValue(phase, keywords) {
    var list = Array.isArray(phase.treatments) ? phase.treatments : [];
    for (var i = 0; i < list.length; i += 1) {
      var item = list[i] || {};
      var name = String(item.itemName || "");
      var matched = false;
      for (var j = 0; j < keywords.length; j += 1) {
        if (name.indexOf(keywords[j]) >= 0) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        continue;
      }
      return clean(item.lastValue) || clean(item.average) || clean(item.high) || clean(item.low);
    }
    return "";
  }

  function getPath(root, path) {
    if (path === "monitorLevel") {
      return hostMonitorLevelState(root, zoneOf(root)).label;
    }
    var parts = path.split(".");
    var current = root;
    for (var i = 0; i < parts.length; i += 1) {
      if (current === undefined || current === null) {
        return "";
      }
      current = current[parts[i]];
    }
    return current;
  }

  function patientPath(root, path) {
    if (path === "petListName") {
      return clean(root.petName) ? "\u6cbb\u7597\u8bb0\u5f55\uff1a" + root.petName : "";
    }
    if (path === "speciesOwner") {
      var species = clean(root.species);
      var owner = clean(root.ownerName);
      if (!species && !owner) {
        return "";
      }
      return "\u79cd\u7c7b\uff1a" + display(species) + "     \u4e3b\u4eba\uff1a" + display(owner);
    }
    if (path === "caseNo") {
      return clean(root.caseNo) ? "\u7f16\u53f7\uff1a" + root.caseNo : "";
    }
    var parts = path.split(".");
    var current = root;
    for (var i = 0; i < parts.length; i += 1) {
      if (current === undefined || current === null) {
        return "";
      }
      current = current[parts[i]];
    }
    return current;
  }

  function renderTreatmentTime(state, zone) {
    // ★ 治疗时长倒计时按舱存：切舱不能把另一舱的 endAt 显示到本舱卡片上。
    var treatmentKey = zoneKey(zone, ".text-group_2");
    var remainingMs = getPath(state, "host.treatmentRemainingMs");
    var countdownText = formatCountdownClock(remainingMs);
    if (countdownText) {
      countdownEndAt[treatmentKey] = Date.now() + remainingMs;
      return countdownText;
    }
    countdownEndAt[treatmentKey] = null;
    var configuredMinutes = Number(getPath(state, "host.treatmentMinutes"));
    if (isFinite(configuredMinutes) && configuredMinutes > 0) {
      // ★ P2-问题12: 统一使用"小时+分钟"格式
      var hours = Math.floor(configuredMinutes / 60);
      var minutes = configuredMinutes % 60;
      if (hours === 0) {
        return minutes + "分钟";
      }
      if (minutes === 0) {
        return hours + "小时";
      }
      return hours + "小时" + minutes + "分钟";
    }
    return normalizeLiveValue("host.treatmentTime", getPath(state, "host.treatmentTime"));
  }

  // ★ P1-2:给所有"待确认"的控制项加 .icu-pending 视觉提示
  //   cmdMap 把 data-icu-live 路径映射到对应的 BLE 命令名
  //   ★ 环境数据(温度/氧/湿/CO2)用 host.XXX,开关类用 host.controls.XXX
  var pendingCmdMap = {
    'host.temp':                  'set_temp',
    'host.oxygen':                'set_o2',
    'host.humidity':              'set_humidity',
    'host.co2':                   'set_co2',
    'host.controls.redTherapy':   'set_red_enable',
    'host.controls.blueTherapy':  'set_blue_enable',
    'host.controls.uv':           'set_uv_enable',
    'host.controls.nebulizer':    'set_nebulizer_enable',
    'host.controls.anion':        'set_anion_enable',
    'host.controls.coldLight':    'set_cold_light',
    'host.controls.warmLight':    'set_warm_light',
    'host.controls.outerCycle':   'set_outer_cycle',
    'host.controls.innerCycle':   'set_inner_cycle'
  };

  function applyPendingStyle(node, path, hostState) {
    var pendingControls = hostState && hostState.pendingControls
      ? String(hostState.pendingControls).split(',')
      : [];
    var cmdName = pendingCmdMap[path];
    var isPending = cmdName && pendingControls.indexOf(cmdName) >= 0;
    if (isPending) {
      node.classList.add('icu-pending');
      node.setAttribute('data-pending-cmd', cmdName);
    } else {
      node.classList.remove('icu-pending');
      node.removeAttribute('data-pending-cmd');
    }
  }

  // ★ P1-1:CO2 报警时给对应元素加 .icu-alarm class(变红闪烁)
  function applyCo2AlarmStyle(node, path, hostState) {
    if (path !== 'host.co2') return;
    var inAlarm = hostState && hostState.co2AlarmActive;
    if (inAlarm) {
      node.classList.add('icu-alarm');
      node.setAttribute('data-alarm', 'co2');
    } else {
      node.classList.remove('icu-alarm');
      node.removeAttribute('data-alarm');
    }
  }

  // ★ 监护等级的"确认中"视觉提示(专用,带文字角标)
  function renderMonitorLevel(node, state) {
    var pending = state.host && state.host.pendingStatusLightColor;
    var label = hostMonitorLevelState(state, zoneOf(state)).label;
    node.textContent = label;
    if (pending) {
      node.classList.add('icu-monitor-pending');
      node.setAttribute('data-pending', pending);
    } else {
      node.classList.remove('icu-monitor-pending');
      node.removeAttribute('data-pending');
    }
  }

  function render() {
    var nodes = document.querySelectorAll("[data-icu-live]");
    var patientNodes = document.querySelectorAll("[data-patient-live]");
    if (!nodes.length && !patientNodes.length) {
      return;
    }
    var state = readState();
    // ★ 只有 native 明确给了舱位才更新当前舱。state 为空（原生未就绪）
    //   时不能把当前舱打回默认舱，否则会覆盖 bridge 切舱时写入的舱位。
    var reportedZone = state.host ? clean(state.host.zone) : '';
    if (reportedZone) {
      setCurrentZone(reportedZone);
    }
    var zone = currentZone();
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      var path = node.getAttribute("data-icu-live") || "";
      // ★ P1-1:监护等级走专用渲染分支(带 pending 视觉提示)
      if (path === "monitorLevel") {
        renderMonitorLevel(node, state);
        continue;
      }
      var value = path === "host.treatmentTime"
        ? renderTreatmentTime(state, zone)
        : normalizeLiveValue(path, getPath(state, path));
      var fallback = missingLiveFallbackText(path);
      node.textContent = !clean(value) && fallback ? fallback : display(value);
      statusFitFont(node);
      applyLiveMetricStyle(node, path);
      // ★ P1-2:所有 set_* 控制的字段加 .icu-pending 视觉提示
      applyPendingStyle(node, path, state.host);
      // ★ P1-1:CO2 报警样式
      applyCo2AlarmStyle(node, path, state.host);
    }
    if (window.IcuPatientTemp && typeof window.IcuPatientTemp.render === "function") {
      window.IcuPatientTemp.render();
    } else {
      var patient = readPatient();
      for (var j = 0; j < patientNodes.length; j += 1) {
        var patientNode = patientNodes[j];
        var patientKey = patientNode.getAttribute("data-patient-live") || "";
        patientNode.textContent = display(patientPath(patient, patientKey));
      }
    }
    clearDeadVisuals(state);
  }

  function hasValue(state, path) {
    return !!clean(getPath(state, path));
  }

  function isOnValue(value) {
    if (value === true) {
      return true;
    }
    if (value === false || value === undefined || value === null) {
      return false;
    }
    var text = clean(value).toLowerCase();
    if (!text) {
      return false;
    }
    if (text === "off" || text === "false" || text === "0" || text.indexOf("\u5173") === 0 || text.indexOf("\u95ed") === 0 || text.indexOf("\u934f") === 0) {
      return false;
    }
    return text === "on" || text === "true" || text === "1" || text.indexOf("\u5f00") === 0 || text.indexOf("\u5f00\u542f") === 0 || text.indexOf("\u5bee") === 0;
  }

  function switchOn(state, onPath, valuePath) {
    var direct = getPath(state, onPath);
    if (direct === true || direct === false) {
      return direct;
    }
    if (clean(direct)) {
      return isOnValue(direct);
    }
    return isOnValue(getPath(state, valuePath));
  }

  function switchBool(state, onPath) {
    return getPath(state, onPath) === true;
  }

  function setVisible(selector, visible) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].style.visibility = visible ? "visible" : "hidden";
    }
  }

  function setTextValue(selector, value) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].textContent = value;
    }
  }

  function setStatusSwitch(wrapperSelector, action, on) {
    var nodes = document.querySelectorAll(wrapperSelector);
    for (var i = 0; i < nodes.length; i += 1) {
      var wrapper = nodes[i];
      wrapper.style.visibility = "visible";
      wrapper.setAttribute("data-native-action", action);
      wrapper.style.cursor = "pointer";
      wrapper.style.touchAction = "manipulation";
      var img = wrapper.querySelector("img");
      if (img) {
        // ★ 恢复原图显示(用户明确要求显示图片而不是 fake toggle)
        img.style.display = "";
        // ★ 根据 on/off 状态给 img 加 class,触发 CSS 视觉差异
        //   on: 加亮 + 绿色光晕
        //   off: 默认
        img.classList.toggle("on", !!on);
        img.classList.toggle("off", !on);
      }
    }
  }

  // ★ 红外/蓝光按协议 65536 表示不限时：显示“常开”，不产生倒计时。
  //   不能再用“分钟数 ≥ 1440 就是 24h常开”猜测，65536 会被误标成 24 小时。
  var UNLIMITED_MINUTES = 65536;

  function isUnlimitedMinutes(minutes) {
    return Number(minutes) === UNLIMITED_MINUTES;
  }

  function formatTimedControlDisplay(value, on, remainingMs, connected, unlimited) {
    if (unlimited) {
      return "常开";
    }
    // 定时控制以原生剩余时间为准。开关确认或连接状态稍后到达时，不能把已启动的倒计时重置为 "--"。
    if (typeof remainingMs === "number" && remainingMs > 0) {
      // ★ P1-3 修复:用 Math.round 替代 Math.ceil
      //   Math.ceil 导致 9:00 反复(8:59 → 9:00 卡几秒 → 8:58),用户体验差
      //   Math.round 边界更平滑: 9:30 → 9:00(平滑)→ 8:59 → 8:58
      var totalSeconds = Math.round(remainingMs / 1000);
      var minutes = Math.floor(totalSeconds / 60);
      var seconds = totalSeconds % 60;
      if (isUnlimitedMinutes(minutes)) {
        return "常开";
      }
      if (minutes >= 1440) {
        return "24h常开";
      }
      return minutes + ":" + (seconds < 10 ? "0" + seconds : seconds);
    }
    if (!connected) {
      return "--";
    }
    if (!on) {
      return "关";
    }
    var text = clean(value);
    var clock = text.match(/^\d{1,4}:\d{2}(?::\d{2})?$/);
    if (clock) {
      return clock[0] === "0:00" || clock[0] === "00:00" ? "开" : clock[0];
    }
    var alwaysOn = text.match(/^(\d+)\s*h\s*常开$/i);
    if (alwaysOn) {
      return alwaysOn[0].replace(/\s+/g, "");
    }
    var number = text.match(/-?\d+(?:\.\d+)?/);
    if (!number) {
      return "开";
    }
    var minutes = Number(number[0]);
    if (!isFinite(minutes)) {
      return "开";
    }
    if (isUnlimitedMinutes(minutes)) {
      return "常开";
    }
    if (minutes >= 1440) {
      return "24h常开";
    }
    return minutes <= 0 ? "开" : String(minutes) + ":00";
  }

  function setTimedCardVisual(valueSelector, titleSelector, settingSelector, rawValue, on, remainingMs, connected, unlimited, zone) {
    var pendingKeyMap = {
      '.text-wrapper_29': 'redTherapy',
      '.text-wrapper_30': 'blueTherapy',
      '.text_95': 'uv',
      '.text_98': 'nebulizer',
      '.text_101': 'anion',
      '.text_71': 'redTherapy',
      '.text_83': 'blueTherapy'
    };
    // ★ P1-7 修复:用 window 级 endAtMap 跨 render 保持
    //   只在 native 推新值时更新,避免每次 render 都重置导致卡顿
    // ★ 左右舱隔离:endAt / pending 全部按 "舱|key" 存,切舱互不覆盖。
    if (!window.__icuCountdownEndAt) window.__icuCountdownEndAt = {};
    if (!window.__icuLastRemainingMs) window.__icuLastRemainingMs = {};
    var endAtMap = window.__icuCountdownEndAt;
    var lastMsMap = window.__icuLastRemainingMs;
    var endAtKey = zoneKey(zone, valueSelector);
    var pendingKey = zoneKey(zone, pendingKeyMap[valueSelector]);

    // ★ 修复问题3: 关闭开关时清除倒计时
    if (!on || unlimited) {
      endAtMap[endAtKey] = null;
      lastMsMap[endAtKey] = null;
    }

    var pending = window.__icuPendingTimedCountdowns || {};
    var pendingEndAt = pending[pendingKey] || 0;
    var pendingRemainingMs = pendingEndAt - Date.now();
    if (unlimited) {
      // 不限时控件没有倒计时，清掉乐观计时避免残留。
      pending[pendingKey] = 0;
      window.__icuPendingTimedCountdowns = pending;
      remainingMs = 0;
    } else if (typeof remainingMs === 'number' && remainingMs > 0) {
      // ★ 主机校时优先：每次原生推送剩余时间都重置本地 endAt，
      //   否则本地自减会和主机越差越多（本次同步问题的根因之一）。
      endAtMap[endAtKey] = Date.now() + remainingMs;
      lastMsMap[endAtKey] = remainingMs;
      pending[pendingKey] = 0;
      window.__icuPendingTimedCountdowns = pending;
    } else if (pendingRemainingMs > 0) {
      remainingMs = pendingRemainingMs;
      on = true;
    } else if (pendingEndAt) {
      pending[pendingKey] = 0;
      window.__icuPendingTimedCountdowns = pending;
    }

    // ★ 关键:用本地缓存的 endAt 算精确剩余时间(每秒自减)
    var effectiveRemaining = remainingMs;
    var storedEndAt = endAtMap[endAtKey];
    if (!unlimited && storedEndAt && storedEndAt > Date.now()) {
      effectiveRemaining = storedEndAt - Date.now();
    }

    setTextValue(valueSelector, formatTimedControlDisplay(rawValue, on, effectiveRemaining, connected, unlimited));
    var valueNodes = document.querySelectorAll(valueSelector);
    for (var i = 0; i < valueNodes.length; i += 1) {
      statusFitFont(valueNodes[i]);
      valueNodes[i].style.setProperty('color', (on || unlimited || (typeof effectiveRemaining === 'number' && effectiveRemaining > 0)) ? '#222222' : '#A8B2C1', 'important');
    }
  }

  // ★ 定时控制器显示位置映射（用于本地倒计时刷新）
  var countdownDisplays = {
    ".text-wrapper_29": true,   // 红外理疗
    ".text-wrapper_30": true,   // 蓝光理疗
    ".text_95":         true,   // 紫外消毒
    ".text_98":         true,   // 雾化器
    ".text_101":        true,   // 负离子
    ".text-group_2":    true    // 治疗时长
  };
  var countdownEndAt = {};

  // ★★ P1-5 修复:前端本地倒计时 — 用 window 级 endAtMap,跨 render 保持
  function refreshCountdownLocally() {
    var now = Date.now();
    var endAtMap = window.__icuCountdownEndAt || {};
    var zone = currentZone();
    var prefix = normalizeZone(zone) + '|';
    Object.keys(endAtMap).forEach(function (zoneSelector) {
      // ★ 只刷新当前舱的倒计时：另一舱的 endAt 属于另一舱，不能画到本舱卡片上。
      if (zoneSelector.indexOf(prefix) !== 0) {
        return;
      }
      var selector = zoneSelector.slice(prefix.length);
      var endAt = endAtMap[zoneSelector];
      if (!endAt) return;
      var remaining = endAt - now;
      var node = document.querySelector(selector);
      if (!node) return;
      if (remaining <= 0) {
        endAtMap[zoneSelector] = null;
        // ★ 到点了主动调 Java 端拉取一次最新 state（让 Java 关闭并推送 on=false）
        if (window.IcuNative && typeof window.IcuNative.bleState === 'function') {
          try { window.IcuNative.bleState(); } catch (e) {}
        }
        return;
      }
      var totalSeconds = Math.round(remaining / 1000);
      var minutes = Math.floor(totalSeconds / 60);
      var seconds = totalSeconds % 60;
      if (isUnlimitedMinutes(minutes)) {
        node.textContent = '常开';
      } else if (minutes >= 1440) {
        node.textContent = '24h常开';
      } else {
        node.textContent = minutes + ':' + (seconds < 10 ? '0' + seconds : seconds);
      }
      statusFitFont(node);
    });
  }

  window.setInterval(refreshCountdownLocally, 1000);

  function clearDeadVisuals(state) {
    var page = window.location.pathname || "";
    var zone = zoneOf(state);
    if (page.indexOf("lanhu_1icuzhuangtaikaobei") >= 0) {
      var nebulizerOn = switchOn(state, "host.controls.nebulizerOn", "host.controls.nebulizer");
      var warmOn = switchOn(state, "host.controls.warmLightOn", "host.controls.warmLight");
      var innerOn = switchOn(state, "host.controls.innerCycleOn", "host.controls.innerCycle");
      setVisible(".group_11", hasValue(state, "host.temp"));
      setVisible(".box_20", hasValue(state, "host.oxygen"));
      setVisible(".box_22", hasValue(state, "host.infraredTemp"));
      setVisible(".group_14", hasValue(state, "host.treatmentTime"));
      setVisible(".box_45", switchBool(state, "host.controls.blueTherapyOn"));
      setVisible(".box_42", hasValue(state, "host.humidity"));
      setVisible(".box_44", hasValue(state, "host.co2"));
      setTextValue(".text_73", nebulizerOn ? "开" : "关");
      setTextValue(".text_75", warmOn ? "开" : "关");
      setTextValue(".text_77", innerOn ? "开" : "关");
      var statusHostConnected = !!(state.host && state.host.connected);
      setTimedCardVisual(".text_71", "", "",
        getPath(state, "host.controls.redTherapy"),
        switchBool(state, "host.controls.redTherapyOn"),
        getPath(state, "host.controls.redTherapyRemainingMs"), statusHostConnected,
        !!getPath(state, "host.controls.redTherapyUnlimited"), zone);
      setTimedCardVisual(".text_83", "", "",
        getPath(state, "host.controls.blueTherapy"),
        switchBool(state, "host.controls.blueTherapyOn"),
        getPath(state, "host.controls.blueTherapyRemainingMs"), statusHostConnected,
        !!getPath(state, "host.controls.blueTherapyUnlimited"), zone);
      return;
    }
    if (page.indexOf("lanhu_2zhujikongzhi") < 0) {
      return;
    }
    // ★ 主机控制页:环境数据路径与 ICU 状态页一致(host.XXX),不是 host.controls.XXX
    setVisible(".section_3", hasValue(state, "host.temp"));
    setVisible(".section_4", hasValue(state, "host.oxygen"));
    setVisible(".box_14", hasValue(state, "host.humidity"));
    setVisible(".block_15", hasValue(state, "host.co2"));
    setVisible(".box_48", true);
    setVisible(".group_39,.section_17,.box_37,.box_41,.box_44,.group_50,.label_3,.group_61", true);
    var redOn = switchBool(state, "host.controls.redTherapyOn");
    var blueOn = switchBool(state, "host.controls.blueTherapyOn");
    var uvOn = switchBool(state, "host.controls.uvOn");
    var nebulizerOn = switchBool(state, "host.controls.nebulizerOn");
    var anionOn = switchBool(state, "host.controls.anionOn");
    var coldOn = switchBool(state, "host.controls.coldLightOn");
    var warmOn = switchBool(state, "host.controls.warmLightOn");
    var outerOn = switchBool(state, "host.controls.outerCycleOn");
    var innerOn = switchBool(state, "host.controls.innerCycleOn");
    var hostConnected = !!(state.host && state.host.connected);
    setTimedCardVisual(".text-wrapper_29", ".text_78", ".text_93",
      getPath(state, "host.controls.redTherapy"), redOn,
      getPath(state, "host.controls.redTherapyRemainingMs"), hostConnected,
      !!getPath(state, "host.controls.redTherapyUnlimited"), zone);
    setTimedCardVisual(".text-wrapper_30", ".text_81", ".text_94",
      getPath(state, "host.controls.blueTherapy"), blueOn,
      getPath(state, "host.controls.blueTherapyRemainingMs"), hostConnected,
      !!getPath(state, "host.controls.blueTherapyUnlimited"), zone);
    setTimedCardVisual(".text_95", ".text_84", ".text_96",
      getPath(state, "host.controls.uv"), uvOn,
      getPath(state, "host.controls.uvRemainingMs"), hostConnected,
      !!getPath(state, "host.controls.uvUnlimited"), zone);
    setTimedCardVisual(".text_98", ".text_97", ".text_99",
      getPath(state, "host.controls.nebulizer"), nebulizerOn,
      getPath(state, "host.controls.nebulizerRemainingMs"), hostConnected,
      !!getPath(state, "host.controls.nebulizerUnlimited"), zone);
    setTimedCardVisual(".text_101", ".text_100", ".text_102",
      getPath(state, "host.controls.anion"), anionOn,
      getPath(state, "host.controls.anionRemainingMs"), hostConnected,
      !!getPath(state, "host.controls.anionUnlimited"), zone);
    // ★ 通知 lanhu-fit.js 跟踪 state（用来记录上次设置值的变化）
    notifySettingsTracker(state);
  }

  function scheduleRender(delay) {
    window.clearTimeout(timer);
    timer = window.setTimeout(render, delay || 0);
  }

  // ★ 通知 lanhu-fit.js：当前 state 让其更新设置记忆（舱内温度、氧浓度等）
  // render 完成后调用，让 trackStateForSettings 跟踪值变化
  function notifySettingsTracker(state) {
    if (typeof window.__icuTrackStateForSettings === 'function') {
      try {
        window.__icuTrackStateForSettings(state);
      } catch (e) {
        // 忽略：跟踪失败不影响主渲染
      }
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    render();
    // 原生 BLE 回包通过 icu-native-state 立即触发；1.2 秒定时仅作页面兜底渲染。
    window.setInterval(render, 1200);
  });

  window.addEventListener("icu-native-state", function () {
    scheduleRender(0);
  });
})();
