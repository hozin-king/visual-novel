package com.hozinking.visualnovel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

import java.util.HashMap;
import java.util.Map;

/** BGM (loop) + SFX. Semua aset lokal, full offline. */
public class AudioMan {
    private final Map<String, Sound> sfx = new HashMap<>();
    private Music bgm;
    private String bgmName = "";

    public void load() {
        String[] names = {"jingle", "wa", "telpon", "beep", "static", "petir", "keroncong"};
        for (String n : names) {
            try {
                sfx.put(n, Gdx.audio.newSound(Gdx.files.internal("audio/" + n + ".ogg")));
            } catch (Exception ignored) {
            }
        }
    }

    /** Ganti BGM (loop). Nama tanpa ekstensi; "-" atau kosong = hentikan. */
    public void bgm(String name) {
        if (name == null || name.equals("-") || name.isEmpty()) {
            stopBgm();
            return;
        }
        if (name.equals(bgmName) && bgm != null && bgm.isPlaying()) return;
        stopBgm();
        try {
            bgm = Gdx.audio.newMusic(Gdx.files.internal("audio/" + name + ".ogg"));
            bgm.setLooping(true);
            bgm.setVolume(0.55f);
            bgm.play();
            bgmName = name;
        } catch (Exception ignored) {
            bgm = null;
            bgmName = "";
        }
    }

    public void stopBgm() {
        if (bgm != null) {
            try {
                bgm.stop();
                bgm.dispose();
            } catch (Exception ignored) {
            }
            bgm = null;
        }
        bgmName = "";
    }

    public void sfx(String name) {
        Sound s = sfx.get(name);
        if (s != null) {
            try {
                s.play(0.9f);
            } catch (Exception ignored) {
            }
        }
    }

    public void dispose() {
        stopBgm();
        for (Sound s : sfx.values()) {
            try {
                s.dispose();
            } catch (Exception ignored) {
            }
        }
        sfx.clear();
    }
}
