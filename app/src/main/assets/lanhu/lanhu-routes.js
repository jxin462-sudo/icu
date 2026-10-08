/**
 * lanhu-routes.js — 全局路由表(单一来源)
 * 加载方式:<script src="../../../lanhu-routes.js" defer></script>
 *
 * 行为:
 *  - 监听 document click,从点击元素向上遍历祖先,匹配 textContent 与路由表
 *  - 命中后用 window.location.href 跳转
 *  - 如果原生桥已激活(window.IcuBridgeActive = true),让出给原生处理
 *
 * 改菜单/页面跳转:只改本文件的 routes 对象
 */
(function () {
  var routes = {
    "ICU状态": "../lanhu_1icuzhuangtaikaobei/index.html",
    "动物样本": "../lanhu_1icuzhuangtaikaobei/index.html",
    "主机控制": "../lanhu_2zhujikongzhi/index.html",
    "实时监护": "../lanhu_3shishijianhu/index.html",
    "摄像监控": "../lanhu_4jiankong/index.html",
    "治疗记录": "../lanhu_6zhiliaojilu/index.html",
    "使用教程": "../lanhu_7jiaocheng/index.html",
    "设置": "../lanhu_84lianjiezhuangtai/index.html",
    "医院信息": "../lanhu_81shezhi/index.html",
    "仪器状态": "../lanhu_82shezhi/index.html",
    "报告模版-默认": "../lanhu_82shezhi/index.html",
    "连接状态": "../lanhu_84lianjiezhuangtai/index.html",
    "补偿设置": "../lanhu_85buchangshezhi/index.html",
    "其他设置": "../lanhu_86qita/index.html",
    "关于": "../lanhu_87guanyu/index.html",
    "管理员设置": "../lanhu_88guanliyuan/index.html",
    "实况": "../lanhu_4jiankong/index.html",
    "回放": "../lanhu_5jiankonghuifang/index.html",
    "打印": "../lanhu_baogao/index.html",
    "下载": "../lanhu_baogao/index.html",
    "登录": "../lanhu_2zhujikongzhi/index.html"
  };

  document.addEventListener('click', function (event) {
    // 原生桥接管时,让原生处理导航
    if (window.IcuBridgeActive) {
      return;
    }
    var node = event.target;
    while (node && node !== document.body) {
      if (node.textContent) {
        var text = node.textContent.replace(/\s+/g, '').trim();
        if (routes[text]) {
          window.location.href = routes[text];
          return;
        }
      }
      node = node.parentNode;
    }
  });
})();
