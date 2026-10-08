#!/usr/bin/env python3
"""
lanhu_audit.py — CSS / JS 冲突自检
用法: python lanhu_audit.py [view_name]
不带参数:审计全部 13 个 view,列出每页 CSS/JS 来源 + 同 class 多处定义
带参数:只审计指定 view
"""
import os, re, sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.abspath(__file__))
VIEWS = os.path.join(ROOT, 'src', 'views')

def css_selectors(path):
    if not os.path.exists(path):
        return set()
    c = open(path, 'r', encoding='utf-8').read()
    out = set()
    for m in re.finditer(r'([^{}]+?)\s*\{([^{}]*)\}', c, re.S):
        body = m.group(2)
        if not body.strip() or body.lstrip().startswith('@'):
            continue
        for s in m.group(1).split(','):
            s = s.strip()
            if s:
                # 去掉 :hover/:nth-child 等伪类,只留 base
                base = re.split(r'::|:', s, 1)[0].strip()
                if base:
                    out.add(base)
    return out

def inline_blocks(html):
    style = re.search(r'(?s)<style[^>]*>(.*?)</style>', html)
    script = re.search(r'(?s)<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>', html)
    return (style.group(1) if style else '',
            script.group(1) if script else '')

def class_set_from_inline(css_body):
    # 抓 .className / #idName
    out = set()
    for m in re.finditer(r'([.#])([A-Za-z0-9_\-]+)', css_body):
        out.add(m.group(1) + m.group(2))
    return out

def audit(view_name):
    vdir = os.path.join(VIEWS, view_name)
    html = os.path.join(vdir, 'index.html')
    if not os.path.exists(html):
        print(f'NOT FOUND: {html}')
        return

    print(f'\n========== {view_name} ==========')
    src = open(html, 'r', encoding='utf-8').read()

    # 静态 <link> / <script src>
    links = re.findall(r'<link[^>]+href="([^"]+\.css)"', src)
    scripts = re.findall(r'<script[^>]+src="([^"]+\.js)"', src)
    print(f'CSS: {len(links)}  →  {links}')
    print(f'JS : {len(scripts)}  →  {scripts}')

    # 内联 <style> / <script>
    inline_css, inline_js = inline_blocks(src)
    print(f'Inline <style>  : {len(inline_css)} bytes')
    print(f'Inline <script> : {len(inline_js)} bytes')

    # 把所有 CSS 源折成"class → 来源"映射
    cls_to_src = defaultdict(list)
    for href in links:
        path = os.path.normpath(os.path.join(vdir, href))
        for s in css_selectors(path):
            cls_to_src[s].append(href)
    for s in class_set_from_inline(inline_css):
        cls_to_src[s].append('<inline-style>')

    # 找冲突:class 出现 ≥2 处
    conflicts = {k: v for k, v in cls_to_src.items() if len(set(v)) >= 2}
    if conflicts:
        print(f'\n  ⚠ 同 class 出现在多处(改一处可能落空):')
        for k in sorted(conflicts)[:30]:
            print(f'    {k}  ←  {conflicts[k]}')
        if len(conflicts) > 30:
            print(f'    ... 还有 {len(conflicts)-30} 条')

    # 检查 JS 是否被多次加载
    print(f'\n  ✓ 全部 JS 都从外链加载 (无内联): {len(inline_js) == 0}')

if __name__ == '__main__':
    if len(sys.argv) >= 2:
        audit(sys.argv[1])
    else:
        for v in sorted(os.listdir(VIEWS)):
            audit(v)
