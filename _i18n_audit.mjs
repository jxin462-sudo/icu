/* i18n 漏译审计：扫描 v2 JS 中含 CJK 的字符串字面量，
   排除 I18N 字典区（screens.js 前 ~250 行内的 zh/en 键值）与纯注释行 */
import fs from 'fs';
import path from 'path';
const root = 'C:/Users/jx/Desktop/icu.apk/icu.apk/app/src/main/assets/lanhu/v2';
const files = ['js/screens.js', 'js/core.js', 'js/app.js', 'icu-native-bridge.js'];
// screens.js 的 I18N 字典：从 "const I18N" 到 "window.T = T" 之后的多语言补充块，粗略按行号范围排除 zh 键行
for (const f of files) {
  const src = fs.readFileSync(path.join(root, f), 'utf8');
  const lines = src.split(/\r?\n/);
  lines.forEach((ln, i) => {
    // 跳过注释
    const t = ln.trim();
    if (t.startsWith('//') || t.startsWith('/*') || t.startsWith('*')) return;
    // 找含 CJK 的字符串字面量
    const re = /'([^'\n]*[\u4e00-\u9fff][^'\n]*)'/g;
    let m, hits = [];
    while ((m = re.exec(ln))) hits.push(m[1]);
    if (!hits.length) return;
    // 纯字典行：整行都是 key: '值', 形式（可带 zh: { / en: { 前缀、} 结尾）
    if (/^(?:(?:zh|en)\s*:\s*\{\s*)?(?:[A-Za-z0-9_]+\s*:\s*'[^']*',?\s*)+\}?,?\s*$/.test(t)) return;
    console.log(`${f}:${i + 1}\t${hits.join(' | ')}\t\t${t.slice(0, 160)}`);
  });
}
