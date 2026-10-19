package com.hozinking.visualnovel;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

import java.util.HashMap;
import java.util.Map;

/** Lentera Malam — visual novel engine (libGDX). */
public class VnGame extends Game {
    public SaveManager save;
    public AudioMan audio;
    public final Map<String, BitmapFont> fonts = new HashMap<>();
    public TitleScreen titleScreen;
    public StoryScreen storyScreen;
    public GalleryScreen galleryScreen;

    /** Daftar ending untuk galeri: id -> judul (tanpa spoiler di luar judul). */
    public static final String[][] ENDINGS = {
            {"lentera_menyala", "Lentera Tetap Menyala"},
            {"siaran_perpisahan", "Siaran Perpisahan"},
            {"frekuensi_kosong", "Frekuensi Kosong"},
            {"tanda_tangan", "Tanda Tangan"},
    };

    @Override
    public void create() {
        save = new SaveManager();
        audio = new AudioMan();
        audio.load();
        loadFonts();
        titleScreen = new TitleScreen(this);
        storyScreen = new StoryScreen(this);
        galleryScreen = new GalleryScreen(this);
        setScreen(titleScreen);
    }

    private void loadFonts() {
        try {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("font/Merriweather.ttf"));
            fonts.put("title", gen.generateFont(param(72)));
            fonts.put("dialog", gen.generateFont(param(33)));
            fonts.put("speaker", gen.generateFont(param(30)));
            fonts.put("choice", gen.generateFont(param(30)));
            fonts.put("ui", gen.generateFont(param(26)));
            fonts.put("small", gen.generateFont(param(22)));
            gen.dispose();
        } catch (Exception e) {
            // Fallback: font default bila TTF gagal dimuat
            BitmapFont def = new BitmapFont();
            fonts.put("title", def); fonts.put("dialog", def); fonts.put("speaker", def);
            fonts.put("choice", def); fonts.put("ui", def); fonts.put("small", def);
        }
    }

    private FreeTypeFontGenerator.FreeTypeFontParameter param(int size) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        p.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        p.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        return p;
    }

    public BitmapFont font(String name) {
        BitmapFont f = fonts.get(name);
        return f != null ? f : fonts.get("ui");
    }

    @Override
    public void dispose() {
        for (BitmapFont f : fonts.values()) {
            try { f.dispose(); } catch (Exception ignored) {}
        }
        if (audio != null) audio.dispose();
        super.dispose();
    }
}
