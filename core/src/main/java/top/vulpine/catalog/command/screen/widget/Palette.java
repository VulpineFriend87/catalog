package top.vulpine.catalog.command.screen.widget;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The colours every screen uses.
 */
public final class Palette {

    /** Catalog's color. */
    public static final TextColor BRAND = TextColor.color(0xC08CFF);

    /** Something waiting to be applied. */
    public static final TextColor PENDING = TextColor.color(0xF2C46B);

    /** Destructive. */
    public static final TextColor DANGER = TextColor.color(0xFF7B72);

    /** Done. */
    public static final TextColor DONE = TextColor.color(0x7BE38B);

    public static final NamedTextColor TEXT = NamedTextColor.WHITE;

    public static final NamedTextColor MUTED = NamedTextColor.GRAY;

    private Palette() {
    }

}
