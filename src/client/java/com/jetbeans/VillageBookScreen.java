package com.jetbeans;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class VillageBookScreen extends Screen {

    // ── Texture ───────────────────────────────────────────────────────
    private static final Identifier TEXTURE =
            new Identifier("villageexpansion", "textures/gui/village_ledger.png");

    // Source PNG dimensions
    private static final int TEX_W = 640;
    private static final int TEX_H = 400;

    // Rendered at half resolution: 640×400 → 320×200
    private static final int BOOK_W = 320;
    private static final int BOOK_H = 200;

    // ── Source-space page regions (pixels in the PNG) ─────────────────
    // Left page content:  x=22..316,  y=12..374
    // Right page content: x=324..618, y=12..374
    // Spine:              x=316..324  (8px, centered at 320)
    // Close button (X):   ~(580, 27), ~18×18px

    // ── Screen-space constants (source / 2) ───────────────────────────
    private static final int LEFT_PAGE_X  = 11;   // 22 / 2
    private static final int RIGHT_PAGE_X = 162;  // 324 / 2
    private static final int PAGE_Y       = 6;    // 12 / 2
    private static final int PAGE_W       = 147;  // 294 / 2
    private static final int PAGE_H       = 181;  // 362 / 2

    private static final int CLOSE_X = 290;  // 580 / 2
    private static final int CLOSE_Y = 13;   // 27 / 2
    private static final int CLOSE_W = 9;    // 18 / 2
    private static final int CLOSE_H = 9;

    // Content padding & line height
    private static final int PAD_X  = 8;
    private static final int PAD_Y  = 8;
    private static final int LINE_H = 10;

    // Nav arrow button size
    private static final int ARROW_W = 14;
    private static final int ARROW_H = 10;

    // ── Ink colours ───────────────────────────────────────────────────
    private static final int COL_INK     = 0xFF2C1A06;
    private static final int COL_MUTED   = 0xFF7A5228;
    private static final int COL_OK      = 0xFF3A7A28;
    private static final int COL_MISSING = 0xFF9B2020;
    private static final int COL_PROG_BG = 0xFFCCB89A;
    private static final int COL_PROG    = 0xFF8B5E3C;

    // ── Runtime state ─────────────────────────────────────────────────
    private final VillageBookPacket.Snapshot data;
    private final Map<String, Integer> availMap = new HashMap<>();
    private int page = 0;
    private static final int MAX_PAGE = 2;

    // Absolute screen positions computed in init()
    private int bookX, bookY;
    private int prevBtnX, prevBtnY, nextBtnX, nextBtnY;
    private int closeBtnX, closeBtnY;

    public VillageBookScreen(VillageBookPacket.Snapshot data) {
        super(Text.of("Village Ledger"));
        this.data = data;
        for (VillageBookPacket.ResourceEntry e : data.available) {
            availMap.put(e.itemId, e.count);
        }
    }

    @Override
    protected void init() {
        super.init();
        bookX = (width  - BOOK_W) / 2;
        bookY = (height - BOOK_H) / 2;

        // Prev arrow: bottom-left of left page
        prevBtnX = bookX + LEFT_PAGE_X + PAD_X;
        prevBtnY = bookY + PAGE_Y + PAGE_H - ARROW_H - 4;

        // Next arrow: bottom-right of right page
        nextBtnX = bookX + RIGHT_PAGE_X + PAGE_W - ARROW_W - PAD_X;
        nextBtnY = bookY + PAGE_Y + PAGE_H - ARROW_H - 4;

        // Close button: matches the X drawn on the texture
        closeBtnX = bookX + CLOSE_X;
        closeBtnY = bookY + CLOSE_Y;
    }

    // ── Rendering ─────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawBook(matrices, mouseX, mouseY);
        super.render(matrices, mouseX, mouseY, delta);
    }

    private void drawBook(MatrixStack m, int mx, int my) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, TEXTURE);

        // Use raw quad drawing for full UV control — drawTexture helpers all assume
        // 256×256 sheet size internally and cannot be overridden cleanly in 1.18.2.
        // UV 0.0→1.0 maps the entire 640×400 texture onto our BOOK_W×BOOK_H destination.
        int x0 = bookX,           y0 = bookY;
        int x1 = bookX + BOOK_W,  y1 = bookY + BOOK_H;

        Matrix4f matrix = m.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buf.vertex(matrix, x0, y1, 0).texture(0f, 1f).next();
        buf.vertex(matrix, x1, y1, 0).texture(1f, 1f).next();
        buf.vertex(matrix, x1, y0, 0).texture(1f, 0f).next();
        buf.vertex(matrix, x0, y0, 0).texture(0f, 0f).next();
        Tessellator.getInstance().draw();

        // Page content
        int lpx = bookX + LEFT_PAGE_X  + PAD_X;
        int rpx = bookX + RIGHT_PAGE_X + PAD_X;
        int py  = bookY + PAGE_Y       + PAD_Y;

        switch (page) {
            case 0 -> { drawOverviewPage(m, lpx, py);    drawResourcePage(m, rpx, py);    }
            case 1 -> { drawConstructionPage(m, lpx, py); drawQueuePage(m, rpx, py);      }
            case 2 -> { drawCataloguePage(m, lpx, py, false); drawCataloguePage(m, rpx, py, true); }
        }

        // Page numbers
        drawPageNum(m, bookX + LEFT_PAGE_X  + PAGE_W / 2, bookY + PAGE_Y + PAGE_H - 10, page * 2 + 1);
        drawPageNum(m, bookX + RIGHT_PAGE_X + PAGE_W / 2, bookY + PAGE_Y + PAGE_H - 10, page * 2 + 2);

        // Nav arrows
        if (page > 0)        drawArrow(m, prevBtnX, prevBtnY, "◀", isHov(mx, my, prevBtnX,  prevBtnY,  ARROW_W, ARROW_H));
        if (page < MAX_PAGE) drawArrow(m, nextBtnX, nextBtnY, "▶", isHov(mx, my, nextBtnX,  nextBtnY,  ARROW_W, ARROW_H));
    }

    // ── Page content ──────────────────────────────────────────────────

    private void drawOverviewPage(MatrixStack m, int x, int y) {
        drawTitle(m, "Village Ledger", x, y);
        drawLine(m, x, y + 11);
        int cy = y + 16;
        drawKV(m, "Center",    data.center.getX() + ", " + data.center.getZ(), x, cy); cy += LINE_H;
        drawKV(m, "Level",     romanLevel(data.level), x, cy);                          cy += LINE_H;
        drawKV(m, "Buildings", String.valueOf(data.buildingsCompleted), x, cy);
    }

    private void drawResourcePage(MatrixStack m, int x, int y) {
        drawTitle(m, "Resources", x, y);
        drawLine(m, x, y + 11);
        int cy = y + 16;
        if (data.required.isEmpty()) { drawMuted(m, "No pending build.", x, cy); return; }
        drawMuted(m, "Next build needs:", x, cy); cy += LINE_H;
        for (VillageBookPacket.ResourceEntry req : data.required) {
            int have = availMap.getOrDefault(req.itemId, 0);
            boolean ok = have >= req.count;
            String label = shortName(req.itemId) + ": " + have + "/" + req.count;
            if (label.length() > 20) label = label.substring(0, 19) + ".";
            drawTicked(m, label, ok, x, cy);
            cy += LINE_H;
            if (cy > y + PAGE_H - 16) break;
        }
    }

    private void drawConstructionPage(MatrixStack m, int x, int y) {
        drawTitle(m, "Construction", x, y);
        drawLine(m, x, y + 11);
        int cy = y + 16;
        if (data.activeProjects.isEmpty()) { drawMuted(m, "No active projects.", x, cy); return; }
        for (VillageBookPacket.ProjectSnapshot p : data.activeProjects) {
            drawInk(m, p.buildingId, x, cy); cy += LINE_H;
            if (p.complete) {
                drawOk(m, "Complete!", x, cy);
            } else {
                drawMuted(m, "Layer " + p.currentLayer + " / " + p.totalLayers, x, cy);
                cy += LINE_H;
                drawProgressBar(m, x, cy, p.currentLayer, p.totalLayers);
            }
            cy += LINE_H + 4;
        }
    }

    private void drawQueuePage(MatrixStack m, int x, int y) {
        drawTitle(m, "Queue Build", x, y);
        drawLine(m, x, y + 11);
        int cy = y + 16;
        drawMuted(m, "Place resources in", x, cy);   cy += LINE_H;
        drawMuted(m, "the town hall chest.", x, cy); cy += LINE_H + 4;
        drawMuted(m, "Next up:", x, cy); cy += LINE_H;
        if (!data.allBuildingIds.isEmpty()) drawInk(m, data.allBuildingIds.get(0), x, cy);
    }

    private void drawCataloguePage(MatrixStack m, int x, int y, boolean right) {
        if (!right) { drawTitle(m, "Buildings", x, y); drawLine(m, x, y + 11); }
        int cy = y + (right ? PAD_Y : 16);
        int half = (data.allBuildingIds.size() + 1) / 2;
        int start = right ? half : 0;
        int end   = right ? data.allBuildingIds.size() : half;
        for (int i = start; i < end; i++) {
            drawInk(m, "• " + data.allBuildingIds.get(i), x, cy);
            cy += LINE_H;
            if (cy > y + PAGE_H - 16) break;
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────

    private void drawTitle(MatrixStack m, String text, int x, int y) {
        int tw = textRenderer.getWidth(text);
        textRenderer.draw(m, text, x + (PAGE_W - PAD_X * 2 - tw) / 2f, y, COL_INK);
    }

    private void drawLine(MatrixStack m, int x, int y) {
        fill(m, x, y, x + PAGE_W - PAD_X * 2, y + 1, 0x80806040);
    }

    private void drawKV(MatrixStack m, String k, String v, int x, int y) {
        textRenderer.draw(m, k + ": " + v, x, y, COL_INK);
    }

    private void drawInk  (MatrixStack m, String t, int x, int y) { textRenderer.draw(m, t, x, y, COL_INK);    }
    private void drawMuted(MatrixStack m, String t, int x, int y) { textRenderer.draw(m, t, x, y, COL_MUTED);  }
    private void drawOk   (MatrixStack m, String t, int x, int y) { textRenderer.draw(m, t, x, y, COL_OK);     }

    private void drawTicked(MatrixStack m, String text, boolean ok, int x, int y) {
        textRenderer.draw(m, (ok ? "\u2713 " : "\u2717 ") + text, x, y, ok ? COL_OK : COL_MISSING);
    }

    private void drawProgressBar(MatrixStack m, int x, int y, int current, int total) {
        int w = PAGE_W - PAD_X * 2 - 4;
        fill(m, x, y, x + w, y + 4, COL_PROG_BG);
        int filled = total > 0 ? (int)((float) current / total * w) : 0;
        if (filled > 0) fill(m, x, y, x + filled, y + 4, COL_PROG);
    }

    private void drawPageNum(MatrixStack m, int cx, int y, int num) {
        String s = String.valueOf(num);
        textRenderer.draw(m, s, cx - textRenderer.getWidth(s) / 2f, y, COL_MUTED);
    }

    private void drawArrow(MatrixStack m, int x, int y, String sym, boolean hovered) {
        textRenderer.draw(m, sym, x, y, hovered ? COL_INK : COL_MUTED);
    }

    // ── Input ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ix = (int) mx, iy = (int) my;
        if (isHov(ix, iy, closeBtnX, closeBtnY, CLOSE_W, CLOSE_H)) { close(); return true; }
        if (page > 0        && isHov(ix, iy, prevBtnX, prevBtnY, ARROW_W, ARROW_H)) { page--; return true; }
        if (page < MAX_PAGE && isHov(ix, iy, nextBtnX, nextBtnY, ARROW_W, ARROW_H)) { page++; return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 || keyCode == 65) { if (page > 0)        { page--; return true; } }
        if (keyCode == 262 || keyCode == 68) { if (page < MAX_PAGE) { page++; return true; } }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldPause() { return false; }

    // ── Utilities ─────────────────────────────────────────────────────

    private static boolean isHov(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String romanLevel(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }

    private static String shortName(String id) {
        return (id.contains(":") ? id.split(":")[1] : id).replace("_", " ");
    }
}