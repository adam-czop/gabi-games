"""Built-in illustrations (docs/02 §4, Q9): 3 categories x 8 simple flat drawings in the muted v2 palette.

Usage (needs Google Chrome and Pillow):
    python tools/builtin_art.py <out_dir_for_svg_png> [--assets app/src/main/assets/builtin]

Each drawing is a 400x400 SVG, rendered by headless Chrome at 800x800 and saved as WebP
(800 px image + 400 px thumbnail). Objects stay inside the central band so the 4:3 centre crop
used by the puzzles (docs/05) keeps the whole object.
"""
import argparse
import subprocess
import sys
from pathlib import Path

INK = "#3B3936"
SAGE, SAGE_D = "#A9C2B4", "#7E9C8C"
MIST, MIST_L, MIST_D = "#A9BCCB", "#D3E0E8", "#7F95A6"
HONEY, HONEY_D = "#E6C98F", "#D6B47A"
TERRA, TERRA_L = "#DDA38C", "#EBC3B3"
ROSE, ROSE_D = "#EDBFB8", "#E0A59B"
WOOD, WOOD_L, WOOD_D = "#C9A27E", "#E2C9AC", "#A9825F"
CREAM, WHITE = "#FFF8EC", "#FFFFFF"
GREY, GREY_D = "#D8D1C7", "#9C958B"
SKIN = "#F3D9C4"

BACKGROUNDS = {
    "dom": ("#F1EDE7", "#E9E2D8"),
    "zwierzeta": ("#EEF1EC", "#E2E9E0"),
    "zabawki": ("#EDF0F1", "#E0E7EA"),
    "pojazdy": ("#F3F0E8", "#E9E3D6"),
}

TYRE = "#5A5651"
ROAD = f'<path d="M30 314 H370" fill="none" stroke="{GREY_D}" stroke-width="6"/>'


def wheel(cx: int, cy: int, r: int, hub: str = GREY) -> str:
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{TYRE}"/><circle cx="{cx}" cy="{cy}" r="{int(r * 0.42)}" fill="{hub}" stroke-width="5"/>'

ART = {
    # ---------------- Nasz dom ----------------
    "dom_kanapa": f"""
      <rect x="95" y="135" width="210" height="85" rx="24" fill="{SAGE}"/>
      <rect x="125" y="150" width="62" height="52" rx="14" fill="{HONEY}" transform="rotate(-8 156 176)"/>
      <rect x="75" y="198" width="250" height="72" rx="18" fill="{SAGE}"/>
      <path d="M200 204 V262" fill="none"/>
      <rect x="52" y="170" width="58" height="112" rx="24" fill="{SAGE_D}"/>
      <rect x="290" y="170" width="58" height="112" rx="24" fill="{SAGE_D}"/>
      <rect x="78" y="280" width="16" height="30" rx="5" fill="{WOOD_D}"/>
      <rect x="306" y="280" width="16" height="30" rx="5" fill="{WOOD_D}"/>""",
    "dom_lozko": f"""
      <rect x="58" y="125" width="54" height="180" rx="22" fill="{WOOD}"/>
      <rect x="85" y="238" width="262" height="44" rx="12" fill="{WOOD}"/>
      <rect x="98" y="195" width="240" height="52" rx="14" fill="{WHITE}"/>
      <ellipse cx="148" cy="192" rx="40" ry="24" fill="{WHITE}"/>
      <path d="M185 190 H322 a20 20 0 0 1 20 20 V262 H185 Z" fill="{MIST}"/>
      <path d="M185 214 H342" fill="none"/>
      <rect x="92" y="280" width="16" height="28" rx="5" fill="{WOOD_D}"/>
      <rect x="322" y="280" width="16" height="28" rx="5" fill="{WOOD_D}"/>""",
    "dom_lampa": f"""
      <path d="M200 92 V72" fill="none" stroke="{HONEY_D}"/>
      <path d="M132 120 l-16 -14 M268 120 l16 -14" fill="none" stroke="{HONEY_D}"/>
      <path d="M150 110 H250 L292 205 H108 Z" fill="{HONEY}"/>
      <circle cx="200" cy="214" r="12" fill="{CREAM}"/>
      <rect x="192" y="205" width="16" height="92" rx="6" fill="{GREY_D}"/>
      <ellipse cx="200" cy="300" rx="62" ry="17" fill="{WOOD}"/>""",
    "dom_wanna": f"""
      <circle cx="150" cy="140" r="16" fill="{WHITE}" stroke="{MIST_D}" stroke-width="6"/>
      <circle cx="190" cy="112" r="11" fill="{WHITE}" stroke="{MIST_D}" stroke-width="6"/>
      <circle cx="225" cy="148" r="20" fill="{WHITE}" stroke="{MIST_D}" stroke-width="6"/>
      <path d="M300 182 V122 H332 V142" fill="none" stroke="{GREY_D}" stroke-width="12"/>
      <path d="M68 196 H332 V228 a62 62 0 0 1 -62 62 H130 a62 62 0 0 1 -62 -62 Z" fill="{WHITE}"/>
      <rect x="52" y="178" width="296" height="26" rx="13" fill="{MIST}"/>
      <circle cx="120" cy="300" r="12" fill="{GREY_D}"/>
      <circle cx="280" cy="300" r="12" fill="{GREY_D}"/>""",
    "dom_stol": f"""
      <path d="M240 112 C232 92 250 80 262 92" fill="none" stroke="{SAGE_D}"/>
      <circle cx="262" cy="86" r="14" fill="{HONEY}"/>
      <rect x="226" y="116" width="40" height="58" rx="15" fill="{MIST}"/>
      <path d="M128 150 H178 V160 a25 20 0 0 1 -50 0 Z" fill="{WHITE}"/>
      <path d="M178 156 a10 10 0 0 1 0 18" fill="none"/>
      <rect x="58" y="172" width="284" height="26" rx="10" fill="{WOOD}"/>
      <rect x="84" y="198" width="18" height="110" rx="6" fill="{WOOD_D}"/>
      <rect x="298" y="198" width="18" height="110" rx="6" fill="{WOOD_D}"/>""",
    "dom_krzeslo": f"""
      <rect x="140" y="80" width="120" height="122" rx="20" fill="{WOOD}"/>
      <path d="M180 108 V178 M220 108 V178" fill="none"/>
      <rect x="138" y="225" width="16" height="88" rx="6" fill="{WOOD_D}"/>
      <rect x="246" y="225" width="16" height="88" rx="6" fill="{WOOD_D}"/>
      <rect x="118" y="196" width="164" height="32" rx="12" fill="{TERRA}"/>""",
    "dom_okno": f"""
      <rect x="92" y="78" width="216" height="216" rx="16" fill="{WHITE}"/>
      <rect x="110" y="96" width="180" height="180" rx="8" fill="{MIST_L}"/>
      <circle cx="152" cy="138" r="20" fill="{HONEY}" stroke="none"/>
      <path d="M220 236 a18 18 0 0 1 30 -16 a20 20 0 0 1 34 10 a14 14 0 0 1 -4 26 H226 a12 12 0 0 1 -6 -20 Z" fill="{WHITE}" stroke="none"/>
      <path d="M200 96 V276 M110 186 H290" fill="none"/>
      <path d="M78 70 H140 C122 130 122 220 140 290 H78 Z" fill="{TERRA_L}"/>
      <path d="M322 70 H260 C278 130 278 220 260 290 H322 Z" fill="{TERRA_L}"/>
      <rect x="70" y="290" width="260" height="22" rx="8" fill="{WOOD}"/>""",
    "dom_drzwi": f"""
      <ellipse cx="200" cy="318" rx="92" ry="14" fill="{TERRA_L}"/>
      <rect x="122" y="58" width="156" height="258" rx="14" fill="{WOOD}"/>
      <rect x="148" y="86" width="104" height="92" rx="10" fill="{WOOD_L}"/>
      <rect x="148" y="200" width="104" height="92" rx="10" fill="{WOOD_L}"/>
      <circle cx="252" cy="190" r="11" fill="{HONEY}"/>""",
    # ---------------- Zwierzęta ----------------
    "zwierzeta_kot": f"""
      <path d="M112 160 L122 68 L184 118 Z" fill="{HONEY}"/>
      <path d="M288 160 L278 68 L216 118 Z" fill="{HONEY}"/>
      <path d="M128 132 L132 96 L158 118 Z M272 132 L268 96 L242 118 Z" fill="{ROSE}" stroke="none"/>
      <circle cx="200" cy="205" r="100" fill="{HONEY}"/>
      <ellipse cx="165" cy="198" rx="10" ry="16" fill="{INK}" stroke="none"/>
      <ellipse cx="235" cy="198" rx="10" ry="16" fill="{INK}" stroke="none"/>
      <path d="M188 230 H212 L200 244 Z" fill="{ROSE_D}" stroke-width="5"/>
      <path d="M200 244 q-12 18 -28 8 M200 244 q12 18 28 8" fill="none" stroke-width="6"/>
      <path d="M140 236 H96 M140 250 L100 262 M260 236 H304 M260 250 L300 262" fill="none" stroke-width="5"/>""",
    "zwierzeta_pies": f"""
      <ellipse cx="200" cy="205" rx="92" ry="100" fill="{WOOD_L}"/>
      <ellipse cx="112" cy="196" rx="32" ry="70" fill="{WOOD_D}" transform="rotate(18 112 196)"/>
      <ellipse cx="288" cy="196" rx="32" ry="70" fill="{WOOD_D}" transform="rotate(-18 288 196)"/>
      <ellipse cx="236" cy="180" rx="26" ry="24" fill="{WOOD}" stroke="none"/>
      <circle cx="166" cy="182" r="10" fill="{INK}" stroke="none"/>
      <circle cx="236" cy="182" r="10" fill="{INK}" stroke="none"/>
      <ellipse cx="200" cy="248" rx="50" ry="38" fill="{CREAM}"/>
      <path d="M190 268 h20 v12 a10 10 0 0 1 -20 0 Z" fill="{ROSE_D}" stroke-width="5"/>
      <ellipse cx="200" cy="232" rx="17" ry="12" fill="{INK}" stroke="none"/>
      <path d="M200 244 V258 q-12 12 -24 2 M200 258 q12 12 24 2" fill="none" stroke-width="6"/>""",
    "zwierzeta_krowa": f"""
      <path d="M150 104 C130 96 124 74 132 62 M250 104 C270 96 276 74 268 62" fill="none" stroke="{HONEY_D}" stroke-width="14"/>
      <ellipse cx="106" cy="150" rx="38" ry="20" fill="{GREY}" transform="rotate(-15 106 150)"/>
      <ellipse cx="294" cy="150" rx="38" ry="20" fill="{GREY}" transform="rotate(15 294 150)"/>
      <rect x="122" y="92" width="156" height="196" rx="72" fill="{WHITE}"/>
      <path d="M150 104 C178 100 190 130 168 150 C150 166 128 150 124 132 C126 116 136 106 150 104 Z" fill="{GREY_D}" stroke="none"/>
      <circle cx="168" cy="178" r="10" fill="{INK}" stroke="none"/>
      <circle cx="232" cy="178" r="10" fill="{INK}" stroke="none"/>
      <ellipse cx="200" cy="248" rx="72" ry="44" fill="{ROSE}"/>
      <ellipse cx="178" cy="246" rx="9" ry="13" fill="{INK}" stroke="none"/>
      <ellipse cx="222" cy="246" rx="9" ry="13" fill="{INK}" stroke="none"/>""",
    "zwierzeta_kaczka": f"""
      <path d="M60 300 q20 -14 40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0" fill="none" stroke="{MIST_D}" stroke-width="7"/>
      <path d="M92 236 C92 192 140 178 196 186 C250 192 300 170 322 150 C330 200 312 280 220 288 C140 294 92 276 92 236 Z" fill="{WHITE}"/>
      <path d="M170 228 C200 210 250 212 276 232 C252 256 200 258 170 228 Z" fill="{GREY}"/>
      <circle cx="132" cy="150" r="48" fill="{SAGE}"/>
      <path d="M90 156 C70 152 56 160 54 170 C70 178 88 176 100 168 Z" fill="{TERRA}"/>
      <circle cx="138" cy="140" r="8" fill="{INK}" stroke="none"/>
      <path d="M100 190 C110 200 150 202 166 190" fill="none" stroke="{WHITE}" stroke-width="10"/>""",
    "zwierzeta_kon": f"""
      <path d="M150 112 L156 60 L188 96 Z M250 112 L244 60 L212 96 Z" fill="{WOOD}"/>
      <ellipse cx="200" cy="190" rx="72" ry="112" fill="{WOOD}"/>
      <path d="M176 92 C184 70 216 70 224 92 C214 108 186 108 176 92 Z" fill="{WOOD_D}"/>
      <path d="M200 120 C190 150 192 190 200 214 C208 190 210 150 200 120 Z" fill="{CREAM}" stroke="none"/>
      <circle cx="160" cy="168" r="10" fill="{INK}" stroke="none"/>
      <circle cx="240" cy="168" r="10" fill="{INK}" stroke="none"/>
      <ellipse cx="200" cy="262" rx="58" ry="42" fill="{WOOD_L}"/>
      <ellipse cx="180" cy="258" rx="8" ry="12" fill="{INK}" stroke="none"/>
      <ellipse cx="220" cy="258" rx="8" ry="12" fill="{INK}" stroke="none"/>""",
    "zwierzeta_swinka": f"""
      <path d="M118 140 L112 82 L170 112 Z M282 140 L288 82 L230 112 Z" fill="{ROSE_D}"/>
      <circle cx="200" cy="205" r="100" fill="{ROSE}"/>
      <circle cx="162" cy="182" r="10" fill="{INK}" stroke="none"/>
      <circle cx="238" cy="182" r="10" fill="{INK}" stroke="none"/>
      <circle cx="132" cy="226" r="14" fill="{ROSE_D}" stroke="none"/>
      <circle cx="268" cy="226" r="14" fill="{ROSE_D}" stroke="none"/>
      <ellipse cx="200" cy="232" rx="44" ry="31" fill="{ROSE_D}"/>
      <ellipse cx="185" cy="232" rx="7" ry="11" fill="{INK}" stroke="none"/>
      <ellipse cx="215" cy="232" rx="7" ry="11" fill="{INK}" stroke="none"/>
      <path d="M182 276 q18 12 36 0" fill="none" stroke-width="6"/>""",
    "zwierzeta_kura": f"""
      <path d="M180 296 V318 M220 296 V318 M170 318 H192 M210 318 H232" fill="none" stroke="{HONEY_D}" stroke-width="8"/>
      <path d="M282 172 C300 140 320 128 334 132 C330 150 318 166 300 180 M290 200 C312 186 330 186 340 194 C330 210 312 216 292 216" fill="{TERRA_L}"/>
      <ellipse cx="208" cy="226" rx="96" ry="76" fill="{CREAM}"/>
      <path d="M172 226 C200 206 246 208 262 232 C240 256 196 256 172 226 Z" fill="{HONEY}"/>
      <path d="M108 104 a14 14 0 0 1 24 -12 a14 14 0 0 1 26 4 a13 13 0 0 1 8 22 Z" fill="{TERRA}"/>
      <circle cx="136" cy="150" r="44" fill="{CREAM}"/>
      <path d="M96 146 L72 158 L98 168 Z" fill="{HONEY_D}"/>
      <path d="M102 172 C96 190 112 200 118 186 Z" fill="{TERRA}" stroke-width="6"/>
      <circle cx="128" cy="140" r="8" fill="{INK}" stroke="none"/>""",
    "zwierzeta_owca": f"""
      <g fill="{WHITE}">
        <circle cx="200" cy="112" r="46"/><circle cx="268" cy="140" r="46"/><circle cx="292" cy="208" r="46"/>
        <circle cx="262" cy="270" r="46"/><circle cx="200" cy="292" r="46"/><circle cx="138" cy="270" r="46"/>
        <circle cx="108" cy="208" r="46"/><circle cx="132" cy="140" r="46"/><circle cx="200" cy="205" r="100" stroke="none"/>
      </g>
      <ellipse cx="128" cy="198" rx="34" ry="16" fill="{GREY_D}" transform="rotate(20 128 198)"/>
      <ellipse cx="272" cy="198" rx="34" ry="16" fill="{GREY_D}" transform="rotate(-20 272 198)"/>
      <ellipse cx="200" cy="218" rx="54" ry="70" fill="{GREY_D}"/>
      <path d="M160 160 a20 20 0 0 1 26 -14 a20 20 0 0 1 28 0 a20 20 0 0 1 26 14 a16 16 0 0 1 -8 18 H168 a16 16 0 0 1 -8 -18 Z" fill="{WHITE}"/>
      <circle cx="180" cy="208" r="12" fill="{WHITE}" stroke="none"/><circle cx="220" cy="208" r="12" fill="{WHITE}" stroke="none"/>
      <circle cx="182" cy="210" r="6" fill="{INK}" stroke="none"/><circle cx="222" cy="210" r="6" fill="{INK}" stroke="none"/>
      <path d="M186 256 q14 10 28 0" fill="none" stroke="{WHITE}" stroke-width="6"/>""",
    # ---------------- Zabawki ----------------
    "zabawki_mis": f"""
      <circle cx="122" cy="112" r="38" fill="{WOOD}"/><circle cx="278" cy="112" r="38" fill="{WOOD}"/>
      <circle cx="122" cy="112" r="17" fill="{WOOD_L}" stroke="none"/><circle cx="278" cy="112" r="17" fill="{WOOD_L}" stroke="none"/>
      <circle cx="200" cy="188" r="96" fill="{WOOD}"/>
      <circle cx="164" cy="168" r="10" fill="{INK}" stroke="none"/><circle cx="236" cy="168" r="10" fill="{INK}" stroke="none"/>
      <ellipse cx="200" cy="222" rx="48" ry="38" fill="{WOOD_L}"/>
      <ellipse cx="200" cy="206" rx="17" ry="12" fill="{INK}" stroke="none"/>
      <path d="M200 218 V232 q-12 12 -24 2 M200 232 q12 12 24 2" fill="none" stroke-width="6"/>
      <path d="M200 296 L152 274 V318 Z M200 296 L248 274 V318 Z" fill="{TERRA}"/>
      <circle cx="200" cy="296" r="12" fill="{TERRA}"/>""",
    "zabawki_pilka": f"""
      <circle cx="200" cy="200" r="110" fill="{WHITE}"/>
      <path d="M200 90 A110 110 0 0 0 200 310 C138 270 138 130 200 90 Z" fill="{TERRA}"/>
      <path d="M200 90 A110 110 0 0 1 200 310 C262 270 262 130 200 90 Z" fill="{MIST}"/>
      <path d="M200 90 C170 140 170 260 200 310 C230 260 230 140 200 90 Z" fill="{HONEY}"/>
      <circle cx="200" cy="200" r="110" fill="none"/>
      <circle cx="200" cy="96" r="12" fill="{WHITE}"/>""",
    "zabawki_klocki": f"""
      <rect x="78" y="196" width="112" height="112" rx="14" fill="{SAGE}"/>
      <rect x="210" y="196" width="112" height="112" rx="14" fill="{HONEY}"/>
      <rect x="144" y="76" width="112" height="112" rx="14" fill="{TERRA}" transform="rotate(-6 200 132)"/>
      <circle cx="134" cy="252" r="26" fill="{WHITE}"/>
      <path d="M266 226 L294 278 H238 Z" fill="{WHITE}"/>
      <path d="M200 106 l9 19 21 3 -15 14 4 21 -19 -10 -19 10 4 -21 -15 -14 21 -3 Z" fill="{WHITE}" transform="rotate(-6 200 132)"/>""",
    "zabawki_auto": f"""
      <path d="M118 192 L148 132 H258 L294 192 Z" fill="{TERRA}"/>
      <path d="M162 146 H200 V188 H140 Z M214 146 H252 L276 188 H214 Z" fill="{MIST_L}" stroke-width="6"/>
      <rect x="58" y="186" width="284" height="74" rx="26" fill="{TERRA}"/>
      <circle cx="320" cy="214" r="10" fill="{HONEY}" stroke-width="5"/>
      <circle cx="128" cy="262" r="34" fill="{INK}" stroke="none"/><circle cx="128" cy="262" r="14" fill="{GREY}" stroke="none"/>
      <circle cx="272" cy="262" r="34" fill="{INK}" stroke="none"/><circle cx="272" cy="262" r="14" fill="{GREY}" stroke="none"/>""",
    "zabawki_lalka": f"""
      <circle cx="132" cy="138" r="26" fill="{WOOD_D}"/><circle cx="268" cy="138" r="26" fill="{WOOD_D}"/>
      <path d="M160 222 L116 266 M240 222 L284 266" fill="none" stroke-width="30"/>
      <path d="M160 222 L116 266 M240 222 L284 266" fill="none" stroke="{SKIN}" stroke-width="16"/>
      <path d="M150 208 L104 300 H296 L250 208 Z" fill="{ROSE}"/>
      <circle cx="200" cy="150" r="60" fill="{SKIN}"/>
      <path d="M140 150 C138 104 170 86 200 88 C232 86 262 104 260 150 C246 124 222 112 200 120 C178 112 154 124 140 150 Z" fill="{WOOD_D}"/>
      <circle cx="178" cy="156" r="8" fill="{INK}" stroke="none"/><circle cx="222" cy="156" r="8" fill="{INK}" stroke="none"/>
      <circle cx="164" cy="176" r="10" fill="{ROSE_D}" stroke="none"/><circle cx="236" cy="176" r="10" fill="{ROSE_D}" stroke="none"/>
      <path d="M186 182 q14 12 28 0" fill="none" stroke-width="6"/>""",
    "zabawki_bebenek": f"""
      <path d="M150 140 L96 70 M250 140 L304 70" fill="none" stroke="{WOOD_D}" stroke-width="12"/>
      <circle cx="96" cy="70" r="14" fill="{HONEY}"/><circle cx="304" cy="70" r="14" fill="{HONEY}"/>
      <path d="M96 156 V270 a104 30 0 0 0 208 0 V156 Z" fill="{TERRA}"/>
      <path d="M96 176 L130 286 L164 176 L200 300 L236 176 L270 286 L304 176" fill="none" stroke="{CREAM}" stroke-width="7"/>
      <path d="M96 176 V270 a104 30 0 0 0 208 0 V176" fill="none"/>
      <ellipse cx="200" cy="156" rx="104" ry="30" fill="{CREAM}"/>""",
    "zabawki_rakieta": f"""
      <path d="M118 90 l4 10 10 4 -10 4 -4 10 -4 -10 -10 -4 10 -4 Z M292 150 l3 8 8 3 -8 3 -3 8 -3 -8 -8 -3 8 -3 Z" fill="{HONEY}" stroke="none"/>
      <path d="M176 268 C176 300 200 322 200 322 C200 322 224 300 224 268 Z" fill="{HONEY}"/>
      <path d="M160 214 L118 262 L160 266 Z M240 214 L282 262 L240 266 Z" fill="{TERRA}"/>
      <path d="M200 64 C246 104 254 190 242 272 H158 C146 190 154 104 200 64 Z" fill="{WHITE}"/>
      <path d="M200 64 C220 80 232 98 238 118 H162 C168 98 180 80 200 64 Z" fill="{TERRA}"/>
      <circle cx="200" cy="170" r="26" fill="{MIST}"/>""",
    "zabawki_kaczuszka": f"""
      <path d="M60 304 q20 -14 40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0" fill="none" stroke="{MIST_D}" stroke-width="7"/>
      <path d="M96 236 C96 196 150 196 190 200 C250 204 292 182 312 170 C324 214 312 290 210 292 C138 294 96 276 96 236 Z" fill="{HONEY}"/>
      <path d="M176 232 C206 216 248 220 268 240 C244 260 200 260 176 232 Z" fill="{HONEY_D}"/>
      <circle cx="150" cy="150" r="52" fill="{HONEY}"/>
      <path d="M104 150 C84 144 66 152 64 164 C82 174 100 172 112 162 Z" fill="{TERRA}"/>
      <circle cx="158" cy="138" r="9" fill="{INK}" stroke="none"/>""",
    # ---------------- Pojazdy ----------------
    "pojazdy_koparka": f"""
      {ROAD}
      <rect x="62" y="262" width="186" height="46" rx="23" fill="{TYRE}"/>
      {wheel(90, 285, 15)}{wheel(155, 285, 15)}{wheel(220, 285, 15)}
      <rect x="84" y="200" width="44" height="58" rx="8" fill="{HONEY_D}"/>
      <rect x="118" y="168" width="112" height="94" rx="12" fill="{HONEY}"/>
      <rect x="172" y="182" width="44" height="42" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <path d="M206 212 L286 112 L306 128 L226 228 Z" fill="{HONEY}"/>
      <path d="M286 112 L344 196 L322 208 L272 130 Z" fill="{HONEY}"/>
      <path d="M318 196 L364 202 L352 250 L308 234 Z" fill="{GREY_D}"/>""",
    "pojazdy_traktor": f"""
      {ROAD}
      <rect x="258" y="150" width="14" height="52" rx="4" fill="{GREY_D}"/>
      <rect x="168" y="196" width="150" height="66" rx="12" fill="{SAGE}"/>
      <rect x="96" y="120" width="98" height="140" rx="12" fill="{SAGE_D}"/>
      <rect x="112" y="138" width="66" height="58" rx="6" fill="{MIST_L}" stroke-width="6"/>
      {wheel(136, 262, 54, HONEY)}{wheel(286, 280, 34, HONEY)}""",
    "pojazdy_kombajn": f"""
      {ROAD}
      <path d="M296 168 L356 116" fill="none" stroke-width="28"/>
      <path d="M296 168 L356 116" fill="none" stroke="{TERRA}" stroke-width="14"/>
      <rect x="150" y="146" width="176" height="122" rx="14" fill="{TERRA}"/>
      <rect x="106" y="106" width="78" height="78" rx="10" fill="{TERRA}"/>
      <rect x="120" y="120" width="50" height="44" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <rect x="36" y="236" width="104" height="40" rx="8" fill="{HONEY_D}"/>
      <path d="M52 246 V266 M72 246 V266 M92 246 V266 M112 246 V266" fill="none" stroke-width="5"/>
      <circle cx="86" cy="214" r="30" fill="{HONEY}"/>
      <path d="M86 184 V244 M56 214 H116" fill="none" stroke-width="5"/>
      {wheel(200, 270, 46)}{wheel(298, 284, 30)}""",
    "pojazdy_samochod": f"""
      {ROAD}
      <path d="M70 262 V222 C70 206 82 196 98 194 L136 190 L170 148 C176 140 186 136 196 136 H268 C280 136 290 142 296 152 L322 192 C344 196 356 208 356 228 V262 Z" fill="{MIST}"/>
      <path d="M184 154 H236 V192 H156 Z M252 154 H270 C276 154 280 158 284 164 L300 192 H252 Z" fill="{MIST_L}" stroke-width="6"/>
      <circle cx="340" cy="222" r="8" fill="{HONEY}" stroke-width="4"/>
      {wheel(126, 268, 34)}{wheel(296, 268, 34)}""",
    "pojazdy_autobus": f"""
      {ROAD}
      <rect x="36" y="134" width="328" height="138" rx="22" fill="{HONEY}"/>
      <rect x="58" y="154" width="54" height="50" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <rect x="126" y="154" width="54" height="50" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <rect x="194" y="154" width="54" height="50" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <rect x="276" y="154" width="64" height="98" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <path d="M308 154 V252" fill="none" stroke-width="5"/>
      <path d="M36 228 H262" fill="none" stroke="{HONEY_D}" stroke-width="10"/>
      {wheel(98, 276, 32)}{wheel(296, 276, 32)}""",
    "pojazdy_straz": f"""
      {ROAD}
      <rect x="60" y="150" width="198" height="18" rx="6" fill="{WHITE}"/>
      <path d="M84 150 V168 M114 150 V168 M144 150 V168 M174 150 V168 M204 150 V168 M234 150 V168" fill="none" stroke-width="5"/>
      <rect x="40" y="176" width="232" height="92" rx="10" fill="{TERRA}"/>
      <rect x="264" y="140" width="96" height="128" rx="14" fill="{TERRA}"/>
      <rect x="282" y="156" width="60" height="50" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <rect x="292" y="120" width="34" height="20" rx="6" fill="{MIST}"/>
      <path d="M56 212 H250" fill="none" stroke="{WHITE}" stroke-width="10"/>
      {wheel(96, 272, 32)}{wheel(212, 272, 32)}{wheel(314, 272, 32)}""",
    "pojazdy_wywrotka": f"""
      {ROAD}
      <rect x="54" y="248" width="296" height="20" rx="6" fill="{GREY_D}"/>
      <path d="M42 156 H262 V250 H78 Z" fill="{HONEY}"/>
      <path d="M70 182 H246" fill="none" stroke="{HONEY_D}" stroke-width="10"/>
      <rect x="268" y="150" width="88" height="104" rx="14" fill="{MIST}"/>
      <rect x="286" y="166" width="52" height="44" rx="6" fill="{MIST_L}" stroke-width="6"/>
      {wheel(96, 272, 30)}{wheel(170, 272, 30)}{wheel(310, 272, 30)}""",
    "pojazdy_pociag": f"""
      {ROAD}
      <circle cx="136" cy="112" r="16" fill="{WHITE}" stroke="{GREY_D}" stroke-width="5"/>
      <circle cx="166" cy="92" r="11" fill="{WHITE}" stroke="{GREY_D}" stroke-width="5"/>
      <rect x="112" y="136" width="30" height="52" rx="5" fill="{TYRE}"/>
      <rect x="76" y="182" width="172" height="80" rx="36" fill="{SAGE}"/>
      <rect x="236" y="128" width="98" height="134" rx="12" fill="{SAGE_D}"/>
      <rect x="254" y="146" width="62" height="50" rx="6" fill="{MIST_L}" stroke-width="6"/>
      <rect x="228" y="118" width="114" height="18" rx="6" fill="{TERRA}"/>
      <path d="M76 250 L50 288 H92" fill="{GREY}"/>
      {wheel(126, 278, 26, HONEY)}{wheel(196, 278, 26, HONEY)}{wheel(290, 274, 34, HONEY)}""",
}


def svg(name: str, body: str) -> str:
    bg, bg2 = BACKGROUNDS[name.split("_")[0]]
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400" width="800" height="800">'
        f'<rect width="400" height="400" fill="{bg}"/>'
        f'<circle cx="200" cy="196" r="156" fill="{bg2}"/>'
        f'<ellipse cx="200" cy="318" rx="128" ry="12" fill="{INK}" opacity="0.06"/>'
        f'<g stroke="{INK}" stroke-width="8" stroke-linejoin="round" stroke-linecap="round">{body}</g>'
        "</svg>"
    )


def find_chrome() -> str:
    for p in (r"C:\Program Files\Google\Chrome\Application\chrome.exe",
              r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
              "/usr/bin/google-chrome", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"):
        if Path(p).exists():
            return p
    sys.exit("Google Chrome not found")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("work", type=Path, help="directory for intermediate SVG/PNG files and the preview sheet")
    ap.add_argument("--assets", type=Path, help="write WebP files here (e.g. app/src/main/assets/builtin)")
    args = ap.parse_args()
    from PIL import Image

    args.work.mkdir(parents=True, exist_ok=True)
    chrome = find_chrome()
    pngs = []
    for name, body in ART.items():
        svg_path = args.work / f"{name}.svg"
        png_path = args.work / f"{name}.png"
        svg_path.write_text(svg(name, body), encoding="utf-8")
        subprocess.run(
            [chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--screenshot={png_path.resolve()}",
             "--window-size=800,800", "--force-device-scale-factor=1", svg_path.resolve().as_uri()],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        pngs.append(png_path)

    sheet = Image.new("RGB", (8 * 200, 3 * 200), "white")
    for i, png in enumerate(pngs):
        sheet.paste(Image.open(png).convert("RGB").resize((200, 200), Image.LANCZOS), ((i % 8) * 200, (i // 8) * 200))
    sheet.save(args.work / "_sheet.png")

    if args.assets:
        (args.assets / "thumbs").mkdir(parents=True, exist_ok=True)
        for png in pngs:
            img = Image.open(png).convert("RGB")
            img.save(args.assets / f"{png.stem}.webp", "WEBP", quality=85, method=6)
            img.resize((400, 400), Image.LANCZOS).save(args.assets / "thumbs" / f"{png.stem}.webp", "WEBP", quality=80, method=6)
    print(f"{len(pngs)} illustrations, sheet: {args.work / '_sheet.png'}")


if __name__ == "__main__":
    main()
