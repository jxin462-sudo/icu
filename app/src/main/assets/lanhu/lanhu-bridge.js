(function () {
  window.IcuBridgeActive = true;
  if (window.console && window.console.log) {
    window.console.log('[IcuBridge] script loaded, path=' + (window.location.pathname || '?'));
  }
  var handledAt = 0;
  var handledKey = '';
  var CAMERA_PREVIEW_GUARD_KEY = 'icuCameraPreviewGuardUntil';
  var routes = {
    '\u0049\u0043\u0055\u72b6\u6001': '../lanhu_1icuzhuangtaikaobei/index.html',
    '\u52a8\u7269\u6837\u672c': '../lanhu_1icuzhuangtaikaobei/index.html',
    '\u4e3b\u673a\u63a7\u5236': '../lanhu_2zhujikongzhi/index.html',
    '\u5b9e\u65f6\u76d1\u62a4': '../lanhu_3shishijianhu/index.html',
    '\u5b9e\u65f6\u76d1\u63a7': '../lanhu_3shishijianhu/index.html',
    '\u6444\u50cf\u76d1\u63a7': '../lanhu_4jiankong/index.html',
    '\u6cbb\u7597\u8bb0\u5f55': '../lanhu_6zhiliaojilu/index.html',
    '\u4f7f\u7528\u6559\u7a0b': '../lanhu_7jiaocheng/index.html',
    '\u8bbe\u7f6e': '../lanhu_81shezhi/index.html',
    '\u8fde\u63a5\u72b6\u6001': '../lanhu_84lianjiezhuangtai/index.html',
    // \u2605 \u8bbe\u7f6e\u5bb6\u65cf 7 \u4e2a\u5b50\u9875\uff08\u4e4b\u524d\u53ea\u5199\u4e86 2 \u4e2a\uff0c\u5bfc\u81f4\u70b9\u4e0d\u5230\u4eea\u5668\u72b6\u6001/\u8865\u507f\u8bbe\u7f6e/\u5176\u4ed6\u8bbe\u7f6e/\u5173\u4e8e/\u7ba1\u7406\u5458\u8bbe\u7f6e\uff09
    '\u533b\u9662\u4fe1\u606f': '../lanhu_81shezhi/index.html',
    '\u4eea\u5668\u72b6\u6001': '../lanhu_82shezhi/index.html',
    '\u8865\u507f\u8bbe\u7f6e': '../lanhu_85buchangshezhi/index.html',
    '\u5176\u4ed6\u8bbe\u7f6e': '../lanhu_86qita/index.html',
    '\u5173\u4e8e':           '../lanhu_87guanyu/index.html',
    '\u7ba1\u7406\u5458\u8bbe\u7f6e': '../lanhu_88guanliyuan/index.html',
    '\u5b9e\u51b5': '../lanhu_4jiankong/index.html',
    '\u56de\u653e': '../lanhu_5jiankonghuifang/index.html',
    '\u6253\u5370': '../lanhu_baogao/index.html',
    '\u4e0b\u8f7d': '../lanhu_baogao/index.html'
  };
  function setCameraPreviewGuard(durationMs) {
    try {
      sessionStorage.setItem(CAMERA_PREVIEW_GUARD_KEY, String(Date.now() + Math.max(0, durationMs || 0)));
    } catch (error) {
    }
  }

  function cameraPreviewGuardActive(event, path) {
    if (path.indexOf('lanhu_4jiankong') < 0 || !event || event.type !== 'click') {
      return false;
    }
    try {
      var raw = sessionStorage.getItem(CAMERA_PREVIEW_GUARD_KEY) || '';
      var until = raw ? parseInt(raw, 10) : 0;
      if (!until) {
        return false;
      }
      if (Date.now() >= until) {
        sessionStorage.removeItem(CAMERA_PREVIEW_GUARD_KEY);
        return false;
      }
      return true;
    } catch (error) {
      return false;
    }
  }

  function compact(value) {
    return (value || '').replace(/\s+/g, '').trim();
  }

  function directText(node) {
    if (!node || !node.childNodes) {
      return '';
    }
    var text = '';
    for (var i = 0; i < node.childNodes.length; i += 1) {
      if (node.childNodes[i].nodeType === 3) {
        text += node.childNodes[i].nodeValue || '';
      }
    }
    return compact(text);
  }

  function actionText(node) {
    if (!node) {
      return '';
    }
    var text = directText(node);
    if (text) {
      return text;
    }
    if (node.getAttribute) {
      text = compact(node.getAttribute('aria-label') || node.getAttribute('title') || node.getAttribute('alt'));
      if (text) {
        return text;
      }
    }
    return '';
  }

  function pointInRect(point, rect) {
    return !!rect && point.x >= rect.left && point.x <= rect.right && point.y >= rect.top && point.y <= rect.bottom;
  }

  function elementContainsPoint(selector, point) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      if (!nodes[i].getBoundingClientRect) {
        continue;
      }
      var rect = nodes[i].getBoundingClientRect();
      if (rect.width > 1 && rect.height > 1 && pointInRect(point, rect)) {
        return true;
      }
    }
    return false;
  }

  function setAttr(selector, name, value) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].setAttribute(name, value);
      nodes[i].style.cursor = 'pointer';
      nodes[i].style.touchAction = 'manipulation';
    }
  }

  function findUsefulContainer(node) {
    var current = node;
    var steps = 0;
    while (current && current !== document.body && steps < 5) {
      if (!current.getBoundingClientRect) {
        break;
      }
      var rect = current.getBoundingClientRect();
      if (rect.width >= 48 && rect.height >= 24) {
        return current;
      }
      current = current.parentNode;
      steps += 1;
    }
    return node;
  }

  function setTextContainerAction(matchText, action) {
    var nodes = document.querySelectorAll('span,button,div');
    for (var i = 0; i < nodes.length; i += 1) {
      if (nodes[i].tagName !== 'BUTTON' && nodes[i].children && nodes[i].children.length) {
        continue;
      }
      var text = actionText(nodes[i]);
      if (!text || text.length > Math.max(18, matchText.length + 8)) {
        continue;
      }
      if (text === matchText || text.indexOf(matchText) >= 0) {
        var container = findUsefulContainer(nodes[i].parentNode || nodes[i]);
        if (container && container.setAttribute) {
          container.setAttribute('data-native-action', action);
          container.style.cursor = 'pointer';
          container.style.touchAction = 'manipulation';
        }
      }
    }
  }

  function installWideHitAreas() {
    var previewLabels = document.querySelectorAll('.text_52');
    for (var i = 0; i < previewLabels.length; i += 1) {
      if ((previewLabels[i].textContent || '').trim() === '打印') {
        previewLabels[i].textContent = '打印预览';
      }
    }
    var actionRows = document.querySelectorAll('.text-wrapper_18');
    for (var rowIndex = 0; rowIndex < actionRows.length; rowIndex += 1) {
      var actionRow = actionRows[rowIndex].parentNode;
      if (actionRow && actionRow.querySelector('.text-wrapper_19')
          && actionRow.querySelector('.text-wrapper_20')
          && actionRow.querySelector('.text-wrapper_21')) {
        actionRow.classList.add('icu-record-action-row');
        // ★ 问题5:显式“新建样本”入口，不再只能改当前唯一一条记录。
        //   点击后走 native 新建一条当前治疗记录，再自动打开录入框。
        if (!actionRow.querySelector('.icu-record-new-action')) {
          var newAction = document.createElement('div');
          newAction.className = 'icu-record-new-action';
          newAction.setAttribute('data-native-action', 'patient_new');
          newAction.setAttribute('title', '新建动物样本');
          newAction.style.cursor = 'pointer';
          newAction.style.touchAction = 'manipulation';
          var newLabel = document.createElement('span');
          newLabel.textContent = '新建样本';
          newAction.appendChild(newLabel);
          actionRow.appendChild(newAction);
          actionRow.classList.add('icu-with-new');
        }
      }
    }
    setAttr('.text-wrapper_18,.text_50', 'data-native-action', 'patient_edit');
    setAttr('.text-wrapper_19,.text_51', 'data-native-action', 'patient_delete');
    setAttr('.text-wrapper_20,.text_52', 'data-native-action', 'pdf_generate');
    setAttr('.text-wrapper_21,.text_53', 'data-native-action', 'pdf_download');
    setAttr('.text_4,.thumbnail_2', 'data-native-action', 'account_menu');

    var path = window.location.pathname || '';
    if (path.indexOf('lanhu_1icuzhuangtaikaobei') >= 0) {
      setAttr('.section_1 > .box_5', 'data-native-action', 'organization_switch');
    } else if (path.indexOf('lanhu_2zhujikongzhi') >= 0) {
      setAttr('.block_2 > .block_4', 'data-native-action', 'organization_switch');
    } else if (path.indexOf('lanhu_3shishijianhu') >= 0) {
      setAttr('.box_3 > .group_5', 'data-native-action', 'organization_switch');
    } else if (path.indexOf('lanhu_4jiankong') >= 0) {
      setAttr('.group_1 > .box_4', 'data-native-action', 'organization_switch');
    } else if (path.indexOf('lanhu_5jiankonghuifang') >= 0) {
      setAttr('.group_2 > .box_4', 'data-native-action', 'organization_switch');
    } else if (path.indexOf('lanhu_6zhiliaojilu') >= 0) {
      setAttr('.group_1 > .section_2', 'data-native-action', 'organization_switch');
    } else if (path.indexOf('lanhu_7jiaocheng') >= 0) {
      setAttr('.group_2 > .box_4', 'data-native-action', 'organization_switch');
    }

    if (path.indexOf('lanhu_2zhujikongzhi') >= 0) {
      setAttr('.box_8', 'data-native-action', 'control_temp');
      setAttr('.box_9', 'data-native-action', 'control_oxygen');
      setAttr('.box_11', 'data-native-action', 'control_humidity');
      setAttr('.box_15', 'data-native-action', 'control_co2');
      setAttr('.group_18', 'data-native-action', 'control_red');
      setAttr('.group_21', 'data-native-action', 'control_blue');
      setAttr('.group_26', 'data-native-action', 'control_uv');
      setAttr('.box_35', 'data-native-action', 'control_nebulizer');
      setAttr('.box_39', 'data-native-action', 'control_anion');
      setAttr('.text_93', 'data-native-action', 'control_red_time');
      setAttr('.text_94', 'data-native-action', 'control_blue_time');
      setAttr('.text_96', 'data-native-action', 'control_uv_time');
      setAttr('.text_99', 'data-native-action', 'control_nebulizer_time');
      setAttr('.text_102', 'data-native-action', 'control_anion_time');
      setAttr('.block_31', 'data-native-action', 'control_cold_light');
      // ★ 修复 #10:给雾化按钮(.section_40 232×19)加宽命中区
      //   暖光/外循环/内循环已有类似处理,雾化漏了,补上
      setAttr('.section_40', 'data-native-action', 'control_nebulizer');
      setAttr('.section_42', 'data-native-action', 'control_warm_light');
      setAttr('.group_99', 'data-native-action', 'control_outer');
      setAttr('.group_101', 'data-native-action', 'control_inner');
      setAttr('.group_63', 'data-native-action', 'control_time');
      setAttr('.group_45,.section_41', 'data-native-action', 'host_mode_mother');
      setAttr('.section_20,.box_78', 'data-native-action', 'host_mode_postop');
      setAttr('.group_55,.box_79', 'data-native-action', 'host_mode_cardio');
      setAttr('.group_62,.box_80', 'data-native-action', 'host_mode_custom');
      // ★ P2-问题9 修复:三级(绿)最左 / 一级(红)最右。
      //   与 lanhu-first-phase.js renderHostMonitorLevel() 保持一致,
      //   否则首次渲染前点击会走到旧映射(点最左发出一级)。
      setAttr('.box_17 .block_16,.box_17 .section_10', 'data-native-action', 'monitor_level_green');
      setAttr('.box_17 .block_17,.box_17 .section_11', 'data-native-action', 'monitor_level_yellow');
      setAttr('.box_17 .block_18,.box_17 .section_12', 'data-native-action', 'monitor_level_red');
    }

    if (path.indexOf('lanhu_1icuzhuangtaikaobei') >= 0) {
      setAttr('.image-wrapper_1,.thumbnail_10', 'data-native-action', 'control_nebulizer');
      setAttr('.image-wrapper_2,.thumbnail_11', 'data-native-action', 'control_warm_light');
      setAttr('.image-wrapper_3,.thumbnail_12', 'data-native-action', 'control_inner');
      setAttr('.block_4,.text-wrapper_17,.paragraph_1,.text-wrapper_25,.icu-status-advice', 'data-native-action', 'patient_edit');
      // ★ P2-问题9 修复:三级(绿)最左 / 一级(红)最右。
      //   与 lanhu-first-phase.js renderStatusMonitorLevel() 保持一致。
      setAttr('.section_10 .box_25,.section_10 .box_28', 'data-native-action', 'monitor_level_green');
      setAttr('.section_10 .box_26,.section_10 .box_29', 'data-native-action', 'monitor_level_yellow');
      setAttr('.section_10 .box_27,.section_10 .box_30', 'data-native-action', 'monitor_level_red');
    }

    if (path.indexOf('lanhu_3shishijianhu') >= 0) {
      setAttr('.group_16,.image-text_14', 'data-native-action', 'monitor_ble');
      setAttr('.group_18,.image-text_16', 'data-native-action', 'monitor_alarm_sound');
      // "设置" 按钮：紧挨着"报警音"，用于配置自动报警阈值
      setAttr('.group_19,.image-text_17', 'data-native-action', 'monitor_threshold_settings');
    }
  }

  function hideReportTemplateNavigation() {
    var nodes = document.querySelectorAll('span,div');
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (node.children && node.children.length) {
        continue;
      }
      var text = compact(node.textContent || '');
      if (text === '报告模版-默认' || text === '报告模板-默认') {
        node.style.display = 'none';
        node.style.visibility = 'hidden';
      }
    }
  }

  function isCurrentTreatmentSelected() {
    if (window.IcuPatientTemp && typeof window.IcuPatientTemp.isCurrentSelected === 'function') {
      return window.IcuPatientTemp.isCurrentSelected();
    }
    if (window.IcuNative && typeof window.IcuNative.firstPhaseState === 'function') {
      try {
        var state = JSON.parse(window.IcuNative.firstPhaseState() || '{}') || {};
        return !state.patient || state.patient.currentTreatment !== false;
      } catch (error) {
      }
    }
    return true;
  }

  function selectCurrentTreatment() {
    if (window.IcuPatientTemp && typeof window.IcuPatientTemp.selectCurrentTreatment === 'function') {
      return window.IcuPatientTemp.selectCurrentTreatment();
    }
    return false;
  }

  function ensureCurrentTreatmentForRoute() {
    return true;
  }

  function isSmallActionTarget(node, text) {
    if (!text || text.length > 28 || !node.getBoundingClientRect) {
      return false;
    }
    var rect = node.getBoundingClientRect();
    if (!rect || rect.width < 4 || rect.height < 4) {
      return false;
    }
    if (rect.width > 520 || rect.height > 180) {
      return false;
    }
    return rect.width * rect.height <= 65000;
  }

  function hasClassInPath(node, classNames) {
    while (node && node !== document.body) {
      if (node.classList) {
        for (var i = 0; i < classNames.length; i += 1) {
          if (node.classList.contains(classNames[i])) {
            return true;
          }
        }
      }
      node = node.parentNode;
    }
    return false;
  }

  function hostControlActionFromNode(node) {
    var map = [
      { cls: 'box_8', action: 'control_temp' },
      { cls: 'box_9', action: 'control_oxygen' },
      { cls: 'box_11', action: 'control_humidity' },
      { cls: 'box_15', action: 'control_co2' },
      { cls: 'group_18', action: 'control_red_time' },
      { cls: 'group_21', action: 'control_blue_time' },
      { cls: 'group_26', action: 'control_uv_time' },
      { cls: 'box_35', action: 'control_nebulizer_time' },
      { cls: 'box_39', action: 'control_anion_time' },
      { cls: 'group_63', action: 'control_time' },
      { cls: 'block_31', action: 'control_cold_light' },
      { cls: 'section_42', action: 'control_warm_light' },
      { cls: 'group_99', action: 'control_outer' },
      { cls: 'group_101', action: 'control_inner' }
    ];
    for (var i = 0; i < map.length; i += 1) {
      if (hasClassInPath(node, [map[i].cls])) {
        return map[i].action;
      }
    }
    return '';
  }

  function inlineSearchInputFromNode(node) {
    while (node && node !== document.body) {
      if (node.getAttribute && node.getAttribute('data-icu-search-box') === '1' && node.querySelector) {
        return node.querySelector('.icu-patient-search-input');
      }
      node = node.parentNode;
    }
    return null;
  }

  function insidePatientModal(node) {
    while (node && node !== document.body) {
      if (node.classList && node.classList.contains('icu-patient-modal')) {
        return true;
      }
      node = node.parentNode;
    }
    return false;
  }

  function focusInlineSearch(node, event) {
    var input = inlineSearchInputFromNode(node);
    if (!input) {
      return false;
    }
    if (node !== input && typeof input.focus === 'function') {
      input.focus();
      stopEvent(event);
    }
    return true;
  }

  function nativeActionFromText(text, path, node) {
    if (inlineSearchInputFromNode(node)) {
      return '';
    }
    var dataAction = nativeActionFromData(node);
    if (dataAction) return dataAction;
    if (!text) {
      return '';
    }
    if (text === '登录' || text.indexOf('退出登录') >= 0 || text.indexOf('当前账号') >= 0) return 'account_menu';
    if (text === '\u7f16\u8f91' && hasClassInPath(node, ['text_50', 'text-wrapper_18'])) return 'patient_edit';
    if (text === '\u5220\u9664' && hasClassInPath(node, ['text_51', 'text-wrapper_19'])) return 'patient_delete';
    if ((text.indexOf('\u6253\u5370') >= 0 || text.indexOf('\u751f\u6210PDF') >= 0) && hasClassInPath(node, ['text_52', 'text-wrapper_20'])) return 'pdf_generate';
    if (text.indexOf('\u4e0b\u8f7d') >= 0 && hasClassInPath(node, ['text_53', 'text-wrapper_21'])) return 'pdf_download';
    if (path.indexOf('lanhu_2zhujikongzhi') >= 0 && text.indexOf('\u8bbe\u7f6e') >= 0) {
      return hostControlActionFromNode(node);
    }
    if (path.indexOf('lanhu_4jiankong') >= 0) {
      if (text.indexOf('\u56de\u653e') >= 0) return 'camera_playback';
      if (text.indexOf('\u5b9e\u51b5') >= 0) return 'camera_live';
      if (hasClassInPath(node, ['block_7', 'image_8'])) return 'camera_preview_tap';
      if (hasClassInPath(node, ['box_18', 'image-text_14', 'label_3', 'text-group_2'])) return 'camera_snapshot';
      if (hasClassInPath(node, ['box_19', 'image-text_15', 'image_9', 'text-group_3'])) return 'camera_open';
      if (hasClassInPath(node, ['box_20', 'image-text_16', 'label_4', 'text-group_4'])) return 'camera_open';
      if (hasClassInPath(node, ['image-wrapper_7', 'text_70'])) return 'camera_config';
    }
    if (path.indexOf('lanhu_1icuzhuangtaikaobei') >= 0 && hasClassInPath(node, ['image-wrapper_4', 'image_8'])) {
      return 'goto_camera_tab';
    }
    if (text.indexOf('\u622a\u56fe') >= 0) return 'camera_snapshot';
    if (text.indexOf('\u914d\u7f6e\u6444\u50cf\u5934') >= 0 || text.indexOf('\u89c6\u9891\u53c2\u6570') >= 0) return 'camera_config';
    if (text.indexOf('\u5168\u5c4f') >= 0 || text.indexOf('\u5f55\u5236') >= 0) return 'camera_open';
    if (text === '编辑' && hasClassInPath(node, ['text_50', 'text-wrapper_18'])) return 'patient_edit';
    if (text === '删除' && hasClassInPath(node, ['text_51', 'text-wrapper_19'])) return 'patient_delete';
    if ((text.indexOf('打印') >= 0 || text.indexOf('生成PDF') >= 0) && hasClassInPath(node, ['text_52', 'text-wrapper_20'])) return 'pdf_generate';
    if ((text.indexOf('下载') >= 0 || text.indexOf('下载最新PDF') >= 0) && hasClassInPath(node, ['text_53', 'text-wrapper_21'])) return 'pdf_download';
    if (text.indexOf('截图') >= 0) return 'camera_snapshot';
    if (text.indexOf('配置摄像头') >= 0 || text.indexOf('视频参数') >= 0) return 'camera_config';
    if (text.indexOf('全屏') >= 0 || text.indexOf('录制') >= 0) return 'camera_open';
    if (text.indexOf('手动编排') >= 0 || text.indexOf('手动维护') >= 0) return 'treatment_sample';
    if (text.indexOf('新建治疗记录') >= 0) return 'treatment_new';
    if (text.indexOf('结束当前治疗') >= 0) return 'treatment_finish';
    if (text.indexOf('母幼护理模式') >= 0) return 'host_mode_mother';
    if (text.indexOf('术后护理模式') >= 0) return 'host_mode_postop';
    if (text.indexOf('心肺护理模式') >= 0) return 'host_mode_cardio';
    if (text.indexOf('自定义模式') >= 0) return 'host_mode_custom';
    if (text.indexOf('主机蓝牙') >= 0) return 'host_ble';
    if (text.indexOf('监护蓝牙') >= 0 || text === '蓝牙' || text.indexOf('监护宝') >= 0) return 'monitor_ble';
    if (text.indexOf('舱内温度') >= 0) return 'control_temp';
    if (text.indexOf('氧浓度') >= 0 || text.indexOf('氧气浓度') >= 0) return 'control_oxygen';
    if (text.indexOf('湿度') >= 0) return 'control_humidity';
    if (text.indexOf('二氧化碳浓度') >= 0) return 'control_co2';
    if (text.indexOf('红外理疗') >= 0) return 'control_red';
    if (text.indexOf('蓝光理疗') >= 0) return 'control_blue';
    if (text.indexOf('紫外消毒') >= 0) return 'control_uv';
    if (text.indexOf('雾化器') >= 0) return 'control_nebulizer';
    if (text.indexOf('负离子') >= 0) return 'control_anion';
    if (text.indexOf('冷光照明') >= 0) return 'control_cold_light';
    if (text.indexOf('暖光照明') >= 0) return 'control_warm_light';
    if (text.indexOf('外循环') >= 0) return 'control_outer';
    if (text.indexOf('内循环') >= 0) return 'control_inner';
    if (text.indexOf('治疗时长') >= 0) return 'control_time';
    return '';
  }

  function nativeActionFromData(node) {
    while (node && node !== document.body) {
      if (node.getAttribute) {
        var action = node.getAttribute('data-native-action') || node.getAttribute('data-icu-action') || '';
        if (action) {
          return action;
        }
      }
      node = node.parentNode;
    }
    return '';
  }

  function nativeActionFromPoint(event, path) {
    var point = eventPoint(event);
    if (point.x < 0 || point.y < 0) {
      return '';
    }
    if (path.indexOf('lanhu_4jiankong') >= 0 && elementContainsPoint('.block_7,.image_8', point)) {
      return 'camera_preview_tap';
    }
    if (path.indexOf('lanhu_1icuzhuangtaikaobei') >= 0) {
      var statusPriority = [
        { selector: '.section_10 .box_25,.section_10 .box_28', action: 'monitor_level_red' },
        { selector: '.section_10 .box_26,.section_10 .box_29', action: 'monitor_level_yellow' },
        { selector: '.section_10 .box_27,.section_10 .box_30', action: 'monitor_level_green' },
        { selector: '.image-wrapper_1,.thumbnail_10,.icu-switch-fake[data-for=".image-wrapper_1"]', action: 'control_nebulizer' },
        { selector: '.image-wrapper_2,.thumbnail_11,.icu-switch-fake[data-for=".image-wrapper_2"]', action: 'control_warm_light' },
        { selector: '.image-wrapper_3,.thumbnail_12,.icu-switch-fake[data-for=".image-wrapper_3"]', action: 'control_inner' },
        { selector: '.text-wrapper_20,.text_52', action: 'pdf_generate' },
        { selector: '.text-wrapper_21,.text_53', action: 'pdf_download' },
        { selector: '.block_4,.text-wrapper_17,.paragraph_1,.text-wrapper_25,.icu-status-advice', action: 'patient_edit' },
        { selector: '.image-wrapper_4,.image_8', action: 'goto_camera_tab' }
      ];
      for (var statusIndex = 0; statusIndex < statusPriority.length; statusIndex += 1) {
        if (elementContainsPoint(statusPriority[statusIndex].selector, point)) {
          return statusPriority[statusIndex].action;
        }
      }
    }
    if (path.indexOf('lanhu_2zhujikongzhi') >= 0) {
      var priority = [
        { selector: '.box_17 .block_16,.box_17 .section_10', action: 'monitor_level_red' },
        { selector: '.box_17 .block_17,.box_17 .section_11', action: 'monitor_level_yellow' },
        { selector: '.box_17 .block_18,.box_17 .section_12', action: 'monitor_level_green' },
        { selector: '.text_93', action: 'control_red_time' },
        { selector: '.text_94', action: 'control_blue_time' },
        { selector: '.text_96', action: 'control_uv_time' },
        { selector: '.text_99', action: 'control_nebulizer_time' },
        { selector: '.text_102', action: 'control_anion_time' },
        { selector: '.image_9,.icu-switch-fake[data-for=".image_9"]', action: 'control_red' },
        { selector: '.image_10,.icu-switch-fake[data-for=".image_10"]', action: 'control_blue' },
        { selector: '.image_11,.icu-switch-fake[data-for=".image_11"]', action: 'control_uv' },
        { selector: '.image_12,.icu-switch-fake[data-for=".image_12"]', action: 'control_nebulizer' },
        { selector: '.image_13,.icu-switch-fake[data-for=".image_13"]', action: 'control_anion' },
        { selector: '.image_14,.icu-switch-fake[data-for=".image_14"]', action: 'control_cold_light' },
        { selector: '.image_15,.icu-switch-fake[data-for=".image_15"]', action: 'host_mode_mother' },
        { selector: '.image_16,.icu-switch-fake[data-for=".image_16"]', action: 'control_warm_light' },
        { selector: '.image_17,.icu-switch-fake[data-for=".image_17"]', action: 'host_mode_postop' },
        { selector: '.image_18,.icu-switch-fake[data-for=".image_18"]', action: 'control_outer' },
        { selector: '.image_19,.icu-switch-fake[data-for=".image_19"]', action: 'host_mode_cardio' },
        { selector: '.image_20,.icu-switch-fake[data-for=".image_20"]', action: 'control_inner' },
        { selector: '.image_21,.icu-switch-fake[data-for=".image_21"]', action: 'host_mode_custom' },
        { selector: '.box_8', action: 'control_temp' },
        { selector: '.box_9', action: 'control_oxygen' },
        { selector: '.box_11', action: 'control_humidity' },
        { selector: '.box_15', action: 'control_co2' },
        { selector: '.group_18', action: 'control_red' },
        { selector: '.group_21', action: 'control_blue' },
        { selector: '.group_26', action: 'control_uv' },
        { selector: '.box_35', action: 'control_nebulizer' },
        { selector: '.box_39', action: 'control_anion' },
        { selector: '.block_31', action: 'control_cold_light' },
        { selector: '.section_42', action: 'control_warm_light' },
        { selector: '.group_99', action: 'control_outer' },
        { selector: '.group_101', action: 'control_inner' },
        { selector: '.group_63', action: 'control_time' },
        { selector: '.group_45,.section_41', action: 'host_mode_mother' },
        { selector: '.section_20,.box_78', action: 'host_mode_postop' },
        { selector: '.group_55,.box_79', action: 'host_mode_cardio' },
        { selector: '.group_62,.box_80', action: 'host_mode_custom' }
      ];
      for (var i = 0; i < priority.length; i += 1) {
        if (elementContainsPoint(priority[i].selector, point)) {
          return priority[i].action;
        }
      }
    }
    if (path.indexOf('lanhu_3shishijianhu') >= 0) {
      if (elementContainsPoint('.group_16,.image-text_14', point)) {
        return 'monitor_ble';
      }
      if (elementContainsPoint('.group_18,.image-text_16', point)) {
        return 'monitor_alarm_sound';
      }
      if (elementContainsPoint('.group_19,.image-text_17', point)) {
        return 'monitor_threshold_settings';
      }
    }
    var common = [
      { selector: '.text-wrapper_18,.text_50', action: 'patient_edit' },
      { selector: '.text-wrapper_19,.text_51', action: 'patient_delete' },
      { selector: '.text-wrapper_20,.text_52', action: 'pdf_generate' },
      { selector: '.text-wrapper_21,.text_53', action: 'pdf_download' }
    ];
    for (var j = 0; j < common.length; j += 1) {
      if (elementContainsPoint(common[j].selector, point)) {
        return common[j].action;
      }
    }
    return '';
  }

  function markHandled(key, action) {
    // ★ 修复 #10:开关类 action (control_*/host_mode_*)绕过防抖,
    //   只保留 stopEvent。BLE writeQueue 自身串行化,多次点击会被设备
    //   顺序处理,最终状态以最后一条命令为准。
    if (action && (action.indexOf("control_") === 0 || action.indexOf("host_mode_") === 0)) {
      return true;
    }
    var now = Date.now();
    if (handledKey === key && now - handledAt < 450) {
      return false;
    }
    handledKey = key;
    handledAt = now;
    return true;
  }

  function syncTempPatientForNative(action) {
    if (!window.IcuNative || typeof window.IcuNative.syncTempPatient !== 'function') {
      return;
    }
    if (!window.IcuPatientTemp || typeof window.IcuPatientTemp.currentPatient !== 'function') {
      return;
    }
    // 患者只在编辑弹窗保存时显式同步。切舱、选择、新建、删除和普通页面动作
    // 不再隐式回写当前患者，避免旧舱数据在异步 action 中覆盖新舱。
    return;
  }

  function eventPoint(event) {
    var source = event;
    if (event && event.changedTouches && event.changedTouches.length) {
      source = event.changedTouches[0];
    } else if (event && event.touches && event.touches.length) {
      source = event.touches[0];
    }
    return {
      x: source && typeof source.clientX === 'number' ? source.clientX : -1,
      y: source && typeof source.clientY === 'number' ? source.clientY : -1
    };
  }

  function routeFromTabNode(node) {
    var map = [
      { cls: 'text_54', text: 'ICU状态', route: '../lanhu_1icuzhuangtaikaobei/index.html' },
      { cls: 'text_55', text: '主机控制', route: '../lanhu_2zhujikongzhi/index.html' },
      { cls: 'text_56', text: '实时监护', route: '../lanhu_3shishijianhu/index.html' },
      { cls: 'text_57', text: '摄像监控', route: '../lanhu_4jiankong/index.html' },
      { cls: 'text_58', text: '治疗记录', route: '../lanhu_6zhiliaojilu/index.html' },
      { cls: 'text_59', text: '使用教程', route: '../lanhu_7jiaocheng/index.html' }
    ];
    while (node && node !== document.body) {
      if (node.classList) {
        for (var i = 0; i < map.length; i += 1) {
          if (node.classList.contains(map[i].cls)) {
            return map[i];
          }
        }
      }
      node = node.parentNode;
    }
    return null;
  }

  function navCandidates() {
    var nodes = document.querySelectorAll('.text_2,.text_3');
    var items = [];
    for (var i = 0; i < nodes.length; i += 1) {
      var text = actionText(nodes[i]);
      var route = routes[text] || '';
      if (!route || !nodes[i].getBoundingClientRect) {
        continue;
      }
      var rect = nodes[i].getBoundingClientRect();
      if (!rect || rect.width < 1 || rect.height < 1) {
        continue;
      }
      items.push({ text: text, route: route, rect: rect });
    }
    return items;
  }

  function collectTabBarItems() {
    var labels = [
      { selector: '.text_54', text: 'ICU状态', route: '../lanhu_1icuzhuangtaikaobei/index.html' },
      { selector: '.text_55', text: '主机控制', route: '../lanhu_2zhujikongzhi/index.html' },
      { selector: '.text_56', text: '实时监护', route: '../lanhu_3shishijianhu/index.html' },
      { selector: '.text_57', text: '摄像监控', route: '../lanhu_4jiankong/index.html' },
      { selector: '.text_58', text: '治疗记录', route: '../lanhu_6zhiliaojilu/index.html' },
      { selector: '.text_59', text: '使用教程', route: '../lanhu_7jiaocheng/index.html' }
    ];
    var found = [];
    for (var i = 0; i < labels.length; i += 1) {
      var node = document.querySelector(labels[i].selector);
      if (!node || !node.getBoundingClientRect) {
        continue;
      }
      var rect = node.getBoundingClientRect();
      if (rect.width > 1 && rect.height > 1) {
        found.push({ text: labels[i].text, route: labels[i].route, rect: rect });
      }
    }
    return found;
  }

  function pointInsideUnion(point, items, xPad, yPad) {
    if (!items || !items.length || point.x < 0 || point.y < 0) {
      return false;
    }
    var left = Infinity;
    var right = -Infinity;
    var top = Infinity;
    var bottom = -Infinity;
    for (var i = 0; i < items.length; i += 1) {
      left = Math.min(left, items[i].rect.left);
      right = Math.max(right, items[i].rect.right);
      top = Math.min(top, items[i].rect.top);
      bottom = Math.max(bottom, items[i].rect.bottom);
    }
    return point.x >= left - xPad && point.x <= right + xPad &&
      point.y >= top - yPad && point.y <= bottom + yPad;
  }

  function tabBarRouteFromPoint(event) {
    var point = eventPoint(event);
    if (point.x < 0 || point.y < 0) {
      return null;
    }
    var labels = [
      { selector: '.text_54', text: 'ICU状态', route: '../lanhu_1icuzhuangtaikaobei/index.html' },
      { selector: '.text_55', text: '主机控制', route: '../lanhu_2zhujikongzhi/index.html' },
      { selector: '.text_56', text: '实时监护', route: '../lanhu_3shishijianhu/index.html' },
      { selector: '.text_57', text: '摄像监控', route: '../lanhu_4jiankong/index.html' },
      { selector: '.text_58', text: '治疗记录', route: '../lanhu_6zhiliaojilu/index.html' },
      { selector: '.text_59', text: '使用教程', route: '../lanhu_7jiaocheng/index.html' }
    ];
    var found = [];
    for (var i = 0; i < labels.length; i += 1) {
      var node = document.querySelector(labels[i].selector);
      if (!node || !node.getBoundingClientRect) {
        continue;
      }
      var rect = node.getBoundingClientRect();
      if (rect.width > 1 && rect.height > 1) {
        found.push({ text: labels[i].text, route: labels[i].route, rect: rect });
      }
    }
    if (found.length < 2) {
      return null;
    }
    var top = Infinity;
    var bottom = -Infinity;
    for (var j = 0; j < found.length; j += 1) {
      top = Math.min(top, found[j].rect.top);
      bottom = Math.max(bottom, found[j].rect.bottom);
    }
    var yPad = Math.max(12, (bottom - top) * 0.4);
    if (point.y < top - yPad || point.y > bottom + yPad) {
      return null;
    }
    var best = null;
    var bestDistance = Infinity;
    for (var k = 0; k < found.length; k += 1) {
      var cx = (found[k].rect.left + found[k].rect.right) / 2;
      var distance = Math.abs(point.x - cx);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = found[k];
      }
    }
    if (!best) {
      return null;
    }
    var maxGap = Math.max(72, best.rect.width * 1.1);
    return bestDistance <= maxGap ? best : null;
  }

  function navRouteFromPoint(event) {
    var point = eventPoint(event);
    if (point.x < 0 || point.y < 0) {
      return null;
    }
    var items = navCandidates();
    var best = null;
    var bestScore = Infinity;
    for (var i = 0; i < items.length; i += 1) {
      var rect = items[i].rect;
      var expandX = Math.max(120, rect.width * 1.8);
      var expandY = Math.max(45, rect.height * 2.4);
      if (point.x < rect.left - expandX || point.x > rect.right + expandX ||
        point.y < rect.top - expandY || point.y > rect.bottom + expandY) {
        continue;
      }
      var cx = (rect.left + rect.right) / 2;
      var cy = (rect.top + rect.bottom) / 2;
      var score = Math.abs(point.x - cx) + Math.abs(point.y - cy) * 2;
      if (score < bestScore) {
        bestScore = score;
        best = items[i];
      }
    }
    return best;
  }

  function navCandidates() {
    var nodes = document.querySelectorAll('.text_2,.text_3');
    var items = [];
    for (var i = 0; i < nodes.length; i += 1) {
      var text = actionText(nodes[i]);
      var route = routes[text] || '';
      if (!route || !nodes[i].getBoundingClientRect) {
        continue;
      }
      var rect = nodes[i].getBoundingClientRect();
      if (!rect || rect.width < 1 || rect.height < 1) {
        continue;
      }
      items.push({ text: text, route: route, rect: rect });
    }
    return items;
  }

  function collectTabBarItems() {
    var labels = [
      { selector: '.text_54', text: 'ICU状态', route: '../lanhu_1icuzhuangtaikaobei/index.html' },
      { selector: '.text_55', text: '主机控制', route: '../lanhu_2zhujikongzhi/index.html' },
      { selector: '.text_56', text: '实时监护', route: '../lanhu_3shishijianhu/index.html' },
      { selector: '.text_57', text: '摄像监控', route: '../lanhu_4jiankong/index.html' },
      { selector: '.text_58', text: '治疗记录', route: '../lanhu_6zhiliaojilu/index.html' },
      { selector: '.text_59', text: '使用教程', route: '../lanhu_7jiaocheng/index.html' }
    ];
    var found = [];
    for (var i = 0; i < labels.length; i += 1) {
      var node = document.querySelector(labels[i].selector);
      if (!node || !node.getBoundingClientRect) {
        continue;
      }
      var rect = node.getBoundingClientRect();
      if (rect.width > 1 && rect.height > 1) {
        found.push({ text: labels[i].text, route: labels[i].route, rect: rect });
      }
    }
    return found;
  }

  function pointInsideUnion(point, items, xPad, yPad) {
    if (!items || !items.length || point.x < 0 || point.y < 0) {
      return false;
    }
    var left = Infinity;
    var right = -Infinity;
    var top = Infinity;
    var bottom = -Infinity;
    for (var i = 0; i < items.length; i += 1) {
      left = Math.min(left, items[i].rect.left);
      right = Math.max(right, items[i].rect.right);
      top = Math.min(top, items[i].rect.top);
      bottom = Math.max(bottom, items[i].rect.bottom);
    }
    return point.x >= left - xPad && point.x <= right + xPad &&
      point.y >= top - yPad && point.y <= bottom + yPad;
  }

  function tabBarRouteFromPoint(event) {
    var point = eventPoint(event);
    if (point.x < 0 || point.y < 0) {
      return null;
    }
    var found = collectTabBarItems();
    if (found.length < 2) {
      return null;
    }
    if (!pointInsideUnion(point, found, 24, 16)) {
      return null;
    }
    var best = null;
    var bestDistance = Infinity;
    for (var i = 0; i < found.length; i += 1) {
      var cx = (found[i].rect.left + found[i].rect.right) / 2;
      var distance = Math.abs(point.x - cx);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = found[i];
      }
    }
    if (!best) {
      return null;
    }
    var maxGap = Math.max(48, best.rect.width * 0.95);
    return bestDistance <= maxGap ? best : null;
  }

  function navRouteFromPoint(event) {
    var point = eventPoint(event);
    if (point.x < 0 || point.y < 0) {
      return null;
    }
    var items = navCandidates();
    if (!pointInsideUnion(point, items, 36, 18)) {
      return null;
    }
    var best = null;
    var bestScore = Infinity;
    for (var i = 0; i < items.length; i += 1) {
      var rect = items[i].rect;
      var expandX = Math.max(28, rect.width * 0.9);
      var expandY = Math.max(12, rect.height * 0.9);
      if (point.x < rect.left - expandX || point.x > rect.right + expandX ||
        point.y < rect.top - expandY || point.y > rect.bottom + expandY) {
        continue;
      }
      var cx = (rect.left + rect.right) / 2;
      var cy = (rect.top + rect.bottom) / 2;
      var score = Math.abs(point.x - cx) + Math.abs(point.y - cy) * 2;
      if (score < bestScore) {
        bestScore = score;
        best = items[i];
      }
    }
    return best;
  }

  function stopEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    if (event.stopImmediatePropagation) {
      event.stopImmediatePropagation();
    }
  }

  function navigateRoute(text, route, event) {
    if (!route) {
      return false;
    }
    if (!markHandled('r:' + route)) {
      return true;
    }
    stopEvent(event);
    if (window.location.href.indexOf(route) < 0) {
      window.location.href = route;
    }
    return true;
  }

  function optimisticSwitchForAction(action) {
    var map = {
      control_red: '.image_9',
      control_blue: '.image_10',
      control_uv: '.image_11',
      control_nebulizer: ['.image_12', '.image-wrapper_1'],
      control_anion: '.image_13',
      control_cold_light: '.image_14',
      control_warm_light: ['.image_16', '.image-wrapper_2'],
      control_outer: '.image_18',
      control_inner: ['.image_20', '.image-wrapper_3']
    };
    var selectors = map[action] || '';
    if (!selectors) {
      return false;
    }
    if (typeof selectors === 'string') {
      selectors = [selectors];
    }
    var changed = false;
    for (var i = 0; i < selectors.length; i += 1) {
      var img = document.querySelector(selectors[i]);
      var parent = img && img.parentNode;
      var fake = document.querySelector('.icu-switch-fake[data-for="' + selectors[i] + '"]')
        || (parent && parent.querySelector('.icu-switch-fake[data-for="' + selectors[i] + '"]'));
      if (!fake) {
        continue;
      }
      var nextOn = !fake.classList.contains('on');
      fake.classList.toggle('on', nextOn);
      fake.classList.toggle('off', !nextOn);
      fake.setAttribute('title', nextOn ? '点击关闭' : '点击开启');
      setPendingTimedCountdown(action, nextOn);
      changed = true;
    }
    return changed;
  }

  function setPendingTimedCountdown(action, enabled) {
    var keyMap = {
      control_red: 'redTherapy',
      control_blue: 'blueTherapy',
      control_uv: 'uv',
      control_nebulizer: 'nebulizer',
      control_anion: 'anion'
    };
    // ★ 乐观倒计时按直接开启的本次运行值：紫外/雾化/负离子 120 分钟；
    //   红外/蓝光为不限时（协议 65536），不起本地倒计时。
    var directStartMinutesMap = {
      control_red: 0,
      control_blue: 0,
      control_uv: 120,
      control_nebulizer: 120,
      control_anion: 120
    };
    var key = keyMap[action];
    if (!key) {
      return;
    }
    var minutes = directStartMinutesMap[action] || 0;
    // ★ 左右舱隔离：乐观倒计时按舱命名（"left|uv" / "right|uv"）。
    //   当前舱由 lanhu-live-state-v3.js 跟随 native state 维护，切舱点击时已乐观更新。
    var zone = String(window.__icuCurrentZone || '').toLowerCase() === 'right' ? 'right' : 'left';
    var zonedKey = zone + '|' + key;
    var pending = window.__icuPendingTimedCountdowns || {};
    pending[zonedKey] = (enabled && minutes > 0) ? Date.now() + minutes * 60 * 1000 : 0;
    window.__icuPendingTimedCountdowns = pending;
  }

  function optimisticModeForAction(action) {
    var map = {
      host_mode_mother: '.image_15',
      host_mode_postop: '.image_17',
      host_mode_cardio: '.image_19',
      host_mode_custom: '.image_21'
    };
    var selector = map[action] || '';
    if (!selector) {
      return false;
    }
    // 护理模式必须等待原生端完成“保存并开启”后的真实状态推送。
    // 不能在弹出参数框或蓝牙断连时先显示已开启。
    return false;
  }

  function openSharedPrintPreview() {
    var modal = document.getElementById('icu-shared-print-preview');
    var cameraPage = (window.location.pathname || '').indexOf('lanhu_4jiankong') >= 0;
    if (!modal) {
      modal = document.createElement('div');
      modal.id = 'icu-shared-print-preview';
      modal.innerHTML = '<div><iframe title="ICU动物舱治疗记录单预览"></iframe></div><button type="button" aria-label="关闭预览">关闭</button><button type="button">打印</button>';
      modal.style.cssText = 'position:fixed;z-index:10000;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(15,35,60,.42)';
      var frame = modal.querySelector('iframe');
      var frameShell = modal.querySelector('div');
      var close = modal.querySelectorAll('button')[0];
      var print = modal.querySelectorAll('button')[1];
      frameShell.style.cssText = 'position:relative;width:620px;height:880px;overflow:hidden;background:#fff;border-radius:8px';
      frame.style.cssText = 'position:absolute;top:0;left:0;width:1512px;height:2138px;border:0;background:#fff;transform-origin:top left';
      close.style.cssText = 'position:fixed;right:calc(50% - 300px);bottom:5%;z-index:1';
      print.style.cssText = 'position:fixed;right:calc(50% - 370px);bottom:5%;z-index:1';
      var closePreview = function () {
        modal.style.display = 'none';
        if (modal.getAttribute('data-camera-preview-paused') === '1' && window.IcuNative) {
          window.IcuNative.action('camera_preview_resume', '', window.location.pathname || '');
        }
        modal.removeAttribute('data-camera-preview-paused');
      };
      close.onclick = closePreview;
      modal.onclick = function (event) {
        if (event.target === modal) {
          closePreview();
        }
      };
      print.onclick = function () { frame.contentWindow.print(); };
      document.body.appendChild(modal);
    }
    if (cameraPage && window.IcuNative && typeof window.IcuNative.action === 'function') {
      modal.setAttribute('data-camera-preview-paused', '1');
      window.IcuNative.action('camera_preview_pause', '', window.location.pathname || '');
    }
    modal.style.display = 'flex';
    var sharedFrame = modal.querySelector('iframe');
    var sharedShell = sharedFrame.parentNode;
    var maxWidth = Math.max(320, window.innerWidth - 64);
    var maxHeight = Math.max(320, window.innerHeight * 0.92);
    var scale = Math.min(maxWidth / 1512, maxHeight / 2138);
    sharedShell.style.width = Math.round(1512 * scale) + 'px';
    sharedShell.style.height = Math.round(2138 * scale) + 'px';
    sharedFrame.style.transformOrigin = 'top left';
    sharedFrame.style.transform = 'scale(' + scale + ')';
    sharedFrame.src = '../lanhu_baogao/index.html?preview=' + Date.now();
  }

  function runNativeAction(action, text, path, event) {
    if (!action) {
      return false;
    }
    if (action === 'camera_preview_tap' && cameraPreviewGuardActive(event, path)) {
      stopEvent(event);
      return true;
    }
    if (action === 'pdf_generate') {
      stopEvent(event);
      var previewEvent = new Event('icu-print-preview-open', { cancelable: true });
      window.dispatchEvent(previewEvent);
      if (!previewEvent.defaultPrevented) {
        openSharedPrintPreview();
      }
      return true;
    }
    if (!markHandled('a:' + action + ':' + text, action)) {
      stopEvent(event);
      return true;
    }
    stopEvent(event);
    // ★ 左右舱隔离：切舱动作立刻更新当前舱，不等 native 回包。
    //   否则"切到右舱后马上点开关"会把乐观倒计时写进左舱。
    if (action === 'tablet_zone_left' || action === 'tablet_zone_right') {
      window.__icuCurrentZone = action === 'tablet_zone_right' ? 'right' : 'left';
    }
    if (action === 'goto_camera_tab') {
      // 跳到摄像监控页后，吞掉上一页遗留到新页的那次 synthetic click，
      // 避免刚完成跳转就误触发旧的原生全屏摄像头预览。
      setCameraPreviewGuard(1200);
    }
    if (!optimisticModeForAction(action)) {
      optimisticSwitchForAction(action);
    }
    if (window.IcuPatientTemp && typeof window.IcuPatientTemp.handleAction === 'function' && window.IcuPatientTemp.handleAction(action)) {
      return true;
    }
    if (action === 'camera_playback') {
      if (window.IcuCameraRuntime && typeof window.IcuCameraRuntime.showPlayback === 'function') {
        window.IcuCameraRuntime.showPlayback();
      }
      return true;
    }
    if (action === 'camera_live') {
      if (window.IcuCameraRuntime && typeof window.IcuCameraRuntime.showLive === 'function') {
        window.IcuCameraRuntime.showLive();
      }
      return true;
    }
    syncTempPatientForNative(action);
    if (window.IcuNative && typeof window.IcuNative.action === 'function') {
      window.IcuNative.action(action, text, path);
    }
    return true;
  }

  function priorityPdfActionFromNode(node) {
    while (node && node !== document.body) {
      var text = actionText(node);
      if (node.classList && (node.classList.contains('text_52') || node.classList.contains('text-wrapper_20'))) {
        return { action: 'pdf_generate', text: text || '打印' };
      }
      if (node.classList && (node.classList.contains('text_53') || node.classList.contains('text-wrapper_21'))) {
        return { action: 'pdf_download', text: text || '下载' };
      }
      node = node.parentNode;
    }
    return null;
  }

  function cameraActionFromPoint(event, path) {
    if (path.indexOf('lanhu_4jiankong') < 0) {
      return '';
    }
    var point = eventPoint(event);
    if (point.x < 0 || point.y < 0) {
      return '';
    }
    return elementContainsPoint('.block_7,.image_8', point) ? 'camera_preview_tap' : '';
  }

  function fastHandle(event) {
    var node = event.target;
    var path = window.location.pathname || '';
    var printPreview = document.getElementById('icu-print-preview');
    if (printPreview && !printPreview.hidden) {
      var previewControl = node && node.closest ? node.closest('[data-print-preview-close],[data-print-preview-print],.icu-print-preview__close') : null;
      if (!previewControl) {
        stopEvent(event);
      }
      return;
    }
    var sharedPrintPreview = document.getElementById('icu-shared-print-preview');
    if (sharedPrintPreview && sharedPrintPreview.style.display !== 'none') {
      var sharedPreviewControl = node && node.closest ? node.closest('#icu-shared-print-preview button') : null;
      if (node === sharedPrintPreview) {
        return;
      }
      if (!sharedPreviewControl) {
        stopEvent(event);
      }
      return;
    }
    var thermalFullscreen = document.getElementById('icu-thermal-fullscreen');
    if (thermalFullscreen) {
      var thermalActionNode = node && node.closest ? node.closest(
        '.icu-thermal-close,.icu-thermal-connect,.icu-thermal-picker-close,.icu-thermal-device') : null;
      if (!thermalActionNode) {
        stopEvent(event);
      }
      return;
    }
    if (insidePatientModal(node)) {
      return;
    }
    if (focusInlineSearch(node, event)) {
      return;
    }
    var cameraPointAction = cameraActionFromPoint(event, path);
    if (cameraPointAction && runNativeAction(cameraPointAction, cameraPointAction, path, event)) {
      return;
    }
    var directTabRoute = routeFromTabNode(node);
    if (directTabRoute && navigateRoute(directTabRoute.text, directTabRoute.route, event)) {
      return;
    }
    var priorityAction = priorityPdfActionFromNode(node);
    if (priorityAction && runNativeAction(priorityAction.action, priorityAction.text, path, event)) {
      return;
    }
    var dataAction = nativeActionFromData(node);
    if (dataAction && runNativeAction(dataAction, actionText(node) || dataAction, path, event)) {
      return;
    }
    var pointAction = nativeActionFromPoint(event, path);
    if (pointAction && runNativeAction(pointAction, pointAction, path, event)) {
      return;
    }
    var tabRoute = tabBarRouteFromPoint(event);
    if (tabRoute && navigateRoute(tabRoute.text, tabRoute.route, event)) {
      return;
    }
    var navRoute = navRouteFromPoint(event);
    if (navRoute && navigateRoute(navRoute.text, navRoute.route, event)) {
      return;
    }
    while (node && node !== document.body) {
      var text = actionText(node);
      var action = isSmallActionTarget(node, text) ? nativeActionFromText(text, path, node) : '';
      if (action) {
        runNativeAction(action, text, path, event);
        return;
      }
      var route = routes[text] || '';
      if (route) {
        if (!markHandled('r:' + route)) {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        if (event.stopImmediatePropagation) {
          event.stopImmediatePropagation();
        }
        if (window.location.href.indexOf(route) < 0) {
          window.location.href = route;
        }
        return;
      }
      node = node.parentNode;
    }
  }

  function tabletControlState() {
    if (!window.IcuNative || typeof window.IcuNative.firstPhaseState !== 'function') {
      return {};
    }
    try {
      var state = JSON.parse(window.IcuNative.firstPhaseState() || '{}') || {};
      return (state.ble && state.ble.host) || {};
    } catch (error) {
      return {};
    }
  }

  function formatSystemTime(date) {
    function two(value) { return value < 10 ? '0' + value : String(value); }
    // ★ 修复问题6: 时间显示添加年月日
    // ★ 日期改中文形式:2026年8月27日 14:04(不用 2026-08-27)。月/日不补零。
    var year = date.getFullYear();
    var month = date.getMonth() + 1;
    var day = date.getDate();
    var hours = two(date.getHours());
    var minutes = two(date.getMinutes());
    return year + '年' + month + '月' + day + '日 ' + hours + ':' + minutes;
  }

  function renderTabletControlStatus() {
    if (!document.body) {
      return;
    }
    var pageRoot = document.querySelector('.page') || document.body;
    var isSettingsPage = (window.location.pathname || '').indexOf('lanhu_8') >= 0;
    // ★ 找到顶部导航栏
    var accountEl = pageRoot.querySelector('.text_4[data-account-live]');
    var topNav = (accountEl && accountEl.parentNode) || pageRoot.querySelector('.box_1.flex-row') || pageRoot.querySelector('.group_1.flex-row') || pageRoot.querySelector('.box_1') || pageRoot;
    // ★ 找 bar 元素
    var bar = document.getElementById('icu-tablet-control-status');
    if (!bar) {
      bar = document.createElement('div');
      bar.id = 'icu-tablet-control-status';
      bar.className = 'icu-tablet-control-status';
      bar.innerHTML = '<span class="icu-tablet-label">本平板控制</span>'
        + '<button type="button" data-native-action="tablet_zone_left">左舱</button>'
        + '<button type="button" data-native-action="tablet_zone_right">右舱</button>'
        + '<time class="icu-system-time"></time>';
      pageRoot.appendChild(bar);
    }
    // ★ 把 bar 移到 .box_1 内部
    if (bar.parentNode !== topNav) {
      var label2 = topNav.querySelector ? topNav.querySelector('.label_2') : null;
      if (label2 && label2.parentNode === topNav) {
        topNav.insertBefore(bar, label2);
      } else {
        topNav.appendChild(bar);
      }
    } else {
      var refLabel2 = topNav.querySelector ? topNav.querySelector('.label_2') : null;
      if (refLabel2 && bar.previousSibling !== refLabel2) {
        topNav.insertBefore(bar, refLabel2);
      }
    }
    // ★ 用 try-catch 保护后续操作
    try {
      // 注意:不再覆盖 display,改由 lanhu-fit.css 的 .icu-tablet-control-status 控制
      // (原来写 display:flex!important,会让 CSS 的 display:inline-flex 永远不生效)
      bar.style.cssText = 'visibility:visible!important;opacity:1!important;'
        + 'align-items:center!important;margin-left:auto!important;padding:0 12px!important;gap:8px!important;'
        + 'background:transparent!important;border:none!important;border-radius:0!important;'
        + 'box-shadow:none!important;color:#ffffff!important;height:46px!important;line-height:46px!important;';
      var controlLabel = bar.querySelector('.icu-tablet-label');
      if (controlLabel) {
        controlLabel.textContent = '控制舱';
        controlLabel.style.display = isSettingsPage ? 'none' : '';
      }
      var host = tabletControlState();
      var zone = host.zone === 'left' ? 'left' : 'right';
      var buttons = bar.querySelectorAll('button[data-native-action]');
      for (var i = 0; i < buttons.length; i += 1) {
        buttons[i].classList.toggle('active', buttons[i].getAttribute('data-native-action') === 'tablet_zone_' + zone);
        buttons[i].style.display = isSettingsPage ? 'none' : '';
      }
      bar.classList.toggle('icu-time-only', isSettingsPage);
    } catch (e) {
      // 静默失败
    }
    // ★ 系统时间（独立 try-catch，确保一定能更新）
    try {
      var clock = bar.querySelector('.icu-system-time');
      if (clock) {
        clock.textContent = formatSystemTime(new Date());
      }
    } catch (e) {
      // 静默失败
    }
  }

  // ★ 兼容旧版本：保留一个独立的 renderTabletClock 函数供外部调用
  function renderTabletClock() {
    try {
      var bar = document.getElementById('icu-tablet-control-status');
      if (bar) {
        var clock = bar.querySelector('.icu-system-time');
        if (clock) {
          clock.textContent = formatSystemTime(new Date());
        }
      }
    } catch (e) {}
  }

  document.addEventListener('DOMContentLoaded', hideReportTemplateNavigation);
  document.addEventListener('DOMContentLoaded', installWideHitAreas);
  document.addEventListener('DOMContentLoaded', renderTabletControlStatus);
  window.addEventListener('icu-native-state', renderTabletControlStatus);
  window.setTimeout(hideReportTemplateNavigation, 120);
  window.setTimeout(installWideHitAreas, 120);
  window.setTimeout(renderTabletControlStatus, 120);
  // ★ 修复 #12:移除 1s 一次的 renderTabletControlStatus 定时器,
//   renderTabletControlStatus 已在 lanhu-first-phase.js 的 render() 中处理,
//   这里只保留启动时的一次性调用。
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', function () { renderTabletControlStatus(); });
} else {
  renderTabletControlStatus();
}
  window.setTimeout(installWideHitAreas, 600);
  document.addEventListener('touchend', fastHandle, { capture: true, passive: false });
  document.addEventListener('click', fastHandle, true);
})();
