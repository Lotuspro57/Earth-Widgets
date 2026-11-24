#!/usr/bin/env python3
"""Renders the widget-picker preview images and the launcher icon.

The shading here mirrors EarthRenderer.kt, so these images also show what the widgets look like.
Needs: numpy, Pillow.   Run:  python3 make_previews.py <assets-dir> <res-dir>
"""
import sys
import math
import datetime
import numpy as np
from PIL import Image, ImageDraw, ImageFont

TILT = math.radians(23.4393)
SIN_E, COS_E = math.sin(TILT), math.cos(TILT)
ECC, PERI = 0.01671, math.radians(102.9373)
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def orbit_state(dt):
    d = (dt - datetime.datetime(2000, 1, 1, 12, tzinfo=datetime.timezone.utc)).total_seconds() / 86400.0
    m = math.radians((357.5291 + 0.98560028 * d) % 360.0)
    nu = m + 2 * ECC * math.sin(m) + 1.25 * ECC * ECC * math.sin(2 * m)
    L = (nu + PERI) % (2 * math.pi)
    r = (1 - ECC * ECC) / (1 + ECC * math.cos(nu))
    return L, r


def radius_at(L):
    return (1 - ECC * ECC) / (1 + ECC * math.cos(L - PERI))


def globe_view(L, alpha=math.radians(35), beta=math.radians(20)):
    """Camera for the Globe and Strip shapes: looks at Earth from 35 degrees off the Sun, 20 above the orbit plane."""
    s = np.array([-math.cos(L), -math.sin(L), 0.0])
    perp = np.array([-s[1], s[0], 0.0])
    c = math.cos(beta) * (math.cos(alpha) * s + math.sin(alpha) * perp) + np.array([0, 0, math.sin(beta)])
    c = c / np.linalg.norm(c)
    z = np.array([0.0, 0.0, 1.0])
    u = z - np.dot(z, c) * c
    u = u / np.linalg.norm(u)
    r = np.cross(u, c)
    return r, u, c


POLAR = (np.array([1.0, 0, 0]), np.array([0, 1.0, 0]), np.array([0, 0, 1.0]))


def sun_vec(L):
    return np.array([-math.cos(L), -math.sin(L), 0.0])


def earth_sprite(R, view, sun_e, theta, color, aux):
    Rv, Uv, Cv = view
    sx, sy, sz = float(np.dot(sun_e, Rv)), float(np.dot(sun_e, Uv)), float(np.dot(sun_e, Cv))
    th, tw = color.shape[:2]
    D = int(math.ceil(R * 2 * 1.2))
    D += D % 2
    j, i = np.mgrid[0:D, 0:D]
    c = D / 2.0
    x = (i + 0.5 - c) / R
    y = -(j + 0.5 - c) / R
    rr = np.hypot(x, y)
    inside = rr <= 1.0
    z = np.sqrt(np.clip(1 - rr * rr, 0, 1))
    ne = x[..., None] * Rv + y[..., None] * Uv + z[..., None] * Cv
    lat = np.arcsin(np.clip(ne[..., 1] * SIN_E + ne[..., 2] * COS_E, -1, 1))
    phi = np.arctan2(ne[..., 1] * COS_E - ne[..., 2] * SIN_E, ne[..., 0])
    u = ((phi - theta + math.pi) / (2 * math.pi)) % 1.0
    fx = u * tw - 0.5
    x0f = np.floor(fx)
    wx = fx - x0f
    x0 = x0f.astype(int) % tw
    x1 = (x0 + 1) % tw
    fy = np.clip((math.pi / 2 - lat) / math.pi * th - 0.5, 0, th - 1.001)
    y0 = fy.astype(int)
    wy = fy - y0
    y1 = y0 + 1

    def bil(arr):
        return (arr[y0, x0] * ((1 - wx) * (1 - wy))[..., None] + arr[y0, x1] * (wx * (1 - wy))[..., None]
                + arr[y1, x0] * ((1 - wx) * wy)[..., None] + arr[y1, x1] * (wx * wy)[..., None])

    col = bil(color.astype(np.float64))
    ax = bil(aux.astype(np.float64))
    ca = ax[..., 0] / 255 * 0.92
    ln = ax[..., 1] / 255
    flag = ax[..., 2]
    col = col * (1 - ca[..., None]) + np.array([245, 248, 252]) * ca[..., None]

    d = x * sx + y * sy + z * sz
    lit = np.clip(d, 0, None)
    shade = 0.03 + 1.05 * lit ** 0.8
    col = col * shade[..., None]
    night = 1 - sstep(-0.05, 0.2, d)
    col += np.array([4, 8, 18]) * night[..., None]
    tw_ = sstep(-0.12, 0.0, d) * (1 - sstep(0.0, 0.16, d))
    col += np.array([255, 110, 40]) * (tw_ * 0.07)[..., None]
    nf = np.clip((d - 0.04) / (-0.18), 0, 1)
    nf = nf * nf * (3 - 2 * nf) * (1 - ca * 0.75)
    col += np.array([255, 196, 110]) * (ln * nf * 0.95)[..., None]
    hx, hy, hz = sx, sy, sz + 1.0
    hl = math.sqrt(hx * hx + hy * hy + hz * hz)
    nh = (x * hx + y * hy + z * hz) / hl
    sp = np.where((d > 0) & (flag < 128) & (nh > 0), np.clip(nh, 0, 1) ** 90 * 0.85 * (1 - ca), 0)
    col += np.array([255, 250, 240]) * sp[..., None]
    rim = (1 - z) ** 3
    am = rim * (0.12 + 0.88 * sstep(-0.25, 0.5, d)) * 0.85
    col = col * (1 - am * 0.5)[..., None] + np.array([110, 170, 255]) * am[..., None]

    out = np.zeros((D, D, 4), dtype=np.float64)
    out[..., :3] = np.clip(col, 0, 255)
    out[..., 3] = np.where(inside, 255, 0)
    # halo outside the disc
    t = (rr - 1.0) / 0.2
    dd = (x / np.maximum(rr, 1e-6)) * sx + (y / np.maximum(rr, 1e-6)) * sy
    lf = sstep(-0.4, 0.65, dd)
    halo = np.clip(1 - t, 0, 1) ** 2.4 * 0.75 * (0.12 + 0.88 * lf)
    hm = (rr > 1.0) & (rr <= 1.2)
    out[hm, 0], out[hm, 1], out[hm, 2] = 110, 170, 255
    out[..., 3] = np.where(hm, halo * 255, out[..., 3])
    return Image.fromarray(out.astype(np.uint8), 'RGBA')


def glow(size, center, radius, stops):
    """stops: list of (pos 0..1, (r,g,b,a 0..255))"""
    yy, xx = np.mgrid[0:size[1], 0:size[0]]
    t = np.clip(np.hypot(xx - center[0], yy - center[1]) / radius, 0, 1)
    pos = [s[0] for s in stops]
    out = np.zeros((size[1], size[0], 4))
    for ch in range(4):
        out[..., ch] = np.interp(t, pos, [s[1][ch] for s in stops])
    return Image.fromarray(out.astype(np.uint8), 'RGBA')


def sun_sprite(r):
    s = int(math.ceil(r * 2)) + 4
    j, i = np.mgrid[0:s, 0:s]
    x, y = (i + 0.5 - s / 2) / r, (j + 0.5 - s / 2) / r
    rr = np.hypot(x, y)
    z = np.sqrt(np.clip(1 - rr * rr, 0, 1))
    b = np.clip((0.38 + 0.62 * z ** 0.55) * 1.0, 0, 1.05)
    out = np.zeros((s, s, 4))
    out[..., 0] = 255
    out[..., 1] = 110 + 145 * b ** 1.3
    out[..., 2] = 20 + 215 * b ** 2.2
    out[..., 3] = np.clip(1 - (rr - 1) * r, 0, 1) * 255
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


def stars_bg(w, h, seed=7):
    rng = np.random.default_rng(seed)
    img = Image.new('RGBA', (w, h), (3, 6, 14, 255))
    img = Image.alpha_composite(img, glow((w, h), (w / 2, h / 2), max(w, h) * 0.75,
                                          [(0, (12, 22, 46, 255)), (1, (1, 2, 7, 255))]))
    d = ImageDraw.Draw(img)
    for _ in range(int(w * h / 5200)):
        x, y, mag = rng.random() * w, rng.random() * h, rng.random() ** 3
        a = int((0.25 + mag * 0.75) * 255)
        r = 0.4 + mag * 1.1
        d.ellipse([x - r, y - r, x + r, y + r], fill=(235, 240, 255, a))
    return img


def rounded_mask(w, h, radius, circle=False):
    m = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(m)
    if circle:
        s = min(w, h)
        d.ellipse([(w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2], fill=255)
    else:
        d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    return m


def theta_for(L, now):
    s = sun_vec(L)
    return math.atan2(s[1] * COS_E, s[0]) - math.radians((12 - (now.hour + now.minute / 60)) * 15)


def paste_center(base, sprite, cx, cy):
    base.alpha_composite(sprite, (int(round(cx - sprite.width / 2)), int(round(cy - sprite.height / 2))))


def draw_orbit(img, cx, cy, size, L, r, color, aux, now):
    d = ImageDraw.Draw(img)
    orbit_r, earth_r, sun_r = size * 0.335, size * 0.098, size * 0.058
    pts = []
    for a in range(0, 361, 3):
        l = math.radians(a)
        rr = radius_at(l)
        pts.append((cx + orbit_r * rr * math.cos(l), cy - orbit_r * rr * math.sin(l)))
    d.line(pts, fill=(140, 170, 230, 90), width=max(1, int(size / 300)))
    for k in range(40):
        a0, a1 = L - math.radians(k * 1.6), L - math.radians((k + 1) * 1.6)
        r0, r1 = radius_at(a0), radius_at(a1)
        d.line([(cx + orbit_r * r0 * math.cos(a0), cy - orbit_r * r0 * math.sin(a0)),
                (cx + orbit_r * r1 * math.cos(a1), cy - orbit_r * r1 * math.sin(a1))],
               fill=(130, 190, 255, int(255 * (1 - k / 40) * 0.6)), width=max(2, int(size / 170)))
    w, h = img.size
    g = glow((w, h), (cx, cy), sun_r * 7, [(0, (255, 190, 90, 140)), (0.114, (255, 190, 90, 140)),
                                           (0.25, (255, 150, 50, 56)), (0.55, (255, 110, 30, 15)), (1, (255, 100, 20, 0))])
    img.alpha_composite(g)
    paste_center(img, sun_sprite(sun_r), cx, cy)
    ex, ey = cx + orbit_r * r * math.cos(L), cy - orbit_r * r * math.sin(L)
    paste_center(img, earth_sprite(earth_r, POLAR, sun_vec(L), theta_for(L, now), color, aux), ex, ey)


def make(assets, res):
    color = np.asarray(Image.open(f'{assets}/earth_color.png').convert('RGB'))
    aux = np.asarray(Image.open(f'{assets}/earth_aux.png').convert('RGB'))
    now = datetime.datetime.now(datetime.timezone.utc)
    L, r = orbit_state(now)
    theta = theta_for(L, now)
    gview = globe_view(L)
    sun_e = sun_vec(L)
    sx, sy = float(np.dot(sun_e, gview[0])), float(np.dot(sun_e, gview[1]))
    shown = now + datetime.timedelta(hours=6)  # label previews with the local (UTC+6) calendar date
    date_txt = f'{shown.day} {shown.strftime("%b")} {shown.year}'
    dist = f'{r * 149.598:.1f}M km'
    season = ['Spring', 'Summer', 'Autumn', 'Winter'][int(((L + math.pi) % (2 * math.pi)) // (math.pi / 2)) % 4]
    SS = 2
    out_dir = f'{res}/drawable-nodpi'

    # orbit (square)
    S = 400 * SS
    img = stars_bg(S, S)
    draw_orbit(img, S / 2, S / 2, S, L, r, color, aux, now)
    ImageDraw.Draw(img).text((S * 0.07, S * 0.06), date_txt, font=ImageFont.truetype(FONT_B, int(S * 0.062)), fill=(233, 238, 251, 255))
    img.putalpha(rounded_mask(S, S, int(S * 0.2)))
    img.resize((400, 400), Image.LANCZOS).save(f'{out_dir}/preview_orbit.png')

    # globe (circle)
    img = stars_bg(S, S)
    n = math.hypot(sx, sy) or 1.0
    gl = glow((S, S), (S / 2 + sx / n * S * 0.31 * 1.9, S * 0.44 - sy / n * S * 0.31 * 1.9), S * 0.31 * 2.2,
              [(0, (255, 180, 90, 90)), (1, (255, 100, 20, 0))])
    img.alpha_composite(gl)
    paste_center(img, earth_sprite(S * 0.31, gview, sun_e, theta, color, aux), S / 2, S * 0.44)
    d = ImageDraw.Draw(img)
    d.text((S / 2, S * 0.855), date_txt, font=ImageFont.truetype(FONT_B, int(S * 0.075)), fill=(233, 238, 251, 255), anchor='ms')
    d.text((S / 2, S * 0.94), dist, font=ImageFont.truetype(FONT_R, int(S * 0.052)), fill=(180, 192, 220, 255), anchor='ms')
    img.putalpha(rounded_mask(S, S, 0, circle=True))
    img.resize((400, 400), Image.LANCZOS).save(f'{out_dir}/preview_globe.png')

    # wide
    W, H = 500 * SS, 220 * SS
    img = stars_bg(W, H)
    draw_orbit(img, H / 2, H / 2, H, L, r, color, aux, now)
    d = ImageDraw.Draw(img)
    x0 = H * 1.02
    d.text((x0, H * 0.30), date_txt, font=ImageFont.truetype(FONT_B, int(H * 0.17)), fill=(233, 238, 251, 255), anchor='ls')
    for k, (txt, base) in enumerate([(f'{season} · day {now.timetuple().tm_yday}', 0.47), (f'{dist} from Sun', 0.65), ('30.2 km/s', 0.82)]):
        d.text((x0, H * base), txt, font=ImageFont.truetype(FONT_R, int(H * 0.095)), fill=(180, 192, 220, 255), anchor='ls')
    img.putalpha(rounded_mask(W, H, int(H * 0.2)))
    img.resize((500, 220), Image.LANCZOS).save(f'{out_dir}/preview_wide.png')

    # strip
    W, H = 500 * SS, 90 * SS
    img = stars_bg(W, H)
    paste_center(img, earth_sprite(H * 0.40, gview, sun_e, theta, color, aux), H * 0.55, H / 2)
    d = ImageDraw.Draw(img)
    x0 = H * 1.12
    d.text((x0, H * 0.50), date_txt, font=ImageFont.truetype(FONT_B, int(H * 0.30)), fill=(233, 238, 251, 255), anchor='ls')
    d.text((x0, H * 0.80), f'{dist} · {season}', font=ImageFont.truetype(FONT_R, int(H * 0.19)), fill=(180, 192, 220, 255), anchor='ls')
    img.putalpha(rounded_mask(W, H, H // 2))
    img.resize((500, 90), Image.LANCZOS).save(f'{out_dir}/preview_strip.png')

    # launcher icon
    S = 192 * SS
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    bg = stars_bg(S, S, seed=3)
    bg.putalpha(rounded_mask(S, S, 0, circle=True))
    img.alpha_composite(bg)
    paste_center(img, earth_sprite(S * 0.36, gview, sun_e, theta, color, aux), S / 2, S / 2)
    img.resize((192, 192), Image.LANCZOS).save(f'{res}/mipmap-nodpi/ic_launcher.png')
    print('previews written')


if __name__ == '__main__':
    make(sys.argv[1], sys.argv[2])
