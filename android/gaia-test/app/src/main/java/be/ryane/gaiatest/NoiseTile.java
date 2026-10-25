package be.ryane.gaiatest;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: tap cycles Adaptive -> Custom -> Off. Long-press opens the app (system default). */
public class NoiseTile extends TileService {
    @Override public void onStartListening() { draw(); }

    @Override public void onClick() {
        int cur = Widgets.noise(this);
        int next = cur < 0 ? Remote.NOISE_ADAPTIVE : (cur + 1) % 3;
        Tile t = getQsTile();
        if (t != null) { t.setState(Tile.STATE_UNAVAILABLE); t.updateTile(); }
        Widgets.applyNoise(this, next, Remote.TILE_ATTEMPTS, this::draw);
    }

    private void draw() {
        Tile t = getQsTile();
        if (t == null) return;
        int m = Widgets.noise(this);
        t.setLabel(m < 0 ? "Noise: tap to set" : "Noise: " + Remote.NOISE_NAMES[m]);
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("M4");
        int icon = m == Remote.NOISE_CUSTOM ? R.drawable.ic_tile_anc_custom
                : m == Remote.NOISE_OFF ? R.drawable.ic_tile_anc_off : R.drawable.ic_tile_anc;
        t.setIcon(android.graphics.drawable.Icon.createWithResource(this, icon));
        t.setState(m == Remote.NOISE_OFF ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        t.updateTile();
    }
}
