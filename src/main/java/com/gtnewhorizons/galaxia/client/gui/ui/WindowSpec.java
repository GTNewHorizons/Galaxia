package com.gtnewhorizons.galaxia.client.gui.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;

/**
 * Everything a window needs to be opened: its id, title, body and where it goes. Built fluently and handed to
 * {@link WindowLayer#open(WindowSpec)}
 */
public final class WindowSpec {

    /** Where a window opens the first time, from the size of the layer and of the window */
    @FunctionalInterface
    public interface Anchor {

        int[] place(int layerWidth, int layerHeight, int width, int height);
    }

    final String id;
    final IKey title;
    Consumer<GalaxiaUi.Column> body = column -> {};
    int maxHeight = -1;
    boolean modal;
    boolean closable = true;
    String owner;
    Runnable onClose;
    Runnable onUpdate;
    Anchor anchor;
    final List<IWidget> headerButtons = new ArrayList<>();

    private WindowSpec(String id, IKey title) {
        this.id = id;
        this.title = title;
    }

    public static WindowSpec of(String id, IKey title) {
        return new WindowSpec(id, title);
    }

    public static WindowSpec of(String id, String titleKey) {
        return new WindowSpec(id, IKey.lang(titleKey));
    }

    /** Fills the window; called again whenever the window is rebuilt */
    public WindowSpec body(Consumer<GalaxiaUi.Column> body) {
        this.body = body;
        return this;
    }

    /** Height above which the body scrolls, by default most of the layer */
    public WindowSpec maxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        return this;
    }

    /** Blocks everything below it until it is closed, for dialogs that ask for a decision */
    public WindowSpec modal() {
        this.modal = true;
        return this;
    }

    public WindowSpec closable(boolean closable) {
        this.closable = closable;
        return this;
    }

    /** Window whose closing closes this one too */
    public WindowSpec owner(String ownerId) {
        this.owner = ownerId;
        return this;
    }

    public WindowSpec onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    /** Runs every tick while the window is open, for content that follows the game */
    public WindowSpec onUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
        return this;
    }

    public WindowSpec anchor(Anchor anchor) {
        this.anchor = anchor;
        return this;
    }

    /** Opens at a fixed point of the layer */
    public WindowSpec at(int x, int y) {
        return anchor((lw, lh, w, h) -> new int[] { x, y });
    }

    /** Opens centred on the layer */
    public WindowSpec centered() {
        return anchor((lw, lh, w, h) -> new int[] { (lw - w) / 2, (lh - h) / 2 });
    }

    /** Small button placed in the title bar before the close button */
    public WindowSpec headerButton(IWidget button) {
        headerButtons.add(button);
        return this;
    }

    public String id() {
        return id;
    }
}
