package com.hozinking.visualnovel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Auto-save per scene (label + flags) dan galeri ending.
 * Disimpan di Preferences "vn_save". Full offline.
 */
public class SaveManager {
    private final Preferences prefs;

    public SaveManager() {
        prefs = Gdx.app.getPreferences("vn_save");
    }

    public boolean hasSave() {
        return prefs.contains("label");
    }

    /** Simpan posisi: label tujuan + semua flags. Dipanggil tiap tiba di label. */
    public void saveScene(String label, Map<String, Integer> flags) {
        prefs.putString("label", label);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : flags.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.putString("flags", sb.toString());
        prefs.flush();
    }

    public String loadLabel() {
        return prefs.getString("label", "prolog");
    }

    public Map<String, Integer> loadFlags() {
        Map<String, Integer> flags = new HashMap<>();
        String s = prefs.getString("flags", "");
        if (!s.isEmpty()) {
            for (String part : s.split(";")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    try {
                        flags.put(kv[0], Integer.parseInt(kv[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return flags;
    }

    public void clearSave() {
        prefs.remove("label");
        prefs.remove("flags");
        prefs.flush();
    }

    /** Mode tampilan: "chat" (default sejak v1.4) atau "klasik". */
    public String getMode() {
        return prefs.getString("vn_mode", "chat");
    }

    public void setMode(String mode) {
        prefs.putString("vn_mode", mode);
        prefs.flush();
    }

    /** Ending yang sudah terbuka (id). */
    public Set<String> loadEndings() {
        Set<String> set = new HashSet<>();
        String s = prefs.getString("endings", "");
        if (!s.isEmpty()) {
            for (String id : s.split(",")) {
                if (!id.isEmpty()) set.add(id);
            }
        }
        return set;
    }

    public void unlockEnding(String id) {
        Set<String> set = loadEndings();
        if (set.add(id)) {
            StringBuilder sb = new StringBuilder();
            for (String e : set) {
                if (sb.length() > 0) sb.append(',');
                sb.append(e);
            }
            prefs.putString("endings", sb.toString());
            prefs.flush();
        }
    }
}
