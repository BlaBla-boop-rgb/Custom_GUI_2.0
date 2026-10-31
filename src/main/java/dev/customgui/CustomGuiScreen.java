package dev.customgui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomGuiScreen extends Screen {

    private static final int PW = 300;          // Panel-Breite
    private static final int PH = 250;          // Panel-Höhe
    private static final int ROWS_PER_PAGE = 4; // Inseln pro Seite
    private static final int ROW_W = 264;

    private enum Tab { HOME, ISLANDS }

    /** Ein animierter Button. hover läuft weich zwischen 0 und 1. */
    private static final class Btn {
        final String label;
        final String sub;     // zweite Zeile (nur bei linksbündigen Buttons)
        final String tip;     // Hinweistext unten im Panel
        final int accent;     // 0xRRGGBB
        final Runnable action;
        boolean left = false;
        int x, y, w, h;
        float hover = 0f;
        boolean wasHovered = false;

        Btn(String label, String sub, String tip, int accent, Runnable action) {
            this.label = label;
            this.sub = sub;
            this.tip = tip;
            this.accent = accent;
            this.action = action;
        }

        Btn at(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> buttons = new ArrayList<>();
    private final float[] tabHover = new float[2];
    private final boolean[] tabWasHovered = new boolean[2];

    private Tab tab = Tab.HOME;
    private boolean formMode = false;
    private String formName = "";
    private String formCmd = "";
    private String formError = "";
    private int page = 0;

    private EditBox nameBox;
    private EditBox cmdBox;

    private int px, py;
    private float indicatorX = -1f;
    private long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    public CustomGuiScreen() {
        super(Component.literal("Custom GUI"));
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        px = (this.width - PW) / 2;
        py = (this.height - PH) / 2;

        nameBox = new EditBox(this.font, px + 30, py + 82, PW - 60, 20, Component.literal("Name"));
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.literal("z. B. Markshop").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(formName);
        nameBox.setResponder(s -> formName = s);
        this.addWidget(nameBox);

        cmdBox = new EditBox(this.font, px + 30, py + 128, PW - 60, 20, Component.literal("Befehl"));
        cmdBox.setMaxLength(100);
        cmdBox.setHint(Component.literal("z. B. /warp Markshop").withStyle(ChatFormatting.DARK_GRAY));
        cmdBox.setValue(formCmd);
        cmdBox.setResponder(s -> formCmd = s);
        this.addWidget(cmdBox);

        updateBoxes();
        rebuild();
        openedAt = System.nanoTime();
        lastFrame = openedAt;
    }

    private void updateBoxes() {
        boolean show = formMode && tab == Tab.ISLANDS;
        nameBox.setVisible(show);
        nameBox.active = show;
        cmdBox.setVisible(show);
        cmdBox.active = show;
        if (show) {
            this.setFocused(nameBox);
            nameBox.setFocused(true);
        }
    }

    /** Baut die Buttons für den aktuellen Tab neu auf. */
    private void rebuild() {
        buttons.clear();

        if (tab == Tab.HOME) {
            int bw = 200;
            int bx = px + (PW - bw) / 2;
            buttons.add(new Btn("Spawn", "", "Führt /spawn aus", 0x55FFFF,
                    () -> runCommand("spawn")).at(bx, py + 74, bw, 30));
            buttons.add(new Btn("Shop", "", "Öffnet den Shop im Browser", 0x55FF55,
                    this::openShop).at(bx, py + 114, bw, 30));
            buttons.add(new Btn("Schließen", "", "Schließt dieses Menü", 0xFF5555,
                    this::onClose).at(bx, py + 154, bw, 30));
            return;
        }

        // ---- Inseln-Tab
        if (formMode) {
            int by = py + 172;
            buttons.add(new Btn("Speichern", "", "Insel speichern", 0x55FF55,
                    this::saveIsland).at(px + PW / 2 - 120, by, 116, 28));
            buttons.add(new Btn("Abbrechen", "", "Zurück zur Liste", 0xFF5555, () -> {
                formMode = false;
                formError = "";
                updateBoxes();
                rebuild();
            }).at(px + PW / 2 + 4, by, 116, 28));
            return;
        }

        List<CustomGuiConfig.Island> list = CustomGuiConfig.get().islands;
        int pages = pageCount();
        page = Math.max(0, Math.min(page, pages - 1));
        int rx = px + (PW - ROW_W) / 2;

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int idx = page * ROWS_PER_PAGE + i;
            if (idx >= list.size()) break;
            CustomGuiConfig.Island is = list.get(idx);
            int ry = py + 68 + i * 36;

            String cmd = is.command;
            Btn row = new Btn(is.name, "/" + cmd, "Klicke, um dich zu " + is.name + " zu bringen", 0x55FFFF,
                    () -> runCommand(cmd)).at(rx, ry, ROW_W - 30, 30);
            row.left = true;
            buttons.add(row);

            buttons.add(new Btn("x", "", "Insel aus der Liste entfernen", 0xFF5555, () -> {
                list.remove(idx);
                CustomGuiConfig.save();
                rebuild();
            }).at(rx + ROW_W - 24, ry, 24, 30));
        }

        int by = py + 68 + ROWS_PER_PAGE * 36 + 4;
        if (pages > 1) {
            buttons.add(new Btn("<", "", "Vorherige Seite", 0xFFFF55, () -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).at(rx, by, 24, 26));
            buttons.add(new Btn("+ Insel hinzufügen", "", "Neue Insel mit Befehl speichern", 0xFF55FF,
                    this::openForm).at(rx + 30, by, ROW_W - 60, 26));
            buttons.add(new Btn(">", "", "Nächste Seite", 0xFFFF55, () -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).at(rx + ROW_W - 24, by, 24, 26));
        } else {
            buttons.add(new Btn("+ Insel hinzufügen", "", "Neue Insel mit Befehl speichern", 0xFF55FF,
                    this::openForm).at(rx, by, ROW_W, 26));
        }
    }

    private int pageCount() {
        int n = CustomGuiConfig.get().islands.size();
        return Math.max(1, (n + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private void openForm() {
        formMode = true;
        formError = "";
        updateBoxes();
        rebuild();
    }

    private void saveIsland() {
        String name = formName.trim();
        String cmd = formCmd.trim();
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (name.isEmpty()) {
            formError = "Bitte gib einen Namen ein.";
            return;
        }
        if (cmd.isEmpty()) {
            formError = "Bitte gib einen Befehl ein.";
            return;
        }
        List<CustomGuiConfig.Island> list = CustomGuiConfig.get().islands;
        list.add(new CustomGuiConfig.Island(name, cmd));
        CustomGuiConfig.save();

        formName = "";
        formCmd = "";
        nameBox.setValue("");
        cmdBox.setValue("");
        formError = "";
        formMode = false;
        page = pageCount() - 1;
        updateBoxes();
        rebuild();
    }

    // ------------------------------------------------------------------ Aktionen

    private void runCommand(String command) {
        String cmd = command;
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(cmd);
        }
        this.onClose();
    }

    private void openShop() {
        try {
            openInBrowser(CustomGuiConfig.get().shopUrl);
        } catch (Exception e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(
                        Component.literal("Shop-Link konnte nicht geöffnet werden: " + e.getMessage())
                                .withStyle(ChatFormatting.RED));
            }
        }
        this.onClose();
    }

    /** Öffnet einen http(s)-Link im Standard-Browser (nur mit Java-Mitteln, ohne Minecraft-API). */
    private static void openInBrowser(String url) throws Exception {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Es sind nur http(s)-Links erlaubt");
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (os.contains("win")) {
            pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString());
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("open", uri.toString());
        } else {
            pb = new ProcessBuilder("xdg-open", uri.toString());
        }
        pb.start();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Rendern

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta); // Hintergrund

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float time = (now - openedAt) / 1_000_000_000f;

        float open = Math.min(1f, time / 0.35f);
        open = 1f - (float) Math.pow(1f - open, 3);
        int slide = Math.round((1f - open) * 18f);
        boolean interactive = open > 0.95f;

        drawStars(gfx, time, open);

        // Panel mit Schatten
        int x1 = px, y1 = py + slide, x2 = px + PW, y2 = py + PH + slide;
        roundRect(gfx, x1 - 3, y1 - 3, x2 + 3, y2 + 3, withAlpha(0xFF000000, open * 0.25f));
        roundRect(gfx, x1, y1, x2, y2, withAlpha(0xFF3A3A55, open));
        roundRect(gfx, x1 + 1, y1 + 1, x2 - 1, y2 - 1, withAlpha(0xFF14141E, open * 0.96f));

        // Verlaufs-Header (leichter Glanz oben)
        for (int i = 0; i < 40; i++) {
            float a = (1f - i / 40f) * 0.10f;
            gfx.fill(x1 + 2, y1 + 2 + i, x2 - 2, y1 + 3 + i, withAlpha(0xFFFFFFFF, open * a));
        }

        // Regenbogen-Leiste
        for (int sx = x1 + 8; sx < x2 - 8; sx += 4) {
            float hue = (sx - x1) / (float) PW + time * 0.12f;
            gfx.fill(sx, y1 + 4, Math.min(sx + 4, x2 - 8), y1 + 6, withAlpha(hsv(hue, 0.55f, 1f), open));
        }

        // Titel mit wanderndem Farbverlauf
        String title = "Mein Custom GUI";
        int totalW = 0;
        for (int i = 0; i < title.length(); i++) {
            totalW += this.font.width(boldChar(title.charAt(i)));
        }
        int tx = this.width / 2 - totalW / 2;
        for (int i = 0; i < title.length(); i++) {
            Component c = boldChar(title.charAt(i));
            int col = hsv(0.5f + 0.18f * (float) Math.sin(time * 1.5f + i * 0.45f), 0.45f, 1f);
            gfx.text(this.font, c, tx, y1 + 14, withAlpha(col, open));
            tx += this.font.width(c);
        }

        drawTabs(gfx, mouseX, mouseY - slide, dt, open, slide, interactive);

        // Inhalt je Tab
        if (tab == Tab.ISLANDS) {
            if (formMode) {
                drawLabel(gfx, "Name der Insel", px + 30, py + 70 + slide, open);
                drawLabel(gfx, "Befehl (zum Hinkommen)", px + 30, py + 116 + slide, open);
                nameBox.extractRenderState(gfx, mouseX, mouseY, delta);
                cmdBox.extractRenderState(gfx, mouseX, mouseY, delta);
                if (!formError.isEmpty()) {
                    Component err = Component.literal(formError).withStyle(ChatFormatting.RED);
                    gfx.text(this.font, err, this.width / 2 - this.font.width(err) / 2,
                            py + 156 + slide, withAlpha(0xFFFFFFFF, open));
                }
            } else {
                if (CustomGuiConfig.get().islands.isEmpty()) {
                    Component e1 = Component.literal("Noch keine Inseln gespeichert.").withStyle(ChatFormatting.GRAY);
                    Component e2 = Component.literal("Füge deine erste Insel hinzu!").withStyle(ChatFormatting.DARK_GRAY);
                    gfx.text(this.font, e1, this.width / 2 - this.font.width(e1) / 2, py + 110 + slide,
                            withAlpha(0xFFFFFFFF, open));
                    gfx.text(this.font, e2, this.width / 2 - this.font.width(e2) / 2, py + 124 + slide,
                            withAlpha(0xFFFFFFFF, open));
                }
                int pages = pageCount();
                if (pages > 1) {
                    Component pc = Component.literal("Seite " + (page + 1) + "/" + pages).withStyle(ChatFormatting.GRAY);
                    gfx.text(this.font, pc, x2 - 18 - this.font.width(pc), py + 42 + slide,
                            withAlpha(0xFFFFFFFF, open));
                }
            }
        }

        // Buttons
        Btn hoveredBtn = null;
        for (Btn b : buttons) {
            boolean hovered = interactive && b.contains(mouseX, mouseY - slide);
            if (hovered) hoveredBtn = b;
            if (hovered && !b.wasHovered) {
                playUi(1.8f);
            }
            b.wasHovered = hovered;
            b.hover += ((hovered ? 1f : 0f) - b.hover) * (1f - (float) Math.exp(-dt * 14f));
            drawButton(gfx, b, slide, time, open);
        }

        // Hinweistext unten
        if (hoveredBtn != null && !hoveredBtn.tip.isEmpty()) {
            Component tip = Component.literal(hoveredBtn.tip).withStyle(ChatFormatting.GRAY);
            gfx.text(this.font, tip, this.width / 2 - this.font.width(tip) / 2,
                    py + PH - 11 + slide, withAlpha(0xFFFFFFFF, open));
        }
    }

    private void drawTabs(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float dt, float open,
                          int slide, boolean interactive) {
        String[] names = {"Hauptmenü", "Inseln"};
        int tabW = 110, tabH = 20, gap = 8;
        int startX = px + (PW - (2 * tabW + gap)) / 2;
        int ty = py + 34 + slide;

        int selected = tab == Tab.HOME ? 0 : 1;
        float targetX = startX + selected * (tabW + gap);
        if (indicatorX < 0) indicatorX = targetX;
        indicatorX += (targetX - indicatorX) * (1f - (float) Math.exp(-dt * 12f));

        for (int i = 0; i < 2; i++) {
            int tx = startX + i * (tabW + gap);
            boolean hovered = interactive && mouseX >= tx && mouseX < tx + tabW
                    && mouseY >= py + 34 && mouseY < py + 34 + tabH;
            if (hovered && !tabWasHovered[i] && i != selected) playUi(1.8f);
            tabWasHovered[i] = hovered;
            tabHover[i] += ((hovered ? 1f : 0f) - tabHover[i]) * (1f - (float) Math.exp(-dt * 14f));

            if (tabHover[i] > 0.02f) {
                roundRect(gfx, tx, ty, tx + tabW, ty + tabH, withAlpha(0xFFFFFFFF, open * tabHover[i] * 0.07f));
            }
            int base = i == selected ? 0xFFFFFFFF : 0xFF9A9AB0;
            int col = lerpColor(base, 0xFFFFFFFF, tabHover[i]);
            Component c = Component.literal(names[i]).withStyle(ChatFormatting.BOLD);
            gfx.text(this.font, c, tx + tabW / 2 - this.font.width(c) / 2,
                    ty + tabH / 2 - this.font.lineHeight / 2, withAlpha(col, open));
        }
        // gleitender Unterstrich
        gfx.fill(Math.round(indicatorX) + 8, ty + tabH + 1, Math.round(indicatorX) + tabW - 8, ty + tabH + 3,
                withAlpha(0xFF55FFFF, open));
    }

    private void drawLabel(GuiGraphicsExtractor gfx, String text, int x, int y, float open) {
        gfx.text(this.font, Component.literal(text).withStyle(ChatFormatting.GRAY), x, y,
                withAlpha(0xFFFFFFFF, open));
    }

    private void drawStars(GuiGraphicsExtractor gfx, float time, float open) {
        for (int i = 0; i < 36; i++) {
            float fx = ((i * 0.61803f) % 1f) * this.width;
            float speed = 6f + (i % 5) * 4f;
            float fy = this.height - ((time * speed + i * 41f) % this.height);
            float twinkle = 0.5f + 0.5f * (float) Math.sin(time * 2f + i);
            int size = i % 4 == 0 ? 2 : 1;
            int col = hsv(0.5f + (i % 7) * 0.06f, 0.35f, 1f);
            gfx.fill((int) fx, (int) fy, (int) fx + size, (int) fy + size,
                    withAlpha(col, open * (0.15f + 0.30f * twinkle)));
        }
    }

    private void drawButton(GuiGraphicsExtractor gfx, Btn b, int slide, float time, float open) {
        float h = b.hover;
        int grow = Math.round(h * 2f);
        int lift = Math.round(h * 2f);
        int x1 = b.x - grow;
        int y1 = b.y + slide - grow - lift;
        int x2 = b.x + b.w + grow;
        int y2 = b.y + slide + b.h + grow - lift;
        int accent = 0xFF000000 | b.accent;

        // Glow
        if (h > 0.02f) {
            for (int i = 4; i >= 1; i--) {
                roundRect(gfx, x1 - i, y1 - i, x2 + i, y2 + i, withAlpha(accent, open * h * 0.07f));
            }
        }

        int border = lerpColor(0xFF44445A, accent, h);
        int bg = lerpColor(0xFF24243A, mixWithBlack(accent, 0.42f), h * 0.85f);
        roundRect(gfx, x1, y1, x2, y2, withAlpha(border, open));
        roundRect(gfx, x1 + 1, y1 + 1, x2 - 1, y2 - 1, withAlpha(bg, open));

        // Akzentstreifen links
        int stripe = Math.round(2 + h * 3);
        gfx.fill(x1 + 3, y1 + 4, x1 + 3 + stripe, y2 - 4, withAlpha(accent, open * (0.35f + 0.65f * h)));

        // Glanzstreifen beim Hover
        if (h > 0.02f) {
            float t = (time * 0.9f) % 1.4f - 0.2f;
            int sx = x1 + Math.round(t * (x2 - x1));
            int l = Math.max(x1 + 2, sx);
            int r = Math.min(x2 - 2, sx + 10);
            if (r > l) {
                gfx.fill(l, y1 + 2, r, y2 - 2, withAlpha(0xFFFFFFFF, open * h * 0.16f));
            }
        }

        int textColor = lerpColor(0xFFDDDDDD, 0xFFFFFFFF, h);
        Component label = Component.literal(b.label).withStyle(ChatFormatting.BOLD);
        if (b.left) {
            gfx.text(this.font, label, x1 + 12, y1 + 5, withAlpha(textColor, open));
            if (!b.sub.isEmpty()) {
                Component sub = Component.literal(b.sub).withStyle(ChatFormatting.GRAY);
                gfx.text(this.font, sub, x1 + 12, y1 + 17, withAlpha(0xFFFFFFFF, open));
            }
        } else {
            gfx.text(this.font, label, (x1 + x2) / 2 - this.font.width(label) / 2,
                    (y1 + y2) / 2 - this.font.lineHeight / 2 + 1, withAlpha(textColor, open));
        }
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true; // Klick auf ein Textfeld
        }
        if (event.button() == 0) {
            // Tabs
            int tabW = 110, tabH = 20, gap = 8;
            int startX = px + (PW - (2 * tabW + gap)) / 2;
            for (int i = 0; i < 2; i++) {
                int tx = startX + i * (tabW + gap);
                if (event.x() >= tx && event.x() < tx + tabW
                        && event.y() >= py + 34 && event.y() < py + 34 + tabH) {
                    Tab target = i == 0 ? Tab.HOME : Tab.ISLANDS;
                    if (target != tab) {
                        tab = target;
                        formMode = false;
                        formError = "";
                        page = 0;
                        updateBoxes();
                        rebuild();
                        playUi(1.0f);
                    }
                    return true;
                }
            }
            // Buttons
            for (Btn b : buttons) {
                if (b.contains(event.x(), event.y())) {
                    playUi(1.0f);
                    b.action.run();
                    return true;
                }
            }
        }
        return false;
    }

    private void playUi(float pitch) {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    // ------------------------------------------------------------------ Helfer

    private static Component boldChar(char c) {
        return Component.literal(String.valueOf(c)).withStyle(ChatFormatting.BOLD);
    }

    /** Rechteck mit abgerundeten Ecken (Radius 2), ohne überlappende Flächen. */
    private static void roundRect(GuiGraphicsExtractor gfx, int x1, int y1, int x2, int y2, int color) {
        gfx.fill(x1, y1 + 2, x2, y2 - 2, color);
        gfx.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
        gfx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        gfx.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        gfx.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
    }

    private static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float scaled = h * 6f;
        int i = (int) scaled;
        float f = scaled - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r = v, g = t, b = p;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private static int withAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = lerp((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, t);
        int r = lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
        int g = lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
        int b = lerp(c1 & 0xFF, c2 & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int mixWithBlack(int argb, float keep) {
        int r = Math.round(((argb >> 16) & 0xFF) * keep);
        int g = Math.round(((argb >> 8) & 0xFF) * keep);
        int b = Math.round((argb & 0xFF) * keep);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
