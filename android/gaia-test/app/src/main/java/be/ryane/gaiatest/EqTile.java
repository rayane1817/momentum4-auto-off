package be.ryane.gaiatest;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: tap steps to the next EQ preset (built-ins, then your custom ones). */
public class EqTile extends TileService {
    @Override public void onStartListening() { draw(); }

    @Override public void onClick() {
        Tile t = getQsTile();
        if (t != null) { t.setState(Tile.STATE_UNAVAILABLE); t.updateTile(); }
        Widgets.stepEq(this, 1, Remote.TILE_ATTEMPTS, this::draw);
    }

    private void draw() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setLabel("EQ: " + Widgets.eqName(this));
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("MOMENTUM 4");
        t.setState(Tile.STATE_ACTIVE);
        t.updateTile();
    }
}
