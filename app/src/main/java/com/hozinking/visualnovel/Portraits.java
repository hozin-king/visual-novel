package com.hozinking.visualnovel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pemuatan portrait terpusat. Crop dilakukan di level Pixmap (koordinat
 * piksel top-left yang tidak ambigu), lalu Texture digambar penuh dengan
 * overload batch.draw() sederhana yang sudah terbukti tampil benar.
 *
 * Ini memperbaiki bug "portrait hitam": menggambar via
 * batch.draw(tex,x,y,w,h,srcX,srcY,srcW,srcH,...) memakai konvensi srcY yang
 * tidak sesuai asumsi kode lama sehingga region yang ter-sample adalah
 * bagian bawah gambar yang gelap.
 */
public final class Portraits {
    /** Rasio target portrait: 3:4, crop bias ke atas (wajah). */
    private static final float RATIO = 3f / 4f;

    private static final Map<String, Texture> cache = new HashMap<>();
    private static final Map<String, Texture> avatarCache = new HashMap<>();
    private static final Set<String> knownChars = new HashSet<>();
    private static final Set<String> unknownChars = new HashSet<>();

    private Portraits() { }

    /** Apakah karakter punya portrait (file <key>_normal.jpg ada). */
    public static boolean has(String key) {
        if (key == null || key.isEmpty()) return false;
        if (knownChars.contains(key)) return true;
        if (unknownChars.contains(key)) return false;
        boolean ok = Gdx.files.internal("portrait/" + key + "_normal.jpg").exists();
        (ok ? knownChars : unknownChars).add(key);
        return ok;
    }

    /**
     * Portrait karakter + ekspresi, sudah di-crop 3:4 dari atas (wajah).
     * Fallback ke ekspresi normal bila file ekspresi tidak ada.
     * Return null bila tidak ada file sama sekali.
     */
    public static Texture get(String key, String expr) {
        if (!has(key)) return null;
        String e = (expr == null || expr.isEmpty()) ? "normal" : expr;
        String path = "portrait/" + key + "_" + e + ".jpg";
        if (!cache.containsKey(path)) {
            Texture t = loadCropped(path);
            if (t == null && !"normal".equals(e)) {
                String fb = "portrait/" + key + "_normal.jpg";
                if (!cache.containsKey(fb)) cache.put(fb, loadCropped(fb));
                t = cache.get(fb);
            }
            cache.put(path, t); // boleh null: penanda gagal
        }
        return cache.get(path);
    }

    /** Avatar lingkaran 128px dari portrait normal (untuk Mode Chat). Null bila tak ada. */
    public static Texture getAvatar(String key) {
        if (!has(key)) return null;
        if (avatarCache.containsKey(key)) return avatarCache.get(key);
        Texture t = null;
        try {
            FileHandle fh = Gdx.files.internal("portrait/" + key + "_normal.jpg");
            Pixmap full = new Pixmap(fh);
            Pixmap work = new Pixmap(full.getWidth(), full.getHeight(), Pixmap.Format.RGBA8888);
            work.drawPixmap(full, 0, 0);
            full.dispose();
            int tw = work.getWidth(), th = work.getHeight();
            int s = Math.min(tw, th);
            int cx = (tw - s) / 2, cy = 0; // square dari atas (wajah)
            int S = 128;
            Pixmap out = new Pixmap(S, S, Pixmap.Format.RGBA8888);
            for (int y = 0; y < S; y++) {
                for (int x = 0; x < S; x++) {
                    float dx = (x + 0.5f) / S - 0.5f;
                    float dy = (y + 0.5f) / S - 0.5f;
                    if (dx * dx + dy * dy > 0.25f) continue; // luar lingkaran: transparan
                    int sx = cx + (int) (((x + 0.5f) / S) * s);
                    int sy = cy + (int) (((y + 0.5f) / S) * s);
                    out.drawPixel(x, y, work.getPixel(sx, sy));
                }
            }
            work.dispose();
            t = new Texture(out);
            out.dispose();
        } catch (Exception ignored) {
        }
        avatarCache.put(key, t);
        return t;
    }

    /** Muat JPG lalu crop 3:4 dari atas di level Pixmap. Null bila gagal. */
    private static Texture loadCropped(String path) {
        try {
            FileHandle fh = Gdx.files.internal(path);
            if (!fh.exists()) return null;
            Pixmap full = new Pixmap(fh);
            int tw = full.getWidth(), th = full.getHeight();
            int cw = tw, ch = (int) (tw / RATIO);
            if (ch > th) { ch = th; cw = (int) (th * RATIO); }
            int cx = (tw - cw) / 2, cy = 0; // bias ke atas (wajah)
            Pixmap crop = new Pixmap(cw, ch, Pixmap.Format.RGB888);
            crop.drawPixmap(full, 0, 0, cx, cy, cw, ch);
            full.dispose();
            Texture t = new Texture(crop);
            crop.dispose();
            return t;
        } catch (Exception e) {
            return null;
        }
    }
}
