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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mode Chat ala WhatsApp: layar portrait 720x1280, cerita tampil sebagai
 * chat bubbles. Kiri = karakter lain (avatar lingkaran + nama berwarna),
 * kanan = protagonis (narator first-person naskah), tengah = narasi.
 * Pilihan tampil sebagai bubble opsi yang bisa di-tap.
 */
public class ChatScreen implements Screen, InputProcessor {
    private static final float W = 720f, H = 1280f;
    private static final float TOP_H = 112f, BOT_H = 88f;
    /** Narator first-person di naskah -> bubble kanan (pesan sendiri). */
    private static final String PROTAGONIST = "bagas";
    private static final float TYPE_CPS = 45f;

    private static final int K_OTHER = 0, K_OWN = 1, K_SYS = 2, K_OPT = 3;

    private final VnGame game;
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private final GlyphLayout layout = new GlyphLayout();

    // Runner naskah (logika sama seperti StoryScreen)
    private Script script;
    private final Map<String, Integer> flags = new HashMap<>();
    private int pc = 0;

    private static class Msg {
        int kind;
        String name = "";
        float[] nameRgb = {1f, 1f, 1f};
        String avatarKey; // null -> lingkaran inisial
        String text = "";
        List<String> lines = new ArrayList<>();
        float w, h;
        float ry; // posisi bawah bubble (koordinat layar, sudah termasuk scroll)
        Script.Option option; // K_OPT
    }
    private final List<Msg> history = new ArrayList<>();
    private Msg typingMsg;
    private float shown = 0f;
    private boolean typing = false;
    private float scrollY = 0f;
    private float contentH = 0f;
    private boolean showEnding = false;
    private String endingTitle = "";

    // Bedakan tap vs scroll
    private float downX, downY, lastY, dragDist;
    private boolean touching = false;

    private static final float[][] NAME_COLORS = {
        {0.35f, 0.9f, 0.85f}, {1f, 0.72f, 0.35f}, {0.78f, 0.62f, 1f},
        {1f, 0.58f, 0.62f}, {0.6f, 0.88f, 0.45f}, {0.55f, 0.8f, 1f},
    };

    public ChatScreen(VnGame game) {
        this.game = game;
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new StretchViewport(W, H, camera);
        viewport.apply();
        camera.position.set(W / 2f, H / 2f, 0f);
        camera.update();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        ensureScript();
        game.requestOrientation(true); // mode chat = portrait
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
    }

    private void ensureScript() {
        if (script == null) {
            try {
                script = Script.parse(Gdx.files.internal("cerita.vn").readString("UTF-8"));
            } catch (Exception e) {
                script = new Script();
            }
        }
    }

    public void startNew() {
        ensureScript();
        flags.clear();
        game.save.clearSave();
        showEnding = false;
        history.clear();
        typingMsg = null;
        typing = false;
        scrollY = 0f;
        contentH = 0f;
        jumpTo("prolog");
    }

    public void startFromSave() {
        ensureScript();
        flags.clear();
        flags.putAll(game.save.loadFlags());
        showEnding = false;
        history.clear();
        typingMsg = null;
        typing = false;
        scrollY = 0f;
        contentH = 0f;
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
        while (pc < script.blocks.size()) {
            Script.Block b = script.blocks.get(pc);
            switch (b.type) {
                case DIALOG:
                    if (b.cond != null && !b.cond.eval(flags)) { pc++; continue; }
                    startDialog(b);
                    return;
                case BG:
                case STYLE:
                    pc++; break; // mode chat: tanpa visual background
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
        Msg m = new Msg();
        m.text = b.text;
        if (b.speaker == null || b.speaker.isEmpty()) {
            m.kind = K_SYS;
        } else {
            String first = b.speaker.split(" ")[0].toLowerCase();
            if (first.equals(PROTAGONIST)) {
                m.kind = K_OWN;
            } else {
                m.kind = K_OTHER;
                m.name = b.speaker;
                m.avatarKey = Portraits.has(first) ? first : null;
                float[][] nc = NAME_COLORS;
                float[] c = nc[Math.abs(b.speaker.hashCode()) % nc.length];
                m.nameRgb = c;
            }
        }
        layoutMsg(m);
        history.add(m);
        typingMsg = m;
        shown = 0f;
        typing = true;
        recomputeContentH();
        snapBottom();
    }

    private void showChoice(Script.Block b) {
        typing = false;
        typingMsg = null;
        for (Script.Option o : b.options) {
            if (o.cond != null && !o.cond.eval(flags)) continue;
            Msg m = new Msg();
            m.kind = K_OPT;
            m.text = o.text;
            m.option = o;
            layoutMsg(m);
            history.add(m);
        }
        recomputeContentH();
        snapBottom();
    }

    private void doEnding(Script.Block b) {
        game.save.unlockEnding(b.arg);
        game.audio.bgm("-");
        game.audio.sfx("chime");
        endingTitle = b.arg2;
        showEnding = true;
    }

    private void advance() {
        pc++;
        runBlocks();
    }

    private void choose(Script.Option o) {
        for (Script.SetOp s : o.sets) s.apply(flags);
        game.audio.sfx("beep");
        // Tampilkan pilihan sebagai bubble sendiri (seperti membalas chat)
        Msg m = new Msg();
        m.kind = K_OWN;
        m.text = o.text;
        layoutMsg(m);
        history.add(m);
        recomputeContentH();
        jumpTo(o.target);
    }

    // ------------------------------------------------------------ layout chat

    private void layoutMsg(Msg m) {
        BitmapFont f = game.font("dialog");
        float maxW = (m.kind == K_SYS) ? 600f : 540f;
        if (m.kind == K_OPT) maxW = 600f;
        List<String> lines = wrap(m.text, f, maxW - 48f);
        m.lines = lines;
        float maxLine = 0f;
        for (String s : lines) {
            layout.setText(f, s);
            if (layout.width > maxLine) maxLine = layout.width;
        }
        m.w = Math.min(maxW, maxLine + 48f);
        if (m.w < 90f) m.w = 90f;
        float lh = 46f;
        m.h = 26f + lines.size() * lh + 14f;
        if (m.kind == K_OTHER) m.h += 36f; // header nama
        if (m.kind == K_OPT) m.h += 10f;
    }

    private void recomputeContentH() {
        float h = 20f;
        for (Msg m : history) h += m.h + 14f;
        contentH = h;
    }

    private float maxScroll() {
        float viewH = (H - TOP_H) - BOT_H;
        return Math.max(0f, contentH - viewH);
    }

    private void snapBottom() {
        float ms = maxScroll();
        if (ms - scrollY < 160f) scrollY = ms;
        scrollY = Math.max(0f, Math.min(scrollY, ms));
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
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.05f);
        if (typing && typingMsg != null) {
            shown += dt * TYPE_CPS;
            if (shown >= typingMsg.text.length()) {
                shown = typingMsg.text.length();
                typing = false;
            }
        }

        Gdx.gl.glClearColor(0.043f, 0.078f, 0.102f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Hitung posisi tiap bubble (top-down)
        float y = H - TOP_H - 20f + scrollY;
        for (Msg m : history) {
            y -= (m.h + 14f);
            m.ry = y;
        }

        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);

        drawBackground();
        drawTopBar(false);
        for (Msg m : history) drawBubble(m);
        drawBottomHint(false);
        if (showEnding) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, 0.88f);
            shapes.rect(0f, 0f, W, H);
            shapes.end();
        }

        batch.begin();
        drawTopBar(true);
        for (Msg m : history) drawMsgContent(m);
        drawBottomHint(true);
        if (showEnding) drawEnding();
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawBackground() {
        // Pola titik samar ala wallpaper chat
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, 1f, 1f, 0.035f);
        for (float gx = 45f; gx < W; gx += 90f) {
            for (float gy = 120f; gy < H - TOP_H; gy += 90f) {
                shapes.circle(gx, gy, 2.5f, 8);
            }
        }
        shapes.end();
    }

    private void drawTopBar(boolean text) {
        if (!text) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.122f, 0.173f, 0.204f, 1f);
            shapes.rect(0f, H - TOP_H, W, TOP_H);
            shapes.end();
        } else {
            game.font("ui").setColor(1f, 1f, 1f, 0.9f);
            game.font("ui").draw(batch, "\u2039", 30f, H - 42f);
            layout.setText(game.font("choice"), "Mode Chat");
            game.font("choice").setColor(Color.WHITE);
            game.font("choice").draw(batch, "Mode Chat", (W - layout.width) / 2f, H - 46f);
            game.font("choice").setColor(Color.WHITE);
        }
    }

    private void drawBottomHint(boolean text) {
        if (!text) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.043f, 0.078f, 0.102f, 1f);
            shapes.rect(0f, 0f, W, BOT_H);
            shapes.end();
        } else if (!showEnding) {
            layout.setText(game.font("small"), "Ketuk untuk lanjutkan \u25B8");
            game.font("small").setColor(1f, 1f, 1f, 0.4f);
            game.font("small").draw(batch, "Ketuk untuk lanjutkan \u25B8",
                    (W - layout.width) / 2f, 52f);
            game.font("small").setColor(Color.WHITE);
        }
    }

    /** Bubble sudut membulat via rect + 4 lingkaran sudut. */
    private void bubble(float x, float y, float w, float h, float r,
                        float cr, float cg, float cb, float a) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(cr, cg, cb, a);
        shapes.rect(x + r, y, w - 2f * r, h);
        shapes.rect(x, y + r, w, h - 2f * r);
        shapes.circle(x + r, y + r, r, 12);
        shapes.circle(x + w - r, y + r, r, 12);
        shapes.circle(x + r, y + h - r, r, 12);
        shapes.circle(x + w - r, y + h - r, r, 12);
        shapes.end();
    }

    private void drawBubble(Msg m) {
        float r = 22f;
        if (m.kind == K_OWN) {
            float x = W - 16f - m.w;
            bubble(x, m.ry, m.w, m.h, r, 0f, 0.36f, 0.29f, 1f);
        } else if (m.kind == K_OTHER) {
            float x = 96f;
            bubble(x, m.ry, m.w, m.h, r, 0.122f, 0.173f, 0.204f, 1f);
        } else if (m.kind == K_SYS) {
            float x = (W - m.w) / 2f;
            bubble(x, m.ry, m.w, m.h, 14f, 0.094f, 0.133f, 0.161f, 1f);
        } else { // K_OPT
            float x = (W - m.w) / 2f;
            bubble(x - 3f, m.ry - 3f, m.w + 6f, m.h + 6f, r, 0.85f, 0.7f, 0.35f, 1f);
            bubble(x, m.ry, m.w, m.h, r, 0.10f, 0.11f, 0.10f, 1f);
        }
    }

    private void drawMsgContent(Msg m) {
        BitmapFont f = game.font("dialog");
        List<String> lines = m.lines;
        String visText = m.text;
        if (m == typingMsg && typing) {
            visText = m.text.substring(0, Math.min(m.text.length(), (int) shown));
            lines = wrap(visText, f, (m.kind == K_SYS ? 600f : 540f) - 48f);
        }
        float bx;
        if (m.kind == K_OWN) bx = W - 16f - m.w;
        else if (m.kind == K_OTHER) bx = 96f;
        else bx = (W - m.w) / 2f;

        // Avatar lingkaran (kiri)
        if (m.kind == K_OTHER) {
            float ax = 16f, ay = m.ry + m.h - 72f, as = 64f;
            Texture av = m.avatarKey != null ? Portraits.getAvatar(m.avatarKey) : null;
            if (av != null) {
                batch.draw(av, ax, ay, as, as);
            } else {
                // Fallback: lingkaran inisial
                game.font("choice").setColor(m.nameRgb[0], m.nameRgb[1], m.nameRgb[2], 1f);
                String init = m.name.isEmpty() ? "?" : m.name.substring(0, 1).toUpperCase();
                layout.setText(game.font("choice"), init);
                game.font("choice").draw(batch, init,
                        ax + (as - layout.width) / 2f, ay + 44f);
                game.font("choice").setColor(Color.WHITE);
            }
        }

        float tx = bx + 24f;
        float ty = m.ry + m.h - 22f;
        if (m.kind == K_OTHER) {
            // Header nama
            game.font("speaker").setColor(m.nameRgb[0], m.nameRgb[1], m.nameRgb[2], 1f);
            game.font("speaker").draw(batch, m.name, tx, ty);
            game.font("speaker").setColor(Color.WHITE);
            ty -= 40f;
        }
        f.setColor(m.kind == K_SYS ? 0.85f : 1f, m.kind == K_SYS ? 0.9f : 1f,
                m.kind == K_SYS ? 0.95f : 1f, m.kind == K_SYS ? 0.85f : 1f);
        for (String ln : lines) {
            f.draw(batch, ln, tx, ty);
            ty -= 46f;
        }
        f.setColor(Color.WHITE);
    }

    private void drawEnding() {
        layout.setText(game.font("ui"), "\u2014 TAMAT \u2014");
        game.font("ui").setColor(0.85f, 0.7f, 0.35f, 1f);
        game.font("ui").draw(batch, "\u2014 TAMAT \u2014", (W - layout.width) / 2f, 760f);
        List<String> lines = wrap(endingTitle, game.font("choice"), 620f);
        float ty = 680f;
        game.font("choice").setColor(Color.WHITE);
        for (String ln : lines) {
            layout.setText(game.font("choice"), ln);
            game.font("choice").draw(batch, ln, (W - layout.width) / 2f, ty);
            ty -= 52f;
        }
        layout.setText(game.font("small"), "Ketuk di mana saja untuk kembali");
        game.font("small").setColor(1f, 1f, 1f, 0.6f);
        game.font("small").draw(batch, "Ketuk di mana saja untuk kembali",
                (W - layout.width) / 2f, 480f);
        game.font("small").setColor(Color.WHITE);
    }

    // ------------------------------------------------------------ input

    private float[] toWorld(int sx, int sy) {
        Vector3 v = new Vector3(sx, sy, 0);
        camera.unproject(v, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return new float[]{v.x, v.y};
    }

    private void handleTap(float x, float y) {
        // Tombol kembali (top bar kiri)
        if (x < 120f && y > H - TOP_H) {
            game.setScreen(game.titleScreen);
            return;
        }
        if (showEnding) {
            game.setScreen(game.titleScreen);
            return;
        }
        // Opsi pilihan
        for (Msg m : history) {
            if (m.kind == K_OPT && m.option != null) {
                float bx = (W - m.w) / 2f;
                if (x >= bx - 8f && x <= bx + m.w + 8f && y >= m.ry - 8f && y <= m.ry + m.h + 8f) {
                    choose(m.option);
                    return;
                }
            }
        }
        if (typing) {
            if (typingMsg != null) shown = typingMsg.text.length();
            typing = false;
        } else {
            game.audio.sfx("beep");
            advance();
        }
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pointer != 0) return false;
        float[] p = toWorld(screenX, screenY);
        downX = p[0]; downY = p[1]; lastY = p[1];
        dragDist = 0f;
        touching = true;
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!touching || pointer != 0) return false;
        float[] p = toWorld(screenX, screenY);
        float dy = p[1] - lastY;
        lastY = p[1];
        dragDist += Math.abs(dy);
        scrollY -= dy;
        float ms = maxScroll();
        if (scrollY < 0f) scrollY = 0f;
        if (scrollY > ms) scrollY = ms;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (!touching || pointer != 0) return false;
        touching = false;
        float[] p = toWorld(screenX, screenY);
        if (dragDist < 24f) handleTap(p[0], p[1]);
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
            game.setScreen(game.titleScreen);
            return true;
        }
        if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) {
            handleTap(W / 2f, H / 2f);
            return true;
        }
        return false;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean mouseMoved(int sx, int sy) { return false; }
    @Override public boolean scrolled(float a, float b) { return false; }
    @Override public boolean touchCancelled(int sx, int sy, int p, int b) { touching = false; return false; }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        camera.position.set(W / 2f, H / 2f, 0f);
        camera.update();
    }

    @Override public void pause() { }
    @Override public void resume() { }

    @Override
    public void hide() {
        Gdx.input.setCatchKey(Input.Keys.BACK, false);
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
    }
}
