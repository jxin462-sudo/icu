(function () {
  var DESIGN_WIDTH = 1920;
  var DESIGN_HEIGHT = 1200;
  var rafId = 0;
  var observersReady = false;
  var stableViewport = { width: 0, height: 0 };

  function normalizeViewportMeta() {
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'viewport');
      document.head.appendChild(meta);
    }
    meta.setAttribute('content', 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover');
  }

  function editableFocused() {
    var node = document.activeElement;
    if (!node) {
      return false;
    }
    var tag = String(node.tagName || '').toUpperCase();
    return tag === 'INPUT' || tag === 'TEXTAREA' || node.isContentEditable;
  }

  function rawViewportSize() {
    var visualViewport = window.visualViewport;
    var width = Math.max(1, Math.floor(
      window.innerWidth ||
      document.documentElement.clientWidth ||
      (visualViewport && visualViewport.width) ||
      DESIGN_WIDTH
    ));
    var height = Math.max(1, Math.floor(
      window.innerHeight ||
      document.documentElement.clientHeight ||
      (visualViewport && visualViewport.height) ||
      DESIGN_HEIGHT
    ));
    return { width: width, height: height };
  }

  function getViewportSize() {
    var viewport = rawViewportSize();
    var focused = editableFocused();
    var sameWidth = stableViewport.width > 0 && Math.abs(viewport.width - stableViewport.width) < 80;
    var keyboardShrink = focused && sameWidth && stableViewport.height > 0 && viewport.height < stableViewport.height * 0.88;

    if (!keyboardShrink) {
      stableViewport.width = viewport.width;
      stableViewport.height = viewport.height;
      return viewport;
    }

    return {
      width: stableViewport.width || viewport.width,
      height: stableViewport.height || viewport.height
    };
  }

  function getPageSize(page) {
    return { width: DESIGN_WIDTH, height: DESIGN_HEIGHT };
  }

  var lastFitAt = 0;
  var lastFitViewport = { width: 0, height: 0 };
  var lastFitScale = -1;
  var lastFitLeft = -1;
  var lastFitTop = -1;

  function fitLanhuPage() {
    rafId = 0;
    var page = document.querySelector('.page');
    if (!page) {
      return;
    }

    var viewport = getViewportSize();
    var pageSize = getPageSize(page);
    if (
      Math.abs(viewport.width - lastFitViewport.width) < 0.5 &&
      Math.abs(viewport.height - lastFitViewport.height) < 0.5
    ) {
      return;
    }
    var scale = Math.min(viewport.width / pageSize.width, viewport.height / pageSize.height);
    scale = Math.min(1, Math.max(0.1, scale));

    var left = Math.max(0, (viewport.width - pageSize.width * scale) / 2);
    var top = Math.max(0, (viewport.height - pageSize.height * scale) / 2);
    if (
      Math.abs(scale - lastFitScale) < 0.001 &&
      Math.abs(left - lastFitLeft) < 0.5 &&
      Math.abs(top - lastFitTop) < 0.5
    ) {
      lastFitViewport = viewport;
      return;
    }
    lastFitViewport = viewport;
    lastFitScale = scale;
    lastFitLeft = left;
    lastFitTop = top;
    var rootStyle = document.documentElement.style;
    rootStyle.setProperty('--lanhu-scale', scale.toFixed(6));
    rootStyle.setProperty('--lanhu-left', left.toFixed(2) + 'px');
    rootStyle.setProperty('--lanhu-top', top.toFixed(2) + 'px');
    rootStyle.setProperty('--lanhu-page-width', pageSize.width + 'px');
    rootStyle.setProperty('--lanhu-page-height', pageSize.height + 'px');
    document.documentElement.classList.add('lanhu-fit-ready');

    dispatchFitEvent(scale, left, top, pageSize);
  }

  function dispatchFitEvent(scale, left, top, pageSize) {
    var detail = {
      scale: scale,
      left: left,
      top: top,
      width: pageSize.width,
      height: pageSize.height
    };
    var event;
    if (typeof window.CustomEvent === 'function') {
      event = new CustomEvent('lanhu:fit', { detail: detail });
    } else {
      event = document.createEvent('CustomEvent');
      event.initCustomEvent('lanhu:fit', false, false, detail);
    }
    window.dispatchEvent(event);
  }

  function scheduleFit() {
    var now = Date.now();
    if (now - lastFitAt < 200) {
      return;
    }
    if (rafId) {
      return;
    }
    rafId = window.requestAnimationFrame(function () {
      lastFitAt = Date.now();
      fitLanhuPage();
    });
  }

  /**
   * ★ 无淡入淡出的首帧稳定：lanhu_8x 设置页把 lanhu-fit.js 改为 head 内同步加载。
   *   脚本在 HTML 解析完成前(document.readyState === "loading")先按 viewport 算好
   *   scale/left/top 并缓存，但先不显示页面；等 DOM 完整、二次适配收敛后一次性放行，
   *   避免“先按一个视口显示、随后因布局/视口变化再平移”造成的侧栏文字右移跳动。
   */
  function applyEarlyFitIfLoading() {
    if (document.readyState !== 'loading') {
      return;
    }
    try {
      var viewport = rawViewportSize();
      var scale = Math.min(1, Math.max(0.1,
        viewport.width / DESIGN_WIDTH,
        viewport.height / DESIGN_HEIGHT));
      var left = Math.max(0, (viewport.width - DESIGN_WIDTH * scale) / 2);
      var top = Math.max(0, (viewport.height - DESIGN_HEIGHT * scale) / 2);
      var rootStyle = document.documentElement.style;
      rootStyle.setProperty('--lanhu-scale', scale.toFixed(6));
      rootStyle.setProperty('--lanhu-left', left.toFixed(2) + 'px');
      rootStyle.setProperty('--lanhu-top', top.toFixed(2) + 'px');
      rootStyle.setProperty('--lanhu-page-width', DESIGN_WIDTH + 'px');
      rootStyle.setProperty('--lanhu-page-height', DESIGN_HEIGHT + 'px');
      lastFitViewport = viewport;
      lastFitScale = scale;
      lastFitLeft = left;
      lastFitTop = top;
    } catch (error) {
    }
  }

  function installContentObservers() {
    if (observersReady || !document.body) {
      return;
    }
    observersReady = true;
    var page = document.querySelector('.page');
    if (window.ResizeObserver && page) {
      new ResizeObserver(scheduleFit).observe(page);
    }
  }

  /**
   * 强制覆盖：主机控制页 5 张理疗卡片的图标尺寸
   * 通过 JS 直接修改 element.style.* 属性，绕过所有 CSS 优先级和缓存。
   * 即便 CSS 没有加载、CSS 被覆盖、浏览器缓存，都能让尺寸生效。
   */
  function forceControlPageIcons() {
    // ★ 关键守卫：只在主机控制页（lanhu_2zhujikongzhi）生效
    var page = document.querySelector('.page');
    if (!page || !page.classList.contains('icu-page-control')) {
      return;
    }
    var iconTargets = [
      '.page.icu-page-control .group_39',
      '.page.icu-page-control .section_17',
      '.page.icu-page-control .box_37',
      '.page.icu-page-control .box_41'
    ];
    for (var i = 0; i < iconTargets.length; i += 1) {
      var nodes = document.querySelectorAll(iconTargets[i]);
      for (var j = 0; j < nodes.length; j += 1) {
        var n = nodes[j];
        if (!n) continue;
        // ★ 用 transform 替代 margin-right：CSS 规则重置 margin-right 也无效！
        // transform 在最终合成阶段生效，不受任何后续 CSS 影响
        n.style.setProperty('width', '70px', 'important');
        n.style.setProperty('height', '70px', 'important');
        n.style.setProperty('background-size', '100% 100%', 'important');
        n.style.removeProperty('margin-right');
        // ★ 用 transform 强制左移 20px
        n.style.setProperty('transform', 'translate3d(-20px, 0, 0)', 'important');
        n.style.cssText += '; width: 70px !important; height: 70px !important; background-size: 100% 100% !important; transform: translate3d(-20px, 0, 0) !important; z-index: 1 !important;';
        n.setAttribute('data-icon-forced', '1');
      }
    }
  }

  /**
   * 防御性兜底：使用 MutationObserver 监听 DOM 变化，
   * 防止 JS 重建 .group_39 等节点后丢失我们的强制设置
   */
  function watchControlPageIcons() {
    // ★ 关键守卫：只监控主机控制页的 DOM 变化
    var page = document.querySelector('.page');
    if (!page || !page.classList.contains('icu-page-control')) return;
    var iconClasses = [
      'group_39', 'section_17', 'box_37', 'box_41'
    ];
    if (!document.body) return;
    var observer = new MutationObserver(function (mutations) {
      // 再确认一次当前是控制页（防止页面切换后遗留监控）
      var currentPage = document.querySelector('.page');
      if (!currentPage || !currentPage.classList.contains('icu-page-control')) {
        return;
      }
      for (var i = 0; i < mutations.length; i += 1) {
        var added = mutations[i].addedNodes;
        for (var k = 0; k < added.length; k += 1) {
          var node = added[k];
          if (node && node.nodeType === 1 && node.classList) {
            for (var t = 0; t < iconClasses.length; t += 1) {
              if (node.classList.contains(iconClasses[t])) {
                node.style.removeProperty('margin-right');
                node.style.setProperty('width', '70px', 'important');
                node.style.setProperty('height', '70px', 'important');
                node.style.setProperty('background-size', '100% 100%', 'important');
                node.style.setProperty('transform', 'translate3d(-20px, 0, 0)', 'important');
                node.style.cssText += '; width: 70px !important; height: 70px !important; background-size: 100% 100% !important; transform: translate3d(-20px, 0, 0) !important; z-index: 1 !important;';
              }
            }
          }
        }
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  // ★★ 设置记忆：舱内温度 + 氧浓度
  // - 每次当状态页/控制页收到 native state 更新时，把用户最近设置过的值缓存到 localStorage
  // - 提供 IcuGetLastSetting(action) 给 Java 端弹窗调用（Java 可通过 webview.evaluateJavascript 调用）
  // - localStorage key 格式：lanhu_last_<zone>_<action>  e.g.  lanhu_last_left_control_temp
  // ★ 左右舱隔离：一个主机管两舱，左右舱的"上次设置值"必须分开记，
  //   否则切舱后弹窗会带出另一舱的温度/氧浓度。
  var LAST_PREFIX = 'lanhu_last_';

  function normalizeZone(value) {
    var text = String(value || '').toLowerCase();
    return text === 'right' ? 'right' : 'left';
  }

  function currentZone() {
    return normalizeZone(window.__icuCurrentZone);
  }

  function zoneStorageKey(zone, action) {
    return LAST_PREFIX + normalizeZone(zone) + '_' + action;
  }

  // ★ 旧版只有全局 key（lanhu_last_control_temp）。首次按舱读取时把旧值
  //   迁入当前舱，另一舱保持为空 —— 与患者数据 v2 的迁移策略一致。
  function migrateLegacySetting(zone, action) {
    var zonedKey = zoneStorageKey(zone, action);
    // ★ 旧全局值只迁入升级时所在的那个舱（当前舱），另一舱必须为空，
    //   否则左右舱会同时继承同一份历史设置值，等于没有隔离。
    if (normalizeZone(zone) !== currentZone()) {
      return;
    }
    try {
      if (window.localStorage.getItem(zonedKey)) {
        return;
      }
      var legacy = window.localStorage.getItem(LAST_PREFIX + action);
      if (legacy) {
        window.localStorage.setItem(zonedKey, legacy);
      }
    } catch (e) {
      // localStorage 可能被禁用（如隐身模式）：忽略
    }
  }

  function persistLastSetting(zone, action, value) {
    if (!action || value === undefined || value === null || value === '') return;
    try {
      window.localStorage.setItem(zoneStorageKey(zone, action), String(value));
    } catch (e) {
      // localStorage 可能被禁用（如隐身模式）：忽略
    }
  }

  function readLastSetting(zone, action) {
    if (!action) return '';
    migrateLegacySetting(zone, action);
    try {
      return window.localStorage.getItem(zoneStorageKey(zone, action)) || '';
    } catch (e) {
      return '';
    }
  }

  // ★ 全局暴露给 Java Native 调用：弹窗打开前可调 IcuGetLastSetting(action, zone)
  // Java 端示例（如果在 MainActivity 中需要）：
  //   webView.evaluateJavascript("window.IcuGetLastSetting && IcuGetLastSetting('control_temp', 'left')", callback)
  // zone 缺省时跟随当前 UI 舱。
  window.IcuGetLastSetting = function (action, zone) {
    return readLastSetting(normalizeZone(zone || currentZone()), action);
  };

  // ★ 监听 native state 更新：把每个传感器的"上次值"和"上次设置值"持久化
  // 当用户点"设置"打开弹窗，最后一次编辑过的值会自动缓存
  // 同时监听 state 变化，对比新老值，记录变化（用于确定"上次手动设置"）
  // ★ 左右舱隔离：每个舱各自维护一份传感器历史。
  //   共用一份时，切舱会把"左舱 28℃ → 右舱 22℃"误判成用户改了设置并覆盖上次设置值。
  var sensorHistory = { left: {}, right: {} };

  function trackStateForSettings(state) {
    if (!state || !state.host) return;
    // state.host.zone 是权威当前舱；缺失时退回 live-state 维护的当前舱。
    var zone = normalizeZone(state.host.zone || window.__icuCurrentZone);
    var history = sensorHistory[zone] || (sensorHistory[zone] = {});
    var sensors = {
      control_temp:       ['temp',         '℃'],
      control_oxygen:     ['oxygen',       '%'],
      control_humidity:   ['humidity',     '%'],
      control_co2:        ['co2',          'PPM'],
      control_infrared:   ['infraredTemp', '℃']
    };
    Object.keys(sensors).forEach(function (action) {
      var sensorKey = sensors[action][0];
      var newValue = state.host[sensorKey];
      if (newValue === undefined || newValue === null) return;
      var oldValue = history[sensorKey];
      history[sensorKey] = newValue;
      // 当值发生变化（用户通过弹窗修改后写入设备），把新值作为"上次设置值"
      if (oldValue !== undefined && oldValue !== newValue && Math.abs(parseFloat(newValue) - parseFloat(oldValue)) > 0.01) {
        // ★ Java 侧 formatValue() 已经把单位拼进去了（"28.0℃"），这里再追加
        //   一次会得到 "28.0℃℃"，回填弹窗时显示错值。只在该单位缺失时补。
        var unit = sensors[action][1];
        var stored = String(newValue);
        if (stored.indexOf(unit) < 0) {
          stored = stored + unit;
        }
        persistLastSetting(zone, action, stored);
      }
    });
  }

  // 监听 icu-native-state 事件
  window.addEventListener('icu-native-state', function () {
    // lanhu-live-state-v3.js 已经处理了渲染，我们在这里监听它的 lastState
    if (typeof window.__lastIcuState === 'object' && window.__lastIcuState) {
      trackStateForSettings(window.__lastIcuState);
    }
  });

  // 同时暴露给其他 JS 模块：让 lanhu-live-state-v3.js 写入 state 时通知
  window.__icuTrackStateForSettings = trackStateForSettings;

  normalizeViewportMeta();
  document.addEventListener('DOMContentLoaded', function () {
    installContentObservers();
    scheduleFit();
    forceControlPageIcons();
    watchControlPageIcons();
  });
  // 兜底：load 事件后再执行一次，防止 DOMContentLoaded 早于元素就绪
  window.addEventListener('load', function () {
    forceControlPageIcons();
  });
  // ★ 第 3 重保险：用 requestAnimationFrame 在下一个动画帧再设置一次
  window.requestAnimationFrame(function () {
    forceControlPageIcons();
  });
  // ★ 第 4 重保险：500ms 之后再设置一次，应对延迟加载
  window.setTimeout(function () {
    forceControlPageIcons();
  }, 500);
  window.addEventListener('load', scheduleFit);
  window.addEventListener('resize', scheduleFit);
  window.addEventListener('orientationchange', scheduleFit);
  window.addEventListener('pageshow', scheduleFit);
  document.addEventListener('focusin', scheduleFit, true);
  document.addEventListener('focusout', function () {
    window.setTimeout(scheduleFit, 160);
  }, true);
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', scheduleFit);
  }
  applyEarlyFitIfLoading();
  // 同步加载(lanhu_8x 设置页)时：等 load 事件后再跑一次收敛适配并放行，
  // 若个别资源卡住则 DOMContentLoaded 后 900ms 兜底，避免一直白屏。
  if (document.readyState === 'loading') {
    var revealFired = false;
    function revealSettingsPageWhenStable() {
      if (revealFired) {
        return;
      }
      revealFired = true;
      window.setTimeout(function () {
        fitLanhuPage();
        window.requestAnimationFrame(function () {
          document.documentElement.classList.add('lanhu-fit-ready');
        });
      }, 40);
    }
    window.addEventListener('load', revealSettingsPageWhenStable);
    document.addEventListener('DOMContentLoaded', function () {
      window.setTimeout(function () {
        if (!revealFired) {
          revealSettingsPageWhenStable();
        }
      }, 900);
    });
  }
  scheduleFit();
})();
