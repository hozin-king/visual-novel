package com.hozinking.visualnovel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.List;

/** Layar judul: Mulai / Lanjutkan / Galeri Ending. */
public class TitleScreen implements Screen, InputProcessor {
    private static final float W = 1280f, H = 720f;
    private final VnGame game;
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private final GlyphLayout layout = new GlyphLayout();
    private Texture bg;
    private final List<float[]> btnRects = new ArrayList<>();
    private final List<String> btnLabels = new ArrayList<>();
    private final List<Runnable> btnActions = new ArrayList<>();

    public TitleScreen(VnGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new StretchViewport(W, H, camera);
        viewport.apply();
        // StretchViewport tidak menengahkan kamera sendiri: wajib manual,
        // kalau tidak seluruh layar bergeser (separuh kiri hitam).
        camera.position.set(W / 2f, H / 2f, 0f);
        camera.update();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        if (bg == null) {
            try {
                bg = new Texture(Gdx.files.internal("bg/studio_malam.jpg"));
            } catch (Exception ignored) {
            }
        }
        buildButtons();
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCatchKey(Input.Keys.BACK, false);
        game.audio.bgm("studio_pad");
    }

    private void buildButtons() {
        btnRects.clear(); btnLabels.clear(); btnActions.clear();
        addBtn("Mulai dari Awal", new Runnable() {
            @Override public void run() { game.audio.sfx("beep"); game.storyScreen.startNew(); game.setScreen(game.storyScreen); }
        });
        if (game.save.hasSave()) {
            addBtn("Lanjutkan", new Runnable() {
                @Override public void run() { game.audio.sfx("beep"); game.storyScreen.startFromSave(); game.setScreen(game.storyScreen); }
            });
        }
        addBtn("Galeri Ending", new Runnable() {
            @Override public void run() { game.audio.sfx("beep"); game.setScreen(game.galleryScreen); }
        });
    }

    private void addBtn(String label, Runnable action) {
        int i = btnLabels.size();
        float bw = 420f, bh = 76f, gap = 20f;
        float bx = (W - bw) / 2f;
        float by = 250f - i * (bh + gap);
        btnRects.add(new float[]{bx, by, bw, bh});
        btnLabels.add(label);
        btnActions.add(action);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (bg != null) {
            float s = Math.max(W / bg.getWidth(), H / bg.getHeight());
            batch.setColor(0.45f, 0.45f, 0.5f, 1f);
            batch.draw(bg, (W - bg.getWidth() * s) / 2f, (H - bg.getHeight() * s) / 2f,
                    bg.getWidth() * s, bg.getHeight() * s);
            batch.setColor(Color.WHITE);
        }
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0f, 0f, W, H);
        shapes.end();

        batch.begin();
        layout.setText(game.font("title"), "LENTERA MALAM");
        game.font("title").setColor(0.95f, 0.85f, 0.6f, 1f);
        game.font("title").draw(batch, "LENTERA MALAM", (W - layout.width) / 2f, 560f);
        layout.setText(game.font("ui"), "sebuah novel visual");
        game.font("ui").setColor(1f, 1f, 1f, 0.7f);
        game.font("ui").draw(batch, "sebuah novel visual", (W - layout.width) / 2f, 505f);
        game.font("ui").setColor(Color.WHITE);
        batch.end();

        for (int i = 0; i < btnRects.size(); i++) {
            float[] r = btnRects.get(i);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.08f, 0.07f, 0.06f, 0.92f);
            shapes.rect(r[0], r[1], r[2], r[3]);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0.85f, 0.7f, 0.35f, 1f);
            shapes.rect(r[0], r[1], r[2], r[3]);
            shapes.end();
            batch.begin();
            layout.setText(game.font("choice"), btnLabels.get(i));
            game.font("choice").setColor(1f, 0.96f, 0.88f, 1f);
            game.font("choice").draw(batch, btnLabels.get(i), r[0] + (r[2] - layout.width) / 2f, r[1] + 50f);
            game.font("choice").setColor(Color.WHITE);
            batch.end();
        }

        batch.begin();
        layout.setText(game.font("small"), "Ketuk tombol untuk memilih  •  107.8 Larasati FM");
        game.font("small").setColor(1f, 1f, 1f, 0.45f);
        game.font("small").draw(batch, "Ketuk tombol untuk memilih  •  107.8 Larasati FM", (W - layout.width) / 2f, 60f);
        game.font("small").setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        if (pointer != 0) return false;
        Vector3 v = new Vector3(sx, sy, 0);
        camera.unproject(v, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        for (int i = 0; i < btnRects.size(); i++) {
            float[] r = btnRects.get(i);
            if (v.x >= r[0] && v.x <= r[0] + r[2] && v.y >= r[1] && v.y <= r[1] + r[3]) {
                btnActions.get(i).run();
                return true;
            }
        }
        return true;
    }

    @Override public boolean keyDown(int keycode) { return false; }
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char c) { return false; }
    @Override public boolean touchUp(int sx, int sy, int p, int b) { return false; }
    @Override public boolean touchDragged(int sx, int sy, int p) { return false; }
    @Override public boolean mouseMoved(int sx, int sy) { return false; }
    @Override public boolean scrolled(float a, float b) { return false; }
    @Override public boolean touchCancelled(int sx, int sy, int p, int b) { return false; }

    @Override public void resize(int w, int h) {
        viewport.update(w, h);
        camera.position.set(W / 2f, H / 2f, 0f);
        camera.update();
    }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }

    @Override
    public void dispose() {
        if (bg != null) bg.dispose();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
    }
}
