#!/usr/bin/env python3
"""Generates the two Earth texture maps used by the widget renderer.

  earth_color.png  RGB   surface colour (land biomes, ocean depth, ice)
  earth_aux.png    RGB   R = cloud cover, G = city lights, B = 255 on land/ice (no ocean glint)

Both are equirectangular, 1024 x 512, row 0 = 90 N, column 0 = 180 W.
Needs: numpy, Pillow.   Run:  python3 make_textures.py <output-dir>
"""
import sys
import numpy as np
from PIL import Image, ImageDraw

TW, TH = 1024, 512

# Coarse continent outlines, flat lists of lon, lat.
LAND = [
    [-165,64,-157,71,-140,70,-125,70,-110,68,-95,68,-85,70,-82,63,-93,59,-94,57,-82,55,-79,52,-78,60,-70,60,-64,60,-61,56,-56,52,-60,47,-66,45,-70,43,-70,41.5,-74,40,-76,37,-76,35,-81,31,-80,26,-82,26,-83,29,-88,30,-90,29,-94,29.5,-97,26,-97.5,22,-95,18.5,-91,18.5,-90,21,-87,21.5,-88,16,-84,15.5,-83,10,-79.5,9,-77.5,8.5,-80,7.5,-83,8.5,-86,11,-88,13,-92,14.5,-96,16,-105,19.5,-106,23,-109,25,-113,31,-115,32,-118,34,-121,35,-124,40,-124,46,-125,49,-130,54,-135,58,-142,60,-150,61,-152,59,-158,56,-163,55,-158,58,-162,60,-165,62],
    [-77.5,8.5,-72,12,-65,10.5,-60,8,-52,5,-50,0,-44,-2.5,-35,-5.5,-35,-9,-39,-14,-39,-18,-41,-22,-45,-23.5,-48,-26,-49,-29,-53,-34,-57,-35,-57,-38,-62,-39,-65,-41,-64,-43,-67,-46,-66,-48,-69,-51,-68,-53,-70,-55,-74,-52,-75,-47,-73,-42,-73,-37,-71.5,-30,-70.5,-24,-70,-18,-76,-14,-79.5,-8,-81,-5,-80,-1,-78.5,2,-77.5,6.5],
    [-17,21,-16,15,-17.5,14.5,-15,11,-13,8,-8,4.5,-4,5,2,6,5,4.5,9,4,9.5,1,9,-2,12,-5,13.5,-11,12,-17,14.5,-23,16,-29,18,-34,20,-35,26,-34,32,-28.5,35,-24,35,-19,40,-15,40.5,-10,39,-5,41,-1,44,1.5,48,5,51,11.8,44,10.5,43,12.5,39,16,37,20,35,24.5,32.5,30,29,31,20,32,15,32.5,11,33,10,37,3,36.5,-2,35.5,-6,35.8,-10,30,-13,27.5,-16,23],
    [-9,37,-9,43,-2,43.5,-1.5,46,-4.5,48.5,-1,49.5,2,51,5,53,8.5,54,8.5,57,10.5,57.5,10.5,54.5,14,54,19,54.5,21,57,24,57.5,24,59.5,29,60,23,60,21.5,61,21,64,25,65.5,22,65.8,17.5,62.5,18.5,60,16,56.5,13,55.5,11,58.5,8,58,5,59,5,62,10,64,14,67.5,19,70,26,71,31,70,33,69,41,67,35,66,34,64,38,64.5,41,66,44,66,44,68.5,53,68.5,60,69,66,69,69,73,74,72.5,80,73,87,75,100,77,105,77.5,113,74,125,73.5,130,71,140,72.5,150,71,160,69.5,170,70,180,69,180,65,178,64.5,170,60,163,59.5,163,56,156,51,156,57,160,61,155,59.5,143,59,137,54,141,52.5,140,48,135,43.5,130,42.5,129.5,39.5,127.5,39.5,129,35.5,126.5,34.5,126,37.5,125,39.5,121.5,39,122,41,118,39,119.5,37,122.5,37,120,35,121.5,31.5,122,29.5,120,26.5,116.5,23,110.5,21,108,21.5,106.5,19.5,109,15,109,12,105,8.7,104.5,10,101,12.5,100,13.5,99.5,9.5,101,6.5,103.5,4,103.5,1.5,101,2.5,98.5,8,98.5,13,97.5,16.5,94.5,16,94,19,91.5,22.5,89,21.5,86.5,20,84,18,80.5,15.5,80,10.5,77.5,8,76,10.5,73,17,72.8,21,70,20.8,68.5,23.5,66.5,25.3,61.5,25,57,25.8,56.5,27,52,28,50,30,48.5,30,50,26.5,51.5,24.5,54,24,56.5,24.5,56.5,26,59.5,22.5,57.5,19,52,16,44,12.8,43,15,39,21.5,35,28,34.5,27.8,32.5,30,34.5,31.5,35.5,34,36,36.5,30,36.5,27.5,37,26.5,39.5,29,41,28,41.5,26,40.7,23.5,40.2,24,38,22,36.5,21,38.5,19.5,41.5,15,44.5,13.5,45.5,12.5,44,16,41.5,18.5,40,16,38,15.7,40,12.5,41.5,10.2,44,7,43.7,3,43.2,3,41.8,0.5,40.5,-0.5,38.5,-2,36.8,-5.5,36,-7,37],
    [114,-22,114,-26,115,-34,118,-35,123.5,-34,129,-31.5,134,-32.5,138,-35,140,-38,145,-38.8,150,-37.5,153,-31,153,-25,146,-19,145.5,-15,143,-14,142,-10.7,141,-17,136,-15,137,-12,132,-11.5,129,-15,126,-14,122,-17.5,121,-19.5,116,-20.5],
    [-73,78,-60,82,-30,83.5,-18,80,-20,75,-22,70,-32,68,-42,60,-50,63,-54,67,-58,75,-68,76],
    [-5,50,1.5,51,1.7,53,-2,56,-2,57.5,-4,58.5,-6,57,-5,55,-3,54.5,-3,53.5,-4.5,53,-5,51.7],
    [-10,52,-6,52,-6,54.5,-8,55,-10,54],
    [-24,65.5,-18,66.3,-14,65,-19,63.4,-22,64],
    [130,31,132,34,136,34,140,35,141,38.5,142,41.5,140,41,139.5,38,136.5,37,133,35.5,130.5,34],
    [140,42,145,43.5,142,45.5],
    [95,5.5,98,4,104,-2,106,-6,102,-4,97,1],
    [109,1.5,113,3,117,7,119,5,117,1,116,-4,111,-3],
    [105,-6.5,114.5,-7.5,114,-8.5,106,-7.5],
    [131,-1,135,-3,141,-2.5,147,-6,150,-10.5,143,-9,138,-8,137,-5,132,-3],
    [120,18,122,18,124,13,126,7,125,6,122,7,121,13],
    [172.5,-34.5,178,-37.5,175,-41.5,174,-39,172.5,-37],
    [172.5,-40.5,174,-41.5,171,-45,167,-46.5,166.5,-45.5,170,-43],
    [49,-12,50.5,-15.5,47,-25,44,-24,43.5,-21,44.5,-16],
    [80,9.5,82,7,81,6,80,6],
    [-85,22,-80,23,-74,20,-77,20,-82,22.5],
    [-80,73,-68,70,-62,66,-70,62,-78,64,-90,70],
    [-118,70,-102,73,-105,69],
    [-90,80,-62,82,-75,78],
]
antarctica = []
for lon in range(-180, 181, 15):
    antarctica += [lon, -69 + 3 * np.sin(lon * 0.07) + 2 * np.cos(lon * 0.19)]
antarctica += [180, -90, -180, -90]
LAND.append(antarctica)


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mixc(a, b, t):
    return a + (b - a) * t


U32 = np.uint32


def h3(x, y, z):
    x = x.astype(np.uint32); y = y.astype(np.uint32); z = z.astype(np.uint32)
    h = (x * U32(374761393)) ^ (y * U32(668265263)) ^ (z * U32(1274126177))
    h = (h ^ (h >> U32(13))) * U32(1274126177)
    h = h ^ (h >> U32(16))
    return h.astype(np.float64) / 4294967295.0


def fade(t):
    return t * t * (3 - 2 * t)


def noise3(x, y, z):
    xf0, yf0, zf0 = np.floor(x), np.floor(y), np.floor(z)
    xf, yf, zf = fade(x - xf0), fade(y - yf0), fade(z - zf0)
    xi, yi, zi = xf0.astype(np.int64), yf0.astype(np.int64), zf0.astype(np.int64)

    def c(a, b, d):
        return h3(xi + a, yi + b, zi + d)

    x00 = c(0, 0, 0) + (c(1, 0, 0) - c(0, 0, 0)) * xf
    x10 = c(0, 1, 0) + (c(1, 1, 0) - c(0, 1, 0)) * xf
    x01 = c(0, 0, 1) + (c(1, 0, 1) - c(0, 0, 1)) * xf
    x11 = c(0, 1, 1) + (c(1, 1, 1) - c(0, 1, 1)) * xf
    y0 = x00 + (x10 - x00) * yf
    y1 = x01 + (x11 - x01) * yf
    return y0 + (y1 - y0) * zf


def fbm(x, y, z, octaves):
    a, f, s, n = 0.5, 1.0, 0.0, 0.0
    for _ in range(octaves):
        s = s + a * noise3(x * f, y * f, z * f)
        n += a
        a *= 0.5
        f *= 2.03
    return s / n


def box1d(a, r, axis, wrap):
    n = 2 * r + 1
    pad = [(0, 0), (0, 0)]
    pad[axis] = (r, r)
    p = np.pad(a, pad, mode='wrap' if wrap else 'edge')
    c = np.cumsum(p, axis=axis, dtype=np.float64)
    zshape = list(c.shape)
    zshape[axis] = 1
    c = np.concatenate([np.zeros(zshape), c], axis=axis)
    hi = [slice(None)] * 2
    lo = [slice(None)] * 2
    hi[axis] = slice(n, None)
    lo[axis] = slice(0, -n)
    return ((c[tuple(hi)] - c[tuple(lo)]) / n).astype(np.float32)


def blur(a, r, passes):
    for _ in range(passes):
        a = box1d(a, r, 1, True)
        a = box1d(a, r, 0, False)
    return a


def arid(lon, lat):
    boxes = [(-17, 36, 14, 32, 11), (36, 58, 14, 32, 10), (118, 142, -31, -19, 11),
             (90, 116, 37, 48, 9), (-117, -103, 26, 39, 8), (-73, -67, -29, -17, 4), (54, 71, 27, 41, 9)]
    out = np.zeros_like(lon)
    for l0, l1, a0, a1, soft in boxes:
        dx = np.minimum(lon - l0, l1 - lon)
        dy = np.minimum(lat - a0, a1 - lat)
        out = np.maximum(out, sstep(0, soft, np.minimum(dx, dy)))
    return out


def main(out_dir):
    im = Image.new('L', (TW, TH), 0)
    d = ImageDraw.Draw(im)
    for p in LAND:
        pts = [((p[i] + 180) / 360 * TW, (90 - p[i + 1]) / 180 * TH) for i in range(0, len(p), 2)]
        d.polygon(pts, fill=255)
    m = np.asarray(im, dtype=np.float32) / 255.0
    near = blur(m, 3, 2)
    wide = blur(m, 9, 2)

    jj, ii = np.mgrid[0:TH, 0:TW]
    lon = (ii + 0.5) / TW * 2 * np.pi - np.pi
    lat = np.pi / 2 - (jj + 0.5) / TH * np.pi
    X, Y, Z = np.cos(lat) * np.cos(lon), np.cos(lat) * np.sin(lon), np.sin(lat)
    latd, lond = np.degrees(lat), np.degrees(lon)
    alat = np.abs(latd)

    nc = fbm(X * 9 + 3.1, Y * 9 + 1.7, Z * 9 + 8.3, 4)
    land = (near + (nc - 0.5) * 0.85) > 0.5
    h = fbm(X * 4 + 17, Y * 4 + 5, Z * 4 + 11, 5)
    cn = fbm(X * 2.3 + 20, Y * 2.3 + 9, Z * 2.3 + 4, 5)
    band = 0.78 + 0.22 * np.cos(lat * 6)
    cloud = sstep(0.5, 0.73, cn * band + 0.06) * 0.95

    # ---- land ----
    green_ice = sstep(-76, -66, lond) * (1 - sstep(-20, -10, lond)) * sstep(58, 68, latd)
    ice = np.where(latd < -63, 1.0,
                   np.maximum(green_ice, sstep(68, 78, alat + (h - 0.5) * 10)))
    moist = fbm(X * 2.4 + 5, Y * 2.4 + 2, Z * 2.4 + 9, 3)
    detail = fbm(X * 16 + 7, Y * 16 + 3, Z * 16 + 1, 4)
    # warp the desert regions so they get organic outlines instead of box edges
    wl = fbm(X * 3.2 + 50, Y * 3.2 + 8, Z * 3.2 + 21, 4)
    wa = fbm(X * 3.2 + 12, Y * 3.2 + 44, Z * 3.2 + 6, 4)
    dry_zone = arid(lond + (wl - 0.5) * 30, latd + (wa - 0.5) * 22)
    dry = np.maximum(dry_zone * (1.0 + 0.5 * (detail - 0.5) * 2) * 1.25,
                     np.clip((0.44 - moist) * 1.4, 0, 1) * sstep(8, 24, alat) * (1 - sstep(40, 55, alat)))
    dry = np.clip(dry, 0, 1)
    t1 = sstep(10, 36, alat)
    gr, gg, gb = mixc(28, 62, t1), mixc(78, 98, t1), mixc(36, 50, t1)
    t2 = sstep(42, 60, alat)
    gr, gg, gb = mixc(gr, 38, t2), mixc(gg, 64, t2), mixc(gb, 46, t2)
    t3 = sstep(60, 72, alat)
    gr, gg, gb = mixc(gr, 128, t3), mixc(gg, 126, t3), mixc(gb, 108, t3)
    gras = sstep(0.4, 0.7, moist) * 0.45 * (1 - t3)
    gr, gg, gb = mixc(gr, 92, gras), mixc(gg, 114, gras), mixc(gb, 58, gras)
    # steppe: dry-ish grassland turns olive-tan before it becomes true desert
    steppe = sstep(0.18, 0.55, dry) * (1 - sstep(0.55, 0.85, dry))
    gr, gg, gb = mixc(gr, 150, steppe * 0.7), mixc(gg, 140, steppe * 0.7), mixc(gb, 82, steppe * 0.7)
    dtone = sstep(0.35, 0.65, h)
    dr, dg, db = mixc(214, 168, dtone), mixc(178, 128, dtone), mixc(120, 86, dtone)
    dm = sstep(0.2, 0.65, dry)
    lr, lg, lb = mixc(gr, dr, dm), mixc(gg, dg, dm), mixc(gb, db, dm)
    # fine-grained variation so land is not a flat colour
    dv = 1 + (detail - 0.5) * 0.45
    lr, lg, lb = lr * dv, lg * dv, lb * dv
    rock = sstep(0.58, 0.74, h) * 0.75
    lr, lg, lb = mixc(lr, 112, rock), mixc(lg, 100, rock), mixc(lb, 88, rock)
    snow = sstep(0.72, 0.82, h) * sstep(20, 40, alat)
    lr, lg, lb = mixc(lr, 236, snow), mixc(lg, 240, snow), mixc(lb, 246, snow)
    lr, lg, lb = mixc(lr, 238, ice), mixc(lg, 244, ice), mixc(lb, 250, ice)
    sh = np.clip(1 + (np.roll(h, 1, axis=1) - np.roll(h, -1, axis=1)) * 2.6, 0.75, 1.25)
    lr, lg, lb = lr * sh, lg * sh, lb * sh

    li = fbm(X * 26 + 31, Y * 26 + 12, Z * 26 + 5, 3)
    pop = sstep(0.58, 0.80, li) * sstep(0.4, 0.95, near) * (1 - sstep(44, 58, alat)) * (1 - dry * 0.75)
    lights = np.where(land & (ice < 0.35) & (alat < 66), pop, 0.0)

    # ---- ocean ----
    s = sstep(0.0, 0.75, wide)
    orr, ogg, obb = mixc(7, 26, s), mixc(38, 100, s), mixc(86, 142, s)
    vn = (h - 0.5) * 16
    orr, ogg, obb = orr + vn * 0.3, ogg + vn * 0.6, obb + vn
    sea = sstep(72, 82, alat + (h - 0.5) * 9) * 0.92
    orr, ogg, obb = mixc(orr, 214, sea), mixc(ogg, 228, sea), mixc(obb, 238, sea)
    sea_flag = sea > 0.02

    R = np.where(land, lr, orr)
    G = np.where(land, lg, ogg)
    B = np.where(land, lb, obb)
    flag = np.where(land | sea_flag, 255.0, 0.0)

    color = np.clip(np.stack([R, G, B], -1), 0, 255).astype(np.uint8)
    aux = np.clip(np.stack([cloud * 255, lights * 255, flag], -1), 0, 255).astype(np.uint8)
    Image.fromarray(color, 'RGB').save(f'{out_dir}/earth_color.png', optimize=True)
    Image.fromarray(aux, 'RGB').save(f'{out_dir}/earth_aux.png', optimize=True)
    print('wrote textures to', out_dir)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.')
