package com.hozinking.visualnovel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Layar utama novel visual: background + portrait + dialogue box typewriter + pilihan.
 * Viewport logis 1280x720 (landscape).
 */
public class StoryScreen implements Screen, InputProcessor {
    private static final float W = 1280f, H = 720f;

    private final VnGame game;
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private final GlyphLayout layout = new GlyphLayout();

    private Script script;
    private final Map<String, Integer> flags = new HashMap<>();
    private int pc = 0;

    // Dialog aktif
    private String curText = "";
    private String curSpeaker = "";
    private String curExpr = "normal";
    private float shown = 0f;
    private boolean typing = false;
    private List<String> wrapped = new ArrayList<>();

    // Pilihan aktif
    private List<Script.Option> curOptions;
    private final List<float[]> optRects = new ArrayList<>();

    // Ending overlay
    private boolean showEnding = false;
    private String endingTitle = "";

    // Background + transisi
    private final Map<String, Texture> bgCache = new HashMap<>();
    private Texture bgTex, bgOld;
    private float bgFade = 1f;
    private float tintR = 1f, tintG = 1f, tintB = 1f;

    // Portrait (dimuat via Portraits: crop deterministik di level Pixmap)
    private Texture portraitTex;
    private String portraitKey = "";

    // Tombol MENU
    private final float menuBX = 1150f, menuBY = 662f, menuBW = 100f, menuBH = 44f;

    public StoryScreen(VnGame game) {
        this.game = game;
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new StretchViewport(W, H, camera);
        viewport.apply();
        centerCamera();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        ensureScript();
        game.requestOrientation(false); // mode klasik = landscape
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
    }

    /** Tengahkankan kamera ke dunia 1280x720 (StretchViewport tidak melakukannya sendiri). */
    private void centerCamera() {
        camera.position.set(W / 2f, H / 2f, 0f);
        camera.update();
    }

    /** Muat naskah sekali saja; aman dipanggil sebelum show() (mis. dari tombol Mulai). */
    private void ensureScript() {
        if (script == null) {
            try {
                String src = Gdx.files.internal("cerita.vn").readString("UTF-8");
                script = Script.parse(src);
            } catch (Exception e) {
                script = new Script();
            }
        }
    }

    /** Mulai dari awal (ending yang terbuka tetap tersimpan). */
    public void startNew() {
        ensureScript();
        flags.clear();
        game.save.clearSave();
        showEnding = false;
        curOptions = null;
        jumpTo("prolog");
    }

    /** Lanjutkan dari auto-save. */
    public void startFromSave() {
        ensureScript();
        flags.clear();
        flags.putAll(game.save.loadFlags());
        showEnding = false;
        curOptions = null;
        jumpTo(game.save.loadLabel());
    }

    // ------------------------------------------------------------ runner

    private void jumpTo(String label) {
        Integer idx = script.labels.get(label);
        pc = (idx != null) ? idx : 0;
        game.save.saveScene(label, flags);
        runBlocks();
    }

    private void runBlocks() {
        showEnding = false;
        curOptions = null;
        while (pc < script.blocks.size()) {
            Script.Block b = script.blocks.get(pc);
            switch (b.type) {
                case DIALOG:
                    if (b.cond != null && !b.cond.eval(flags)) { pc++; continue; }
                    startDialog(b);
                    return;
                case BG: setBg(b.arg); pc++; break;
                case STYLE: setStyle(b.arg); pc++; break;
                case BGM: game.audio.bgm(b.arg); pc++; break;
                case SFX: game.audio.sfx(b.arg); pc++; break;
                case SET:
                    for (Script.SetOp s : b.sets) s.apply(flags);
                    pc++;
                    break;
                case JUMP:
                    jumpTo(b.arg);
                    return;
                case IFJUMP: {
                    Script.Cond c = new Script.Cond();
                    c.flag = b.arg; c.op = b.ifOp; c.value = b.ifVal;
                    String tgt = c.eval(flags) ? b.text : b.arg2;
                    if (tgt == null || tgt.isEmpty()) { pc++; continue; }
                    jumpTo(tgt);
                    return;
                }
                case CHOICE:
                    showChoice(b);
                    return;
                case ENDING:
                    doEnding(b);
                    return;
            }
        }
        game.setScreen(game.titleScreen);
    }

    private void startDialog(Script.Block b) {
        curSpeaker = b.speaker;
        curExpr = (b.expr != null) ? b.expr : "normal";
        curText = b.text;
        shown = 0f;
        typing = true;
        wrapped = wrap(curText, game.font("dialog"), 1140f);
        updatePortrait();
    }

    private void showChoice(Script.Block b) {
        curOptions = new ArrayList<>();
        for (Script.Option o : b.options) {
            if (o.cond == null || o.cond.eval(flags)) curOptions.add(o);
        }
        typing = false;
    }

    private void doEnding(Script.Block b) {
        game.save.unlockEnding(b.arg);
        game.audio.bgm("-");
        game.audio.sfx("chime");
        endingTitle = b.arg2;
        showEnding = true;
        curOptions = null;
    }

    private void advance() {
        pc++;
        runBlocks();
    }

    private void choose(Script.Option o) {
        for (Script.SetOp s : o.sets) s.apply(flags);
        game.audio.sfx("beep");
        jumpTo(o.target);
    }

    // ------------------------------------------------------------ aset

    private void setBg(String name) {
        if (name == null || name.isEmpty()) return;
        Texture t = bgCache.get(name);
        if (t == null) {
            try {
                t = new Texture(Gdx.files.internal("bg/" + name));
                bgCache.put(name, t);
            } catch (Exception e) {
                return;
            }
        }
        if (t != bgTex) {
            bgOld = bgTex;
            bgTex = t;
            bgFade = 0f;
        }
    }

    private void setStyle(String name) {
        if ("memory".equals(name)) { tintR = 1.02f; tintG = 0.93f; tintB = 0.76f; }
        else if ("crisis".equals(name)) { tintR = 0.72f; tintG = 0.76f; tintB = 0.88f; }
        else { tintR = 1f; tintG = 1f; tintB = 1f; }
    }

    private void updatePortrait() {
        String key = "";
        if (curSpeaker != null && !curSpeaker.isEmpty()) {
            String first = curSpeaker.split(" ")[0].toLowerCase();
            if (Portraits.has(first)) key = first;
        }
        portraitKey = key;
        portraitTex = key.isEmpty() ? null : Portraits.get(key, curExpr);
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.05f);
        if (typing) {
            shown += dt * 45f;
            if (shown >= curText.length()) { shown = curText.length(); typing = false; }
        }
        if (bgFade < 1f) bgFade = Math.min(1f, bgFade + dt * 2.8f);

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawBg();
        drawPortrait();
        batch.end();

        drawUi();

        // Indikator ketuk
        if (!typing && curOptions == null && !showEnding && !curText.isEmpty()) {
            batch.begin();
            game.font("ui").setColor(1f, 0.85f, 0.4f, 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 300.0));
            game.font("ui").draw(batch, "▼", 1210f, 60f);
            game.font("ui").setColor(Color.WHITE);
            batch.end();
        }
    }

    private void drawBg() {
        if (bgOld != null && bgFade < 1f) {
            batch.setColor(tintR, tintG, tintB, 1f - bgFade);
            drawCover(bgOld);
        }
        if (bgTex != null) {
            batch.setColor(tintR, tintG, tintB, bgOld != null && bgFade < 1f ? bgFade : 1f);
            drawCover(bgTex);
        }
        batch.setColor(Color.WHITE);
        if (bgFade >= 1f) bgOld = null;
    }

    private void drawCover(Texture t) {
        float tw = t.getWidth(), th = t.getHeight();
        float s = Math.max(W / tw, H / th);
        float dw = tw * s, dh = th * s;
        batch.draw(t, (W - dw) / 2f, (H - dh) / 2f, dw, dh);
    }

    private void drawPortrait() {
        if (portraitTex == null || showEnding) return;
        // Texture sudah di-crop 3:4 oleh Portraits: gambar penuh, overload sederhana.
        float x = 70f, y = 250f, w = 330f, h = 440f;
        batch.setColor(tintR, tintG, tintB, 1f);
        batch.draw(portraitTex, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    private void drawUi() {
        shapes.setProjectionMatrix(camera.combined);

        // Bingkai portrait
        if (portraitTex != null && !showEnding) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, 0.55f);
            shapes.rect(64f, 244f, 342f, 452f);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0.85f, 0.7f, 0.35f, 0.9f);
            shapes.rect(64f, 244f, 342f, 452f);
            shapes.end();
        }

        if (showEnding) { drawEnding(); return; }

        // Kotak dialog
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.82f);
        shapes.rect(40f, 30f, 1200f, 200f);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.85f, 0.7f, 0.35f, 0.55f);
        shapes.rect(40f, 30f, 1200f, 200f);
        shapes.end();

        batch.begin();
        // Nameplate
        if (curSpeaker != null && !curSpeaker.isEmpty()) {
            layout.setText(game.font("speaker"), curSpeaker);
            float pw = layout.width + 44f;
            batch.end();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.85f, 0.7f, 0.35f, 1f);
            shapes.rect(60f, 218f, pw, 46f);
            shapes.end();
            batch.begin();
            game.font("speaker").setColor(0.12f, 0.1f, 0.08f, 1f);
            game.font("speaker").draw(batch, curSpeaker, 82f, 252f);
            game.font("speaker").setColor(Color.WHITE);
        }
        // Teks dialog (typewriter)
        String vis = curText.substring(0, Math.min(curText.length(), (int) shown));
        List<String> lines = wrap(vis, game.font("dialog"), 1140f);
        float ty = 195f;
        game.font("dialog").setColor(Color.WHITE);
        for (String ln : lines) {
            if (ty < 55f) break;
            game.font("dialog").draw(batch, ln, 70f, ty);
            ty -= 42f;
        }
        // Tombol MENU
        game.font("ui").setColor(1f, 1f, 1f, 0.75f);
        game.font("ui").draw(batch, "MENU", menuBX + 18f, menuBY + 30f);
        game.font("ui").setColor(Color.WHITE);
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.4f);
        shapes.rect(menuBX, menuBY, menuBW, menuBH);
        shapes.end();

        if (curOptions != null) drawChoices();
    }

    private void drawChoices() {
        // Redupkan layar
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.45f);
        shapes.rect(0f, 0f, W, H);
        shapes.end();

        optRects.clear();
        int n = curOptions.size();
        float bw = 760f, bh = 84f, gap = 18f;
        float totalH = n * bh + (n - 1) * gap;
        float y0 = (H + totalH) / 2f - bh;

        batch.begin();
        for (int i = 0; i < n; i++) {
            Script.Option o = curOptions.get(i);
            float bx = (W - bw) / 2f, by = y0 - i * (bh + gap);
            optRects.add(new float[]{bx, by, bw, bh});
            batch.end();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.08f, 0.07f, 0.06f, 0.95f);
            shapes.rect(bx, by, bw, bh);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0.85f, 0.7f, 0.35f, 1f);
            shapes.rect(bx, by, bw, bh);
            shapes.end();
            batch.begin();
            List<String> lines = wrap(o.text, game.font("choice"), bw - 50f);
            float ty = by + bh / 2f + (lines.size() - 1) * 19f;
            game.font("choice").setColor(1f, 0.96f, 0.88f, 1f);
            for (String ln : lines) {
                layout.setText(game.font("choice"), ln);
                game.font("choice").draw(batch, ln, bx + (bw - layout.width) / 2f, ty);
                ty -= 38f;
            }
            game.font("choice").setColor(Color.WHITE);
        }
        batch.end();
    }

    private void drawEnding() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.88f);
        shapes.rect(0f, 0f, W, H);
        shapes.end();
        batch.begin();
        layout.setText(game.font("ui"), "— TAMAT —");
        game.font("ui").setColor(0.85f, 0.7f, 0.35f, 1f);
        game.font("ui").draw(batch, "— TAMAT —", (W - layout.width) / 2f, 420f);
        List<String> lines = wrap(endingTitle, game.font("title"), 1000f);
        float ty = 330f;
        game.font("title").setColor(Color.WHITE);
        for (String ln : lines) {
            layout.setText(game.font("title"), ln);
            game.font("title").draw(batch, ln, (W - layout.width) / 2f, ty);
            ty -= 84f;
        }
        layout.setText(game.font("ui"), "Ketuk di mana saja untuk kembali ke judul");
        game.font("ui").setColor(1f, 1f, 1f, 0.6f);
        game.font("ui").draw(batch, "Ketuk di mana saja untuk kembali ke judul", (W - layout.width) / 2f, 180f);
        game.font("ui").setColor(Color.WHITE);
        batch.end();
    }

    private List<String> wrap(String text, BitmapFont font, float maxW) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                layout.setText(font, test);
                if (layout.width > maxW && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    // ------------------------------------------------------------ input

    private float[] toWorld(int sx, int sy) {
        com.badlogic.gdx.math.Vector3 v = new com.badlogic.gdx.math.Vector3(sx, sy, 0);
        camera.unproject(v, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return new float[]{v.x, v.y};
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pointer != 0) return false;
        float[] p = toWorld(screenX, screenY);
        float x = p[0], y = p[1];

        if (showEnding) {
            game.setScreen(game.titleScreen);
            return true;
        }
        // Tombol MENU
        if (x >= menuBX && x <= menuBX + menuBW && y >= menuBY && y <= menuBY + menuBH) {
            game.setScreen(game.titleScreen);
            return true;
        }
        if (curOptions != null) {
            for (int i = 0; i < optRects.size() && i < curOptions.size(); i++) {
                float[] r = optRects.get(i);
                if (x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) {
                    choose(curOptions.get(i));
                    return true;
                }
            }
            return true;
        }
        if (typing) {
            shown = curText.length();
            typing = false;
        } else {
            game.audio.sfx("beep");
            advance();
        }
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
            game.setScreen(game.titleScreen);
            return true;
        }
        if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) {
            touchDown(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, 0, 0);
            return true;
        }
        return false;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchUp(int sx, int sy, int p, int b) { return false; }
    @Override public boolean touchDragged(int sx, int sy, int p) { return false; }
    @Override public boolean mouseMoved(int sx, int sy) { return false; }
    @Override public boolean scrolled(float a, float b) { return false; }
    @Override public boolean touchCancelled(int sx, int sy, int p, int b) { return false; }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        centerCamera();
    }

    @Override public void pause() { }
    @Override public void resume() { }

    @Override
    public void hide() {
        Gdx.input.setCatchKey(Input.Keys.BACK, false);
    }

    @Override
    public void dispose() {
        for (Texture t : bgCache.values()) t.dispose();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
    }
}
