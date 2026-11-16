#!/usr/bin/env python3
"""Runs demo.sh and renders what it printed, with its timing, as an animated SVG.

    ./record.py ../../docs/demo.svg

The SVG is a replay of real output, not a screen capture: long pauses are shortened and
a pause is added before each command, nothing else is changed.
"""
import html
import re
import subprocess
import sys
import time

out_path = sys.argv[1] if len(sys.argv) > 1 else "demo.svg"
process = subprocess.Popen(["./demo.sh"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
events = []
start = time.monotonic()
for raw in process.stdout:
    events.append((time.monotonic() - start, raw.rstrip("\n")))
process.wait()
if process.returncode != 0:
    sys.exit("demo.sh failed:\n" + "\n".join(line for _, line in events))

ansi = re.compile(r"\x1b\[[0-9;]*m")
lines = []
clock = 0.0
previous = None
for at, text in events:
    plain = ansi.sub("", text)
    if previous is not None:
        gap = at - previous
        # A command is "typed" after a short pause; long waits are cut down.
        clock += min(max(gap, 0.25), 1.2) + (1.0 if plain.startswith("$ ") else 0)
    previous = at
    if len(plain) > 100:
        plain = plain[:97] + "..."
    lines.append((clock, plain))
total = clock + 5.0

line_height, pad, char_width = 19, 18, 8.45
width = int(pad * 2 + 100 * char_width)
height = pad * 2 + 34 + line_height * len(lines)
css, body = [], []
for i, (at, text) in enumerate(lines):
    percent = 100 * at / total
    css.append(f"@keyframes l{i}{{0%,{percent:.2f}%{{opacity:0}}{percent + 0.01:.2f}%,100%{{opacity:1}}}}"
               f".l{i}{{animation:l{i} {total:.1f}s linear infinite}}")
    kind = "cmd" if text.startswith("$ ") else "hook" if text.startswith("[tendril]") else "out"
    y = pad + 34 + line_height * (i + 1) - 5
    body.append(f'<text x="{pad}" y="{y}" class="l{i} {kind}" xml:space="preserve">{html.escape(text)}</text>')

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" role="img" aria-label="Tendril timing the SQL and HTTP requests of a running Spring Boot application">
<style>
text{{font:14px ui-monospace,SFMono-Regular,Menlo,Consolas,"DejaVu Sans Mono",monospace;fill:#c9d1d9;opacity:0}}
.cmd{{fill:#f0f6fc;font-weight:bold}}.hook{{fill:#7ee787}}
{"".join(css)}
</style>
<rect width="100%" height="100%" rx="10" fill="#0d1117"/>
<circle cx="24" cy="22" r="6" fill="#ff5f56"/><circle cx="44" cy="22" r="6" fill="#ffbd2e"/><circle cx="64" cy="22" r="6" fill="#27c93f"/>
{chr(10).join(body)}
</svg>
'''
with open(out_path, "w") as f:
    f.write(svg)
print(f"{out_path}: {len(lines)} lines, {total:.0f} s loop")
