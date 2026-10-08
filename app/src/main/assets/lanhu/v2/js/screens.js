/* ==========================================================================
   screens.js · 35 个页面（与 PDF 的 35 页一一对应）
   ========================================================================== */
(function () {
  'use strict';
  const I = window.I, D = window.D, P = window.P;
  const SCREENS = {};

  /* 页面骨架：top 顶栏 / band 内容 / bottom 底栏 */
  /* extra（如左下角会话信息框）置于内容区内，保证不与底部导航栏重合 */
  function PAGE(top, band, bottom, noBar, extra) {
    return '<div class="screen">' + top
      + '<div class="band' + (noBar ? ' no-bar' : '') + '">' + band + '</div>'
      + (extra || '')
      + (bottom === null ? '' : (bottom || '')) + '</div>';
  }
  const A = (l, t, txt, cls) => '<div class="annot ' + (cls || '') + '" style="left:' + l + 'px;top:' + t + 'px">' + txt + '</div>';

  /* 轻量 i18n：优先覆盖高频界面；未翻译项 fallback 中文 */
  const I18N = {
    zh: {
      loginTitle: '用户登录', loginBtn: '登 录', debugPwdTitle: '调试密码验证', debugPwdPlaceholder: '请输入调试密码', debugPwdCancel: '取消', debugPwdOk: '确认进入',
      account: '账号名', password: '登录口令', confirmPwd: '确认口令', org: '机构名称（可选）', backToLogin: '已有账号，返回登录',
      navCare: '护疗', navReview: '回顾', navMenu: '菜单',
      newSample: '新建', careRecord: '护疗记录', startCare: '开始护疗', edit: '编辑', dataSend: '数据发送', del: '删除', tutorial: '教程',
      query: '查询', print: '打印', exportReport: '导出报告', sendData: '发送数据',
      status: '状态', control: '主控', monitor: '监护', video: '视频', finish: '结束',
      statusTemp: '舱内温度（℃）', statusO2: '氧浓度（%）', statusHum: '湿度（%）', statusIr: '红外体温 ℃',
      statusCo2: '二氧化碳（PPM）', statusDur: '治疗时长（h）', statusRed: '红外理疗（m）', statusBlue: '蓝光理疗（m）',
      ctrlTemp: '舱内温度 ℃', ctrlO2: '氧浓度 %', ctrlHum: '湿度 %', ctrlCo2: '二氧化碳浓度 PPM', ctrlLevel: '监护等级',
      ctrlRed: '红外理疗', ctrlBlue: '蓝光理疗', ctrlUv: '紫外消毒', ctrlNeb: '雾化器', ctrlAnion: '负离子',
      statusWarm: '暖光灯',
      ctrlCold: '冷光照明', ctrlWarm: '暖光照明', ctrlOuter: '外循环', ctrlInner: '内循环', ctrlTime: '治疗时长',
      modeMother: '母幼护理模式', modePostop: '术后护理模式', modeCardio: '心肺护理模式', modeCustom: '自定义模式',
      monHr: '心率', monBp: '血压', monSpo2: '血氧', monPr: '脉率', monTemp: '体温', monRr: '呼吸率',
      monEcg: '心电图', monPleth: '血氧波形', monResp: '呼吸率波形',
      fieldCaseNo: '住院号', fieldPetName: '宠物名', fieldAge: '年龄', fieldSex: '性别', fieldSpecies: '物种', fieldOtherSpecies: '其他物种',
      fieldOwner: '宠物主人', fieldPhone: '联系电话', fieldDoctor: '主治医生',
      setGeneral: '常规', setConn: '连接', setTransfer: '传输', setPrint: '打印', setUser: '用户', setUpgrade: '升级',
      lang: '语言', time: '时间', model: '型号', sn: '机号/SN', prodDate: '生产日期', reset: '恢复', factoryReset: '恢复出厂设置',
      langZh: '中文', langEn: 'English', sexFemale: '雌性', sexMale: '雄性', sexUnknown: '未知',
      ageDay: '天', ageMonth: '月', ageYear: '岁',
      speciesDog: '犬', speciesCat: '猫', speciesRabbit: '兔', speciesLizard: '蜥蜴', speciesSnake: '蛇', speciesOther: '其它'
    },
    en: {
      loginTitle: 'Login', loginBtn: 'Login', debugPwdTitle: 'Debug Password', debugPwdPlaceholder: 'Enter debug password', debugPwdCancel: 'Cancel', debugPwdOk: 'Confirm',
      account: 'Username', password: 'Password', confirmPwd: 'Confirm Password', org: 'Organization (optional)', backToLogin: 'Back to Login',
      navCare: 'Care', navReview: 'Review', navMenu: 'Menu',
      newSample: 'New', careRecord: 'Care Record', startCare: 'Start Care', edit: 'Edit', dataSend: 'Send Data', del: 'Delete', tutorial: 'Tutorial',
      query: 'Query', print: 'Print', exportReport: 'Export', sendData: 'Send Data',
      status: 'Status', control: 'Control', monitor: 'Monitor', video: 'Video', finish: 'Finish',
      statusTemp: 'Cabin Temp (℃)', statusO2: 'O₂ (%)', statusHum: 'Humidity (%)', statusIr: 'IR Temp ℃',
      statusCo2: 'CO₂ (PPM)', statusDur: 'Duration (h)', statusRed: 'Red Therapy (m)', statusBlue: 'Blue Therapy (m)',
      ctrlTemp: 'Cabin Temp ℃', ctrlO2: 'O₂ %', ctrlHum: 'Humidity %', ctrlCo2: 'CO₂ PPM', ctrlLevel: 'Monitor Level',
      ctrlRed: 'Red Therapy', ctrlBlue: 'Blue Therapy', ctrlUv: 'UV Sterilize', ctrlNeb: 'Nebulizer', ctrlAnion: 'Anion',
      ctrlCold: 'Cold Light', ctrlWarm: 'Warm Light', ctrlOuter: 'Outer Cycle', ctrlInner: 'Inner Cycle', ctrlTime: 'Duration',
      statusWarm: 'Warm Light',
      modeMother: 'Mother & Infant', modePostop: 'Post-op', modeCardio: 'Cardio', modeCustom: 'Custom',
      monHr: 'HR', monBp: 'BP', monSpo2: 'SpO₂', monPr: 'PR', monTemp: 'Temp', monRr: 'RR',
      monEcg: 'ECG', monPleth: 'PLETH', monResp: 'RESP',
      fieldCaseNo: 'Case No.', fieldPetName: 'Pet Name', fieldAge: 'Age', fieldSex: 'Sex', fieldSpecies: 'Species', fieldOtherSpecies: 'Other Species',
      fieldOwner: 'Owner', fieldPhone: 'Phone', fieldDoctor: 'Doctor',
      setGeneral: 'General', setConn: 'Connection', setTransfer: 'Transfer', setPrint: 'Print', setUser: 'Users', setUpgrade: 'Upgrade',
      lang: 'Language', time: 'Time', model: 'Model', sn: 'S/N', prodDate: 'Mfg. Date', reset: 'Reset', factoryReset: 'Factory Reset',
      langZh: '中文', langEn: 'English', sexFemale: 'Female', sexMale: 'Male', sexUnknown: 'Unknown',
      ageDay: 'Day', ageMonth: 'Month', ageYear: 'Year',
      speciesDog: 'Dog', speciesCat: 'Cat', speciesRabbit: 'Rabbit', speciesLizard: 'Lizard', speciesSnake: 'Snake', speciesOther: 'Other'
    }
  };
  function T(k, fb) { var lang = (window.D && window.D.lang) || 'zh'; return (I18N[lang] || I18N.zh || {})[k] || fb; }
  window.T = T; // 供 core.js（顶栏/底栏/会话栏）复用
  /* ★ 2026-10-08：样本数据里物种以中文存储（犬/猫/兔/蜥蜴/蛇/其它），
     英文界面显示时映射到物种翻译；自定义「其它物种」文本原样显示 */
  function spTxt(sp) {
    var m = { '犬': 'speciesDog', '猫': 'speciesCat', '兔': 'speciesRabbit', '蜥蜴': 'speciesLizard', '蛇': 'speciesSnake', '其它': 'speciesOther' };
    return (sp && m[sp]) ? T(m[sp], sp) : (sp || '');
  }
  window.spTxt = spTxt;
  /* 补充翻译键（菜单 / 控制 / 监护 / 视频 / 弹窗 / 补偿 / 关于 / 日志） */
  (function () {
    var more = {
      zh: {
        menuSet: '设置', menuDebug: '调试', menuClose: '关闭', menuAbout: '关于', menuLog: '日志', menuLogout: '注销',
        engParams: '工程师参数（出厂后不可修改，置灰）',
        prevPage: '‹ 上一页', nextPage: '下一页 ›', update: '更新', logTimeQuery: '时间查询', logCur: '用户日志 · 当前：',
        ctrlSetting: '设置', on: '开', off: '关',
        confirm: '确认', cancel: '取消', yes: '是', back: '返回',
        errPetName: '宠物名不能为空', errPhoneEmpty: '联系电话不能为空', errPhoneFmt: '联系电话格式不正确',
        timeApply: '应用', timeFormat: '格式',
        monBle: '蓝牙', monGuard: '监护宝', monAlarm: '报警音', monSetting: '设置',
        live: '实时', physical: '物理', avgPressure: '平均压',
        level1: '一级', level2: '二级', level3: '三级',
        videoTalk: '对讲', videoMask: '镜头遮蔽', videoFlip: '画面翻转', videoManual: '手动报警',
        videoOrig: '原图', videoCruise: '巡航', videoParam: '视频参数', videoChart: '图表', videoCustom: '自定义',
        liveView: '实况', playback: '回放', shot: '截图', rec: '录制', full: '全屏',
        compTemp: '温度补偿（℃）', compO2: '氧浓度补偿（%）', compHum: '湿度补偿（%）',
        compCo2: '二氧化碳浓度补偿（PPM）', compIr: '红外体温补偿（℃）',
        aboutBasic: '基础信息', aboutVer: '版本信息',
        logFront: '前端调试', logBack: '后端调试', logErr: '错误记录', logComm: '通信记录',
        logStack: '堆栈信息', logNormal: '常规信息', logUser: '用户日志',
        /* ★ 任务13：用户管理 / 舱区 / 连接 */
        roleService: '工程师', roleAdmin: '管理员', roleUser: '用户',
        userAdd: '用户新增', userEdit: '用户编辑', userAddBtn: '新增', userEditBtn: '编辑/修改',
        userDelBtn: '删除', userCancelBtn: '取消', userConfirmBtn: '确认',
        roleLabel: '权 限', pwdLabel: '密 码', nameLabel: '用户名',
        zoneTitle: '当前舱区', zoneLeft: '左舱', zoneRight: '右舱', zoneNoPerm: '无权限（仅工程师可切换）',
        connHost: '主机名称', connWifi: 'wifi连接', connHostBle: '主机蓝牙', connMonitorBle: '监护蓝牙',
        connNotConn: '未连接', connConnected: '已连接',
        blePickHost: '选择主机蓝牙', blePickMon: '选择监护设备', bleScanning: '正在搜索附近设备…', bleRescan: '重新搜索',
        bleHostConnOk: '主机蓝牙已连接', bleHostConnLost: '主机蓝牙已断开', bleMonConnOk: '监护蓝牙已连接', bleMonConnLost: '监护蓝牙已断开',
        wifiLocOff: '请开启系统定位后重试', wifiNeedPerm: '请授予定位权限',
        delUserTitle: '删除用户', delUserBody: '确认删除该用户？删除后不可恢复。',
        /* ★ 任务15 */
        curLogo: '当前LOGO', footerLogo: '页脚LOGO', factoryConfirmBody: '确认恢复出厂设置？所有自定义设置将恢复默认。',
        reviewNoData: '暂无样本数据，无法执行该操作', reviewNoSel: '请先选择一个样本',
        /* ★ 任务14：打印页 */
        printerAvail: '可用打印机', printerAddr: '打印机地址', scanPrinter: '扫描',
        addPrinter: '添加打印机', printTest: '打印测试页', uploadLogo: '上传LOGO',
        hospitalName: '医院名称', hospitalPhone: '医院电话', hospitalAddr: '医院地址', hospitalLogo: '医院LOGO',
        reportTitle: '报告标题', reportDecl: '报告声明', setBtn: '设置',
        scanUnsupported: '暂不支持自动扫描，请手动填写打印机地址后添加'
      },
      en: {
        menuSet: 'Settings', menuDebug: 'Debug', menuClose: 'Close', menuAbout: 'About', menuLog: 'Log', menuLogout: 'Logout',
        engParams: 'Engineer params (locked after shipment)',
        prevPage: '‹ Prev', nextPage: 'Next ›', update: 'Update', logTimeQuery: 'Time query', logCur: 'User log · Current: ',
        ctrlSetting: 'Settings', on: 'On', off: 'Off',
        confirm: 'Confirm', cancel: 'Cancel', yes: 'Yes', back: 'Back',
        errPetName: 'Pet name is required', errPhoneEmpty: 'Phone number is required', errPhoneFmt: 'Phone number format is incorrect',
        timeApply: 'Apply', timeFormat: 'Format',
        monBle: 'Bluetooth', monGuard: 'Monitor', monAlarm: 'Alarm', monSetting: 'Settings',
        live: 'Live', physical: 'Physical', avgPressure: 'MAP',
        level1: 'Level 1', level2: 'Level 2', level3: 'Level 3',
        videoTalk: 'Intercom', videoMask: 'Lens Mask', videoFlip: 'Flip', videoManual: 'Manual Alarm',
        videoOrig: 'Original', videoCruise: 'Cruise', videoParam: 'Camera', videoChart: 'Chart', videoCustom: 'Custom',
        liveView: 'Live', playback: 'Playback', shot: 'Snapshot', rec: 'Record', full: 'Full',
        compTemp: 'Temp Comp (℃)', compO2: 'O₂ Comp (%)', compHum: 'Humidity Comp (%)',
        compCo2: 'CO₂ Comp (PPM)', compIr: 'IR Temp Comp (℃)',
        aboutBasic: 'Basic Info', aboutVer: 'Version Info',
        logFront: 'Frontend', logBack: 'Backend', logErr: 'Errors', logComm: 'Comm',
        logStack: 'Stack', logNormal: 'General', logUser: 'User Log',
        /* ★ 任务13：用户管理 / 舱区 / 连接 */
        roleService: 'Engineer', roleAdmin: 'Admin', roleUser: 'User',
        userAdd: 'Add User', userEdit: 'Edit User', userAddBtn: 'Add', userEditBtn: 'Edit',
        userDelBtn: 'Delete', userCancelBtn: 'Cancel', userConfirmBtn: 'Confirm',
        roleLabel: 'Role', pwdLabel: 'Password', nameLabel: 'Username',
        zoneTitle: 'Current Cabin', zoneLeft: 'Left', zoneRight: 'Right', zoneNoPerm: 'No permission (Engineer only)',
        connHost: 'Host Name', connWifi: 'WiFi', connHostBle: 'Host BLE', connMonitorBle: 'Monitor BLE',
        connNotConn: 'Disconnected', connConnected: 'Connected',
        blePickHost: 'Select host Bluetooth', blePickMon: 'Select monitor device', bleScanning: 'Searching nearby devices…', bleRescan: 'Rescan',
        bleHostConnOk: 'Host Bluetooth connected', bleHostConnLost: 'Host Bluetooth disconnected', bleMonConnOk: 'Monitor Bluetooth connected', bleMonConnLost: 'Monitor Bluetooth disconnected',
        wifiLocOff: 'Enable system location, then retry', wifiNeedPerm: 'Location permission required',
        delUserTitle: 'Delete User', delUserBody: 'Delete this user? This cannot be undone.',
        /* ★ 任务15 */
        curLogo: 'Current Logo', footerLogo: 'Footer Logo', factoryConfirmBody: 'Restore factory settings? All custom settings will be reset.',
        reviewNoData: 'No sample data; this action is unavailable', reviewNoSel: 'Please select a sample first',
        /* ★ 任务14：打印页 */
        printerAvail: 'Available Printers', printerAddr: 'Printer Address', scanPrinter: 'Scan',
        addPrinter: 'Add Printer', printTest: 'Print Test Page', uploadLogo: 'Upload Logo',
        hospitalName: 'Hospital Name', hospitalPhone: 'Hospital Phone', hospitalAddr: 'Hospital Address', hospitalLogo: 'Hospital Logo',
        reportTitle: 'Report Title', reportDecl: 'Report Declaration', setBtn: 'Set',
        scanUnsupported: 'Auto-scan not supported; please enter the printer address manually'
      }
    };
    ['zh', 'en'].forEach(function (l) { for (var k in more[l]) I18N[l][k] = more[l][k]; });
  })();
  /* ★ 任务34：i18n 补全 —— 表头 / 记录单 / 发送数据 / 升级 / 视频回放 / toast 等全部用户可见文案 */
  (function () {
    var more2 = {
      zh: {
        colDoctor: '医生', colDisease: '病症', colCreateTime: '创建时间', phDisease: '请输入',
        newSampleTitle: '新建样本', editSampleTitle: '编辑样本', monLevel: '监护等级',
        treatStart: '治疗开始时间', treatEnd: '结束时间',
        colSeq: '序号', colItem: '项目', colOpen: '是否打开', colReport: '报告录入',
        colPeriod: '选取时段', colAvg: '平均值', colHigh: '最高', colLow: '最低', openPeriod: '打开时段',
        sheetAnimal: '动物名称', sheetOwner: '主人', sheetTreatDate: '治疗日期', sheetCage: '舱位',
        sheetSpecies: '动物种类', sheetWeight: '体重', sheetDept: '科室', sheetDur: '治疗时长', sheetDoctor: '操作医师',
        colTime: '时间', sheetNoVitals: '暂无体征记录，可点击下方「添加行」录入', sheetAddRow: '＋ 添加行',
        waveEcg: '【心电图】', wavePleth: '【脉搏波形图】', waveResp: '【呼吸率图】',
        secProj: '【治疗项目】', secVitals: '【生命体征记录】', secEnv: '【环境参数】',
        secImgs: '【影像记录】', secAlarm: '【报警事件】', secConcl: '【治疗结论】',
        sheetEffect: '治疗效果：', sheetAdvice: '出院建议：',
        conclGood: '良好', conclFair: '一般', conclPoor: '差',
        sheetLabDoc: '检验医师：', sheetLabTime: '检验时间：',
        recordSave: '保存', recordPrev: '上一样本', recordNext: '下一样本',
        recordNoSwitch: '暂无可切换的样本', recordFirstSample: '已经是第一个样本', recordLastSample: '已经是最后一个样本',
        sheetTitle: 'ICU动物舱治疗记录单',
        qCareTime: '护疗时间', printing: '正在打印......', sendingNow: '正在发送......',
        sendFileHelper: '文件助手', sendBle: '蓝牙传输', sendQrCap: '微信文件助手传输',
        sendQrTip: '微信扫码打开报告 → 右上角「…」→ 发送给「文件传输助手」',
        tipTitle: '提示', delDataBody: '确认删除数据?',
        tagPhase2: '二期', transferConn: '连接', statusLabel: '状态：', notLinked: '未接入', cloudPlatform: '云平台',
        printerAddrPh: '如 192.168.1.10:9100', userCurrent: '（当前）', pwdKeepBlank: '不修改请留空',
        upSw: '软件', upCb: '控制板', upOnline: '在线升级', chooseFile: '选择文件', upgrade: '升级',
        upLatest: '升级最新', upModule: '模块',
        qrBan: '传输助手仅用于客户便捷查询，非商用用途。', qrBanShort: '传输助手仅用于客户便捷查询',
        qrCap: '使用文件传输助手，手机电脑轻松互传文件', qrNote: '微信文件传输助手网页版',
        logoutBody: '是否注销当前账户?', exitBody: '确认是否退出软件？', exitingMsg: '缓存完成后自动退出软件', no: '否',
        debugPwdErr: '调试密码错误，请重试', weekdayThu: '星期四',
        playSearchDate: '监控日期', today: '今天', thisWeek: '本周', thisMonth: '本月',
        cloudRec: '云录像', cardRec: '卡录像', filter: '筛选', timeline: '时间轴',
        aiRecog: 'Ai识图', download: '下载', manage: '管理', secUnit: '秒',
        alwaysOn: '常开', uv24h: '24h常开', autoGen: '自动生成',
        qrFail: '二维码生成失败', qrPending: '二维码生成中…', errForm: '表单数据无效',
        cabinA: 'A舱', cabinB: 'B舱',
        vrRow: '第{n}行', vrErrTime: '时间格式应为 HH:MM', vrErrHr: '心率应为数字',
        vrErrSpo2: '血氧应为 0-100 的数字', vrErrBp: '血压格式应为 收缩压/舒张压', vrErrTemp: '体温应为数字（如 38.5）',
        vrFix: '，请修改后再保存',
        phPetName: '请输入宠物名', phAge: '请输入年龄', phOwner: '请输入宠物主人',
        phPhone: '请输入联系电话', phDoctor: '请输入主治医生',
        maxVitalsRows: '最多 50 行体征记录',
        factoryNoPerm: '无权限：恢复出厂设置仅工程师可用',
        errPrinterAddr: '请输入打印机地址', errPrinterAddrFmt: '打印机地址格式不正确，示例 192.168.1.10:9100',
        errHospPhone: '医院电话格式不正确',
        errUserName: '请输入用户名', errPwdReq: '密码必填', errPwdLen: '密码至少 4 位', errRoleReq: '权限必选',
        userNoPerm: '无权限：账号管理仅工程师/管理员可用',
        errSelEditUser: '请先选择要编辑的用户', errSelDelUser: '请先选择要删除的用户', errDelCurUser: '不能删除当前登录用户',
        datePh: '----年--月--日', dateTimePh: '----年--月--日 --:--:--',
        changesTitle: '本次改版标注汇总', changesSub: '对应设计稿中的红字 / 划线标注',
        logColTime: '时间', logColAccount: '账号', logColZone: '舱区', logColModule: '模块',
        logColAction: '操作', logColDetail: '详情', logColResult: '结果',
        logEmpty: '暂无记录', logNotReady: '该分类日志暂未接入',
        logColLevel: '级别', logColSource: '来源', logColDir: '方向', logColType: '类型', logColStack: '堆栈',
        logPageInfo: '第 {n}/{m} 页', logAllDates: '全部日期', zoneLeft: '左舱', zoneRight: '右舱'
      },
      en: {
        colDoctor: 'Doctor', colDisease: 'Condition', colCreateTime: 'Created', phDisease: 'Enter',
        newSampleTitle: 'New Sample', editSampleTitle: 'Edit Sample', monLevel: 'Monitor Level',
        treatStart: 'Treatment Start', treatEnd: 'End Time',
        colSeq: 'No.', colItem: 'Item', colOpen: 'Enabled', colReport: 'In Report',
        colPeriod: 'Period', colAvg: 'Avg', colHigh: 'High', colLow: 'Low', openPeriod: 'Open Period',
        sheetAnimal: 'Animal', sheetOwner: 'Owner', sheetTreatDate: 'Treat Date', sheetCage: 'Cabin',
        sheetSpecies: 'Species', sheetWeight: 'Weight', sheetDept: 'Dept.', sheetDur: 'Duration', sheetDoctor: 'Operator',
        colTime: 'Time', sheetNoVitals: 'No vitals yet; tap "Add Row" below to enter', sheetAddRow: '+ Add Row',
        waveEcg: '[ECG]', wavePleth: '[Pleth Wave]', waveResp: '[Resp Wave]',
        secProj: '[Treatment Items]', secVitals: '[Vital Signs]', secEnv: '[Environment]',
        secImgs: '[Images]', secAlarm: '[Alarm Events]', secConcl: '[Conclusion]',
        sheetEffect: 'Outcome: ', sheetAdvice: 'Discharge Advice: ',
        conclGood: 'Good', conclFair: 'Fair', conclPoor: 'Poor',
        sheetLabDoc: 'Lab Doctor: ', sheetLabTime: 'Lab Time: ',
        recordSave: 'Save', recordPrev: 'Prev Sample', recordNext: 'Next Sample',
        recordNoSwitch: 'No other sample to switch to', recordFirstSample: 'Already the first sample', recordLastSample: 'Already the last sample',
        sheetTitle: 'ICU Animal Cabin Treatment Record',
        qCareTime: 'Care Time', printing: 'Printing...', sendingNow: 'Sending...',
        sendFileHelper: 'File Helper', sendBle: 'Bluetooth', sendQrCap: 'WeChat File Helper Transfer',
        sendQrTip: 'Scan with WeChat to open the report, then "..." at top right, send to "File Transfer"',
        tipTitle: 'Notice', delDataBody: 'Delete this data?',
        tagPhase2: 'Phase 2', transferConn: 'Connect', statusLabel: 'Status: ', notLinked: 'Not linked', cloudPlatform: 'Cloud',
        printerAddrPh: 'e.g. 192.168.1.10:9100', userCurrent: ' (current)', pwdKeepBlank: 'Leave blank to keep unchanged',
        upSw: 'Software', upCb: 'Control Board', upOnline: 'Online', chooseFile: 'Choose File', upgrade: 'Upgrade',
        upLatest: 'Upgrade to Latest', upModule: 'Module',
        qrBan: 'The transfer helper is for customer convenience only, not commercial use.', qrBanShort: 'Transfer helper for customer queries only',
        qrCap: 'Use File Transfer to share files between phone and PC', qrNote: 'WeChat File Transfer web version',
        logoutBody: 'Log out of the current account?', exitBody: 'Exit the application?', exitingMsg: 'Will exit automatically after caching', no: 'No',
        debugPwdErr: 'Incorrect debug password, please retry', weekdayThu: 'Thursday',
        playSearchDate: 'Monitor Date', today: 'Today', thisWeek: 'This Week', thisMonth: 'This Month',
        cloudRec: 'Cloud', cardRec: 'SD Card', filter: 'Filter', timeline: 'Timeline',
        aiRecog: 'AI Recognize', download: 'Download', manage: 'Manage', secUnit: 's',
        alwaysOn: 'Always On', uv24h: '24h Always On', autoGen: 'Auto-generated',
        qrFail: 'QR generation failed', qrPending: 'Generating QR...', errForm: 'Invalid form data',
        cabinA: 'Cabin A', cabinB: 'Cabin B',
        vrRow: 'Row {n}', vrErrTime: 'time must be HH:MM', vrErrHr: 'HR must be a number',
        vrErrSpo2: 'SpO2 must be 0-100', vrErrBp: 'BP must be systolic/diastolic', vrErrTemp: 'temp must be a number (e.g. 38.5)',
        vrFix: ', please correct and save again',
        phPetName: 'Enter pet name', phAge: 'Enter age', phOwner: 'Enter owner name',
        phPhone: 'Enter phone number', phDoctor: 'Enter doctor',
        maxVitalsRows: 'Up to 50 vitals rows',
        factoryNoPerm: 'No permission: factory reset is engineer-only',
        errPrinterAddr: 'Please enter printer address', errPrinterAddrFmt: 'Invalid printer address, e.g. 192.168.1.10:9100',
        errHospPhone: 'Invalid hospital phone format',
        errUserName: 'Please enter username', errPwdReq: 'Password is required', errPwdLen: 'Password must be at least 4 characters', errRoleReq: 'Role is required',
        userNoPerm: 'No permission: account management is engineer/admin only',
        errSelEditUser: 'Please select a user to edit', errSelDelUser: 'Please select a user to delete', errDelCurUser: 'Cannot delete the currently logged-in user',
        datePh: '----/--/--', dateTimePh: '----/--/-- --:--:--',
        changesTitle: 'Redesign Annotations Summary', changesSub: 'Red / strikethrough annotations from the design',
        logColTime: 'Time', logColAccount: 'Account', logColZone: 'Zone', logColModule: 'Module',
        logColAction: 'Action', logColDetail: 'Detail', logColResult: 'Result',
        logEmpty: 'No records', logNotReady: 'This log category is not available yet',
        logColLevel: 'Level', logColSource: 'Source', logColDir: 'Direction', logColType: 'Type', logColStack: 'Stack',
        logPageInfo: 'Page {n}/{m}', logAllDates: 'All dates', zoneLeft: 'Left', zoneRight: 'Right'
      }
    };
    ['zh', 'en'].forEach(function (l) { for (var k in more2[l]) I18N[l][k] = more2[l][k]; });
  })();

  /* 通用表格行 ------------------------------------------------------------ */
  function stDot(st) {
    if (st === 'load') return '<span class="dot load"></span>';
    if (st === 'ok') return '<span class="dot" style="color:#22b14c">' + I.dot + '</span>';
    return '<span class="dot" style="color:#f0483e">' + I.x + '</span>';
  }

  /* ======================================================================
     p00 启动页：黑底发光粒子 → 优利特品牌 Logo 渐显，播完自动进登录/护理
     ====================================================================== */
  SCREENS['splash'] = {
    name: '启动页', group: '0 引导',
    render() {
      return '<div class="screen"><div class="splash" data-go="login">'
        + '<div class="boot-core"></div><div class="boot-ring"></div>'
        + '<div class="brand"><span class="particles"></span><span class="en">URIT</span><span class="cn">优利特</span></div>'
        + '<div class="tag">Better Health For All</div></div></div>';
    }
  };

  /* ======================================================================
     p01 用户登录
     ====================================================================== */
  SCREENS['login'] = {
    name: '用户登录', group: '0 引导', render() {
      return '<div class="screen"><div class="login-bg"></div>'
        + '<div class="login-paw">' + (window.NI && window.NI.paw ? NI.paw : I.paw) + '</div>'
        + '<div class="login-foot"><div class="fb">' + (window.NI && window.NI.paw ? NI.paw : I.paw) + '<span>URIT animal</span></div></div>'
        + '<div class="login-box">'
        + '<h1>' + T('loginTitle', '用户登录') + '</h1>'
        + '<div class="lf">' + I.user + '<input placeholder="' + T('account', '账号名') + '" value=""></div>'
        + '<div class="lf"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="#8b95a6" stroke-width="1.8"><rect x="5" y="10.5" width="14" height="9" rx="2"/><path d="M8.4 10.5V8a3.6 3.6 0 0 1 7.2 0v2.5"/></svg><input type="password" placeholder="' + T('password', '登录口令') + '"></div>'
        + '<button class="lb" data-go="care">' + T('loginBtn', '登 录') + '</button></div></div>';
    }
  };

  /* ======================================================================
     p02 护理列表（空态）
     ====================================================================== */
  /* 护疗列表表头（改版后：样本号→住院号、新增宠物主人、状态→病症）
     病症列为下拉选项：绝育 / 骨折 / 心脏病 / 幼儿护理
     ★ 行为可点击切换"选中的样本"（tr.data-caseid 让 bridge 查找 S.cases 全量下标并调 patient_select_<zone>_<idx>）
     ★ "sel" 反映 D.selCaseId（bridge 在 syncD 后写入），而非硬编码 i===0 */
  function careTable(rows, noAutoSel) {
    const head = [T('fieldCaseNo', '住院号'), T('fieldPetName', '宠物名'), T('fieldSpecies', '物种'), T('fieldOwner', '宠物主人'), T('fieldPhone', '联系电话'), T('colDoctor', '医生'), T('colDisease', '病症'), T('colCreateTime', '创建时间')];
    /* ★ 任务15(#11)：noAutoSel=true 时不做任何默认选中（未点击不高亮），点击后才由 bridge 写入 D.selCaseId */
    const selId = noAutoSel ? (D.selCaseId || '') : (D.selCaseId || (rows[0] && rows[0].caseId) || '');
    let h = '<table class="tbl list"><tr>' + head.map(function (t) { return '<th>' + t + '</th>'; }).join('') + '</tr>';
    rows.forEach(function (r, i) {
      const sel = (r.caseId && r.caseId === selId) || (!noAutoSel && !selId && i === 0);
      h += '<tr class="' + (sel ? 'sel' : '') + '" data-caseid="' + (r.caseId || '') + '">'
        + '<td>' + r.no + '</td><td>' + r.name + '</td><td>' + spTxt(r.sp) + '</td>'
        + '<td>' + r.owner + '</td><td>' + r.tel + '</td><td>' + r.doc + '</td>'
        + '<td><input class="iln-in" data-disease data-caseid="' + (r.caseId || '') + '" value="' + (r.illness || '') + '" placeholder="' + T('phDisease', '请输入') + '" maxlength="20"></td>'
        + '<td>' + fmtDT(r.time) + '</td></tr>';
    });
    return h + '</table>';
  }
  const CARE_BAR = [
    { id: 'new', label: '新建', key: 'newSample', icon: 'plus', go: 'new-sample' },
    { id: 'rec', label: '护疗记录', key: 'careRecord', icon: 'doc', go: 'care-record' },
    { id: 'start', label: '开始护疗', key: 'startCare', icon: 'play', go: 'status' },
    { id: 'edit', label: '编辑', key: 'edit', icon: 'pencil', go: 'new-sample' },
    { id: 'send', label: '数据发送', key: 'dataSend', icon: 'upload', go: 'sending' },
    { id: 'del', label: '删除', key: 'del', icon: 'trash', go: 'del-confirm' },
    { id: 'tu', label: '教程', key: 'tutorial', icon: 'cap', go: 'about' },
  ];
  /* 设计图 5.png（有数据态）底栏不含「教程」，仅空态（3.png）显示 —— 故有数据态过滤掉 */
  const CARE_BAR_FULL = CARE_BAR.slice(0, -1);
  const CARE_NOTE = A(150, 470, '（注：该页面，仅显示当天新建信息列表,若在当天结束的护理，会在凌晨0点过后消失，转入【回顾】列表里。<br>若连续多日护疗的，以结束护疗后，过凌晨0点为基准转入【回顾】列表）', 'plain');

  /* ★ 任务15(#10)：进入护理页仅顶栏「护理」高亮，底栏按钮默认不高亮（active 传空） */
  SCREENS['care'] = {
    name: '护理列表（空态）', group: '1 主流程', render() {
      return PAGE(P.topbar('care'),
        '<div class="listwrap">' + careTable([]) + '</div>' + CARE_NOTE
        + A(20, 46, '样本号', 'strike') + A(24, 78, '住院号') + A(630, 46, '宠物主人'),
        P.bottombar(CARE_BAR, ''));
    }
  };

  /* ======================================================================
     p03 新建样本（弹窗）
     ====================================================================== */
  SCREENS['new-sample'] = {
    name: '新建样本', group: '1 主流程', render() {
      const specKeys = ['dog', 'cat', 'rabbit', 'lizard', 'snake', 'other'];
      const sp = specKeys.map(function (k, i) {
        var label = T('species' + k.charAt(0).toUpperCase() + k.slice(1), ['犬', '猫', '兔', '蜥蜴', '蛇', '其它'][i]);
        return '<div class="chip' + (k === 'rabbit' ? ' on' : '') + '" data-spec="' + k + '">' + label + '</div>';
      }).join('');
      const au = '<select data-ageunit class="age-unit"><option value="天">' + T('ageDay', '天') + '</option><option value="月" selected>' + T('ageMonth', '月') + '</option><option value="岁">' + T('ageYear', '岁') + '</option></select>';
      const body = '<div class="form-grid">'
        + '<div class="col">'
        + '<div class="frow" data-row="caseNo"><label>' + T('fieldCaseNo', '住院号') + '</label><div class="ctl"><div class="inp flex"></div></div></div>'
        + '<div class="frow" data-row="petName"><label><span style="color:#f0483e">*</span>' + T('fieldPetName', '宠物名') + '</label><div class="ctl"><div class="inp flex"></div></div></div>'
        + '<div class="frow" data-row="age"><label>' + T('fieldAge', '年龄') + '</label><div class="ctl"><div class="inp flex icu-field-row"><input class="icu-input" data-field="age" value="">' + au + '</div></div></div>'
        + '<div class="frow"><label>' + T('fieldSex', '性别') + '</label><div class="ctl"><div class="chips cols3"><div class="chip" data-sex="female">' + T('sexFemale', '雌性') + '</div><div class="chip on" data-sex="male">' + T('sexMale', '雄性') + '</div><div class="chip" data-sex="unknown">' + T('sexUnknown', '未知') + '</div></div></div></div>'
        + '<div class="frow" style="align-items:flex-start" data-row="species"><label style="padding-top:16px">' + T('fieldSpecies', '物种') + '</label><div class="ctl"><div class="chips cols3">' + sp + '</div><div class="species-other" hidden><input data-other-species maxlength="10" placeholder="' + T('fieldOtherSpecies', '其他物种') + '"></div></div></div>'
        + '<div class="frow" data-row="ownerName"><label>' + T('fieldOwner', '宠物主人') + '</label><div class="ctl"><div class="inp flex"></div></div></div>'
        + '</div>'
        + '<div class="col">'
        + '<div class="cam" style="height:300px;background:transparent;margin-bottom:26px;display:flex;align-items:center;justify-content:center">'
        + '<span id="petSil" style="width:170px;height:170px;color:#0f3e86"></span></div>'
        + '<div class="frow" data-row="ownerPhone"><label><span style="color:#f0483e">*</span>' + T('fieldPhone', '联系电话') + '</label><div class="ctl"><div class="inp flex"></div></div></div>'
        + '<div class="frow" data-row="doctor"><label>' + T('fieldDoctor', '主治医生') + '</label><div class="ctl"><div class="inp flex"></div></div></div>'
        + '</div></div>';
      return PAGE(P.topbar('care'),
        '<div class="listwrap">' + careTable([]) + '</div>'
        + P.mask('<div class="modal wide"><div class="mhead">' + T('newSampleTitle', '新建样本') + '</div>'
          + '<div class="mbody">' + body + '</div>'
          + '<div class="mfoot"><div class="btn" data-foot="confirm">' + T('confirm', '确认') + '</div><div class="btn primary" data-foot="cancel">' + T('cancel', '取消') + '</div></div></div>')
        + A(441, 227, '住院号') + A(441, 271, '样本号', 'strike') + A(466, 695, '主人', 'strike') + A(416, 748, '宠物主人')
        + A(764, 534, '兔') + A(656, 676, '蛇') + A(880, 470, '鸟', 'strike') + A(880, 620, '蜘蛛', 'strike'),
        P.bottombar(CARE_BAR, 'new'));
    }
  };

  /* ======================================================================
     p04 护理列表（有数据）
     ====================================================================== */
  SCREENS['care-data'] = {
    name: '护理列表（有数据）', group: '1 主流程', render() {
      return PAGE(P.topbar('care'),
        '<div class="listwrap">' + careTable(D.careRows) + '</div>' + CARE_NOTE
        + A(20, 46, '样本号', 'strike') + A(24, 78, '住院号') + A(430, 46, '宠物主人') + A(780, 46, '病症'),
        P.bottombar(CARE_BAR_FULL, ''));
    }
  };

  /* 监护等级三色：与设计稿一致 —— 一级红 / 二级橙 / 三级绿 */
  var LVL_COLORS = ['#E95E5E', '#FFA200', '#17B129'];
  var LVL_TEXT = [T('level1', '一级'), T('level2', '二级'), T('level3', '三级')];
  /* 状态页指标统一读 D.status（由原生 host/controls 注入；无数据显示 --）
     ★ 2026-10-08（#39b）1:1 对齐设计稿 6.png：
     标题 → 通栏细分隔线 → 数值行（大黑字左、蓝色图标右）→ 底部进度条（恒显，无数据为空轨） */
  function metric(t, v, ico, pct, lvl) {
    if (lvl) {
      var li = (typeof D.levelIdx === 'number') ? D.levelIdx : 0;
      if (li < 0 || li > 2) li = 0;
      var lt = LVL_TEXT[li];
      var lc = LVL_COLORS[li];
      /* 监护等级卡：标题+分隔线 → 药丸(左)+盾牌(右) 一行 → 三色点一行（选中点带同色外环） */
      return '<div class="metric">'
        + '<div class="mt">' + T('monLevel', '监护等级') + '</div><div class="mhr"></div>'
        + '<div class="level"><span class="lvl" style="background:' + lc + '">' + lt + '</span>'
        + '<span class="mi mi-shield">' + I.shield + '</span></div>'
        + '<span class="dots" data-level>'
        + '<i data-lvl="red" class="' + (li === 0 ? 'on' : '') + '" style="background:#E95E5E;--c:#E95E5E"></i>'
        + '<i data-lvl="yellow" class="' + (li === 1 ? 'on' : '') + '" style="background:#FFA200;--c:#FFA200"></i>'
        + '<i data-lvl="green" class="' + (li === 2 ? 'on' : '') + '" style="background:#17B129;--c:#17B129"></i>'
        + '</span></div>';
    }
    return '<div class="metric"><div class="mt">' + t + '</div><div class="mhr"></div>'
      + '<div class="mvrow"><span class="mv">' + v + '</span><span class="mi">' + I[ico] + '</span></div>'
      + '<div class="pbar"><i style="width:' + ((pct === null || pct === undefined) ? 0 : pct) + '%"></i></div></div>';
  }
  /* 状态页（实时监控）· 9 卡 + 3 快捷按钮，对齐 6.png：
     - 行1 设备图标（thermo/o2/drop）：数值右侧、垂直居中于数值行
     - 行2 红外体温 thermo；监护等级（盾牌右上、药丸红/橙/绿）+ 三色点；二氧化碳 co2
     - 行3 治疗时长 clock；红外理疗 rays；蓝光理疗 blueLight
     - 快捷按钮 雾化器(mist) / 暖光灯(sun) / 内循环(loopIn)，图标在文字前 */
  SCREENS['status'] = {
    name: '状态（实时监控）', group: '2 会话', render() {
      const M = D.status || {};
      const grid = '<div class="status-grid">'
        + metric(T('statusTemp', '舱内温度（℃）'), M.temp || '--', 'thermo', M.tempP)
        + metric(T('statusO2', '氧浓度（%）'), M.o2 || '--', 'o2', M.o2P)
        + metric(T('statusHum', '湿度（%）'), M.hum || '--', 'drop', M.humP)
        + metric(T('statusIr', '红外体温 ℃'), M.irTemp || '--', 'thermo', M.irP)
        + metric('', '', '', null, 1)
        + metric(T('statusCo2', '二氧化碳（PPM）'), M.co2 || '--', 'co2', M.co2P)
        + metric(T('statusDur', '治疗时长（h）'), M.dur || '--', 'clock', M.durP)
        + metric(T('statusRed', '红外理疗（m）'), M.red || '--', 'pulseC', M.redP)
        + metric(T('statusBlue', '蓝光理疗（m）'), M.blue || '--', 'lamp', M.blueP)
        + '</div>';
      const acts = '<div class="status-actions">'
        + '<div class="wbtn" data-act="control_nebulizer">' + I.mist + '<span>' + T('ctrlNeb', '雾化器') + '</span></div>'
        + '<div class="wbtn" data-act="control_warm_light">' + I.sun + '<span>' + T('statusWarm', '暖光灯') + '</span></div>'
        + '<div class="wbtn" data-act="control_inner">' + I.loopIn + '<span>' + T('ctrlInner', '内循环') + '</span></div></div>';
      const cam = '<div class="camcol"><div class="cam big">' + P.fisheye(D.stamp) + '</div>'
        + '<div class="cam small"><div class="glow"></div></div></div>';
      return PAGE(P.topbar('care'), grid + acts + cam, P.sessionBar('status'), false, P.sessionTag());
    }
  };

  /* ======================================================================
     p06 主控
     ====================================================================== */
  /* 卡片统一结构：左上名称 / 数值 / 进度条 / 左下「设置」/ 右上「开·关」/ 右侧设备图标
     —— 所有卡片的「设置」按钮位置完全一致（左 34 / 下 24），不再出现偏移 */
  /* 卡内药丸开关：默认 右下；pos='bl' 时 左下（开关卡 / 模式卡 / 监护等级卡） */
  function pill(on, pos) {
    return '<div class="toggle sw' + (pos === 'bl' ? ' bl' : '') + (on ? ' on' : '') + '" data-sw>'
      + '<span class="l">' + (on ? T('on', '开') : T('off', '关')) + '</span></div>';
  }
  /* 图标落位：window.TP 给出每个矢量图标的精确 right/top/尺寸（相对卡片）。
     未收录的图标走 CSS 兜底。cls 为容器类名（si / si atlvl / mi）。 */
  function icoSpan(name, cls) {
    var p = (window.TP && window.TP[name]) || null;
    var st = p ? ' style="right:' + p.r + 'px;top:' + p.t + 'px;width:' + p.w + 'px;height:' + p.h + 'px"' : '';
    return '<span class="' + (cls || 'si') + '"' + st + '>' + (I[name] || '') + '</span>';
  }
  /* 模式卡：名称 + 模式图标 + 左下药丸开关（无「设置」） */
  function modeCard(c) {
    var mkey = ({ '母幼护理模式': 'host_mode_mother', '术后护理模式': 'host_mode_postop', '心肺护理模式': 'host_mode_cardio', '自定义模式': 'host_mode_custom' })[c.t] || '';
    return '<div class="ctrl modecard" data-mkey="' + mkey + '">'
      + icoSpan(c.icon, 'mi')
      + '<span class="mtxt">' + T((c.tKey || 'modeName'), c.t) + '</span>'
      + pill(c.on, 'bl') + '</div>';
  }
  /* 统一卡片：row=0/1/2 决定数值字号与进度条 */
  function ctrlCard(c, row) {
    var tk = c.tKey || '';
    if (c.level) {
      /* 监护等级卡：标题 + 等级药丸(一级/二级/三级，背景按 D.levelIdx 变：红/橙/绿) + 三色点(可点击切换) + 盾牌 */
      var li2 = (typeof D.levelIdx === 'number') ? D.levelIdx : 0;
      if (li2 < 0 || li2 > 2) li2 = 0;
      var lt2 = T(['level1', 'level2', 'level3'][li2], LVL_TEXT[li2]);
      var lc2 = LVL_COLORS[li2];
      return '<div class="ctrl v" data-tkey="' + tk + '">'
        + '<div class="ct">' + T(tk, c.t) + '</div>'
        + '<span class="lvtag" style="background:' + lc2 + '">' + lt2 + '</span>'
        + '<div class="lvl" data-level><i data-lvl="red" class="' + (li2 === 0 ? 'on' : '') + '" style="background:#E95E5E"></i><i data-lvl="yellow" class="' + (li2 === 1 ? 'on' : '') + '" style="background:#FFA200"></i><i data-lvl="green" class="' + (li2 === 2 ? 'on' : '') + '" style="background:#17B129"></i></div>'
        + icoSpan('shield', 'si atlvl') + '</div>';
    }
    if (c.big) {
      /* 治疗时长卡：设置置右上，无开关；辐射状加载图标 + 大数字 */
      /* 8 刻度 @45°，透明度实测 7.png：N/NW 纯白、NE 最淡 */
      const tkOp = [1, .45, .62, .62, .58, .58, .85, 1];
      let ticks = '';
      for (let i = 0; i < 8; i++) ticks += '<i style="transform:rotate(' + (i * 45) + 'deg);opacity:' + tkOp[i] + '"></i>';
      return '<div class="ctrl tall' + (c.on ? ' on' : '') + '" data-tkey="' + tk + '">'
        + '<div class="ct">' + T(tk, c.t) + '</div>'
        + '<div class="gauge"><span class="ticks">' + ticks + '</span></div>'
        + '<div class="ctime">' + c.v + '</div>'
        + '<div class="setbtn tr">' + T('ctrlSetting', '设置') + '</div></div>';
    }
    if (c.v) {
      return '<div class="ctrl v' + (row === 1 ? ' r1v r2c' : '') + (c.on ? ' on' : '') + '" data-tkey="' + tk + '">'
        + '<div class="ct">' + T(tk, c.t) + '</div>'
        + '<div class="cv' + (/^[0-9:.]+$/.test(c.v || '') ? '' : ' vs') + '">' + c.v + '</div>'
        + (c.p ? '<div class="cbar"><i style="width:' + c.p + '%"></i></div>' : '')
        + '<div class="setbtn">' + T('ctrlSetting', '设置') + '</div>'
        + icoSpan(c.ico)
        + pill(c.on) + '</div>';
    }
    /* 开关卡（照明 / 循环）：仅 标题 + 设备图标（右侧）+ 药丸开关（左下），无「设置」 */
    return '<div class="ctrl s' + (c.on ? ' on' : '') + '" data-tkey="' + tk + '">'
      + '<div class="ct">' + T(tk, c.t) + '</div>'
      + icoSpan(c.ico)
      + pill(c.on, 'bl') + '</div>';
  }
  SCREENS['control'] = {
    name: '主控', group: '2 会话', render() {
      const modes = D.modes.map(modeCard).join('');
      const grid = '<div class="ctrl-grid">'
        + '<div class="crs r1">' + D.ctrlRow1.map(function (c) { return ctrlCard(c, 0); }).join('') + '</div>'
        + '<div class="crs r2">' + D.ctrlRow2.map(function (c) { return ctrlCard(c, 1); }).join('') + '</div>'
        + '<div class="crs r3">' + D.ctrlRow3.map(function (c) { return ctrlCard(c, 2); }).join('') + '</div>'
        + '<div class="crs r4">' + modes + '</div>'
        + '</div>';
      return PAGE(P.topbar('care'), grid, P.sessionBar('control'), false, P.sessionTag());
    }
  };

  /* ======================================================================
     p07 / p08 监护
     ====================================================================== */
  function vitals(stack, units) {
    const u = units ? 1 : 0;
    function box(t, v, cls, unit, sub) {
      return '<div class="v"><div class="vt ' + cls + '">' + t + '</div>'
        + '<div class="vv ' + cls + '"><span>' + v + '</span>' + (unit ? '<span class="u">' + unit + '</span>' : '') + '</div>'
        + (sub ? '<div class="sub">' + sub + '</div>' : '') + '</div>';
    }
    return '<div class="vitals">'
      + box(T('monHr', '心率'), '74', 'v-ecg', u ? 'bpm' : '')
      + box(T('monBp', '血压'), '120/80', '', '', T('avgPressure', '平均压') + '：93.3   mmHg')
      + box(T('monSpo2', '血氧'), '98', 'v-pleth', u ? '%' : '')
      + box(T('monPr', '脉率'), '80', 'v-pleth', u ? 'bpm' : '')
      + box(T('monTemp', '体温'), '37.3', 'v-resp', u ? '℃' : '')
      + box(T('monRr', '呼吸率'), '25', 'v-resp', u ? 'brpm' : '')
      + '</div>';
  }
  function monitorBody(units) {
    /* ★ 任务19：未连蓝牙时默认演示波形（动态滚动，不显示平线）；标签固定英文（ECG/SpO₂/RESP）
       ★ 任务33/41：监护宝 BLE 一连接即退出演示模式，切到读取 AM4100 实时采样：
       有采样画实时波形；已连接但采样未到画平线占位；只有未连接才跑演示滚动波形 */
    var WL = D.waveLive || {};
    var live = !!D.monitorLive;
    var flat = function (c, lab) {
      return '<div class="wave wave-flat"><svg viewBox="0 0 1448 289" preserveAspectRatio="none">'
        + '<line x1="0" y1="145" x2="1448" y2="145" stroke="' + c + '" stroke-width="2" opacity=".45"/></svg>'
        + '<div class="lab" style="color:' + c + '">' + lab + '</div></div>';
    };
    var wb = function (k, c, lab) {
      if (!live) return P.waveBox(k, c, lab);
      var s = WL[k];
      if (s && s.length > 1) return P.liveWaveBox(s, c, lab);
      return flat(c, lab);
    };
    const rows = '<div class="monitor">'
      + '<div class="mrow r1">' + wb('ecg', '#60F471', 'ECG') + vitalsHalf(units, 0) + '</div>'
      + '<div class="mrow r2">' + wb('pleth', '#FF6969', 'SpO₂') + vitalsHalf(units, 1) + '</div>'
      + '<div class="mrow r3">' + wb('resp', '#009DFF', 'RESP') + vitalsHalf(units, 2) + '</div>'
      + '</div>';
    return rows;
  }
  /* 每行右侧两条体征（设计 8-1.png）：
     - 每条体征：标签 + 大号数值 + 右侧单位；颜色随行（第1条满色、第2条浅色）
     - 血压条无「血压」标签，改由「实时⇄物理」切换键 + 平均压 一行占据标签位
     - ★ 数值统一读 D.vitals（由原生注入；无数据时显示 --，不造假数据） */
  function vitalsHalf(units, row) {
    const u = units ? 1 : 0;
    const V = D.vitals || {};
    const hr = V.hr || '--', spo2 = V.spo2 || '--',
          pr = V.pr || '--', temp = V.temp || '--', rr = V.rr || '--';
    /* ★ 任务22：血压条数据源 = BPM 血压仪（主机 BLE）。实时=自动模式(5min)、物理=手动单次，
       数值都读 D.bpm（sys/dia + 平均压 mean）；on 高亮跟随 D.bpMode */
    const B = D.bpm || {};
    const bp = (B.sys && B.dia) ? (B.sys + '/' + B.dia) : '--/--';
    const map = B.mean || '--';
    const bpMode = D.bpMode === 'physical' ? 'physical' : 'live';
    const bpSw = '<div class="bprow"><div class="bpsw"><button data-bpm="live"' + (bpMode === 'live' ? ' class="on"' : '') + '>' + T('live', '实时') + '</button>'
      + '<span class="arrow">⇄</span><button data-bpm="physical"' + (bpMode === 'physical' ? ' class="on"' : '') + '>' + T('physical', '物理') + '</button></div>'
      + '<div class="avg">' + T('avgPressure', '平均压') + '：' + map + '</div></div>';
    const sets = [
      [[T('monHr', '心率'), hr, 'v-ecg', u ? 'bpm' : '', '', 'hi'], ['', bp, 'v-ecg-lo', u ? 'mmHg' : '', '', 'lo', u ? bpSw : '<div class="bprow"><div class="avg">' + T('avgPressure', '平均压') + '：' + map + '</div></div>']],
      [[T('monSpo2', '血氧'), spo2, 'v-pleth', u ? '%' : '', '', 'hi'], [T('monPr', '脉率'), pr, 'v-pleth-lo', u ? 'bpm' : '', '', 'lo']],
      [[T('monTemp', '体温'), temp, 'v-resp', u ? '℃' : '', '', 'hi'], [T('monRr', '呼吸率'), rr, 'v-resp-lo', u ? 'brpm' : '', '', 'lo']],
    ][row];
    return '<div class="vitals">' + sets.map(function (b) {
      return '<div class="v ' + (b[5] || '') + '">'
        + (b[6] ? b[6] : (b[0] ? '<div class="vt ' + b[2] + '">' + b[0] + '</div>' : '<div class="vt"></div>'))
        + '<div class="vv ' + b[2] + '"><span>' + b[1] + '</span>' + (b[3] ? '<span class="u">' + b[3] + '</span>' : '') + '</div>'
        + (b[4] ? '<div class="sub">' + b[4] + '</div>' : '') + '</div>';
    }).join('') + '</div>';
  }
  /* 监护页操作按钮：原为右侧竖排，改版移到底部横向排列（置于底部导航栏之上，不与其重合）
     ★ 2026-10-08（#40c）「蓝牙」按钮改走监护通道 monitor_ble：与连接页「监护蓝牙」连同一台设备（AM4100），
     不再映射主机通道 host_ble；「监护宝」同为监护通道，两者状态同源同步 */
  const MON_ACT = [['蓝牙', 'bluetooth', 'monitor_ble', 'monBle'], ['监护宝', 'shield', 'monitor_ble', 'monGuard'], ['报警音', 'speaker', 'monitor_alarm_sound', 'monAlarm'], ['设置', 'settings', 'monitor_threshold_settings', 'monSetting']];
  function monActions() {
    /* on 态由真实连接状态驱动：蓝牙/监护宝 均 ← S.ble.monitor.connected */
    const st = window.S || {};
    const monOn = !!(st.ble && st.ble.monitor && st.ble.monitor.connected);
    return '<div class="mon-actions">' + MON_ACT.map(function (b, i) {
      const on = (i === 0 || i === 1) ? monOn : false;
      return '<div class="mbtn' + (on ? ' on' : '') + '" data-act="' + b[2] + '">' + I[b[1]] + '<span>' + T(b[3], b[0]) + '</span></div>';
    }).join('') + '</div>';
  }
  SCREENS['monitor'] = {
    name: '监护（补单位·对齐 8-1.png）', group: '2 会话', render() {
      return PAGE(P.topbar('care'),
        monitorBody(true) + monActions() + blePickModal(),
        P.sessionBar('monitor'), false, P.sessionTag());
    }
  };
  SCREENS['monitor-unit'] = {
    name: '监护（改版·补单位）', group: '2 会话', render() {
      return PAGE(P.topbar('care'),
        monitorBody(true) + monActions() + blePickModal()
        + '<div style="position:absolute;left:676px;top:606px;width:568px;height:172px;background:#fff;border:1px solid #dde3ec;display:flex;align-items:center;justify-content:center;color:#ff0000;font-size:28px">同上页</div>',
        P.sessionBar('monitor'), false, P.sessionTag());
    }
  };

  /* ======================================================================
     p09 视频 · 实况
     ====================================================================== */
  SCREENS['video'] = {
    name: '视频 · 实况', group: '2 会话', render() {
      const btns = [['ai', '对讲', 'videoTalk'], ['eyeoff', '镜头遮蔽', 'videoMask'], ['flip', '画面翻转', 'videoFlip', 1], ['alarm', '手动报警', 'videoManual'], ['img', '原图', 'videoOrig'], ['cruise', '巡航', 'videoCruise'], ['cam', '视频参数', 'videoParam'], ['chart', '图表', 'videoChart'], ['plus', '自定义', 'videoCustom']];
      return PAGE(P.topbar('care'),
        '<div class="video-wrap">'
        + '<div class="video-view">' + P.fisheye(D.stamp) + '</div>'
        + '<div class="video-side">'
        + '<div class="seg"><button class="on">' + T('liveView', '实况') + '</button><button data-go="video-play">' + T('playback', '回放') + '</button></div>'
        + '<div class="vbtns"><div class="vbtn" style="justify-content:flex-start" data-act="camera_snapshot">' + I.cam + '<span>' + T('shot', '截图') + '</span></div>'
        + '<div class="vbtn" style="justify-content:flex-start" data-act="camera_record">' + I.rec + '<span>' + T('rec', '录制') + '</span></div>'
        + '<div class="vbtn" style="justify-content:flex-start" data-act="camera_fullscreen">' + I.full + '<span>' + T('full', '全屏') + '</span></div></div>'
        + '<div class="vgrid">' + btns.map(function (b) {
          var actAttr = (b[0] === 'cam') ? ' data-act="camera_config"' : '';
          return '<div class="vbtn' + (b[3] ? ' on' : '') + actAttr + '">' + I[b[0]] + '<span>' + T(b[2], b[1]) + '</span></div>';
        }).join('') + '</div>'
        + '</div></div>', P.sessionBar('video'), false, P.sessionTag());
    }
  };

  /* ======================================================================
     p10 视频 · 回放
     ====================================================================== */
  SCREENS['video-play'] = {
    name: '视频 · 回放', group: '2 会话', render() {
      let thumbs = '';
      for (let i = 0; i < 9; i++) thumbs += '<div class="thumb"><span class="th-ts">11:12</span><span class="th-d">19' + T('secUnit', '秒') + '</span><div class="fk"></div></div>';
      const side = [['img', 'aiRecog', 'Ai识图'], ['scissors', 'shot', '截图'], ['download', 'download', '下载'], ['trash', 'del', '删除'], ['manage', 'manage', '管理']];
      return PAGE(P.topbar('care'),
        '<div class="video-wrap" style="padding-top:12px">'
        + '<div><div class="play-top">'
        + '<div class="play-search">' + I.search + '<span>' + T('playSearchDate', '监控日期') + '</span></div>'
        + '<div class="seg"><button>' + T('today', '今天') + '</button><button>' + T('thisWeek', '本周') + '</button><button>' + T('thisMonth', '本月') + '</button></div>'
        + '<div class="seg"><button class="on">' + T('cloudRec', '云录像') + '</button><button>' + T('cardRec', '卡录像') + '</button><button>' + T('filter', '筛选') + '</button></div></div>'
        + '<div class="play-grid">' + thumbs + '</div>'
        + '<div class="timeline"><div style="font-size:20px;color:#575f6b;margin-bottom:6px">' + T('timeline', '时间轴') + '</div>'
        + '<div class="tl-ax"><span>0</span><span>5</span><span>10</span><span>15</span><span>20</span><span>24</span></div>'
        + '<div class="rail"></div>'
        + '<div style="text-align:center;font-size:20px;color:#575f6b">11:01:00</div></div></div>'
        + '<div class="play-side">'
        + '<div class="seg"><button data-go="video">' + T('liveView', '实况') + '</button><button class="on">' + T('playback', '回放') + '</button></div>'
        + side.map(function (b) { return '<div class="vbtn" style="justify-content:flex-start;padding-left:28px">' + I[b[0]] + '<span>' + T(b[1], b[2]) + '</span></div>'; }).join('')
        + '</div></div>', P.sessionBar('video'), false, P.sessionTag());
    }
  };

  /* ======================================================================
     p11 护理记录（列表 + 明细表）
     ====================================================================== */
  SCREENS['care-record'] = {
    name: '护理记录（样本列表）', group: '1 主流程', render() {
      /* ★ 任务15(#11)：本页只放样本列表；表单抽到独立的 record-sheet 页，
         点击某行后才选中（高亮）并跳转到该样本的治疗记录单。 */
      return PAGE(P.topbar('care'),
        '<div class="listwrap">' + careTable(D.careRows, true) + '</div>'
        + CARE_NOTE
        + A(20, 46, '样本号', 'strike') + A(24, 78, '住院号') + A(430, 46, '宠物主人') + A(780, 46, '病症'),
        P.bottombar(CARE_BAR, 'rec'));
    }
  };

  /* ======================================================================
     p11-1 治疗记录数据表（★ 任务16：护疗记录点行后跳转的本页，对齐 11-1.png）
     列：序号/项目/是否打开/报告录入/选取时段/平均值/最高/最低（18 项真实数据）
     ====================================================================== */
  SCREENS['record-data'] = {
    name: '治疗记录数据表', group: '1 主流程', render() {
      var pt = S.patient || {};
      var head = '<div style="text-align:center;font-size:21px;color:#575f6b;margin:8px 0 6px">'
        + T('treatStart', '治疗开始时间') + '：' + (pt.treatmentStartTime ? fmtDT(pt.treatmentStartTime) : '--')
        + '<span style="display:inline-block;width:60px"></span>'
        + T('treatEnd', '结束时间') + '：' + (pt.treatmentEndTime ? fmtDT(pt.treatmentEndTime) : '--') + '</div>';
      let t = '<table class="tbl" style="font-size:20px"><tr>'
        + [T('colSeq', '序号'), T('colItem', '项目'), T('colOpen', '是否打开'), T('colReport', '报告录入'), T('colPeriod', '选取时段'), T('colAvg', '平均值'), T('colHigh', '最高'), T('colLow', '最低')].map(function (x) { return '<th>' + x + '</th>'; }).join('')
        + '</tr>';
      D.recordRows.forEach(function (r) {
        var no = String(r[0] || '');
        var opened = (no === '16' || no === '17' || no === '18') ? '—' : (r[2] || '✗');
        t += '<tr><td>' + no + '</td><td>' + (r[1] || '') + '</td>'
          + '<td><span style="color:' + (opened === '✓' ? '#22b14c' : '#8b95a6') + '">' + opened + '</span></td>'
          + '<td><span style="color:' + (r[3] === '✓' ? '#22b14c' : '#f0483e') + '">' + (r[3] || '✗') + '</span></td>'
          + '<td>' + (r[4] || T('openPeriod', '打开时段')) + '</td>'
          + '<td>' + (r[5] || '--') + '</td><td>' + (r[6] || '--') + '</td><td>' + (r[7] || '--') + '</td></tr>';
      });
      t += '</table>';
      return PAGE(P.topbar('care'), head + t, P.bottombar(CARE_BAR, 'rec'));
    }
  };

  /* ======================================================================
     p12 治疗记录单（可打印）
     ====================================================================== */
  SCREENS['record-sheet'] = {
    name: '治疗记录单（可打印）', group: '1 主流程', render() {
      const s = D.sheet;
      /* ★ 任务26a/29：数据编辑态。D.sheetEdit 非空 = 编辑中：
         可编辑字段（主人/体重/科室/操作医师、治疗项目勾选+参数、环境参数、体征表增删行、治疗结论）渲染为输入框/可点选框，
         值取自 D.sheetEdit 暂存；底栏切换为 保存/取消。保存经 act('update_record') 落本地存储（#26c）。 */
      const ed = D.sheetEdit || null;
      /* 文本字段：只读态原样输出；编辑态渲染输入框
         ★ 任务32：字段限制 —— 主人/科室/医师30、体重6(数字+小数点)、建议100 */
      var SE_LIMITS = { owner: 30, weight: 6, dept: 30, doc: 30, advice: 100 };
      function seTxt(key, val) {
        if (!ed) return val;
        var mx = SE_LIMITS[key] ? ' maxlength="' + SE_LIMITS[key] + '"' : '';
        var num = key === 'weight' ? ' data-num2="1"' : '';
        return '<input class="sheet-in" data-se="' + key + '"' + mx + num + ' value="' + (val || '') + '">';
      }
      const info = '<div class="sheet-info">'
        + '<div><b>' + T('sheetAnimal', '动物名称') + '：</b>' + s.animal + '</div><div><b>' + T('fieldCaseNo', '住院号') + '：</b>' + s.no + '</div>'
        + '<div><b>' + T('sheetOwner', '主人') + '：</b>' + seTxt('owner', s.owner) + '</div><div><b>' + T('sheetTreatDate', '治疗日期') + '：</b>' + s.date + '</div><div><b>' + T('sheetCage', '舱位') + '：</b>' + s.cage + '</div>'
        + '<div><b>' + T('sheetSpecies', '动物种类') + '：</b>' + spTxt(s.sp) + '</div><div><b>' + T('sheetWeight', '体重') + '：</b>' + seTxt('weight', s.weight) + '</div>'
        + '<div><b>' + T('sheetDept', '科室') + '：</b>' + seTxt('dept', s.dept) + '</div><div><b>' + T('sheetDur', '治疗时长') + '：</b>' + s.dur + '</div><div><b>' + T('sheetDoctor', '操作医师') + '：</b>' + seTxt('doc', s.doc) + '</div>'
        + '</div>';
      const proj = '<div class="tproj">' + s.proj.map(function (p, i) {
        if (ed) {
          const ep = (ed.proj && ed.proj[i]) || p;
          return '<div class="p ed" data-proj-i="' + i + '"><span class="ckbox' + (ep.on ? ' on' : '') + '"></span><span class="lab">' + p.lab + '</span>'
            + '<input class="sheet-in pv" data-se="proj-val-' + i + '" maxlength="20" value="' + (ep.val || '') + '"></div>';
        }
        return '<div class="p"><span class="ckbox' + (p.on ? ' on' : '') + '"></span><span class="lab">' + p.lab + '</span><span class="val">' + p.val + '</span></div>';
      }).join('') + '</div>';
      /* ★ 任务29（#26b）：编辑态体征表 —— 单元格变输入框，行尾删除按钮，表尾「添加行」；
         只读态保持原样（无数据显示 4 行空行） */
      let vs = '<table class="tbl sheet-tbl"><tr>'
        + [T('colTime', '时间'), T('monHr', '心率'), T('monSpo2', '血氧'), T('monBp', '血压'), T('monTemp', '体温')].map(function (x) { return '<th>' + x + '</th>'; }).join('')
        + (ed ? '<th class="vr-op"></th>' : '') + '</tr>';
      if (ed) {
        const evr = ed.vitals || [];
        evr.forEach(function (r, i) {
          vs += '<tr class="vr-row">'
            + [0, 1, 2, 3, 4].map(function (c) {
              return '<td><input class="sheet-in vr-in" data-vr="' + i + '-' + c + '" value="' + (r[c] || '') + '"'
                + (c === 0 ? ' placeholder="HH:MM"' : '') + '></td>';
            }).join('')
            + '<td class="vr-op"><span class="vr-del" data-vr-del="' + i + '">×</span></td></tr>';
        });
        if (!evr.length) vs += '<tr class="vr-empty"><td colspan="6">' + T('sheetNoVitals', '暂无体征记录，可点击下方「添加行」录入') + '</td></tr>';
        vs += '</table><div class="vr-addbar"><span class="vr-add">' + T('sheetAddRow', '＋ 添加行') + '</span></div>';
      } else {
        const vr = D.sheet.vitalsRows || [];
        vr.forEach(function (r) { vs += '<tr><td>' + r[0] + '</td><td>' + r[1] + '</td><td>' + r[2] + '</td><td>' + r[3] + '</td><td>' + r[4] + '</td></tr>'; });
        if (!vr.length) { for (let i = 0; i < 4; i++) vs += '<tr><td>&nbsp;</td><td></td><td></td><td></td><td></td></tr>'; }
        vs += '</table>';
      }
      /* 三条波形（对齐设计图：心电图 / 脉搏波形图 / 呼吸率图） */
      const ws = P.waveStrip('ecg', '#3dff6b', T('waveEcg', '【心电图】'))
        + P.waveStrip('pleth', '#ff6b6b', T('wavePleth', '【脉搏波形图】'))
        + P.waveStrip('resp', '#4aa3ff', T('waveResp', '【呼吸率图】'));
      /* 【环境参数】舱内温度 / 湿度 / CO₂浓度 / 氧浓度 */
      const env = '<div class="sheet-env">' + s.env.map(function (e, i) {
        if (ed) {
          const ev = (ed.env && ed.env[i] != null) ? ed.env[i] : e[1];
          return '<div class="e"><b>' + e[0] + '：</b><input class="sheet-in ev" data-se="env-' + i + '" maxlength="8" data-num2="1" value="' + (ev || '') + '"></div>';
        }
        return '<div class="e"><b>' + e[0] + '：</b><span>' + e[1] + '</span></div>';
      }).join('') + '</div>';
      /* 【影像记录】图片 × 2 + 二维码 */
      const imgs = '<div class="img-row">' + s.imgs.map(function (im) {
        return '<div class="imgcell"><div class="ph' + (im.qr ? ' qr' : '') + '">' + im.lab + '</div>'
          + '<div class="cp">' + im.cap + '</div><div class="tm">' + (im.t || '') + '</div></div>';
      }).join('') + '</div>';
      /* 【治疗结论】治疗效果三态 + 出院建议（任务26a：selIdx 记录选中态，编辑态可点选/编辑建议） */
      const ci = ed ? (ed.conclIdx || 0) : (s.concl.selIdx || 0);
      const conclKeys = ['conclGood', 'conclFair', 'conclPoor'];
      const concl = '<div class="concl">'
        + '<div class="crow"><label>' + T('sheetEffect', '治疗效果：') + '</label>' + s.concl.states.map(function (x, i) {
          return '<span class="opt' + (ed ? ' ed' : '') + '"' + (ed ? ' data-concl-i="' + i + '"' : '') + '><span class="ckbox' + (i === ci ? ' on' : '') + '"></span>' + T(conclKeys[i] || 'conclFair', x) + '</span>';
        }).join('') + '</div>'
        + '<div class="crow"><label>' + T('sheetAdvice', '出院建议：') + '</label>'
        + (ed ? '<input class="sheet-in advice-in" data-se="advice" value="' + (ed.advice || '') + '">'
          : '<div class="advice">' + s.concl.advice + '</div>')
        + '</div></div>';
      /* 页脚：医院信息 / 检验信息 / 品牌 */
      const foot = '<div class="sheet-foot">'
        + '<div class="fcol"><div>' + T('hospitalPhone', '医院电话') + '：' + s.foot.tel + '</div><div>' + T('hospitalAddr', '医院地址') + '：' + s.foot.addr + '</div><div>' + s.foot.note + '</div></div>'
        + '<div class="fcol"><div>' + T('sheetLabDoc', '检验医师：') + s.foot.doc + '</div><div>' + T('sheetLabTime', '检验时间：') + fmtDT(s.foot.time) + '</div><div>' + s.foot.page + '</div></div>'
        + '<div class="fbrand">' + (s.footerLogoUri ? '<img class="fb-img" src="' + s.footerLogoUri + '" alt="">'
          : (window.NI && window.NI.paw ? NI.paw : I.paw) + '<div class="bt"><b>优利特</b><span>URIT animal</span></div>') + '</div></div>';
      /* ★ 任务26a：编辑态底栏只留 保存/取消，避免编辑中误切样本/打印 */
      const bar = P.bottombar(ed ? [
        { id: 'sv', label: '保存', key: 'recordSave', icon: 'check', go: 'record-sheet' },
        { id: 'cc', label: '取消', key: 'cancel', icon: 'x', go: 'record-sheet' },
      ] : [
        { id: 'p', label: '上一样本', key: 'recordPrev', icon: 'back', go: 'record-sheet' },
        { id: 'n', label: '下一样本', key: 'recordNext', icon: 'play', go: 'record-sheet' },
        { id: 'ed', label: '编辑', key: 'edit', icon: 'pencil', go: 'record-sheet' },
        { id: 'pr', label: '打印', key: 'print', icon: 'print', go: 'printing' },
        { id: 'ex', label: '导出', key: 'exportReport', icon: 'export', go: 'sending' },
      ], '');
      return PAGE(P.topbar('care'),
        '<div class="sheet">'
        + '<div class="sheet-head"><div class="lg">' + (s.logoUri ? '<img src="' + s.logoUri + '" alt="LOGO">' : 'LOGO') + '</div><div class="hname">' + D.hospital + '</div>'
        + '<div class="htitle">' + (s.title || T('sheetTitle', 'ICU动物舱治疗记录单')) + '</div></div>'
        + info
        + '<div class="sec-title">' + T('secProj', '【治疗项目】') + '</div><div class="sec-line"></div>' + proj
        + '<div class="sec-title">' + T('secVitals', '【生命体征记录】') + '</div><div class="sec-line"></div>' + vs
        + ws
        + '<div class="sec-title">' + T('secEnv', '【环境参数】') + '</div><div class="sec-line"></div>' + env
        + '<div class="sheet-duo">'
        + '<div class="duo-col"><div class="sec-title">' + T('secImgs', '【影像记录】') + '</div><div class="sec-line"></div>' + imgs + '</div>'
        + '<div class="duo-col"><div class="sec-title">' + T('secAlarm', '【报警事件】') + '</div><div class="sec-line"></div><div class="alarmbox">' + s.alarm + '</div></div>'
        + '</div>'
        + '<div class="sec-title">' + T('secConcl', '【治疗结论】') + '</div><div class="sec-line"></div>' + concl
        + foot
        + '</div>', bar, false, P.sessionTag());
    }
  };

  /* ======================================================================
     p13 回顾列表
     ====================================================================== */
  /* 回顾列表（对齐设计图 13.png：住院号 / 宠物名 / 物种 / 宠物主人 / 联系电话 / 医生 / 病症 / 创建时间）
     —— 原「护疗类型」列在改版设计图中已删除 */
  function reviewTable(selFirst) {
    const head = [T('fieldCaseNo', '住院号'), T('fieldPetName', '宠物名'), T('fieldSpecies', '物种'), T('fieldOwner', '宠物主人'), T('fieldPhone', '联系电话'), T('colDoctor', '医生'), T('colDisease', '病症'), T('colCreateTime', '创建时间')];
    let t = '<table class="tbl list" style="font-size:21px"><tr><th class="ck"></th>'
      + head.map(function (x) { return '<th>' + x + '</th>'; }).join('') + '</tr>';
    D.reviewRows.forEach(function (r, i) {
      const selId = D.selCaseId || '';
      const on = (r.caseId && r.caseId === selId) || (!selId && i === 0 && selFirst);
      t += '<tr class="' + (on ? 'sel' : '') + '" data-caseid="' + (r.caseId || '') + '"><td class="ck"><span class="ckbox' + (on ? ' on' : '') + '"></span></td>'
        + '<td>' + r.no + '</td><td>' + r.name + '</td><td>' + spTxt(r.sp) + '</td>'
        + '<td>' + r.owner + '</td><td>' + r.tel + '</td><td>' + r.doc + '</td>'
        + '<td><input class="iln-in" data-disease data-caseid="' + (r.caseId || '') + '" value="' + (r.illness || '') + '" placeholder="' + T('phDisease', '请输入') + '" maxlength="20"></td>'
        + '<td>' + fmtDT(r.time) + '</td></tr>';
    });
    return t + '</table>';
  }
  SCREENS['review'] = {
    name: '回顾列表', group: '3 回顾', render() {
      return PAGE(P.topbar('review'),
        '<div class="listwrap">' + reviewTable(true) + '</div>'
        + A(300, 42, '表头同步【护理】列表保持一致', 'plain')
        + A(150, 560, '（注：该页面表格，显示除【护理】列表中的，所有护疗记录）', 'plain'),
        P.bottombar(REVIEW_BAR, ''));
    }
  };

  /* ======================================================================
     p14 查询（弹窗）
     ====================================================================== */
  /* 查询条件行：标签左对齐（119px）+ 内容两列（各 341px，中缝 198px 放 "~" 或行内标签）
     字段与 PDF P15 一致：护疗时间 / 住院号 / 宠物名+医生 / 宠物主人 / 联系电话 / 物种
     （原「护疗类型」标签在 PDF 中被划掉，该行改为「宠物主人」，故不再保留护疗类型） */
  function qrow(label, inner, wide) {
    return '<div class="qrow"><label>' + label + '</label>'
      + '<div class="qctl' + (wide ? ' wide' : '') + '">' + inner + '</div></div>';
  }
  function qinp(v, ico) {
    return '<div class="qinp">' + (ico ? I[ico] : '') + '<span>' + (v || '') + '</span></div>';
  }
  SCREENS['query'] = {
    name: '查询', group: '3 回顾', render() {
      /* 物种与【新建样本】共用同一份数据（D.species），保证两处选项永远一致 */
      const sp = D.species.map(function (x) {
        return '<div class="chip" data-v="' + x + '">' + spTxt(x) + '</div>';
      }).join('');
      const body = '<div class="qform">'
        + qrow(T('qCareTime', '护疗时间'), qinp('', 'cal') + '<span class="tilde">~</span>' + qinp('', 'cal'))
        + qrow(T('fieldCaseNo', '住院号'), qinp('') + '<span class="tilde">~</span>' + qinp(''))
        + qrow(T('fieldPetName', '宠物名'), qinp('') + '<span class="q-lab">' + T('colDoctor', '医生') + '</span>' + qinp(''))
        + qrow(T('fieldOwner', '宠物主人'), qinp(''))
        + qrow(T('fieldPhone', '联系电话'), qinp(''))
        + qrow(T('fieldSpecies', '物种'), '<div class="qchips">' + sp + '</div>', true)
        + '</div>';
      return PAGE(P.topbar('review'),
        '<div class="listwrap" style="opacity:.5">' + careTable([]) + '</div>'
        + P.mask('<div class="modal qmodal"><div class="mhead">' + T('query', '查询') + '</div><div class="mbody">' + body + '</div>'
          + '<div class="mfoot"><div class="btn" data-go="review">' + T('confirm', '确认') + '</div><div class="btn primary" data-go="review">' + T('cancel', '取消') + '</div></div></div>')
        + A(452, 315, '住院号') + A(452, 356, '样本号', 'strike')
        + A(452, 489, '宠物主人') + A(452, 530, '护疗类型', 'strike')
        + A(452, 575, '联系电话')
        + A(875, 712, '与新建物种一样', 'plain'),
        P.bottombar(REVIEW_BAR, 'q'));
    }
  };

  /* ======================================================================
     p15 打印中 / p16 发送数据 / p17 删除确认
     ====================================================================== */
  /* 回顾底部工具栏：按设计图 15-1/15-2/15-3 实现「发送数据」交互 */
  const REVIEW_BAR = [
    { id: 'q', label: '查询', key: 'query', icon: 'search', go: 'query' },
    { id: 'r', label: '护疗记录', key: 'careRecord', icon: 'doc', go: 'care-record' },
    { id: 'p', label: '打印', key: 'print', icon: 'print', go: 'printing' },
    { id: 'e', label: '导出报告', key: 'exportReport', icon: 'export', go: 'sending' },
    { id: 's', label: '发送数据', key: 'sendData', icon: 'upload', go: 'sending' },
    { id: 'd', label: '删除', key: 'del', icon: 'trash', go: 'del-confirm' },
  ];
  function reviewBg() {
    return '<div style="padding:14px 0 0">' + reviewTable(false) + '</div>';
  }
  SCREENS['printing'] = {
    name: '打印中', group: '3 回顾', render() {
      return PAGE(P.topbar('review'), reviewBg() + P.loadingMask(T('printing', '正在打印......')), P.bottombar(REVIEW_BAR, 'p'));
    }
  };
  SCREENS['sending'] = {
    name: '发送数据', group: '3 回顾', render() {
      /* 对齐设计图 15-1 / 15-2 / 15-3：
         底栏「发送数据」可点；进入本屏默认弹出「文件助手 / 蓝牙传输」菜单；
         选「文件助手」→ 二维码卡片（15-2，微信文件助手传输）；选「蓝牙传输」→ 正在发送卡片（15-3，spinner + act('send_data')） */
      const mi = [[T('sendFileHelper', '文件助手'), 'doc', 'file'], [T('sendBle', '蓝牙传输'), 'wifi', 'ble']];
      /* ★ 任务16(#11)：默认不高亮 —— 点击后才由 bridge 加 'on' */
      const menu = '<div class="send-menu">' + mi.map(function (m) {
        return '<div class="mi" data-send="' + m[2] + '">' + (I[m[1]] || I.doc) + '<span>' + m[0] + '</span></div>';
      }).join('') + '</div>';
      /* ★ 任务28：二维码内容由 bridge 按原生回推的 S.shareQr.url 真实渲染（id=shareQrBox），下方附转发指引 */
      const qr = '<div class="send-qr"><div class="code" id="shareQrBox"></div><div class="cap">' + T('sendQrCap', '微信文件助手传输') + '</div>'
        + '<div class="qrtip">' + T('sendQrTip', '微信扫码打开报告 → 右上角「…」→ 发送给「文件传输助手」') + '</div></div>';
      return PAGE(P.topbar('review'), reviewBg()
        + '<div class="send-card qrcard" hidden>' + P.mask(qr) + '</div>'
        + '<div class="send-card blecard" hidden>' + P.loadingMask(T('sendingNow', '正在发送......')) + '</div>'
        + menu,
        P.bottombar(REVIEW_BAR, 's'));
    }
  };
  SCREENS['del-confirm'] = {
    name: '删除确认', group: '3 回顾', render() {
      return PAGE(P.topbar('review'), reviewBg()
        + P.mask(P.modal(T('tipTitle', '提示'), '<div class="alert-row">' + I.warn + '<span>' + T('delDataBody', '确认删除数据?') + '</span></div>',
          '<div class="btn" data-go="review">' + T('confirm', '确认') + '</div><div class="btn primary" data-go="review">' + T('cancel', '取消') + '</div>')),
        P.bottombar(REVIEW_BAR, 'd'));
    }
  };

  /* ======================================================================
     p18 菜单
     ====================================================================== */
  SCREENS['menu'] = {
    name: '菜单', group: '4 系统', render() {
      const tiles = [
        /* ★ 2026-09-30 用户确认：调试对所有角色开放 —— 工程师直进，管理员/用户输密码 */
        ['set-general', 'menuSet', 'settings', 0], ['comp', 'menuDebug', 'tune', 0], ['exit-confirm', 'menuClose', 'x', 0],
        ['about', 'menuAbout', 'info', 0], ['log', 'menuLog', 'log', 0], ['logout-confirm', 'menuLogout', 'logout', 0],
      ];
      const t = tiles.map(function (x) {
        return '<div class="tile' + (x[3] ? ' dis' : '') + '"' + (x[3] ? '' : ' data-go="' + x[0] + '"') + '>'
          + '<div class="ti">' + (I[x[2]] || I.doc) + '</div><span>' + T(x[1], x[1]) + '</span></div>';
      }).join('');
      /* ★ 任务13（R93）→ 2026-09-30 用户确认：舱区切换卡，所有角色均可切换 */
      var isLeft = (D.zone !== 'right');
      var zoneCard = '<div class="zone-card">'
        + '<span class="zt">' + T('zoneTitle', '当前舱区') + '：' + (isLeft ? T('zoneLeft', '左舱') : T('zoneRight', '右舱')) + '</span>'
        + '<div class="seg zseg">'
        + '<button data-zone="left"' + (isLeft ? ' class="on"' : '')
        + '>' + T('zoneLeft', '左舱') + '</button>'
        + '<button data-zone="right"' + (isLeft ? '' : ' class="on"')
        + '>' + T('zoneRight', '右舱') + '</button>'
        + '</div>'
        + '</div>';
      /* ★ 任务21：调试密码弹层（角色通过后才弹出，输入正确密码才进 comp） */
      var pwdModal = (window.__debugPwdPending) ? (
        '<div class="mask debug-pwd-mask"><div class="modal debug-pwd-modal">'
          + '<div class="mhead">' + T('debugPwdTitle', '调试密码验证') + '</div>'
          + '<div class="mbody"><div class="lf"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="#8b95a6" stroke-width="1.8"><rect x="5" y="10.5" width="14" height="9" rx="2"/><path d="M8.4 10.5V8a3.6 3.6 0 0 1 7.2 0v2.5"/></svg><input class="debug-pwd-input" type="password" placeholder="' + T('debugPwdPlaceholder', '请输入调试密码') + '"></div>'
          + '<div class="debug-pwd-err" style="color:#ff6969;font-size:20px;min-height:26px;margin-top:6px"></div></div>'
          + '<div class="mfoot"><div class="btn" data-action="debug-pwd-cancel">' + T('debugPwdCancel', '取消') + '</div>'
          + '<div class="btn primary" data-action="debug-pwd-ok">' + T('debugPwdOk', '确认进入') + '</div></div>'
          + '</div></div>'
      ) : '';
      return PAGE(P.topbar('menu'), zoneCard + '<div class="menu-grid">' + t + '</div>' + pwdModal, null, true);
    }
  };

  /* ======================================================================
     设置：左侧栏
     ====================================================================== */
  /* 设置左侧栏：对齐设计图 20.png / 25.png —— 仅 6 项（补偿页不在设置内，由 菜单→调试 进入） */
  const SIDE = [['set-general', '常规', 'settings', 'setGeneral'], ['set-conn', '连接', 'globe', 'setConn'], ['set-transfer', '传输', 'swap', 'setTransfer'],
  ['set-print', '打印', 'print', 'setPrint'], ['set-user', '用户', 'user', 'setUser'], ['upgrade-sw', '升级', 'gearup', 'setUpgrade']];
  function sideList(active) {
    return '<div class="side">' + SIDE.map(function (s) {
      return '<div class="sideitem' + (s[0] === active ? ' on' : '') + '" data-go="' + s[0] + '">' + I[s[2]] + '<span>' + T(s[3], s[1]) + '</span></div>';
    }).join('') + '</div>';
  }
  function setPage(active, body) {
    return PAGE(P.topbarBack('menu'), '<div class="set-wrap">' + sideList(active) + '<div class="setbody">' + body + '</div></div>', null, true);
  }
  function frow(label, inner) { return '<div class="frow"><label>' + label + '</label><div class="ctl">' + inner + '</div></div>'; }

  /* p19 设置·常规 */
  SCREENS['set-general'] = {
    name: '设置 · 常规', group: '5 设置', render() {
      var isEn = D.lang === 'en';
      /* ★ 任务15(#4)：语言改为「选择列表」下拉（不要中英分段按钮混排） */
      var langSel = '<select class="icu-input lang-select" data-langsel>'
        + '<option value="zh"' + (isEn ? '' : ' selected') + '>' + T('langZh', '中文') + '</option>'
        + '<option value="en"' + (isEn ? ' selected' : '') + '>' + T('langEn', 'English') + '</option></select>';
      return setPage('set-general',
        '<div style="width:960px;margin:0 auto">'
        + frow(T('lang', '语言'), '<div class="inp" style="width:400px">' + langSel + '</div>')
        /* ★ 2026-09-30：时间自动显示系统当前时间（bridge 每秒刷新 [data-sysclock]）；「应用/格式」按钮无实际功能，已按用户要求删除 */
        + frow(T('time', '时间'), '<div class="inp" style="width:400px"><span data-sysclock>' + T('dateTimePh', '----年--月--日 --:--:--') + '</span></div>')
        /* ★ 任务16(#10)：删除「工程师参数（出厂后不可修改，置灰）」提示行 */
        + frow(T('model', '型号'), '<div class="inp dis" style="width:400px"></div>')
        + frow(T('sn', '机号/SN'), '<div class="inp dis" style="width:400px"></div>')
        + frow(T('prodDate', '生产日期'), '<div class="inp dis" style="width:400px"></div>')
        /* ★ 任务15(#12)：恢复出厂设置已移到「调试」页第 6 个模块 */
        + '</div>'
        + A(560, 330, '（这三个工程师账号可编辑，出厂后不可修改，为灰色）', 'plain'));
    }
  };

  /* p20 设置·连接 */
  /* ★ 2026-10-08 蓝牙设备选择弹窗：主机/监护分开（D.blePick='host'|'monitor'）
     数据源 S.host.devices / S.ble.monitor.devices（原生扫描实时推送），点行即连接 */
  function blePickModal() {
    if (!D.blePick) return '';
    const isHost = D.blePick === 'host';
    const st = window.S || {};
    const ch = isHost ? (st.host || (st.ble && st.ble.host) || {}) : ((st.ble && st.ble.monitor) || {});
    const devs = Array.isArray(ch.devices) ? ch.devices : [];
    const title = isHost ? T('blePickHost', '选择主机蓝牙') : T('blePickMon', '选择监护设备');
    const rows = devs.length
      ? devs.map(function (d) {
        return '<div class="bledev" data-bledev="' + esc(d.deviceId || d.id || '') + '">'
          + '<span class="bn">' + esc(d.deviceName || d.name || '--') + '</span>'
          + '<span class="br">RSSI ' + esc(d.rssi == null ? '--' : d.rssi) + '</span></div>';
      }).join('')
      : '<div class="logempty">' + T('bleScanning', '正在搜索附近设备…') + '</div>';
    return P.mask('<div class="modal blepick"><div class="mhead">' + title + '</div>'
      + '<div class="mbody"><div class="blelist">' + rows + '</div></div>'
      + '<div class="mfoot"><div class="btn" data-blepick-rescan>' + T('bleRescan', '重新搜索') + '</div>'
      + '<div class="btn primary" data-blepick-close>' + T('cancel', '取消') + '</div></div></div>');
  }
  SCREENS['set-conn'] = {
    name: '设置 · 连接', group: '5 设置', render() {
      /* ★ 任务13（R53-R55）：读原生真实状态；开关点击走 wifi_toggle / host_ble_toggle / monitor_ble_toggle */
      var st = (window.S || {});
      var host = st.host || (st.ble && st.ble.host) || {};
      var mon = (st.ble && st.ble.monitor) || {};
      var wifi = st.wifi || {};
      var hostName = host.deviceName || host.deviceId || '';
      var wifiOn = !!wifi.enabled;
      /* ★ 2026-10-08：连接上显示对端名称（wifi=SSID，蓝牙=设备名），不再显示「已连接」；
         SSID 读不到时区分原因提示：系统定位未开 / 缺定位权限 */
      var wifiTxt = wifiOn
        ? (wifi.current || (wifi.locOff ? T('wifiLocOff', '请开启系统定位后重试') : (wifi.needLocPerm ? T('wifiNeedPerm', '请授予定位权限') : '--')))
        : T('connNotConn', '未连接');
      var hostOn = !!host.connected;
      var hostTxt = hostOn ? (host.deviceName || host.deviceId || '--') : T('connNotConn', '未连接');
      var monOn = !!mon.connected;
      var monTxt = monOn ? (mon.deviceName || mon.deviceId || '--') : T('connNotConn', '未连接');
      const tg = (on, act) => '<div class="toggle' + (on ? ' on' : '') + '" data-sw data-conn="' + act + '">'
        + '<span class="l">' + (on ? T('on', '开') : T('off', '关')) + '</span></div>';
      const val = (t) => '<div class="inp flex"><span style="flex:1">' + t + '</span></div>';
      return setPage('set-conn',
        '<div style="width:1100px;margin:0 auto">'
        + frow(T('connHost', '主机名称'), val(hostName || ''))
        + frow(T('connWifi', 'wifi连接'), val(wifiTxt) + tg(wifiOn, 'wifi'))
        + frow(T('connHostBle', '主机蓝牙'), val(hostTxt) + tg(hostOn, 'host_ble'))
        + frow(T('connMonitorBle', '监护蓝牙'), val(monTxt) + tg(monOn, 'monitor_ble'))
        + '</div>' + blePickModal() + A(760, 470, '开关即时生效', 'plain'));
    }
  };

  /* p21 设置·传输 */
  SCREENS['set-transfer'] = {
    name: '设置 · 传输', group: '5 设置', render() {
      /* V1.02：云平台 / LIS 置灰预留，标注「二期」 */
      const card = (t) => '<div class="card dis" style="width:430px;height:380px;padding:34px 40px">'
        + '<div style="font-size:30px;margin-bottom:34px;display:flex;align-items:center;gap:18px">' + t
        + '<em class="tag2">' + T('tagPhase2', '二期') + '</em></div>'
        + '<div style="display:flex;align-items:center;gap:18px;margin-bottom:30px;opacity:.5"><span style="font-size:24px">' + T('transferConn', '连接') + '</span>'
        + '<div class="toggle"><span class="l">' + T('off', '关') + '</span></div></div>'
        + '<div style="display:flex;align-items:center;gap:14px;font-size:24px;opacity:.5"><span style="color:#575f6b">' + T('statusLabel', '状态：') + '</span>'
        + '<span class="ckbox"></span><span>' + T('notLinked', '未接入') + '</span></div></div>';
      return setPage('set-transfer',
        '<div style="display:flex;gap:70px;padding:80px 0 0 120px">' + card(T('cloudPlatform', '云平台')) + card('LIS') + '</div>');
    }
  };

  /* p22 设置·打印 */
  SCREENS['set-print'] = {
    name: '设置 · 打印', group: '5 设置', render() {
      /* ★ 任务14（R58-R66）：按钮全部接线
         data-print: scan=扫描打印机 / addPrinter=添加打印机 / test=打印测试页 /
                     org=保存医院信息 / logo=上传LOGO / reportTitle / reportDecl */
      var org = (S.organization || {});
      /* ★ 任务32：字段长度/字符限制 —— 名称30 电话20(电话字符过滤) 地址50 */
      var ORG_LIMITS = { name: 30, phone: 20, address: 50 };
      var pf = function (k, val) {
        var mx = ORG_LIMITS[k] ? ' maxlength="' + ORG_LIMITS[k] + '"' : '';
        var tel = k === 'phone' ? ' data-tel="1"' : '';
        return '<div class="inp flex"><input class="icu-input" data-org="' + k + '"' + mx + tel + ' value="' + (val || '') + '"></div>';
      };
      var setBtn = '<div class="btn sm" data-print="org">' + T('setBtn', '设置') + '</div>';
      var upLogo = '<div class="btn sm" data-print="logo">' + T('uploadLogo', '上传LOGO') + '</div>';
      var upFooter = '<div class="btn sm" data-print="footerLogo">' + T('uploadLogo', '上传LOGO') + '</div>';
      /* ★ 任务15(#5)：整页包一层 .print-fields，让字段输入框保持平铺、不凸显 */
      var body = '<div class="print-fields" style="width:1180px;margin:0 auto">'
        + frow(T('printerAvail', '可用打印机'), '<div class="inp flex">' + I.chev + '</div><div class="btn sm" data-print="scan">' + T('scanPrinter', '扫描') + '</div>')
        + frow(T('printerAddr', '打印机地址'), '<div class="inp flex"><input class="icu-input" data-print-addr maxlength="30" data-addr="1" placeholder="' + T('printerAddrPh', '如 192.168.1.10:9100') + '" value="'
          + ((D.settings && D.settings.printerAddress) || '') + '"></div>'
          + '<div class="btn sm" data-print="addPrinter">' + T('addPrinter', '添加打印机') + '</div><div class="btn sm" data-print="test">' + T('printTest', '打印测试页') + '</div>')
        + frow(T('hospitalName', '医院名称'), pf('name', org.name) + setBtn)
        + frow(T('hospitalPhone', '医院电话'), pf('phone', org.phone) + setBtn)
        + frow(T('hospitalAddr', '医院地址'), pf('address', org.address) + setBtn)
        + '<div class="frow"><label>' + T('hospitalLogo', '医院LOGO') + '</label><div class="ctl" style="gap:40px">'
        + '<div style="text-align:center"><div class="logo-box"></div><div style="font-size:20px;margin-top:6px;color:#575f6b">' + T('curLogo', '当前LOGO') + '</div></div>'
        + upLogo
        + '<div style="text-align:center"><div style="font-size:22px;margin-bottom:6px">' + T('footerLogo', '页脚LOGO') + '</div><div class="logo-box"></div><div style="font-size:20px;margin-top:6px;color:#575f6b">' + T('curLogo', '当前LOGO') + '</div></div>'
        + upFooter
        + '</div></div>'
        + frow(T('reportTitle', '报告标题'), '<div class="inp flex"><input class="icu-input" data-print-title maxlength="30" value="'
          + ((D.settings && D.settings.reportTitle) || '') + '"></div><div class="btn sm" data-print="reportTitle">' + T('setBtn', '设置') + '</div>')
        + frow(T('reportDecl', '报告声明'), '<div class="inp flex"><input class="icu-input" data-print-decl maxlength="60" value="'
          + ((D.settings && D.settings.reportDeclaration) || '') + '"></div><div class="btn sm" data-print="reportDecl">' + T('setBtn', '设置') + '</div>')
        + '</div>';
      return setPage('set-print', body);
    }
  };

  /* p23 / p24 设置·用户（列表 + 新增/编辑弹窗）
     ★ 任务13（R67-R73）：列表读 D.users（原生 accounts），多选框可选行，
       新增/编辑/删除走 account_save / account_delete。 */
  const ROLE_LABEL = { service: 'roleService', admin: 'roleAdmin', user: 'roleUser' };
  function roleText(r) { return T(ROLE_LABEL[r] || 'roleUser', r === 'service' ? '工程师' : (r === 'admin' ? '管理员' : '用户')); }
  function userTable() {
    var rows = (D.users || []).map(function (u) {
      var sel = (window.__selUser && window.__selUser === u.name) ? ' class="sel"' : '';
      var on = sel ? ' on' : '';
      return '<tr' + sel + ' data-uname="' + u.name + '"><td>' + u.name + (u.isCurrent ? T('userCurrent', '（当前）') : '')
        + '</td><td>' + u.pwd + '</td><td>' + roleText(u.role) + '</td>'
        + '<td class="ck"><span class="ckbox' + on + '"></span></td></tr>';
    }).join('');
    if (!rows) {
      rows = '<tr><td colspan="4" style="color:#8b95a6">—</td></tr>';
    }
    return '<table class="tbl usertable"><tr><th>' + T('nameLabel', '用户名') + '</th><th>'
      + T('pwdLabel', '密码') + '</th><th>' + T('roleLabel', '权限') + '</th><th class="ck"></th></tr>'
      + rows + '</table>';
  }
  function userRoleSelect(sel) {
    var opts = ['service', 'admin', 'user'].map(function (r) {
      return '<option value="' + r + '"' + (r === sel ? ' selected' : '') + '>' + roleText(r) + '</option>';
    }).join('');
    return '<select class="icu-input" data-urole>' + opts + '</select>';
  }
  function setUserBtns() {
    return '<div class="userbtns">'
      + '<div class="btn" data-uact="add">' + T('userAddBtn', '新增') + '</div>'
      + '<div class="btn primary" data-uact="edit">' + T('userEditBtn', '编辑/修改') + '</div>'
      + '<div class="btn" data-uact="del">' + T('userDelBtn', '删除') + '</div>'
      + '<div class="btn" data-uact="cancel">' + T('userCancelBtn', '取消') + '</div></div>';
  }
  SCREENS['set-user'] = {
    name: '设置 · 用户', group: '5 设置', render() {
      var body = userTable() + setUserBtns();
      /* ★ 任务13（R73）：删除二次确认 */
      if (window.__userDelConfirm) {
        body += P.mask(P.modal(T('delUserTitle', '删除用户'),
          '<div style="width:520px;font-size:22px;color:#575f6b">' + T('delUserBody', '确认删除该用户？删除后不可恢复。')
          + '<div style="margin-top:18px">' + T('nameLabel', '用户名') + '：<b>' + window.__userDelConfirm + '</b></div></div>',
          '<div class="btn" data-udel="yes">' + T('yes', '是') + '</div>'
          + '<div class="btn primary" data-udel="no">' + T('cancel', '取消') + '</div>'));
      }
      return setPage('set-user', body);
    }
  };
  SCREENS['set-user-edit'] = {
    name: '设置 · 用户编辑', group: '5 设置', render() {
      var ed = window.__userEdit || { mode: 'add', name: '', role: 'user' };
      return setPage('set-user',
        userTable() + setUserBtns()
        + P.mask(P.modal(ed.mode === 'add' ? T('userAdd', '用户新增') : T('userEdit', '用户编辑'),
          '<div style="width:620px">'
          + frow(T('nameLabel', '用户名'), '<div class="inp flex"><input class="icu-input" data-uname maxlength="20" value="'
            + ed.name + '"' + (ed.mode === 'edit' ? ' readonly style="background:#eef3fb;color:#8b95a6"' : '') + '></div>')
          + frow(T('pwdLabel', '密 码'), '<div class="inp flex"><input class="icu-input" data-upwd type="password" maxlength="32" placeholder="'
            + (ed.mode === 'edit' ? T('pwdKeepBlank', '不修改请留空') : '') + '"></div>')
          + frow(T('roleLabel', '权 限'), '<div class="inp flex">' + userRoleSelect(ed.role) + '</div>')
          + '</div>',
          '<div class="btn" data-ufoot="confirm">' + T('userConfirmBtn', '确认') + '</div>'
          + '<div class="btn primary" data-ufoot="cancel">' + T('userCancelBtn', '取消') + '</div>')));
    }
  };

  /* p25 / p26 / p27 升级 */
  function upTabs(active) {
    const t = [['upgrade-sw', 'upSw', '软件'], ['upgrade-cb', 'upCb', '控制板'], ['upgrade-online', 'upOnline', '在线升级']];
    return '<div class="seg" style="margin:0 auto 60px;width:900px"><div style="display:flex;width:100%">'
      + t.map(function (x) { return '<button style="flex:1" class="' + (x[0] === active ? 'on' : '') + '" data-go="' + x[0] + '">' + T(x[1], x[2]) + '</button>'; }).join('')
      + '</div></div>';
  }
  const QR = '<div class="qr" style="margin:30px auto 0"><div class="ban">' + T('qrBan', '传输助手仅用于客户便捷查询，非商用用途。') + '</div><div class="cap">' + T('qrCap', '使用文件传输助手，手机电脑轻松互传文件') + '</div><div class="code"></div><div style="font-size:15px;color:#8b95a6">' + T('qrNote', '微信文件传输助手网页版') + '</div></div>';
  SCREENS['upgrade-sw'] = {
    name: '升级 · 软件', group: '5 设置', render() {
      return setPage('upgrade-sw', upTabs('upgrade-sw')
        + '<div style="width:1000px;margin:0 auto;display:flex;gap:20px;align-items:center"><div class="prog"></div><div class="btn sm">' + T('chooseFile', '选择文件') + '</div><div class="btn sm">' + T('upgrade', '升级') + '</div></div>'
        + QR + A(700, 520, '增加微信"传输助手"互传二维码。', 'plain'));
    }
  };
  SCREENS['upgrade-cb'] = {
    name: '升级 · 控制板', group: '5 设置', render() {
      let lines = '';
      for (let i = 0; i < 7; i++) lines += '<br>';
      return setPage('upgrade-cb', upTabs('upgrade-cb')
        + '<div style="width:1000px;margin:0 auto">'
        + frow(T('upModule', '模块'), '<div class="inp flex">' + I.chev + '</div>')
        + '<div style="display:flex;gap:20px;align-items:center;margin-bottom:20px"><div class="prog"></div><div class="btn sm">' + T('chooseFile', '选择文件') + '</div></div>'
        + '<div style="display:flex;gap:24px"><div class="inp" style="flex:1;height:280px;display:block;padding:20px;font-size:20px;line-height:1.6">' + lines + '</div>'
        + '<div class="qr" style="width:220px;height:280px"><div class="ban">' + T('qrBanShort', '传输助手仅用于客户便捷查询') + '</div><div class="code" style="width:150px;height:150px"></div></div></div>'
        + '<div style="margin-top:24px"><div class="prog" style="height:44px;position:relative"><i style="width:48%"></i><span style="position:absolute;right:20px;top:8px;color:#1c2430">48%</span></div></div>'
        + '<div style="text-align:center;margin-top:24px"><div class="btn">' + T('upgrade', '升级') + '</div></div>'
        + '</div>' + A(700, 500, '增加微信"传输助手"互传二维码。', 'plain'));
    }
  };
  SCREENS['upgrade-online'] = {
    name: '在线升级', group: '5 设置', render() {
      return setPage('upgrade-online', upTabs('upgrade-online')
        + '<div style="width:1000px;margin:0 auto">'
        + '<div style="display:flex;gap:20px;align-items:center;margin-bottom:20px"><div class="prog" style="background:#eef2f9"></div><div class="btn sm">' + T('upLatest', '升级最新') + '</div></div>'
        + '<div class="inp" style="height:400px;display:block;padding:20px;font-size:20px;line-height:1.7;white-space:pre-wrap">' + D.upLog + '</div>'
        + '<div style="margin-top:24px"><div class="prog" style="height:44px;position:relative"><i style="width:48%"></i><span style="position:absolute;right:20px;top:8px;color:#1c2430">48%</span></div></div>'
        + '</div>');
    }
  };

  /* p28 补偿（★ V1.02·任务8 对齐 27调试.png：3×2 栅格、5 张补偿卡 + 1 张 + 添加卡）
     - 标题居中上；下排 [−灰圆钮][白底数值][+蓝圆钮]；+/− 本地状态 ±1（不落原生）
     - 默认值 0（中性默认值，非虚构数据），由 icu-native-bridge 的 [data-comp] 点击处理 */
  const COMP_TITLES = [T('compTemp', '温度补偿（℃）'), T('compO2', '氧浓度补偿（%）'), T('compHum', '湿度补偿（%）'), T('compCo2', '二氧化碳浓度补偿（PPM）'), T('compIr', '红外体温补偿（℃）')];
  SCREENS['comp'] = {
    name: '补偿（校准）', group: '5 设置', render() {
      if (!D.compVals || D.compVals.length < COMP_TITLES.length) {
        D.compVals = [0, 0, 0, 0, 0];
      }
      const cards = COMP_TITLES.map(function (t, i) {
        const v = D.compVals[i];
        return '<div class="compcard" data-comp-idx="' + i + '"><div class="ct">' + t + '</div><div class="crow">'
          + '<span class="step" data-comp-step="m" data-comp-i="' + i + '">−</span>'
          + '<span class="cval" data-comp-val="' + i + '">' + v + '</span>'
          + '<span class="step plus" data-comp-step="p" data-comp-i="' + i + '">＋</span></div></div>';
      }).join('')
        /* ★ 任务16：第 6 项 = 恢复出厂设置（纯按钮，无卡片框/无字段） */
        + '<div class="compfactory"><span class="btn" data-factory="1">' + T('factoryReset', '恢复出厂设置') + '</span></div>';
      var page = '<div class="comp-grid">' + cards + '</div>';
      /* ★ 任务15(#12)：恢复出厂设置二次确认（R51） */
      if (window.__factoryConfirm) {
        page += P.mask(P.modal(T('factoryReset', '恢复出厂设置'),
          '<div class="alert-row">' + I.warn + '<span>' + T('factoryConfirmBody', '确认恢复出厂设置？所有自定义设置将恢复默认。') + '</span></div>',
          '<div class="btn" data-factory-confirm="yes">' + T('yes', '是') + '</div>'
          + '<div class="btn primary" data-factory-confirm="no">' + T('cancel', '取消') + '</div>'));
      }
      return PAGE(P.topbarBack('set-general'), page, null, true);
    }
  };

  /* p29 关于 */
  SCREENS['about'] = {
    name: '关于', group: '5 设置', render() {
      const base = D.aboutBase.map(function (x) { return '<div class="ln"><span style="flex:1">' + x + '</span></div>'; }).join('');
      const ver = D.aboutVer.map(function (v) {
        return '<div class="ln"><span class="k">' + (v[0] || '') + '</span><span>' + v[1] + '</span></div>';
      }).join('');
      return PAGE(P.topbarBack('menu'),
        '<div class="about-grid">'
        + '<div class="aboutcard"><h3>' + T('aboutBasic', '基础信息') + '</h3>' + base + '</div>'
        + '<div class="aboutcard"><h3>' + T('aboutVer', '版本信息') + '</h3>' + ver + '</div></div>', null, true);
    }
  };

  /* p30 日志（★ V1.02·任务8 对齐 29日志.png）
     - 左侧 7 分类：横排图标左 + 文字右（每个 sideitem 用 row 排列，图标在左、文字在右）
     - 右侧内容区：浅蓝圆角大框（沿用 .logbox）
     - 底栏：日期框 "2026/08/06" + 日历图标 ｜ ‹上一页(灰) ｜ 下一页›(蓝 primary) ｜ 更新（间距按设计）
     - 内容保持为空（真实 userOpLog 接入是下轮，不造数据） */
  const LOG_CATS = [
    { k: '前端调试', key: 'logFront', ico: 'tune' },
    { k: '后端调试', key: 'logBack', ico: 'doc' },
    { k: '错误记录', key: 'logErr', ico: 'pencil' },
    { k: '通信记录', key: 'logComm', ico: 'wifi' },
    { k: '堆栈信息', key: 'logStack', ico: 'hdd' },
    { k: '常规信息', key: 'logNormal', ico: 'info' },
    { k: '用户日志', key: 'logUser', ico: 'user' }
  ];
  const LOG_PAGE_SIZE = 20;
  function esc(s) {
    return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  /* ★ 2026-10-08 通用件：7 类日志共用的列定义 + 行渲染
     cols: [字段key, i18n key, 中文回退]；rows() 取 D 上对应数组（均为最新在前） */
  const LOG_DEFS = {
    '前端调试': {
      cols: [['time', 'logColTime', '时间'], ['level', 'logColLevel', '级别'], ['source', 'logColSource', '来源'], ['msg', 'logColDetail', '详情']],
      rows: function () { return D.frontLogs; }
    },
    '后端调试': {
      cols: [['time', 'logColTime', '时间'], ['level', 'logColLevel', '级别'], ['source', 'logColSource', '来源'], ['msg', 'logColDetail', '详情']],
      rows: function () { return D.debugLogs; }
    },
    '错误记录': {
      cols: [['time', 'logColTime', '时间'], ['source', 'logColSource', '来源'], ['msg', 'logColDetail', '详情']],
      rows: function () { return D.errorLogs; }
    },
    '通信记录': {
      cols: [['time', 'logColTime', '时间'], ['dir', 'logColDir', '方向'], ['type', 'logColType', '类型'], ['msg', 'logColDetail', '详情']],
      rows: function () { return D.commLogs; }
    },
    '堆栈信息': {
      cols: [['time', 'logColTime', '时间'], ['msg', 'logColStack', '堆栈']],
      rows: function () { return D.stackLogs; },
      preMsg: true
    },
    '常规信息': {
      cols: [['time', 'logColTime', '时间'], ['source', 'logColSource', '来源'], ['msg', 'logColDetail', '详情']],
      rows: function () { return D.generalLogs; }
    },
    '用户日志': {
      cols: [['time', 'logColTime', '时间'], ['account', 'logColAccount', '账号'], ['zone', 'logColZone', '舱区'], ['module', 'logColModule', '模块'], ['action', 'logColAction', '操作'], ['detail', 'logColDetail', '详情'], ['result', 'logColResult', '结果']],
      rows: function () { return D.userLogs; },
      /* 用户日志行特化：舱区 left/right 显示侧翻译；结果非「成功」标红 */
      cell: function (r, k) {
        if (k === 'zone') {
          return r.zone === 'left' ? T('zoneLeft', '左舱') : r.zone === 'right' ? T('zoneRight', '右舱') : esc(r.zone);
        }
        if (k === 'result') {
          var ok = !r.result || r.result === '成功';
          return ok ? esc(r.result || '成功') : '<span class="ng">' + esc(r.result) + '</span>';
        }
        return esc(r[k]);
      }
    }
  };
  /* 通用表体：D.logDate（YYYY-MM-DD，空=全部）过滤 + D.logPage 分页（20/页），适用全部 7 类 */
  function logBody(def) {
    const all = Array.isArray(def.rows()) ? def.rows() : [];
    const dateF = D.logDate || '';
    const rows = dateF ? all.filter(function (r) { return r && String(r.time || '').indexOf(dateF) === 0; }) : all;
    const total = Math.max(1, Math.ceil(rows.length / LOG_PAGE_SIZE));
    let page = D.logPage || 1;
    if (page > total) page = total;
    if (page < 1) page = 1;
    D.logPage = page;
    const head = '<tr>' + def.cols.map(function (c) { return '<th>' + T(c[1], c[2]) + '</th>'; }).join('') + '</tr>';
    let body = '';
    if (!rows.length) {
      body = '<tr><td colspan="' + def.cols.length + '" class="logempty">' + T('logEmpty', '暂无记录') + '</td></tr>';
    } else {
      body = rows.slice((page - 1) * LOG_PAGE_SIZE, page * LOG_PAGE_SIZE).map(function (r) {
        return '<tr>' + def.cols.map(function (c) {
          const k = c[0];
          if (def.preMsg && k === 'msg') {
            return '<td class="logpre"><pre>' + esc(r.msg) + '</pre></td>';
          }
          return '<td>' + (def.cell ? def.cell(r, k) : esc(r[k])) + '</td>';
        }).join('') + '</tr>';
      }).join('');
    }
    return {
      html: '<table class="tbl logtbl"><thead>' + head + '</thead><tbody>' + body + '</tbody></table>',
      page: page, total: total
    };
  }
  SCREENS['log'] = {
    name: '日志', group: '5 设置', render() {
      const cat = D.logCat || '用户日志';
      const def = LOG_DEFS[cat] || LOG_DEFS['用户日志'];
      const side = '<div class="side logside">' + LOG_CATS.map(function (c) {
        return '<div class="sideitem rowitem' + (c.k === cat ? ' on' : '') + '" data-log="' + c.k + '">'
          + (I[c.ico] || I.doc) + '<span>' + T(c.key, c.k) + '</span></div>';
      }).join('') + '</div>';
      /* 7 类日志统一：表格 + 日期过滤 + 翻页 + 更新（无数据时显示「暂无记录」，不造数据） */
      const b = logBody(def);
      const footMid = '<div class="btn' + (b.page <= 1 ? ' dim' : '') + '" data-logpage="-1">' + T('prevPage', '‹ 上一页') + '</div>'
        + '<div class="logpage">' + T('logPageInfo', '第 {n}/{m} 页').replace('{n}', b.page).replace('{m}', b.total) + '</div>'
        + '<div class="btn primary' + (b.page >= b.total ? ' dim' : '') + '" data-logpage="1">' + T('nextPage', '下一页 ›') + '</div>';
      return PAGE(P.topbarBack('menu'),
        '<div class="set-wrap">' + side + '<div class="logwrap">'
        + '<div class="logbox tblmode">' + b.html + '</div>'
        + '<div class="logfoot">'
        + '<input type="date" class="logdate" data-logdate value="' + (D.logDate || '') + '">'
        + '<div class="btn" data-logdate-clear>' + T('logAllDates', '全部日期') + '</div>'
        + '<div class="grow"></div>'
        + footMid
        + '<div class="grow"></div>'
        + '<div class="btn" data-logrefresh>' + T('update', '更新') + '</div>'
        + '</div>'
        + '</div></div>'
        + A(20, 380, T('logCur', '日志 · 当前：') + T((LOG_CATS.find(function (x) { return x.k === cat; }) || LOG_CATS[6]).key, cat)), null, true);
    }
  };

  /* ======================================================================
     p31 注销确认 / p32 登录弹窗 / p33 退出确认 / p34 退出中
     ====================================================================== */
  function menuBg() {
    const tiles = [['', 'menuSet', '设置', 'settings', 0], ['', 'menuDebug', '调试', 'tune', 1], ['', 'menuClose', '关闭', 'x', 1],
    ['', 'menuAbout', '关于', 'info', 0], ['', 'menuLog', '日志', 'log', 0], ['', 'menuLogout', '注销', 'logout', 0]];
    return '<div class="menu-grid">' + tiles.map(function (x) {
      return '<div class="tile' + (x[4] ? ' dis' : '') + '"><div class="ti">' + (I[x[3]] || I.doc) + '</div><span>' + T(x[1], x[2]) + '</span></div>';
    }).join('') + '</div>';
  }
  SCREENS['logout-confirm'] = {
    name: '注销确认', group: '6 弹窗', render() {
      return PAGE(P.topbar('menu'), menuBg()
        + P.mask(P.modal(T('tipTitle', '提示'), '<div class="alert-row">' + I.warn + '<span>' + T('logoutBody', '是否注销当前账户?') + '</span></div>',
          '<div class="btn" data-go="login">' + T('yes', '是') + '</div><div class="btn primary" data-go="menu">' + T('no', '否') + '</div>'))
        + A(560, 640, '注销后回到初始登录界面', 'plain'), null, true);
    }
  };
  SCREENS['login-modal'] = {
    name: '登录弹窗', group: '6 弹窗', render() {
      return PAGE(P.topbar('menu'), menuBg()
        + P.mask('<div class="login-card"><h1>' + T('loginTitle', '用户登录') + '</h1>'
          + '<div class="lf">' + I.user + '<input value=""></div>'
          + '<div class="lf"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="#8b95a6" stroke-width="1.8"><rect x="5" y="10.5" width="14" height="9" rx="2"/><path d="M8.4 10.5V8a3.6 3.6 0 0 1 7.2 0v2.5"/></svg><input type="password"></div>'
          + '<button class="lb" data-go="care">' + T('loginBtn', '登 录') + '</button></div>'), null, true);
    }
  };
  SCREENS['exit-confirm'] = {
    name: '退出软件确认', group: '6 弹窗', render() {
      /* ★ V1.02·任务8 对齐 32关机.png：去掉红字划线「确认是否关机？」，只保留 ⚠ + "确认是否退出软件？" */
      return PAGE(P.topbar('menu'), menuBg()
        + P.mask(P.modal(T('tipTitle', '提示'),
          '<div class="alert-row">' + I.warn + '<span>' + T('exitBody', '确认是否退出软件？') + '</span></div>',
          '<div class="btn" data-go="exiting">' + T('yes', '是') + '</div><div class="btn primary" data-go="menu">' + T('no', '否') + '</div>')), null, true);
    }
  };
  SCREENS['exiting'] = {
    name: '退出中', group: '6 弹窗', render() {
      /* ★ V1.02·任务8 对齐 33关机缓存.png：去掉红字划线「缓存完成后自动关机」，只留 spinner + "缓存完成后自动退出软件" */
      return PAGE(P.topbar('menu'), menuBg()
        + P.mask('<div class="modal" style="min-width:620px"><div class="mbody" style="padding:80px 90px">'
          + P.spinnerBlock(T('exitingMsg', '缓存完成后自动退出软件'))
          + '</div></div>'), null, true);
    }
  };

  /* ======================================================================
     改版标注总览
     ====================================================================== */
  SCREENS['changes'] = {
    name: '改版标注总览', group: '7 附', render() {
      const items = D.changes.map(function (c) {
        return '<div class="item"><div class="t">' + c[0] + '</div><div class="d">' + c[1] + '</div></div>';
      }).join('');
      return PAGE(P.topbarBack('menu'),
        '<div class="changes"><h2 style="font-size:34px;font-weight:400;margin-bottom:6px">' + T('changesTitle', '本次改版标注汇总') + '</h2>'
        + '<div style="color:#8b95a6;font-size:22px;margin-bottom:26px">' + T('changesSub', '对应设计稿中的红字 / 划线标注') + '</div>' + items + '</div>', null, true);
    }
  };

  window.SCREENS = SCREENS;
})();
