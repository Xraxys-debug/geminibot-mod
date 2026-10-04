package com.geminibot;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BotScreen extends Screen {
    private enum Tab {
        SETTINGS("Ayarlar"), BOTS("Botlar"), CHAT("Sohbet");
        final String label;
        Tab(String l) { label = l; }
    }

    private record Label(int x, int y, String text) {}
    private record Row(OrderedText text, int color) {}

    private static final int SB = 72; // sidebar width
    private static Tab tab = Tab.SETTINGS;
    private static String target = "all";
    private static int scroll = 0;

    private final BotConfig cfg = BotConfig.INSTANCE;
    private final List<Label> labels = new ArrayList<>();
    private TextFieldWidget fKey, fModel, fHost, fPort, fName, fChat;
    private int px, py, pw, ph, cx, cy, cw;
    private int logX, logY, logW, logH;
    private List<String> lastBots = List.of();
    private List<Row> rows = List.of();
    private int rowsVer = -1, rowsW = -1;

    public BotScreen() {
        super(Text.literal("GeminiBot"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------- layout ----------

    @Override
    protected void init() {
        labels.clear();
        fKey = fModel = fHost = fPort = fName = fChat = null;
        lastBots = Bridge.bots;

        pw = Math.min(width - 8, 430);
        ph = Math.min(height - 8, 270);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + SB + 6;
        cy = py + 34;
        cw = pw - SB - 12;

        int by = py + 34;
        for (Tab t : Tab.values()) {
            addDrawableChild(ButtonWidget.builder(Text.literal((t == tab ? "> " : "") + t.label), b -> {
                tab = t;
                rebuild();
            }).dimensions(px + 6, by, SB - 12, 20).build());
            by += 24;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> close())
                .dimensions(px + pw - 22, py + 4, 18, 18).build());

        switch (tab) {
            case SETTINGS -> initSettings();
            case BOTS -> initBots();
            case CHAT -> initChat();
        }
    }

    private void rebuild() {
        saveFields();
        clearAndInit();
    }

    private TextFieldWidget field(int x, int y, int w, String hint) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(hint));
        f.setMaxLength(256);
        f.setPlaceholder(Text.literal(hint).formatted(Formatting.DARK_GRAY));
        addDrawableChild(f);
        return f;
    }

    private void initSettings() {
        int lw = 58, fx = cx + lw + 4, fw = cw - lw - 4, y = cy;

        labels.add(new Label(cx, y + 6, "API Key"));
        fKey = field(fx, y, fw, "AIza...");
        fKey.setText(cfg.apiKey);
        fKey.setRenderTextProvider((s, i) ->
                OrderedText.styledForwardsVisitedString("*".repeat(s.length()), Style.EMPTY));

        y += 24;
        labels.add(new Label(cx, y + 6, "Model"));
        fModel = field(fx, y, fw, "gemini-3.5-flash-lite");
        fModel.setText(cfg.model);

        y += 24;
        labels.add(new Label(cx, y + 6, "Sunucu"));
        addDrawableChild(ButtonWidget.builder(Text.literal(cfg.localMode ? "Localhost" : "IP / Domain"), b -> {
            saveFields();
            cfg.localMode = !cfg.localMode;
            cfg.save();
            rebuild();
        }).dimensions(fx, y, fw, 20).build());

        if (!cfg.localMode) {
            y += 24;
            labels.add(new Label(cx, y + 6, "Host"));
            fHost = field(fx, y, fw, "play.sunucu.com");
            fHost.setText(cfg.host == null ? "" : cfg.host);
        }

        y += 24;
        labels.add(new Label(cx, y + 6, "Port"));
        fPort = field(fx, y, fw, "25565");
        fPort.setText(String.valueOf(cfg.port));

        y += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Kaydet & Uygula"), b -> {
            saveFields();
            Bridge.sendConfig();
            Bridge.addLine(null, "log", Bridge.connected
                    ? "Ayarlar servise gönderildi (model: " + cfg.model + ")."
                    : "Ayarlar kaydedildi ama servis bağlı değil.");
        }).dimensions(fx, y, fw, 20).build());
    }

    private void initBots() {
        int lw = 58, fx = cx + lw + 4, fw = cw - lw - 4, y = cy;

        labels.add(new Label(cx, y + 6, "Bot adı"));
        fName = field(fx, y, fw, "Bot1");
        fName.setText(cfg.botName);

        y += 24;
        String server = cfg.serverHost() + ":" + cfg.port;
        addDrawableChild(ButtonWidget.builder(Text.literal("Bot Çağır  >  " + server), b -> {
            saveFields();
            Bridge.spawn(cfg.botName, cfg.serverHost(), cfg.port);
        }).dimensions(cx, y, cw, 20).build());

        y += 28;
        List<String> items = new ArrayList<>();
        items.add("all");
        items.addAll(Bridge.bots);
        for (String n : items) {
            if (y + 20 > py + ph - 6) break;
            boolean all = n.equals("all");
            addDrawableChild(ButtonWidget.builder(
                    Text.literal((n.equals(target) ? "[x] " : "[ ] ") + (all ? "Hepsi" : n)), b -> {
                        target = n;
                        rebuild();
                    }).dimensions(cx, y, all ? cw : cw - 24, 20).build());
            if (!all) {
                addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                    Bridge.remove(n);
                    if (n.equals(target)) target = "all";
                }).dimensions(cx + cw - 22, y, 22, 20).build());
            }
            y += 24;
        }
    }

    private void initChat() {
        int inputY = py + ph - 26;
        fChat = field(cx, inputY, cw - 54, "Bota ne yaptırmak istersin?");
        addDrawableChild(ButtonWidget.builder(Text.literal("Gönder"), b -> sendChat())
                .dimensions(cx + cw - 50, inputY, 50, 20).build());

        logX = cx;
        logY = py + 44;
        logW = cw - 20;
        logH = inputY - 6 - logY;
        addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> scroll += 5)
                .dimensions(cx + cw - 16, logY, 16, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> scroll = Math.max(0, scroll - 5))
                .dimensions(cx + cw - 16, logY + logH - 16, 16, 16).build());
        setInitialFocus(fChat);
    }

    // ---------- logic ----------

    private void saveFields() {
        if (fKey != null) cfg.apiKey = fKey.getText().trim();
        if (fModel != null && !fModel.getText().isBlank()) cfg.model = fModel.getText().trim();
        if (fHost != null) cfg.host = fHost.getText().trim();
        if (fPort != null) {
            try {
                cfg.port = Integer.parseInt(fPort.getText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (fName != null) {
            String n = fName.getText().replaceAll("[^A-Za-z0-9_]", "");
            if (n.length() > 16) n = n.substring(0, 16);
            if (!n.isEmpty()) cfg.botName = n;
        }
        cfg.save();
    }

    private void sendChat() {
        if (fChat == null) return;
        String t = fChat.getText().trim();
        if (t.isEmpty()) return;
        fChat.setText("");
        scroll = 0;
        Bridge.chat(t, target);
        setFocused(fChat);
    }

    @Override
    public void tick() {
        if (!target.equals("all") && !Bridge.bots.contains(target)) target = "all";
        if (tab == Tab.BOTS && !Bridge.bots.equals(lastBots)) rebuild();
    }

    @Override
    public void removed() {
        saveFields();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.CHAT && fChat != null && fChat.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendChat();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.CHAT) {
            scroll = Math.max(0, scroll + (verticalAmount > 0 ? 3 : -3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---------- render ----------

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x99000000);
        ctx.fill(px, py, px + pw, py + ph, 0xF012141C);
        ctx.fill(px, py, px + pw, py + 27, 0xFF1B1F2B);
        ctx.fill(px, py + 27, px + pw, py + 28, 0xFF8B5CF6);
        ctx.fill(px, py + 28, px + SB, py + ph, 0xFF161923);

        ctx.drawTextWithShadow(textRenderer, "GeminiBot", px + 8, py + 9, 0xFFC4B5FD);
        boolean on = Bridge.connected;
        String st = on ? "Servis bağlı" : "Servis yok";
        int sx = px + pw - 30 - textRenderer.getWidth(st);
        ctx.fill(sx - 10, py + 10, sx - 4, py + 16, on ? 0xFF22C55E : 0xFFEF4444);
        ctx.drawTextWithShadow(textRenderer, st, sx, py + 9, 0xFFD1D5DB);

        for (Label l : labels) ctx.drawTextWithShadow(textRenderer, l.text(), l.x(), l.y(), 0xFFD1D5DB);

        if (tab == Tab.CHAT) drawLog(ctx);
    }

    private void drawLog(DrawContext ctx) {
        String t = "Hedef: " + ("all".equals(target) ? "Hepsi" : target)
                + (Bridge.bots.isEmpty() ? "  (aktif bot yok)" : "");
        ctx.drawTextWithShadow(textRenderer, t, cx, py + 32, 0xFFC4B5FD);
        ctx.fill(logX, logY, logX + logW, logY + logH, 0xFF0B0D13);

        buildRows();
        int lh = textRenderer.fontHeight + 2;
        int max = Math.max(1, (logH - 4) / lh);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - max)));
        int end = rows.size() - scroll;
        int start = Math.max(0, end - max);

        ctx.enableScissor(logX, logY, logX + logW, logY + logH);
        for (int i = start; i < end; i++) {
            Row r = rows.get(i);
            int y = logY + logH - 2 - (end - i) * lh;
            ctx.drawTextWithShadow(textRenderer, r.text(), logX + 3, y, r.color());
        }
        ctx.disableScissor();
    }

    private void buildRows() {
        int v = Bridge.version;
        if (v == rowsVer && logW == rowsW) return;
        rowsVer = v;
        rowsW = logW;
        List<Row> out = new ArrayList<>();
        for (Bridge.Line l : Bridge.lines) {
            String pre;
            int col;
            switch (l.kind()) {
                case "user" -> {
                    pre = "Sen" + (l.bot() == null ? "" : " > " + l.bot()) + ": ";
                    col = 0xFF7DD3FC;
                }
                case "ai" -> {
                    pre = l.bot() == null ? "" : "[" + l.bot() + "] ";
                    col = 0xFFA7F3D0;
                }
                default -> {
                    pre = l.bot() == null ? "" : "[" + l.bot() + "] ";
                    col = 0xFF9CA3AF;
                }
            }
            for (OrderedText o : textRenderer.wrapLines(Text.literal(pre + l.text()), Math.max(40, logW - 6))) {
                out.add(new Row(o, col));
            }
        }
        rows = out;
    }
}
