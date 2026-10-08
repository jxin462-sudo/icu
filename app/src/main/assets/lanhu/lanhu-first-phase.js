(function () {
  var timer = 0;
  var empty = "--";
  var cameraConfigPrompted = false;
  var selectedMonitorWave = "all";
  var cameraPlaybackMode = false;
  var cameraPlaybackPeriod = "today";
  var cameraPlaybackSource = "cloud";
  var cameraPlaybackPageIndex = 0;
  var cameraPlaybackKeyword = "";
  var selectedCameraSnapshotKey = "";
  var syncedCameraSnapshotKey = "";
  var selectedTreatmentRow = -1;
  var monitorHistoryOpen = false;
  var monitorHistoryMetric = "heartRate";
  var monitorHistoryProbeIndices = {};
  var lastMonitorState = {};
  var lastHostMonitorLevelState = {
    color: "yellow",
    label: "二级"
  };

  // 教程页缩略图：JS 缓存的视频列表指纹（sessionStorage），用于判断"视频列表是否变化"
  var TUTORIAL_FP_STORAGE_KEY = "icuTutorialVideoFingerprint";
  var TUTORIAL_SELECTORS = [
    ".group_9 img",
    ".group_10 img",
    ".image-wrapper_1 img",
    ".image-wrapper_2 img",
    ".image-wrapper_3 img",
    ".image-wrapper_4 img"
  ];
  var TUTORIAL_TRANSPARENT_PLACEHOLDER =
    "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==";

  function nativeReady() {
    return !!(window.IcuNative && typeof window.IcuNative.firstPhaseState === "function");
  }

  function readFirstPhaseState() {
    if (!nativeReady()) {
      return {};
    }
    try {
      return JSON.parse(window.IcuNative.firstPhaseState() || "{}") || {};
    } catch (error) {
      return {};
    }
  }

  function monitorHasData(state) {
    if (!state) {
      return false;
    }
    if (typeof state.monitorHasData === "boolean") {
      return state.monitorHasData;
    }
    return !!(state.monitor && state.monitor.hasData);
  }

  function monitorConnected(state) {
    return !!(state && state.ble && state.ble.monitor && state.ble.monitor.connected);
  }

  function isPatientSwitchLocked() {
    var state = readFirstPhaseState();
    return monitorConnected(state) && monitorHasData(state);
  }

  function readBleState() {
    if (!window.IcuNative || typeof window.IcuNative.bleState !== "function") {
      return {};
    }
    try {
      return JSON.parse(window.IcuNative.bleState() || "{}") || {};
    } catch (error) {
      return {};
    }
  }

  function callAction(action, label) {
    if (!window.IcuNative || typeof window.IcuNative.action !== "function") {
      return;
    }
    try {
      window.IcuNative.action(action, label || action, window.location.pathname || "");
    } catch (error) {
    }
    schedule(0);
  }

  function clean(value) {
    if (value === undefined || value === null) {
      return "";
    }
    value = String(value).replace(/\s+/g, " ").trim();
    if (!value || value === "-" || value === "--" || value === "/" || value === "null" || value === "undefined") {
      return "";
    }
    if (isCommonAnimalSpecies(value)) {
      return value;
    }
    if (isDemoValue(value)) {
      return "";
    }
    if (/^X+$/.test(value.replace(/\s+/g, ""))) {
      return "";
    }
    return value;
  }

  function isCommonAnimalSpecies(value) {
    var compact = String(value || "").replace(/\s+/g, "");
    return compact === "\u732b" ||
      compact === "\u72d7" ||
      compact === "\u72ac" ||
      compact === "\u5154" ||
      compact === "\u9e1f" ||
      compact === "\u4ed3\u9f20" ||
      compact === "\u8c5a\u9f20" ||
      compact === "\u9f99\u732b";
  }

  function isDemoValue(value) {
    var compact = String(value || "").replace(/\s+/g, "");
    return compact === "饭团" ||
      compact === "饭团饭团" ||
      compact === "咪咪" ||
      compact === "李某" ||
      compact === "铲屎官" ||
      compact === "力大狮" ||
      compact === "黄医生" ||
      compact === "猫" ||
      compact === "01234567891" ||
      compact === "A0000001" ||
      compact === "A00000001" ||
      compact === "000005";
  }

  function display(value) {
    return clean(value) || empty;
  }

  function installPatientTempStore() {
    if (window.IcuPatientTemp) {
      return;
    }
    var storeKey = "icu_temp_patients_v2";
    var selectedKey = "icu_temp_patient_selected_v2";
    var selectedMatchKeyStore = "icu_temp_patient_selected_match_v2";
    var windowNameKey = "__icu_patient_temp__";
    var activePatientZone = "right";
    var memoryPatientsByZone = { left: [], right: [] };
    var searchQueriesByZone = { left: "", right: "" };
    var memoryPatients = memoryPatientsByZone.right;
    var searchQuery = "";
    var clicksInstalled = false;
    var modal = null;
    var modalVisualViewportHandler = null;
    var modalFocusInHandler = null;
    var modalKeyboardActive = false;
    var modalKeyboardShrinkSeen = false;
    var modalKeyboardSuppressActiveFallback = false;
    var modalLayoutBaseline = 0;
    var modalKeyboardClosedAt = 0;
    var modalOpenTimer = null;
    var modalLayoutToken = 0;
    // 弹窗当前 translateY 偏移（键盘贴顶时用增量计算：
    // 键盘动画中 setModalKeyboardLayout 会被调用多次，若每次都写绝对
    // 位移，第二次会把第一次的结果覆盖成错误位置，弹窗反复跳动）。
    var modalShiftY = 0;
    // 手指是否仍按在弹窗输入框上：按下期间任何事件都不得立即移动弹窗，
    // 否则第一次点击会被吞掉（键盘不弹/贴顶不生效）。
    var modalPointerDown = false;
    // ★ 是否正在“新建样本”（区别于编辑当前样本）
    var editingNewPatient = false;
    var lastNativePatientPayloadByZone = { left: "", right: "" };
    var lastNativePatientPayload = "";
    var lastSelectionId = "";
    var lastSelectionAt = 0;
    var pendingLocalEditsByZone = { left: {}, right: {} };
    var pendingLocalEdits = pendingLocalEditsByZone.right;
    var lastRenderedZone = "";
    var lastRenderedSelection = "";

    function normalizePatientZone(zone) {
      return zone === "left" ? "left" : "right";
    }

    function setActivePatientZone(zone) {
      activePatientZone = normalizePatientZone(zone);
      memoryPatients = memoryPatientsByZone[activePatientZone];
      searchQuery = searchQueriesByZone[activePatientZone] || "";
      pendingLocalEdits = pendingLocalEditsByZone[activePatientZone];
      lastNativePatientPayload = lastNativePatientPayloadByZone[activePatientZone] || "";
    }

    function zoneStorageKey(base) {
      return base + "_" + activePatientZone;
    }

    function blankPatient() {
      return {
        id: "",
        caseId: "",
        zone: activePatientZone,
        petName: "",
        species: "",
        age: "",
        recordNo: "",
        caseNo: "",
        ownerName: "",
        ownerPhone: "",
        doctor: "",
        visitDate: "",
        note: "",
        // 报告上的三个补充项：体重 / 科室 / 复诊时间
        weight: "",
        department: "",
        followUpDate: "",
        currentTreatment: false,
        treatmentStartTime: "",
        treatmentEndTime: ""
      };
    }

    function storageGet(key) {
      var value = "";
      try {
        value = window.sessionStorage.getItem(key) || "";
      } catch (error) {
      }
      if (value) {
        return value;
      }
      try {
        var bag = JSON.parse(window.name || "{}") || {};
        return bag[windowNameKey] && bag[windowNameKey][key] ? String(bag[windowNameKey][key]) : "";
      } catch (error2) {
        return "";
      }
    }

    function storageSet(key, value) {
      try {
        window.sessionStorage.setItem(key, value);
      } catch (error) {
      }
      try {
        var bag = JSON.parse(window.name || "{}") || {};
        bag[windowNameKey] = bag[windowNameKey] || {};
        bag[windowNameKey][key] = value;
        window.name = JSON.stringify(bag);
      } catch (error2) {
      }
    }

    function loadPatients() {
      try {
        var raw = storageGet(zoneStorageKey(storeKey));
        var parsed = raw ? JSON.parse(raw) : [];
        memoryPatientsByZone[activePatientZone] = normalizePatientList(Array.isArray(parsed) ? parsed : []);
        memoryPatients = memoryPatientsByZone[activePatientZone];
      } catch (error) {
        memoryPatients = normalizePatientList(memoryPatients || []);
      }
      return memoryPatients;
    }

    function savePatients(list) {
      memoryPatientsByZone[activePatientZone] = normalizePatientList(list || []);
      memoryPatients = memoryPatientsByZone[activePatientZone];
      storageSet(zoneStorageKey(storeKey), JSON.stringify(memoryPatients));
    }

    function editablePatientKeys() {
      return ["petName", "species", "age", "recordNo", "caseNo", "ownerName", "ownerPhone", "doctor", "visitDate", "note",
        "weight", "department", "followUpDate"];
    }

    function patientEditableSnapshot(patient) {
      var source = copyPatient(patient);
      var snapshot = {};
      var keys = editablePatientKeys();
      for (var i = 0; i < keys.length; i += 1) {
        snapshot[keys[i]] = clean(source[keys[i]]);
      }
      snapshot.caseNo = snapshot.caseNo || snapshot.recordNo;
      snapshot.recordNo = snapshot.recordNo || snapshot.caseNo;
      return snapshot;
    }

    function snapshotEquals(patient, snapshot) {
      var current = patientEditableSnapshot(patient);
      return JSON.stringify(current) === JSON.stringify(snapshot || {});
    }

    function rememberPendingLocalEdit(patient) {
      var current = copyPatient(patient);
      if (!current.id) {
        return;
      }
      pendingLocalEdits[current.id] = {
        snapshot: patientEditableSnapshot(current),
        until: Date.now() + 15000
      };
    }

    function clearPendingLocalEdit(id) {
      if (id && pendingLocalEdits[id]) {
        delete pendingLocalEdits[id];
      }
    }

    function mergePendingLocalEdit(patient) {
      var current = copyPatient(patient);
      if (!current.id) {
        return current;
      }
      var pending = pendingLocalEdits[current.id];
      if (!pending) {
        return current;
      }
      if (snapshotEquals(current, pending.snapshot)) {
        clearPendingLocalEdit(current.id);
        return current;
      }
      if (Date.now() > pending.until) {
        clearPendingLocalEdit(current.id);
        return current;
      }
      var keys = editablePatientKeys();
      for (var i = 0; i < keys.length; i += 1) {
        current[keys[i]] = pending.snapshot[keys[i]];
      }
      current.caseNo = current.caseNo || current.recordNo;
      current.recordNo = current.recordNo || current.caseNo;
      return current;
    }

    function selectedId() {
      return storageGet(zoneStorageKey(selectedKey));
    }

    function selectedMatchKey() {
      return storageGet(zoneStorageKey(selectedMatchKeyStore));
    }

    function patientMatchKey(item) {
      var patient = copyPatient(item);
      var record = clean(patient.recordNo) || clean(patient.caseNo);
      var visit = clean(patient.visitDate);
      var name = clean(patient.petName);
      var owner = clean(patient.ownerName);
      if (!record && !visit && !name && !owner) {
        return "";
      }
      return [record, visit, name, owner].join("|").toLowerCase();
    }

    function findPatientById(list, id) {
      var rows = Array.isArray(list) ? list : [];
      for (var i = 0; i < rows.length; i += 1) {
        if (rows[i] && rows[i].id === id) {
          return rows[i];
        }
      }
      return null;
    }

    function findPatientByMatchKey(list, key) {
      var rows = Array.isArray(list) ? list : [];
      if (!key) {
        return null;
      }
      for (var i = 0; i < rows.length; i += 1) {
        if (patientMatchKey(rows[i]) === key) {
          return rows[i];
        }
      }
      return null;
    }

    function setSelectedId(id, list) {
      storageSet(zoneStorageKey(selectedKey), id || "");
      if (!id) {
        storageSet(zoneStorageKey(selectedMatchKeyStore), "");
        return;
      }
      var matched = findPatientById(list || loadPatients(), id);
      storageSet(zoneStorageKey(selectedMatchKeyStore), matched ? patientMatchKey(matched) : "");
    }

    function currentPatient() {
      var list = loadPatients();
      var id = selectedId();
      var matchKey = selectedMatchKey();
      var matched = findPatientById(list, id);
      if (matched && (!matchKey || patientMatchKey(matched) === matchKey)) {
        return copyPatient(matched);
      }
      if (matchKey) {
        matched = findPatientByMatchKey(list, matchKey);
        if (matched) {
          setSelectedId(matched.id, list);
          return copyPatient(matched);
        }
      }
      if (matched) {
        setSelectedId(matched.id, list);
        return copyPatient(matched);
      }
      if (list.length) {
        setSelectedId(list[0].id, list);
        return copyPatient(list[0]);
      }
      return blankPatient();
    }

    function copyPatient(item) {
      var p = blankPatient();
      if (!item) {
        return p;
      }
      for (var key in p) {
        if (Object.prototype.hasOwnProperty.call(p, key)) {
          if (key === "currentTreatment") {
            p[key] = item[key] === true || item[key] === "true";
          } else {
            p[key] = clean(item[key]);
          }
        }
      }
      p.id = item.id || "";
      p.caseId = clean(item.caseId);
      p.zone = normalizePatientZone(item.zone || activePatientZone);
      p.caseNo = clean(item.caseNo) || clean(item.recordNo);
      p.recordNo = clean(item.recordNo) || clean(item.caseNo);
      return p;
    }

    function isPlaceholderPatientText(value) {
      var text = clean(value);
      if (!text || text === "-" || text === "--" || text === "/") {
        return true;
      }
      if (/^0{1,6}$/.test(text)) {
        return true;
      }
      return text === "\u5f85\u5f55\u5165" ||
        text === "\u672a\u547d\u540d" ||
        text === "\u672a\u77e5" ||
        text === "null" ||
        text === "undefined";
    }

    function patientHasListData(item) {
      if (!item) {
        return false;
      }
      var p = copyPatient(item);
      var keys = ["petName", "species", "ownerName", "ownerPhone", "doctor", "recordNo", "caseNo"];
      for (var i = 0; i < keys.length; i += 1) {
        if (!isPlaceholderPatientText(p[keys[i]])) {
          return true;
        }
      }
      return false;
    }

    function normalizePatientList(list) {
      var result = [];
      var seen = {};
      for (var i = 0; i < list.length; i += 1) {
        var item = copyPatient(list[i]);
        if (!patientHasListData(item)) {
          continue;
        }
        if (!item.id) {
          item.id = "tmp_" + Date.now() + "_" + i;
        }
        if (seen[item.id]) {
          item.id = item.id + "_" + i;
        }
        seen[item.id] = true;
        result.push(item);
      }
      return result;
    }

    function patientValue(patient, key) {
      var p = patient || blankPatient();
      if (key === "petListName") {
        return clean(p.petName) ? "宠物名：" + p.petName : "";
      }
      if (key === "speciesOwner") {
        var species = clean(p.species);
        var owner = clean(p.ownerName);
        return (species || owner)
          ? "种类：" + (species || "待录入") + "  主人：" + (owner || "待录入")
          : "";
      }
      if (key === "caseNo") {
        var no = clean(p.caseNo) || clean(p.recordNo);
        return no ? "编号：" + no : "";
      }
      if (key === "recordNo") {
        return clean(p.recordNo) || clean(p.caseNo);
      }
      // 就诊时间展示统一“年月日 时分秒”，与实时监护等页一致；
      // 存储值仍是 yyyy-MM-dd HH:mm:ss，只在展示时转换。
      if (key === "visitDate") {
        return clean(p.visitDate) ? toChineseDateTime(p.visitDate) : "";
      }
      return p[key] || "";
    }

    function writePatientLive(patient) {
      var nodes = document.querySelectorAll("[data-patient-live]");
      for (var i = 0; i < nodes.length; i += 1) {
        var key = nodes[i].getAttribute("data-patient-live") || "";
        var text = clean(patientValue(patient, key));
        // 与其它页面“待录入”占位保持一致：ICU状态/主机控制页走
        // data-patient-live 渲染路径，空值不再显示 "--"。
        nodes[i].textContent = text || "待录入";
      }
    }

    function writePatientFallback(patient) {
      // 兼容无 .box_6 的模块页（实时监护/摄像监控等）：只要存在顶部样本卡
      // 字段就执行，保证空值统一“待录入”，而不是留下 renderCommon 的 "--"。
      if (isSettingsFamilyPage() ||
          !document.querySelector(".text_35,.text_36,.text_40,.text_44,.text_48")) {
        return;
      }
      setPatientText(".text_10", patientValue(patient, "petListName") || "宠物名：待录入");
      setPatientText(".text_11", patientValue(patient, "speciesOwner") || "种类：待录入  主人：待录入");
      setPatientText(".text_12", patientValue(patient, "caseNo") || "编号：待录入");
      setPatientText(".text_13", toChineseDateTime(patient.visitDate) || "待录入");
      setPatientText(".text_35", patient.petName);
      setPatientText(".text_36", patient.species);
      setPatientText(".text_39", patient.age);
      setPatientText(".text_40", patient.recordNo || patient.caseNo);
      setPatientText(".text_43", patient.ownerName);
      setPatientText(".text_44", patient.ownerPhone);
      setPatientText(".text_47", patient.doctor);
      setPatientText(".text_48", toChineseDateTime(patient.visitDate));
      setText(".paragraph_1", patient.note);
    }

    function setPatientText(selector, value) {
      var node = document.querySelector(selector);
      if (!node) {
        return;
      }
      var text = clean(value);
      // 患者信息空值统一显示“待录入”（与其它页面一致），不再显示 "--"。
      node.textContent = text || "待录入";
    }

    function syncNativePatient(patient, force) {
      if (!window.IcuNative || typeof window.IcuNative.syncTempPatient !== "function") {
        return;
      }
      try {
        var payload = JSON.stringify(patient || currentPatient() || blankPatient());
        if (!force && payload === lastNativePatientPayload) {
          return;
        }
        lastNativePatientPayload = payload;
        lastNativePatientPayloadByZone[activePatientZone] = payload;
        window.IcuNative.syncTempPatient(payload);
      } catch (error) {
      }
    }

    function nativePatientRef(id) {
      var match = String(id || "").match(/^native_(left|right)_(\d+)$/);
      return match ? { zone: match[1], index: parseInt(match[2], 10) } : null;
    }

    function nativeIndexFromPatientId(id) {
      var ref = nativePatientRef(id);
      return ref && ref.zone === activePatientZone ? ref.index : -1;
    }

    function isNativePatientId(id) {
      return nativeIndexFromPatientId(id) >= 0;
    }

    function selectNativePatient(id) {
      var index = nativeIndexFromPatientId(id);
      if (index < 0 || !window.IcuNative || typeof window.IcuNative.action !== "function") {
        return false;
      }
      try {
        var ref = nativePatientRef(id);
        window.IcuNative.action("patient_select_" + ref.zone + "_" + ref.index,
          "patient_select_" + ref.zone + "_" + ref.index, window.location.pathname || "");
        return true;
      } catch (error) {
        return false;
      }
    }

    function currentPageName() {
      var path = window.location.pathname || "";
      var match = path.match(/src\/views\/([^/]+)\//);
      return match ? match[1] : "";
    }

    function sidebarConfig() {
      var page = currentPageName();
      if (page === "lanhu_2zhujikongzhi") {
        return {
          container: ".block_2",
          search: ".block_5",
          slots: [
            { box: ".block_6", name: ".text_10", desc: ".text_11", no: ".text_12", date: ".text_13", bullet: ".section_2", bleIcon: null },
            { box: ".section_31", name: ".text-group_1", desc: ".text_14", no: ".text_15", date: ".text_16", bullet: ".thumbnail_6", bleIcon: ".image_3" },
            { box: ".section_32", name: ".text_17", desc: ".text_18", no: ".text_19", date: ".text_20", bullet: ".group_6", bleIcon: ".image_4" },
            { box: ".section_33", name: ".text_21", desc: ".text_22", no: ".text_23", date: ".text_24", bullet: ".group_7", bleIcon: ".image_5" },
            { box: ".section_34", name: ".text_25", desc: ".text_26", no: ".text_27", date: ".text_28", bullet: ".block_11", bleIcon: ".image_6" },
            { box: ".section_35", name: ".text_29", desc: ".text_30", no: ".text_31", date: ".text_32", bullet: ".box_1", bleIcon: ".image_7" }
          ]
        };
      }
      if (page === "lanhu_3shishijianhu") {
        return {
          container: ".box_3",
          search: ".group_6",
          slots: [
            { box: ".group_7", name: ".text_10", desc: ".text_11", no: ".text_12", date: ".text_13", bullet: ".section_2", bleIcon: null },
            { box: ".group_30", name: ".text-group_1", desc: ".text_14", no: ".text_15", date: ".text_16", bullet: ".thumbnail_6", bleIcon: ".image_3" },
            { box: ".group_31", name: ".text_17", desc: ".text_18", no: ".text_19", date: ".text_20", bullet: ".group_10", bleIcon: ".image_4" },
            { box: ".group_32", name: ".text_21", desc: ".text_22", no: ".text_23", date: ".text_24", bullet: ".block_1", bleIcon: ".image_5" },
            { box: ".group_33", name: ".text_25", desc: ".text_26", no: ".text_27", date: ".text_28", bullet: ".block_2", bleIcon: ".image_6" },
            { box: ".group_34", name: ".text_29", desc: ".text_30", no: ".text_31", date: ".text_32", bullet: ".box_5", bleIcon: ".image_7" }
          ]
        };
      }
      if (page === "lanhu_4jiankong") {
        return {
          container: ".group_1",
          search: ".box_5",
          slots: [
            { box: ".box_6", name: ".text_10", desc: ".text_11", no: ".text_12", date: ".text_13", bullet: ".block_5", bleIcon: null },
            { box: ".box_37", name: ".text-group_1", desc: ".text_14", no: ".text_15", date: ".text_16", bullet: ".thumbnail_6", bleIcon: ".image_3" },
            { box: ".box_38", name: ".text_17", desc: ".text_18", no: ".text_19", date: ".text_20", bullet: ".box_9", bleIcon: ".image_4" },
            { box: ".box_39", name: ".text_21", desc: ".text_22", no: ".text_23", date: ".text_24", bullet: ".box_11", bleIcon: ".image_5" },
            { box: ".box_40", name: ".text_25", desc: ".text_26", no: ".text_27", date: ".text_28", bullet: ".block_6", bleIcon: ".image_6" },
            { box: ".box_41", name: ".text_29", desc: ".text_30", no: ".text_31", date: ".text_32", bullet: ".box_14", bleIcon: ".image_7" }
          ]
        };
      }
      if (page === "lanhu_5jiankonghuifang") {
        return {
          container: ".group_2",
          search: ".box_5",
          slots: [
            { box: ".box_6", name: ".text_10", desc: ".text_11", no: ".text_12", date: ".text_13", bullet: ".section_3" },
            { box: ".box_35", name: ".text-group_1", desc: ".text_14", no: ".text_15", date: ".text_16", bullet: ".thumbnail_6" },
            { box: ".box_36", name: ".text_17", desc: ".text_18", no: ".text_19", date: ".text_20", bullet: ".group_5" },
            { box: ".box_37", name: ".text_21", desc: ".text_22", no: ".text_23", date: ".text_24", bullet: ".block_1" },
            { box: ".box_38", name: ".text_25", desc: ".text_26", no: ".text_27", date: ".text_28", bullet: ".block_2" },
            { box: ".box_39", name: ".text_29", desc: ".text_30", no: ".text_31", date: ".text_32", bullet: ".group_6" }
          ]
        };
      }
      if (page === "lanhu_6zhiliaojilu") {
        return {
          container: ".group_1",
          search: ".section_3",
          slots: [
            { box: ".section_4", name: ".text_10", desc: ".text_11", no: ".text_12", date: ".text_13", bullet: ".group_2" },
            { box: ".box_41", name: ".text-group_1", desc: ".text_14", no: ".text_15", date: ".text_16", bullet: ".thumbnail_6" },
            { box: ".box_42", name: ".text_17", desc: ".text_18", no: ".text_19", date: ".text_20", bullet: ".box_5" },
            { box: ".box_43", name: ".text_21", desc: ".text_22", no: ".text_23", date: ".text_24", bullet: ".group_3" },
            { box: ".box_44", name: ".text_25", desc: ".text_26", no: ".text_27", date: ".text_28", bullet: ".section_9" },
            { box: ".box_45", name: ".text_29", desc: ".text_30", no: ".text_31", date: ".text_32", bullet: ".box_6" }
          ]
        };
      }
      if (page === "lanhu_7jiaocheng") {
        return {
          container: ".group_2",
          search: ".box_5",
          slots: [
            { box: ".box_6", name: ".text_10", desc: ".text_11", no: ".text_12", date: ".text_13", bullet: ".section_2" },
            { box: ".section_9", name: ".text-group_1", desc: ".text_14", no: ".text_15", date: ".text_16", bullet: ".thumbnail_6" },
            { box: ".section_10", name: ".text_17", desc: ".text_18", no: ".text_19", date: ".text_20", bullet: ".box_9" },
            { box: ".section_11", name: ".text_21", desc: ".text_22", no: ".text_23", date: ".text_24", bullet: ".block_4" },
            { box: ".section_12", name: ".text_25", desc: ".text_26", no: ".text_27", date: ".text_28", bullet: ".section_3" },
            { box: ".section_13", name: ".text_29", desc: ".text_30", no: ".text_31", date: ".text_32", bullet: ".group_3" }
          ]
        };
      }
      return {
        container: ".section_1",
        search: ".box_6",
        slots: [
          { box: ".box_7", name: ".text_10", desc: ".text_11", no: ".text_12", date: ".text_13", bullet: ".box_8" },
          { box: ".box_9", name: ".text-group_1", desc: ".text_14", no: ".text_15", date: ".text_16", bullet: ".thumbnail_6" },
          { box: ".box_10", name: ".text_17", desc: ".text_18", no: ".text_19", date: ".text_20", bullet: ".section_3" },
          { box: ".box_11", name: ".text_21", desc: ".text_22", no: ".text_23", date: ".text_24", bullet: ".group_3" },
          { box: ".box_12", name: ".text_25", desc: ".text_26", no: ".text_27", date: ".text_28", bullet: ".block_2" },
          { box: ".box_13", name: ".text_29", desc: ".text_30", no: ".text_31", date: ".text_32", bullet: ".block_3" }
        ]
      };
    }

    function sidebarContainer() {
      var config = sidebarConfig();
      var node = document.querySelector(config.container);
      if (node) {
        node.classList.add("icu-patient-sidebar");
      }
      return node;
    }

    function querySidebar(selector) {
      var container = sidebarContainer();
      return container ? container.querySelector(selector) : null;
    }

    function containsNode(parent, child) {
      while (child && child !== document.body) {
        if (child === parent) {
          return true;
        }
        child = child.parentNode;
      }
      return false;
    }

    function layoutScale() {
      var value = "1";
      try {
        value = window.getComputedStyle(document.documentElement).getPropertyValue("--lanhu-scale") || "1";
      } catch (error) {
      }
      var scale = parseFloat(value);
      return scale > 0 ? scale : 1;
    }

    function ensureSearchInput() {
      var config = sidebarConfig();
      var container = sidebarContainer();
      var box = container ? container.querySelector(config.search) : null;
      if (!box || box.querySelector(".icu-patient-search-input")) {
        return;
      }
      var label = box.querySelector(".text_9");
      if (label) {
        label.style.display = "none";
      }
      var input = document.createElement("input");
      input.className = "icu-patient-search-input";
      input.type = "text";
      input.setAttribute("inputmode", "search");
      input.setAttribute("autocomplete", "off");
      input.setAttribute("autocorrect", "off");
      input.setAttribute("spellcheck", "false");
      input.placeholder = "搜索宠物名字/主人";
      input.value = searchQuery;
      box.setAttribute("data-icu-search-box", "1");
      input.addEventListener("input", function () {
      searchQueriesByZone[activePatientZone] = input.value || "";
      searchQuery = searchQueriesByZone[activePatientZone];
        render();
      });
      input.addEventListener("click", function (event) {
        event.stopPropagation();
      }, true);
      input.addEventListener("touchend", function (event) {
        event.stopPropagation();
      }, { capture: true, passive: true });
      box.appendChild(input);
    }

    function listSlots() {
      return sidebarConfig().slots;
    }

    function matchesQuery(item) {
      var q = clean(searchQuery).toLowerCase();
      if (!q) {
        return true;
      }
      var text = [item.petName, item.ownerName, item.species, item.recordNo, item.caseNo, item.ownerPhone].join(" ").toLowerCase();
      return text.indexOf(q) >= 0;
    }

    function filteredPatients() {
      var source = loadPatients().filter(patientHasListData).filter(matchesQuery);
      source.sort(function (a, b) {
        if (!!a.currentTreatment !== !!b.currentTreatment) {
          return a.currentTreatment ? -1 : 1;
        }
        return 0;
      });
      return source;
    }

    function importNativeRecords(records, selectedIndex, zone) {
      if (!Array.isArray(records)) {
        return;
      }
      setActivePatientZone(zone);
      var previousList = loadPatients();
      var previousSelected = selectedId();
      var previousMatch = selectedMatchKey();
      if (!previousMatch) {
        previousMatch = patientMatchKey(findPatientById(previousList, previousSelected));
      }
      var list = [];
      var selectedNativeId = "";
      for (var i = 0; i < records.length; i += 1) {
        var item = copyPatient(records[i]);
        if (!patientHasListData(item)) {
          continue;
        }
        item.zone = activePatientZone;
        item.id = "native_" + activePatientZone + "_" + i;
        item = mergePendingLocalEdit(item);
        list.push(item);
        if (i === selectedIndex) {
          selectedNativeId = item.id;
        }
      }
      savePatients(list);
      if (selectedNativeId) {
        setSelectedId(selectedNativeId, list);
        return;
      }
      var chosen = null;
      if (previousSelected) {
        chosen = findPatientById(list, previousSelected);
        if (chosen && previousMatch && patientMatchKey(chosen) !== previousMatch) {
          chosen = null;
        }
      }
      if (!chosen && previousMatch) {
        var nativeSelected = selectedNativeId ? findPatientById(list, selectedNativeId) : null;
        if (!nativeSelected || patientMatchKey(nativeSelected) !== previousMatch) {
          chosen = findPatientByMatchKey(list, previousMatch);
        }
      }
      if (chosen) {
        setSelectedId(chosen.id, list);
      } else if (selectedNativeId) {
        setSelectedId(selectedNativeId, list);
      } else if (list.length) {
        setSelectedId(list[0].id, list);
      } else {
        setSelectedId("", list);
      }
    }

    function isCurrentSelected() {
      return !!currentPatient().currentTreatment;
    }

    function selectCurrentTreatment() {
      var list = loadPatients();
      for (var i = 0; i < list.length; i += 1) {
        if (list[i].currentTreatment) {
          setSelectedId(list[i].id, list);
          render();
          window.dispatchEvent(new CustomEvent("icu-patient-temp-change"));
          return true;
        }
      }
      return false;
    }

    function setTextIn(scope, selector, value) {
      var node = scope ? scope.querySelector(selector) : querySidebar(selector);
      if (node) {
        node.style.visibility = "";
        var text = clean(value);
        // 侧栏槽位空值统一“待录入”（renderList 最后执行，必须在这里
        // 也处理，否则会把 writePatientLive 刚写入的“待录入”覆盖成 "--"）。
        node.textContent = text || "待录入";
      }
    }

    // 左侧当前样本卡“主人”专用行：插在“种类”行之后，跟随种类行左对齐。
    // 每个样本槽只保留一个主人行：用 data-sidebar-owner-for 标记按种类行选择器复用，
    // 避免每轮 render 重复插行（后面的纯文本槽行不在槽盒内，不能只在盒内查找）。
    function ensureSidebarOwnerRow(container, slot) {
      if (!container || !slot) {
        return null;
      }
      var descNode = container.querySelector(slot.desc);
      if (!descNode) {
        return null;
      }
      var marker = String(slot.desc || "");
      var owners = container.querySelectorAll('[data-sidebar-owner-for="' + marker + '"]');
      if (owners.length > 0) {
        // 清理历史版本可能残留的重复主人行，只保留第一个
        for (var d = 1; d < owners.length; d += 1) {
          if (owners[d].parentNode) {
            owners[d].parentNode.removeChild(owners[d]);
          }
        }
        return owners[0];
      }
      // 首卡/带行容器的槽：插到“种类”行容器之后；
      // 后面纯文本槽（text_14/18/22/26/30 直接挂在侧栏容器下）：插到“种类”文本之后，
      // 保证主人行紧随种类行，而不是被追加到列表末尾。
      var anchor = descNode
        ? (descNode.parentElement === container ? descNode : descNode.parentElement)
        : null;
      if (!anchor || !anchor.parentNode) {
        return null;
      }
      var owner = document.createElement("div");
      owner.className = "icu-sidebar-owner";
      owner.setAttribute("data-sidebar-owner-for", marker);
      anchor.parentNode.insertBefore(owner, anchor.nextSibling);
      return owner;
    }

    function slotNodes(slot) {
      var ownerSelector = '[data-sidebar-owner-for="' + String(slot.desc || "") + '"]';
      var selectors = [slot.box, slot.name, slot.desc, slot.no, slot.date, ownerSelector];
      var nodes = [];
      var container = sidebarContainer();
      for (var i = 0; i < selectors.length; i += 1) {
        var node = container ? container.querySelector(selectors[i]) : null;
        if (node && nodes.indexOf(node) < 0) {
          nodes.push(node);
        }
      }
      return nodes;
    }

    function slotVisualNodes(slot) {
      var nodes = slotNodes(slot);
      var bullet = slot.bullet ? querySidebar(slot.bullet) : null;
      if (bullet && nodes.indexOf(bullet) < 0) {
        nodes.push(bullet);
      }
      return nodes;
    }

    function setSlotVisible(slot, visible) {
      var nodes = slotVisualNodes(slot);
      for (var i = 0; i < nodes.length; i += 1) {
        nodes[i].style.visibility = visible ? "" : "hidden";
      }
    }

    function markSlotTargets(slot, item, index) {
      var nodes = slotVisualNodes(slot);
      var id = item ? item.id : "";
      for (var i = 0; i < nodes.length; i += 1) {
        nodes[i].setAttribute("data-temp-patient-slot", String(index));
        nodes[i].setAttribute("data-temp-patient-id", id);
      }
    }

    function removeRecordLayers() {
      var layers = document.querySelectorAll(".icu-record-hit-layer,.icu-record-active-bg");
      for (var i = 0; i < layers.length; i += 1) {
        if (layers[i].parentNode) {
          layers[i].parentNode.removeChild(layers[i]);
        }
      }
    }

    function clampRowBounds(top, height, sectionHeight) {
      var rowTop = Math.max(0, top);
      var rowHeight = Math.max(0, Math.min(height, sectionHeight - rowTop));
      if (rowHeight <= 0) {
        return null;
      }
      return {
        top: rowTop,
        height: rowHeight
      };
    }

    function rowBounds(sectionRect, slot, scale) {
      scale = scale || layoutScale();
      var sectionHeight = sectionRect.height / scale;
      var box = querySidebar(slot.box);
      var firstBox = sidebarConfig().slots[0] && sidebarConfig().slots[0].box;
      if (box && slot.box === firstBox) {
        var boxRect = box.getBoundingClientRect();
        if (boxRect && boxRect.width > 0 && boxRect.height > 0) {
          return clampRowBounds(
            (boxRect.top - sectionRect.top) / scale,
            boxRect.height / scale,
            sectionHeight
          );
        }
      }
      var nodes = slotNodes(slot);
      var top = Infinity;
      var bottom = -Infinity;
      for (var i = 0; i < nodes.length; i += 1) {
        var rect = nodes[i].getBoundingClientRect();
        if (!rect || rect.width <= 0 || rect.height <= 0) {
          continue;
        }
        top = Math.min(top, rect.top);
        bottom = Math.max(bottom, rect.bottom);
      }
      if (top === Infinity || bottom <= top) {
        return null;
      }
      return clampRowBounds(
        (top - sectionRect.top) / scale - 12,
        (bottom - top) / scale + 24,
        sectionHeight
      );
    }

    function setColor(selector, color) {
      var node = querySidebar(selector);
      if (node) {
        node.style.color = color;
      }
    }

    function setBulletColor(slot, active) {
      var bullet = slot.bullet ? querySidebar(slot.bullet) : null;
      if (bullet) {
        bullet.style.backgroundColor = active ? "rgba(0, 116, 255, 1)" : "rgba(255, 255, 255, 1)";
      }
    }

    function applySlotVisualState(slot, item, currentId) {
      var active = !!item && item.id === currentId;
      var nodes = slotNodes(slot);
      var firstBox = sidebarConfig().slots[0] && sidebarConfig().slots[0].box;
      for (var i = 0; i < nodes.length; i += 1) {
        nodes[i].classList.add("icu-temp-patient-row-node");
        nodes[i].classList.toggle("icu-temp-patient-row-active", active);
      }
      var box = querySidebar(slot.box);
      if (box) {
        box.classList.toggle("icu-temp-patient-active", active);
        if (slot.box === firstBox) {
          box.style.backgroundColor = active ? "rgba(255, 255, 255, 1)" : "transparent";
        }
      }
      setColor(slot.name, active ? "rgba(0, 116, 255, 1)" : "rgba(255, 255, 255, 1)");
      setColor(slot.desc, active ? "rgba(87, 87, 87, 1)" : "rgba(255, 255, 255, 1)");
      setColor(slot.no, active ? "rgba(87, 87, 87, 1)" : "rgba(255, 255, 255, 1)");
      setColor(slot.date, active ? "rgba(87, 87, 87, 1)" : "rgba(255, 255, 255, 1)");
      setBulletColor(slot, active);

      // ★ P2-BUG#11 修复: 更新蓝牙连接图标状态
      if (slot.bleIcon) {
        var bleIcon = querySidebar(slot.bleIcon);
        if (bleIcon && item) {
          var bleState = window.IcuNative ? window.IcuNative.bleState() : null;
          var connected = false;
          if (bleState && bleState.host && bleState.host.connected) {
            connected = true;
          }
          bleIcon.classList.toggle("icu-ble-connected", connected);
          bleIcon.classList.toggle("icu-ble-disconnected", !connected);
        }
      }
    }

    function ensureRecordLayers(slots, rows, currentId) {
      var section = sidebarContainer();
      var config = sidebarConfig();
      if (!section || !section.querySelector(config.search)) {
        removeRecordLayers();
        return;
      }
      removeRecordLayers();
      var sectionRect = section.getBoundingClientRect();
      if (!sectionRect || !sectionRect.width || !sectionRect.height) {
        return;
      }
      var scale = layoutScale();
      var sectionWidth = section.offsetWidth || (sectionRect.width / scale);
      for (var i = 0; i < slots.length; i += 1) {
        var item = rows[i] ? copyPatient(rows[i]) : null;
        if (!item || !item.id) {
          continue;
        }
        var bounds = rowBounds(sectionRect, slots[i], scale);
        if (!bounds) {
          continue;
        }
        if (item.id === currentId) {
          var activeBg = document.createElement("div");
          activeBg.className = "icu-record-active-bg";
          activeBg.style.top = bounds.top + "px";
          activeBg.style.height = bounds.height + "px";
          section.appendChild(activeBg);
        }
        var layer = document.createElement("div");
        layer.className = "icu-record-hit-layer";
        layer.setAttribute("data-temp-patient-slot", String(i));
        layer.setAttribute("data-temp-patient-id", item.id);
        layer.style.left = "0px";
        layer.style.top = bounds.top + "px";
        layer.style.width = sectionWidth + "px";
        layer.style.height = bounds.height + "px";
        section.appendChild(layer);
      }
    }

    function renderLegacyList() {
      var slots = listSlots();
      var rows = filteredPatients();
      var current = selectedId();
      var container = sidebarContainer();
      if (!container) {
        removeRecordLayers();
        return;
      }
      for (var i = 0; i < slots.length; i += 1) {
        var box = container.querySelector(slots[i].box);
        if (!box) {
          continue;
        }
        // 左侧“当前样本信息”卡（各模块页可见的白色样本卡）：种类/主人拆成两行。
        // 这里只处理第一个样本槽；ICU状态页其余历史槽位保持原有“种类 主人”同行，
        // 同样通过下方 CSS 做省略号防超框。
        if (i === 0) {
          box.classList.add("icu-patient-first-card");
        }
        box.style.visibility = "";
        var item = rows[i] ? copyPatient(rows[i]) : null;
        box.setAttribute("data-temp-patient-slot", String(i));
        box.setAttribute("data-temp-patient-id", item ? item.id : "");
        markSlotTargets(slots[i], item, i);
        // 空槽/空字段也保留前缀占位，与“宠物名：待录入、主人：待录入”一致。
        var petText = item ? patientValue(item, "petListName") : "";
        var noText = item ? patientValue(item, "caseNo") : "";
        var dateText = item ? toChineseDateTime(item.visitDate) : "";
        // ★ 0904 自测：左侧样本信息展示（每个模块页）中“主人”与“种类”拆行，
        //   主人排在种类下一行并左对齐；任何样本槽保存后都按此展示，
        //   不再把“种类：X  主人：Y”拼在同一行。
        var speciesText = item ? "种类：" + display(item.species) : "";
        var ownerText = item ? "主人：" + display(item.ownerName) : "";
        setTextIn(box, slots[i].name, petText || "宠物名：待录入");
        setTextIn(container, slots[i].desc, speciesText || "种类：待录入");
        setTextIn(container, slots[i].no, noText || "编号：待录入");
        setTextIn(container, slots[i].date, dateText || "待录入");
        var ownerRow = ensureSidebarOwnerRow(container, slots[i]);
        var descNode = container.querySelector(slots[i].desc);
        if (ownerRow) {
          ownerRow.style.visibility = "";
          ownerRow.textContent = ownerText || "主人：待录入";
          // 独立文本槽（非首卡带 row wrapper 的槽）按种类行的实际左边距对齐
          if (descNode && descNode.parentElement === container) {
            ownerRow.style.setProperty("margin-left", getComputedStyle(descNode).marginLeft, "important");
          } else {
            ownerRow.style.removeProperty("margin-left");
          }
        }
        applySlotVisualState(slots[i], item, current);
        if (ownerRow && descNode) {
          // 主人行颜色跟随该槽“种类”行的激活/未激活颜色，避免文字颜色不一致。
          ownerRow.style.color = getComputedStyle(descNode).color;
        }
        setSlotVisible(slots[i], !!item);
      }
      var emptyHint = document.querySelector(".icu-patient-empty-hint");
      if (!emptyHint && container) {
        emptyHint = document.createElement("div");
        emptyHint.className = "icu-patient-empty-hint";
        container.appendChild(emptyHint);
      }
      if (emptyHint) {
        emptyHint.textContent = "";
      }
      ensureRecordLayers(slots, rows, current);
      if (window.requestAnimationFrame) {
        window.requestAnimationFrame(function () {
          ensureRecordLayers(slots, rows, selectedId());
        });
      }
    }

    function hideLegacyPatientSlots(container) {
      var slots = listSlots();
      for (var i = 0; i < slots.length; i += 1) {
        var nodes = slotVisualNodes(slots[i]);
        for (var j = 0; j < nodes.length; j += 1) {
          nodes[j].style.display = "none";
          nodes[j].setAttribute("aria-hidden", "true");
          nodes[j].removeAttribute("data-temp-patient-id");
        }
      }
      removeRecordLayers();
    }

    function ensurePatientListViewport(container) {
      var viewport = container.querySelector(".icu-patient-list-viewport");
      if (viewport) {
        return viewport;
      }
      viewport = document.createElement("div");
      viewport.className = "icu-patient-list-viewport";
      viewport.setAttribute("role", "listbox");
      viewport.setAttribute("aria-label", "当前舱样本列表");
      var list = document.createElement("div");
      list.className = "icu-patient-list";
      viewport.appendChild(list);
      var search = container.querySelector(sidebarConfig().search);
      if (search && search.parentNode === container) {
        container.insertBefore(viewport, search.nextSibling);
      } else {
        container.appendChild(viewport);
      }
      return viewport;
    }

    function addPatientListField(row, className, label, value) {
      var field = document.createElement("div");
      field.className = "icu-patient-list-field " + className;
      var prefix = document.createElement("span");
      prefix.className = "icu-patient-list-label";
      prefix.textContent = label;
      var text = document.createElement("span");
      text.className = "icu-patient-list-value";
      text.textContent = clean(value) || "待录入";
      field.appendChild(prefix);
      field.appendChild(text);
      row.appendChild(field);
    }

    function renderList() {
      var container = sidebarContainer();
      if (!container) {
        removeRecordLayers();
        return;
      }
      hideLegacyPatientSlots(container);
      var viewport = ensurePatientListViewport(container);
      var list = viewport.querySelector(".icu-patient-list");
      var oldScrollTop = viewport.scrollTop;
      var rows = filteredPatients();
      var selected = selectedId();
      var fragment = document.createDocumentFragment();
      if (!rows.length) {
        var emptyHint = document.createElement("div");
        emptyHint.className = "icu-patient-empty-hint";
        emptyHint.textContent = searchQuery ? "没有匹配的样本" : "当前舱暂无样本";
        fragment.appendChild(emptyHint);
      }
      for (var i = 0; i < rows.length; i += 1) {
        var item = copyPatient(rows[i]);
        var row = document.createElement("div");
        var active = item.id === selected;
        row.className = "icu-patient-list-row" + (active ? " is-active" : "");
        row.setAttribute("role", "option");
        row.setAttribute("aria-selected", active ? "true" : "false");
        row.setAttribute("data-temp-patient-id", item.id);
        row.setAttribute("data-temp-patient-slot", String(i));
        addPatientListField(row, "icu-patient-list-name", "宠物名：", item.petName);
        addPatientListField(row, "icu-patient-list-species", "种类：", item.species);
        addPatientListField(row, "icu-patient-list-owner", "主人：", item.ownerName);
        addPatientListField(row, "icu-patient-list-number", "编号：", item.recordNo || item.caseNo);
        addPatientListField(row, "icu-patient-list-date", "就诊时间：", toChineseDateTime(item.visitDate));
        fragment.appendChild(row);
      }
      while (list.firstChild) {
        list.removeChild(list.firstChild);
      }
      list.appendChild(fragment);
      var selectionChanged = lastRenderedZone !== activePatientZone || lastRenderedSelection !== selected;
      lastRenderedZone = activePatientZone;
      lastRenderedSelection = selected;
      if (selectionChanged) {
        var currentRow = list.querySelector('.icu-patient-list-row[data-temp-patient-id="' + selected + '"]');
        if (currentRow && currentRow.scrollIntoView) {
          currentRow.scrollIntoView({ block: "nearest" });
        }
      } else {
        viewport.scrollTop = oldScrollTop;
      }
    }

    function installClicks() {
      if (clicksInstalled) {
        return;
      }
      clicksInstalled = true;
      function handleListSelection(event) {
        var node = event.target;
        var container = sidebarContainer();
        while (node && node !== document.body) {
          if (node.getAttribute && node.getAttribute("data-temp-patient-id") !== null) {
            if (!container || !containsNode(container, node)) {
              return;
            }
            var id = node.getAttribute("data-temp-patient-id") || "";
            if (id) {
              var now = Date.now();
              event.preventDefault();
              event.stopPropagation();
              if (event.stopImmediatePropagation) {
                event.stopImmediatePropagation();
              }
              if (id === lastSelectionId && now - lastSelectionAt < 360) {
                return;
              }
              if (isPatientSwitchLocked()) {
                if (window.IcuNative && typeof window.IcuNative.action === "function") {
                  window.IcuNative.action("patient_switch_locked", "实时监护已有数据，不能切换宠物", window.location.pathname || "");
                }
                return;
              }
              lastSelectionId = id;
              lastSelectionAt = now;
              setSelectedId(id);
              if (!selectNativePatient(id)) {
                syncNativePatient(currentPatient(), true);
              }
              render();
              window.dispatchEvent(new CustomEvent("icu-patient-temp-change"));
              window.setTimeout(function () {
                schedule(0);
              }, 180);
            }
            return;
          }
          node = node.parentNode;
        }
      }
      document.addEventListener("touchend", handleListSelection, { capture: true, passive: false });
      document.addEventListener("click", handleListSelection, true);
    }

    // ★ P2-问题4 修复:录入信息需限制长度,否则超长文本会把
    //   患者卡片、报告和治疗记录里的显示撑破/截断。
    //   上限按患者栏白框/列表行能完整显示的长度收紧：
    //   宠物名/主人/医生等在 10~12 字以内，病历号 12，医嘱多行放宽到 200。
    //   ownerPhone 不在此表:它由 normalizePhoneDigits + 自己的 maxLength 管(7-15 位)。
    var FIELD_MAX_LENGTH = {
      petName:  12,
      species:  8,
      age:      6,
      recordNo: 12,
      caseNo:   12,
      ownerName: 10,
      doctor:   10,
      weight:   10,
      department: 10,
      followUpDate: 20,
      note:     200
    };

    function field(label, key, value, area) {
      var wrap = document.createElement("label");
      wrap.className = "icu-patient-field";
      if (area) {
        wrap.className += " icu-patient-field-wide";
      }
      var title = document.createElement("span");
      title.textContent = label;
      wrap.appendChild(title);
      var input = area ? document.createElement("textarea") : document.createElement("input");
      input.value = value || "";
      input.setAttribute("data-field", key);
      // ★ 问题2:输入键盘右下角显示/触发“确定(完成)”，收起软键盘。
      //   对医嘱 textarea 同样生效：完成键提交并收起键盘，粘贴的多行内容仍可保留。
      input.setAttribute("enterkeyhint", "done");
      input.setAttribute("autocomplete", "off");
      input.setAttribute("autocorrect", "off");
      input.setAttribute("spellcheck", "false");
      // ★ P2-问题4:限制录入长度。maxLength 对 input 和 textarea 都有效,
      //   同时给出剩余字数提示,避免用户打到上限时以为键盘失灵。
      var limit = FIELD_MAX_LENGTH[key];
      if (limit) {
        // 旧档/粘贴进来的超长值也要在打开弹窗时立刻截断，而不是只在保存时兜底。
        var initial = String(input.value || "");
        if (initial.length > limit) {
          input.value = initial.slice(0, limit);
        }
        input.maxLength = limit;
        input.setAttribute("title", label + "最多 " + limit + " 个字符");
      }
      if (area) {
        input.rows = 2;
      }
      wrap.appendChild(input);
      return wrap;
    }

    function normalizePhoneDigits(value) {
      // ★ P2-问题3 补修:这里原本硬截断到 11 位(slice(0, 11)),
      //   而本函数既绑在输入框的 input 事件上、又用在保存逻辑里,
      //   所以之前把 maxLength 放宽到 20、校验放宽到 7-15 位都被它抵消 ——
      //   用户实际仍然输不进超过 11 位。上限改为与校验一致的 15 位。
      return String(value || "").replace(/\D+/g, "").slice(0, 15);
    }

    function toDateTimeLocalValue(value) {
      var text = clean(value).replace(/\s+/g, " ").trim();
      if (!text) {
        return "";
      }
      var match = text.match(/^(\d{4})-(\d{2})-(\d{2})[ T]+(\d{2}):(\d{2})(?::(\d{2}))?$/);
      if (!match) {
        return "";
      }
      return match[1] + "-" + match[2] + "-" + match[3] + "T" + match[4] + ":" + match[5];
    }

    function fromDateTimeLocalValue(value) {
      var text = clean(value).replace(/\s+/g, " ").trim();
      if (!text) {
        return "";
      }
      var match = text.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/);
      if (match) {
        return match[1] + "-" + match[2] + "-" + match[3] + " " + match[4] + ":" + match[5] + ":00";
      }
      return text;
    }

    function resizeModalTextarea(input) {
      if (!input || input.tagName !== "TEXTAREA") {
        return;
      }
      input.style.height = "auto";
      input.style.height = Math.max(104, input.scrollHeight) + "px";
    }

    // 键盘弹出/收起时切换弹窗布局：键盘弹出时弹窗靠上，
    // 并把内部最大高度限制在键盘上方可视范围内，让内容自己滚动。
    // 布局与滚动一律瞬时完成（样式表已禁用弹窗内 transition/smooth），
    // 多路 focus/touch/resize 通知最后只会执行一次收敛后的结果，
    // 避免内容被多段平滑滚动互相追赶、在弹窗内上下反复滑动。
    function setModalKeyboardLayout(modalEl, dialogEl, forced) {
      if (!modalEl || !dialogEl) {
        return;
      }
      var visual = window.visualViewport;
      var layoutHeight = window.innerHeight || document.documentElement.clientHeight || 0;
      var visualShrunk = !!(visual && visual.height && layoutHeight &&
        visual.height < layoutHeight - 60);
      // 兼容 ADJUST_RESIZE：键盘弹出时窗口高度会缩小，与打开弹窗时的基准高度比较。
      var layoutShrunk = !!(modalLayoutBaseline > 0 && layoutHeight &&
        layoutHeight < modalLayoutBaseline - 60);
      var activeInside = !!(document.activeElement &&
        nodeInsideDialog(document.activeElement, dialogEl));
      var shrinkNow = visualShrunk || layoutShrunk;
      var now = Date.now();
      var keyboardOpen;
      if (forced === true) {
        // 主动输入（focus/touch/原生通知）：一律按键盘弹出贴顶。
        // 不再用“刚收起 700ms 内忽略”的规则——那会让收起后立刻点其它
        // 输入框时布局不生效，出现“医嘱不在键盘上方/其它框点不动”。
        modalKeyboardSuppressActiveFallback = false;
        modalKeyboardClosedAt = 0;
        keyboardOpen = true;
      } else if (forced === false) {
        // 原生输入法监听确认键盘已收起：直接恢复居中。
        modalKeyboardShrinkSeen = false;
        modalKeyboardSuppressActiveFallback = true;
        modalKeyboardClosedAt = now;
        keyboardOpen = false;
      } else if (shrinkNow) {
        // 高度缩小 → 键盘确定弹出，保持贴顶；记录“见过缩小”，
        // 等高度恢复时再判定键盘已收起。
        modalKeyboardShrinkSeen = true;
        modalKeyboardSuppressActiveFallback = false;
        modalKeyboardClosedAt = 0;
        keyboardOpen = true;
      } else if (modalKeyboardShrinkSeen) {
        // 之前见过缩小、现在高度恢复 → 键盘已收起，滑回居中。
        modalKeyboardShrinkSeen = false;
        modalKeyboardSuppressActiveFallback = true;
        keyboardOpen = false;
      } else {
        // 个别 WebView 不报高度变化：只要焦点仍在输入框内就一直贴顶，
        // 避免输入过程中效果消失。
        keyboardOpen = activeInside && !modalKeyboardSuppressActiveFallback;
      }
      modalKeyboardActive = keyboardOpen;
      modalLayoutToken += 1;
      var token = modalLayoutToken;
      if (keyboardOpen) {
        modalEl.classList.add("icu-patient-modal-keyboard");
        modalEl.classList.remove("icu-patient-modal-closed");
        var available;
        if (visualShrunk) {
          var visibleTop = typeof visual.offsetTop === "number" ? visual.offsetTop : 0;
          // 底部多留 ~120px：拼音输入法的候选栏/联想栏在键盘上方，
          // 只留 12px 时文字仍会被候选栏盖住。
          available = Math.max(160, Math.round(visual.height - visibleTop - 120));
        } else if (layoutShrunk) {
          // 窗口确实被输入法 resize（ADJUST_RESIZE 生效）：
          // 可视高度就是 layoutHeight，再留出候选栏高度。
          available = Math.max(160, Math.round(layoutHeight - 120));
        } else {
          // 覆盖式键盘且无 resize/visualViewport 信号（华为擎云 C5e 实测：
          // innerHeight 仍是全屏 686=1200/1.75，但键盘从约 52% 高度开始遮住
          // 屏幕）。此时不能按“窗口已缩小”给满高度，只能按比例保守估算，
          // 取 46% 保证医嘱整行都露在键盘上方。
          available = Math.max(200, Math.round(layoutHeight * 0.46));
        }
        // 样式表里 .icu-patient-dialog 的 max-height 带 !important，
        // 普通内联样式压不过它，必须也用 !important 设置。
        dialogEl.style.setProperty("max-height", available + "px", "important");
      } else {
        modalEl.classList.remove("icu-patient-modal-keyboard");
        modalEl.classList.add("icu-patient-modal-closed");
        dialogEl.style.removeProperty("max-height");
      }
      // 用 transform 精确控制“居中 ↔ 贴顶”，不依赖 flex 对齐是否生效。
      // token 防止旧帧稍后执行、把刚收起的键盘又“拉回顶部”：
      // 之前偶发“键盘已关但弹窗仍贴顶”，顶部以外的输入框被移出点击区。
      if (keyboardOpen) {
        var dialogRect = dialogEl.getBoundingClientRect();
        var baseTop = 10;
        if (visualShrunk && typeof visual.offsetTop === "number" && visual.offsetTop > 0) {
          baseTop = Math.round(visual.offsetTop + 10);
        }
        // rect.top 已含当前 transform：加上差值即可，不能直接写绝对量。
        var delta = baseTop - dialogRect.top;
        modalShiftY += delta;
        dialogEl.style.setProperty("transform", "translateY(" + modalShiftY + "px)", "important");
      } else {
        dialogEl.style.removeProperty("transform");
        modalShiftY = 0;
        // 收起时不再强行把内容滚回顶部：键盘消失的瞬间内容如果在手指下
        // 移动，会吞掉随后对其它输入框的点击。只夹回越界滚动值。
        window.setTimeout(function () {
          if (token !== modalLayoutToken || !dialogEl || !dialogEl.isConnected) {
            return;
          }
          var maxScroll = Math.max(0, dialogEl.scrollHeight - dialogEl.clientHeight);
          if (dialogEl.scrollTop > maxScroll) {
            dialogEl.scrollTop = maxScroll;
          }
        }, 300);
      }
    }

    // 普通单行输入：让“标签+输入框”整体出现在弹窗可视区内。
    // 只滚动弹窗自身、不用 scrollIntoView、不做 smooth，
    // 避免连带滚动外层容器或让内容在手指下移动。
    function alignModalField(wrapEl) {
      if (!wrapEl) {
        return;
      }
      var dialogEl = wrapEl.closest ? wrapEl.closest(".icu-patient-dialog") : null;
      if (!dialogEl) {
        return;
      }
      for (var i = 0; i < 3; i += 1) {
        var dialogRect = dialogEl.getBoundingClientRect();
        var wrapRect = wrapEl.getBoundingClientRect();
        var viewTop = dialogRect.top + 12;
        var viewBottom = dialogRect.top + dialogEl.clientHeight - 12;
        if (wrapRect.top < viewTop) {
          dialogEl.scrollTop += wrapRect.top - viewTop;
          continue;
        }
        if (wrapRect.bottom > viewBottom) {
          dialogEl.scrollTop += wrapRect.bottom - viewBottom;
          continue;
        }
        break;
      }
      // 部分 WebView（华为擎云 C5e/HarmonyOS）直接改 scrollTop 不生效，
      // 用浏览器原生 scrollIntoView 兜底一次；此时手动对齐已基本完成，
      // “nearest” 一般不会产生额外滚动。
      var dr = dialogEl.getBoundingClientRect();
      var wr = wrapEl.getBoundingClientRect();
      if (wr.top < dr.top || wr.bottom > dr.bottom) {
        try {
          wrapEl.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "auto" });
        } catch (error) {
          try { wrapEl.scrollIntoView(true); } catch (error2) {}
        }
      }
    }

    // 医嘱是自动长高的多行框：焦点进入/每输入一个字符都要把“当前正在输入的
    // 那一行”滚到键盘上方，而不是只滚动字段顶部。
    function scrollNoteInputVisible(input) {
      if (!input) {
        return;
      }
      var modalEl = input.closest ? input.closest(".icu-patient-modal") : null;
      var dialogEl = input.closest ? input.closest(".icu-patient-dialog") : null;
      if (!dialogEl) {
        return;
      }
      // 输入过程中若布局没贴顶（个别设备事件缺失），按键盘弹出状态补一次；
      // 平时该状态已由 schedule 建立，这里不会反复触发。
      if (modalEl && !modalEl.classList.contains("icu-patient-modal-keyboard")) {
        setModalKeyboardLayout(modalEl, dialogEl, true);
      }
      var wrap = input.closest ? input.closest(".icu-patient-field") : input;
      var bottomReserve = 24;
      for (var i = 0; i < 4; i += 1) {
        var dialogRect = dialogEl.getBoundingClientRect();
        var inputRect = input.getBoundingClientRect();
        var wrapRect = wrap.getBoundingClientRect();
        var clientH = dialogEl.clientHeight;
        var visibleBottom = dialogRect.top + clientH - bottomReserve;
        // 医嘱块不高时：整块（标签+输入框）贴到可视区顶部，让内容完整可见；
        // 只在跨过“一屏放不下”的临界点时切换一次，不做反复居中。
        if (inputRect.height <= clientH - bottomReserve - 88) {
          var targetTop = dialogRect.top + 8;
          var deltaTop = wrapRect.top - targetTop;
          if (deltaTop > 1 || deltaTop < -1) {
            dialogEl.scrollTop += deltaTop;
            continue;
          }
          break;
        }
        // 内容很多、输入框快占满弹窗时：跟随底部，保证当前输入行不被键盘盖住。
        if (inputRect.top < dialogRect.top + 8) {
          dialogEl.scrollTop += inputRect.top - (dialogRect.top + 8);
          continue;
        }
        if (inputRect.bottom > visibleBottom) {
          dialogEl.scrollTop += inputRect.bottom - visibleBottom;
          continue;
        }
        break;
      }
      // 兜底：scrollTop 手动滚动在部分 WebView 上不生效时，让浏览器原生
      // 把正在输入的医嘱区域滚进可视范围。
      var dr2 = dialogEl.getBoundingClientRect();
      var ir2 = input.getBoundingClientRect();
      if (ir2.top < dr2.top || ir2.bottom > dr2.bottom) {
        try {
          input.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "auto" });
        } catch (error) {
          try { input.scrollIntoView(true); } catch (error2) {}
        }
      }
    }

    // 把“键盘弹出 → 贴顶 → 滚动医嘱”合并成一次动作。
    // 一律延迟到事件结束后执行（不直接在 focus 事件里同步改布局）：
    // 华为 C5e/HarmonyOS 上，focus 过程中同步移动弹窗会让第一次焦点/键盘
    // 提交失败，表现为“第一次点没反应，第二次才整体上移”。
    function scheduleModalKeyboardOpen(modalEl, dialogEl, inputElement) {
      if (!modalEl || !dialogEl) {
        return;
      }
      if (modalOpenTimer) {
        window.clearTimeout(modalOpenTimer);
      }
      var run = function () {
        modalOpenTimer = null;
        if (!dialogEl || !dialogEl.isConnected) {
          return;
        }
        if (modalPointerDown) {
          // 手指仍按着：延后执行，绝不在这时移动弹窗。
          modalOpenTimer = window.setTimeout(run, 90);
          return;
        }
        setModalKeyboardLayout(modalEl, dialogEl, true);
        if (!modalKeyboardActive) {
          return;
        }
        var input = inputElement || document.activeElement;
        if (!input || !nodeInsideDialog(input, dialogEl) ||
            input.readOnly || input.disabled) {
          return;
        }
        if (String(input.tagName || "").toUpperCase() === "TEXTAREA") {
          scrollNoteInputVisible(input);
        } else {
          var wrap = input.closest ? input.closest(".icu-patient-field") : null;
          alignModalField(wrap || input);
        }
      };
      modalOpenTimer = window.setTimeout(run, 90);
    }

    // 键盘弹出/收起过程中 visualViewport/resize 会连续触发：
    // 这里用“未强制”的状态机跟随，缩小→贴顶、恢复→居中，
    // 避免把“高度恢复”误判成“键盘又弹出”而反复上下。
    function scheduleModalLayoutSync(modalEl, dialogEl, inputElement) {
      if (!modalEl || !dialogEl) {
        return;
      }
      if (modalOpenTimer) {
        window.clearTimeout(modalOpenTimer);
      }
      var run = function () {
        modalOpenTimer = null;
        if (modalPointerDown) {
          modalOpenTimer = window.setTimeout(run, 90);
          return;
        }
        setModalKeyboardLayout(modalEl, dialogEl);
        if (!modalKeyboardActive || !inputElement) {
          return;
        }
        if (String(inputElement.tagName || "").toUpperCase() === "TEXTAREA") {
          scrollNoteInputVisible(inputElement);
        } else {
          var wrap = inputElement.closest ? inputElement.closest(".icu-patient-field") : null;
          alignModalField(wrap || inputElement);
        }
      };
      modalOpenTimer = window.setTimeout(run, 120);
    }

    function cancelModalKeyboardOpen() {
      if (modalOpenTimer) {
        window.clearTimeout(modalOpenTimer);
        modalOpenTimer = null;
      }
    }

    function nodeInsideDialog(node, dialog) {
      while (node && node !== document.body) {
        if (node === dialog) {
          return true;
        }
        node = node.parentNode;
      }
      return false;
    }

    // 键盘右下角“确定/完成”：单行输入框按回车直接收起键盘；
    // 医嘱多行框保留 Shift+回车换行，普通回车视为完成并收起键盘。
    function installModalDoneKeys(dialog) {
      dialog.addEventListener("keydown", function (event) {
        var target = event.target;
        if (!target || !nodeInsideDialog(target, dialog)) {
          return;
        }
        var tag = String(target.tagName || "").toUpperCase();
        if (tag !== "INPUT" && tag !== "TEXTAREA") {
          return;
        }
        if (event.key !== "Enter" || event.isComposing) {
          return;
        }
        if (event.shiftKey && tag === "TEXTAREA") {
          return; // Shift+回车：医嘱内主动换行
        }
        if (event.ctrlKey || event.altKey || event.metaKey) {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        if (typeof target.blur === "function") {
          target.blur();
        }
      }, true);
    }

    function openEditor(forceNew) {
      closeModal();
      if (forceNew && isPatientSwitchLocked()) {
        if (window.IcuNative && typeof window.IcuNative.action === "function") {
          window.IcuNative.action("patient_new_locked", "实时监护已有数据，请结束后再新建样本", window.location.pathname || "");
        }
        return;
      }
      editingNewPatient = !!forceNew;
      nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
      document.documentElement.classList.add("icu-patient-modal-open");
      var p = editingNewPatient ? blankPatient() : currentPatient();
      modal = document.createElement("div");
      modal.className = "icu-patient-modal";
      modalLayoutBaseline = window.innerHeight || document.documentElement.clientHeight || 0;
      modal.innerHTML = '<div class="icu-patient-dialog"><div class="icu-patient-title">新建样本</div><div class="icu-patient-form"></div><div class="icu-patient-actions"><button data-act="cancel">取消</button><button data-act="save">保存</button></div></div>';
      var form = modal.querySelector(".icu-patient-form");
      var dialog = modal.querySelector(".icu-patient-dialog");

      // P2-BUG#2 修复：为所有输入框添加自动滚动功能，防止被键盘遮挡
      var addAutoScroll = function(fieldElement) {
        var input = fieldElement.querySelector("input, textarea");
        if (input) {
          input.addEventListener("focus", function() {
            // 统一走防抖入口：与 focusin/原生通知合并成一次“贴顶+对齐”，
            // 不再立即多段平滑滚动，避免弹窗内上下反复滑动。
            scheduleModalKeyboardOpen(modal, dialog, input);
            var limit = FIELD_MAX_LENGTH[fieldElement.querySelector("input, textarea").getAttribute("data-field")];
            if (limit) {
              input.setAttribute("title", fieldElement.textContent.replace(/\s+/g, "").trim() + "最多 " + limit + " 个字符");
            }
          });
        }
      };

      var petNameField = field("宠物名称", "petName", p.petName);
      addAutoScroll(petNameField);
      form.appendChild(petNameField);

      var speciesField = field("动物种类", "species", p.species);
      addAutoScroll(speciesField);
      form.appendChild(speciesField);

      var ageField = field("年龄", "age", p.age);
      addAutoScroll(ageField);
      form.appendChild(ageField);

      var recordField = field("病历编号", "recordNo", p.recordNo || p.caseNo);
      addAutoScroll(recordField);
      form.appendChild(recordField);

      var ownerField = field("宠物主人", "ownerName", p.ownerName);
      addAutoScroll(ownerField);
      form.appendChild(ownerField);
      var phoneField = field("联系电话", "ownerPhone", p.ownerPhone);
      var phoneInput = phoneField.querySelector("[data-field='ownerPhone']");
      if (phoneInput) {
        phoneInput.type = "tel";
        phoneInput.inputMode = "numeric";
        // ★ P2-问题3:联系电话不再限制 11 位。
        //   maxLength 与保存校验(7-15 位)及 normalizePhoneDigits 的上限保持一致,
        //   之前写 20 会让用户先打到 20 位再被校验拒绝。
        phoneInput.maxLength = 15;
        phoneInput.placeholder = "请输入联系电话";
        phoneInput.addEventListener("input", function () {
          this.value = normalizePhoneDigits(this.value);
        });
        // P2-BUG#2: 添加自动滚动
        phoneInput.addEventListener("focus", function() {
          scheduleModalKeyboardOpen(modal, dialog, phoneInput);
        });
      }
      form.appendChild(phoneField);

      var doctorField = field("主治医生", "doctor", p.doctor);
      addAutoScroll(doctorField);
      form.appendChild(doctorField);

      // ★ 报告上的三个补充项：体重 / 科室 / 复诊时间。
      //   暂时用不到，输入框先注释掉；数据字段（weight/department/followUpDate）
      //   和报告绑定都保留，之后要用把下面几行放开即可，PDF 会自动带出。
      // var weightField = field("体重", "weight", p.weight);
      // addAutoScroll(weightField);
      // form.appendChild(weightField);
      //
      // var departmentField = field("科室", "department", p.department);
      // addAutoScroll(departmentField);
      // form.appendChild(departmentField);
      //
      // var followUpField = field("复诊时间", "followUpDate", p.followUpDate);
      // addAutoScroll(followUpField);
      // form.appendChild(followUpField);

      var visitField = field("就诊时间", "visitDate", p.visitDate);
      var visitInput = visitField.querySelector("[data-field='visitDate']");
      if (visitInput) {
        // ★ 原先用 type="datetime-local" + showPicker(),弹出的是 Chromium 自带的
        //   英文选择器("Set date and time"、Jul/Aug/Sep、AM/PM),语言跟 WebView
        //   locale 走,改不了。这里换成只读文本框 + 自建中文选择器。
        //   显示中文形式,真实值放 data-storage-value,保存时读它。
        visitInput.type = "text";
        visitInput.readOnly = true;
        visitInput.placeholder = "请选择就诊时间";
        var setVisit = function (storageText) {
          visitInput.setAttribute("data-storage-value", storageText || "");
          visitInput.value = toChineseDateTime(storageText);
        };
        setVisit(clean(p.visitDate));
        var openPicker = function (event) {
          if (event) {
            event.preventDefault();
          }
          openChineseDateTimePicker(visitInput.getAttribute("data-storage-value"), setVisit);
        };
        visitInput.addEventListener("click", openPicker);
        visitInput.addEventListener("focus", function () {
          // 只读框仍会取得焦点,但不弹系统键盘;主动失焦避免遮挡。
          visitInput.blur();
        });
      }
      form.appendChild(visitField);
      var noteField = field("医嘱", "note", p.note, true);
      var noteInput = noteField.querySelector("[data-field='note']");
      if (noteInput) {
        noteInput.placeholder = "请输入医嘱";
        noteInput.value = p.note || "";
        resizeModalTextarea(noteInput);
        noteInput.addEventListener("input", function () {
          resizeModalTextarea(noteInput);
          // 一开始输入就立刻上滑，持续跟随正在输入的内容。
          scrollNoteInputVisible(noteInput);
        });
        var focusNote = function () {
          resizeModalTextarea(noteInput);
          // 统一走防抖入口：触摸/聚焦/原生通知都合并成一次“贴顶+滚动”。
          scheduleModalKeyboardOpen(modal, dialog, noteInput);
        };
        noteInput.addEventListener("focus", focusNote);
        noteInput.addEventListener("click", focusNote);
        // 只记录“手指是否按着”：按下期间任何事件都不得立即移动弹窗，
        // 避免第一次点击被吞掉；不再在抬起时主动排程滚动——否则每次
        // 上滑查看内容后松手，120ms 后内容会被自动拉回，造成“滑不上去”。
        ["touchstart", "pointerdown", "mousedown"].forEach(function (type) {
          noteInput.addEventListener(type, function () {
            modalPointerDown = true;
          }, { capture: true, passive: true });
        });
        ["touchend", "pointerup", "mouseup", "pointercancel", "touchcancel"].forEach(function (type) {
          noteInput.addEventListener(type, function () {
            modalPointerDown = false;
          }, { capture: true, passive: true });
        });
      }
      form.appendChild(noteField);
      // 取消按钮直接绑定 click 关闭。不能在 pointerup/touchend 时提前关弹窗：
      // 那样同一次点击的 click 会落到弹窗关闭后露出的页面模块上，
      // 误触发下方可点击跳转的入口。
      var cancelButton = modal.querySelector('button[data-act="cancel"]');
      if (cancelButton) {
        var closeByCancel = function (event) {
          if (!modal) {
            return;
          }
          if (event) {
            if (event.cancelable) {
              event.preventDefault();
            }
            if (event.stopPropagation) {
              event.stopPropagation();
            }
          }
          closeModal();
        };
        cancelButton.addEventListener("click", closeByCancel);
      }
      modal.addEventListener("click", function (event) {
        var act = event.target && event.target.getAttribute ? event.target.getAttribute("data-act") : "";
        if (act === "cancel") {
          closeModal();
        } else if (act === "save") {
          saveFromModal();
        }
      });
      document.body.appendChild(modal);
      // 手指从弹窗外深色空白处开始上滑时，原生不会滚动弹窗内容；
      // 这里把空白处的滑动换算成 .icu-patient-dialog 的滚动，
      // 避免“起点在空白区域就滑不动/卡住”。
      var backdropScrollStart = null;
      var backdropTouchId = null;
      var backdropInside = function (node) {
        return !!node && !!dialog && !!dialog.contains && dialog.contains(node);
      };
      var backdropClearScroll = function () {
        backdropScrollStart = null;
        backdropTouchId = null;
      };
      modal.addEventListener("touchstart", function (event) {
        var touch = event.touches && event.touches[0];
        if (!touch) {
          return;
        }
        if (backdropInside(event.target)) {
          backdropClearScroll();
          return;
        }
        backdropScrollStart = { y: touch.clientY, top: dialog.scrollTop };
        backdropTouchId = touch.identifier;
      }, { passive: true });
      modal.addEventListener("touchmove", function (event) {
        var touch = event.touches && event.touches[0];
        if (!backdropScrollStart || !touch || touch.identifier !== backdropTouchId) {
          return;
        }
        var deltaY = touch.clientY - backdropScrollStart.y;
        dialog.scrollTop = Math.max(0, backdropScrollStart.top - deltaY);
        if (event.cancelable) {
          event.preventDefault();
        }
      }, { passive: false });
      modal.addEventListener("touchend", backdropClearScroll, { passive: true });
      modal.addEventListener("touchcancel", backdropClearScroll, { passive: true });
      installModalDoneKeys(dialog);
      // document 级兜底：无论焦点怎样进入弹窗输入框，都立刻切到“贴顶可滚动”布局。
      modalFocusInHandler = function (event) {
        var target = event && event.target;
        if (!target || !modal || !nodeInsideDialog(target, dialog)) {
          return;
        }
        var tag = String(target.tagName || "").toUpperCase();
        if (tag !== "INPUT" && tag !== "TEXTAREA") {
          return;
        }
        // 只读框（就诊时间）不弹系统键盘，交给日期选择器，不触发贴顶。
        if (target.readOnly || target.disabled) {
          return;
        }
        scheduleModalKeyboardOpen(modal, dialog, target);
      };
      document.addEventListener("focusin", modalFocusInHandler, true);
      // 键盘弹出/收起、visualViewport 变化时，实时切换弹窗布局并滚动正在输入的字段。
      modalVisualViewportHandler = function () {
        var active = document.activeElement;
        var tag = active ? String(active.tagName || "").toUpperCase() : "";
        var inputActive = tag === "INPUT" || tag === "TEXTAREA";
        if (!active || !inputActive || !nodeInsideDialog(active, dialog) ||
            active.readOnly || active.disabled) {
          cancelModalKeyboardOpen();
          setModalKeyboardLayout(modal, dialog, false);
          return;
        }
        // 用未强制状态机跟随：缩小→贴顶、恢复→居中，
        // 避免高度恢复时被当成“键盘又弹出”反复上下。
        scheduleModalLayoutSync(modal, dialog, active);
      };
      if (window.visualViewport && modalVisualViewportHandler) {
        window.visualViewport.addEventListener("resize", modalVisualViewportHandler);
        window.visualViewport.addEventListener("scroll", modalVisualViewportHandler);
      }
      window.addEventListener("resize", modalVisualViewportHandler);
      if (noteInput) {
        resizeModalTextarea(noteInput);
      }
    }

    function closeModal() {
      editingNewPatient = false;
      cancelModalKeyboardOpen();
      modalKeyboardActive = false;
      modalKeyboardShrinkSeen = false;
      modalKeyboardSuppressActiveFallback = false;
      modalKeyboardClosedAt = 0;
      modalLayoutBaseline = 0;
      modalShiftY = 0;
      modalPointerDown = false;
      if (modalFocusInHandler) {
        document.removeEventListener("focusin", modalFocusInHandler, true);
        modalFocusInHandler = null;
      }
      if (modalVisualViewportHandler) {
        window.removeEventListener("resize", modalVisualViewportHandler);
      }
      if (window.visualViewport && modalVisualViewportHandler) {
        window.visualViewport.removeEventListener("resize", modalVisualViewportHandler);
        window.visualViewport.removeEventListener("scroll", modalVisualViewportHandler);
        modalVisualViewportHandler = null;
      }
      if (modal && modal.parentNode) {
        modal.parentNode.removeChild(modal);
      }
      modal = null;
      document.documentElement.classList.remove("icu-patient-modal-open");
      schedule(60);
    }

    function saveFromModal() {
      if (!modal) {
        return;
      }
      var p = editingNewPatient ? blankPatient() : currentPatient();
      if (!p.id) {
        p.id = "tmp_" + Date.now();
      }
      var inputs = modal.querySelectorAll("[data-field]");
      for (var i = 0; i < inputs.length; i += 1) {
        var key = inputs[i].getAttribute("data-field");
        if (key === "ownerPhone") {
          p.ownerPhone = normalizePhoneDigits(inputs[i].value);
          continue;
        }
        if (key === "visitDate") {
          // 输入框显示的是中文形式,真实存储值在 data-storage-value 上。
          // 兼容三种来源:自建选择器写入的存储值、旧的中文文本、旧的 ISO 文本。
          var stored = clean(inputs[i].getAttribute("data-storage-value"));
          p.visitDate = stored ||
            fromChineseDateTime(clean(inputs[i].value)) ||
            fromDateTimeLocalValue(inputs[i].value) ||
            clean(inputs[i].value);
          continue;
        }
        // ★ P2-问题4:保存时按 FIELD_MAX_LENGTH 兜底截断。
        //   maxLength 只挡键盘输入,粘贴、旧存档或从别处导入的值仍可能超长。
        var text = clean(inputs[i].value);
        var limit = FIELD_MAX_LENGTH[key];
        if (limit && text.length > limit) {
          text = text.slice(0, limit);
        }
        p[key] = text;
      }
      // P2-BUG#3 修复：放宽电话号码长度限制，支持7-15位数字
      if (p.ownerPhone && (p.ownerPhone.length < 7 || p.ownerPhone.length > 15)) {
        window.alert("联系电话必须为7-15位数字");
        return;
      }
      if (!clean(p.visitDate)) {
        p.visitDate = formatDateTime(new Date()).replace(/\s+/g, " ").trim();
      }
      p.caseNo = p.recordNo;
      var list = loadPatients();
      var replaced = false;
      for (var j = 0; j < list.length; j += 1) {
        if (list[j].id === p.id) {
          list[j] = p;
          replaced = true;
          break;
        }
      }
      if (!replaced) {
        list.unshift(p);
      }
      savePatients(list);
      setSelectedId(p.id, list);
      rememberPendingLocalEdit(p);
      closeModal();
      syncNativePatient(p, true);
      render();
      window.dispatchEvent(new CustomEvent("icu-patient-temp-change"));
    }

    function deleteCurrent() {
      var id = selectedId();
      if (!id) {
        return true;
      }
      if (isNativePatientId(id)) {
        clearPendingLocalEdit(id);
        return false;
      }

      // ★ 修复 P2-BUG#13: 添加删除确认提示
      var patient = currentPatient();
      var patientName = patient && patient.name ? patient.name : "此治疗记录";
      if (!window.confirm("确认删除" + patientName + "吗？删除后将无法恢复。")) {
        return true; // 用户取消，不执行删除
      }

      var list = loadPatients().filter(function (item) {
        return item.id !== id;
      });
      clearPendingLocalEdit(id);
      savePatients(list);
      setSelectedId(list.length ? list[0].id : "", list);
      render();
      syncNativePatient(currentPatient(), true);
      window.dispatchEvent(new CustomEvent("icu-patient-temp-change"));
      return true;
    }

    function handleAction(action) {
      if (action === "patient_edit") {
        openEditor(false);
        return true;
      }
      if (action === "patient_new") {
        // 有原生桥时走 native：真正在病例列表里新增一条当前治疗记录，
        // 原生创建成功后通过 openAfterNew 回调自动打开样本信息录入框，
        // 不再用固定延时猜测列表刷新时机，避免保存时覆盖旧样本。
        if (nativeReady()) {
          if (isPatientSwitchLocked()) {
            if (window.IcuNative && typeof window.IcuNative.action === "function") {
              window.IcuNative.action("patient_new_locked", "实时监护已有数据，请结束后再新建样本", window.location.pathname || "");
            }
            return true;
          }
          window.IcuNative.action("patient_new", "新建样本", window.location.pathname || "");
          return true;
        }
        openEditor(true);
        return true;
      }
      if (action === "patient_delete") {
        return deleteCurrent();
      }
      return false;
    }

    function render() {
      ensureSearchInput();
      installClicks();
      var current = currentPatient();
      writePatientLive(current);
      writePatientFallback(current);
      renderList();
    }

    window.IcuPatientTemp = {
      currentPatient: currentPatient,
      handleAction: handleAction,
      render: render,
      syncNative: syncNativePatient,
      importNativeRecords: importNativeRecords,
      isCurrentSelected: isCurrentSelected,
      selectCurrentTreatment: selectCurrentTreatment,
      // 原生“新建样本”成功后由 Java 回调调用（见 MainActivity.openLanhuPatientEditorAfterNew）
      openAfterNew: function () {
        openEditor(false);
      },
      // 原生输入法可见性通知（见 MainActivity 的 IME 监听）：
      // visible=true 键盘弹出，false 键盘收起。
      notifyIme: function (visible) {
        var modalEl = document.querySelector(".icu-patient-modal");
        var dialogEl = modalEl ? modalEl.querySelector(".icu-patient-dialog") : null;
        if (!modalEl || !dialogEl) {
          return;
        }
        if (visible) {
          var active = document.activeElement;
          var inputForScroll = null;
          if (active && nodeInsideDialog(active, dialogEl)) {
            inputForScroll = String(active.tagName || "").toUpperCase() === "TEXTAREA" ? active : null;
          }
          scheduleModalKeyboardOpen(modalEl, dialogEl, inputForScroll);
          return;
        }
        cancelModalKeyboardOpen();
        setModalKeyboardLayout(modalEl, dialogEl, false);
      }
    };
  }

  function writeTextNode(node, value, prefix, suffix) {
    if (!node) {
      return;
    }
    var cleanValue = clean(value);
    node.textContent = cleanValue ? (prefix || "") + cleanValue + (suffix || "") : empty;
  }

  function setText(selector, value, prefix, suffix) {
    var node = document.querySelector(selector);
    writeTextNode(node, value, prefix, suffix);
  }

  function setAllText(selector, value, prefix, suffix) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      writeTextNode(nodes[i], value, prefix, suffix);
    }
  }

  function setPreferredText(preferredSelector, fallbackSelector, value, prefix, suffix) {
    var nodes = preferredSelector ? document.querySelectorAll(preferredSelector) : [];
    if (nodes.length) {
      for (var i = 0; i < nodes.length; i += 1) {
        writeTextNode(nodes[i], value, prefix, suffix);
      }
      return;
    }
    if (fallbackSelector) {
      setAllText(fallbackSelector, value, prefix, suffix);
    }
  }

  function showIfAnyText(selector) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].style.visibility = "";
    }
  }

  function hide(selector) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].style.visibility = "hidden";
    }
  }

  function show(selector) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].style.visibility = "";
    }
  }

  function pageName() {
    var path = window.location.pathname || "";
    var match = path.match(/src\/views\/([^/]+)\//);
    return match ? match[1] : "";
  }

  function markPageContext() {
    var page = document.querySelector(".page");
    if (!page) {
      return;
    }
    var modulePages = [
      "lanhu_1icuzhuangtaikaobei",
      "lanhu_2zhujikongzhi",
      "lanhu_3shishijianhu",
      "lanhu_4jiankong",
      "lanhu_5jiankonghuifang",
      "lanhu_6zhiliaojilu",
      "lanhu_7jiaocheng"
    ];
    var current = pageName();
    page.classList.toggle("icu-app-module", modulePages.indexOf(current) >= 0);
    page.classList.toggle("icu-page-status", current === "lanhu_1icuzhuangtaikaobei");
    page.classList.toggle("icu-page-control", current === "lanhu_2zhujikongzhi");
    page.classList.toggle("icu-page-monitor", current === "lanhu_3shishijianhu");
    page.classList.toggle("icu-page-camera-live", current === "lanhu_4jiankong");
    page.classList.toggle("icu-page-camera-playback", current === "lanhu_5jiankonghuifang");
    page.classList.toggle("icu-page-treatment", current === "lanhu_6zhiliaojilu");
    page.classList.toggle("icu-page-tutorial", current === "lanhu_7jiaocheng");
  }

  function patient(state) {
    if (window.IcuPatientTemp && typeof window.IcuPatientTemp.currentPatient === "function") {
      return window.IcuPatientTemp.currentPatient();
    }
    return state.patient || {};
  }

  function org(state) {
    return state.organization || {};
  }

  function ble(state) {
    return state.ble || {};
  }

  function monitor(state) {
    return ble(state).monitor || {};
  }

  function host(state) {
    return ble(state).host || {};
  }

  function hostControls(state) {
    return host(state).controls || {};
  }

  function hostMonitorLevelState(state) {
    var hostInfo = host(state);
    var controls = hostControls(state);
    if ((!hostInfo || !Object.keys(hostInfo).length) && (!controls || !Object.keys(controls).length)) {
      var bleState = readBleState();
      hostInfo = host(bleState);
      controls = hostControls(bleState);
    }
    var color = clean(hostInfo.statusLightColor).toLowerCase();
    var label = clean(hostInfo.monitorLevel);
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
      lastHostMonitorLevelState = {
        color: color,
        label: label
      };
      return lastHostMonitorLevelState;
    }
    return lastHostMonitorLevelState;
  }

  function deviceProfile(state) {
    return state.deviceProfile || {};
  }

  function monitorValue(state, key) {
    return display(monitor(state)[key]);
  }

  function numericWaveSamples(value) {
    if (!Array.isArray(value)) {
      return [];
    }
    var samples = [];
    for (var i = 0; i < value.length; i += 1) {
      var current = value[i];
      if (current === undefined || current === null || current === "") {
        continue;
      }
      var number = typeof current === "number" ? current : parseFloat(String(current));
      if (isFinite(number)) {
        samples.push(number);
      }
    }
    return samples;
  }

  function monitorSeries(state, keys) {
    var m = monitor(state);
    keys = Array.isArray(keys) ? keys : [keys];
    for (var i = 0; i < keys.length; i += 1) {
      var samples = numericWaveSamples(m[keys[i]]);
      if (samples.length) {
        return samples;
      }
    }
    return [];
  }

  function monitorHistorySeries(state, key) {
    if (key === "heartRate") {
      return monitorSeries(state, ["heartRateHistory", "ecgWaveSamples"]);
    }
    if (key === "bloodPressure") {
      return monitorSeries(state, ["bloodPressureHistory"]);
    }
    if (key === "spo2") {
      return monitorSeries(state, ["spo2History", "spo2WaveSamples"]);
    }
    if (key === "pulse") {
      return monitorSeries(state, ["pulseRateHistory", "spo2WaveSamples"]);
    }
    if (key === "bodyTemp") {
      return monitorSeries(state, ["temperatureHistory"]);
    }
    if (key === "resp") {
      return monitorSeries(state, ["respHistory", "respWaveSamples"]);
    }
    return [];
  }

  function monitorMetricDef(key) {
    return findMonitorWaveDef(key) || findMonitorWaveDef("heartRate");
  }

  function monitorMetricName(key) {
    var def = monitorMetricDef(key);
    return def ? (def.singleLabel || def.name || def.label || key) : key;
  }

  function monitorMetricUnit(key) {
    if (key === "heartRate" || key === "pulse") {
      return "bpm";
    }
    if (key === "bloodPressure") {
      return "mmHg";
    }
    if (key === "spo2") {
      return "%";
    }
    if (key === "bodyTemp") {
      return "℃";
    }
    if (key === "resp") {
      return "brpm";
    }
    return "";
  }

  function monitorMetricValue(state, key) {
    var m = monitor(state);
    if (key === "heartRate") {
      return m.heartRate;
    }
    if (key === "bloodPressure") {
      return m.bloodPressure;
    }
    if (key === "spo2") {
      return m.spo2;
    }
    if (key === "pulse") {
      return m.pulse || m.pulseRate;
    }
    if (key === "bodyTemp") {
      return m.bodyTemp;
    }
    if (key === "resp") {
      return m.resp;
    }
    return "";
  }

  function monitorHistoryKey(preferred) {
    var def = monitorMetricDef(preferred || monitorHistoryMetric || (selectedMonitorWave !== "all" ? selectedMonitorWave : "heartRate"));
    return def ? def.key : "heartRate";
  }

  function openMonitorHistory(key) {
    monitorHistoryMetric = monitorHistoryKey(key);
    monitorHistoryOpen = true;
    render();
  }

  function closeMonitorHistory() {
    if (!monitorHistoryOpen) {
      return;
    }
    monitorHistoryOpen = false;
    render();
  }

  function currentOrganizationFromNative() {
    if (!window.IcuNative || typeof window.IcuNative.firstPhaseState !== "function") {
      return {};
    }
    try {
      var state = JSON.parse(window.IcuNative.firstPhaseState() || "{}") || {};
      return state.organization || {};
    } catch (error) {
      return {};
    }
  }

  function isHospitalSettingsPage() {
    return pageName() === "lanhu_81shezhi";
  }

  function isDeviceSettingsPage() {
    return pageName() === "lanhu_82shezhi";
  }

  function isCompensationSettingsPage() {
    return pageName() === "lanhu_85buchangshezhi";
  }

  // ★ 新加：剩余 3 个设置子页判断
  function isOtherSettingsPage() {
    return pageName() === "lanhu_86qita";
  }

  function isAboutSettingsPage() {
    return pageName() === "lanhu_87guanyu";
  }

  function isAdminSettingsPage() {
    return pageName() === "lanhu_88guanliyuan";
  }

  function isSettingsFamilyPage() {
    return /^lanhu_8/.test(pageName() || "");
  }

  function setHospitalField(selector, value, placeholder) {
    var nodes = document.querySelectorAll(selector);
    var cleanValue = clean(value) || "";
    var cleanPlaceholder = clean(value) ? "" : (placeholder || "");
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (!node) {
        continue;
      }
      // 取消之前可能绑定的 organization_edit 等点击行为
      node.removeAttribute("data-native-action");
      node.removeAttribute("data-back-bound");

      // ★ 关键：setHospitalField 每 1.5s 会被定时 render 重新调用。
      //   如果每次都销毁重建 input，正在输入的焦点会被打断、输入法直接关闭。
      //   所以这里做幂等：已经有 input 就只更新值，不重建 DOM。
      var existing = node.querySelector("input[data-hospital-field='1']");
      if (existing) {
        // ★ 关键：native 端的 organization 值只在点“保存”后才更新。
        //   也就是说，用户改了字但还没点保存时，cleanValue 仍然是旧值。
        //   如果这里把 cleanValue 写回 input，刚刚改的内容就被回退了。
        //   因此：已经创建过的 input 永远不要再覆盖 value，只刷新 placeholder / title。
        existing.placeholder = cleanPlaceholder;
        existing.setAttribute("title", existing.value || cleanPlaceholder || "");
        return;
      }

      // ★ 修复：仪器状态页 .text_14 等节点 HTML 里写死了 "EM-150Vet-20" 文字，
      //   追加 input 后文字 + input 同时显示，看起来像字体错乱。这里先清空容器。
      node.innerHTML = "";

      var input = document.createElement("input");
      input.type = "text";
      input.dataset.hospitalField = "1";
      input.autocomplete = "off";
      input.spellcheck = false;
      input.setAttribute("enterkeyhint", "done");
      input.value = cleanValue;
      input.placeholder = cleanPlaceholder;
      input.style.boxSizing = "border-box";
      input.style.display = "block";
      input.style.width = "100%";
      input.style.height = "100%";
      input.style.lineHeight = "36px";
      input.style.padding = "0 16px";
      input.style.fontSize = "18px";
      input.style.color = cleanValue ? "rgba(87, 87, 87, 1)" : "rgba(141, 141, 141, 1)";
      input.style.background = "transparent";
      input.style.border = "none";
      input.style.outline = "none";
      input.style.textAlign = "left";              // ★ 左对齐
      input.style.overflow = "hidden";
      input.style.whiteSpace = "nowrap";
      input.style.textOverflow = "ellipsis";
      input.setAttribute("title", cleanValue || cleanPlaceholder || "");
      // ★ 编辑时默认全替换：聚焦后全选，用户直接覆盖输入
      //   用 setSelectionRange 替代 input.select()，避开某些 Android WebView 上
      //   select() 触发的同步重布局造成的 IME 抖动。
      var scheduleSelect = function () {
        try {
          var len = (input.value || "").length;
          if (typeof input.setSelectionRange === "function") {
            input.setSelectionRange(0, len);
          } else if (typeof input.select === "function") {
            input.select();
          }
        } catch (e) {}
      };
      input.addEventListener("focus", function () {
        // 延后一拍，等 IME 完全起来再全选，避免抢占 focus 导致键盘被回收
        window.setTimeout(scheduleSelect, 0);
      });
      // 阻止冒泡到 lanhu-bridge.js 的 fastHandle，避免被误识别为可点击项
      input.addEventListener("click", function (event) {
        if (event && event.stopPropagation) {
          event.stopPropagation();
        }
      });
      // 阻止 pointer/touch 事件被 fastHandle 截获
      ["mousedown", "touchstart", "touchend"].forEach(function (evt) {
        input.addEventListener(evt, function (event) {
          if (event && event.stopPropagation) {
            event.stopPropagation();
          }
        });
      });
      node.appendChild(input);
    }
  }

  function bindHospitalAction(selector, action, title) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].setAttribute("data-native-action", action);
      nodes[i].style.cursor = "pointer";
      nodes[i].style.touchAction = "manipulation";
      nodes[i].setAttribute("title", title || (action === "organization_edit" ? "点击编辑医院信息" : "点击切换机构"));
    }
  }

  function bindNativeAction(selector, action, title) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].setAttribute("data-native-action", action);
      nodes[i].style.cursor = "pointer";
      nodes[i].style.touchAction = "manipulation";
      if (title) {
        nodes[i].setAttribute("title", title);
      }
    }
  }

  function bindBackAction(selector, token) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (!node || node.getAttribute("data-back-bound") === token) {
        continue;
      }
      node.setAttribute("data-back-bound", token);
      node.style.cursor = "pointer";
      node.style.touchAction = "manipulation";
      node.addEventListener("click", function (event) {
        event.preventDefault();
        event.stopPropagation();
        if (window.history && typeof window.history.back === "function") {
          window.history.back();
        }
      }, true);
    }
  }

  function bindNavigateAction(selector, href, token) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (!node || node.getAttribute("data-nav-bound") === token) {
        continue;
      }
      node.setAttribute("data-nav-bound", token);
      node.style.cursor = "pointer";
      node.style.touchAction = "manipulation";
      node.addEventListener("click", function (event) {
        event.preventDefault();
        event.stopPropagation();
        window.location.href = href;
      }, true);
    }
  }

  function setSettingsField(selector, value, placeholder, options) {
    var nodes = document.querySelectorAll(selector);
    options = options || {};
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (!node) {
        continue;
      }
      node.innerHTML = "";
      var span = document.createElement("span");
      var text = clean(value) || placeholder || "";
      span.textContent = text;
      span.style.display = "block";
      span.style.boxSizing = "border-box";
      span.style.width = "100%";
      span.style.height = "100%";
      // ★ 用 setProperty + !important 防止 lanhu-fit.css 的 .page span { line-height: 1.18 !important } 压回
      span.style.setProperty("line-height", (options.lineHeight || 36) + "px", "important");
      span.style.setProperty("padding", "0 16px", "important");
      span.style.setProperty("text-align", "left", "important");
      span.style.overflow = "hidden";
      span.style.whiteSpace = "nowrap";
      span.style.textOverflow = "ellipsis";
      span.style.color = clean(value) ? "rgba(87, 87, 87, 1)" : "rgba(141, 141, 141, 1)";
      span.style.fontSize = (options.fontSize || 18) + "px";
      node.appendChild(span);
      node.setAttribute("title", text);
    }
  }

  function numericSetting(value, fallback) {
    var raw = clean(value);
    if (!raw) {
      return fallback;
    }
    var match = raw.match(/-?\d+(?:\.\d+)?/);
    return match ? parseFloat(match[0]) : fallback;
  }

  // 治疗时长现在是自动正计时的时钟串 "HH:MM:SS"，补偿设置页需要的是分钟数。
  // 直接 numericSetting 会把 "01:23:45" 解析成 1，这里先把时钟换成总分钟。
  function treatmentClockToMinutes(value, fallback) {
    var raw = clean(value);
    var m = raw.match(/^(\d{1,3}):(\d{2}):(\d{2})$/);
    if (m) {
      return Number(m[1]) * 60 + Number(m[2]);
    }
    return numericSetting(raw, fallback);
  }

  function formatNumericSetting(value, digits, fallbackText) {
    if (!isFinite(value)) {
      return fallbackText || empty;
    }
    if (digits <= 0) {
      return String(Math.round(value));
    }
    return value.toFixed(digits);
  }

  function setBarFill(selector, value, min, max, minWidth, maxWidth) {
    var node = document.querySelector(selector);
    if (!node || !isFinite(value)) {
      return;
    }
    var ratio = (value - min) / (max - min);
    if (!isFinite(ratio)) {
      ratio = 0;
    }
    ratio = Math.max(0, Math.min(1, ratio));
    var width = minWidth + (maxWidth - minWidth) * ratio;
    node.style.width = Math.round(width) + "px";
  }

  function normalizeDeadText() {
    var spans = document.querySelectorAll("span,div");
    for (var i = 0; i < spans.length; i += 1) {
      var node = spans[i];
      if (node.children && node.children.length) {
        continue;
      }
      var text = (node.textContent || "").replace(/\s+/g, " ").trim();
      if (/^X+$/.test(text) || isDemoValue(text)) {
        node.textContent = empty;
      } else if (/^(宠物名：|动物名称：)(饭团|咪咪)/.test(text)) {
        node.textContent = text.replace(/(饭团|咪咪).*/, empty);
      } else if (/2026[-/](05|06)/.test(text) || /0773-0000/.test(text) || /012\s*3456\s*7891/.test(text)) {
        node.textContent = empty;
      } else if (text.indexOf("瑞鹏宠物医院") >= 0 || text.indexOf("桂林优利特宠物医院") >= 0 || text.indexOf("广东省深圳市龙岗区") >= 0) {
        node.textContent = empty;
      }
    }
  }

  function renderCommon(state) {
    var organization = org(state);
    var p = patient(state);
    var orgFallbackEnabled = !isSettingsFamilyPage();
    setPreferredText("[data-org-live='name']", orgFallbackEnabled ? ".text_6" : "", organization.name);
    setPreferredText("[data-org-live='address']", orgFallbackEnabled ? ".text_7" : "", organization.address);
    setPreferredText("[data-account-live='name']", ".text_4", state.accountName || "");
    renderOrganizationLogo(clean(organization.logoUri));
    showIfAnyText("[data-org-live],[data-account-live]");

    if (!isSettingsFamilyPage() && !document.querySelector("[data-patient-live]")) {
      setText(".text_10", p.petName, "治疗记录：");
      setText(".text_11", (clean(p.species) || clean(p.ownerName)) ? "种类：" + display(p.species) + "  主人：" + display(p.ownerName) : "");
      setText(".text_12", p.caseNo || p.recordNo, "编号：");
      setText(".text_13", toChineseDateTime(p.visitDate));
      setText(".text-group_1", p.petName, "治疗记录：");
      setText(".text_14", (clean(p.species) || clean(p.ownerName)) ? "种类：" + display(p.species) + "  主人：" + display(p.ownerName) : "");
      setText(".text_15", p.recordNo || p.caseNo, "编号：");
      setText(".text_16", toChineseDateTime(p.visitDate));
      setText(".text_35", p.petName);
      setText(".text_36", p.species);
      setText(".text_39", p.age);
      setText(".text_40", p.recordNo || p.caseNo);
      setText(".text_43", p.ownerName);
      setText(".text_44", p.ownerPhone);
      setText(".text_47", p.doctor);
      setText(".text_48", toChineseDateTime(p.visitDate));
      setText(".paragraph_1", p.note);
    }

    var page = pageName();
    if (!isSettingsFamilyPage() &&
      page !== "lanhu_1icuzhuangtaikaobei" &&
      page !== "lanhu_2zhujikongzhi" &&
      page !== "lanhu_5jiankonghuifang") {
      var staleRows = ".group_30,.group_31,.group_32,.group_33,.group_34,.box_9,.box_10,.box_11,.box_12,.box_13,.box_41,.box_43,.box_44,.box_45,.text_14,.text_15,.text_16,.text_17,.text_18,.text_19,.text_20,.text_21,.text_22,.text_23,.text_24,.text_25,.text_26,.text_27,.text_28,.text_29,.text_30,.text_31,.text_32";
      if (page === "lanhu_4jiankong") {
        staleRows += ",.box_37,.box_38,.box_39,.box_40";
      }
      hide(staleRows);
    }
  }

  function renderOrganizationLogo(logoUri) {
    var slots = Array.prototype.slice.call(document.querySelectorAll("[data-org-logo-slot]"));
    var mainLogoPages = {
      lanhu_1icuzhuangtaikaobei: true,
      lanhu_2zhujikongzhi: true,
      lanhu_3shishijianhu: true,
      lanhu_4jiankong: true,
      lanhu_6zhiliaojilu: true,
      lanhu_7jiaocheng: true
    };
    if (mainLogoPages[pageName()]) {
      var fallbackSlot = document.querySelector(".text-wrapper_1");
      if (fallbackSlot && slots.indexOf(fallbackSlot) < 0) slots.push(fallbackSlot);
    }
    for (var i = 0; i < slots.length; i += 1) {
      (function (slot) {
        var text = slot.querySelector("[data-org-logo-text]") || slot.querySelector(".text_5");
        if (slot.__icuLogoUri === logoUri && slot.__icuLogoState === "loaded") return;
        var token = (slot.__icuLogoToken || 0) + 1;
        slot.__icuLogoToken = token;
        slot.__icuLogoUri = logoUri || "";
        function restorePlaceholder() {
          slot.style.backgroundImage = "";
          slot.style.backgroundSize = "";
          slot.style.backgroundPosition = "";
          slot.style.backgroundRepeat = "";
          if (text) text.style.opacity = "1";
        }
        restorePlaceholder();
        if (!logoUri) return;
        var image = new Image();
        image.onload = function () {
          if (slot.__icuLogoToken !== token) return;
          slot.style.backgroundImage = 'url("' + logoUri.replace(/"/g, "%22") + '")';
          slot.style.backgroundSize = "cover";
          slot.style.backgroundPosition = "center";
          slot.style.backgroundRepeat = "no-repeat";
          if (text) text.style.opacity = "0";
          slot.__icuLogoState = "loaded";
        };
        image.onerror = function () {
          if (slot.__icuLogoToken === token) {
            slot.__icuLogoState = "error";
            restorePlaceholder();
          }
        };
        image.src = logoUri;
      })(slots[i]);
    }
  }

  function renderHospitalSettingsPage() {
    if (!isHospitalSettingsPage()) {
      return;
    }
    var organization = currentOrganizationFromNative();
    // ★ LOGO 上传框用 flex 居中 +!important,防止 lanhu-fit.css 覆盖
    var logoSlot = document.querySelector(".text-wrapper_2");
    if (logoSlot) {
      logoSlot.style.setProperty("display", "flex", "important");
      logoSlot.style.setProperty("align-items", "center", "important");
      logoSlot.style.setProperty("justify-content", "center", "important");
      var plus = logoSlot.querySelector(".text_17");
      if (plus) {
        plus.style.setProperty("text-align", "center", "important");
        plus.style.setProperty("margin", "0", "important");
      }
    }
    setHospitalField(".group_4", organization.name, "未设置医院");
    setHospitalField(".group_5", organization.address, "未设置地址");
    setHospitalField(".group_6", organization.phone, "未设置电话");
    renderOrganizationLogo(clean(organization.logoUri));
    // ★ LOGO 行垂直居中：原 CSS 中 section_4 的 LOGO 框（150×150）落在行顶部，
    //   下方留白。让 section_4 的子项在交叉轴居中即可。
    var logoRow = document.querySelector(".section_4");
    if (logoRow && logoRow.style.alignItems !== "center") {
      logoRow.style.alignItems = "center";
    }
    // 医院信息字段改为页面内直接编辑（见 setHospitalField），不再触发 organization_edit 弹窗
    // “保存”按钮：把当前三个输入框值一次性写入 native
    bindNativeAction(".text-wrapper_3", "organization_save", "点击保存医院信息");
    bindNativeAction(".text-wrapper_2", "organization_logo_pick", "点击上传医院 Logo");
    bindBackAction(".text-wrapper_4", "hospital-back");
  }

  function restoreSettingsMenuLabels() {
    if (!isSettingsFamilyPage()) {
      return;
    }
    setText(".text_10", "其他设置");
    setText(".text_11", "关于");
    setText(".text_12", "管理员设置");
  }

  function renderDeviceSettingsPage(state) {
    if (!isDeviceSettingsPage()) {
      return;
    }
    // ★ 让菜单栏高度自适应,菜单项紧凑排列在顶部,不再有底部大空白
    var sidebar = document.querySelector(".group_2");
    if (sidebar) {
      sidebar.style.setProperty("height", "auto", "important");
      sidebar.style.setProperty("min-height", "auto", "important");
      sidebar.style.setProperty("justify-content", "flex-start", "important");
      sidebar.style.setProperty("padding-bottom", "20px", "important");
    }
    var profile = deviceProfile(state);
    var hostInfo = host(state);
    var monitorInfo = monitor(state);
    // ★ 改成 inline edit：5 个字段全部用 input，仿照 setHospitalField
    setHospitalField(".text_14",   clean(profile.productModel)    || clean(hostInfo.deviceName) || "EM-150Vet-20", "未登记");
    setHospitalField(".box_3",     clean(profile.machineType)     || clean(hostInfo.deviceName) || "ICU主机", "未登记");
    // ★ P2-问题17 修复:机号只显示 SN,不再兜底成蓝牙物理地址(MAC)。
    //   原来 SN 未登记时会把 hostInfo.deviceId(形如 AA:BB:CC:DD:EE:FF)顶上来,
    //   就是用户反馈的"机号用物理地址显示,太复杂"。
    //   该字段是 inline input,保存走 device_profile_save → webUpdateDeviceProfile,
    //   未登记时显示占位"未登记",提示录入真实 SN。
    //   MAC 仍可在设备列表和 PDF 的"设备ID"处查看,信息没有丢失。
    setHospitalField(".box_5",     clean(profile.serialNo), "未登记");
    setHospitalField(".section_2", clean(profile.manufactureDate) || "", "未登记");
    setHospitalField(".section_3", clean(profile.softwareVersion) || clean(monitorInfo.deviceName) || clean(monitorInfo.state), "未登记");
    // ★ "保存" 走新的 device_profile_save（native 端 evaluateJavascript 读 5 个 input）
    bindNativeAction(".text-wrapper_3", "device_profile_save", "点击保存仪器信息");
    bindBackAction(".text-wrapper_4", "device-back");
    // ★ 修复：5 个 input 容器不再绑 device_profile_edit —— 之前绑定会让 fastHandle
    //   顺着 DOM 往上走到 .text_14 时找到 data-native-action="device_profile_edit",
    //   触发原生弹窗,inline edit 就失效了。现在只走底部"保存"按钮。
    // 标题 → 左对齐并与右侧文本框垂直居中：
    // 之前 text-align:center + 保留 margin-top 会让两字标签右移且文字
    // 比文本框中心低约 8px（与连接状态页不一致）。
    ["text_13", "text_15", "text_16", "text_17", "text_18"].forEach(function (cls) {
      var n = document.querySelector("." + cls);
      if (!n) return;
      n.style.setProperty("text-align", "left", "important");
      n.style.setProperty("line-height", "36px", "important");
      n.style.setProperty("height", "36px", "important");
      n.style.setProperty("margin-top", "0", "important");
    });
  }

  /**
   * ★ 仿照 setHospitalField，把空 div 改造成 <textarea data-about-field="1">。
   *   用于"关于"页单字段编辑。幂等、聚焦全选、不打断 IME。
   */
  function setAboutField(selector, value, placeholder) {
    var nodes = document.querySelectorAll(selector);
    var cleanValue = clean(value) || "";
    var cleanPlaceholder = clean(value) ? "" : (placeholder || "");
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (!node) continue;
      node.removeAttribute("data-native-action");
      node.removeAttribute("data-back-bound");
      var existing = node.querySelector("textarea[data-about-field='1']");
      if (existing) {
        existing.placeholder = cleanPlaceholder;
        existing.setAttribute("title", existing.value || cleanPlaceholder || "");
        return;
      }
      // ★ 修复：HTML 里写死的 "XXXXXXXXX" 占位文字需清空,否则会和 textarea 同时显示
      node.innerHTML = "";
      var ta = document.createElement("textarea");
      ta.dataset.aboutField = "1";
      ta.autocomplete = "off";
      ta.spellcheck = false;
      ta.value = cleanValue;
      ta.placeholder = cleanPlaceholder;
      ta.style.boxSizing = "border-box";
      ta.style.display = "block";
      ta.style.width = "100%";
      ta.style.minHeight = "100%";
      ta.style.padding = "8px 16px";
      ta.style.fontSize = "18px";
      ta.style.color = cleanValue ? "rgba(87, 87, 87, 1)" : "rgba(141, 141, 141, 1)";
      ta.style.background = "transparent";
      ta.style.border = "none";
      ta.style.outline = "none";
      ta.style.textAlign = "left";
      ta.style.resize = "none";
      ta.style.fontFamily = "SourceHanSansCN-Regular, sans-serif";
      ta.style.lineHeight = "1.6";
      ta.setAttribute("title", cleanValue || cleanPlaceholder || "");
      // 阻止冒泡避免被 fastHandle 误识别
      ["click", "mousedown", "touchstart", "touchend"].forEach(function (evt) {
        ta.addEventListener(evt, function (event) {
          if (event && event.stopPropagation) event.stopPropagation();
        });
      });
      node.appendChild(ta);
    }
  }

  /**
   * ★ 新加：仪器状态页以外的 4 个 input 类（用户名 / 密码 / 升级文件名 / 补偿值）
   *   共享的工具函数。input 上挂 data-field="..." 属性，JS 后续通过 selector 读值。
   *   仿 setHospitalField 保持幂等、不打断 IME。
   */
  function setFormInput(selector, value, placeholder, fieldKey) {
    var nodes = document.querySelectorAll(selector);
    var cleanValue = clean(value) || "";
    var cleanPlaceholder = clean(value) ? "" : (placeholder || "");
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (!node) continue;
      node.removeAttribute("data-native-action");
      node.removeAttribute("data-back-bound");
      var existing = node.querySelector("input[data-form-field='1']");
      if (existing) {
        existing.placeholder = cleanPlaceholder;
        existing.setAttribute("title", existing.value || cleanPlaceholder || "");
        return;
      }
      // ★ 修复：清空容器内可能存在的占位文本（HTML 里有的节点写了"XXXXXXXXX"等）
      node.innerHTML = "";
      var input = document.createElement("input");
      input.type = (fieldKey === "password") ? "password" : "text";
      input.dataset.formField = "1";
      input.dataset.fieldKey = fieldKey || "";
      input.autocomplete = "off";
      input.spellcheck = false;
      input.setAttribute("enterkeyhint", "done");
      input.value = cleanValue;
      input.placeholder = cleanPlaceholder;
      input.style.boxSizing = "border-box";
      input.style.display = "block";
      input.style.width = "100%";
      input.style.height = "100%";
      input.style.lineHeight = "36px";
      input.style.padding = "0 16px";
      input.style.fontSize = "18px";
      input.style.color = cleanValue ? "rgba(87, 87, 87, 1)" : "rgba(141, 141, 141, 1)";
      input.style.background = "transparent";
      input.style.border = "none";
      input.style.outline = "none";
      input.style.textAlign = "left";
      input.style.overflow = "hidden";
      input.style.whiteSpace = "nowrap";
      input.style.textOverflow = "ellipsis";
      input.setAttribute("title", cleanValue || cleanPlaceholder || "");
      var scheduleSelect = function () {
        try {
          var len = (input.value || "").length;
          if (typeof input.setSelectionRange === "function") {
            input.setSelectionRange(0, len);
          } else if (typeof input.select === "function") {
            input.select();
          }
        } catch (e) {}
      };
      input.addEventListener("focus", function () {
        window.setTimeout(scheduleSelect, 0);
      });
      ["click", "mousedown", "touchstart", "touchend"].forEach(function (evt) {
        input.addEventListener(evt, function (event) {
          if (event && event.stopPropagation) event.stopPropagation();
        });
      });
      node.appendChild(input);
    }
  }

  /**
   * ★ 新加：其他设置页（lanhu_86qita）。A1~A4 升级文件路径输入。
   *   A1/A2 走 BLE upgrade_main/upgrade_ctrl，A3/A4 占位 → Toast 提示。
   */
  function renderOtherSettingsPage(state) {
    if (!isOtherSettingsPage()) {
      return;
    }
    // 4 个 input：text-wrapper_2 (A1) / box_4 (A2) / group_5 (A3) / group_7 (A4)
    setFormInput(".text-wrapper_2", "", "升级主控文件名(例:firmware_v1.bin)", "a1");
    setFormInput(".box_4",         "", "升级控制板文件名",                  "a2");
    setFormInput(".group_5",       "", "A3 占位（暂未启用）",                "a3");
    setFormInput(".group_7",       "", "A4 占位（暂未启用）",                "a4");
    bindNativeAction(".text-wrapper_3", "other_settings_save", "保存升级配置");
    bindBackAction(".text-wrapper_4", "other-back");
  }

  /**
   * ★ 新加：关于页（lanhu_87guanyu）。单 textarea inline 编辑。
   */
  function renderAboutSettingsPage(state) {
    if (!isAboutSettingsPage()) {
      return;
    }
    var aboutText = (state && state.about && state.about.text) || "";
    setAboutField(".text-wrapper_2", aboutText, "在此输入关于本系统的说明");
    bindNativeAction(".text-wrapper_3", "about_save", "保存关于");
    bindBackAction(".text-wrapper_4", "about-back");
  }

  /**
   * ★ 新加：管理员设置页（lanhu_88guanliyuan）。用户名 + 密码登录。
   */
  function renderAdminSettingsPage(state) {
    if (!isAdminSettingsPage()) {
      return;
    }
    setFormInput(".group_2", "", "管理员账户", "name");
    setFormInput(".box_7",   "", "密码",       "password");
    bindNativeAction(".text-wrapper_2", "admin_login", "登录管理员");
    bindBackAction(".text-wrapper_3", "admin-back");
  }

  /**
   * ★ 历史函数,已被 setHospitalField 替代,保留注释占位以防误删
   *   旧逻辑:对 .text_14 这种 HTML 里硬编码的 span,直接把样式打到该 span 上,
   *   把 span 伪装成 input 视觉效果。现在直接用真的 <input>,不再需要。
   */
  // function applyDeviceInputStyle(selector, options) { ... }  // 已删除

  function renderCompensationSettingsPage(state) {
    if (!isCompensationSettingsPage()) {
      return;
    }
    var controls = hostControls(state);
    var hostInfo = host(state);
    var temp = numericSetting(controls.temp, numericSetting(hostInfo.temp, 39.5));
    var oxygen = numericSetting(controls.oxygen, numericSetting(hostInfo.oxygen, 36.6));
    var humidity = numericSetting(controls.humidity, numericSetting(hostInfo.humidity, 45.9));
    var co2 = numericSetting(controls.co2, numericSetting(hostInfo.co2, 1000));
    var treatmentMinutes = treatmentClockToMinutes(controls.treatmentTime, treatmentClockToMinutes(hostInfo.treatmentTime, 0));

    // ★ JS 主动设置所有标题，确保标题不丢失（用户反馈"只有数值没有标题"）
    setText(".text_13", "温度补偿  ℃");
    setText(".text_15", "氧浓度补偿  %");
    setText(".text_17", "湿度补偿  %");
    setText(".text_19", "二氧化碳浓度补偿  PPM");
    setText(".text_21", "红外体温  ℃");
    setText(".text_24", "保存");
    setText(".text_25", "返回");

    setText(".text_14", formatNumericSetting(temp, 1, "39.5"));
    setText(".text_16", formatNumericSetting(oxygen, 1, "36.6"));
    setText(".text_18", formatNumericSetting(humidity, 1, "45.9"));
    setText(".text_20", formatNumericSetting(co2, 0, "1000"));
    setText(".text_22", formatNumericSetting(treatmentMinutes, 0, "32"));

    setBarFill(".block_6", temp, 20, 45, 30, 227);
    setBarFill(".group_4", oxygen, 0, 100, 30, 227);
    setBarFill(".block_9", humidity, 0, 100, 30, 227);
    setBarFill(".block_13", co2, 0, 2000, 30, 227);
    setBarFill(".box_7", treatmentMinutes, 0, 180, 30, 227);

    bindNativeAction(".block_3,.thumbnail_4",  "compensation_edit_temp",      "点击设置温度补偿");
    bindNativeAction(".block_7,.thumbnail_6",  "compensation_edit_oxygen",    "点击设置氧浓度补偿");
    bindNativeAction(".block_8,.thumbnail_8",  "compensation_edit_humidity",  "点击设置湿度补充");
    bindNativeAction(".box_5,.thumbnail_10",   "compensation_edit_co2",       "点击设置CO2浓度补偿");
    bindNativeAction(".box_6,.thumbnail_12",   "compensation_edit_infrared",  "点击设置红外体温补偿");
    bindNativeAction(".text-wrapper_2",        "compensation_add",            "点击添加补偿项（占位）");
    bindNativeAction(".text-wrapper_3",        "compensation_save",           "点击保存补偿设置");
    bindBackAction(".text-wrapper_4", "compensation-back");
  }

  function renderStatusAdvice(state) {
    if (pageName() !== "lanhu_1icuzhuangtaikaobei") {
      return;
    }
    var box = document.querySelector(".text-wrapper_25");
    if (!box) {
      return;
    }
    box.setAttribute("data-native-action", "patient_edit");
    box.style.cursor = "pointer";
    var content = box.querySelector(".icu-status-advice");
    if (!content) {
      content = document.createElement("div");
      content.className = "icu-status-advice";
      content.setAttribute("data-native-action", "patient_edit");
      box.appendChild(content);
    }
    var p = patient(state);
    content.textContent = display(p.note);
    content.setAttribute("title", clean(p.note) ? p.note : "点击编辑医嘱");
    // 排版(字号/行高/内边距/定位)统一交给 lanhu-fit.css 的
    // .page.icu-page-status .icu-status-advice,避免这里的行内样式与初稿对不上。
  }

  function hideReportTemplateNavigation() {
    var nodes = document.querySelectorAll("span,div");
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      if (node.children && node.children.length) {
        continue;
      }
      var text = (node.textContent || "").replace(/\s+/g, "").trim();
      if (text === "报告模版-默认" || text === "报告模板-默认") {
        node.style.display = "none";
        node.style.visibility = "hidden";
      }
    }
  }

  function setWaveVisible(selector, visible) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].style.visibility = visible ? "" : "hidden";
    }
  }

  function setDisplay(selector, visible) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].style.display = visible ? "" : "none";
    }
  }

  function applyMonitorLayoutStyles(selectors, styles) {
    var nodes = document.querySelectorAll(selectors);
    for (var i = 0; i < nodes.length; i += 1) {
      var node = nodes[i];
      for (var key in styles) {
        if (Object.prototype.hasOwnProperty.call(styles, key)) {
          node.style[key] = styles[key];
        }
      }
    }
  }

  function resetMonitorSingleWaveLayout() {
    applyMonitorLayoutStyles(".section_5", {
      display: "",
      flexDirection: "",
      boxSizing: "",
      paddingBottom: ""
    });
    applyMonitorLayoutStyles(".group_41", {
      flex: "",
      margin: ""
    });
    applyMonitorLayoutStyles(".block_9,.block_10,.block_13", {
      flex: "",
      height: "",
      minHeight: "",
      display: "",
      alignItems: "",
      marginBottom: ""
    });
    applyMonitorLayoutStyles(".group_37,.group_39,.box_30", {
      flex: "",
      width: "",
      height: "",
      minHeight: "",
      margin: "",
      overflow: "",
      display: "",
      flexDirection: ""
    });
    applyMonitorLayoutStyles(".group_38,.group_40,.box_31", {
      flex: "",
      height: "",
      marginLeft: ""
    });
    applyMonitorLayoutStyles(".box_7,.box_8,.block_11,.block_12,.box_13,.box_14", {
      flex: "",
      height: ""
    });
    applyMonitorLayoutStyles(".icu-wave", {
      flex: "",
      width: "",
      height: "",
      minHeight: "",
      marginTop: ""
    });
  }

  function applyMonitorSingleWaveLayout(rowSelector, waveSelector) {
    var row = document.querySelector(rowSelector);
    if (!row) {
      return;
    }
    applyMonitorLayoutStyles(".section_5", {
      display: "flex",
      flexDirection: "column",
      boxSizing: "border-box",
      paddingBottom: "0"
    });
    applyMonitorLayoutStyles(".group_41", {
      flex: "0 0 auto",
      margin: "18px 0 11px 182px"
    });
    applyMonitorLayoutStyles(rowSelector, {
      flex: "1 1 auto",
      height: "auto",
      minHeight: "0",
      display: "flex",
      alignItems: "stretch",
      marginBottom: "0"
    });
    applyMonitorLayoutStyles(waveSelector, {
      flex: "1 1 auto",
      width: "auto",
      height: "auto",
      minHeight: "0",
      margin: "14px 0 14px 13px",
      overflow: "hidden",
      display: "flex",
      flexDirection: "column"
    });
    var metricColumn = row.querySelector(".group_38, .group_40, .box_31");
    if (metricColumn) {
      metricColumn.style.flex = "0 0 438px";
      metricColumn.style.height = "auto";
      metricColumn.style.marginLeft = "14px";
    }
    var metricCards = row.querySelectorAll(".box_7,.box_8,.block_11,.block_12,.box_13,.box_14");
    for (var i = 0; i < metricCards.length; i += 1) {
      metricCards[i].style.flex = "1 1 0";
      metricCards[i].style.height = "auto";
    }
  }

  function setMonitorSingleWaveMode(enabled, rowSelector, waveSelector) {
    resetMonitorSingleWaveLayout();
    var page = document.querySelector(".section_5");
    if (page) {
      page.classList.toggle("icu-monitor-single-wave", !!enabled);
    }
    if (enabled && rowSelector && waveSelector) {
      applyMonitorSingleWaveLayout(rowSelector, waveSelector);
    }
  }

  function clearWave(selector) {
    var target = document.querySelector(selector);
    if (!target) {
      return;
    }
    var placeholders = target.querySelectorAll("img");
    var old = target.querySelector(".icu-wave");
    if (old) {
      old.remove();
    }
    for (var i = 0; i < placeholders.length; i += 1) {
      placeholders[i].style.display = "";
    }
    target.style.justifyContent = "";
    target.classList.remove("icu-wave-empty");
    target.removeAttribute("data-empty-label");
  }

  function monitorWaveDefs() {
    return [
      { key: "heartRate", label: "ECG", singleLabel: "\u5fc3\u7387", name: "\u5fc3\u7387", row: ".block_9", wave: ".group_37", title: ".text_60", card: ".box_7", color: "#4ee070", type: 0, seriesKeys: ["ecgWaveSamples", "heartRateHistory"] },
      { key: "bloodPressure", label: "NIBP", singleLabel: "\u8840\u538b", name: "\u8840\u538b", row: ".block_9", wave: ".group_37", title: ".text_60", card: ".box_8", color: "#ffd166", type: 0, seriesKeys: ["bloodPressureHistory"] },
      { key: "spo2", label: "PLETH", singleLabel: "\u8840\u6c27", name: "\u8840\u6c27", row: ".block_10", wave: ".group_39", title: ".text_68", card: ".block_11", color: "#ee635d", type: 1, seriesKeys: ["spo2WaveSamples", "spo2History"] },
      { key: "pulse", label: "PR", singleLabel: "\u8109\u7387", name: "\u8109\u7387", row: ".block_10", wave: ".group_39", title: ".text_68", card: ".block_12", color: "#ff8f5a", type: 1, seriesKeys: ["pulseRateHistory"] },
      { key: "bodyTemp", label: "TEMP", singleLabel: "\u4f53\u6e29", name: "\u4f53\u6e29", row: ".block_13", wave: ".box_30", title: ".text_75", card: ".box_13", color: "#f7c948", type: 2, seriesKeys: ["temperatureHistory"] },
      { key: "resp", label: "RESP", singleLabel: "\u547c\u5438\u7387", name: "\u547c\u5438\u7387", row: ".block_13", wave: ".box_30", title: ".text_75", card: ".box_14", color: "#5ea9f3", type: 2, seriesKeys: ["respWaveSamples", "respHistory"] }
    ];
  }

  function monitorWaveRows() {
    return [".block_9", ".block_10", ".block_13"];
  }

  function monitorWaveTargets() {
    return [".group_37", ".group_39", ".box_30"];
  }

  function setMonitorWaveTitle(selector, text, color) {
    var node = document.querySelector(selector);
    if (!node) {
      return;
    }
    node.textContent = text;
    node.style.color = color || "";
  }

  function resetMonitorWaveTitles() {
    setMonitorWaveTitle(".text_60", "ECG", "");
    setMonitorWaveTitle(".text_68", "PLETH", "");
    setMonitorWaveTitle(".text_75", "RESP", "");
  }

  function showOnlyMonitorWaveRow(rowSelector) {
    var rows = monitorWaveRows();
    for (var i = 0; i < rows.length; i += 1) {
      setDisplay(rows[i], rows[i] === rowSelector);
    }
  }

  function showAllMonitorWaveRows() {
    var rows = monitorWaveRows();
    for (var i = 0; i < rows.length; i += 1) {
      setDisplay(rows[i], true);
    }
  }

  function clearOtherMonitorWaves(activeSelector) {
    var targets = monitorWaveTargets();
    for (var i = 0; i < targets.length; i += 1) {
      if (targets[i] !== activeSelector) {
        clearWave(targets[i]);
      }
    }
  }

  function updateMonitorMetricSelection() {
    var defs = monitorWaveDefs();
    for (var i = 0; i < defs.length; i += 1) {
      var nodes = document.querySelectorAll(defs[i].card);
      for (var j = 0; j < nodes.length; j += 1) {
        nodes[j].classList.toggle("icu-monitor-card-active", selectedMonitorWave === defs[i].key);
      }
    }
  }

  function findMonitorWaveDef(key) {
    var defs = monitorWaveDefs();
    for (var i = 0; i < defs.length; i += 1) {
      if (defs[i].key === key) {
        return defs[i];
      }
    }
    return null;
  }

  function setSelectedMonitorWave(key) {
    key = key || "all";
    selectedMonitorWave = selectedMonitorWave === key ? "all" : key;
    render();
  }

  function ensureMonitorFilter() {
    var page = document.querySelector(".section_5");
    if (!page) {
      return;
    }
    var bar = page.querySelector(".icu-monitor-filter");
    if (!bar) {
      bar = document.createElement("div");
      bar.className = "icu-monitor-filter";
      page.appendChild(bar);
    }
    var defs = [{ key: "all", name: "\u5168\u90e8" }].concat(monitorWaveDefs());
    if (bar.getAttribute("data-ready") !== "1") {
      var html = [];
      for (var i = 0; i < defs.length; i += 1) {
        html.push('<button type="button" data-wave="' + defs[i].key + '">' + defs[i].name + '</button>');
      }
      bar.innerHTML = html.join("");
      bar.setAttribute("data-ready", "1");
      bar.addEventListener("click", function (event) {
        var target = event.target;
        if (!target || target.tagName !== "BUTTON") {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        setSelectedMonitorWave(target.getAttribute("data-wave") || "all");
      }, true);
    }
    var buttons = bar.querySelectorAll("button");
    for (var j = 0; j < buttons.length; j += 1) {
      var active = buttons[j].getAttribute("data-wave") === selectedMonitorWave;
      buttons[j].classList.toggle("active", active);
    }
  }

  function ensureMonitorMetricClicks() {
    var defs = monitorWaveDefs();
    for (var i = 0; i < defs.length; i += 1) {
      (function (def) {
        var nodes = document.querySelectorAll(def.card);
        for (var j = 0; j < nodes.length; j += 1) {
          if (nodes[j].getAttribute("data-icu-wave-click") === "1") {
            continue;
          }
          nodes[j].setAttribute("data-icu-wave-click", "1");
          nodes[j].style.cursor = "pointer";
          nodes[j].addEventListener("click", function (event) {
            event.preventDefault();
            event.stopPropagation();
            setSelectedMonitorWave(def.key);
          }, true);
        }
      })(defs[i]);
    }
  }

  function renderMonitorWaves(state) {
    updateMonitorMetricSelection();
    if (selectedMonitorWave === "all") {
      setMonitorSingleWaveMode(false);
      showAllMonitorWaveRows();
      resetMonitorWaveTitles();
      setWaveVisible(".group_37", true);
      setWaveVisible(".group_39", true);
      setWaveVisible(".box_30", true);
      installWave(".group_37", "ECG", monitorSeries(state, ["ecgWaveSamples", "heartRateHistory"]), "#4ee070", 0, { key: "heartRate" });
      installWave(".group_39", "PLETH", monitorSeries(state, ["spo2WaveSamples", "spo2History"]), "#ee635d", 1, { key: "spo2" });
      installWave(".box_30", "RESP", monitorSeries(state, ["respWaveSamples", "respHistory"]), "#5ea9f3", 2, { key: "resp" });
      return;
    }
    var def = findMonitorWaveDef(selectedMonitorWave);
    if (!def) {
      selectedMonitorWave = "all";
      renderMonitorWaves(state);
      return;
    }
    setMonitorSingleWaveMode(true, def.row, def.wave);
    showOnlyMonitorWaveRow(def.row);
    resetMonitorWaveTitles();
    clearOtherMonitorWaves(def.wave);
    setWaveVisible(def.wave, true);
    setMonitorWaveTitle(def.title, def.singleLabel || def.name || def.label, def.color);
    installWave(def.wave, def.singleLabel || def.label, monitorSeries(state, def.seriesKeys), def.color, def.type, { key: def.key });
  }

  function renderMonitor(state) {
    if (pageName() !== "lanhu_3shishijianhu") {
      return;
    }
    lastMonitorState = state || {};
    // ★ 点击"离开"按钮 → 断开监护蓝牙,留在本页
    bindNativeAction(".image-text_18", "monitor_ble_leave", "点击断开监护蓝牙");
    cleanupMonitorOverlays();
    ensureMonitorMetricClicks();
    show(".box_13,.box_14");
    setText(".text_62", monitorValue(state, "heartRate"));
    setText(".text_63", "bpm");
    setText(".text_65", monitorValue(state, "bloodPressure"), "平均压：");
    setText(".text_66", monitorValue(state, "bloodPressure"));
    setText(".text_70", monitorValue(state, "spo2"));
    setText(".text_73", monitorValue(state, "pulse"));
    setText(".text_77", monitorValue(state, "bodyTemp"));
    setText(".text_80", monitorValue(state, "resp"));
    setText(".text-group_2", monitor(state).connected ? "已连接" : (monitor(state).scanning ? "扫描中" : "蓝牙"));
    setText(".text-group_3", monitor(state).connected ? (monitor(state).deviceName || monitor(state).deviceId) : "");
    renderMonitorWaves(state);
    ensureMonitorHistoryOverlay(state);
    styleMonitorAlarmText(state);
  }

  // "报警音" 文字颜色：自动报警开启=蓝色，关闭=黑色
  function styleMonitorAlarmText(state) {
    var settings = state.monitorSettings || {};
    var enabled = !!settings.alarmEnabled;
    var node = document.querySelector(".text-group_4");
    if (!node) {
      return;
    }
    node.style.color = enabled ? "rgba(0, 116, 255, 1)" : "rgba(0, 0, 0, 1)";
  }

  function ensureMonitorHistoryOverlay(state) {
    var page = document.querySelector(".section_5");
    if (!page) {
      return;
    }
    var overlay = page.querySelector(".icu-monitor-history-modal");
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.className = "icu-monitor-history-modal";
      page.appendChild(overlay);
    }
    if (!monitorHistoryOpen) {
      overlay.classList.remove("open");
      overlay.innerHTML = "";
      return;
    }
    overlay.classList.add("open");
    var key = monitorHistoryKey();
    var def = monitorMetricDef(key);
    var samples = monitorHistorySeries(state, key);
    var currentValue = display(monitorMetricValue(state, key));
    var unit = monitorMetricUnit(key);
    var min = samples.length ? Math.min.apply(Math, samples) : null;
    var max = samples.length ? Math.max.apply(Math, samples) : null;
    var latest = samples.length ? samples[samples.length - 1] : null;
    var defs = monitorWaveDefs();
    var tabs = [];
    for (var i = 0; i < defs.length; i += 1) {
      tabs.push('<button type="button" data-history-wave="' + defs[i].key + '" class="' + (defs[i].key === key ? "active" : "") + '">' + (defs[i].singleLabel || defs[i].name || defs[i].label) + '</button>');
    }
    overlay.innerHTML = [
      '<div class="icu-monitor-history-shell">',
      '<div class="icu-monitor-history-header">',
      '<div class="icu-monitor-history-title">历史数据</div>',
      '<button type="button" class="icu-monitor-history-close">关闭</button>',
      '</div>',
      '<div class="icu-monitor-history-tabs">' + tabs.join("") + '</div>',
      '<div class="icu-monitor-history-stats">',
      '<div><span>当前</span><strong>' + currentValue + (unit ? " " + unit : "") + '</strong></div>',
      '<div><span>最近点</span><strong>' + (latest === null ? empty : formatWaveNumeric(latest)) + (unit ? " " + unit : "") + '</strong></div>',
      '<div><span>最高</span><strong>' + (min === null ? empty : formatWaveNumeric(max)) + '</strong></div>',
      '<div><span>最低</span><strong>' + (min === null ? empty : formatWaveNumeric(min)) + '</strong></div>',
      '<div><span>历史点数</span><strong>' + samples.length + '</strong></div>',
      '</div>',
      '<div class="icu-monitor-history-chart-wrap">',
      '<div class="icu-monitor-history-chart"></div>',
      '<div class="icu-monitor-history-note">点击折线查看该点坐标和值</div>',
      '</div>',
      '</div>'
    ].join("");
    var closeButton = overlay.querySelector(".icu-monitor-history-close");
    if (closeButton) {
      closeButton.onclick = function (event) {
        event.preventDefault();
        event.stopPropagation();
        closeMonitorHistory();
      };
    }
    var tabButtons = overlay.querySelectorAll("[data-history-wave]");
    for (var j = 0; j < tabButtons.length; j += 1) {
      tabButtons[j].onclick = function (event) {
        event.preventDefault();
        event.stopPropagation();
        monitorHistoryMetric = monitorHistoryKey(this.getAttribute("data-history-wave"));
        render();
      };
    }
    overlay.onclick = function (event) {
      if (event.target === overlay) {
        closeMonitorHistory();
      }
    };
    var chart = overlay.querySelector(".icu-monitor-history-chart");
    if (chart) {
      renderMonitorHistoryChart(chart, def, samples);
    }
  }

  function renderMonitorHistoryChart(host, def, samples) {
    if (!host) {
      return;
    }
    if (!samples.length) {
      host.innerHTML = '<div class="icu-monitor-history-empty">当前指标还没有累计到历史数据</div>';
      return;
    }
    var plot = buildWavePlotData(samples, {
      maxPoints: 240,
      viewWidth: 720,
      viewHeight: 260,
      paddingLeft: 16,
      paddingRight: 16,
      mid: 130,
      visualAmplitude: 82,
      type: def.type
    });
    if (!plot) {
      host.innerHTML = '<div class="icu-monitor-history-empty">当前指标还没有累计到历史数据</div>';
      return;
    }
    host.innerHTML = waveSvg(def.label, samples, def.color, def.type, plot);
    host.classList.add("interactive");
    var pointIndex = monitorHistoryProbeIndices[def.key];
    if (!isFinite(pointIndex)) {
      pointIndex = plot.points.length - 1;
    }
    updateWaveProbe(host, plot, pointIndex, def.color, historyProbeLabel(def.key, plot, pointIndex));
    host.onclick = function (event) {
      event.preventDefault();
      event.stopPropagation();
      var index = waveIndexFromEvent(event, host, plot);
      monitorHistoryProbeIndices[def.key] = index;
      updateWaveProbe(host, plot, index, def.color, historyProbeLabel(def.key, plot, index));
    };
  }

  function cleanupMonitorOverlays() {
    var nodes = document.querySelectorAll(".icu-monitor-filter,.icu-monitor-tools");
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].remove();
    }
  }

  function ensureMonitorTools(state) {
    var page = document.querySelector(".section_5");
    if (!page) {
      return;
    }
    var panel = page.querySelector(".icu-monitor-tools");
    if (!panel) {
      panel = document.createElement("div");
      panel.className = "icu-monitor-tools";
      page.appendChild(panel);
    }
    var settings = state.monitorSettings || {};
    var mode = settings.mode || "live";
    var volumeOn = settings.pulseBeep !== false || settings.alarmSound !== false;
    panel.innerHTML = [
      '<div class="icu-monitor-toolbar">',
      '<button type="button" data-native-action="monitor_history" class="' + (mode === "history" ? "active" : "") + '">历史数据</button>',
      '<button type="button" data-native-action="monitor_limits" class="' + (mode === "settings" ? "active" : "") + '">上下限设置</button>',
      '<button type="button" data-native-action="monitor_volume" class="' + (volumeOn ? "active" : "") + '">音量' + (volumeOn ? "开" : "关") + '</button>',
      '<button type="button" data-native-action="monitor_ble">监护蓝牙</button>',
      '</div>',
      '<div class="icu-monitor-panel">',
      monitorSummaryHtml(state),
      monitorLimitsHtml(settings),
      '</div>'
    ].join("");
  }

  function monitorSummaryHtml(state) {
    var m = monitor(state);
    var items = [
      ["心率", m.heartRate, "bpm"],
      ["血压", m.bloodPressure, "mmHg"],
      ["血氧", m.spo2, "%"],
      ["脉率", m.pulse || m.pulseRate, "bpm"],
      ["体温", m.bodyTemp, "℃"],
      ["呼吸率", m.resp, "brpm"]
    ];
    var html = ['<div class="icu-monitor-summary">'];
    for (var i = 0; i < items.length; i += 1) {
      html.push('<div class="icu-monitor-summary-item"><strong>' + items[i][0] + '</strong><span>' + display(items[i][1]) + '</span><em>' + items[i][2] + '</em></div>');
    }
    html.push('</div>');
    html.push('<div class="icu-monitor-last">最近帧：' + display(m.summary || m.lastError || m.state) + '</div>');
    return html.join("");
  }

  function monitorLimitsHtml(settings) {
    var limits = settings.limits || [];
    if (!limits.length) {
      return '<div class="icu-monitor-limits"><div>上下限：等待监护设置同步</div></div>';
    }
    var html = ['<div class="icu-monitor-limits">'];
    for (var i = 0; i < limits.length; i += 1) {
      var item = limits[i] || {};
      html.push('<div class="icu-monitor-limit-row"><span>' + display(item.name) + '</span><b>' + display(item.low) + '-' + display(item.high) + ' ' + display(item.unit) + '</b><em>' + (item.enabled === false ? "关" : "开") + '</em></div>');
    }
    html.push('</div>');
    return html.join("");
  }

  function cameraPageRoute(mode) {
    return mode === "playback" ? "../lanhu_5jiankonghuifang/index.html" : "../lanhu_4jiankong/index.html";
  }

  function openCameraPage(mode) {
    var route = cameraPageRoute(mode);
    if (route && window.location.href.indexOf(route) < 0) {
      window.location.href = route;
    }
  }

  function cameraState(state) {
    return state && state.camera ? state.camera : {};
  }

  function cameraSnapshots(state) {
    var rows = cameraState(state).snapshots;
    return Array.isArray(rows) ? rows.slice() : [];
  }

  function cameraSnapshotKey(item) {
    if (!item) {
      return "";
    }
    return clean(item.cacheUrl) || clean(item.fileName) || String(item.nativeIndex || "");
  }

  function cameraSnapshotTime(item) {
    var ts = Number(item && item.capturedAt || 0);
    if (ts > 0) {
      return ts;
    }
    var text = clean(item && item.timeText);
    if (!text) {
      return 0;
    }
    ts = Date.parse(text.replace(/-/g, "/"));
    return isNaN(ts) ? 0 : ts;
  }

  function sameCameraDay(ts, now) {
    var date = new Date(ts);
    return date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate();
  }

  function sameCameraWeek(ts, now) {
    var date = new Date(ts);
    var start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    var day = start.getDay() || 7;
    start.setDate(start.getDate() - day + 1);
    start.setHours(0, 0, 0, 0);
    var end = new Date(start.getTime());
    end.setDate(start.getDate() + 7);
    return date.getTime() >= start.getTime() && date.getTime() < end.getTime();
  }

  function sameCameraMonth(ts, now) {
    var date = new Date(ts);
    return date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth();
  }

  function cameraScopedSnapshots(state) {
    var rows = cameraSnapshots(state);
    var p = patient(state);
    var recordNo = clean(p.recordNo) || clean(p.caseNo);
    var petName = clean(p.petName);
    if (recordNo) {
      var matchedRecord = rows.filter(function (item) {
        return clean(item.recordNo) === recordNo;
      });
      if (matchedRecord.length) {
        return matchedRecord;
      }
    }
    if (petName) {
      var matchedPet = rows.filter(function (item) {
        return clean(item.petName) === petName;
      });
      if (matchedPet.length) {
        return matchedPet;
      }
    }
    if (recordNo || petName) {
      return [];
    }
    return rows;
  }

  function filteredCameraSnapshots(state) {
    var rows = cameraScopedSnapshots(state);
    var now = new Date();
    rows = rows.filter(function (item) {
      var ts = cameraSnapshotTime(item);
      if (cameraPlaybackPeriod === "today") {
        return !ts || sameCameraDay(ts, now);
      }
      if (cameraPlaybackPeriod === "week") {
        return !ts || sameCameraWeek(ts, now);
      }
      if (cameraPlaybackPeriod === "month") {
        return !ts || sameCameraMonth(ts, now);
      }
      return true;
    });
    if (cameraPlaybackSource === "card") {
      rows = rows.filter(function (item) {
        return item && item.downloaded !== true;
      });
    }
    var keyword = clean(cameraPlaybackKeyword).toLowerCase();
    if (keyword) {
      rows = rows.filter(function (item) {
        var text = [
          item.fileName,
          item.timeText,
          item.petName,
          item.recordNo
        ].join(" ").toLowerCase();
        return text.indexOf(keyword) >= 0;
      });
    }
    rows.sort(function (a, b) {
      var diff = cameraSnapshotTime(b) - cameraSnapshotTime(a);
      if (diff) {
        return diff;
      }
      return Number(b.nativeIndex || 0) - Number(a.nativeIndex || 0);
    });
    return rows;
  }

  function cameraSelectionKey() {
    return clean(selectedCameraSnapshotKey);
  }

  function syncNativeCameraSelection(item) {
    var key = cameraSnapshotKey(item);
    if (!item || !key || syncedCameraSnapshotKey === key) {
      return;
    }
    syncedCameraSnapshotKey = key;
    if (window.IcuNative && typeof window.IcuNative.action === "function") {
      try {
        window.IcuNative.action("camera_select_" + Number(item.nativeIndex || 0), "camera_select", window.location.pathname || "");
      } catch (error) {
      }
    }
  }

  function ensureSelectedCameraSnapshot(items) {
    var key = cameraSelectionKey();
    var matched = null;
    for (var i = 0; i < items.length; i += 1) {
      if (cameraSnapshotKey(items[i]) === key) {
        matched = items[i];
        break;
      }
    }
    if (!matched) {
      selectedCameraSnapshotKey = items.length ? cameraSnapshotKey(items[0]) : "";
      if (!items.length) {
        syncedCameraSnapshotKey = "";
      }
      matched = items.length ? items[0] : null;
    }
    syncNativeCameraSelection(matched);
    return matched;
  }

  function setCameraPlaybackPeriodMode(mode) {
    if (cameraPlaybackPeriod === mode) {
      return;
    }
    cameraPlaybackPeriod = mode;
    cameraPlaybackPageIndex = 0;
    render();
  }

  function setCameraPlaybackSourceMode(mode) {
    if (cameraPlaybackSource === mode) {
      return;
    }
    cameraPlaybackSource = mode;
    cameraPlaybackPageIndex = 0;
    render();
  }

  function setCameraPlaybackKeywordText(text) {
    cameraPlaybackKeyword = clean(text);
    cameraPlaybackPageIndex = 0;
    render();
  }

  function promptCameraPlaybackFilter() {
    var next = window.prompt("输入回放关键词，可按时间、宠物或文件名筛选；留空可清除筛选。", cameraPlaybackKeyword || "");
    if (next === null) {
      return;
    }
    setCameraPlaybackKeywordText(next);
  }

  function selectCameraSnapshot(item) {
    if (!item) {
      return;
    }
    selectedCameraSnapshotKey = cameraSnapshotKey(item);
    syncNativeCameraSelection(item);
    render();
  }

  // 原实现按固定下标切片(slice(11,16) / slice(0,10)),只对 "yyyy-MM-dd HH:mm:ss"
  // 成立。日期显示改中文形式后长度不再固定,改成先解析再取值。
  // 注意:原生侧 timeText 仍是 ISO(它会被 cameraSnapshotTime 的 Date.parse 兜底解析),
  // 所以这里只负责「ISO -> 中文」的展示转换。
  function cameraSnapshotParts(item) {
    var text = clean(item && item.timeText);
    if (!text) {
      return null;
    }
    var m = text.match(CN_DATE_RE);
    if (m) {
      return {
        date: Number(m[1]) + "年" + Number(m[2]) + "月" + Number(m[3]) + "日",
        clock: m[4] == null ? "" : pad2(m[4]) + ":" + m[5]
      };
    }
    var cn = text.match(/^(\d{4})年(\d{1,2})月(\d{1,2})日(?:\s+(\d{1,2}):(\d{2}))?/);
    if (cn) {
      return {
        date: Number(cn[1]) + "年" + Number(cn[2]) + "月" + Number(cn[3]) + "日",
        clock: cn[4] == null ? "" : pad2(cn[4]) + ":" + cn[5]
      };
    }
    return { date: text, clock: "" };
  }

  function cameraSnapshotClock(item) {
    var parts = cameraSnapshotParts(item);
    if (!parts || !parts.clock) {
      return "--:--";
    }
    return parts.clock;
  }

  function cameraSnapshotDate(item) {
    var parts = cameraSnapshotParts(item);
    if (!parts) {
      return "--";
    }
    return parts.date;
  }

  function cameraStatusBadge(item) {
    return item && item.downloaded ? "云" : "卡";
  }

  function setCameraModeClass(selector, active) {
    var node = document.querySelector(selector);
    if (!node) {
      return;
    }
    node.classList.toggle("icu-camera-mode-active", !!active);
    node.classList.toggle("icu-camera-mode-inactive", !active);
  }

  function setCameraModeAction(selector, action, title) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].setAttribute("data-native-action", action);
      nodes[i].style.cursor = "pointer";
      if (title) {
        nodes[i].setAttribute("title", title);
      }
    }
  }

  function setCameraChipClass(selector, active) {
    var node = document.querySelector(selector);
    if (!node) {
      return;
    }
    node.classList.toggle("icu-camera-chip-active", !!active);
    node.classList.toggle("icu-camera-chip-inactive", !active);
  }

  function setCameraModeButtons() {
    var page = pageName();
    if (page === "lanhu_4jiankong") {
      setCameraModeClass(".text-wrapper_23", true);
      setCameraModeClass(".text-wrapper_24", false);
      setCameraModeAction(".text-wrapper_23,.text_62", "camera_live", "切换到实况");
      setCameraModeAction(".text-wrapper_24,.text_63", "camera_playback", "切换到回放");
    } else if (page === "lanhu_5jiankonghuifang") {
      setCameraModeClass(".text-wrapper_29", false);
      setCameraModeClass(".text-wrapper_30", true);
      setCameraModeAction(".text-wrapper_29,.text_67", "camera_live", "切换到实况");
      setCameraModeAction(".text-wrapper_30,.text_68", "camera_playback", "当前为回放");
    }
  }

  function setCameraPlaybackAction(selector, action, title) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].setAttribute("data-native-action", action);
      nodes[i].style.cursor = "pointer";
      if (title) {
        nodes[i].setAttribute("title", title);
      }
    }
  }

  function renderCameraLiveActions() {
    setCameraPlaybackAction(".box_18,.image-text_14,.label_3,.text-group_2", "camera_snapshot", "抓取当前实时监控画面");
    setCameraPlaybackAction(".box_20,.image-text_16,.label_4,.text-group_4", "camera_fullscreen", "应用内全屏显示摄像头");
    setCameraPlaybackAction(".block_7,.image_8", "camera_preview_tap", "点击刷新摄像头预览");
  }

  function renderCameraPlaybackToolbar(state) {
    setCameraChipClass(".text-wrapper_23", cameraPlaybackPeriod === "today");
    setCameraChipClass(".text-wrapper_24", cameraPlaybackPeriod === "week");
    setCameraChipClass(".text-wrapper_25", cameraPlaybackPeriod === "month");
    setCameraChipClass(".text-wrapper_26", cameraPlaybackSource === "cloud");
    setCameraChipClass(".text-wrapper_27", cameraPlaybackSource === "card");
    setCameraChipClass(".text-wrapper_28", !!cameraPlaybackKeyword);

    var groups = [
      { selector: ".text-wrapper_23", handler: function () { setCameraPlaybackPeriodMode("today"); } },
      { selector: ".text-wrapper_24", handler: function () { setCameraPlaybackPeriodMode("week"); } },
      { selector: ".text-wrapper_25", handler: function () { setCameraPlaybackPeriodMode("month"); } },
      { selector: ".text-wrapper_26", handler: function () { setCameraPlaybackSourceMode("cloud"); } },
      { selector: ".text-wrapper_27", handler: function () { setCameraPlaybackSourceMode("card"); } },
      { selector: ".text-wrapper_28", handler: promptCameraPlaybackFilter }
    ];
    for (var i = 0; i < groups.length; i += 1) {
      var node = document.querySelector(groups[i].selector);
      if (!node) {
        continue;
      }
      node.style.cursor = "pointer";
      if (node.getAttribute("data-icu-camera-bind") === "1") {
        continue;
      }
      node.setAttribute("data-icu-camera-bind", "1");
      node.addEventListener("click", groups[i].handler, true);
    }
    var title = cameraPlaybackKeyword ? "当前筛选：" + cameraPlaybackKeyword : "点击设置关键词筛选";
    var filterNode = document.querySelector(".text-wrapper_28");
    if (filterNode) {
      filterNode.setAttribute("title", title);
    }
    var sectionNode = document.querySelector(".section_11");
    if (sectionNode) {
      sectionNode.setAttribute("title", title);
    }
    setText(".text_60", "监控日期");
    setText(".text_61", "今天");
    setText(".text_62", "本周");
    setText(".text_63", "本月");
    setText(".text_64", "云录像");
    setText(".text_65", "卡录像");
    setText(".text_66", "筛选");
  }

  function renderCameraPlaybackGrid(state) {
    var host = document.querySelector(".image-wrapper_7");
    if (!host) {
      return null;
    }
    var items = filteredCameraSnapshots(state);
    var selected = ensureSelectedCameraSnapshot(items);
    var pageSize = 12;
    var totalPages = Math.max(1, Math.ceil(items.length / pageSize));
    cameraPlaybackPageIndex = Math.max(0, Math.min(cameraPlaybackPageIndex, totalPages - 1));
    var start = cameraPlaybackPageIndex * pageSize;
    var visible = items.slice(start, start + pageSize);
    var html = [];
    if (!visible.length) {
      html.push('<div class="icu-camera-empty">暂无符合条件的回放截图</div>');
    } else {
      for (var i = 0; i < visible.length; i += 1) {
        var item = visible[i] || {};
        var key = cameraSnapshotKey(item);
        var selectedClass = selected && cameraSnapshotKey(selected) === key ? " selected" : "";
        var src = clean(item.cacheUrl);
        html.push(
          '<button type="button" class="icu-camera-replay-card' + selectedClass + '" data-camera-key="' + key + '" data-camera-index="' + Number(item.nativeIndex || 0) + '">' +
            (src ? '<img src="' + src + '" alt="回放截图" />' : '<div class="icu-camera-replay-placeholder">暂无预览</div>') +
            '<span class="icu-camera-replay-time">' + cameraSnapshotClock(item) + '</span>' +
            '<span class="icu-camera-replay-badge">' + cameraStatusBadge(item) + '</span>' +
            '<span class="icu-camera-replay-footer">' + cameraSnapshotDate(item) + '</span>' +
          '</button>'
        );
      }
    }
    host.innerHTML = html.join("");
    if (host.getAttribute("data-icu-camera-grid") !== "1") {
      host.setAttribute("data-icu-camera-grid", "1");
      host.addEventListener("click", function (event) {
        var node = event.target;
        while (node && node !== host) {
          if (node.getAttribute && node.getAttribute("data-camera-key") !== null) {
            event.preventDefault();
            event.stopPropagation();
            var rows = filteredCameraSnapshots(readFirstPhaseState());
            var key = node.getAttribute("data-camera-key") || "";
            for (var k = 0; k < rows.length; k += 1) {
              if (cameraSnapshotKey(rows[k]) === key) {
                selectCameraSnapshot(rows[k]);
                return;
              }
            }
            return;
          }
          node = node.parentNode;
        }
      }, true);
    }
    return {
      items: items,
      visible: visible,
      selected: selected,
      totalPages: totalPages,
      currentPage: cameraPlaybackPageIndex
    };
  }

  function renderCameraPlaybackPager(pageState) {
    var pager = document.querySelector(".group_37");
    if (!pager) {
      return;
    }
    var totalPages = pageState ? pageState.totalPages : 1;
    var currentPage = pageState ? pageState.currentPage : 0;
    pager.innerHTML = [
      '<button type="button" data-camera-page="first"' + (currentPage <= 0 ? ' disabled="disabled"' : "") + '>|&lt;</button>',
      '<button type="button" data-camera-page="prev"' + (currentPage <= 0 ? ' disabled="disabled"' : "") + '>&lt;</button>',
      '<span class="icu-camera-pager-index">' + pad2(currentPage + 1) + '</span>',
      '<button type="button" data-camera-page="next"' + (currentPage >= totalPages - 1 ? ' disabled="disabled"' : "") + '>&gt;</button>',
      '<button type="button" data-camera-page="last"' + (currentPage >= totalPages - 1 ? ' disabled="disabled"' : "") + '>&gt;|</button>',
      '<span class="icu-camera-pager-total">共 ' + totalPages + ' 页</span>'
    ].join("");
    if (pager.getAttribute("data-icu-camera-pager") !== "1") {
      pager.setAttribute("data-icu-camera-pager", "1");
      pager.addEventListener("click", function (event) {
        var target = event.target;
        if (!target || target.tagName !== "BUTTON" || target.disabled) {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        var action = target.getAttribute("data-camera-page") || "";
        var current = Math.max(0, cameraPlaybackPageIndex);
        var max = Math.max(0, Math.ceil(filteredCameraSnapshots(readFirstPhaseState()).length / 12) - 1);
        if (action === "first") {
          cameraPlaybackPageIndex = 0;
        } else if (action === "prev") {
          cameraPlaybackPageIndex = Math.max(0, current - 1);
        } else if (action === "next") {
          cameraPlaybackPageIndex = Math.min(max, current + 1);
        } else if (action === "last") {
          cameraPlaybackPageIndex = max;
        }
        render();
      }, true);
    }
  }

  function renderCameraPlaybackActions(pageState) {
    var selected = pageState ? pageState.selected : null;
    setCameraPlaybackAction(".section_16,.image-text_22,.block_6,.text-group_5", "camera_ai", "分析当前选中截图");
    setCameraPlaybackAction(".section_13,.image-text_23,.label_3,.text-group_2", "camera_snapshot", "抓取一张新的监控截图");
    setCameraPlaybackAction(".section_14,.image-text_24,.label_5,.text-group_3", "camera_snapshot_download", "下载当前选中截图");
    setCameraPlaybackAction(".section_15,.image-text_25,.label_6,.text-group_4", "camera_snapshot_delete", "删除当前选中截图");
    setCameraPlaybackAction(".box_18,.image-text_26,.label_7,.text-group_7", "camera_manage", "管理回放截图");
    var sections = [".section_16", ".section_14", ".section_15", ".box_18"];
    for (var i = 0; i < sections.length; i += 1) {
      var node = document.querySelector(sections[i]);
      if (!node) {
        continue;
      }
      node.classList.toggle("icu-camera-action-disabled", !selected && sections[i] !== ".box_18");
    }
  }

  function renderCameraPlaybackPage(state) {
    renderCameraPlaybackToolbar(state);
    var pageState = renderCameraPlaybackGrid(state) || {
      items: [],
      visible: [],
      selected: null,
      totalPages: 1,
      currentPage: 0
    };
    renderCameraPlaybackActions(pageState);
    renderCameraPlaybackPager(pageState);
  }

  function showCameraPlayback() {
    cameraPlaybackMode = true;
    if (pageName() !== "lanhu_5jiankonghuifang") {
      openCameraPage("playback");
      return;
    }
    renderCamera(readFirstPhaseState());
    syncCameraPreview();
  }

  function showCameraLive() {
    cameraPlaybackMode = false;
    if (pageName() === "lanhu_5jiankonghuifang") {
      openCameraPage("live");
      return;
    }
    renderCamera(readFirstPhaseState());
    syncCameraPreview();
  }

  function ensureCameraConfigEntry() {
    var hostNode = document.querySelector(".block_7");
    var legacyButton = hostNode ? hostNode.querySelector(".icu-camera-config-button") : null;
    if (legacyButton && legacyButton.parentNode) {
      legacyButton.parentNode.removeChild(legacyButton);
    }
    setText(".text_70", "\u914d\u7f6e\u6444\u50cf\u5934");
    bindNativeAction(".image-wrapper_7,.label_10,.text_70", "camera_config", "\u70b9\u51fb\u914d\u7f6e\u6444\u50cf\u5934");
  }

  function ensureCameraPlaybackPanel() {
    var hostNode = document.querySelector(".block_7");
    if (!hostNode) {
      return null;
    }
    var panel = hostNode.querySelector(".icu-camera-playback-panel");
    if (panel) {
      return panel;
    }
    panel = document.createElement("div");
    panel.className = "icu-camera-playback-panel";
    panel.innerHTML = [
      '<div class="icu-camera-playback-title">\u6444\u50cf\u56de\u653e</div>',
      '<div class="icu-camera-playback-empty">\u70b9\u51fb\u53f3\u4e0a\u89d2\u201c\u56de\u653e\u201d\u53ef\u8fdb\u5165\u5b8c\u6574\u6444\u50cf\u56de\u653e\u9875\uff0c\u5728\u90a3\u91cc\u67e5\u770b\u3001\u7b5b\u9009\u548c\u7ba1\u7406\u622a\u56fe\u3002</div>',
      '<div class="icu-camera-playback-actions">',
      '<button type="button" data-camera-action="open-playback">\u6253\u5f00\u56de\u653e\u9875</button>',
      '<button type="button" data-camera-action="snapshot">\u622a\u56fe</button>',
      '<button type="button" data-camera-action="config">\u914d\u7f6e\u6444\u50cf\u5934</button>',
      '</div>'
    ].join("");
    panel.addEventListener("click", function (event) {
      var target = event.target;
      if (!target || target.tagName !== "BUTTON") {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      var action = target.getAttribute("data-camera-action") || "";
      if (action === "open-playback") {
        openCameraPage("playback");
      } else if (action === "snapshot") {
        callAction("camera_snapshot", "camera_snapshot");
      } else if (action === "config") {
        callAction("camera_config", "camera_config");
      }
    }, true);
    hostNode.appendChild(panel);
    return panel;
  }

  function renderCameraPlaybackPanel() {
    var panel = ensureCameraPlaybackPanel();
    if (!panel) {
      return;
    }
    panel.style.display = cameraPlaybackMode ? "flex" : "none";
  }

  function renderCamera(state) {
    var page = pageName();
    cameraPlaybackMode = page === "lanhu_5jiankonghuifang";
    setCameraModeButtons();
    if (page === "lanhu_4jiankong") {
      renderCameraLiveActions();
      ensureCameraConfigEntry();
      renderCameraPlaybackPanel();
      var now = new Date();
      setText(".text_60", formatDateTimeCN(now));
      setText(".text_61", weekdayText(now));
      return;
    }
    if (page === "lanhu_5jiankonghuifang") {
      renderCameraPlaybackPage(state);
    }
  }

  function nativeCameraPreview(left, top, width, height, viewportWidth, viewportHeight, visible) {
    if (!window.IcuNative || typeof window.IcuNative.cameraPreview !== "function") {
      return;
    }
    try {
      window.IcuNative.cameraPreview(left || 0, top || 0, width || 0, height || 0,
        viewportWidth || 1920, viewportHeight || 1200, !!visible);
    } catch (error) {
    }
  }

  function cameraConfigured() {
    if (!window.IcuNative || typeof window.IcuNative.cameraConfigured !== "function") {
      return true;
    }
    try {
      return !!window.IcuNative.cameraConfigured();
    } catch (error) {
      return true;
    }
  }

  function promptCameraConfigOnce() {
    if (cameraConfigPrompted || cameraConfigured()) {
      return;
    }
    cameraConfigPrompted = true;
    window.setTimeout(function () {
      callAction("camera_config", "camera_config");
    }, 80);
  }

  function syncCameraPreview() {
    if (document.querySelector(".icu-patient-modal")) {
      nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
      return;
    }
    var page = pageName();
    var selector = "";
    var inset = 8;
    var shouldShow = false;
    if (page === "lanhu_4jiankong") {
      if (cameraPlaybackMode) {
        // 回放模式不显示实时预览
        nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
        return;
      }
      // 实况页当前 DOM 中 .block_7 就是左侧完整视频容器，使用它比 .image_8 更稳，
      // 可避免图片占位元素变化后原生预览挂不到正确区域。
      selector = ".block_7";
      inset = 10;
      promptCameraConfigOnce();
      shouldShow = true;
    } else if (page === "lanhu_1icuzhuangtaikaobei") {
      selector = ".image-wrapper_4";
      shouldShow = true;
    } else {
      // 其他所有页面（设置页、监控页、治疗记录等）：强制隐藏预览
      nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
      return;
    }
    var target = document.querySelector(selector);
    if (!target || !target.getBoundingClientRect) {
      nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
      return;
    }
    var rect = target.getBoundingClientRect();
    if (!rect || rect.width < 20 || rect.height < 20) {
      nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
      return;
    }
    if (!shouldShow) {
      nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
      return;
    }
    var viewportWidth = document.documentElement.clientWidth || window.innerWidth || 1920;
    var viewportHeight = document.documentElement.clientHeight || window.innerHeight || 1200;
    nativeCameraPreview(rect.left + inset, rect.top + inset,
      Math.max(1, rect.width - inset * 2), Math.max(1, rect.height - inset * 2),
      viewportWidth, viewportHeight, true);
  }

  function installWave(selector, label, samples, color, type, options) {
    var target = document.querySelector(selector);
    if (!target) {
      return;
    }
    options = options || {};
    var placeholders = target.querySelectorAll("img");
    var old = target.querySelector(".icu-wave");
    if (!hasWaveData(samples)) {
      if (old) {
        old.remove();
      }
      for (var i = 0; i < placeholders.length; i += 1) {
        placeholders[i].style.display = "";
      }
      target.classList.add("icu-wave-empty");
      target.setAttribute("data-empty-label", "");
      return;
    }
    target.classList.remove("icu-wave-empty");
    target.removeAttribute("data-empty-label");
    for (var j = 0; j < placeholders.length; j += 1) {
      placeholders[j].style.display = "none";
    }
    if (!old) {
      old = document.createElement("div");
      old.className = "icu-wave";
      target.appendChild(old);
    }
    target.style.justifyContent = "flex-start";
    old.style.flex = "1 1 auto";
    old.style.width = "100%";
    old.style.height = "100%";
    old.style.minHeight = "0";
    old.style.marginTop = "0";
    old.style.display = "flex";
    old.style.alignItems = "center";
    old.style.justifyContent = "center";
    old.classList.add("icu-wave-interactive");
    var plot = buildWavePlotData(samples, {
      maxPoints: 132,
      viewWidth: 360,
      viewHeight: 96,
      paddingLeft: 8,
      paddingRight: 8,
      mid: 48,
      visualAmplitude: 28,
      type: type
    });
    old.innerHTML = waveSvg(label, samples, color, type, plot);
    var svg = old.querySelector("svg");
    if (svg) {
      svg.style.display = "block";
      svg.style.width = "100%";
      svg.style.height = "100%";
      svg.style.maxWidth = "100%";
      svg.style.maxHeight = "100%";
      svg.style.margin = "0 auto";
      svg.style.flex = "1 1 auto";
      svg.style.alignSelf = "center";
    }
    if (plot && options.key) {
      updateWaveProbe(old, plot, plot.points.length - 1, color, historyProbeLabel(options.key, plot, plot.points.length - 1));
      old.onclick = function (event) {
        event.preventDefault();
        event.stopPropagation();
        openMonitorHistory(options.key);
      };
      old.title = "点击查看历史数据";
    }
  }

  function hasWaveData(samples) {
    return Array.isArray(samples) && samples.length > 1;
  }

  function normalizeWaveEntries(samples) {
    if (!Array.isArray(samples) || !samples.length) {
      return [];
    }
    var result = [];
    for (var i = 0; i < samples.length; i += 1) {
      var value = samples[i];
      if (value === undefined || value === null || value === "") {
        continue;
      }
      var numeric = typeof value === "number" ? value : parseFloat(String(value));
      if (isFinite(numeric)) {
        result.push({
          value: numeric,
          sampleIndex: i
        });
      }
    }
    return result;
  }

  function waveTypeStyle(type) {
    if (type === 0) {
      return {
        smoothWindow: 0,
        preservePeaks: true,
        strokeWidth: 2.1,
        useSmoothPath: false,
        visualRatio: 0.42,
        paddingY: 6
      };
    }
    if (type === 1) {
      return {
        smoothWindow: 1,
        preservePeaks: true,
        strokeWidth: 2.4,
        useSmoothPath: true,
        visualRatio: 0.35,
        paddingY: 6
      };
    }
    return {
      smoothWindow: 2,
      preservePeaks: false,
      strokeWidth: 2.3,
      useSmoothPath: true,
      visualRatio: 0.33,
      paddingY: 6
    };
  }

  function compressWaveSamples(samples, maxPoints) {
    var entries = Array.isArray(samples) && samples.length && typeof samples[0] === "object"
      ? samples.slice()
      : normalizeWaveEntries(samples);
    if (!entries.length) {
      return [];
    }
    if (entries.length <= maxPoints) {
      return entries.slice();
    }
    var result = [];
    var bucketCount = Math.max(1, Math.floor(maxPoints / 2));
    var step = entries.length / bucketCount;
    var lastIndex = -1;
    for (var i = 0; i < bucketCount; i += 1) {
      var start = Math.floor(i * step);
      var end = Math.floor((i + 1) * step);
      if (i === bucketCount - 1) {
        end = entries.length;
      }
      if (end <= start) {
        end = Math.min(entries.length, start + 1);
      }
      var bucket = entries.slice(start, end);
      if (!bucket.length) {
        continue;
      }
      var minEntry = bucket[0];
      var maxEntry = bucket[0];
      for (var j = 1; j < bucket.length; j += 1) {
        if (bucket[j].value < minEntry.value) {
          minEntry = bucket[j];
        }
        if (bucket[j].value > maxEntry.value) {
          maxEntry = bucket[j];
        }
      }
      var ordered = bucket.length <= 2
        ? bucket
        : (minEntry.sampleIndex <= maxEntry.sampleIndex ? [minEntry, maxEntry] : [maxEntry, minEntry]);
      for (var k = 0; k < ordered.length; k += 1) {
        if (ordered[k].sampleIndex !== lastIndex) {
          result.push(ordered[k]);
          lastIndex = ordered[k].sampleIndex;
        }
      }
    }
    if (result[result.length - 1].sampleIndex !== entries[entries.length - 1].sampleIndex) {
      result.push(entries[entries.length - 1]);
    }
    if (result.length <= maxPoints) {
      return result;
    }
    var reduced = [];
    var reducedStep = (result.length - 1) / Math.max(1, maxPoints - 1);
    for (var m = 0; m < maxPoints; m += 1) {
      var reducedIndex = Math.round(m * reducedStep);
      if (reducedIndex >= result.length) {
        reducedIndex = result.length - 1;
      }
      if (!reduced.length || reduced[reduced.length - 1].sampleIndex !== result[reducedIndex].sampleIndex) {
        reduced.push(result[reducedIndex]);
      }
    }
    return reduced;
  }

  function isWaveTurningPoint(entries, index) {
    if (!entries || index <= 0 || index >= entries.length - 1) {
      return false;
    }
    var prev = entries[index - 1].value;
    var current = entries[index].value;
    var next = entries[index + 1].value;
    return (current >= prev && current >= next) || (current <= prev && current <= next);
  }

  function smoothWaveEntries(entries, windowSize, preservePeaks) {
    if (!Array.isArray(entries) || entries.length < 3 || !windowSize) {
      return entries ? entries.slice() : [];
    }
    var result = [];
    for (var i = 0; i < entries.length; i += 1) {
      if (i === 0 || i === entries.length - 1 || (preservePeaks && isWaveTurningPoint(entries, i))) {
        result.push({
          value: entries[i].value,
          sampleIndex: entries[i].sampleIndex
        });
        continue;
      }
      var sum = 0;
      var weightSum = 0;
      for (var offset = -windowSize; offset <= windowSize; offset += 1) {
        var index = i + offset;
        if (index < 0 || index >= entries.length) {
          continue;
        }
        var weight = windowSize + 1 - Math.abs(offset);
        sum += entries[index].value * weight;
        weightSum += weight;
      }
      result.push({
        value: weightSum ? sum / weightSum : entries[i].value,
        sampleIndex: entries[i].sampleIndex
      });
    }
    return result;
  }

  function percentileValue(sorted, ratio) {
    if (!sorted || !sorted.length) {
      return 0;
    }
    var index = Math.max(0, Math.min(sorted.length - 1, Math.round((sorted.length - 1) * ratio)));
    return sorted[index];
  }

  function waveLinePath(points) {
    if (!points || !points.length) {
      return "";
    }
    var path = ["M", points[0].x.toFixed(1), points[0].y.toFixed(1)];
    for (var i = 1; i < points.length; i += 1) {
      path.push("L", points[i].x.toFixed(1), points[i].y.toFixed(1));
    }
    return path.join(" ");
  }

  function waveSmoothPath(points) {
    if (!points || !points.length) {
      return "";
    }
    if (points.length < 3) {
      return waveLinePath(points);
    }
    var path = ["M", points[0].x.toFixed(1), points[0].y.toFixed(1)];
    for (var i = 1; i < points.length - 1; i += 1) {
      var midX = (points[i].x + points[i + 1].x) / 2;
      var midY = (points[i].y + points[i + 1].y) / 2;
      path.push("Q", points[i].x.toFixed(1), points[i].y.toFixed(1), midX.toFixed(1), midY.toFixed(1));
    }
    path.push("T", points[points.length - 1].x.toFixed(1), points[points.length - 1].y.toFixed(1));
    return path.join(" ");
  }

  function waveGridSvg(plot, mid, color) {
    if (!plot) {
      return "";
    }
    var html = [];
    var gridColor = color || "rgba(255,255,255,0.08)";
    for (var x = 0; x <= plot.viewWidth; x += 20) {
      html.push('<line x1="' + x + '" y1="0" x2="' + x + '" y2="' + plot.viewHeight + '" stroke="' + gridColor + '" stroke-width="0.8"/>');
    }
    for (var y = 0; y <= plot.viewHeight; y += 12) {
      html.push('<line x1="0" y1="' + y + '" x2="' + plot.viewWidth + '" y2="' + y + '" stroke="' + gridColor + '" stroke-width="0.8"/>');
    }
    html.push('<line x1="0" y1="' + mid.toFixed(1) + '" x2="' + plot.viewWidth + '" y2="' + mid.toFixed(1) + '" stroke="rgba(255,255,255,0.18)" stroke-width="1"/>');
    return html.join("");
  }

  function buildWavePlotData(samples, options) {
    options = options || {};
    var style = waveTypeStyle(options.type);
    var entries = compressWaveSamples(samples, options.maxPoints || 128);
    entries = smoothWaveEntries(entries, options.smoothWindow === undefined ? style.smoothWindow : options.smoothWindow,
      options.preservePeaks === undefined ? style.preservePeaks : options.preservePeaks);
    if (!entries.length) {
      return null;
    }
    var source = [];
    var min = entries[0].value;
    var max = entries[0].value;
    var sum = 0;
    for (var i = 0; i < entries.length; i += 1) {
      var currentValue = entries[i].value;
      source.push(currentValue);
      if (currentValue < min) {
        min = currentValue;
      }
      if (currentValue > max) {
        max = currentValue;
      }
      sum += currentValue;
    }
    var sorted = source.slice().sort(function (a, b) { return a - b; });
    var trimmedMin = percentileValue(sorted, 0.04);
    var trimmedMax = percentileValue(sorted, 0.96);
    if (!isFinite(trimmedMin) || !isFinite(trimmedMax) || trimmedMax <= trimmedMin) {
      trimmedMin = min;
      trimmedMax = max;
    }
    var center = percentileValue(sorted, 0.5);
    if (!isFinite(center)) {
      center = sum / source.length;
    }
    var amplitude = Math.max(center - trimmedMin, trimmedMax - center);
    if (!isFinite(amplitude) || amplitude <= 0) {
      amplitude = Math.max((max - min) / 2, 1);
    }
    amplitude *= options.amplitudePadding === undefined ? 1.14 : options.amplitudePadding;
    var viewWidth = options.viewWidth || 320;
    var viewHeight = options.viewHeight || 86;
    var paddingLeft = options.paddingLeft === undefined ? 8 : options.paddingLeft;
    var paddingRight = options.paddingRight === undefined ? paddingLeft : options.paddingRight;
    var paddingY = options.paddingY === undefined ? style.paddingY : options.paddingY;
    var drawWidth = Math.max(1, viewWidth - paddingLeft - paddingRight);
    var mid = options.mid === undefined ? viewHeight / 2 : options.mid;
    var visualAmplitude = options.visualAmplitude === undefined
      ? Math.max(18, (viewHeight / 2 - paddingY) * style.visualRatio / 0.35)
      : options.visualAmplitude;
    var points = [];
    for (var j = 0; j < entries.length; j += 1) {
      var x = paddingLeft;
      if (entries.length > 1) {
        x += (drawWidth * j) / (entries.length - 1);
      }
      var value = entries[j].value;
      if (value < center - amplitude) {
        value = center - amplitude;
      } else if (value > center + amplitude) {
        value = center + amplitude;
      }
      var ratio = (value - center) / amplitude;
      var y = mid - ratio * visualAmplitude;
      y = Math.max(paddingY, Math.min(viewHeight - paddingY, y));
      points.push({
        x: x,
        y: y,
        value: entries[j].value,
        ratio: ratio,
        sampleIndex: entries[j].sampleIndex
      });
    }
    return {
      source: source,
      points: points,
      mid: mid,
      strokeWidth: style.strokeWidth,
      useSmoothPath: style.useSmoothPath,
      viewWidth: viewWidth,
      viewHeight: viewHeight
    };
  }

  function waveSvg(label, samples, color, type, plot) {
    plot = plot || buildWavePlotData(samples, {
      maxPoints: 128,
      viewWidth: 360,
      viewHeight: 96,
      paddingLeft: 8,
      paddingRight: 8,
      mid: 48,
      visualAmplitude: 28,
      type: type
    });
    if (!plot || !plot.points.length) {
      return "";
    }
    var pathData = plot.useSmoothPath ? waveSmoothPath(plot.points) : waveLinePath(plot.points);
    var strokeWidth = plot.strokeWidth || 2.2;
    return [
      '<svg viewBox="0 0 ' + plot.viewWidth + ' ' + plot.viewHeight + '" width="100%" height="100%" preserveAspectRatio="none">',
      '<g class="icu-wave-grid">',
      waveGridSvg(plot, plot.mid, "rgba(255,255,255,0.06)"),
      '</g>',
      '<path d="' + pathData + '" fill="none" stroke="' + color + '" stroke-width="' + (strokeWidth + 1.4).toFixed(1) + '" stroke-opacity="0.12" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke"/>',
      '<path d="' + pathData + '" fill="none" stroke="' + color + '" stroke-width="' + strokeWidth.toFixed(1) + '" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" shape-rendering="geometricPrecision"/>',
      '</svg>'
    ].join("");
  }

  function formatWaveNumeric(value) {
    if (!isFinite(value)) {
      return empty;
    }
    return Math.abs(value - Math.round(value)) < 0.01 ? String(Math.round(value)) : value.toFixed(1);
  }

  function historyProbeLabel(key, plot, index) {
    if (!plot || !plot.points.length) {
      return "";
    }
    index = Math.max(0, Math.min(plot.points.length - 1, index || 0));
    var point = plot.points[index];
    var unit = monitorMetricUnit(key);
    return monitorMetricName(key) + " 坐标(" + (point.sampleIndex + 1) + ", " + formatWaveNumeric(point.value) + (unit ? " " + unit : "") + ")";
  }

  function ensureWaveProbe(host) {
    var probe = host.querySelector(".icu-wave-probe");
    if (probe) {
      return probe;
    }
    probe = document.createElement("div");
    probe.className = "icu-wave-probe";
    probe.innerHTML = [
      '<div class="icu-wave-probe-v"></div>',
      '<div class="icu-wave-probe-h"></div>',
      '<div class="icu-wave-probe-dot"></div>',
      '<div class="icu-wave-probe-label"></div>'
    ].join("");
    host.appendChild(probe);
    return probe;
  }

  function updateWaveProbe(host, plot, index, color, label) {
    if (!host || !plot || !plot.points.length) {
      return;
    }
    index = Math.max(0, Math.min(plot.points.length - 1, index || 0));
    var point = plot.points[index];
    var probe = ensureWaveProbe(host);
    var left = (point.x / plot.viewWidth) * 100;
    var top = (point.y / plot.viewHeight) * 100;
    probe.style.setProperty("--probe-left", left + "%");
    probe.style.setProperty("--probe-top", top + "%");
    probe.style.setProperty("--probe-color", color || "#ffffff");
    probe.classList.toggle("align-right", left > 68);
    var labelNode = probe.querySelector(".icu-wave-probe-label");
    if (labelNode) {
      labelNode.textContent = label || "";
    }
  }

  function waveIndexFromEvent(event, host, plot) {
    if (!host || !plot || !plot.points.length || !host.getBoundingClientRect) {
      return 0;
    }
    var rect = host.getBoundingClientRect();
    if (!rect.width) {
      return plot.points.length - 1;
    }
    var point = event;
    if (event && event.changedTouches && event.changedTouches.length) {
      point = event.changedTouches[0];
    }
    var clientX = point && point.clientX;
    if (!isFinite(clientX)) {
      return plot.points.length - 1;
    }
    var ratio = (clientX - rect.left) / rect.width;
    ratio = Math.max(0, Math.min(1, ratio));
    return Math.round(ratio * (plot.points.length - 1));
  }

  // ★ 首次进入时把 4 个护理模式强制标记为"全部关闭"（未激活）
  // 防止 Java 第一次推送 state 之前，模式图标显示异常状态
  function initHostModesOff() {
    if (pageName() === "lanhu_1icuzhuangtaikaobei") {
      renderStatusControlSwitch(".image-wrapper_1", "control_nebulizer", false);
      renderStatusControlSwitch(".image-wrapper_2", "control_warm_light", false);
      renderStatusControlSwitch(".image-wrapper_3", "control_inner", false);
      return;
    }
    if (pageName() !== "lanhu_2zhujikongzhi") {
      return;
    }
    var iconSelectors = [
      ".group_47",   // 母幼护理
      ".group_52",   // 术后护理
      ".group_57",   // 心肺护理
      ".box_47"      // 自定义
    ];
    for (var i = 0; i < iconSelectors.length; i += 1) {
      var el = document.querySelector(iconSelectors[i]);
      if (!el) continue;
      el.classList.remove("on");
      el.classList.add("icu-mode-off");
      // ★ 强制设置关闭样式
      el.style.opacity = "0.4";
      el.style.filter = "grayscale(100%)";
    }
    // 同时把 fake 元素 on/off 也初始化
    var switchSelectors = [
      ".image_15",   // 母幼
      ".image_17",   // 术后
      ".image_19",   // 心肺
      ".image_21"    // 自定义
    ];
    for (var j = 0; j < switchSelectors.length; j += 1) {
      var imgNode = document.querySelector(switchSelectors[j]);
      if (!imgNode) continue;
      var parent = imgNode.parentNode;
      if (!parent) continue;
      var fake = parent.querySelector(".icu-switch-fake");
      if (fake) {
        fake.classList.remove("on");
        fake.classList.add("off");
      }
    }
    var controlSwitches = [
      { selector: ".image_9", action: "control_red" },
      { selector: ".image_10", action: "control_blue" },
      { selector: ".image_11", action: "control_uv" },
      { selector: ".image_12", action: "control_nebulizer" },
      { selector: ".image_13", action: "control_anion" },
      { selector: ".image_14", action: "control_cold_light" },
      { selector: ".image_16", action: "control_warm_light" },
      { selector: ".image_18", action: "control_outer" },
      { selector: ".image_20", action: "control_inner" }
    ];
    for (var controlIndex = 0; controlIndex < controlSwitches.length; controlIndex += 1) {
      renderHostControlSwitch(controlSwitches[controlIndex].selector, controlSwitches[controlIndex].action, false);
    }
  }

  function renderHostModes(state) {
    // 开关和倒计时必须共用 bleState 的实时主机数据。firstPhaseState 仅保留页面配置，
    // 否则其较旧的仪表盘快照会在下一次轮询把刚下发的开关重置为关闭。
    var liveState = readBleState();
    var hostState = liveState.host || (state.ble || {}).host || state.host || {};
    var controls = hostState.controls || {};
    if (pageName() === "lanhu_1icuzhuangtaikaobei") {
      var nebulizerOn = !!controls.nebulizerOn;
      var warmLightOn = !!controls.warmLightOn;
      var innerCycleOn = !!controls.innerCycleOn;
      renderStatusControlSwitch(".image-wrapper_1", "control_nebulizer", nebulizerOn);
      renderStatusControlSwitch(".image-wrapper_2", "control_warm_light", warmLightOn);
      renderStatusControlSwitch(".image-wrapper_3", "control_inner", innerCycleOn);
      renderStatusControlLabel(".text_73", nebulizerOn);
      renderStatusControlLabel(".text_75", warmLightOn);
      renderStatusControlLabel(".text_77", innerCycleOn);
      return;
    }
    if (pageName() !== "lanhu_2zhujikongzhi") {
      return;
    }
    var controlSwitches = [
      { selector: ".image_9", action: "control_red", on: !!controls.redTherapyOn },
      { selector: ".image_10", action: "control_blue", on: !!controls.blueTherapyOn },
      { selector: ".image_11", action: "control_uv", on: !!controls.uvOn },
      { selector: ".image_12", action: "control_nebulizer", on: !!controls.nebulizerOn },
      { selector: ".image_13", action: "control_anion", on: !!controls.anionOn },
      { selector: ".image_14", action: "control_cold_light", on: !!controls.coldLightOn },
      { selector: ".image_16", action: "control_warm_light", on: !!controls.warmLightOn },
      { selector: ".image_18", action: "control_outer", on: !!controls.outerCycleOn },
      { selector: ".image_20", action: "control_inner", on: !!controls.innerCycleOn }
    ];
    for (var controlIndex = 0; controlIndex < controlSwitches.length; controlIndex += 1) {
      var control = controlSwitches[controlIndex];
      renderHostControlSwitch(control.selector, control.action, control.on);
    }
    var modeSelectors = [
      { text: ".text_104", icon: ".group_47", toggle: ".image_15", action: "host_mode_mother" },
      { text: ".text_106", icon: ".group_52", toggle: ".image_17", action: "host_mode_postop" },
      { text: ".text_108", icon: ".group_57", toggle: ".image_19", action: "host_mode_cardio" },
      { text: ".text_110", icon: ".box_47", toggle: ".image_21", action: "host_mode_custom" }
    ];
    for (var i = 0; i < modeSelectors.length; i += 1) {
      var mode = (state.hostModes || [])[i] || {};
      var textNode = document.querySelector(modeSelectors[i].text);
      var iconNode = document.querySelector(modeSelectors[i].icon);
      if (textNode) {
        textNode.textContent = display(mode.name);
      }
      if (iconNode) {
        // ★ 关键：先重置内联灰显样式，避免切回激活状态时保留 inline 灰色
        iconNode.classList.remove("icu-mode-dot", "on", "last", "icu-mode-off", "icu-mode-on");
        var active = !!mode.active;
        if (active) {
          iconNode.classList.add("on");
          iconNode.style.opacity = "";      // ★ 清除之前的灰显
          iconNode.style.filter = "";
        } else {
          iconNode.classList.add("icu-mode-off");
          iconNode.style.opacity = "0.4";
          iconNode.style.filter = "grayscale(100%)";
        }
      }
      renderHostControlSwitch(modeSelectors[i].toggle, modeSelectors[i].action, active);
      ensureModeSettingsButton(modeSelectors[i].icon, modeSelectors[i].toggle, modeSelectors[i].action, mode.name);
    }
  }

  function renderHostMonitorLevel(state) {
    if (pageName() !== "lanhu_2zhujikongzhi") {
      return;
    }
    var card = document.querySelector(".box_17");
    if (!card) {
      return;
    }
    var status = hostMonitorLevelState(state);
    var titleNode = card.querySelector(".text_76");
    var pill = card.querySelector(".text-wrapper_28");
    var labelNode = card.querySelector(".text_77");
    if (titleNode) {
      titleNode.textContent = "监护等级";
    }
    if (labelNode) {
      labelNode.removeAttribute("data-icu-live");
      labelNode.textContent = status.label;
      labelNode.style.width = "auto";
      labelNode.style.margin = "0";
      labelNode.style.lineHeight = "30px";
      labelNode.style.textAlign = "center";
    }
    if (pill) {
      pill.style.backgroundColor = status.color === "red"
        ? "rgba(233, 94, 94, 1)"
        : (status.color === "green" ? "rgba(23, 177, 41, 1)" : "rgba(255, 162, 0, 1)");
      pill.style.display = "flex";
      pill.style.alignItems = "center";
      pill.style.justifyContent = "center";
    }
    // ★ P2-问题9 修复:监护等级顺序调换 —— 三级(绿)最左,一级(红)最右。
    //   DOM 顺序与几何位置保持不变(选中环 section_10/11/12 是绝对定位在
    //   left 21/61/104px,改 DOM 顺序会让环与色点错位),
    //   这里只交换 block_16 / block_18 的动作与标签,
    //   对应的色点背景色在 lanhu-2.css 中一并交换。
    setHostMonitorLevelButton(card.querySelector(".block_16"), "monitor_level_green", "三级", status.color === "green", "rgba(23, 177, 41, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".block_17"), "monitor_level_yellow", "二级", status.color === "yellow", "rgba(255, 162, 0, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".block_18"), "monitor_level_red", "一级", status.color === "red", "rgba(233, 94, 94, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".section_10"), "monitor_level_green", "三级", status.color === "green", "rgba(23, 177, 41, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".section_11"), "monitor_level_yellow", "二级", status.color === "yellow", "rgba(255, 162, 0, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".section_12"), "monitor_level_red", "一级", status.color === "red", "rgba(233, 94, 94, 0.35)");
  }

  // ★ 0904 自测：护理模式“开启”与“设置”分离。
  //   开关仍触发 host_mode_*（原生直接开启/关闭）；
  //   “设置”入口点击打开对应模式参数弹窗（host_mode_*_settings）。
  //
  // ★ 0907 复核调整：设置按钮改挂到右侧模式图标下方，不再挂在开关下方。
  //   原实现把按钮 append 进开关所在列（.section_41 等，固定 54px 高的
  //   flex-col + justify-between），多出第三个子节点后列内空间被重新分配，
  //   开关被挤离设计稿位置。现在按钮 append 进右侧图标容器并绝对定位到
  //   图标下方（图标本身在 CSS 里上移让出空位），开关列恢复“标签 + 开关”
  //   两个子节点，位置与设计稿一致。
  function ensureModeSettingsButton(iconSelector, toggleSelector, action, modeName) {
    if (!iconSelector || !action) {
      return;
    }
    var icon = document.querySelector(iconSelector);
    var row = icon ? icon.parentNode : null;
    if (!icon || !row) {
      return;
    }
    // 清理旧版本遗留在开关列或图标内部的按钮，否则开关位置仍会被顶偏，
    // 或按钮会跟随未启用图标一起变灰。
    var toggleImg = toggleSelector ? document.querySelector(toggleSelector) : null;
    var toggleParent = toggleImg ? toggleImg.parentNode : null;
    var staleParents = [];
    if (toggleParent && toggleParent !== row) {
      staleParents.push(toggleParent);
    }
    if (icon !== row) {
      staleParents.push(icon);
    }
    for (var p = 0; p < staleParents.length; p += 1) {
      var stale = staleParents[p].querySelectorAll(".icu-mode-settings");
      for (var s = 0; s < stale.length; s += 1) {
        if (stale[s].parentNode) {
          stale[s].parentNode.removeChild(stale[s]);
        }
      }
    }
    var button = row.querySelector(".icu-mode-settings");
    if (!button) {
      button = document.createElement("span");
      button.className = "icu-mode-settings";
      row.appendChild(button);
    }
    button.textContent = "设置";
    button.setAttribute("data-native-action", action + "_settings");
    button.setAttribute("title", "设置" + (modeName || "") + "参数");
    button.style.cursor = "pointer";
    button.style.touchAction = "manipulation";
  }

  function setHostMonitorLevelButton(node, action, label, active, glow) {
    if (!node) {
      return;
    }
    node.setAttribute("data-native-action", action);
    node.setAttribute("title", "点击切换为" + label);
    node.style.cursor = "pointer";
    node.style.touchAction = "manipulation";
    node.style.transition = "transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease, filter 0.18s ease";
    // ★ active: 高亮 + 外发光;inactive: 透明度降到 0.35 + grayscale 完全变灰
    // 之前 opacity 0.9 几乎看不出区别,用户感觉"切换没反应"
    node.style.opacity = active ? "1" : "0.35";
    node.style.transform = active ? "scale(1.2)" : "scale(1)";
    node.style.filter = active ? "none" : "grayscale(100%)";
    node.style.boxShadow = active
      ? "0 0 0 3px rgba(255, 255, 255, 1), 0 0 0 5px " + glow
      : "none";
  }

  function renderStatusMonitorLevel(state) {
    if (pageName() !== "lanhu_1icuzhuangtaikaobei") {
      return;
    }
    var card = document.querySelector(".section_10");
    if (!card) {
      return;
    }
    var status = hostMonitorLevelState(state);
    var titleNode = card.querySelector(".text_66");
    var pill = card.querySelector(".text-wrapper_24");
    var labelNode = card.querySelector(".text_67");
    if (titleNode) {
      titleNode.textContent = "监护等级";
    }
    if (labelNode) {
      labelNode.removeAttribute("data-icu-live");
      labelNode.textContent = status.label;
      labelNode.style.width = "auto";
      labelNode.style.margin = "0";
      labelNode.style.lineHeight = "16px";
      labelNode.style.textAlign = "center";
    }
    if (pill) {
      pill.style.backgroundColor = status.color === "red"
        ? "rgba(233, 94, 94, 1)"
        : (status.color === "green" ? "rgba(23, 177, 41, 1)" : "rgba(255, 162, 0, 1)");
      pill.style.display = "flex";
      pill.style.alignItems = "center";
      pill.style.justifyContent = "center";
    }
    // ★ P2-问题9 修复:ICU状态页同样调换为 三级(绿)最左 / 一级(红)最右。
    //   与主机控制页做法一致:只换动作与标签,几何位置不动
    //   (环 box_28/29/30 绝对定位 left:21/61/104px),色点配色在 lanhu-1.css 交换。
    setHostMonitorLevelButton(card.querySelector(".box_25"), "monitor_level_green", "三级", status.color === "green", "rgba(23, 177, 41, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".box_26"), "monitor_level_yellow", "二级", status.color === "yellow", "rgba(255, 162, 0, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".box_27"), "monitor_level_red", "一级", status.color === "red", "rgba(233, 94, 94, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".box_28"), "monitor_level_green", "三级", status.color === "green", "rgba(23, 177, 41, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".box_29"), "monitor_level_yellow", "二级", status.color === "yellow", "rgba(255, 162, 0, 0.35)");
    setHostMonitorLevelButton(card.querySelector(".box_30"), "monitor_level_red", "一级", status.color === "red", "rgba(233, 94, 94, 0.35)");
  }

  function renderHostControlSwitch(selector, action, on) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      var img = nodes[i];
      var parent = img.parentNode;
      if (!parent) {
        continue;
      }
      var fake = parent.querySelector('.icu-switch-fake[data-for="' + selector + '"]');
      if (!fake) {
        fake = document.createElement("span");
        fake.className = "icu-switch-fake";
        fake.setAttribute("data-for", selector);
        parent.insertBefore(fake, img.nextSibling);
      }
      fake.setAttribute("data-native-action", action);
      fake.setAttribute("title", on ? "点击切换模式" : "点击开启模式");
      fake.classList.toggle("on", !!on);
      fake.classList.toggle("off", !on);
      img.style.display = "none";
    }
  }

  function renderStatusControlSwitch(selector, action, on) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i += 1) {
      var wrapper = nodes[i];
      var image = wrapper.querySelector("img");
      var fake = wrapper.querySelector('.icu-switch-fake[data-for="' + selector + '"]');
      if (!fake) {
        fake = document.createElement("span");
        fake.className = "icu-switch-fake";
        fake.setAttribute("data-for", selector);
        wrapper.appendChild(fake);
      }
      fake.setAttribute("data-native-action", action);
      fake.setAttribute("title", on ? "点击关闭" : "点击开启");
      fake.classList.toggle("on", !!on);
      fake.classList.toggle("off", !on);
      if (image) {
        image.style.display = "none";
      }
      wrapper.style.visibility = "visible";
    }
  }

  function renderStatusControlLabel(selector, on) {
    var node = document.querySelector(selector);
    if (node) {
      node.textContent = on ? "开" : "关";
    }
  }


  function renderTreatments(state) {
    if (pageName() !== "lanhu_6zhiliaojilu") {
      return;
    }
    var container = document.querySelector(".block_6");
    if (!container) {
      return;
    }
    container.classList.add("icu-treatment-table");
    var header = container.querySelector(".text-wrapper_24");
    ensureTreatmentActions(container);
    renderTreatmentPeriodControls(container, state);
    if (header) {
      header.classList.add("icu-treatment-header");
      markTreatmentHeaderCells(header.querySelectorAll("span"));
    }
    var rows = [".group_51", ".group_6", ".group_52", ".group_8", ".group_53", ".group_10", ".group_54", ".group_12", ".group_55", ".group_14", ".group_56", ".group_16", ".group_57", ".group_18", ".group_58", ".group_20", ".group_59", ".group_22", ".group_60", ".group_24", ".group_61", ".box_25"];
    var treatments = state.treatments || [];
    for (var i = 0; i < rows.length; i += 1) {
      var row = container.querySelector(rows[i]);
      if (!row) {
        continue;
      }
      row.classList.add("icu-treatment-row");
      var item = treatments[i];
      if (!item) {
        clearTreatmentRow(row);
        row.style.visibility = "hidden";
        row.removeAttribute("data-native-action");
        row.classList.remove("icu-treatment-selected");
        continue;
      }
      row.style.visibility = "";
      row.classList.toggle("icu-treatment-selected", selectedTreatmentRow === i);
      row.setAttribute("data-native-action", "treatment_edit_" + i);
      row.setAttribute("title", "编辑治疗记录 - " + display(item.itemName));
      bindTreatmentRowSelection(row, i);
      var spans = row.querySelectorAll("span");
      markTreatmentRowCells(row, spans);
      var values = [
        pad2(item.index || i + 1),
        display(item.itemName),
        display(item.startTime),
        display(item.endTime),
        treatmentPeriodLabel(item, state),
        display(item.average),
        display(item.high),
        display(item.low)
      ];
      for (var j = 0; j < spans.length && j < values.length; j += 1) {
        spans[j].textContent = values[j];
        spans[j].setAttribute("title", values[j]);
      }
      setTreatmentChecks(row, item);
    }
    setText(".text_60", "治疗记录表");
    setText(".text_61", "手动维护");
    var manual = document.querySelector(".text_61");
    if (manual) {
      manual.setAttribute("data-native-action", "treatment_sample");
      manual.style.cursor = "pointer";
    }
  }

  function ensureTreatmentActions(container) {
    if (!container || container.querySelector(".icu-treatment-actions")) {
      return;
    }
    var bar = container.querySelector(".text-wrapper_48") || container;
    var actions = document.createElement("div");
    actions.className = "icu-treatment-actions";
    actions.innerHTML = [
      '<button type="button" data-native-action="treatment_sample">写入一次采样</button>',
      '<button type="button" data-native-action="treatment_new">新建治疗记录</button>',
      '<button type="button" data-native-action="treatment_finish">结束当前治疗</button>',
      '<span class="icu-treatment-period">',
      '<button type="button" data-period-mode="all" data-native-action="treatment_period_all">全时段</button>',
      '<button type="button" data-period-mode="enabled" data-native-action="treatment_period_enabled">功能开启时段</button>',
      '<button type="button" data-period-mode="custom" data-native-action="treatment_period_custom">自定义时段</button>',
      '</span>'
    ].join("");
    bar.appendChild(actions);
  }

  function renderTreatmentPeriodControls(container, state) {
    var mode = state.treatmentPeriodMode || "all";
    var buttons = container.querySelectorAll("[data-period-mode]");
    for (var i = 0; i < buttons.length; i += 1) {
      buttons[i].classList.toggle("active", buttons[i].getAttribute("data-period-mode") === mode);
    }
  }

  function treatmentPeriodLabel(item, state) {
    var name = clean(item && item.itemName);
    var mode = clean(state && state.treatmentPeriodMode) || "all";
    var explicit = clean(item && item.period);
    if (!name) {
      return empty;
    }
    if (explicit === "\u5168\u65f6\u6bb5" || explicit === "\u6253\u5f00\u65f6\u6bb5" || explicit === "\u529f\u80fd\u5f00\u542f\u65f6\u6bb5" || explicit === "\u81ea\u5b9a\u4e49\u65f6\u6bb5") {
      return explicit === "\u6253\u5f00\u65f6\u6bb5" ? "\u529f\u80fd\u5f00\u542f\u65f6\u6bb5" : explicit;
    }
    if (mode === "custom") {
      return "\u81ea\u5b9a\u4e49\u65f6\u6bb5";
    }
    if (mode === "enabled" || isEnabledWindowTreatment(name)) {
      return "\u529f\u80fd\u5f00\u542f\u65f6\u6bb5";
    }
    return "\u5168\u65f6\u6bb5";
  }

  function isEnabledWindowTreatment(name) {
    return /\u7ea2\u5916|\u84dd\u5149|\u96fe\u5316|\u8d1f\u79bb\u5b50|\u7d2b\u5916/.test(name);
  }

  function clearTreatmentColClasses(node) {
    for (var i = 0; i <= 12; i += 1) {
      node.classList.remove("icu-col-" + i);
    }
  }

  function markTreatmentCell(node, col) {
    if (!node) {
      return;
    }
    node.classList.add("icu-treatment-cell");
    clearTreatmentColClasses(node);
    node.classList.add("icu-col-" + col);
    node.style.gridColumn = String(col + 1);
    node.style.gridRow = "1";
  }

  function markTreatmentHeaderCells(spans) {
    for (var i = 0; i < spans.length; i += 1) {
      markTreatmentCell(spans[i], i);
    }
  }

  function markTreatmentRowCells(row, spans) {
    var spanCols = [0, 1, 2, 3, 6, 7, 8, 9];
    for (var i = 0; i < spans.length && i < spanCols.length; i += 1) {
      markTreatmentCell(spans[i], spanCols[i]);
    }
    var icons = row.querySelectorAll("img");
    for (var j = 0; j < icons.length; j += 1) {
      icons[j].classList.add("icu-treatment-icon");
      icons[j].style.gridColumn = String(j + 5);
      icons[j].style.gridRow = "1";
    }
  }

  function bindTreatmentRowSelection(row, index) {
    row.setAttribute("data-treatment-index", String(index));
    if (row.getAttribute("data-treatment-select-ready") === "1") {
      return;
    }
    row.setAttribute("data-treatment-select-ready", "1");
    function selectRow() {
      var parsed = Number(row.getAttribute("data-treatment-index") || -1);
      if (parsed < 0 || selectedTreatmentRow === parsed) {
        return;
      }
      selectedTreatmentRow = parsed;
      var rows = document.querySelectorAll(".icu-treatment-row");
      for (var i = 0; i < rows.length; i += 1) {
        rows[i].classList.toggle("icu-treatment-selected", rows[i] === row);
      }
    }
    row.addEventListener("touchstart", selectRow, { passive: true });
    row.addEventListener("mousedown", selectRow, true);
    row.addEventListener("click", selectRow, true);
  }

  function clearTreatmentRow(row) {
    var spans = row.querySelectorAll("span");
    for (var i = 0; i < spans.length; i += 1) {
      spans[i].textContent = "";
      spans[i].removeAttribute("title");
    }
    var icons = row.querySelectorAll("img");
    for (var j = 0; j < icons.length; j += 1) {
      icons[j].style.opacity = "0";
    }
  }

  function treatmentHasData(item) {
    return !!item && (
      Number(item.sampleCount || 0) > 0 ||
      !!clean(item.manualAverageValue) ||
      !!clean(item.manualHighValue) ||
      !!clean(item.manualLowValue) ||
      !!clean(item.manualValue) ||
      !!clean(item.lastValue) ||
      !!clean(item.average) ||
      !!clean(item.high) ||
      !!clean(item.low)
    );
  }

  function setTreatmentChecks(row, item) {
    var icons = row.querySelectorAll("img");
    var hasData = treatmentHasData(item);
    var states = [!!(item && item.enabled), !!(item && item.includeInReport)];
    for (var i = 0; i < icons.length && i < states.length; i += 1) {
      icons[i].style.opacity = hasData ? (states[i] ? "1" : "0.28") : "0";
      icons[i].style.filter = hasData && states[i] ? "" : "grayscale(1)";
      icons[i].setAttribute("title", hasData ? (states[i] ? "是" : "否") : "");
      continue;
      icons[i].setAttribute("title", states[i] ? "是" : "否");
    }
  }

  function renderReport(state) {
    if (pageName() !== "lanhu_baogao") {
      return;
    }
    var p = patient(state);
    var organization = org(state);
    var host = host(state);
    var controls = host.controls || {};
    var monitor = monitor(state);
    var empty = "--";
    function reportValue(value) {
      return clean(value) || empty;
    }
    function setReportText(selector, label, value) {
      setText(selector, reportValue(value), label);
    }
    var reportLogo = document.querySelector(".text_1");
    var reportLogoUri = clean(organization.logoUri);
    if (reportLogoUri) {
      reportLogo.textContent = "";
      reportLogo.style.backgroundImage = 'url("' + reportLogoUri.replace(/"/g, "%22") + '")';
      reportLogo.style.backgroundSize = "contain";
      reportLogo.style.backgroundPosition = "center";
      reportLogo.style.backgroundRepeat = "no-repeat";
    } else {
      reportLogo.style.backgroundImage = "";
      setText(".text_1", clean(organization.logoName) || "LOGO");
    }
    setText(".text_2", reportValue(organization.name));
    setReportText(".text_4", "动物名称：", p.petName);
    setReportText(".text_5", "住院号：", p.recordNo || p.caseNo);
    setReportText(".text_6", "主人：", p.ownerName);
    setReportText(".text_7", "治疗日期：", toChineseDateTime(p.visitDate));
    setReportText(".text_8", "舱 位：", host.zone === "left" ? "左舱" : host.zone === "right" ? "右舱" : "--");
    setReportText(".text_9", "动物种类：", p.species);
    setReportText(".text_10", "体重：", p.weight);
    setReportText(".text_11", "科 室：", p.department);
    setReportText(".text_12", "治疗时长：", host.treatmentTime);
    setReportText(".text_13", "操作医师：", p.doctor);

    // 固定标题和表头不参与业务数据映射，避免通用页面渲染覆盖报告模板文案。
    setText(".text_14", "【治疗项目】");
    setText(".text_16", "【生命体征记录】");
    setText(".text_17", "时间");
    setText(".text_18", "心率");
    setText(".text_19", "血氧");
    setText(".text_20", "血压");
    setText(".text_21", "体温");
    setText(".paragraph_1", "目标温度：" + reportValue(host.temp) + "\n氧浓度：" + reportValue(host.oxygen));
    setText(".text_15", "时长：" + reportValue(controls.nebulizer));
    setText(".paragraph_2", "红光治疗：" + reportValue(controls.redTherapy) + "\n负离子治疗：" + reportValue(controls.anion));

    var vitalRows = [
      [".text_22", ".text_23", ".text_24", ".text_25", ".text_26"],
      [".text_27", ".text_28", ".text_29", ".text_30", ".text_31"],
      [".text_32", ".text_33", ".text_34", ".text_35", ".text_36"],
      [".text_37", ".text_38", ".text_39", ".text_40", ".text_41"]
    ];
    var vitalValues = [toChineseDateTime(p.visitDate), monitor.heartRate, monitor.spo2, monitor.bloodPressure, monitor.bodyTemp];
    for (var i = 0; i < vitalRows.length; i += 1) {
      for (var j = 0; j < vitalRows[i].length; j += 1) {
        setText(vitalRows[i][j], reportValue(vitalValues[j]));
      }
    }

    setReportText(".text_46", "舱内温度：", host.temp);
    setReportText(".text_47", "湿度：", host.humidity);
    setText(".text_48", "CO");
    setText(".text_49", "2");
    setReportText(".text_50", "浓度：", host.co2);
    setReportText(".text_51", "氧浓度：", host.oxygen);
    setText(".text_60", reportValue((state && state.alarmEvent) || (state && state.alarm)));
    setText(".text_66", reportValue(p.note));
    setReportText(".text_67", "复诊时间：", p.followUpDate);
    setReportText(".text_68", "操作医师：", p.doctor || "_________");
    setText(".text_70", "主管护士：_________              动物主人：_________");
    setReportText(".text_71", "打印时间：", new Date().toLocaleString());
    setReportText(".text_72", "医院电话：", organization.phone);
    setReportText(".text_73", "医院地址：", organization.address);
  }

  function renderTutorial() {
    if (pageName() !== "lanhu_7jiaocheng") {
      return;
    }
    cleanupTutorialPanel();
    // 6 个区域全部点击打开平板相册对应视频（按日期降序 0~5）
    setTutorialAction(".group_9", "tutorial_video_0");
    setTutorialAction(".group_10", "tutorial_video_1");
    setTutorialAction(".image-wrapper_1", "tutorial_video_2");
    setTutorialAction(".image-wrapper_2", "tutorial_video_3");
    setTutorialAction(".image-wrapper_3", "tutorial_video_4");
    setTutorialAction(".image-wrapper_4", "tutorial_video_5");

    // 从 native 取教程页状态：相册权限、前 6 张图片 URI、前 6 段视频 _ID、是否首次
    var state = readTutorialState();

    // 未授权：弹自定义弹窗让用户点确认，再发起系统权限申请。
    // 以前是进页面就静默 requestPermissions，用户随手一拒就再也找不到在哪授权了。
    if (!state.permissionGranted) {
      showTutorialPermissionDialog(state);
      return;
    }
    closeTutorialPermissionDialog();

    // 把 6 张相册图片写到对应 <img> 的 src，空槽位保留原占位
    applyTutorialThumbnails(state);

    // 视频列表指纹对比：变化则通过 native action 触发"使用教程已更新" Toast
    var currentFp = fingerprintFromState(state);
    var storedFp = loadStoredFingerprint();

    // 相册里没有视频：不记录指纹、不弹 Toast（但缩略图仍可显示）
    if (!currentFp) {
      return;
    }
    // 首次观察：写入 JS sessionStorage + 通知 Java 持久化到 SharedPreferences
    if (!storedFp) {
      storeFingerprint(currentFp);
      if (window.IcuNative && typeof window.IcuNative.recordTutorialFingerprint === "function") {
        try { window.IcuNative.recordTutorialFingerprint(currentFp); } catch (error) {}
      }
      return;
    }
    // 指纹变化：写入新指纹，并在非首次安装时弹 Toast
    if (currentFp !== storedFp) {
      storeFingerprint(currentFp);
      if (!state.firstLoad) {
        callAction("tutorial_changed", "使用教程已更新");
      }
    }
  }

  function readTutorialState() {
    if (!window.IcuNative || typeof window.IcuNative.tutorialState !== "function") {
      return { permissionGranted: false, canAskAgain: true, thumbnails: [], videoIds: [], firstLoad: true };
    }
    try {
      var raw = window.IcuNative.tutorialState();
      return raw ? (JSON.parse(raw) || {}) : {};
    } catch (error) {
      return { permissionGranted: false, canAskAgain: true, thumbnails: [], videoIds: [], firstLoad: true };
    }
  }

  function loadStoredFingerprint() {
    try { return sessionStorage.getItem(TUTORIAL_FP_STORAGE_KEY) || ""; }
    catch (error) { return ""; }
  }

  function storeFingerprint(value) {
    try { sessionStorage.setItem(TUTORIAL_FP_STORAGE_KEY, value || ""); }
    catch (error) { /* ignore */ }
  }

  function fingerprintFromState(state) {
    var ids = (state && state.videoIds) || [];
    return ids.join(",");
  }

  var TUTORIAL_PERMISSION_DIALOG_ID = "icu-tutorial-permission-dialog";

  // 权限结果由 Java 通过 evaluateJavascript 回调，关掉弹窗并重新拉取缩略图。
  window.IcuOnTutorialPermission = function (granted) {
    closeTutorialPermissionDialog();
    if (granted) {
      renderTutorial();
    }
  };

  function closeTutorialPermissionDialog() {
    var node = document.getElementById(TUTORIAL_PERMISSION_DIALOG_ID);
    if (node && node.parentNode) {
      node.parentNode.removeChild(node);
    }
  }

  function showTutorialPermissionDialog(state) {
    // render 每秒都会跑，弹窗已存在时不能重复创建
    if (document.getElementById(TUTORIAL_PERMISSION_DIALOG_ID)) {
      return;
    }
    // 系统对话框还能不能弹：false 表示用户勾过"不再询问"，只能去系统设置开
    var canAsk = !(state && state.canAskAgain === false);
    var mask = document.createElement("div");
    mask.className = "icu-permission-mask";
    mask.id = TUTORIAL_PERMISSION_DIALOG_ID;
    mask.innerHTML = [
      '<div class="icu-permission-dialog" role="dialog" aria-modal="true">',
      '<h3 class="icu-permission-title">需要读取本机视频和图片</h3>',
      '<p class="icu-permission-body">使用教程的封面和演示视频来自平板相册。',
      '授权后即可显示教程内容；不授权则保留占位图，不影响其他功能。</p>',
      canAsk ? '' : '<p class="icu-permission-tip">该权限此前已被拒绝，请在系统设置中手动开启。</p>',
      '<div class="icu-permission-actions">',
      '<button type="button" class="icu-permission-cancel">取消</button>',
      '<button type="button" class="icu-permission-confirm">' + (canAsk ? '确认授权' : '去设置') + '</button>',
      '</div>',
      '</div>'
    ].join("");
    document.body.appendChild(mask);

    var cancel = mask.querySelector(".icu-permission-cancel");
    if (cancel) {
      cancel.addEventListener("click", function (event) {
        stopBtn(event);
        closeTutorialPermissionDialog();
      });
    }
    var confirm = mask.querySelector(".icu-permission-confirm");
    if (confirm) {
      confirm.addEventListener("click", function (event) {
        stopBtn(event);
        if (canAsk) {
          callTutorialPermissionRequest();
        } else {
          callOpenAppPermissionSettings();
        }
      });
    }
  }

  function stopBtn(event) {
    if (!event) return;
    if (event.stopPropagation) event.stopPropagation();
    if (event.preventDefault) event.preventDefault();
  }

  function callTutorialPermissionRequest() {
    if (!window.IcuNative || typeof window.IcuNative.requestTutorialPermission !== "function") return;
    try { window.IcuNative.requestTutorialPermission(); } catch (error) {}
  }

  function callOpenAppPermissionSettings() {
    if (!window.IcuNative || typeof window.IcuNative.openAppPermissionSettings !== "function") return;
    try { window.IcuNative.openAppPermissionSettings(); } catch (error) {}
  }

  function applyTutorialThumbnails(state) {
    var thumbs = (state && state.thumbnails) || [];
    for (var i = 0; i < TUTORIAL_SELECTORS.length; i += 1) {
      var node = document.querySelector(TUTORIAL_SELECTORS[i]);
      if (!node) continue;
      var src = thumbs[i];
      node.setAttribute("src", src || TUTORIAL_TRANSPARENT_PLACEHOLDER);
    }
  }

  function cleanupTutorialPanel() {
    var nodes = document.querySelectorAll(".icu-tutorial-panel");
    for (var i = 0; i < nodes.length; i += 1) {
      nodes[i].remove();
    }
  }

  function setTutorialAction(selector, action) {
    var node = document.querySelector(selector);
    if (!node) {
      return;
    }
    node.setAttribute("data-native-action", action);
    node.style.cursor = "pointer";
  }

  function tutorialCard(title, badge, body, action) {
    return [
      '<div class="icu-tutorial-card">',
      '<div><strong>' + title + '</strong><em>' + badge + '</em></div>',
      '<p>' + body + '</p>',
      '<button type="button" data-native-action="' + action + '">' + (badge === "固定文档" ? "打开" : "查看") + '</button>',
      '</div>'
    ].join("");
  }

  function pad2(value) {
    value = String(value || "");
    return value.length < 2 ? "0" + value : value;
  }

  function formatDateTime(date) {
    return date.getFullYear() + "-" +
      pad2(date.getMonth() + 1) + "-" +
      pad2(date.getDate()) + "  " +
      pad2(date.getHours()) + ":" +
      pad2(date.getMinutes()) + ":" +
      pad2(date.getSeconds());
  }

  // ★ 日期显示统一为中文形式:2026年1月1日,不用 2026-01-01。
  //   存储格式仍是 "yyyy-MM-dd HH:mm:ss" —— 它被 patientMatchKey() 当匹配键、
  //   被 Java 侧 SimpleDateFormat 解析、也是已存患者数据的既有格式,不能动。
  //   这里只做「存储 -> 显示」的单向转换,展示时才调用。
  //   月/日不补零(1月1日,不是 01月01日),秒缺省时不显示。
  var CN_DATE_RE = /^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?$/;

  function toChineseDateTime(value) {
    var text = String(value == null ? "" : value).replace(/\s+/g, " ").trim();
    if (!text) {
      return "";
    }
    var m = text.match(CN_DATE_RE);
    if (!m) {
      // 已经是中文形式、或是 "--" / "待录入" 之类的占位,原样返回。
      return text;
    }
    var out = Number(m[1]) + "年" + Number(m[2]) + "月" + Number(m[3]) + "日";
    if (m[4] != null) {
      out += " " + pad2(m[4]) + ":" + m[5];
      if (m[6] != null) {
        out += ":" + m[6];
      }
    }
    return out;
  }

  // 中文形式 -> 存储形式,供输入回填用。解析不了就原样返回。
  function fromChineseDateTime(value) {
    var text = String(value == null ? "" : value).replace(/\s+/g, " ").trim();
    if (!text) {
      return "";
    }
    var m = text.match(/^(\d{4})年(\d{1,2})月(\d{1,2})日(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?$/);
    if (!m) {
      return text;
    }
    var out = m[1] + "-" + pad2(m[2]) + "-" + pad2(m[3]);
    out += " " + pad2(m[4] == null ? 0 : m[4]) + ":" + (m[5] == null ? "00" : m[5]);
    out += ":" + (m[6] == null ? "00" : m[6]);
    return out;
  }

  function formatDateTimeCN(date) {
    return date.getFullYear() + "年" + (date.getMonth() + 1) + "月" + date.getDate() + "日 " +
      pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
  }

  // ★ 自建中文日期时间选择器,替换 <input type="datetime-local"> 的
  //   Chromium 原生弹窗(那个是英文的 "Set date and time / Jul-Aug-Sep / AM-PM",
  //   语言跟 WebView locale 走,CSS/JS 改不到它内部,只能自己实现)。
  //   年/月/日/时/分五列,标题与按钮全中文,回调拿到的是存储格式字符串。
  var CN_PICKER_CLASS = "icu-cn-datetime";

  function daysInMonth(year, month) {
    return new Date(year, month, 0).getDate();
  }

  function openChineseDateTimePicker(initialValue, onConfirm) {
    var base = new Date();
    var parsed = String(initialValue == null ? "" : initialValue).trim().match(CN_DATE_RE);
    if (parsed) {
      base = new Date(
        Number(parsed[1]),
        Number(parsed[2]) - 1,
        Number(parsed[3]),
        parsed[4] == null ? 0 : Number(parsed[4]),
        parsed[5] == null ? 0 : Number(parsed[5]),
        0
      );
      if (isNaN(base.getTime())) {
        base = new Date();
      }
    }

    var sel = {
      year: base.getFullYear(),
      month: base.getMonth() + 1,
      day: base.getDate(),
      hour: base.getHours(),
      minute: base.getMinutes()
    };

    var overlay = document.createElement("div");
    overlay.className = CN_PICKER_CLASS;
    overlay.innerHTML =
      '<div class="' + CN_PICKER_CLASS + '__panel" role="dialog" aria-modal="true" aria-label="选择日期和时间">' +
        '<div class="' + CN_PICKER_CLASS + '__title">选择日期和时间</div>' +
        '<div class="' + CN_PICKER_CLASS + '__preview" data-cn-preview></div>' +
        '<div class="' + CN_PICKER_CLASS + '__cols">' +
          '<div class="' + CN_PICKER_CLASS + '__col" data-cn-col="year"><div class="' + CN_PICKER_CLASS + '__colname">年</div><div class="' + CN_PICKER_CLASS + '__list" data-cn-list="year"></div></div>' +
          '<div class="' + CN_PICKER_CLASS + '__col" data-cn-col="month"><div class="' + CN_PICKER_CLASS + '__colname">月</div><div class="' + CN_PICKER_CLASS + '__list" data-cn-list="month"></div></div>' +
          '<div class="' + CN_PICKER_CLASS + '__col" data-cn-col="day"><div class="' + CN_PICKER_CLASS + '__colname">日</div><div class="' + CN_PICKER_CLASS + '__list" data-cn-list="day"></div></div>' +
          '<div class="' + CN_PICKER_CLASS + '__col" data-cn-col="hour"><div class="' + CN_PICKER_CLASS + '__colname">时</div><div class="' + CN_PICKER_CLASS + '__list" data-cn-list="hour"></div></div>' +
          '<div class="' + CN_PICKER_CLASS + '__col" data-cn-col="minute"><div class="' + CN_PICKER_CLASS + '__colname">分</div><div class="' + CN_PICKER_CLASS + '__list" data-cn-list="minute"></div></div>' +
        '</div>' +
        '<div class="' + CN_PICKER_CLASS + '__actions">' +
          '<button type="button" data-cn-act="now">此刻</button>' +
          '<span class="' + CN_PICKER_CLASS + '__spacer"></span>' +
          '<button type="button" data-cn-act="cancel">取消</button>' +
          '<button type="button" class="' + CN_PICKER_CLASS + '__primary" data-cn-act="ok">确定</button>' +
        '</div>' +
      '</div>';

    function close() {
      if (overlay.parentNode) {
        overlay.parentNode.removeChild(overlay);
      }
      document.removeEventListener("keydown", onKey, true);
    }

    function onKey(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        close();
      }
    }

    function storageValue() {
      return sel.year + "-" + pad2(sel.month) + "-" + pad2(sel.day) + " " +
        pad2(sel.hour) + ":" + pad2(sel.minute) + ":00";
    }

    function renderColumn(key, from, to, labelFn) {
      var list = overlay.querySelector('[data-cn-list="' + key + '"]');
      var html = [];
      for (var v = from; v <= to; v++) {
        html.push('<button type="button" data-cn-pick="' + key + '" data-cn-value="' + v + '"' +
          (v === sel[key] ? ' class="is-on"' : '') + '>' + (labelFn ? labelFn(v) : v) + '</button>');
      }
      list.innerHTML = html.join("");
    }

    // 滚动定位必须在所有列都写完 innerHTML 之后单独跑一遍:
    // 在 renderColumn 内部就读 offsetTop/clientHeight 时,面板可能还没完成布局
    // (首次 open 时尚未插入或刚插入),量出来的值不对,选中项会跑到可视区外。
    function centerSelected() {
      var keys = ["year", "month", "day", "hour", "minute"];
      for (var i = 0; i < keys.length; i++) {
        var list = overlay.querySelector('[data-cn-list="' + keys[i] + '"]');
        if (!list) {
          continue;
        }
        var on = list.querySelector(".is-on");
        if (on && list.clientHeight) {
          list.scrollTop = Math.max(0, on.offsetTop - (list.clientHeight - on.offsetHeight) / 2);
        }
      }
    }

    function renderAll() {
      var thisYear = new Date().getFullYear();
      // 年份范围要保证已有值一定在列表里(旧档可能是更早的年份)。
      var minYear = Math.min(thisYear - 10, sel.year);
      var maxYear = Math.max(thisYear + 10, sel.year);
      renderColumn("year", minYear, maxYear);
      renderColumn("month", 1, 12);
      var max = daysInMonth(sel.year, sel.month);
      if (sel.day > max) {
        sel.day = max;
      }
      renderColumn("day", 1, max);
      renderColumn("hour", 0, 23, pad2);
      renderColumn("minute", 0, 59, pad2);
      overlay.querySelector("[data-cn-preview]").textContent = toChineseDateTime(storageValue());
      centerSelected();
    }

    overlay.addEventListener("click", function (event) {
      if (event.target === overlay) {
        close();
        return;
      }
      var pick = event.target.getAttribute && event.target.getAttribute("data-cn-pick");
      if (pick) {
        sel[pick] = Number(event.target.getAttribute("data-cn-value"));
        renderAll();
        return;
      }
      var act = event.target.getAttribute && event.target.getAttribute("data-cn-act");
      if (act === "cancel") {
        close();
      } else if (act === "now") {
        var n = new Date();
        sel.year = n.getFullYear();
        sel.month = n.getMonth() + 1;
        sel.day = n.getDate();
        sel.hour = n.getHours();
        sel.minute = n.getMinutes();
        renderAll();
      } else if (act === "ok") {
        var value = storageValue();
        close();
        if (typeof onConfirm === "function") {
          onConfirm(value);
        }
      }
    });

    document.addEventListener("keydown", onKey, true);
    document.body.appendChild(overlay);
    renderAll();
    // 首次打开时 appendChild 之后布局还没跑完,renderAll 里量到的 clientHeight
    // 可能是 0,选中项定位不准 —— 下一帧再补一次。
    if (typeof window.requestAnimationFrame === "function") {
      window.requestAnimationFrame(centerSelected);
    } else {
      window.setTimeout(centerSelected, 0);
    }
  }

  function weekdayText(date) {
    return ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"][date.getDay()];
  }

  function installActions() {
    document.addEventListener("click", function (event) {
      var node = event.target;
      while (node && node !== document.body) {
        var text = (node.textContent || "").replace(/\s+/g, "").trim();
        if (text === "手动编排" || text === "手动维护") {
          event.preventDefault();
          callAction("treatment_sample", text);
          return;
        }
        if (text === "母幼护理模式") {
          event.preventDefault();
          callAction("host_mode_mother", text);
          return;
        }
        if (text === "术后护理模式") {
          event.preventDefault();
          callAction("host_mode_postop", text);
          return;
        }
        if (text === "心肺护理模式") {
          event.preventDefault();
          callAction("host_mode_cardio", text);
          return;
        }
        if (text === "自定义模式") {
          event.preventDefault();
          callAction("host_mode_custom", text);
          return;
        }
        node = node.parentNode;
      }
    }, true);
  }

  function render() {
    installPatientTempStore();
    var state = readFirstPhaseState();
    markPageContext();
    restoreSettingsMenuLabels();
    renderHospitalSettingsPage();
    renderDeviceSettingsPage(state);
    renderCompensationSettingsPage(state);
    renderOtherSettingsPage(state);
    renderAboutSettingsPage(state);
    renderAdminSettingsPage(state);
    if (window.IcuPatientTemp && typeof window.IcuPatientTemp.importNativeRecords === "function") {
      window.IcuPatientTemp.importNativeRecords(state.cases || [], state.selectedCaseIndex || 0,
        state.zone || (state.host && state.host.zone) || (state.ble && state.ble.host && state.ble.host.zone) || "right");
    }
    normalizeDeadText();
    hideReportTemplateNavigation();
    renderCommon(state);
    renderStatusAdvice(state);
    renderMonitor(state);
    renderCamera(state);
    renderHostModes(state);
    renderStatusMonitorLevel(state);
    renderHostMonitorLevel(state);
    renderTreatments(state);
    renderReport(state);
    renderTutorial();
    if (window.IcuPatientTemp && typeof window.IcuPatientTemp.render === "function") {
      window.IcuPatientTemp.render();
      restoreSettingsMenuLabels();
    }
    syncCameraPreview();
  }

  function schedule(delay) {
    window.clearTimeout(timer);
    timer = window.setTimeout(render, delay || 0);
  }

  window.IcuCameraRuntime = {
    showPlayback: showCameraPlayback,
    showLive: showCameraLive
  };

  document.addEventListener("DOMContentLoaded", function () {
    installActions();
    // ★ 首次进入时主动初始化 4 个护理模式为"全部关闭"
    initHostModesOff();
    render();
    // ★ 修复 #12:从 setInterval(render, 1500) 改为 1000ms,并合并
    //   lanhu-bridge.js 里的 renderTabletControlStatus,统一 render 频率,
    //   减少对 firstPhaseState() 的同步阻塞调用。
    window.setInterval(render, 1000);
  });

  window.addEventListener("icu-native-state", function () {
    schedule(0);
  });

  window.addEventListener("pagehide", function () {
    nativeCameraPreview(0, 0, 0, 0, 1920, 1200, false);
  });
})();
