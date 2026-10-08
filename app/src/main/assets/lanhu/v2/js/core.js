/* ==========================================================================
   core.js · 图标库 + 模拟数据 + 公共构件
   ========================================================================== */
(function () {
  'use strict';

  /* ---------- 图标 ------------------------------------------------------- */
  const s = (p) => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + p + '</svg>';
  const f = (p, vb) => '<svg viewBox="' + (vb || '0 0 24 24') + '" fill="currentColor">' + p + '</svg>';
  /* 描边 + 填充 合成为一个 svg（避免两个 svg 被 flex 并排而分开） */
  const sf = (strokeP, fillP) => '<svg viewBox="0 0 24 24"><g fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + strokeP + '</g><g fill="currentColor" stroke="none">' + fillP + '</g></svg>';

  const I = {
    paw: f('<path d="M7.5 6.3c1.2 0 2.1 1.3 2.1 2.9S8.7 12 7.5 12 5.4 10.7 5.4 9.2 6.3 6.3 7.5 6.3Zm9 0c1.2 0 2.1 1.3 2.1 2.9S17.7 12 16.5 12s-2.1-1.3-2.1-2.8S15.3 6.3 16.5 6.3ZM3.4 11.1c1.1 0 2 1.1 2 2.5s-.9 2.5-2 2.5-2-1.1-2-2.5.9-2.5 2-2.5Zm17.2 0c1.1 0 2 1.1 2 2.5s-.9 2.5-2 2.5-2-1.1-2-2.5.9-2.5 2-2.5ZM12 12.4c2.6 0 5.4 2.2 5.4 4.9 0 1.7-1.3 2.9-3 2.9-1 0-1.6-.3-2.4-.3s-1.4.3-2.4.3c-1.7 0-3-1.2-3-2.9 0-2.7 2.8-4.9 5.4-4.9Z"/>'),
    care: s('<path d="M12 20s-7-4.4-7-9.4A3.9 3.9 0 0 1 12 8a3.9 3.9 0 0 1 7 2.6c0 5-7 9.4-7 9.4Z"/><path d="M12 9.6v4.2M9.9 11.7h4.2"/>'),
    history: s('<path d="M3.5 12a8.5 8.5 0 1 0 2.6-6.1"/><path d="M3.5 4.4V10h5.6"/><path d="M12 8v4.4l3 1.8"/>'),
    menu: s('<path d="M4 7h16M4 12h16M4 17h16"/>'),
    user: s('<circle cx="12" cy="8.2" r="3.6"/><path d="M4.8 20c.6-3.6 3.7-5.6 7.2-5.6S18.6 16.4 19.2 20"/>'),
    back: s('<path d="M15 5l-7 7 7 7"/>'),
    plus: s('<path d="M12 5v14M5 12h14"/>'),
    minus: s('<path d="M5 12h14"/>'),
    chev: s('<path d="M6 9l6 6 6-6"/>'),
    check: s('<path d="M20 6.5 9.4 17 4 11.7"/>'),
    x: s('<path d="M6 6l12 12M18 6 6 18"/>'),
    warn: s('<circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.2v.1"/>'),
    dot: s('<circle cx="12" cy="12" r="9"/><path d="M7.5 12.2l3 3 6-6.4"/>'),
    cart: s('<path d="M5 6h14l-1.2 9H6.2L5 6Z"/><path d="M9 20h.01M16 20h.01"/>'),
    doc: s('<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4M9 12h6M9 16h6"/>'),
    play: s('<circle cx="12" cy="12" r="9"/><path d="M10 8.5l6 3.5-6 3.5z"/>'),
    pencil: s('<path d="M4 20h4L20 8l-4-4L4 16v4Z"/><path d="M14.5 5.5 18.5 9.5"/>'),
    trash: s('<path d="M4 7h16M9 7V4.6h6V7M6 7l1 13h10l1-13"/><path d="M10 11v6M14 11v6"/>'),
    upload: s('<path d="M4 16v3h16v-3"/><path d="M12 4v11M8 8l4-4 4 4"/>'),
    reboot: s('<path d="M4 12a8 8 0 1 0 2.4-5.7"/><path d="M4 4v5.4h5.4"/>'),
    search: s('<circle cx="11" cy="11" r="6.4"/><path d="M20 20l-4.2-4.2"/>'),
    print: s('<path d="M7 9V3.8h10V9"/><rect x="4" y="9" width="16" height="7.4" rx="1.4"/><path d="M7 14h10v6.2H7z"/>'),
    export: s('<path d="M14 4h6v6"/><path d="M20 4l-8.5 8.5"/><path d="M19 14v6H4V5h6"/>'),
    settings: s('<circle cx="12" cy="12" r="3.2"/><path d="M12 3.5l1.3 2.2 2.5-.5.6 2.5 2.2 1.3-1.3 2.2 1.3 2.2-2.2 1.3-.6 2.5-2.5-.5L12 20.5l-1.3-2.2-2.5.5-.6-2.5L5.4 15l1.3-2.2L5.4 10.5l2.2-1.3.6-2.5 2.5.5z"/>'),
    tune: s('<path d="M5 8h14M5 16h14"/><circle cx="10" cy="8" r="2.2"/><circle cx="15" cy="16" r="2.2"/>'),
    globe: s('<circle cx="12" cy="12" r="8.6"/><path d="M3.4 12h17.2M12 3.4c2.6 2.4 2.6 14.8 0 17.2M12 3.4c-2.6 2.4-2.6 14.8 0 17.2"/>'),
    swap: s('<path d="M4 8h13l-3-3M20 16H7l3 3"/>'),
    gauge: s('<path d="M4 18a8.5 8.5 0 1 1 16 0"/><path d="M12 18l3.6-5.4"/>'),
    gearup: s('<path d="M12 4v10M8 8l4-4 4 4"/><path d="M5 16v4h14v-4"/>'),
    info: s('<circle cx="12" cy="12" r="9"/><path d="M12 11v5.4M12 7.8v.1"/>'),
    logout: s('<path d="M14 4H5v16h9"/><path d="M17 8l4 4-4 4M9 12h12"/>'),
    thermo: s('<path d="M13.5 14V4.6a1.8 1.8 0 0 0-3.6 0V14a3.4 3.4 0 1 0 3.6 0Z"/><path d="M11.7 8h2"/>'),
    o2: f('<text x="12" y="16.6" font-size="12.5" font-family="Arial,Helvetica,sans-serif" text-anchor="middle">O</text><text x="19.4" y="20.4" font-size="8" font-family="Arial,Helvetica,sans-serif" text-anchor="middle">2</text>'),
    drop: sf('<path d="M10.9 9.4s4.3 3.9 4.3 6.5a4.3 4.3 0 0 1-8.6 0c0-2.6 4.3-6.5 4.3-6.5Z"/>',
      '<path d="M16.7 3.9s2.4 2.2 2.4 3.6a2.4 2.4 0 0 1-4.8 0c0-1.4 2.4-3.6 2.4-3.6Z"/>'
      + '<path d="M7.2 6.3s1.9 1.7 1.9 2.8a1.9 1.9 0 0 1-3.8 0c0-1.1 1.9-2.8 1.9-2.8Z"/>'),
    clock: s('<circle cx="12" cy="12" r="8.6"/><path d="M12 7.4V12l3.2 1.9"/>'),
    cal: s('<rect x="4" y="5.4" width="16" height="14.2" rx="2"/><path d="M4 10.2h16M8.4 3.4v4M15.6 3.4v4"/>'),
    wave: s('<path d="M3 12h3l1.6-5 2.4 10 2.4-9 1.8 6 1.6-2H21"/>'),
    light: s('<circle cx="12" cy="10" r="3.4"/><path d="M12 3v1.6M12 15.4V17M4.8 10H6.4M17.6 10h1.6M6.9 4.9 8 6M16 6l1.1-1.1M6.9 15.1 8 14M16 14l1.1 1.1M9.4 20h5.2"/>'),
    mist: s('<path d="M4 10h11M6 13.5h10M8 17h8"/><circle cx="18" cy="7.4" r="1.6"/>'),
    shield: s('<path d="M12 3.5 19 6v5.2c0 4.2-2.9 7.6-7 9.3-4.1-1.7-7-5.1-7-9.3V6z"/><path d="M12 10v4.2M9.9 12.1h4.2"/>'),
    uv: s('<circle cx="12" cy="12" r="4"/><path d="M12 3v2.4M12 18.6V21M3 12h2.4M18.6 12H21M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M18.4 5.6l-1.7 1.7M7.3 16.7l-1.7 1.7"/>'),
    cold: s('<circle cx="12" cy="12" r="4"/><path d="M12 2.4v3.2M12 18.4v3.2M2.4 12h3.2M18.4 12h3.2M5.2 5.2l2.3 2.3M16.5 16.5l2.3 2.3M18.8 5.2l-2.3 2.3M7.5 16.5l-2.3 2.3"/>'),
    loop: s('<path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20 4.6V9h-4.4M4 12a8 8 0 1 1 2.6 5.9"/><path d="M4 19.4V15h4.4"/>'),
    loopIn: s('<path d="M12 4a8 8 0 1 1-5.9 2.6"/><path d="M4.6 4v4.4H9M12 20a8 8 0 1 1 5.9-2.6"/><path d="M19.4 20v-4.4H15"/>'),
    cat: f('<path d="M5 9 4 4l4.6 2h6.8L20 4l-1 5v7a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3z"/>'),
    /* ★ V1.02 新建样本弹窗右侧图形（设计稿 4新建弹窗.png）：单色剪影
       全部来自 桌面/{犬,猫,兔子,蜥蜴,蛇}.png 位图描摹（potrace）：
         缩到宽 ~200 → 亮度阈值（<150 为墨迹）→ 掩膜取反 → potracer 单条 path
       viewBox 用各图缩放后的实际尺寸，fill-rule 不需要（剪影实心）。 */
    /* 坐姿犬（dogS）· 97x94 */
    dogS: f('<path d="M20.8 93.3C17.5 92.5 19.6 87.5 23.5 86.8C26.2 86.3 27.1 80.8 26.3 69.0C25.9 61.7 25.2 58.7 23.5 56.0C19.4 49.5 18.7 45.8 20.1 37.6C21.0 32.6 21.0 29.1 20.4 26.9C19.4 23.5 19.4 23.5 12.4 23.0C4.7 22.5 2.7 21.4 1.1 17.0C0.2 14.2 0.3 14.0 5.6 11.6C9.1 10.0 11.3 8.3 12.0 6.6C13.6 2.2 21.9 -0.6 28.1 1.0C32.7 2.3 37.0 5.6 37.0 8.0C37.0 10.1 29.6 18.0 27.5 18.0C25.2 18.0 23.8 14.2 24.4 9.6C24.7 7.4 24.6 4.8 24.0 4.0C23.3 2.9 23.0 4.6 23.0 9.6C23.0 20.5 26.6 22.3 33.7 15.0L36.9 11.8L40.4 18.8C42.9 23.8 47.1 29.2 54.7 37.3C70.6 54.2 77.0 66.3 77.0 79.5C77.0 85.6 77.1 86.0 79.5 86.6C83.2 87.5 90.5 84.4 93.4 80.7C96.4 76.9 97.6 77.6 95.7 82.1C92.6 89.3 85.6 93.2 75.8 93.0C72.9 93.0 65.4 93.2 59.2 93.6C45.8 94.3 37.0 93.5 37.0 91.4C37.0 90.6 37.9 89.1 39.1 87.9C40.8 86.2 41.9 86.0 46.8 86.5C52.5 87.1 52.5 87.1 50.0 84.9C43.6 79.4 42.1 72.3 46.4 67.3C48.6 64.8 49.4 64.5 54.7 64.6C58.5 64.7 59.8 64.5 58.5 63.9C54.0 62.2 48.9 62.9 46.1 65.8C44.6 67.3 43.1 69.2 42.8 70.0C42.4 71.2 41.8 71.0 39.6 69.1C37.3 66.9 37.0 66.1 37.5 61.6C38.3 52.5 36.2 62.3 35.0 73.0C33.9 82.2 30.9 91.7 28.6 93.2C27.4 94.0 23.6 94.1 20.8 93.3Z"/>', '0 0 97 94'),
    /* 坐姿猫（catS）· 106x99 —— 不动现有 I.cat（主机母幼模式卡还在用它） */
    catS: f('<path d="M18.9 97.4C17.3 95.5 18.6 93.0 21.9 91.5C24.1 90.5 24.1 90.1 23.8 80.5C23.5 71.4 23.2 70.1 20.5 65.7C12.1 52.2 12.5 53.2 12.8 43.8C13.0 34.3 12.3 33.0 6.6 33.0C3.9 33.0 0.0 28.9 0.0 26.0C0.0 25.0 0.7 23.7 1.5 23.0C2.3 22.3 3.0 20.6 3.0 19.3C3.0 17.8 4.2 15.7 5.9 14.1C8.5 11.7 8.8 10.8 8.3 7.2C7.7 2.5 8.3 2.1 12.0 5.0C15.4 7.6 15.7 7.5 17.0 3.4C17.7 1.5 18.7 0.1 19.3 0.3C20.0 0.5 22.0 2.6 23.7 5.1C25.5 7.5 28.3 11.4 30.0 13.6C31.7 15.9 33.8 19.9 34.7 22.6C35.6 25.3 37.0 28.6 37.9 29.9C39.7 32.6 44.7 35.0 48.8 35.0C56.9 35.0 67.5 42.0 72.6 50.6C78.1 60.0 81.1 76.4 79.2 86.2C78.1 91.8 78.9 92.1 85.6 89.1C95.7 84.5 98.8 72.2 92.6 61.6C91.3 59.3 86.8 53.7 82.6 49.0C67.9 32.6 65.3 20.5 74.3 10.3C78.1 6.0 84.1 3.1 89.0 3.0C97.3 3.0 105.9 10.9 106.0 18.5C106.0 27.5 94.6 35.8 88.9 31.1C85.9 28.6 87.1 26.0 92.0 24.4C95.4 23.3 98.0 20.6 98.0 18.2C98.0 17.5 96.6 15.5 95.0 13.8C92.3 11.1 91.4 10.8 87.9 11.3C78.4 12.6 74.5 22.3 79.5 32.1C80.3 33.6 84.8 39.3 89.5 44.7C94.2 50.1 99.2 56.8 100.6 59.6C107.3 73.3 103.0 88.2 90.2 95.8C85.5 98.5 85.5 98.5 61.0 98.5L36.5 98.5L36.5 95.5C36.5 93.0 37.0 92.3 39.7 91.5C42.6 90.5 42.7 90.3 41.4 88.5C35.3 80.2 39.8 67.3 50.2 63.3L53.5 62.0L50.2 62.5C43.2 63.5 37.0 71.0 37.0 78.4C37.0 83.2 33.3 94.7 30.9 97.1C28.5 99.5 20.9 99.7 18.9 97.4Z"/>', '0 0 106 99'),
    /* 坐姿兔·长耳（rabS）· 95x106 */
    rabS: f('<path d="M22.0 104.2C22.0 101.8 23.1 100.4 25.6 99.6C28.1 98.8 30.0 95.0 29.9 91.2C29.8 89.1 29.6 89.5 28.8 92.7C28.1 95.9 27.1 97.3 24.5 98.9C22.3 100.2 21.0 101.7 21.0 103.0C21.0 104.4 20.3 105.0 18.9 105.0C17.3 105.0 16.9 104.5 17.2 102.2C17.4 100.3 18.4 99.1 20.7 98.1C23.4 96.9 24.0 96.0 24.6 92.3C25.2 88.1 25.0 87.6 21.2 83.7C15.8 78.1 14.7 75.6 14.1 68.0L13.5 61.5L8.6 60.9C3.1 60.3 -0.0 57.5 0.0 53.1C0.0 47.3 8.9 35.8 14.4 34.4C16.5 33.9 16.8 33.1 17.3 25.8C18.0 16.6 21.3 8.4 26.3 3.2C29.9 -0.5 31.5 -0.8 33.0 1.9C34.7 5.2 34.1 10.5 31.3 15.6C27.2 23.2 25.3 27.3 24.6 30.0C24.2 31.4 25.5 29.2 27.5 25.3C33.7 12.7 46.7 0.8 50.7 4.1C54.9 7.7 48.5 23.4 39.0 32.4C33.3 37.9 33.1 38.3 34.5 40.3C35.3 41.5 36.0 43.5 36.0 44.7C36.0 48.2 40.2 49.5 46.0 47.8C54.0 45.5 63.1 46.3 70.4 49.9C81.3 55.3 87.6 64.9 88.7 77.9C89.2 83.5 89.6 84.9 91.1 85.3C93.4 85.9 95.3 90.5 94.4 93.4C93.2 97.4 88.9 101.0 85.2 101.0C82.9 101.0 81.2 101.7 80.0 103.2C78.2 105.4 77.5 105.5 59.5 105.8C49.0 106.0 40.3 105.7 39.8 105.2C38.4 103.8 40.8 99.4 43.7 98.1C45.0 97.5 47.8 97.0 49.9 97.0L53.7 97.0L51.4 92.3C48.5 86.7 48.4 83.8 50.9 78.4C52.9 74.0 56.9 70.6 61.4 69.4C62.9 69.1 63.9 68.5 63.6 68.3C62.4 67.1 55.8 70.5 52.6 74.0C47.9 79.2 46.8 85.9 49.5 92.4C50.2 94.1 50.0 94.2 47.4 93.5C42.2 92.2 41.7 92.4 38.2 98.5C36.2 101.8 34.2 104.8 33.6 105.2C33.0 105.6 30.1 106.0 27.2 106.0C22.9 106.0 22.0 105.7 22.0 104.2Z"/>', '0 0 95 106'),
    /* 蜥蜴·长尾（lizS）· 148x60 */
    lizS: f('<path d="M37.0 58.6C37.0 56.9 36.1 56.9 31.2 58.7C29.4 59.4 29.7 57.9 31.8 56.4C33.4 55.2 33.3 55.1 31.4 55.6C30.2 55.9 28.9 55.7 28.6 55.1C28.2 54.5 28.6 54.0 29.5 54.0C30.3 54.0 31.3 53.3 31.7 52.4C32.0 51.5 33.2 51.0 34.8 51.2C37.0 51.4 37.8 50.8 39.7 47.5C40.9 45.3 42.0 43.3 42.0 42.9C42.0 42.6 40.1 41.5 37.8 40.4L33.7 38.4L30.2 42.2C28.3 44.3 26.0 46.0 25.1 46.0C24.2 46.0 22.4 46.7 21.2 47.6C19.2 49.0 18.5 48.8 18.9 46.6C19.0 46.2 17.9 46.1 16.5 46.4C15.1 46.8 14.0 46.6 14.0 46.0C14.0 45.5 14.5 45.0 15.1 45.0C15.7 45.0 16.0 44.3 15.6 43.5C15.2 42.5 15.7 42.0 17.0 42.0C18.1 42.0 19.0 41.6 19.0 41.0C19.0 40.4 19.9 40.4 21.3 40.9C23.3 41.7 24.1 41.3 26.3 38.5C28.9 35.2 28.9 35.2 26.6 33.1C25.4 32.0 23.6 31.0 22.7 31.0C21.8 31.0 18.1 28.7 14.5 26.0C10.9 23.2 6.6 20.3 5.0 19.5C-0.7 16.5 -1.0 12.3 4.5 9.9C5.6 9.3 7.6 8.3 8.9 7.6C10.5 6.6 12.1 6.5 13.9 7.0C15.3 7.5 19.3 8.1 22.7 8.4C26.1 8.6 29.5 9.3 30.2 9.9C36.3 15.1 39.6 16.3 49.5 17.1C68.1 18.6 73.4 19.9 90.6 27.5C105.6 34.1 112.6 36.0 122.7 36.0C129.1 36.0 131.6 35.5 135.0 33.7C142.5 29.7 145.1 21.0 141.1 13.2C138.6 8.2 135.6 5.6 129.0 2.6C124.9 0.6 124.1 0.0 126.0 0.0C130.3 0.0 138.4 4.5 142.3 9.0C152.5 21.0 147.3 37.9 131.9 42.5C125.6 44.3 112.2 44.5 100.0 42.8C95.3 42.2 90.1 42.0 88.5 42.4L85.7 43.1L88.7 45.0C90.4 46.0 92.4 47.7 93.2 48.9C94.0 50.0 95.8 51.4 97.3 51.9C100.6 53.2 101.0 55.5 97.8 54.5C91.3 52.5 91.1 52.6 89.5 56.4C87.6 60.9 85.6 61.2 86.5 56.9C87.2 53.8 87.2 53.7 84.9 55.4C81.4 57.8 79.2 57.5 81.5 55.0C83.3 53.0 83.3 52.9 81.6 53.6C80.2 54.1 79.9 53.9 80.2 52.9C80.4 52.1 81.7 51.3 83.1 51.1C85.1 50.8 84.7 50.4 80.2 48.4C77.4 47.2 75.0 45.5 75.0 44.8C75.0 43.8 72.1 43.5 62.1 43.5L49.3 43.5L46.6 48.6C43.4 54.7 37.0 61.4 37.0 58.6Z"/>', '0 0 148 60'),
    /* 蛇·S形（snkS）· 121x90 —— 蛇眼、蛇信作为细节被 potracer 自然保留 */
    snkS: f('<path d="M18.0 89.1C7.7 85.9 1.0 77.5 1.0 67.5C1.0 57.5 8.1 48.9 23.3 40.3C39.0 31.4 44.3 25.6 42.1 19.6C40.6 15.9 39.4 15.6 28.0 15.5C21.4 15.5 16.7 15.0 15.2 14.2C13.2 13.1 12.7 13.2 10.5 15.6C9.1 17.1 8.0 19.3 8.0 20.5C8.0 21.6 7.3 23.5 6.5 24.6C5.1 26.4 5.1 26.3 5.7 23.1C6.3 20.0 6.2 19.8 4.2 20.9C1.5 22.3 -0.1 22.3 2.4 20.8C4.2 19.8 4.2 19.7 2.0 18.0C-0.2 16.3 -0.2 16.3 2.9 16.8C5.5 17.1 6.8 16.6 9.1 14.4C11.1 12.5 11.8 11.0 11.4 9.7C10.5 6.9 13.5 4.3 20.7 1.9C30.9 -1.5 42.8 0.6 49.3 6.8C56.5 13.6 58.0 23.5 53.4 32.8C50.4 38.9 45.5 43.3 34.2 50.3C23.8 56.6 19.0 61.7 19.0 66.3C19.0 71.8 23.7 74.7 30.0 73.0C34.1 71.9 39.7 66.3 49.0 54.1C54.7 46.6 61.8 41.0 68.3 39.0C70.5 38.3 75.2 38.0 79.0 38.2C91.3 38.9 99.4 46.4 99.4 57.1C99.4 63.9 95.8 69.0 88.1 72.8C83.0 75.3 82.4 75.9 84.3 76.4C88.8 77.6 97.6 76.9 101.7 75.1C109.8 71.4 111.4 65.4 107.5 53.1C104.3 43.0 104.6 38.1 108.5 34.0C111.8 30.6 121.0 27.5 121.0 29.9C121.0 30.4 119.8 31.1 118.4 31.4C117.0 31.8 114.7 33.2 113.4 34.5C109.9 38.0 110.3 42.6 114.6 51.2C121.2 64.3 120.2 74.9 111.6 82.8C105.6 88.3 101.5 89.5 88.5 89.5C79.0 89.5 76.8 89.2 72.8 87.2C67.2 84.4 64.7 81.0 64.7 75.9C64.7 70.5 67.0 68.0 75.7 63.5C83.8 59.2 85.7 56.7 82.4 54.3C81.4 53.6 79.1 53.0 77.3 53.0C70.7 53.0 66.1 56.9 57.0 70.3C50.4 79.9 44.5 85.3 37.4 88.0C31.8 90.0 22.9 90.5 18.0 89.1ZM23.8 7.2C24.0 6.5 23.5 6.0 22.6 6.0C21.7 6.0 21.0 6.7 21.0 7.6C21.0 9.3 23.2 9.0 23.8 7.2Z"/>', '0 0 121 90'),
    heartM: s('<path d="M12 19s-6.4-4-6.4-8.6A3.6 3.6 0 0 1 12 8.2a3.6 3.6 0 0 1 6.4 2.2C18.4 15 12 19 12 19Z"/>'),
    lungs: s('<path d="M12 4v9"/><path d="M9.4 8.6c0 2-1 3-2.4 4.2S5 15.6 5 17.4c0 1 .7 1.6 1.8 1.6 1.6 0 2.6-1 2.6-3.2V8.6Z"/><path d="M14.6 8.6c0 2 1 3 2.4 4.2S19 15.6 19 17.4c0 1-.7 1.6-1.8 1.6-1.6 0-2.6-1-2.6-3.2V8.6Z"/>'),
    edit2: s('<path d="M4 20h4L20 8l-4-4L4 16v4Z"/>'),
    hdd: s('<rect x="3.5" y="6" width="17" height="12" rx="2"/><path d="M7 12h.01M11 12h6"/>'),
    wifi: s('<path d="M4 9.5a12 12 0 0 1 16 0M6.8 12.6a8 8 0 0 1 10.4 0M9.6 15.7a4 4 0 0 1 4.8 0"/><path d="M12 19h.01"/>'),
    /* ★ Fix C（2026-10-08）：监护页按钮矢量对齐设计稿 —— 蓝牙符文 / 报警音喇叭+声波 */
    bluetooth: s('<path d="M12 3 18 9 12 12 18 15 12 21 6 15 12 12 6 9Z"/>'),
    speaker: s('<path d="M4 9v6h4l5 4V5L8 9H4z"/><path d="M16 8a5 5 0 0 1 0 8"/><path d="M18.5 5.5a8 8 0 0 1 0 13"/>'),
    cam: s('<path d="M3.5 8h11v9h-11z"/><path d="M14.5 11.5 20.5 8v9l-6-3.5z"/>'),
    rec: s('<circle cx="12" cy="12" r="8.6"/><circle cx="12" cy="12" r="3.4" fill="currentColor"/>'),
    full: s('<path d="M4 9V4.6h4.4M16 4.6h4.4V9M20.4 15v4.4H16M9 19.4H4.6V15"/>'),
    mic: s('<rect x="9.4" y="3.4" width="5.2" height="10" rx="2.6"/><path d="M5.8 11.4a6.2 6.2 0 0 0 12.4 0M12 17.6V21"/>'),
    eyeoff: s('<path d="M4 4l16 16"/><path d="M9.4 5.6A9.6 9.6 0 0 1 12 5.3c5 0 9 6.7 9 6.7a17 17 0 0 1-3 3.6M6.4 7.6A17 17 0 0 0 3 12s4 6.7 9 6.7a9.4 9.4 0 0 0 3.4-.6"/>'),
    flip: s('<rect x="4" y="5" width="16" height="14" rx="2"/><path d="M12 5v14"/>'),
    alarm: s('<path d="M12 4.5a5.4 5.4 0 0 0-5.4 5.4c0 5-2 6.4-2 6.4h14.8s-2-1.4-2-6.4A5.4 5.4 0 0 0 12 4.5Z"/><path d="M10.4 19.2a1.9 1.9 0 0 0 3.2 0"/>'),
    img: s('<rect x="4" y="5" width="16" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="M5 17l4.5-4.5 3 3L16 12l4 5"/>'),
    cruise: s('<circle cx="12" cy="12" r="8.6"/><path d="M15.4 8.6 13.6 13.6 8.6 15.4l1.8-5z"/>'),
    chart: s('<path d="M5 4v16h15"/><path d="M9 16v-4M13 16V8M17 16v-6"/>'),
    ai: s('<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 12h6M12 9v6"/>'),
    download: s('<path d="M12 4v11M8 11l4 4 4-4"/><path d="M4 20h16"/>'),
    manage: s('<rect x="4" y="4" width="7" height="7" rx="1.4"/><rect x="13" y="4" width="7" height="7" rx="1.4"/><rect x="4" y="13" width="7" height="7" rx="1.4"/><rect x="13" y="13" width="7" height="7" rx="1.4"/>'),
    scissors: s('<circle cx="6.4" cy="6.4" r="2.2"/><circle cx="6.4" cy="17.6" r="2.2"/><path d="M8.3 7.7 19 18M19 6 8.3 16.3"/>'),
    save: s('<path d="M5 4h11l3 3v13H5z"/><path d="M8.4 4v5h6V4M8 20v-5h8v5"/>'),
    power: s('<path d="M12 4v8"/><path d="M7.4 6.8a7.4 7.4 0 1 0 9.2 0"/>'),
    empty: s('<path d="M5 7h14v12H5z"/><path d="M5 11h14"/>'),
    log: s('<rect x="4.6" y="4" width="14.8" height="16" rx="2"/><path d="M8.4 8.6h7.2M8.4 12h7.2M8.4 15.4h4.2"/>'),
    tag: s('<path d="M4 11.6V5.6A1.6 1.6 0 0 1 5.6 4h6L20 12.4 12.4 20 4 11.6Z"/><circle cx="8.3" cy="8.3" r="1.3"/>'),
    /* 辐射光线（红外理疗 / 雾化器 / 负离子 / 治疗时长 共用，实测设计 7.png） */
    rays: s('<path d="M12 4.2v3.2M12 16.6v3.2M4.2 12h3.2M16.6 12h3.2M6.5 6.5l2.3 2.3M15.2 15.2l2.3 2.3M17.5 6.5l-2.3 2.3M8.8 15.2l-2.3 2.3"/>'),
    /* 24 小时常开（紫外消毒） */
    uv24: s('<path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20 4.5V9h-4.5"/><text x="12" y="15.6" font-size="9" font-family="Arial,sans-serif" fill="currentColor" stroke="none" text-anchor="middle">24</text>'),
    co2: f('<text x="11" y="16.6" font-size="11.5" font-family="Arial,Helvetica,sans-serif" text-anchor="middle">CO</text><text x="19.6" y="20.6" font-size="7.5" font-family="Arial,Helvetica,sans-serif" text-anchor="middle">2</text>'),
    /* 冷光：雪花 */
    snow: s('<path d="M12 3v18M4.6 7.3l14.8 9.4M19.4 7.3 4.6 16.7"/><path d="M12 6.4 9.6 4M12 6.4 14.4 4M12 17.6 9.6 20M12 17.6l2.4 2.4"/>'),
    /* ★ 2026-10-08（#39c）：蓝牙徽标（监护页功能按钮，设计稿 8-1.png） */
    bluetooth: s('<path d="M7 7l10 10-5 4.2V2.8L17 7 7 17"/>'),
    /* ★ 2026-10-08（#39c）：报警音喇叭+声波 */
    speaker: s('<path d="M4 9.8v4.4h3.2L12 18.4V5.6L7.2 9.8H4Z"/><path d="M15.2 9.2a4.2 4.2 0 0 1 0 5.6M17.6 6.8a7.6 7.6 0 0 1 0 10.4"/>'),
    /* ★ 2026-10-08（#39b）：状态页 红外理疗 = 圆圈内心电脉冲（设计稿 6.png） */
    pulseC: s('<circle cx="12" cy="12" r="8.6"/><path d="M5.8 12h2.6l1.3-3.6 2 7.2 1.7-5.4 1 1.8h3.8"/>'),
    /* ★ 2026-10-08（#39b）：状态页 蓝光理疗 = 灯罩+下方光线（设计稿 6.png） */
    lamp: s('<path d="M6.4 10.6a5.6 5.6 0 0 1 11.2 0l.8 3.6H5.6z"/><path d="M12 3v1.8M8 17.6l-1.2 2.2M12 17.8v2.8M16 17.6l1.2 2.2"/>'),
  };

  /* ---------- 数据（★ 已清空全部虚拟/演示数据，真实值由 icu-native-bridge.js 从原生注入） --- */
  const D = {
    device: 'URIT animal',
    hospital: '',
    user: '',
    time: '--:--',
    selCaseId: '',
    date: '',
    stamp: '',
    session: '',

    /* 病症下拉选项（PDF P5：绝育 / 骨折 / 心脏病 / 幼儿护理） */
    illnessOpts: ['绝育', '骨折', '心脏病', '幼儿护理'],
    /* 护理列表 / 回顾 行（由桥接层从原生 careCases / historyCases 注入） */
    careRows: [],
    reviewRows: [],
    /* 护理记录 18 项（由桥接层从原生 treatments 注入） */
    recordRows: [],
    /* 物种：全系统唯一来源。改版后 = 犬/猫/兔/蛇/蜥蜴/其它
       —— 新建样本弹窗 与 回顾·查询弹窗 共用这一份 */
    species: ['犬', '猫', '兔', '蜥蜴', '蛇', '其它'],
    /* 护疗类型：查询弹窗改版后已不再使用，保留备用 */
    careTypes: ['心肺', '母幼', '术后', '产后', '自定义'],
    modes: [
      { k: '猫', t: '母幼护理模式', tKey: 'modeMother', icon: 'cat' },
      { k: '心', t: '术后护理模式', tKey: 'modePostop', icon: 'heartM' },
      { k: '肺', t: '心肺护理模式', tKey: 'modeCardio', icon: 'lungs' },
      { k: '笔', t: '自定义模式', tKey: 'modeCustom', icon: 'edit2' },
    ],
    /* 主控 4 行卡（由桥接层从原生 host/controls 注入；-- 为无数据） */
    ctrlRow1: [
      { t: '舱内温度 ℃', tKey: 'ctrlTemp', v: '--', p: 0, set: 1, ico: 'thermo' },
      { t: '氧浓度 %', tKey: 'ctrlO2', v: '--', p: 0, set: 1, ico: 'o2' },
      { t: '湿度 %', tKey: 'ctrlHum', v: '--', p: 0, set: 1, ico: 'drop' },
      { t: '二氧化碳浓度 PPM', tKey: 'ctrlCo2', v: '--', p: 0, set: 1, ico: 'co2' },
      { t: '监护等级', tKey: 'ctrlLevel', v: '', level: 1 },
    ],
    ctrlRow2: [
      { t: '红外理疗', tKey: 'ctrlRed', v: '00:00', on: 0, ico: 'rays' },
      { t: '蓝光理疗', tKey: 'ctrlBlue', v: '00:00', on: 0, ico: 'blueLight' },
      { t: '紫外消毒', tKey: 'ctrlUv', v: '00:00', on: 0, ico: 'uv24' },
      { t: '雾化器', tKey: 'ctrlNeb', v: '00:00', on: 0, ico: 'neb' },
      { t: '负离子', tKey: 'ctrlAnion', v: '00:00', on: 0, ico: 'neg' },
    ],
    ctrlRow3: [
      { t: '冷光照明', tKey: 'ctrlCold', v: '', on: 0, ico: 'cold' },
      { t: '暖光照明', tKey: 'ctrlWarm', v: '', on: 0, ico: 'sun' },
      { t: '外循环', tKey: 'ctrlOuter', v: '', on: 0, ico: 'loopOut' },
      { t: '内循环', tKey: 'ctrlInner', v: '', on: 0, ico: 'loopIn' },
      { t: '治疗时长', tKey: 'ctrlTime', v: '--', on: 0, ico: 'spin', big: 1 },
    ],
    /* 监护页体征（任务33：连接监护宝 BLE 时由桥接层从 S.monitor 填实时值；未连接显示 --，不造假数据） */
    vitals: { hr: '--', bp: '--/--', map: '--', spo2: '--', pr: '--', temp: '--', rr: '--' },
    /* 记录单（由桥接层从原生 patient/treatments 注入） */
    sheet: {
      animal: '', no: '', owner: '', date: '', cage: '',
      sp: '', weight: '', dept: '', dur: '', doc: '',
      proj: [],
      env: [],
      imgs: [],
      alarm: '',
      concl: { states: ['良好', '一般', '差'], advice: '' },
      foot: { tel: '', addr: '', note: '', doc: '', time: '', page: '' },
    },
    logText: '',
    upLog: '',
    aboutBase: [],
    aboutVer: [],
    /* ★ V1.02·任务8 补偿页（5 项补偿值，本地状态，初始 0 = 中性默认值，非虚构数据）
       由 comp 屏 +/− 按钮 ±1 步进，不落原生 */
    compVals: [0, 0, 0, 0, 0],
    /* ★ V1.02·任务8 日志页当前分类（'前端调试' 默认） */
    logCat: '',
    /* ★ 2026-10-08 用户日志：原生 userOpLog 倒序推来的记录 + 日期过滤(YYYY-MM-DD,空=全部) + 分页页码 */
    userLogs: [], logDate: '', logPage: 1,
    /* 改版标注汇总（对应《动物ICU软件需求整理 V1.02》第二部分 UI 改版建议） */
    changes: [
      ['P1 启动页', '增加开机动画：打开软件前先播放动画（本次不制作，保持静态 Logo 页）'],
      ['P2 登录页', '删除「新用户注册」入口，仅保留账号密码登录'],
      ['P3/P5 护疗列表 · 表头', '「样本号」→「住院号」；新增「宠物主人」列；「状态」→「病症」'],
      ['P5 护疗列表 · 病症', '病症下拉选项：绝育 / 骨折 / 心脏病 / 幼儿护理'],
      ['P3/P5 护疗列表 · 按钮', '「开始新护疗」→「开始护疗」'],
      ['P5/P12 列表规则', '仅显示当天新建信息；当天结束的护疗过凌晨 0 点转入【回顾】'],
      ['P4 新建样本 · 字段', '「样本号」→「住院号」；「主人」→「宠物主人」（置于「物种」正下方同列）'],
      ['P4 新建样本 · 物种', '删除「鸟」「蜘蛛」，新增「兔」「蛇」→ 犬/猫/兔/蛇/蜥蜴/其它'],
      ['P6~P11 会话页', '底部固定显示「当前住院号 + 宠物名」（监护模式标识）'],
      ['P7 主控 · 功能卡', '每张卡片增加（开 / 关）图标按键'],
      ['P7 主控 · 护理模式', '每个模式增加（开 / 关）和（设置）图标按键'],
      ['P8/P9 监护 · 单位', '按设计图 8-1.png 像素实测还原：心率 bpm、脉率 bpm、呼吸率 brpm、血氧 %、体温 ℃；血压无「血压」标签，改为「实时 ⇄ 物理」切换键 + 平均压：93.3'],
      ['P8 监护 · 血压', '增加「实时血压 / 物理血压」切换键'],
      ['P8/P9 监护 · 菜单', '删除右侧悬浮菜单，蓝牙/监护宝/报警音/设置 移入底部操作栏'],
      ['P12 护疗记录', '顶部列表表头同步【护疗】列表修改'],
      ['P13 报告单', '底部显示当前住院号 + 宠物名'],
      ['P14 回顾 · 表头', '与【护疗】列表保持一致（住院号 / 宠物主人 / 病症）'],
      ['P14 回顾 · 范围', '显示除【护疗】列表外的所有护疗记录'],
      ['P15 查询弹窗', '「样本号」→「住院号」；新增「联系电话」；物种与新建样本一致'],
      ['P17 发送数据', '增加「蓝牙传输」；增加微信「文件传输助手」互传二维码'],
      ['P20 常规设置', '新增「机号 /SN」「生产日期」；出厂后由工程师编辑且置灰'],
      ['P21 连接设置', '删除「确认」「取消」按钮（开关即时生效）'],
      ['P23 打印设置', '新增「页脚 LOGO」与「当前 LOGO」上传区'],
      ['P25 用户管理', '新增「新增」按钮'],
      ['P26/P27 升级', '软件升级、控制板升级均增加微信「文件传输助手」二维码'],
      ['P30 关于页', '版本行标注 A1 / A2 / A3 版本'],
      ['P31 日志页', '左侧菜单新增「用户日志」'],
      ['P32 注销确认', '确认后回到初始登录界面'],
      ['P34 退出提示', '「确认是否关机？」→「确认是否退出软件？」'],
      ['P35 退出中', '「缓存完成后自动关机」→「退出软件」'],
      ['V1.02 · 传输设置', '云平台 / LIS 置灰预留，标注「二期」'],
      ['V1.02 · 发送数据', '回顾页「发送数据」置灰不可点，标注「二期」'],
    ],
  };

  /* ---------- 公共构件 --------------------------------------------------- */
  function pawLogo() { return (window.NI && window.NI.paw) ? NI.paw : I.paw; }
  function clockBox() { return '<div class="clock"><b>' + D.time + '</b>' + D.date + '</div>'; }
  function userBox() { return '<div class="userbox">' + I.user + '<span>' + D.user + '</span></div>'; }

  /* 顶栏（带 护理/回顾/菜单 主航） */
  function topbar(active) {
    /* 设计 7.png 实测：爪印区 x0-138（图标居中 x26-54），护理 active 蓝块 x139-318 w180，
       回顾 x~340-480、菜单 x~520-660（图标+文字），右侧 管理员 + 时钟。无竖分隔线。 */
    const tabs = [
      ['care', '护疗', 'care', 139, 180, 'navCare'],
      ['review', '回顾', 'history', 340, 141, 'navReview'],
      ['menu', '菜单', 'menu', 520, 141, 'navMenu']
    ];
    return '<div class="topbar">'
      + '<div class="paw">' + pawLogo() + '</div>'
      + '<div class="navtabs">' + tabs.map(function (t) {
        return '<div class="navtab' + (t[0] === active ? ' on' : '') + '" data-go="' + t[0]
          + '" style="left:' + t[3] + 'px;width:' + t[4] + 'px">' + I[t[2]] + '<span>' + T(t[5], t[1]) + '</span></div>';
      }).join('') + '</div>'
      + '<div class="spacer"></div>' + userBox() + clockBox() + '</div>';
  }
  /* 顶栏（返回式，设置/关于/日志等） */
  function topbarBack(backGo) {
    return '<div class="topbar">'
      + '<div class="backbar" data-go="' + (backGo || 'menu') + '">' + I.back + '<span>' + T('back', '返回') + '</span></div>'
      + '<div class="spacer"></div>' + userBox() + clockBox() + '</div>';
  }

  /* 底栏（it.dis 置灰不可点；it.tag 右上角标，如「二期」） */
  function bottombar(items, active, extra) {
    return '<div class="bottombar">'
      + items.map(function (it) {
        return '<div class="bbtn' + (it.id === active ? ' on' : '') + (it.dis ? ' dis' : '') + '"'
          + (it.dis ? '' : ' data-go="' + it.go + '"' + (it.key ? ' data-key="' + it.key + '"' : '')) + '>'
          + (it.icon ? I[it.icon] : '') + '<span>' + T(it.key, it.label) + '</span>'
          + (it.tag ? '<em class="tag2">' + it.tag + '</em>' : '') + '</div>';
      }).join('')
      + (extra || '') + '</div>';
  }
  /* 会话底部状态条：状态/主控/监护/视频/结束（设计 7.png 无右侧汉堡按钮）
     设计实测（7.png）各项左边界与宽度，避免字体度量导致的居中漂移 */
  function sessionBar(active) {
    /* docx 3.4 R5：结束 = 关闭所有护疗功能、停止时长累计、保存记录、返回住院清单列表（care） */
    const items = [
      ['status', '状态', 'chart', 553, 145, 'status'],
      ['control', '主控', 'tune', 726, 150, 'control'],
      ['monitor', '监护', 'wave', 899, 145, 'monitor'],
      ['video', '视频', 'cam', 1074, 145, 'video'],
      ['care', '结束', 'power', 1252, 145, 'finish']
    ];
    return '<div class="bottombar sessbar">'
      + items.map(function (it) {
        return '<div class="bbtn' + (it[0] === active ? ' on' : '') + '" data-go="' + it[0]
          + '" data-key="' + it[5] + '" style="left:' + it[3] + 'px;width:' + it[4] + 'px">' + I[it[2]] + '<span>' + T(it[5], it[1]) + '</span></div>';
      }).join('') + '</div>';
  }

  /* 左下角会话信息显示框：住院号-宠物名（设计为纯文字，无边框/图标） */
  function sessionTag() {
    return '<div class="session-info"><div class="si-val">' + D.session + '</div></div>';
  }

  /* 弹窗 */
  function modal(title, body, foot) {
    return '<div class="modal">'
      + (title ? '<div class="mhead">' + title + '</div>' : '')
      + '<div class="mbody">' + body + '</div>'
      + '<div class="mfoot">' + (foot || '') + '</div></div>';
  }
  function mask(inner) { return '<div class="mask">' + inner + '</div>'; }
  function confirmModal(title, text, yes, no) {
    return mask(modal(title || T('tipTitle', '提示'),
      '<div class="alert-row">' + I.warn + '<span>' + text + '</span></div>',
      '<div class="btn" data-go="' + (yes || 'menu') + '">' + (yes ? T('yes', '是') : T('confirm', '确认')) + '</div>'
      + '<div class="btn primary" data-go="' + (no || 'menu') + '">' + (no ? T('no', '否') : T('cancel', '取消')) + '</div>'));
  }
  function spinnerBlock(text) {
    const bars = [];
    for (let i = 0; i < 12; i++) {
      bars.push('<i style="transform:rotate(' + (i * 30) + 'deg) translateY(-48px);animation:fade 1s linear ' + (i * 0.083).toFixed(2) + 's infinite"></i>');
    }
    return '<div class="loading"><div class="spinner">' + bars.join('') + '</div>' + text + '</div>';
  }
  function loadingMask(text) { return mask('<div class="modal" style="min-width:560px">' + '<div class="mbody" style="padding:56px 80px">' + spinnerBlock(text) + '</div></div>'); }

  /* 波形（ECG / PLETH / RESP）——按设计 8-1.png 形态还原
     特征：复合波之间有明显「平直基线段」；脉冲部分窄而尖；基线垂直居中偏下。
     坐标 y 向下；base 为基线，amp 为振幅系数。每类波形按周期 p∈[0,1) 分段拟合。 */
  function wavePath(kind, w, h) {
    const N = 1448, pts = [];
    const base = h * 0.62, amp = h * 0.40;
    for (let i = 0; i <= N; i++) {
      const t = i / N, x = t * w;
      let y;
      if (kind === 'ecg') {
        /* ECG：平直基线 → 小 P → 尖锐 QRS（R 高尖、S 深谷）→ 宽 T → 回基线 */
        const p = (t * 6.2) % 1;
        if (p < 0.14) y = base - amp * 0.08 * Math.sin(p / 0.14 * Math.PI);            /* P 波 */
        else if (p < 0.19) y = base + amp * 0.14 * Math.sin((p - 0.14) / 0.05 * Math.PI); /* Q */
        else if (p < 0.24) y = base - amp * 1.30 * Math.sin((p - 0.19) / 0.05 * Math.PI); /* R 尖峰 */
        else if (p < 0.29) y = base + amp * 0.48 * Math.sin((p - 0.24) / 0.05 * Math.PI); /* S 深谷 */
        else if (p < 0.42) y = base - amp * 0.04 * Math.sin((p - 0.29) / 0.13 * Math.PI); /* ST 段 */
        else if (p < 0.58) y = base - amp * 0.34 * Math.sin((p - 0.42) / 0.16 * Math.PI); /* T 波 */
        else y = base - amp * 0.01 * Math.sin((p - 0.58) / 0.42 * Math.PI);              /* 回基线 */
      } else if (kind === 'pleth') {
        /* PLETH：窄而高的收缩峰 → 小重搏切迹 → 长平低基线（舒张静息） */
        const p = (t * 6.0) % 1;
        if (p < 0.10) y = base - amp * 1.02 * Math.sin(p / 0.10 * Math.PI);            /* 收缩升支+峰 */
        else if (p < 0.18) y = base - amp * 0.30 * Math.sin((p - 0.10) / 0.08 * Math.PI); /* 切迹 */
        else if (p < 0.28) y = base - amp * 0.52 * Math.sin((p - 0.18) / 0.10 * Math.PI); /* 重搏波 */
        else y = base + amp * 0.06 * Math.sin((p - 0.28) / 0.72 * Math.PI);            /* 舒张平低 */
      } else {
        /* RESP：平滑正弦呼吸波（与 Berry base.apk 演示数据形态一致：缓慢圆滑起伏） */
        const p = (t * 3.2) % 1;
        y = base - amp * 0.92 * Math.sin(p * 2 * Math.PI);
      }
      pts.push(x.toFixed(1) + ',' + y.toFixed(1));
    }
    return pts.join(' ');
  }
  /* ★ 任务33：实时波形（AM4100 采样数组 → polyline 点串）。
     samples 为原生整数采样；按窗口 min/max 自适应归一化映射到 [h*0.08, h*0.92]。 */
  function liveWavePath(samples, w, h) {
    if (!samples || !samples.length) return '';
    var n = samples.length, mn = samples[0], mx = samples[0];
    for (var i = 1; i < n; i++) { var v = samples[i]; if (v < mn) mn = v; if (v > mx) mx = v; }
    var span = (mx - mn) || 1, base = h * 0.92, amp = h * 0.84;
    var pts = [];
    for (var j = 0; j < n; j++) {
      var x = j / (n - 1 || 1) * w;
      var y = base - (samples[j] - mn) / span * amp;
      pts.push(x.toFixed(1) + ',' + y.toFixed(1));
    }
    return pts.join(' ');
  }
  /* 实时波形框：快照式渲染（原生节流推送，每次 icu-native-state 后随渲染刷新，无需 rAF） */
  function liveWaveBox(samples, color, label) {
    return '<div class="wave"><svg viewBox="0 0 1448 289" preserveAspectRatio="none">'
      + '<polyline points="' + liveWavePath(samples, 1448, 289) + '" fill="none" stroke="' + color + '" stroke-width="4"/></svg>'
      + (label ? '<div class="lab" style="color:' + color + '">' + label + '</div>' : '') + '</div>';
  }
  function waveBox(kind, color, label) {
    /* ★ 任务19：未连蓝牙默认演示波形 = 动态滚动波形（双 tile 无缝循环）。
       生成 2 份重复的波形点串（总宽 2896），外层 <g class="wscroll"> 由 rAF 平移，
       平移满一个 tile 宽(1448) 即复位，形成无缝滚动（动态效果参考含演示数据 apk）。 */
    var pts = wavePath(kind, 1448, 289).split(' ');
    var tile2 = pts.map(function (p) { var a = p.split(','); return (parseFloat(a[0]) + 1448).toFixed(1) + ',' + a[1]; }).join(' ');
    return '<div class="wave"><svg viewBox="0 0 1448 289" preserveAspectRatio="none">'
      + '<g class="wscroll"><polyline points="' + pts.join(' ') + ' ' + tile2 + '" fill="none" stroke="' + color + '" stroke-width="4"/></g></svg>'
      + (label ? '<div class="lab" style="color:' + color + '">' + label + '</div>' : '') + '</div>';
  }
  /* ★ 任务19：演示波形滚动动画（requestAnimationFrame 驱动 <g class="wscroll"> 平移）。
     页面无 .wave 时自动休眠，切屏后由 __waveAnimKick 在渲染完成时重新唤醒。 */
  (function () {
    var off = 0, last = 0, running = false;
    function tick(t) {
      if (!last) last = t;
      var dt = t - last; last = t;
      var gs = document.querySelectorAll('.wave>svg>g.wscroll');
      if (!gs.length) { running = false; return; }
      off = (off + dt * 0.18) % 1448;
      var tx = 'translate(' + (-off).toFixed(1) + ',0)';
      for (var i = 0; i < gs.length; i++) gs[i].setAttribute('transform', tx);
      requestAnimationFrame(tick);
    }
    window.__waveAnimKick = function () { if (!running) { running = true; last = 0; requestAnimationFrame(tick); } };
  })();
  function waveStrip(kind, color, cap) {
    return '<div class="wave-strip"><div class="cap">' + cap + '</div><div class="wp"><svg viewBox="0 0 900 120" preserveAspectRatio="none">'
      + '<polyline points="' + wavePath(kind, 900, 120) + '" fill="none" stroke="' + color + '" stroke-width="3"/></svg></div></div>';
  }

  function fisheye(ts) {
    return '<div class="fisheye"></div>' + (ts ? '<div class="ts">' + ts + '<br>' + T('weekdayThu', '星期四') + '</div>' : '');
  }

  /* 新建样本弹窗 · 右侧剪影：按物种名返回单色 svg 字符串
     （容器 #0f3e86、尺寸 170px 由调用方包 span 控制）。"其它"→paw 兜底。 */
  function silSvg(name) {
    var m = { '犬': 'dogS', '猫': 'catS', '兔': 'rabS', '蜥蜴': 'lizS', '蛇': 'snkS', '其它': 'paw' };
    var key = m[name] || 'paw';
    var svg = (I && I[key]) ? I[key] : '';
    /* 与原 screens.js 一致：在第一个 <svg 标签上注入 width/height，让 svg 自适应填满 170×170 */
    return svg ? svg.replace('<svg', '<svg width="170" height="170"') : '';
  }

  window.I = I;
  window.D = D;
  window.silSvg = silSvg;
  /* ★ 2026-09-30 用户确认：全工程日期时间显示统一为「yyyy年MM月dd日 HH:mm:ss」。
     仅做显示层转换；原生存储/解析仍用 yyyy-MM-dd（visitDate 等逻辑不动）。
     入参兼容 'yyyy-MM-dd' / 'yyyy-MM-dd HH:mm[:ss]' / 'yyyy/MM/dd'，无法识别时原样返回。 */
  window.fmtDT = function (v) {
    if (v == null) return '';
    var str = String(v).trim();
    var m = str.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?$/);
    if (!m) return str;
    function p2(x) { return (x.length < 2 ? '0' : '') + x; }
    /* ★ 任务34：英文语言下用 yyyy/MM/dd 数字格式，不输出「年月日」汉字 */
    var out = ((window.D && window.D.lang) === 'en')
      ? m[1] + '/' + p2(m[2]) + '/' + p2(m[3])
      : m[1] + '年' + p2(m[2]) + '月' + p2(m[3]) + '日';
    if (m[4] != null) out += ' ' + p2(m[4]) + ':' + p2(m[5]) + (m[6] != null ? ':' + p2(m[6]) : '');
    return out;
  };
  window.P = {
    topbar: topbar, topbarBack: topbarBack, bottombar: bottombar, sessionBar: sessionBar,
    sessionTag: sessionTag, modal: modal, mask: mask, confirmModal: confirmModal,
    loadingMask: loadingMask, spinnerBlock: spinnerBlock, waveBox: waveBox, waveStrip: waveStrip, liveWaveBox: liveWaveBox, liveWavePath: liveWavePath,
    fisheye: fisheye, clockBox: clockBox, userBox: userBox, pawLogo: pawLogo,
    silSvg: silSvg,
  };
})();
