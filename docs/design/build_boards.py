#!/usr/bin/env python3
"""Builds the RKB CLIENT 0.9.0 UI design boards as SVG (then rendered with rsvg-convert).

These are design boards, not screenshots. All geometry is in a 1280x720 concept canvas; the
implementation spec converts it to Minecraft GUI pixels with the responsive rules in the brief.
"""
import base64
import os
from html import escape

W, H = 1280, 720
SIDEBAR = 216
CX = 244
CR = 1256
FOOT_Y = 688

BG = "#101216"
BG2 = "#171A20"
PANEL = "#1C2028"
PANEL2 = "#222733"
PANEL3 = "#1F2530"
BORDER = "#292E38"
BORDER2 = "#343B47"
TEXT = "#F0F1F3"
MUTED = "#A0A6B0"
DIM = "#6F7682"
ACCENT = "#3988D8"
ACCENT2 = "#4B98E6"
SUCCESS = "#4E9E6A"
WARN = "#C9A24B"
DANGER = "#C05A5A"
DANGER_BG = "#2A1A1D"
BLACK = "#0B0D10"

HERE = os.path.dirname(os.path.abspath(__file__))
LOGO = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "rkbclient", "textures", "gui", "rkb_logo.png")
with open(LOGO, "rb") as f:
    LOGO_URI = "data:image/png;base64," + base64.b64encode(f.read()).decode()


def t(x, y, s, size=10, fill=TEXT, weight="normal", anchor="start", ls=0):
    return (f'<text x="{x}" y="{y}" font-family="DejaVu Sans, sans-serif" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}" text-anchor="{anchor}" letter-spacing="{ls}">'
            f'{escape(s)}</text>')


def r(x, y, w, h, fill="none", stroke="none", rx=0, sw=1, opacity=1):
    st = f' stroke="{stroke}" stroke-width="{sw}"' if stroke != "none" else ""
    op = f' opacity="{opacity}"' if opacity != 1 else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}"{st}{op}/>'


def line(x1, y1, x2, y2, color=BORDER, sw=1, opacity=1):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{sw}" opacity="{opacity}"/>')


def logo(x, y, size):
    return (f'<image x="{x}" y="{y}" width="{size}" height="{size}" '
            f'xlink:href="{LOGO_URI}" href="{LOGO_URI}"/>')


def wordmark(cx, y, size, tracking=0):
    """Centered two-tone RKB CLIENT lockup."""
    return (f'<text x="{cx}" y="{y}" font-family="DejaVu Sans, sans-serif" font-size="{size}" '
            f'font-weight="bold" text-anchor="middle" letter-spacing="{tracking}">'
            f'<tspan fill="{TEXT}">RKB</tspan><tspan fill="{ACCENT}" dx="10">CLIENT</tspan></text>')


def dot(x, y, color, size=6):
    return f'<circle cx="{x}" cy="{y}" r="{size/2}" fill="{color}"/>'


def icon(name, x, y, color=MUTED, s=16):
    """Simple 1.5px line icons on a 16px grid."""
    c = color
    p = f'fill="none" stroke="{c}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"'
    if name == "play":
        return f'<path d="M{x+5} {y+3} L{x+13} {y+8} L{x+5} {y+13} Z" fill="{c}" stroke="none"/>'
    if name == "users":
        return (f'<circle cx="{x+6}" cy="{y+5.5}" r="2.6" {p}/>'
                f'<path d="M{x+1.5} {y+13} c0-3 2-4.6 4.5-4.6 s4.5 1.6 4.5 4.6" {p}/>'
                f'<circle cx="{x+11.5}" cy="{y+6.5}" r="2" {p}/>'
                f'<path d="M{x+10.5} {y+12.8} c0-2.2 1.4-3.4 3.4-3.4 1 0 1.9.3 2.4.9" {p}/>')
    if name == "cube":
        return (f'<path d="M{x+8} {y+2} L{x+14} {y+5.5} L{x+14} {y+11} L{x+8} {y+14} L{x+2} {y+11} '
                f'L{x+2} {y+5.5} Z" {p}/><path d="M{x+2} {y+5.5} L{x+8} {y+9} L{x+14} {y+5.5} '
                f'M{x+8} {y+9} L{x+8} {y+14}" {p}/>')
    if name == "shirt":
        return (f'<path d="M{x+5} {y+3} L{x+2.5} {y+5} L{x+1.5} {y+8} L{x+4} {y+9.5} L{x+4} {y+14} '
                f'L{x+12} {y+14} L{x+12} {y+9.5} L{x+14.5} {y+8} L{x+13.5} {y+5} L{x+11} {y+3} '
                f'C{x+10} {y+5} {x+6} {y+5} {x+5} {y+3} Z" {p}/>')
    if name == "grid":
        return (f'<rect x="{x+2}" y="{y+2}" width="5" height="5" rx="1" {p}/>'
                f'<rect x="{x+9}" y="{y+2}" width="5" height="5" rx="1" {p}/>'
                f'<rect x="{x+2}" y="{y+9}" width="5" height="5" rx="1" {p}/>'
                f'<rect x="{x+9}" y="{y+9}" width="5" height="5" rx="1" {p}/>')
    if name == "sliders":
        return (f'<path d="M{x+2} {y+4} H{x+14} M{x+2} {y+8} H{x+14} M{x+2} {y+12} H{x+14}" {p}/>'
                f'<circle cx="{x+6}" cy="{y+4}" r="1.8" fill="{BG}" stroke="{c}" stroke-width="1.5"/>'
                f'<circle cx="{x+11}" cy="{y+8}" r="1.8" fill="{BG}" stroke="{c}" stroke-width="1.5"/>'
                f'<circle cx="{x+5}" cy="{y+12}" r="1.8" fill="{BG}" stroke="{c}" stroke-width="1.5"/>')
    if name == "gear":
        teeth = "".join(
            f'<rect x="{x+6.7}" y="{y+0.5}" width="2.6" height="2.8" rx="0.7" fill="{c}" '
            f'transform="rotate({i*45} {x+8} {y+8})"/>' for i in range(8))
        return (f'<circle cx="{x+8}" cy="{y+8}" r="5.1" fill="none" stroke="{c}" stroke-width="1.5"/>'
                f'<circle cx="{x+8}" cy="{y+8}" r="1.9" fill="none" stroke="{c}" stroke-width="1.5"/>'
                + teeth)
    if name == "globe":
        return (f'<circle cx="{x+8}" cy="{y+8}" r="6" {p}/><path d="M{x+2} {y+8} H{x+14}" {p}/>'
                f'<path d="M{x+8} {y+2} c2.4 2 2.4 10 0 12 c-2.4-2-2.4-10 0-12 Z" {p}/>')
    if name == "power":
        return (f'<path d="M{x+8} {y+2} v6" {p}/>'
                f'<path d="M{x+4.5} {y+4.2} a5.4 5.4 0 1 0 7 0" {p}/>')
    if name == "search":
        return (f'<circle cx="{x+7}" cy="{y+7}" r="4.2" {p}/><path d="M{x+10.2} {y+10.2} L{x+14} {y+14}" {p}/>')
    if name == "upload":
        return (f'<path d="M{x+8} {y+10} V{y+2} M{x+5} {y+5} L{x+8} {y+2} L{x+11} {y+5}" {p}/>'
                f'<path d="M{x+2.5} {y+9} v3.5 h11 V{y+9}" {p}/>')
    if name == "link":
        return (f'<path d="M{x+6.5} {y+9.5} l3-3" {p}/>'
                f'<path d="M{x+5.5} {y+11.5} l-1.6 1.6 a2.8 2.8 0 0 1-4-4 l2.4-2.4 a2.8 2.8 0 0 1 4 0" {p}/>'
                f'<path d="M{x+10.5} {y+4.5} l1.6-1.6 a2.8 2.8 0 0 1 4 4 l-2.4 2.4 a2.8 2.8 0 0 1-4 0" {p}/>')
    if name == "refresh":
        return (f'<path d="M{x+13} {y+8} a5 5 0 1 1-1.6-3.7" {p}/><path d="M{x+13} {y+2.5} V{y+5.5} H{x+10}" {p}/>')
    if name == "check":
        return f'<path d="M{x+3} {y+8.5} L{x+6.5} {y+12} L{x+13} {y+4}" {p}/>'
    if name == "x":
        return f'<path d="M{x+4} {y+4} L{x+12} {y+12} M{x+12} {y+4} L{x+4} {y+12}" {p}/>'
    if name == "chevron":
        return f'<path d="M{x+5} {y+6} L{x+8} {y+9.5} L{x+11} {y+6}" {p}/>'
    if name == "back":
        return f'<path d="M{x+10} {y+3} L{x+5} {y+8} L{x+10} {y+13}" {p}/>'
    if name == "heart":
        return (f'<path d="M{x+8} {y+13} C{x+3} {y+9.5} {x+2} {y+6} {x+4.2} {y+4.2} '
                f'C{x+5.8} {y+2.9} {x+7.4} {y+3.6} {x+8} {y+4.8} '
                f'C{x+8.6} {y+3.6} {x+10.2} {y+2.9} {x+11.8} {y+4.2} '
                f'C{x+14} {y+6} {x+13} {y+9.5} {x+8} {y+13} Z" {p}/>')
    if name == "trash":
        return (f'<path d="M{x+3} {y+4.5} H{x+13} M{x+6} {y+4.5} V{y+3} h4 v1.5" {p}/>'
                f'<path d="M{x+4.5} {y+4.5} l.8 8.5 h5.4 l.8-8.5" {p}/>')
    return ""


def button(x, y, w, h, label, kind="secondary", ic=None, size=10, rx=6):
    if kind == "primary":
        fill, stroke, tc, icc = ACCENT, ACCENT, "#FFFFFF", "#FFFFFF"
    elif kind == "danger":
        fill, stroke, tc, icc = DANGER_BG, "#3A2428", DANGER, DANGER
    elif kind == "ghost":
        fill, stroke, tc, icc = "none", "none", MUTED, MUTED
    elif kind == "disabled":
        fill, stroke, tc, icc = "#232833", BORDER, DIM, DIM
    else:
        fill, stroke, tc, icc = PANEL, BORDER, TEXT, MUTED
    out = r(x, y, w, h, fill, stroke, rx)
    if ic:
        out += icon(ic, x + 11, y + (h - 16) / 2, icc)
        out += t(x + 33, y + h / 2 + size * 0.35, label, size, tc, "bold")
    else:
        out += t(x + w / 2, y + h / 2 + size * 0.35, label, size, tc, "bold", "middle")
    return out


def chip(x, y, label, active=False, h=24, pad=10, size=8):
    w = max(40, len(label) * size * 0.62 + pad * 2)
    if active:
        return r(x, y, w, h, ACCENT, ACCENT, 4) + t(x + w / 2, y + h / 2 + 3, label, size, "#FFFFFF", "bold", "middle")
    return r(x, y, w, h, "none", BORDER, 4) + t(x + w / 2, y + h / 2 + 3, label, size, MUTED, "normal", "middle")


def toggle(x, y, on, w=28, h=14):
    track = ACCENT if on else "#2B303B"
    kx = x + w - 7 if on else x + 7
    return (r(x, y, w, h, track, BORDER, h / 2) +
            f'<circle cx="{kx}" cy="{y + h/2}" r="4.5" fill="{TEXT}"/>')


def field(x, y, w, h, placeholder, value=None, label=None):
    out = ""
    if label:
        out += t(x, y - 7, label, 7.5, MUTED, "bold", "start", 0.8)
    out += r(x, y, w, h, BG2, BORDER, 4)
    out += t(x + 10, y + h / 2 + 3.4, value if value is not None else placeholder,
            9, TEXT if value is not None else DIM)
    return out


def dropdown(x, y, w, value, h=24):
    return (r(x, y, w, h, BG2, BORDER, 4) + t(x + 9, y + h / 2 + 3.2, value, 8.5, TEXT) +
            icon("chevron", x + w - 17, y + (h - 16) / 2, MUTED, 14))


def keybind(x, y, w, value, h=24):
    return (r(x, y, w, h, BG2, BORDER, 4) +
            t(x + w / 2, y + h / 2 + 3.2, value, 7.5, TEXT, "bold", "middle", 0.4))


def head(x, y, s):
    """8x8 Steve face."""
    u = s / 8
    out = r(x, y, s, s, "#C99A6B")
    out += r(x, y, s, u * 2, "#3B2A1E")
    out += r(x, y + u * 2, u, u * 2, "#3B2A1E")
    out += r(x + u * 7, y + u * 2, u, u * 2, "#3B2A1E")
    out += r(x + u, y + u * 3, u, u, "#FFFFFF")
    out += r(x + u * 2, y + u * 3, u, u, "#3B6EA5")
    out += r(x + u * 5, y + u * 3, u, u, "#3B6EA5")
    out += r(x + u * 6, y + u * 3, u, u, "#FFFFFF")
    out += r(x + u * 3, y + u * 5, u * 2, u, "#A9714B")
    out += r(x + u * 3, y + u * 6, u * 2, u * 0.7, "#6B3F2A")
    return out


def head_back(x, y, s):
    """Back of the head: hair, no face."""
    u = s / 8
    out = r(x, y, s, s, "#3B2A1E")
    out += r(x, y, s, u * 0.8, "#2E2118")
    out += r(x, y + u * 6.6, s, u * 1.4, "#C99A6B")
    return out


def steve(cx, top, h):
    """Front-facing blocky Steve, total height h (32 units)."""
    u = h / 32.0
    x = cx - 8 * u
    out = ""
    # legs and shoes
    out += r(x + 4 * u, top + 20 * u, 3.5 * u, 10 * u, "#3B3B8F")
    out += r(x + 8.5 * u, top + 20 * u, 3.5 * u, 10 * u, "#3B3B8F")
    out += r(x + 4 * u, top + 30 * u, 3.5 * u, 2 * u, "#5A5A5A")
    out += r(x + 8.5 * u, top + 30 * u, 3.5 * u, 2 * u, "#5A5A5A")
    # arms
    out += r(x + 1 * u, top + 8 * u, 3 * u, 12 * u, "#C99A6B")
    out += r(x + 12 * u, top + 8 * u, 3 * u, 12 * u, "#C99A6B")
    # torso
    out += r(x + 4 * u, top + 8 * u, 8 * u, 12 * u, "#00AFAF")
    out += r(x + 4 * u, top + 8 * u, 8 * u, 2 * u, "#00C4C4")
    out += line(x + 4 * u, top + 13 * u, x + 12 * u, top + 13 * u, "#0E9A9A", 1, 0.6)
    # head, centered on the torso
    out += head(x + 4 * u, top, 8 * u)
    return out


def steve_back(cx, top, h):
    """Back view with a navy RKB cape."""
    u = h / 32.0
    x = cx - 8 * u
    out = ""
    # legs and shoes
    out += r(x + 4 * u, top + 20 * u, 3.5 * u, 10 * u, "#33337F")
    out += r(x + 8.5 * u, top + 20 * u, 3.5 * u, 10 * u, "#33337F")
    out += r(x + 4 * u, top + 30 * u, 3.5 * u, 2 * u, "#4E4E4E")
    out += r(x + 8.5 * u, top + 30 * u, 3.5 * u, 2 * u, "#4E4E4E")
    # arms
    out += r(x + 1 * u, top + 8 * u, 3 * u, 12 * u, "#B98B60")
    out += r(x + 12 * u, top + 8 * u, 3 * u, 12 * u, "#B98B60")
    # torso
    out += r(x + 4 * u, top + 8 * u, 8 * u, 12 * u, "#008C8C")
    # head
    out += head_back(x + 4 * u, top, 8 * u)
    # cape hangs over the back
    out += (f'<path d="M{x + 3 * u} {top + 8.2 * u} L{x + 13 * u} {top + 8.2 * u} '
            f'L{x + 14 * u} {top + 25 * u} L{x + 2 * u} {top + 25 * u} Z" '
            f'fill="#1E2E4E" stroke="#2E4166" stroke-width="1"/>')
    out += f'<path d="M{x + 5 * u} {top + 12 * u} h6 v6 h-6 Z" fill="#24406E" opacity="0.9"/>'
    out += t(cx, top + 17.2 * u, "R", 6.6 * u, "#4FD8FF", "bold", "middle")
    return out


def cape_swatch(x, y, w, h, colors, pattern="plain"):
    out = r(x, y, w, h, colors[0], BORDER, 2)
    if pattern == "bd":
        out += f'<circle cx="{x + w/2}" cy="{y + h/2}" r="{h*0.24}" fill="{colors[1]}"/>'
    elif pattern == "in":
        out += r(x, y, w, h / 3, colors[1])
        out += r(x, y + h * 2 / 3, w, h / 3, colors[2])
        out += f'<circle cx="{x + w/2}" cy="{y + h/2}" r="{h*0.16}" fill="{colors[3]}"/>'
    elif pattern == "us":
        out += r(x, y, w, h * 0.4, colors[1])
        for i in range(4):
            out += r(x + i * w / 4, y + h * 0.55, w / 8, h * 0.12, colors[1])
        out += r(x, y, w * 0.42, h * 0.4, "#2C3E6B")
    elif pattern == "br":
        out += f'<path d="M{x + w/2} {y + h*0.18} L{x + w*0.84} {y + h/2} L{x + w/2} {y + h*0.82} L{x + w*0.16} {y + h/2} Z" fill="{colors[1]}"/>'
        out += f'<circle cx="{x + w/2}" cy="{y + h/2}" r="{h*0.16}" fill="{colors[2]}"/>'
    elif pattern == "jp":
        out += f'<circle cx="{x + w/2}" cy="{y + h/2}" r="{h*0.28}" fill="{colors[1]}"/>'
    elif pattern == "np":
        out += f'<path d="M{x + w*0.28} {y + h*0.12} L{x + w*0.72} {y + h*0.4} L{x + w*0.28} {y + h*0.4} Z" fill="{colors[1]}"/>'
        out += f'<path d="M{x + w*0.28} {y + h*0.44} L{x + w*0.66} {y + h*0.76} L{x + w*0.28} {y + h*0.76} Z" fill="{colors[1]}"/>'
    elif pattern == "pk":
        out += r(x, y, w * 0.25, h, colors[1])
        out += f'<circle cx="{x + w*0.66}" cy="{y + h/2}" r="{h*0.2}" fill="{colors[2]}"/>'
    elif pattern == "uk":
        out += line(x, y + h / 2, x + w, y + h / 2, colors[1], h * 0.18)
        out += line(x + w / 2, y, x + w / 2, y + h, colors[1], w * 0.16)
        out += line(x, y, x + w, y + h, colors[2], 2)
        out += line(x + w, y, x, y + h, colors[2], 2)
    elif pattern == "rkb":
        out += t(x + w / 2, y + h * 0.72, "R", h * 0.7, "#4FD8FF", "bold", "middle")
    return out


def shell(active=None, account_state="signed-out", page_title="", subtitle=""):
    out = r(0, 0, W, H, BG)
    out += r(0, 0, SIDEBAR, H, BG2)
    out += line(SIDEBAR, 0, SIDEBAR, H, BORDER)
    out += logo(18, 18, 24)
    out += t(50, 34, "RKB", 13, TEXT, "bold")
    out += t(84, 34, "CLIENT", 13, ACCENT, "bold")
    out += t(50, 46, "MINECRAFT PVP", 6.5, DIM, "bold", "start", 0.8)

    groups = [
        ("MINECRAFT", [("Singleplayer", "play"), ("Multiplayer", "users"), ("Mods", "cube"),
                       ("Settings", "globe")]),
        ("RKB", [("Cosmetics", "shirt"), ("RKB Modules", "grid"), ("RKB Settings", "sliders")]),
        ("SYSTEM", [("Quit Game", "power")]),
    ]
    y = 70
    for label, items in groups:
        out += t(18, y, label, 6.8, DIM, "bold", "start", 1.0)
        y += 10
        for name, ic in items:
            act = name == active
            if act:
                out += r(10, y, SIDEBAR - 20, 28, PANEL2, "none", 4)
                out += r(10, y + 3, 3, 22, ACCENT, "none", 2)
            out += icon(ic, 20, y + 6, ACCENT if act else MUTED, 16)
            out += t(44, y + 18, name, 9.5, TEXT if act else MUTED, "bold" if act else "normal")
            y += 32
        y += 10

    # sidebar account card
    out += r(16, 590, SIDEBAR - 32, 66, PANEL, BORDER, 6)
    out += dot(28, 606, ACCENT if account_state != "signed-out" else DIM, 6)
    out += t(38, 609, "RKB CLOUD", 6.8, DIM, "bold", "start", 0.8)
    out += t(28, 624, "Signed in" if account_state != "signed-out" else "Signed out", 9.5, TEXT, "bold")
    out += t(28, 638, "RKB_GAMERZ" if account_state != "signed-out" else "Local mode", 8, MUTED)
    out += t(28, 650, "Manage account", 7.8, ACCENT)

    # header
    out += t(CX, 44, page_title, 17, TEXT, "bold")
    out += t(CX, 60, subtitle, 8.5, MUTED)
    out += account_chip(account_state)
    out += line(SIDEBAR, FOOT_Y, W, FOOT_Y, "#1F242D")
    out += t(CX, 704, "RKB CLIENT 0.9.0  ·  Minecraft 1.21.11", 8, DIM)
    status = "FPS 120  ·  RKB Cloud" if account_state != "signed-out" else "FPS 120  ·  Local mode"
    out += t(CR, 704, status, 8, DIM, "normal", "end")
    return out


def account_chip(state="signed-out", x=1016, y=16, w=240):
    out = r(x, y, w, 34, PANEL, BORDER, 6)
    out += head(x + 8, y + 7, 20)
    out += t(x + 34, y + 17, "RKB_GAMERZ", 9.5, TEXT, "bold")
    out += t(x + 34, y + 28, "Local mode" if state == "signed-out" else "RKB Cloud", 7, DIM)
    out += icon("chevron", x + 104, y + 9, MUTED, 16)
    out += dot(x + 126, y + 17, ACCENT if state != "signed-out" else DIM, 5)
    out += line(x + 138, y + 8, x + 138, y + 26, BORDER)
    out += icon("shirt", x + 146, y + 9, MUTED, 16)
    out += icon("gear", x + 172, y + 9, MUTED, 16)
    out += icon("power", x + 198, y + 9, MUTED, 16)
    return out


def main_menu():
    out = r(0, 0, W, H, BG)
    out += account_chip("signed-out", 1016, 18, 240)

    # brand
    out += logo(604, 112, 72)
    out += wordmark(640, 246, 34)
    out += t(640, 268, "MINECRAFT PVP CLIENT  ·  1.21.11", 8, MUTED, "bold", "middle", 1.6)

    col_w = 238
    gap = 14
    x1 = 640 - col_w - gap / 2
    x2 = 640 + gap / 2
    out += t(x1, 302, "MINECRAFT", 7, DIM, "bold", "start", 1.0)
    out += t(x2, 302, "RKB", 7, DIM, "bold", "start", 1.0)

    rows = [
        (x1, "Singleplayer", "play", "primary"),
        (x1, "Multiplayer", "users", "secondary"),
        (x1, "Mods", "cube", "secondary"),
        (x1, "Settings", "globe", "secondary"),
        (x2, "Cosmetics", "shirt", "secondary"),
        (x2, "RKB Modules", "grid", "secondary"),
        (x2, "RKB Settings", "sliders", "secondary"),
    ]
    for i, (x, label, ic, kind) in enumerate(rows):
        y = 314 + (i % 4) * 42
        out += button(x, y, col_w, 34, label, kind, ic, 9.5)

    out += button(640 - (col_w * 2 + gap) / 2, 504, col_w * 2 + gap, 32, "Quit Game", "danger", "power", 9.5)

    out += line(0, FOOT_Y, W, FOOT_Y, "#1F242D")
    out += t(24, 704, "RKB CLIENT 0.9.0  ·  Minecraft 1.21.11", 8, DIM)
    out += t(1256, 704, "FPS 120  ·  Local mode", 8, DIM, "normal", "end")
    return out


def settings():
    out = shell("RKB Settings", "signed-out", "RKB Settings", "Tune the client, not the game.")
    out += r(1036, 64, 220, 26, BG2, BORDER, 4)
    out += icon("search", 1044, 69, DIM, 16)
    out += t(1066, 81, "Search settings", 8.5, DIM)

    # category rail
    cats = ["General", "Interface", "Performance", "Controls", "HUD", "Modules", "Other"]
    y = 96
    for i, c in enumerate(cats):
        act = i == 0
        if act:
            out += r(CX, y, 156, 28, PANEL2, "none", 4)
            out += r(CX, y + 3, 3, 22, ACCENT, "none", 2)
        out += t(CX + 14, y + 18, c, 9.5, TEXT if act else MUTED, "bold" if act else "normal")
        y += 32

    px, py, pw, ph = 416, 96, CR - 416, 572
    out += r(px, py, pw, ph, PANEL, BORDER, 6)
    inner_x = px + 18
    inner_r = px + pw - 18

    out += t(inner_x, py + 24, "GENERAL", 7, DIM, "bold", "start", 1.0)
    out += line(inner_x, py + 32, inner_r, py + 32, BORDER)

    menu_rows = [
        ("FPS in menu header", "Show the frame rate in the RKB status bar.", "toggle", True),
        ("Version in menu header", "Show the client version next to the FPS counter.", "toggle", True),
        ("Menu key", "Opens the RKB menu from anywhere.", "keybind", "RIGHT SHIFT"),
    ]
    y = py + 44
    out += t(inner_x, y + 8, "MENU", 7, DIM, "bold", "start", 1.0)
    out += line(inner_x, y + 16, inner_r, y + 16, BORDER)
    y += 24
    for label, desc, kind, val in menu_rows:
        out += t(inner_x, y + 17, label, 9.5, TEXT, "bold")
        out += t(inner_x, y + 30, desc, 8, MUTED)
        if kind == "toggle":
            out += toggle(inner_r - 28, y + 12, val)
        else:
            out += keybind(inner_r - 108, y + 8, 108, val, 24)
        out += line(inner_x, y + 42, inner_r, y + 42, "#232833")
        y += 44

    out += t(inner_x, y + 8, "HUD", 7, DIM, "bold", "start", 1.0)
    out += line(inner_x, y + 16, inner_r, y + 16, BORDER)
    y += 24
    hud_rows = [
        ("HUD background", "Draw a quiet backing behind HUD text.", "toggle", True),
        ("HUD text shadow", "Keep HUD text legible over bright terrain.", "toggle", True),
        ("HUD accent color", "Accent used by HUD elements. UI chrome stays RKB blue.", "dropdown", "Cyan"),
        ("HUD text color", "Primary color for HUD labels.", "dropdown", "White"),
    ]
    for label, desc, kind, val in hud_rows:
        out += t(inner_x, y + 17, label, 9.5, TEXT, "bold")
        out += t(inner_x, y + 30, desc, 8, MUTED)
        if kind == "toggle":
            out += toggle(inner_r - 28, y + 12, val)
        else:
            out += dropdown(inner_r - 112, y + 8, 112, val, 24)
        out += line(inner_x, y + 42, inner_r, y + 42, "#232833")
        y += 44

    out += t(inner_x, y + 16, "Changes save immediately.", 8, MUTED)
    out += line(inner_x, y + 40, inner_r, y + 40, BORDER)
    out += t(inner_x, y + 64, "CLIENT TOOLS", 7, DIM, "bold", "start", 1.0)
    out += t(inner_x, y + 82, "Profiles, HUD Editor, Waypoints and Friends live under Other.", 8, MUTED)
    out += button(inner_r - 148, y + 60, 148, 26, "Open Other", "secondary", None, 8.5)
    return out


def modules():
    out = shell("RKB Modules", "signed-out", "Modules", "Every RKB module in one place.")
    out += r(CX, 86, 220, 26, BG2, BORDER, 4)
    out += icon("search", CX + 8, 91, DIM, 16)
    out += t(CX + 30, 103, "Search modules", 8.5, DIM)

    chips = ["All", "HUD", "Performance", "Visuals", "Controls", "Movement", "Utility", "World", "Favorites"]
    x = 480
    for i, c in enumerate(chips):
        cw = max(44, len(c) * 5.2 + 20)
        out += chip(x, 87, c, i == 0, 24)
        x += cw + 6

    cards = [
        ("Armor Status", "Shows worn armor and its durability.", "HUD", True),
        ("FPS Display", "Shows your current frames per second.", "HUD", True),
        ("Keystrokes", "Shows which movement keys you hold.", "HUD", True),
        ("Coordinates", "Shows your XYZ position and facing.", "HUD", False),
        ("Name Tag Badge", "RKB badge before player name tags.", "Visuals", False),
        ("Minimap", "Terrain map with waypoints and a full map.", "World", True),
        ("CPS Counter", "Shows how many times per second you click.", "HUD", True),
        ("Ping Display", "Shows your latency to the server.", "HUD", False),
        ("Toggle Sprint", "Keep sprinting without holding the key.", "Movement", True),
        ("Zoom", "Narrows your field of view while on.", "Visuals", True),
        ("Light Level", "Shows block and sky light where you stand.", "World", False),
        ("Session Timer", "Shows how long the game has been running.", "Utility", False),
    ]
    cw, ch, gap = 324, 96, 12
    for i, (name, desc, cat, on) in enumerate(cards):
        col, row = i % 3, i // 3
        x = CX + col * (cw + gap)
        y = 128 + row * (ch + gap)
        out += r(x, y, cw, ch, PANEL3 if on else PANEL, BORDER, 6)
        if on:
            out += r(x, y + 8, 2, ch - 16, ACCENT, "none", 1)
        out += t(x + 14, y + 20, name, 10, TEXT if on else MUTED, "bold")
        out += t(x + 14, y + 36, desc, 8, MUTED)
        out += t(x + 14, y + 66, cat.upper(), 6.8, DIM, "bold", "start", 0.8)
        out += icon("gear", x + cw - 58, y + 60, MUTED, 16)
        out += toggle(x + cw - 42, y + 12, on)
        out += line(x + 14, y + 50, x + cw - 14, y + 50, "#232833")

    out += t(CX, 604, "12 modules  ·  7 enabled", 8, DIM)
    out += t(CR, 604, "Click a card to configure it", 8, DIM, "normal", "end")
    return out


def cosmetics_skin():
    out = shell("Cosmetics", "signed-out", "Cosmetics", "Customize your look.")
    tabs = ["Skin", "Cape", "Redeem"]
    x = CX
    for i, tab in enumerate(tabs):
        out += button(x, 78, 112, 30, tab, "primary" if i == 0 else "secondary", None, 9)
        x += 120

    # preview
    px, py, pw, ph = CX, 120, 360, 548
    out += r(px, py, pw, ph, PANEL, BORDER, 8)
    out += f'<ellipse cx="{px + pw/2}" cy="{py + 360}" rx="92" ry="14" fill="{BLACK}" opacity="0.75"/>'
    out += steve(px + pw / 2, py + 56, 304)
    out += t(px + 18, py + 410, "Minecraft default", 10, TEXT, "bold")
    out += t(px + 18, py + 426, "Vanilla Steve texture  ·  Classic", 8, MUTED)
    out += button(px + 18, py + 448, 156, 30, "Classic", "secondary", None, 9)
    out += button(px + 186, py + 448, 156, 30, "Slim", "secondary", None, 9)
    out += r(px + 18, py + 448, 156, 30, "none", ACCENT, 6)
    out += r(px + 18, py + 496, pw - 36, 32, BG2, BORDER, 4)
    out += dot(px + 32, py + 512, SUCCESS, 5)
    out += t(px + 42, py + 515, "Local skin · nothing is uploaded", 8, MUTED)

    # collection
    rx, rw = 620, 636
    out += t(rx, 132, "SKIN COLLECTION", 7, DIM, "bold", "start", 1.0)
    out += t(rx + rw, 132, "7 saved", 7.5, DIM, "normal", "end")
    skins = [
        ("Default", True, "#C99A6B", "#00AFAF"),
        ("Alex", False, "#D7A97A", "#E07A3F"),
        ("RKB", False, "#2B2B33", "#1E2E4E"),
        ("Ember", False, "#8A3B2E", "#2A2A2A"),
        ("Frost", False, "#BFD9E8", "#3B6EA5"),
        ("Void", False, "#1A1A22", "#3A2A4A"),
        ("Sand", False, "#D9C08C", "#8A7A55"),
    ]
    for i, (name, sel, skin_c, shirt_c) in enumerate(skins):
        x = rx + i * 90
        out += r(x, 142, 72, 72, PANEL, BORDER, 4)
        out += r(x + 8, 150, 56, 56, skin_c, "none", 2)
        out += r(x + 8, 174, 56, 32, shirt_c, "none", 2)
        out += head(x + 24, 154, 24)
        if sel:
            out += r(x - 1, 141, 74, 74, "none", ACCENT, 5, 2)
        out += t(x + 36, 210, name, 7, ACCENT if sel else MUTED, "bold" if sel else "normal", "middle")

    # action tiles
    ty = 230
    tw = (rw - 12) / 2
    for i, (title, sub, ic) in enumerate([("Upload PNG", "Select a skin file", "upload"),
                                         ("Import URL", "Paste a skin URL", "link")]):
        x = rx + i * (tw + 12)
        out += r(x, ty, tw, 58, PANEL, BORDER, 6)
        out += icon(ic, x + 18, ty + 13, ACCENT, 18)
        out += t(x + 44, ty + 26, title, 9.5, TEXT, "bold")
        out += t(x + 44, ty + 40, sub, 8, MUTED)

    # url input
    out += t(rx, 304, "PNG URL", 7, DIM, "bold", "start", 0.8)
    out += r(rx, 312, 500, 30, BG2, BORDER, 4)
    out += t(rx + 10, 331, "https://example.com/skin.png", 9, DIM)
    out += button(rx + 512, 312, 124, 30, "Import", "secondary", None, 8.5)

    # selected info
    out += r(rx, 356, rw, 60, BG2, BORDER, 6)
    out += t(rx + 14, 376, "Minecraft default", 9.5, TEXT, "bold")
    out += t(rx + 14, 392, "Vanilla Steve  ·  Classic  ·  built in", 8, MUTED)
    out += t(rx + rw - 14, 376, "Active", 7.5, SUCCESS, "bold", "end")
    out += t(rx + rw - 14, 392, "Rename   ·   Delete", 7.5, MUTED, "normal", "end")

    out += button(rx + rw - 320, 432, 150, 34, "Apply Skin", "primary", "check", 9)
    out += button(rx + rw - 158, 432, 158, 34, "Reset to Default", "secondary", "refresh", 8.5)
    out += t(rx, 482, "Applying changes only your local appearance.", 8, MUTED)

    # saved sets
    out += t(rx, 502, "LOCAL SKIN SETS", 7, DIM, "bold", "start", 1.0)
    sets = [("Minecraft default", "Vanilla · Classic", "Active"),
            ("RKB Black", "Local PNG · Classic", "Saved"),
            ("Frost", "Local PNG · Slim", "Saved")]
    swatches = ["#C99A6B", "#2B2B33", "#BFD9E8"]
    for i, (name, meta, state) in enumerate(sets):
        y = 514 + i * 44
        out += r(rx, y, rw, 38, PANEL, BORDER, 4)
        out += r(rx + 6, y + 5, 28, 28, PANEL2, BORDER, 2)
        out += r(rx + 11, y + 10, 18, 18, swatches[i], "none", 2)
        if i == 0:
            out += head(rx + 14, y + 12, 12)
        out += t(rx + 44, y + 16, name, 9, TEXT, "bold")
        out += t(rx + 44, y + 29, meta, 7.5, MUTED)
        out += t(rx + rw - 14, y + 23, state, 7.5, ACCENT if state == "Active" else DIM, "bold", "end")
    return out


def cosmetics_cape():
    out = shell("Cosmetics", "signed-out", "Cosmetics", "Customize your look.")
    tabs = ["Skin", "Cape", "Redeem"]
    x = CX
    for i, tab in enumerate(tabs):
        out += button(x, 78, 112, 30, tab, "primary" if i == 1 else "secondary", None, 9)
        x += 120

    px, py, pw, ph = CX, 120, 360, 548
    out += r(px, py, pw, ph, PANEL, BORDER, 8)
    out += f'<ellipse cx="{px + pw/2}" cy="{py + 360}" rx="92" ry="14" fill="{BLACK}" opacity="0.75"/>'
    out += steve_back(px + pw / 2, py + 50, 300)
    out += t(px + 18, py + 410, "RKB Original Cape", 10, TEXT, "bold")
    out += t(px + 18, py + 426, "Rare  ·  equipped", 8, SUCCESS)
    out += button(px + 18, py + 448, 156, 30, "Equipped", "disabled", "check", 9)
    out += button(px + 186, py + 448, 156, 30, "Remove", "secondary", None, 9)
    out += r(px + 18, py + 496, pw - 36, 32, BG2, BORDER, 4)
    out += t(px + 30, py + 515, "In-game preview shows the real cape.", 8, MUTED)

    rx, rw = 620, 636
    out += t(rx, 132, "CAPE COLLECTION", 7, DIM, "bold", "start", 1.0)
    out += t(rx + rw, 132, "10 designs", 7.5, DIM, "normal", "end")

    rows = [
        ("RKB", "RKB Original Cape", "Rare  ·  RKB", "EQUIPPED", "cape_rkb_original", True),
        ("RKB", "RKB Shadow Cape", "Epic  ·  RKB", "OWNED", "cape_rkb_shadow", True),
        ("COUNTRY", "Bangladesh Cape", "Common  ·  Bangladesh", "OWNED", "cape_bangladesh", True),
        ("COUNTRY", "India Cape", "Common  ·  India", "OWNED", "cape_india", True),
        ("COUNTRY", "USA Cape", "Common  ·  United States", "OWNED", "cape_usa", True),
        ("COUNTRY", "Brazil Cape", "Common  ·  Brazil", "OWNED", "cape_brazil", True),
        ("COUNTRY", "Japan Cape", "Common  ·  Japan", "OWNED", "cape_japan", True),
        ("COUNTRY", "Nepal Cape", "Common  ·  Nepal", "OWNED", "cape_nepal", True),
        ("COUNTRY", "Pakistan Cape", "Common  ·  Pakistan", "OWNED", "cape_pakistan", True),
        ("COUNTRY", "UK Cape", "Common  ·  United Kingdom", "UNAVAILABLE", "cape_uk", False),
    ]
    patterns = {
        "cape_rkb_original": ("#1E2E4E", "#4FD8FF", "#4FD8FF", "#4FD8FF", "rkb"),
        "cape_rkb_shadow": ("#15161C", "#2E4166", "#2E4166", "#2E4166", "plain"),
        "cape_bangladesh": ("#0B6B3A", "#D62A2A", "#D62A2A", "#D62A2A", "bd"),
        "cape_india": ("#E8892B", "#FFFFFF", "#138808", "#1B3A8A", "in"),
        "cape_usa": ("#B22234", "#FFFFFF", "#FFFFFF", "#FFFFFF", "us"),
        "cape_brazil": ("#009C3B", "#FFDF00", "#002776", "#002776", "br"),
        "cape_japan": ("#F2F2F2", "#BC002D", "#BC002D", "#BC002D", "jp"),
        "cape_nepal": ("#DC143C", "#003893", "#003893", "#003893", "np"),
        "cape_pakistan": ("#01411C", "#FFFFFF", "#FFFFFF", "#FFFFFF", "pk"),
        "cape_uk": ("#012169", "#FFFFFF", "#C8102E", "#C8102E", "uk"),
    }
    y = 148
    group = None
    for g, name, meta, status, key, available in rows:
        if g != group:
            group = g
            out += t(rx, y + 8, "RKB" if g == "RKB" else "COUNTRY CAPES", 7, ACCENT if g == "RKB" else DIM,
                     "bold", "start", 1.0)
            y += 16
        equipped = status == "EQUIPPED"
        out += r(rx, y, rw, 40, PANEL3 if equipped else PANEL, BORDER, 4)
        if equipped:
            out += r(rx, y + 5, 2, 30, ACCENT, "none", 1)
        c = patterns[key]
        out += cape_swatch(rx + 10, y + 9, 30, 22, c[:4], c[4])
        out += t(rx + 52, y + 16, name, 9.5, TEXT if available else MUTED, "bold")
        out += t(rx + 52, y + 30, meta, 7.5, DIM)
        scolor = ACCENT if equipped else SUCCESS if available else DIM
        out += t(rx + rw - 92, y + 24, status, 7, scolor, "bold", "end")
        if equipped:
            out += button(rx + rw - 78, y + 8, 68, 24, "Remove", "secondary", None, 7.5)
        elif available:
            out += button(rx + rw - 78, y + 8, 68, 24, "Equip", "secondary", None, 7.5)
        else:
            out += button(rx + rw - 78, y + 8, 68, 24, "Equip", "disabled", None, 7.5)
        y += 40

    out += t(rx, 596, "Availability is confirmed by RKB Cosmetics V2.", 8, MUTED)
    out += t(rx, 610, "Local previews never grant ownership.", 8, MUTED)
    return out


def cosmetics_redeem():
    out = shell("Cosmetics", "signed-in", "Cosmetics", "Customize your look.")
    tabs = ["Skin", "Cape", "Redeem"]
    x = CX
    for i, tab in enumerate(tabs):
        out += button(x, 78, 112, 30, tab, "primary" if i == 2 else "secondary", None, 9)
        x += 120

    lx, ly, lw, lh = CX, 120, 552, 548
    rx, rw = 812, 444
    out += r(lx, ly, lw, lh, PANEL, BORDER, 6)
    out += t(lx + 24, ly + 30, "REDEEM A CODE", 7, DIM, "bold", "start", 1.0)
    out += t(lx + 24, ly + 58, "Enter your code", 16, TEXT, "bold")
    out += t(lx + 24, ly + 76, "Codes come from events and giveaways. Each code can be used once.", 8.5, MUTED)

    out += t(lx + 24, ly + 116, "CODE", 7, DIM, "bold", "start", 0.8)
    out += r(lx + 24, ly + 124, 340, 34, BG2, BORDER, 4)
    out += t(lx + 36, ly + 146, "RKB-XXXX-XXXX", 10, DIM)
    out += button(lx + 376, ly + 124, 128, 34, "Redeem", "primary", None, 9)
    out += t(lx + 24, ly + 174, "Codes are checked by the RKB cloud while you are signed in and linked.", 8, MUTED)

    out += line(lx + 24, ly + 196, lx + lw - 24, ly + 196, BORDER)
    out += t(lx + 24, ly + 218, "RESULT  ·  EXAMPLE", 7, DIM, "bold", "start", 1.0)
    out += r(lx + 24, ly + 228, lw - 48, 52, "#152019", "#23382B", 4)
    out += icon("check", lx + 36, ly + 244, SUCCESS, 16)
    out += t(lx + 60, ly + 252, "Cape added to your inventory.", 9.5, SUCCESS, "bold")
    out += t(lx + 60, ly + 266, "RKB Original Cape  ·  owned", 8, MUTED)

    out += t(lx + 24, ly + 310, "STATE BEHAVIOUR", 7, DIM, "bold", "start", 1.0)
    states = [
        ("Loading", "Button reads Redeeming... and is disabled.", WARN),
        ("Success", "Server item name is shown. No invented history.", SUCCESS),
        ("Error", "Server message is shown verbatim.", DANGER),
        ("Signed out", "Form is disabled. Open Account to sign in.", DIM),
    ]
    y = ly + 322
    for name, desc, color in states:
        out += dot(lx + 30, y + 6, color, 5)
        out += t(lx + 42, y + 9, name, 8.5, TEXT, "bold")
        out += t(lx + 118, y + 9, desc, 8, MUTED)
        y += 24

    out += t(lx + 24, ly + 512, "No redemption history is shown because V2 has no history action.", 8, DIM)

    out += r(rx, ly, rw, 548, PANEL, BORDER, 6)
    out += t(rx + 20, ly + 30, "CLOUD INVENTORY", 7, DIM, "bold", "start", 1.0)
    out += dot(rx + 26, ly + 54, SUCCESS, 6)
    out += t(rx + 38, ly + 57, "Signed in", 9.5, TEXT, "bold")
    out += t(rx + 38, ly + 71, "RKB_GAMERZ", 8, MUTED)
    out += t(rx + 20, ly + 100, "Minecraft link", 8, MUTED)
    out += t(rx + rw - 20, ly + 100, "Linked", 8, SUCCESS, "bold", "end")
    out += t(rx + 20, ly + 122, "Owned items", 8, MUTED)
    out += t(rx + rw - 20, ly + 122, "—", 8, TEXT, "bold", "end")
    out += t(rx + 20, ly + 144, "Last sync", 8, MUTED)
    out += t(rx + rw - 20, ly + 144, "Not yet", 8, TEXT, "normal", "end")
    out += button(rx + 20, ly + 166, (rw - 52) / 2, 28, "Sync now", "secondary", "refresh", 8)
    out += button(rx + 20 + (rw - 52) / 2 + 12, ly + 166, (rw - 52) / 2, 28, "Manage account", "secondary",
                  None, 8)
    out += line(rx + 20, ly + 214, rx + rw - 20, ly + 214, BORDER)
    out += t(rx + 20, ly + 238, "REQUIREMENTS", 7, DIM, "bold", "start", 1.0)
    out += t(rx + 20, ly + 258, "An RKB account signed in on this device.", 8, MUTED)
    out += t(rx + 20, ly + 274, "A linked Minecraft account for delivery.", 8, MUTED)
    out += line(rx + 20, ly + 298, rx + rw - 20, ly + 298, BORDER)
    out += t(rx + 20, ly + 322, "SIGNED OUT", 7, DIM, "bold", "start", 1.0)
    out += t(rx + 20, ly + 342, "The form is disabled and the button", 8, MUTED)
    out += t(rx + 20, ly + 356, "reads Sign in to redeem. Local skins", 8, MUTED)
    out += t(rx + 20, ly + 370, "and capes still work without an account.", 8, MUTED)
    out += button(rx + 20, ly + 392, 132, 26, "Open Account", "ghost", None, 8)
    out += line(rx + 20, ly + 430, rx + rw - 20, ly + 430, BORDER)
    out += t(rx + 20, ly + 454, "PRIVACY", 7, DIM, "bold", "start", 1.0)
    out += t(rx + 20, ly + 474, "Redeem codes are checked by RKB Cloud.", 8, MUTED)
    out += t(rx + 20, ly + 488, "Your Minecraft token is never sent.", 8, MUTED)
    return out


def account():
    out = shell("Account", "signed-out", "Account", "Minecraft identity and RKB cloud.")
    lx, ly, lw = CX, 92, 352
    rx, rw = 620, 636

    out += r(lx, ly, lw, 552, PANEL, BORDER, 6)
    out += head(lx + 20, ly + 20, 48)
    out += t(lx + 80, ly + 40, "RKB_GAMERZ", 15, TEXT, "bold")
    out += t(lx + 80, ly + 56, "Minecraft account", 8, MUTED)
    out += t(lx + 20, ly + 96, "ACCOUNT STATE", 7, DIM, "bold", "start", 1.0)

    states = [
        ("Minecraft account", "Online  ·  Mojang session", SUCCESS, None),
        ("RKB Cloud", "Signed out", DIM, None),
        ("Minecraft link", "Not linked", DIM, "Link"),
    ]
    y = ly + 112
    for name, value, color, action in states:
        out += r(lx + 16, y, lw - 32, 52, BG2, BORDER, 4)
        out += dot(lx + 30, y + 18, color, 6)
        out += t(lx + 42, y + 21, name, 8, MUTED)
        out += t(lx + 42, y + 37, value, 9, TEXT, "bold")
        if action:
            out += button(lx + lw - 32 - 64, y + 14, 64, 24, action, "secondary", None, 7.5)
        y += 60

    out += r(lx + 16, ly + 306, lw - 32, 96, "#151A22", BORDER, 4)
    out += t(lx + 30, ly + 328, "OFFLINE SUPPORT", 7, DIM, "bold", "start", 0.8)
    out += t(lx + 30, ly + 346, "Local skins, capes and settings", 8.5, TEXT)
    out += t(lx + 30, ly + 360, "work without an RKB account.", 8.5, TEXT)
    out += t(lx + 30, ly + 380, "Offline UUIDs are never shown as", 7.5, MUTED)
    out += t(lx + 30, ly + 392, "verified Mojang accounts.", 7.5, MUTED)

    out += line(lx + 20, ly + 424, lx + lw - 20, ly + 424, BORDER)
    out += t(lx + 20, ly + 448, "PRIVACY", 7, DIM, "bold", "start", 1.0)
    out += t(lx + 20, ly + 468, "Minecraft access tokens never leave", 7.8, MUTED)
    out += t(lx + 20, ly + 482, "this device. RKB Cloud never receives", 7.8, MUTED)
    out += t(lx + 20, ly + 496, "your Minecraft token.", 7.8, MUTED)

    # right form
    out += r(rx, ly, rw, 552, PANEL, BORDER, 6)
    out += t(rx + 24, ly + 30, "RKB CLOUD ACCOUNT", 7, DIM, "bold", "start", 1.0)
    out += t(rx + 24, ly + 58, "Sign in or create an account", 15, TEXT, "bold")
    out += t(rx + 24, ly + 76, "Cloud inventory, sync and redeem need an RKB account.", 8, MUTED)

    out += r(rx + 24, ly + 96, 196, 30, PANEL2, BORDER, 4)
    out += t(rx + 122, ly + 115, "Sign in", 8.5, TEXT, "bold", "middle")
    out += r(rx + 220, ly + 96, 196, 30, "none", "none", 4)
    out += t(rx + 318, ly + 115, "Create account", 8.5, MUTED, "normal", "middle")

    out += t(rx + 24, ly + 150, "E-MAIL", 7, DIM, "bold", "start", 0.8)
    out += r(rx + 24, ly + 158, rw - 48, 32, BG2, BORDER, 4)
    out += t(rx + 36, ly + 178, "you@example.com", 9, DIM)

    out += t(rx + 24, ly + 206, "PASSWORD", 7, DIM, "bold", "start", 0.8)
    out += r(rx + 24, ly + 214, rw - 48, 32, BG2, BORDER, 4)
    out += t(rx + 36, ly + 235, "Enter your password", 9, DIM)

    out += button(rx + 24, ly + 262, rw - 48, 34, "Sign in", "primary", None, 9.5)
    out += t(rx + rw / 2, ly + 316, "Continue offline", 8.5, MUTED, "normal", "middle")
    out += line(rx + 24, ly + 338, rx + rw - 24, ly + 338, BORDER)

    out += t(rx + 24, ly + 360, "STATUS", 7, DIM, "bold", "start", 1.0)
    out += dot(rx + 30, ly + 378, DIM, 5)
    out += t(rx + 42, ly + 381, "Signed out. Local skins and settings stay available.", 8, MUTED)

    out += r(rx + 24, ly + 400, rw - 48, 76, "#151A22", BORDER, 4)
    out += t(rx + 38, ly + 420, "LOCAL MODE", 7, DIM, "bold", "start", 0.8)
    out += t(rx + 38, ly + 438, "Import PNG skins, use default Steve or Alex,", 8, MUTED)
    out += t(rx + 38, ly + 452, "equip bundled capes and keep every setting.", 8, MUTED)
    out += t(rx + 38, ly + 468, "No registration is required.", 8, MUTED)

    out += t(rx + 24, ly + 504, "Cloud features are optional.", 7.5, DIM)
    out += t(rx + 24, ly + 518, "Offline play never requires an account.", 7.5, DIM)
    return out


def write(name, body):
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
           f'width="{W}" height="{H}" viewBox="0 0 {W} {H}">' + body + "</svg>")
    path = os.path.join(HERE, name + ".svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)
    print(path)


write("concept-01-main-menu", main_menu())
write("concept-02-settings", settings())
write("concept-03-modules", modules())
write("concept-04-cosmetics-skin", cosmetics_skin())
write("concept-05-cosmetics-cape", cosmetics_cape())
write("concept-06-cosmetics-redeem", cosmetics_redeem())
write("concept-07-account", account())
