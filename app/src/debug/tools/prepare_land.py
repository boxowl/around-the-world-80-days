#!/usr/bin/env python3
"""V21 globe prototype: Natural Earth land -> compact binary asset.

Source data (NOT committed to Git, download manually):
  https://github.com/nvkelso/natural-earth-vector (Natural Earth, public domain,
  https://www.naturalearthdata.com/about/terms-of-use/)
  ne_50m_land.geojson (1:50m land polygons)

Pipeline: drop micro-islands -> Douglas-Peucker simplify in lon/lat degrees
-> force ring orientation (exterior CCW, holes CW) -> quantize to 16 bit
-> delta-encode with zigzag varints -> app/src/debug/assets/ne_land.bin

Binary format (little-endian):
  4s   magic "NEL1"
  uv   polygon count
  per polygon:
    uv   ring count
    per ring:
      uv   flags (bit0 = interior ring / hole)
      uv   point count (closing point omitted, ring is implicitly closed)
      sv   first quantized lon, sv first quantized lat
      then (point count - 1) pairs of sv deltas (dlon, dlat)
Quantization: lon q = round((lon+180)/360*65535) mod 65536 (wraps, deltas are
chosen minimal mod 65536); lat q = clamp(round((lat+90)/180*65535), 0, 65535).
uv = unsigned LEB128, sv = zigzag(uv).

Usage: python3 prepare_land.py ne_50m_land.geojson ../assets/ne_land.bin [tol_deg]
"""
import json
import struct
import sys

MAGIC = b"NEL1"


def dp_simplify(points, tol):
    """Douglas-Peucker on a closed ring (list of (lon, lat), last == first)."""
    if len(points) <= 4:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        if b <= a + 1:
            continue
        ax, ay = points[a]
        bx, by = points[b]
        dx, dy = bx - ax, by - ay
        seg_len2 = dx * dx + dy * dy
        worst, worst_d = -1, tol
        for i in range(a + 1, b):
            px, py = points[i]
            if seg_len2 == 0:
                d = ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            else:
                t = ((px - ax) * dx + (py - ay) * dy) / seg_len2
                t = max(0.0, min(1.0, t))
                ex, ey = ax + t * dx, ay + t * dy
                d = ((px - ex) ** 2 + (py - ey) ** 2) ** 0.5
            if d > worst_d:
                worst, worst_d = i, d
        if worst >= 0:
            keep[worst] = True
            stack.append((a, worst))
            stack.append((worst, b))
    return [p for p, k in zip(points, keep) if k]


def signed_area(ring):
    area = 0.0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
        area += (x2 - x1) * (y2 + y1)
    return area / 2.0


def uvarint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def svarint(n):
    return uvarint((n << 1) ^ (n >> 63) if n >= 0 else ((-n) << 1) - 1)


def q_lon(lon):
    return int(round((lon + 180.0) / 360.0 * 65535.0)) % 65536


def q_lat(lat):
    return max(0, min(65535, int(round((lat + 90.0) / 180.0 * 65535.0))))


def main():
    src, dst = sys.argv[1], sys.argv[2]
    tol = float(sys.argv[3]) if len(sys.argv) > 3 else 0.45
    min_extent = float(sys.argv[4]) if len(sys.argv) > 4 else 0.35

    data = json.load(open(src))
    out = bytearray(MAGIC)
    polygons_out = []
    stats = {"rings_in": 0, "rings_out": 0, "pts_in": 0, "pts_out": 0, "holes": 0}

    for feat in data["features"]:
        geom = feat["geometry"]
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        for poly in polys:
            rings = []
            for ri, ring in enumerate(poly):
                stats["rings_in"] += 1
                stats["pts_in"] += len(ring)
                lons = [p[0] for p in ring]
                lats = [p[1] for p in ring]
                if ri > 0:
                    stats["holes"] += 1
                # drop micro-islands (never holes)
                if ri == 0 and (max(lons) - min(lons) < min_extent) and \
                        (max(lats) - min(lats) < min_extent):
                    continue
                ring = [(p[0], p[1]) for p in ring]
                if ring[0] != ring[-1]:
                    ring.append(ring[0])
                ring = dp_simplify(ring, tol)
                if len(ring) < 4:
                    continue
                ring = ring[:-1]  # drop duplicated closing point
                # enforce orientation: exterior CCW, hole CW (lon/lat plane)
                area = signed_area(ring + [ring[0]])
                want_ccw = (ri == 0)
                if (area > 0) != want_ccw:
                    ring.reverse()
                # Note: a segment may jump +180 -> -180 (Natural Earth splits
                # polygons at the antimeridian). That is fine: quantization
                # wraps mod 65536 and the renderer works on 3D unit vectors.
                rings.append((ri > 0, ring))
                stats["rings_out"] += 1
                stats["pts_out"] += len(ring)
            if rings:
                polygons_out.append(rings)

    buf = bytearray()
    buf += uvarint(len(polygons_out))
    for rings in polygons_out:
        buf += uvarint(len(rings))
        for is_hole, ring in rings:
            buf += uvarint(1 if is_hole else 0)
            buf += uvarint(len(ring))
            prev_lo = prev_la = None
            for lon, lat in ring:
                lo, la = q_lon(lon), q_lat(lat)
                if prev_lo is None:
                    buf += svarint(lo if lo < 32768 else lo - 65536)
                    buf += svarint(la - 32768)
                else:
                    dlo = (lo - prev_lo + 32768) % 65536 - 32768
                    dla = la - prev_la
                    buf += svarint(dlo)
                    buf += svarint(dla)
                prev_lo, prev_la = lo, la
    out += buf
    with open(dst, "wb") as f:
        f.write(out)
    print("tolerance %.2f deg, min extent %.2f deg" % (tol, min_extent))
    print("rings %d -> %d, points %d -> %d, holes %d, polygons %d" % (
        stats["rings_in"], stats["rings_out"], stats["pts_in"],
        stats["pts_out"], stats["holes"], len(polygons_out)))
    print("asset size: %d bytes (%.1f KB)" % (len(out), len(out) / 1024.0))


if __name__ == "__main__":
    main()
