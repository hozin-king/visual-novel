package com.hozinking.visualnovel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Set;

/** Galeri ending: yang terbuka tampil judul, yang terkunci tampil "???". */
public class GalleryScreen implements Screen, InputProcessor {
    private static final float W = 1280f, H = 720f;
    private final VnGame game;
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private final GlyphLayout layout = new GlyphLayout();
    private final float backBX = 60f, backBY = 620f, backBW = 160f, backBH = 52f;

    public GalleryScreen(VnGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new StretchViewport(W, H, camera);
        viewport.apply();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.04f, 0.035f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        Set<String> unlocked = game.save.loadEndings();
        batch.begin();
        layout.setText(game.font("title"), "GALERI ENDING");
        game.font("title").setColor(0.95f, 0.85f, 0.6f, 1f);
        game.font("title").draw(batch, "GALERI ENDING", (W - layout.width) / 2f, 600f);
        game.font("title").setColor(Color.WHITE);
        layout.setText(game.font("ui"), unlocked.size() + " dari " + VnGame.ENDINGS.length + " terbuka");
        game.font("ui").setColor(1f, 1f, 1f, 0.6f);
        game.font("ui").draw(batch, unlocked.size() + " dari " + VnGame.ENDINGS.length + " terbuka",
                (W - layout.width) / 2f, 555f);
        game.font("ui").setColor(Color.WHITE);
        batch.end();

        float y0 = 470f;
        for (int i = 0; i < VnGame.ENDINGS.length; i++) {
            String id = VnGame.ENDINGS[i][0];
            String title = VnGame.ENDINGS[i][1];
            boolean open = unlocked.contains(id);
            float by = y0 - i * 90f;
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.09f, 0.08f, 0.07f, 0.95f);
            shapes.rect(240f, by - 58f, 800f, 72f);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            if (open) shapes.setColor(0.85f, 0.7f, 0.35f, 1f);
            else shapes.setColor(0.4f, 0.4f, 0.42f, 0.7f);
            shapes.rect(240f, by - 58f, 800f, 72f);
            shapes.end();
            batch.begin();
            String label = open ? title : "???";
            layout.setText(game.font("choice"), label);
            if (open) game.font("choice").setColor(1f, 0.93f, 0.75f, 1f);
            else game.font("choice").setColor(0.45f, 0.45f, 0.47f, 1f);
            game.font("choice").draw(batch, label, 280f, by - 12f);
            game.font("choice").setColor(Color.WHITE);
            batch.end();
        }

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.5f);
        shapes.rect(backBX, backBY, backBW, backBH);
        shapes.end();
        batch.begin();
        layout.setText(game.font("ui"), "KEMBALI");
        game.font("ui").draw(batch, "KEMBALI", backBX + (backBW - layout.width) / 2f, backBY + 34f);
        batch.end();
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        if (pointer != 0) return false;
        Vector3 v = new Vector3(sx, sy, 0);
        camera.unproject(v, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (v.x >= backBX && v.x <= backBX + backBW && v.y >= backBY && v.y <= backBY + backBH) {
            game.audio.sfx("beep");
            game.setScreen(game.titleScreen);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
            game.setScreen(game.titleScreen);
            return true;
        }
        return false;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char c) { return false; }
    @Override public boolean touchUp(int sx, int sy, int p, int b) { return false; }
    @Override public boolean touchDragged(int sx, int sy, int p) { return false; }
    @Override public boolean mouseMoved(int sx, int sy) { return false; }
    @Override public boolean scrolled(float a, float b) { return false; }
    @Override public boolean touchCancelled(int sx, int sy, int p, int b) { return false; }

    @Override public void resize(int w, int h) { viewport.update(w, h); }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
    }
}
