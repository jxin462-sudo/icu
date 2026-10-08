/* 提取所有 T('key',...) / TT / Tg / T2/Tg3 使用的 key，对比 screens.js I18N 字典已有 key，列出缺失 */
import fs from 'fs';
const root = 'C:/Users/jx/Desktop/icu.apk/icu.apk/app/src/main/assets/lanhu/v2/';
const scr = fs.readFileSync(root + 'js/screens.js', 'utf8');
// 字典已定义 key
const defined = new Set();
const dictRe = /([A-Za-z0-9_]+)\s*:\s*'/g;
// 只取 I18N 区域（文件开头到 window.T = T 之后的补充块结束，粗略：前 280 行）
scr.split(/\r?\n/).slice(0, 280).forEach(l => { let m; while ((m = dictRe.exec(l))) defined.add(m[1]); });
const used = new Map(); // key -> fallback
for (const f of ['js/screens.js', 'js/core.js', 'js/app.js', 'icu-native-bridge.js']) {
  const src = fs.readFileSync(root + f, 'utf8');
  const re = /\bT[Tg23]?\(\s*'([A-Za-z0-9_]+)'\s*,\s*'([^']*)'/g;
  let m; while ((m = re.exec(src))) { if (!used.has(m[1])) used.set(m[1], m[2]); }
}
const missing = [...used.entries()].filter(([k]) => !defined.has(k));
console.log('MISSING ' + missing.length);
for (const [k, fb] of missing) console.log(k + '\t' + fb);
