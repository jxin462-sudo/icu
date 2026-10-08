(function () {
  var openTarget = "";
  var renderTimer = 0;
  var panel = null;
  var statusEl = null;
  var rows = {};
  var optionSignatureMap = {};
  var interacting = false;
  var pendingRender = false;

  var TEXT = {
    hostName: "主机名称",
    wifi: "wifi链接",
    hostBle: "主机蓝牙",
    monitorBle: "监护蓝牙",
    noData: "暂无数据",
    scanning: "扫描中",
    connected: "已连接",
    disconnected: "未连接",
    hostState: "主机状态",
    monitorState: "监护状态",
    wifiState: "WiFi状态",
    save: "保存",
    cancel: "取消",
    rssi: "信号",
    secure: "加密",
    openSettings: "打开系统设置"
  };

  function nativeReady() {
    return !!(window.IcuNative && typeof window.IcuNative.bleState === "function");
  }

  function requestedDeviceTarget() {
    var match = /(?:[?&]device=)(monitor|host)(?:&|$)/.exec(window.location.search || "");
    return match ? match[1] : "";
  }

  function readState() {
    if (!nativeReady()) {
      return {};
    }
    try {
      return JSON.parse(window.IcuNative.bleState() || "{}") || {};
    } catch (error) {
      return {};
    }
  }

  function callNative(target, action, value) {
    if (nativeReady() && typeof window.IcuNative.bleAction === "function") {
      try {
        window.IcuNative.bleAction(target, action, value || "");
      } catch (error) {
      }
    }
    scheduleRender(180);
  }

  function scheduleRender(delay) {
    window.clearTimeout(renderTimer);
    if (interacting) {
      pendingRender = true;
      return;
    }
    renderTimer = window.setTimeout(render, delay === undefined ? 60 : delay);
  }

  function beginInteraction() {
    interacting = true;
    pendingRender = false;
    window.clearTimeout(renderTimer);
  }

  function endInteraction() {
    interacting = false;
    if (pendingRender) {
      pendingRender = false;
      window.clearTimeout(renderTimer);
      renderTimer = window.setTimeout(render, 60);
    }
  }

  function clean(value) {
    if (value === undefined || value === null) {
      return "";
    }
    value = String(value).replace(/\s+/g, " ").trim();
    if (!value || value === "null" || value === "undefined" || value === "<unknown ssid>") {
      return "";
    }
    return value;
  }

  function display(value) {
    return clean(value) || "--";
  }

  function escapeHtml(value) {
    return display(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function escapeAttr(value) {
    return clean(value)
      .replace(/&/g, "&amp;")
      .replace(/"/g, "&quot;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function deviceTitle(device) {
    if (!device) {
      return "";
    }
    return clean(device.name) || clean(device.deviceName) || clean(device.id) || clean(device.deviceId);
  }

  function connectedTitle(state) {
    if (!state) {
      return "";
    }
    return clean(state.deviceName) || clean(state.deviceId);
  }

  function hostNameValue(state) {
    var host = state.host || {};
    if (!host.connected) {
      return "";
    }
    return connectedTitle(host);
  }

  function connectedValue(device) {
    if (device && device.connected) {
      return connectedTitle(device);
    }
    return "";
  }

  function wifiOptions(wifi) {
    var list = [];
    var seen = {};
    var networks = (wifi && wifi.networks) || [];
    for (var i = 0; i < networks.length; i += 1) {
      var item = networks[i] || {};
      var ssid = clean(item.ssid || item.name);
      if (!ssid || seen[ssid]) {
        continue;
      }
      seen[ssid] = true;
      var metaParts = [];
      if (typeof item.level === "number") {
        metaParts.push(TEXT.rssi + " " + item.level);
      }
      if (item.secure) {
        metaParts.push(TEXT.secure);
      }
      list.push({
        name: ssid,
        value: ssid,
        meta: metaParts.join(" / ")
      });
    }
    return list;
  }

  function bleOptions(items) {
    var list = [];
    var seen = {};
    items = items || [];
    for (var i = 0; i < items.length; i += 1) {
      var item = items[i] || {};
      var id = clean(item.id || item.deviceId);
      var name = deviceTitle(item);
      if (!id || seen[id]) {
        continue;
      }
      seen[id] = true;
      list.push({
        name: name || id,
        value: id,
        meta: (typeof item.rssi === "number" ? TEXT.rssi + " " + item.rssi : "")
      });
    }
    return list;
  }

  function optionsHtml(target, options) {
    if (!options.length) {
      return '<div class="conn-option empty"><span class="conn-option-name">--</span><span class="conn-option-meta">' + TEXT.noData + '</span></div>';
    }
    return options.map(function (item) {
      return [
        '<div class="conn-option" data-target="', escapeAttr(target), '" data-value="', escapeAttr(item.value), '">',
        '<span class="conn-option-name">', escapeHtml(item.name), '</span>',
        '<span class="conn-option-meta">', escapeHtml(item.meta), '</span>',
        '</div>'
      ].join("");
    }).join("");
  }

  function optionSignature(options) {
    options = options || [];
    var parts = [];
    for (var i = 0; i < options.length; i += 1) {
      var item = options[i] || {};
      parts.push([
        escapeAttr(item.name),
        escapeAttr(item.value),
        escapeAttr(item.meta)
      ].join("\u0001"));
    }
    return parts.join("\u0002");
  }

  function statusText(state) {
    var host = state.host || {};
    var monitor = state.monitor || {};
    var wifi = state.wifi || {};
    var hostState = clean(host.state) || (host.connected ? TEXT.connected : TEXT.disconnected);
    var monitorState = clean(monitor.state) || (monitor.connected ? TEXT.connected : TEXT.disconnected);
    var wifiState = wifi.enabled === false ? "--" : display(wifi.current);
    if (host.scanning) {
      hostState = TEXT.scanning;
    }
    if (monitor.scanning) {
      monitorState = TEXT.scanning;
    }
    return [
      TEXT.hostState + ": " + hostState,
      TEXT.monitorState + ": " + monitorState,
      TEXT.wifiState + ": " + wifiState
    ].join("　");
  }

  function createRow(label, target, showToggle) {
    var row = document.createElement("div");
    row.className = "conn-row";

    var labelEl = document.createElement("div");
    labelEl.className = "conn-label";
    labelEl.textContent = label;

    var select = document.createElement("div");
    select.className = "conn-select";
    select.setAttribute("data-target", target);

    var value = document.createElement("div");
    value.className = "conn-value disabled";
    value.setAttribute("data-open", target);
    value.textContent = "--";

    var dropdown = document.createElement("div");
    dropdown.className = "conn-dropdown";

    select.appendChild(value);
    select.appendChild(dropdown);
    row.appendChild(labelEl);
    row.appendChild(select);

    var toggle = null;
    if (showToggle) {
      toggle = document.createElement("div");
      toggle.className = "conn-toggle";
      toggle.setAttribute("data-toggle", target);
      row.appendChild(toggle);
    } else {
      var placeholder = document.createElement("div");
      placeholder.className = "conn-toggle-placeholder";
      row.appendChild(placeholder);
    }

    var entry = {
      target: target,
      row: row,
      select: select,
      value: value,
      dropdown: dropdown,
      toggle: toggle
    };
    panel.appendChild(row);
    rows[target] = entry;
    return entry;
  }

  function buildRows() {
    if (!panel) {
      return;
    }
    rows.hostName = createRow(TEXT.hostName, "hostName", false);
    rows.wifi = createRow(TEXT.wifi, "wifi", true);
    rows.host = createRow(TEXT.hostBle, "host", true);
    rows.monitor = createRow(TEXT.monitorBle, "monitor", true);

    statusEl = document.createElement("div");
    statusEl.className = "conn-status";
    panel.appendChild(statusEl);

    var actions = document.createElement("div");
    actions.className = "conn-actions";
    var saveBtn = document.createElement("button");
    saveBtn.className = "conn-btn";
    saveBtn.setAttribute("data-command", "save");
    saveBtn.textContent = TEXT.save;
    var cancelBtn = document.createElement("button");
    cancelBtn.className = "conn-btn primary";
    cancelBtn.setAttribute("data-command", "cancel");
    cancelBtn.textContent = TEXT.cancel;
    actions.appendChild(saveBtn);
    actions.appendChild(cancelBtn);
    panel.appendChild(actions);
  }

  function updateOptions(entry, options) {
    var sig = optionSignature(options);
    if (optionSignatureMap[entry.target] === sig) {
      return;
    }
    optionSignatureMap[entry.target] = sig;
    entry.dropdown.innerHTML = optionsHtml(entry.target, options);
  }

  function updateRow(entry, rawValue, clickable, isOn, options) {
    if (!entry) {
      return;
    }
    var text = display(rawValue);
    if (entry.value.textContent !== text) {
      entry.value.textContent = text;
    }
    var valueClass = "conn-value" + (clickable ? " clickable" : " disabled");
    if (entry.value.className !== valueClass) {
      entry.value.className = valueClass;
    }
    if (entry.toggle) {
      var toggleClass = "conn-toggle" + (isOn ? " on" : "");
      if (entry.toggle.className !== toggleClass) {
        entry.toggle.className = toggleClass;
      }
    }
    var wantsOpen = openTarget === entry.target;
    var isOpen = entry.select.classList.contains("open");
    if (wantsOpen !== isOpen) {
      if (wantsOpen) {
        entry.select.classList.add("open");
      } else {
        entry.select.classList.remove("open");
      }
    }
    if (options !== undefined) {
      updateOptions(entry, options);
    }
  }

  function render() {
    if (!panel) {
      panel = document.getElementById("icu-ble-panel");
    }
    if (!panel) {
      return;
    }
    if (!rows.hostName) {
      buildRows();
    }
    var state = readState();
    var host = state.host || {};
    var monitor = state.monitor || {};
    var wifi = state.wifi || {};

    updateRow(rows.hostName, hostNameValue(state), false, false, []);
    updateRow(rows.wifi, clean(wifi.current), clean(wifi.current) !== "" || wifi.enabled === true, true, wifiOptions(wifi));
    updateRow(rows.host, connectedValue(host), true, !!host.connected || !!host.scanning, bleOptions(host.devices));
    updateRow(rows.monitor, connectedValue(monitor), true, !!monitor.connected || !!monitor.scanning, bleOptions(monitor.devices));

    if (statusEl) {
      var status = statusText(state);
      if (statusEl.textContent !== status) {
        statusEl.textContent = status;
      }
    }
  }

  function openSelect(target) {
    if (target === "hostName") {
      openTarget = "";
      scheduleRender(0);
      return;
    }
    openTarget = openTarget === target ? "" : target;
    if (openTarget === "wifi") {
      callNative("wifi", "scan", "");
    } else if (openTarget === "host" || openTarget === "monitor") {
      callNative(openTarget, "scan", "");
    }
    scheduleRender(0);
  }

  function onSelect(target, value) {
    openTarget = "";
    if (target === "wifi") {
      callNative("wifi", "select", value);
      return;
    }
    if (target === "host" || target === "monitor") {
      callNative(target, "connect", value);
    }
  }

  function onToggle(target) {
    var state = readState();
    if (target === "wifi") {
      openTarget = "wifi";
      if (state.wifi && state.wifi.enabled === false) {
        callNative("wifi", "settings", "");
      } else {
        callNative("wifi", "scan", "");
      }
      return;
    }
    if (target === "host") {
      var host = state.host || {};
      if (host.connected) {
        openTarget = "";
        callNative("host", "disconnect", "");
      } else if (host.scanning) {
        // 扫描中再次点击开关 = 取消扫描,避免“看起来开了却关不掉”
        openTarget = "";
        callNative("host", "stop", "");
      } else {
        openSelect("host");
      }
      return;
    }
    if (target === "monitor") {
      var monitor = state.monitor || {};
      if (monitor.connected) {
        openTarget = "";
        callNative("monitor", "disconnect", "");
      } else if (monitor.scanning) {
        openTarget = "";
        callNative("monitor", "stop", "");
      } else {
        openSelect("monitor");
      }
    }
  }

  document.addEventListener("click", function (event) {
    var option = event.target.closest ? event.target.closest(".conn-option") : null;
    if (option && !option.className.match(/\bempty\b/)) {
      onSelect(option.getAttribute("data-target"), option.getAttribute("data-value"));
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    var toggle = event.target.closest ? event.target.closest("[data-toggle]") : null;
    if (toggle) {
      onToggle(toggle.getAttribute("data-toggle"));
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    var opener = event.target.closest ? event.target.closest("[data-open]") : null;
    if (opener) {
      openSelect(opener.getAttribute("data-open"));
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    var command = event.target.closest ? event.target.closest("[data-command]") : null;
    if (command) {
      if (command.getAttribute("data-command") === "save") {
        callNative("host", "readAll", "");
      } else {
        openTarget = "";
        scheduleRender(0);
      }
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    if (openTarget && !(event.target.closest && event.target.closest("#icu-ble-panel"))) {
      openTarget = "";
      scheduleRender(0);
    }
  }, true);

  function installInteractionGuard() {
    if (typeof window.PointerEvent === "function") {
      document.addEventListener("pointerdown", function (event) {
        if (panel && event.target && panel.contains(event.target)) {
          beginInteraction();
        }
      }, true);
      document.addEventListener("pointerup", endInteraction, true);
      document.addEventListener("pointercancel", endInteraction, true);
    } else {
      document.addEventListener("touchstart", function (event) {
        if (panel && event.target && panel.contains(event.target)) {
          beginInteraction();
        }
      }, true);
      document.addEventListener("touchend", endInteraction, true);
      document.addEventListener("touchcancel", endInteraction, true);
      document.addEventListener("mousedown", function (event) {
        if (panel && event.target && panel.contains(event.target)) {
          beginInteraction();
        }
      }, true);
      document.addEventListener("mouseup", endInteraction, true);
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    var requestedTarget = requestedDeviceTarget();
    if (requestedTarget) {
      openTarget = requestedTarget;
      callNative(requestedTarget, "scan", "");
    }
    installInteractionGuard();
    render();
    window.setInterval(function () {
      scheduleRender(0);
    }, 1200);
  });

  window.addEventListener("icu-native-state", function () {
    scheduleRender(0);
  });
})();
