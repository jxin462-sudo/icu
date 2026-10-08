/**
 * lanhu-toast-guard.js — Toast/原生 action 频率限制
 * 用法:在 lanhu-bridge.js 之前加载
 *
 * 行为:
 *  - 拦截 data-native-action 的 click 事件
 *  - 同一 action 2 秒内只发 1 次
 *  - 控制台打印被节流的 action(便于排查)
 */
(function () {
  if (window.__lanhuToastGuard) return;
  window.__lanhuToastGuard = true;

  var lastSend = {};
  var THROTTLE_MS = 2000;

  document.addEventListener('click', function (e) {
    var node = e.target;
    while (node && node !== document.body) {
      if (node.dataset && node.dataset.nativeAction) {
        var action = node.dataset.nativeAction;
        var now = Date.now();
        if (lastSend[action] && now - lastSend[action] < THROTTLE_MS) {
          // 节流:不抛 action 给原生
          e.stopImmediatePropagation();
          console.log('[lanhu-toast-guard] throttled:', action,
            '(last sent', Math.round((now - lastSend[action]) / 100) * 100, 'ms ago)');
          return;
        }
        lastSend[action] = now;
        return;
      }
      node = node.parentNode;
    }
  }, true);  // capture 阶段,先于业务监听器
})();
