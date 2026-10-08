(function () {
  "use strict";

  // === 性能配置（GPU 缩放版） ===
  // 不再做 JS 双三次插值！只算 32×24=768 像素的 RGB，缩放交给浏览器/GPU。
  // 这是 Kirin 710A 平板的根本修复：JS 运算从 12288 像素 → 768 像素（-94%）。

  var canvas;
  var context;
  var fullscreenCanvas;
  var fullscreenContext;
  var connectionState = "等待红外测温桥";
  var latestTemperatures;
  var latestWidth = 32;
  var latestHeight = 24;
  var displayedMin;
  var displayedMax;
  var rawCanvas;                // 32×24 离屏 canvas，浏览器用 GPU 缩放
  var rawContext;
  var rawImageData;

  // 颜色锚点保持与原版完全一致，仅用于一次性构建 LUT
  var colorStops = [
    [0, 0, 4], [31, 12, 72], [85, 15, 109], [136, 34, 106],
    [186, 54, 85], [227, 89, 51], [249, 140, 10], [252, 205, 37], [252, 255, 164]
  ];

  // === Inferno 颜色查找表（256 项） ===
  // 替换原 inferno() 函数：将每像素 9 段线性插值 → 1 次数组查表
  // 单帧 49152 像素时，颜色计算总耗时下降 ~9x
  var INFERNO_LUT_R = new Uint8Array(256);
  var INFERNO_LUT_G = new Uint8Array(256);
  var INFERNO_LUT_B = new Uint8Array(256);
  (function buildInfernoLut() {
    for (var i = 0; i < 256; i += 1) {
      var t = i / 255;
      var scaled = t * (colorStops.length - 1);
      var index = Math.min(colorStops.length - 2, Math.floor(scaled));
      var ratio = scaled - index;
      var start = colorStops[index];
      var end = colorStops[index + 1];
      INFERNO_LUT_R[i] = (start[0] + (end[0] - start[0]) * ratio) | 0;
      INFERNO_LUT_G[i] = (start[1] + (end[1] - start[1]) * ratio) | 0;
      INFERNO_LUT_B[i] = (start[2] + (end[2] - start[2]) * ratio) | 0;
    }
  })();
  function infernoLut(r, g, b, t) {
    var idx = (t < 0 ? 0 : t > 1 ? 255 : (t * 255) | 0);
    r[0] = INFERNO_LUT_R[idx];
    g[0] = INFERNO_LUT_G[idx];
    b[0] = INFERNO_LUT_B[idx];
  }

  // === 预分配数组，避免每帧 GC 压力 ===
  var sortScratch = [];       // 温度排序用
  var decodeBuffer;            // Uint8Array for atob result
  var tempBuffer;             // Float32Array for centi-degrees → ℃

  // === requestAnimationFrame 单帧调度 + 自动丢帧 ===
  var renderScheduled = false;
  var pendingFrameBase64 = null;
  var pendingFrameW = 0;
  var pendingFrameH = 0;

  // === 诊断日志面板（临时调试用） ===
  // 屏幕底部显示最近 25 条事件 + 帧统计，方便截图反馈。
  // 修好连接问题后可删除。
  var logEntries = [];
  var MAX_LOG_ENTRIES = 25;
  var thermalLogDiv = null;
  var frameCount = 0;
  var lastFrameTime = 0;
  var logIntervalHandle = null;

  function pad2(value) {
    return value < 10 ? "0" + value : "" + value;
  }

  function addLog(message) {
    var now = new Date();
    var ts = pad2(now.getHours()) + ":" + pad2(now.getMinutes()) + ":" + pad2(now.getSeconds());
    logEntries.unshift("[" + ts + "] " + message);
    if (logEntries.length > MAX_LOG_ENTRIES) {
      logEntries.length = MAX_LOG_ENTRIES;
    }
    renderThermalLog();
  }

  function renderThermalLog() {
    if (!thermalLogDiv) return;
    var stats = "状态: " + (connectionState || "?") +
                " | 帧: " + frameCount +
                " | 距上次: " + (lastFrameTime > 0
                    ? ((Date.now() - lastFrameTime) / 1000).toFixed(1) + "s"
                    : "-");
    var lines = "<div style=\"color:#ff0;border-bottom:1px solid #444;padding-bottom:2px;margin-bottom:2px;\">"
        + escapeHtml(stats) + "</div>";
    for (var i = 0; i < logEntries.length; i += 1) {
      var entry = logEntries[i];
      var color = "#0f0";
      if (entry.indexOf("错误") >= 0 || entry.indexOf("失败") >= 0 || entry.indexOf("超时") >= 0) {
        color = "#f55";
      } else if (entry.indexOf("成功") >= 0 || entry.indexOf("已连接") >= 0) {
        color = "#5f5";
      } else if (entry.indexOf("用户") >= 0 || entry.indexOf("Native") >= 0) {
        color = "#5ff";
      }
      lines += "<div style=\"color:" + color + ";white-space:nowrap;\">"
          + escapeHtml(entry) + "</div>";
    }
    thermalLogDiv.innerHTML = lines;
  }

  function ensureThermalLogDiv() {
    if (thermalLogDiv) return thermalLogDiv;
    thermalLogDiv = document.createElement("div");
    thermalLogDiv.id = "icu-thermal-log";
    thermalLogDiv.style.cssText =
        "position:fixed;left:8px;right:8px;bottom:8px;max-height:32%;" +
        "overflow-y:auto;background:rgba(0,0,0,0.78);color:#0f0;" +
        "font-family:monospace;font-size:11px;line-height:1.4;" +
        "padding:6px 10px;border-radius:4px;z-index:99999;" +
        "pointer-events:none;border:1px solid #333;";
    var host = document.getElementById("icu-thermal-fullscreen") || document.body;
    host.appendChild(thermalLogDiv);
    // 1 秒刷新一次"距上次帧"显示
    if (logIntervalHandle) clearInterval(logIntervalHandle);
    logIntervalHandle = setInterval(renderThermalLog, 1000);
    return thermalLogDiv;
  }

  function dismissThermalLog() {
    if (logIntervalHandle) {
      window.clearInterval(logIntervalHandle);
      logIntervalHandle = null;
    }
    if (thermalLogDiv) {
      thermalLogDiv.remove();
      thermalLogDiv = null;
    }
    logEntries.length = 0;
  }

  function initialize() {
    canvas = document.getElementById("icu-thermal-canvas");
    if (!canvas) {
      return;
    }
    context = canvas.getContext("2d", { alpha: false });
    readInitialConnectionState();
    drawPlaceholder(context, canvas);
    canvas.addEventListener("click", openFullscreen);
    canvas.addEventListener("touchend", function (event) {
      event.preventDefault();
      openFullscreen();
    }, { passive: false });
  }

  function readInitialConnectionState() {
    try {
      if (window.IcuNative && typeof window.IcuNative.thermalState === "function") {
        connectionState = window.IcuNative.thermalState() || connectionState;
      }
    } catch (error) {
    }
  }

  function drawPlaceholder(targetContext, targetCanvas) {
    if (!targetContext || !targetCanvas) {
      return;
    }
    var width = targetCanvas.width;
    var height = targetCanvas.height;
    targetContext.fillStyle = "#111820";
    targetContext.fillRect(0, 0, width, height);
    targetContext.fillStyle = "#f4f8fc";
    targetContext.font = "bold " + Math.max(20, Math.round(width * 0.048)) + "px sans-serif";
    targetContext.fillText("红外热图", Math.round(width * 0.04), Math.round(height * 0.13));
    targetContext.fillStyle = "#8fa5b9";
    targetContext.font = Math.max(16, Math.round(width * 0.036)) + "px sans-serif";
    targetContext.fillText(connectionState, Math.round(width * 0.04), Math.round(height * 0.28));
    targetContext.strokeStyle = "#33495c";
    targetContext.lineWidth = Math.max(1, Math.round(width * 0.004));
    targetContext.strokeRect(Math.round(width * 0.04), Math.round(height * 0.35),
      Math.round(width * 0.92), Math.round(height * 0.57));
  }

  // === 重写：把 base64 解析与温度转换抽出来，先缓存到 pendingFrame，避免在 renderHeatmap 内做重活 ===
  function receiveFrame(base64, sourceWidth, sourceHeight) {
    if (!context || !canvas || sourceWidth !== 32 || sourceHeight !== 24 || !base64) {
      addLog("帧被拒 (context=" + !!context + " w=" + sourceWidth + " h=" + sourceHeight + ")");
      return;
    }
    frameCount += 1;
    lastFrameTime = Date.now();
    if (frameCount === 1 || frameCount % 20 === 0) {
      addLog("收到第 " + frameCount + " 帧 (" + base64.length + "B base64)");
    }
    pendingFrameBase64 = base64;
    pendingFrameW = sourceWidth;
    pendingFrameH = sourceHeight;
    connectionState = "实时数据";
    if (renderScheduled) {
      // 已有渲染排队，本次只覆盖待渲染数据（自动丢中间帧）
      return;
    }
    renderScheduled = true;
    var raf = window.requestAnimationFrame || function (cb) { return window.setTimeout(cb, 16); };
    raf(performScheduledRender);
  }

  function performScheduledRender() {
    renderScheduled = false;
    if (!pendingFrameBase64) {
      return;
    }
    // 不可见时跳过渲染（页面在后台或全屏未打开），保留 latestTemperatures 给可见时追上
    if (document.hidden) {
      return;
    }

    // 解析 base64 → 温度数组
    var bytes;
    try {
      var binary = window.atob(pendingFrameBase64);
      if (binary.length !== pendingFrameW * pendingFrameH * 2) {
        pendingFrameBase64 = null;
        return;
      }
      if (!decodeBuffer || decodeBuffer.length !== binary.length) {
        decodeBuffer = new Uint8Array(binary.length);
      }
      for (var di = 0; di < binary.length; di += 1) {
        decodeBuffer[di] = binary.charCodeAt(di);
      }
      bytes = decodeBuffer;
    } catch (error) {
      pendingFrameBase64 = null;
      return;
    }
    var pixelCount = pendingFrameW * pendingFrameH;
    if (!tempBuffer || tempBuffer.length !== pixelCount) {
      tempBuffer = new Float32Array(pixelCount);
    }
    for (var p = 0; p < pixelCount; p += 1) {
      var raw = bytes[p * 2] | (bytes[p * 2 + 1] << 8);
      tempBuffer[p] = (raw & 0x8000 ? raw - 0x10000 : raw) / 100;
    }
    latestTemperatures = tempBuffer;
    latestWidth = pendingFrameW;
    latestHeight = pendingFrameH;
    pendingFrameBase64 = null;

    renderHeatmap(context, canvas, latestTemperatures, latestWidth, latestHeight);
    if (fullscreenCanvas && fullscreenContext) {
      renderHeatmap(fullscreenContext, fullscreenCanvas, latestTemperatures, latestWidth, latestHeight);
    }
  }

  function renderHeatmap(targetContext, targetCanvas, temperatures, sourceWidth, sourceHeight) {
    // === 计算显示温度范围 ===
    var pixelCount = temperatures.length;
    if (!sortScratch.length) {
      sortScratch = new Array(pixelCount);
    } else if (sortScratch.length !== pixelCount) {
      sortScratch = new Array(pixelCount);
    }
    for (var si = 0; si < pixelCount; si += 1) {
      sortScratch[si] = temperatures[si];
    }
    sortScratch.sort(function (a, b) { return a - b; });
    var targetMin = sortScratch[Math.floor((pixelCount - 1) * 0.03)];
    var targetMax = sortScratch[Math.floor((pixelCount - 1) * 0.97)];
    if (targetMax - targetMin < 2.0) {
      targetMax = targetMin + 2.0;
    }
    // 平滑颜色范围，避免帧间闪烁
    displayedMin = displayedMin === undefined ? targetMin : displayedMin * 0.82 + targetMin * 0.18;
    displayedMax = displayedMax === undefined ? targetMax : displayedMax * 0.82 + targetMax * 0.18;
    if (displayedMax - displayedMin < 2.0) {
      displayedMax = displayedMin + 2.0;
    }
    var range = displayedMax - displayedMin;

    // === 找最热像素 ===
    var hottest = temperatures[0];
    var hottestIndex = 0;
    for (var i = 1; i < pixelCount; i += 1) {
      if (temperatures[i] > hottest) {
        hottest = temperatures[i];
        hottestIndex = i;
      }
    }

    // === GPU 缩放：只画 32×24 原始数据 ===
    // 关键：必须显式设置 rawCanvas.width/height，否则默认 300×150 会被 drawImage 拉伸
    if (!rawCanvas) {
      rawCanvas = document.createElement("canvas");
    }
    if (rawCanvas.width !== sourceWidth || rawCanvas.height !== sourceHeight) {
      rawCanvas.width = sourceWidth;
      rawCanvas.height = sourceHeight;
      rawContext = rawCanvas.getContext("2d", { alpha: false });
      rawImageData = rawContext.createImageData(sourceWidth, sourceHeight);
    }
    // 主循环：768 像素（原 12288，-94%）。颜色用 inferno LUT，零双三次插值。
    var r = [0], g = [0], b = [0];
    var dmin = displayedMin;
    var data = rawImageData.data;
    var temps = temperatures;
    for (var py = 0; py < sourceHeight; py += 1) {
      var rowOff = py * sourceWidth * 4;
      for (var px = 0; px < sourceWidth; px += 1) {
        infernoLut(r, g, b, (temps[py * sourceWidth + px] - dmin) / range);
        var off = rowOff + px * 4;
        data[off] = r[0];
        data[off + 1] = g[0];
        data[off + 2] = b[0];
        data[off + 3] = 255;
      }
    }
    rawContext.putImageData(rawImageData, 0, 0);

    // === 绘制到目标 canvas（GPU 双线性缩放） ===
    var width = targetCanvas.width;
    var height = targetCanvas.height;
    var margin = Math.round(Math.min(width, height) * 0.025);
    var imageHeight = height - margin * 2;
    var imageWidth = Math.round(imageHeight * sourceWidth / sourceHeight);
    if (imageWidth > width - margin * 2) {
      imageWidth = width - margin * 2;
      imageHeight = Math.round(imageWidth * sourceHeight / sourceWidth);
    }
    var imageX = Math.round((width - imageWidth) / 2);
    var imageY = Math.round((height - imageHeight) / 2);
    targetContext.fillStyle = "#17002a";
    targetContext.fillRect(0, 0, width, height);
    // 浏览器/GPU 用 imageSmoothingEnabled 双线性插值缩放（Mali-G51 硬件）
    targetContext.imageSmoothingEnabled = true;
    targetContext.imageSmoothingQuality = "high";
    targetContext.drawImage(rawCanvas, imageX, imageY, imageWidth, imageHeight);
    targetContext.strokeStyle = "#00e792";
    targetContext.lineWidth = Math.max(2, Math.round(Math.min(width, height) * 0.003));
    targetContext.strokeRect(imageX, imageY, imageWidth, imageHeight);

    // === 最热点十字（直接用源 32×24 坐标，不再插值） ===
    var hotX = imageX + ((hottestIndex % sourceWidth) + 0.5) * imageWidth / sourceWidth;
    var hotY = imageY + (Math.floor(hottestIndex / sourceWidth) + 0.5) * imageHeight / sourceHeight;
    var crossSize = Math.max(8, Math.round(Math.min(width, height) * 0.018));
    targetContext.strokeStyle = "#ff5d67";
    targetContext.lineWidth = Math.max(2, Math.round(Math.min(width, height) * 0.003));
    targetContext.beginPath();
    targetContext.moveTo(hotX - crossSize, hotY);
    targetContext.lineTo(hotX + crossSize, hotY);
    targetContext.moveTo(hotX, hotY - crossSize);
    targetContext.lineTo(hotX, hotY + crossSize);
    targetContext.stroke();
    drawTemperatureLabel(targetContext, "MAX " + hottest.toFixed(1) + "°C", hotX + crossSize + 5, hotY - crossSize - 5,
      "#ff5d67", width, height);

    // === 中心十字（中心点温度取源 (15, 11) 像素） ===
    var centerX = imageX + imageWidth / 2;
    var centerY = imageY + imageHeight / 2;
    var centerIndex = 11 * sourceWidth + 15;  // 24×32 阵列的中心
    var centerTemperature = centerIndex < pixelCount ? temperatures[centerIndex] : 0;
    targetContext.strokeStyle = "#ffffff";
    targetContext.lineWidth = Math.max(1, Math.round(Math.min(width, height) * 0.002));
    targetContext.beginPath();
    targetContext.moveTo(centerX - crossSize, centerY);
    targetContext.lineTo(centerX + crossSize, centerY);
    targetContext.moveTo(centerX, centerY - crossSize);
    targetContext.lineTo(centerX, centerY + crossSize);
    targetContext.stroke();
    drawTemperatureLabel(targetContext, centerTemperature.toFixed(1) + "°C", centerX + crossSize + 5,
      centerY - crossSize - 5, "#ffffff", width, height);
    targetContext.textAlign = "left";
  }

  function drawTemperatureLabel(targetContext, label, x, y, color, width, height) {
    var fontSize = Math.max(16, Math.round(Math.min(width, height) * 0.034));
    targetContext.font = "bold " + fontSize + "px sans-serif";
    var textWidth = targetContext.measureText(label).width;
    var padding = Math.round(fontSize * 0.35);
    x = Math.max(padding, Math.min(width - textWidth - padding * 2, x));
    y = Math.max(fontSize + padding, Math.min(height - padding, y));
    targetContext.fillStyle = "rgba(28, 31, 33, 0.78)";
    targetContext.fillRect(x - padding, y - fontSize - padding, textWidth + padding * 2, fontSize + padding * 1.5);
    targetContext.fillStyle = color;
    targetContext.fillText(label, x, y - padding * 0.15);
  }

  function openFullscreen() {
    if (document.getElementById("icu-thermal-fullscreen")) {
      return;
    }
    var overlay = document.createElement("div");
    overlay.id = "icu-thermal-fullscreen";
    addLog("打开全屏热图");
    var fullCanvas = document.createElement("canvas");
    // 硬件适配：Kirin 710A / 1920×1200 屏幕上 devicePixelRatio=2 会让 canvas 达到 3840×2400=9.2M 像素，
    // drawImage 单次绘制开销极高。限制 ratio 上限到 1.5，把全屏 canvas 控制在 ~5M 像素，视觉无明显差异。
    var ratio = Math.min(window.devicePixelRatio || 1, 1.5);
    fullCanvas.width = Math.max(1, Math.floor(window.innerWidth * ratio));
    fullCanvas.height = Math.max(1, Math.floor(window.innerHeight * ratio));
    var close = document.createElement("button");
    close.type = "button";
    close.className = "icu-thermal-close";
    close.setAttribute("aria-label", "关闭红外热图全屏");
    close.textContent = "×";
    close.addEventListener("click", function () { closeFullscreen(overlay); });
    var connect = document.createElement("button");
    connect.type = "button";
    connect.className = "icu-thermal-connect";
    connect.textContent = "连接红外";
    connect.addEventListener("click", requestConnection);
    connect.addEventListener("touchend", function (event) {
      event.preventDefault();
      event.stopPropagation();
      requestConnection();
    }, { passive: false });
    overlay.appendChild(fullCanvas);
    overlay.appendChild(close);
    overlay.appendChild(connect);
    document.body.appendChild(overlay);
    setNativeFullscreen(true);
    var fullContext = fullCanvas.getContext("2d", { alpha: false });
    fullscreenCanvas = fullCanvas;
    fullscreenContext = fullContext;
    ensureThermalLogDiv();
    addLog("全屏 overlay 已就绪，Native 状态=" + connectionState);
    if (latestTemperatures) {
      renderHeatmap(fullContext, fullCanvas, latestTemperatures, latestWidth, latestHeight);
    } else {
      drawPlaceholder(fullContext, fullCanvas);
    }
  }

  function closeFullscreen(overlay) {
    fullscreenCanvas = null;
    fullscreenContext = null;
    if (overlay) {
      overlay.remove();
    }
    setNativeFullscreen(false);
  }

  function logRequestConnection() {
    addLog("用户点击「连接红外」");
  }

  function setNativeFullscreen(visible) {
    try {
      if (window.IcuNative && typeof window.IcuNative.thermalFullscreen === "function") {
        window.IcuNative.thermalFullscreen(visible);
      }
    } catch (error) {
    }
  }

  function requestConnection() {
    addLog("用户点击「连接红外」");
    openDevicePicker();
    requestDeviceScan();
  }

  function openDevicePicker() {
    if (document.getElementById("icu-thermal-device-picker")) {
      return;
    }
    var picker = document.createElement("div");
    picker.id = "icu-thermal-device-picker";
    picker.innerHTML = "<div class='icu-thermal-picker-head'>选择红外测温桥</div>"
      + "<button type='button' class='icu-thermal-picker-close' aria-label='关闭设备列表'>×</button>"
      + "<div class='icu-thermal-device-list'></div>"
      + "<div class='icu-thermal-device-empty'>正在扫描附近设备...</div>";
    var close = picker.querySelector(".icu-thermal-picker-close");
    close.addEventListener("click", function () { closeDevicePicker(picker); });
    var fullscreen = document.getElementById("icu-thermal-fullscreen");
    (fullscreen || document.body).appendChild(picker);
    picker._refreshTimer = window.setInterval(refreshDeviceList, 900);
    refreshDeviceList();
  }

  function closeDevicePicker(picker) {
    if (!picker) {
      return;
    }
    if (picker._refreshTimer) {
      window.clearInterval(picker._refreshTimer);
    }
    picker.remove();
  }

  function requestDeviceScan() {
    try {
      if (window.IcuNative && typeof window.IcuNative.thermalAction === "function") {
        addLog("请求 Native 扫描 BLE 设备");
        window.IcuNative.thermalAction("scan", "");
      } else {
        addLog("错误: IcuNative.thermalAction 不存在");
      }
    } catch (error) {
      addLog("错误: thermalAction 异常 " + error);
    }
  }

  function readThermalDevices() {
    try {
      if (window.IcuNative && typeof window.IcuNative.thermalDevices === "function") {
        return JSON.parse(window.IcuNative.thermalDevices() || "[]");
      }
    } catch (error) {
    }
    return [];
  }

  function refreshDeviceList() {
    var picker = document.getElementById("icu-thermal-device-picker");
    if (!picker) {
      return;
    }
    var devices = readThermalDevices();
    var list = picker.querySelector(".icu-thermal-device-list");
    var empty = picker.querySelector(".icu-thermal-device-empty");
    list.innerHTML = "";
    empty.style.display = devices.length ? "none" : "block";
    if (frameCount === 0 && devices.length > 0) {
      addLog("扫描到 " + devices.length + " 个设备");
    }
    for (var index = 0; index < devices.length; index += 1) {
      (function (device) {
        var item = document.createElement("button");
        item.type = "button";
        item.className = "icu-thermal-device";
        item.innerHTML = "<strong>" + escapeHtml(device.name || "红外测温桥") + "</strong>"
          + "<span>" + escapeHtml(device.id || "") + "  ·  " + (device.rssi || 0) + " dBm</span>";
        item.addEventListener("click", function () {
          try {
            addLog("用户选中设备: " + (device.name || "?") + " [" + (device.id || "?") + "]");
            window.IcuNative.thermalAction("connect", device.id || "");
          } catch (error) {
            addLog("错误: connect 调用异常 " + error);
          }
          closeDevicePicker(picker);
        });
        list.appendChild(item);
      })(devices[index]);
    }
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>\"]/g, function (character) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;" }[character];
    });
  }

  window.IcuThermal = {
    receiveFrame: receiveFrame,
    setConnectionState: function (state) {
      var oldState = connectionState;
      connectionState = state || "等待红外测温桥";
      if (connectionState.indexOf("已连接") >= 0) {
        dismissThermalLog();
      }
      addLog("Native 状态: " + oldState + " → " + connectionState);
      if (!latestTemperatures) {
        drawPlaceholder(context, canvas);
      }
    },
    addLog: addLog
  };

  document.addEventListener("DOMContentLoaded", initialize);
  window.addEventListener("icu-thermal-devices", refreshDeviceList);
})();
