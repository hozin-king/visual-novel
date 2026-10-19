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
 * Mode Chat ala WhatsApp iOS (tema terang), mode utama sejak v1.4.
 * Layar portrait 720x1280. Kiri = karakter lain (avatar + nama berwarna),
 * kanan = protagonis, tengah = narasi sistem, gambar = pesan gambar
 * (ketuk untuk melihat besar).
 *
 * Perbaikan akar bug v1.3 (header/footer menimpa teks bubble): lapisan
 * pesan di-clip dengan glScissor tepat di antara header dan footer,
 * sehingga konten yang di-scroll selalu tersembunyi di balik keduanya.
 */
public class ChatScreen implements Screen, InputProcessor {
    private static final float W = 720f, H = 1280f;
    private static final float TOP_H = 128f, BOT_H = 96f;
    private static final String PROTAGONIST = "bagas";
    private static final float TYPE_CPS = 45f;
    private static final float TYPING_DELAY = 0.9f;

    private static final int K_OTHER = 0, K_OWN = 1, K_SYS = 2, K_OPT = 3;
    private static final int K_IMG = 4, K_TYPING = 5, K_DAY = 6;

    // Tema terang ala WA iOS
    private static final float[] C_BG = {0.93f, 0.90f, 0.865f};
    private static final float[] C_HEAD = {0.98f, 0.98f, 0.975f};
    private static final float[] C_FOOT = {0.96f, 0.96f, 0.955f};
    private static final float[] C_OWN = {0.84f, 0.955f, 0.745f};
    private static final float[] C_OTHER = {1f, 1f, 1f};
    private static final float[] C_SYS = {1f, 0.955f, 0.80f};
    private static final float[] C_DAY = {0.82f, 0.88f, 0.94f};
    private static final float[] C_BLUE = {0.05f, 0.48f, 1f};
    private static final float[] C_CHECK = {0.20f, 0.70f, 0.93f};
    private static final float[] C_TXT = {0.11f, 0.11f, 0.12f};
    private static final float[] C_DIM = {0.50f, 0.50f, 0.52f};
    private static final float[] C_DAYTXT = {0.32f, 0.40f, 0.52f};

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
        String avatarKey;
        String text = "";
        List<String> lines = new ArrayList<>();
        float w, h;
        float ry;
        float imgW, imgH;
        String imgFile;
        Texture imgTex;
        String time = "";
        float timeW;
        Script.Option option;
    }
    private final List<Msg> history = new ArrayList<>();
    private Msg typingMsg;
    private Msg typingPlaceholder;
    private Script.Block pendingBlock;
    private float typingDelay = 0f;
    private float shown = 0f;
    private boolean typing = false;
    private float scrollY = 0f;
    private float contentH = 0f;
    private boolean showEnding = false;
    private String endingTitle = "";
    private String lastBg = "";
    private int storyMin = 23 * 60 + 10;
    private float animT = 0f;

    private final Map<String, Texture> bgCache = new HashMap<>();
    private Texture viewing;

    private float downX, downY, lastY, dragDist;
    private boolean touching = false;

    private static final float[][] NAME_COLORS = {
        {0.00f, 0.55f, 0.65f}, {0.85f, 0.45f, 0.10f}, {0.55f, 0.35f, 0.85f},
        {0.85f, 0.25f, 0.35f}, {0.30f, 0.60f, 0.25f}, {0.20f, 0.45f, 0.90f},
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
        game.requestOrientation(true);
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
        resetChat();
        jumpTo("prolog");
    }

    public void startFromSave() {
        ensureScript();
        flags.clear();
        flags.putAll(game.save.loadFlags());
        resetChat();
        jumpTo(game.save.loadLabel());
    }

    private void resetChat() {
        showEnding = false;
        history.clear();
        typingMsg = null;
        typingPlaceholder = null;
        pendingBlock = null;
        typingDelay = 0f;
        typing = false;
        scrollY = 0f;
        contentH = 0f;
        lastBg = "";
        viewing = null;
        storyMin = 23 * 60 + 10;
        Msg day = new Msg();
        day.kind = K_DAY;
        day.text = "HARI INI";
        layoutMsg(day);
        history.add(day);
        recomputeContentH();
    }

    private String nextTime() {
        storyMin++;
        return String.format("%02d:%02d", (storyMin / 60) % 24, storyMin % 60);
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
                    addImageMsg(b.arg);
                    pc++;
                    break;
                case STYLE:
                    pc++;
                    break;
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

    private void addImageMsg(String file) {
        if (file == null) return;
        String f = file.trim();
        if (f.isEmpty() || f.equals(lastBg)) return;
        lastBg = f;
        Texture t = bgTex(f);
        if (t == null) return;
        Msg m = new Msg();
        m.kind = K_IMG;
        m.imgFile = f;
        m.imgTex = t;
        m.time = nextTime();
        layoutMsg(m);
        history.add(m);
        recomputeContentH();
        snapBottom();
    }

    private Texture bgTex(String file) {
        if (!bgCache.containsKey(file)) {
            Texture t = null;
            try {
                t = new Texture(Gdx.files.internal("bg/" + file));
            } catch (Exception ignored) {
            }
            bgCache.put(file, t);
        }
        return bgCache.get(file);
    }

    private Msg buildTextMsg(Script.Block b) {
        Msg m = new Msg();
        m.text = b.text;
        m.time = nextTime();
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
                float[] c = NAME_COLORS[Math.abs(b.speaker.hashCode()) % NAME_COLORS.length];
                m.nameRgb = c;
            }
        }
        return m;
    }

    private void startDialog(Script.Block b) {
        Msg m = buildTextMsg(b);
        if (m.kind == K_OTHER) {
            // Indikator "mengetik..." dulu sebelum bubble karakter masuk
            Msg t = new Msg();
            t.kind = K_TYPING;
            layoutMsg(t);
            history.add(t);
            typingPlaceholder = t;
            pendingBlock = b;
            typingDelay = TYPING_DELAY;
            typing = false;
            typingMsg = null;
        } else {
            addRealDialog(m);
        }
        recomputeContentH();
        snapBottom();
    }

    private void addRealDialog(Msg m) {
        layoutMsg(m);
        history.add(m);
        typingMsg = m;
        shown = 0f;
        typing = true;
    }

    private void showChoice(Script.Block b) {
        typing = false;
        typingMsg = null;
        for (Script.Option o : b.options) {
            if (o.cond != null && !o.cond.eval(flags)) continue;
            Msg m = new Msg();
            m.kind = K_OPT;
            m.text = o.text;
            m.time = nextTime();
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
        Msg m = new Msg();
        m.kind = K_OWN;
        m.text = o.text;
        m.time = nextTime();
        addRealDialog(m);
        recomputeContentH();
        jumpTo(o.target);
    }

    // ------------------------------------------------------------ layout chat

    private void layoutMsg(Msg m) {
        BitmapFont f = game.font("dialog");
        BitmapFont tiny = game.font("tiny");
        if (m.kind == K_IMG) {
            float iw = 470f, ih = 300f;
            if (m.imgTex != null) {
                ih = iw * m.imgTex.getHeight() / (float) m.imgTex.getWidth();
                if (ih > 360f) {
                    ih = 360f;
                    iw = ih * m.imgTex.getWidth() / (float) m.imgTex.getHeight();
                }
            }
            m.imgW = iw;
            m.imgH = ih;
            m.w = iw + 16f;
            m.h = ih + 16f + 30f;
            layout.setText(tiny, m.time);
            m.timeW = layout.width;
            return;
        }
        if (m.kind == K_TYPING) {
            m.w = 150f;
            m.h = 78f;
            return;
        }
        if (m.kind == K_DAY) {
            layout.setText(tiny, m.text);
            m.w = layout.width + 56f;
            m.h = 52f;
            return;
        }
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
        if (m.w < 110f) m.w = 110f;
        float lh = 46f;
        m.h = 26f + lines.size() * lh + 14f;
        if (m.kind == K_OTHER) m.h += 36f;
        if (m.kind == K_OPT) m.h += 10f;
        if (m.kind == K_OWN || m.kind == K_OTHER) m.h += 28f; // baris timestamp
        layout.setText(tiny, m.time);
        m.timeW = layout.width;
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
        animT += dt;
        if (typingDelay > 0f) {
            typingDelay -= dt;
            if (typingDelay <= 0f) {
                if (typingPlaceholder != null) history.remove(typingPlaceholder);
                typingPlaceholder = null;
                Script.Block b = pendingBlock;
                pendingBlock = null;
                if (b != null) {
                    addRealDialog(buildTextMsg(b));
                    recomputeContentH();
                    snapBottom();
                }
            }
        }
        if (typing && typingMsg != null) {
            shown += dt * TYPE_CPS;
            if (shown >= typingMsg.text.length()) {
                shown = typingMsg.text.length();
                typing = false;
            }
        }

        Gdx.gl.glClearColor(C_BG[0], C_BG[1], C_BG[2], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

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
        drawBottomBar(false);

        // Lapisan pesan: clip tepat di antara header & footer (akar fix v1.3)
        scissorOn();
        for (Msg m : history) drawBubble(m);
        scissorOff();

        batch.begin();
        drawTopBar(true);
        scissorOn();
        for (Msg m : history) drawMsgContent(m);
        scissorOff();
        drawBottomBar(true);
        batch.end();

        // Overlay redup (di luar batch): ending & image viewer
        if (showEnding || viewing != null) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, showEnding ? 0.88f : 0.93f);
            shapes.rect(0f, 0f, W, H);
            shapes.end();
        }
        batch.begin();
        if (showEnding) drawEnding();
        if (viewing != null) drawViewer();
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Aktifkan clip: hanya area di antara header dan footer yang digambar. */
    private void scissorOn() {
        float sx = Gdx.graphics.getWidth() / W;
        float sy = Gdx.graphics.getHeight() / H;
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, (int) (BOT_H * sy), (int) (W * sx),
                (int) ((H - TOP_H - BOT_H) * sy));
    }

    private void scissorOff() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawBackground() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.84f, 0.80f, 0.75f, 0.35f);
        for (float gx = 45f; gx < W; gx += 90f) {
            for (float gy = BOT_H + 30f; gy < H - TOP_H; gy += 90f) {
                shapes.circle(gx, gy, 2.5f, 8);
            }
        }
        shapes.end();
    }

    private void drawTopBar(boolean text) {
        if (!text) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(C_HEAD[0], C_HEAD[1], C_HEAD[2], 1f);
            shapes.rect(0f, H - TOP_H, W, TOP_H);
            shapes.setColor(0.78f, 0.78f, 0.78f, 1f);
            shapes.rect(0f, H - TOP_H, W, 2f);
            // Avatar lingkaran header "LM"
            shapes.setColor(0.20f, 0.26f, 0.32f, 1f);
            shapes.circle(124f, H - TOP_H + 64f, 32f, 24);
            shapes.end();
        } else {
            // Tombol back iOS
            game.font("choice").setColor(C_BLUE[0], C_BLUE[1], C_BLUE[2], 1f);
            game.font("choice").draw(batch, "\u2039", 30f, H - 56f);
            game.font("choice").setColor(Color.WHITE);
            layout.setText(game.font("small"), "LM");
            game.font("small").setColor(1f, 1f, 1f, 1f);
            game.font("small").draw(batch, "LM", 124f - layout.width / 2f,
                    H - TOP_H + 64f + 8f);
            layout.setText(game.font("choice"), "Lentera Malam");
            game.font("choice").setColor(C_TXT[0], C_TXT[1], C_TXT[2], 1f);
            game.font("choice").draw(batch, "Lentera Malam", 172f, H - 48f);
            layout.setText(game.font("tiny"), "online");
            game.font("tiny").setColor(0.45f, 0.45f, 0.45f, 1f);
            game.font("tiny").draw(batch, "online", 172f, H - 84f);
            game.font("choice").setColor(Color.WHITE);
            game.font("small").setColor(Color.WHITE);
            game.font("tiny").setColor(Color.WHITE);
        }
    }

    private void drawBottomBar(boolean text) {
        if (!text) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(C_FOOT[0], C_FOOT[1], C_FOOT[2], 1f);
            shapes.rect(0f, 0f, W, BOT_H);
            shapes.setColor(0.78f, 0.78f, 0.78f, 1f);
            shapes.rect(0f, BOT_H - 2f, W, 2f);
            shapes.end();
        } else if (!showEnding) {
            layout.setText(game.font("small"), "Ketuk untuk lanjutkan \u203a");
            game.font("small").setColor(C_DIM[0], C_DIM[1], C_DIM[2], 1f);
            game.font("small").draw(batch, "Ketuk untuk lanjutkan \u203a",
                    (W - layout.width) / 2f, 56f);
            game.font("small").setColor(Color.WHITE);
        }
    }

    private void bubble(float x, float y, float w, float h, float r,
                        float[] c) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(c[0], c[1], c[2], 1f);
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
        float bx = bubbleX(m);
        if (m.kind == K_OWN) {
            bubble(bx, m.ry, m.w, m.h, r, C_OWN);
            drawChecks(bx + m.w - 14f, m.ry + 16f);
        } else if (m.kind == K_OTHER) {
            bubble(bx, m.ry, m.w, m.h, r, C_OTHER);
        } else if (m.kind == K_SYS) {
            bubble(bx, m.ry, m.w, m.h, 14f, C_SYS);
        } else if (m.kind == K_IMG) {
            bubble(bx, m.ry, m.w, m.h, r, C_OTHER);
        } else if (m.kind == K_TYPING) {
            bubble(bx, m.ry, m.w, m.h, r, C_OTHER);
            // Tiga titik animasi
            for (int i = 0; i < 3; i++) {
                float ph = animT * 4f - i * 0.9f;
                float a = 0.35f + 0.65f * Math.max(0f, (float) Math.sin(ph));
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(0.55f, 0.55f, 0.58f, a);
                shapes.circle(bx + 40f + i * 32f, m.ry + m.h / 2f, 9f, 12);
                shapes.end();
            }
        } else if (m.kind == K_DAY) {
            bubble(bx, m.ry, m.w, m.h, m.h / 2f, C_DAY);
        } else { // K_OPT
            bubble(bx - 3f, m.ry - 3f, m.w + 6f, m.h + 6f, r, C_BLUE);
            bubble(bx, m.ry, m.w, m.h, r, C_OTHER);
        }
    }

    private float bubbleX(Msg m) {
        if (m.kind == K_OWN) return W - 16f - m.w;
        if (m.kind == K_OTHER || m.kind == K_TYPING) return 96f;
        return (W - m.w) / 2f;
    }

    /** Centang ganda biru (read receipt) untuk bubble sendiri. */
    private void drawChecks(float right, float y) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(C_CHECK[0], C_CHECK[1], C_CHECK[2], 1f);
        for (int k = 0; k < 2; k++) {
            float x = right - 30f + k * 9f;
            shapes.line(x, y + 7f, x + 6f, y + 1f);
            shapes.line(x + 6f, y + 1f, x + 15f, y + 13f);
        }
        shapes.end();
    }

    private void drawMsgContent(Msg m) {
        float bx = bubbleX(m);
        if (m.kind == K_IMG) {
            if (m.imgTex != null) {
                batch.draw(m.imgTex, bx + 8f, m.ry + 8f + 30f, m.imgW, m.imgH);
            }
            drawTime(m, bx, false);
            return;
        }
        if (m.kind == K_TYPING || m.kind == K_DAY) {
            if (m.kind == K_DAY) {
                layout.setText(game.font("tiny"), m.text);
                game.font("tiny").setColor(C_DAYTXT[0], C_DAYTXT[1], C_DAYTXT[2], 1f);
                game.font("tiny").draw(batch, m.text, bx + (m.w - layout.width) / 2f,
                        m.ry + 34f);
                game.font("tiny").setColor(Color.WHITE);
            }
            return;
        }

        BitmapFont f = game.font("dialog");
        List<String> lines = m.lines;
        if (m == typingMsg && typing) {
            String vis = m.text.substring(0, Math.min(m.text.length(), (int) shown));
            lines = wrap(vis, f, (m.kind == K_SYS ? 600f : 540f) - 48f);
        }

        if (m.kind == K_OTHER) {
            float ax = 16f, ay = m.ry + m.h - 72f, as = 64f;
            Texture av = m.avatarKey != null ? Portraits.getAvatar(m.avatarKey) : null;
            if (av != null) {
                batch.draw(av, ax, ay, as, as);
            } else {
                game.font("choice").setColor(m.nameRgb[0], m.nameRgb[1], m.nameRgb[2], 1f);
                String init = m.name.isEmpty() ? "?" : m.name.substring(0, 1).toUpperCase();
                layout.setText(game.font("choice"), init);
                game.font("choice").draw(batch, init, ax + (as - layout.width) / 2f, ay + 44f);
                game.font("choice").setColor(Color.WHITE);
            }
        }

        float tx = bx + 24f;
        float ty = m.ry + m.h - 22f;
        if (m.kind == K_OTHER) {
            game.font("speaker").setColor(m.nameRgb[0], m.nameRgb[1], m.nameRgb[2], 1f);
            game.font("speaker").draw(batch, m.name, tx, ty);
            game.font("speaker").setColor(Color.WHITE);
            ty -= 40f;
        }
        boolean centered = (m.kind == K_SYS || m.kind == K_OPT);
        f.setColor(C_TXT[0], C_TXT[1], C_TXT[2], 1f);
        if (m.kind == K_OPT) f.setColor(C_BLUE[0], C_BLUE[1], C_BLUE[2], 1f);
        for (String ln : lines) {
            layout.setText(f, ln);
            float lx = centered ? bx + (m.w - layout.width) / 2f : tx;
            f.draw(batch, ln, lx, ty);
            ty -= 46f;
        }
        f.setColor(Color.WHITE);
        drawTime(m, bx, m.kind == K_OWN);
    }

    private void drawTime(Msg m, float bx, boolean own) {
        if (m.time == null || m.time.isEmpty()) return;
        float tx = bx + m.w - 14f - m.timeW - (own ? 36f : 0f);
        game.font("tiny").setColor(C_DIM[0], C_DIM[1], C_DIM[2], 1f);
        game.font("tiny").draw(batch, m.time, tx, m.ry + 26f);
        game.font("tiny").setColor(Color.WHITE);
    }

    /** Image viewer: gambar pesan ditampilkan besar (hanya pass batch). */
    private void drawViewer() {
        float tw = viewing.getWidth(), th = viewing.getHeight();
        float s = Math.min(W / tw, H / th);
        float dw = tw * s, dh = th * s;
        batch.draw(viewing, (W - dw) / 2f, (H - dh) / 2f, dw, dh);
        layout.setText(game.font("small"), "Ketuk untuk tutup");
        game.font("small").setColor(1f, 1f, 1f, 0.7f);
        game.font("small").draw(batch, "Ketuk untuk tutup",
                (W - layout.width) / 2f, 80f);
        game.font("small").setColor(Color.WHITE);
    }

    private void drawEnding() {
        layout.setText(game.font("ui"), "\u2014 TAMAT \u2014");
        game.font("ui").setColor(0.75f, 0.55f, 0.20f, 1f);
        game.font("ui").draw(batch, "\u2014 TAMAT \u2014", (W - layout.width) / 2f, 760f);
        List<String> lines = wrap(endingTitle, game.font("choice"), 620f);
        float ty = 680f;
        game.font("choice").setColor(C_TXT[0], C_TXT[1], C_TXT[2], 1f);
        for (String ln : lines) {
            layout.setText(game.font("choice"), ln);
            game.font("choice").draw(batch, ln, (W - layout.width) / 2f, ty);
            ty -= 52f;
        }
        layout.setText(game.font("small"), "Ketuk di mana saja untuk kembali");
        game.font("small").setColor(C_DIM[0], C_DIM[1], C_DIM[2], 1f);
        game.font("small").draw(batch, "Ketuk di mana saja untuk kembali",
                (W - layout.width) / 2f, 480f);
        game.font("small").setColor(Color.WHITE);
        game.font("choice").setColor(Color.WHITE);
    }

    // ------------------------------------------------------------ input

    private float[] toWorld(int sx, int sy) {
        Vector3 v = new Vector3(sx, sy, 0);
        camera.unproject(v, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return new float[]{v.x, v.y};
    }

    private void handleTap(float x, float y) {
        if (viewing != null) {
            viewing = null;
            return;
        }
        if (x < 140f && y > H - TOP_H) {
            game.setScreen(game.titleScreen);
            return;
        }
        if (showEnding) {
            game.setScreen(game.titleScreen);
            return;
        }
        if (typingDelay > 0f) {
            typingDelay = 0.001f;
            return;
        }
        for (Msg m : history) {
            if (m.kind == K_IMG && m.imgTex != null) {
                float bx = bubbleX(m);
                if (x >= bx && x <= bx + m.w && y >= m.ry && y <= m.ry + m.h) {
                    viewing = m.imgTex;
                    return;
                }
            }
        }
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
            if (viewing != null) {
                viewing = null;
                return true;
            }
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
        for (Texture t : bgCache.values()) {
            try { if (t != null) t.dispose(); } catch (Exception ignored) {}
        }
        bgCache.clear();
    }
}
