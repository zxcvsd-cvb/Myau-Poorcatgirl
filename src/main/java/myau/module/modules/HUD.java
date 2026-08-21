package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.util.RenderUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * 复用的 HSB 缓冲，避免每帧分配 float[3] 数组（渲染循环为单线程）。
     */
    private static final float[] HSB_BUFFER = new float[3];

    private List<RenderEntry> renderEntries = new ArrayList<>();
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    public final BooleanProperty notifications = new BooleanProperty("notifications", true);
    public final BooleanProperty rounded = new BooleanProperty("rounded", true);
    public final FloatProperty cornerRadius = new FloatProperty("corner-radius", 4.0F, 1.0F, 8.0F, () -> rounded.getValue());
    public final FloatProperty padding = new FloatProperty("padding", 2.0F, 0.0F, 6.0F);

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", true, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        return new Color(this.getColorRGB(time, offset));
    }

    /**
     * 直接返回 ARGB 整数，避免每帧创建 Color 对象与 HSB 数组分配。
     * 语义与 getColor 完全一致，仅用于高频渲染路径。
     */
    public int getColorRGB(long time, long offset) {
        int rgb;
        switch (this.colorMode.getValue()) {
            case 0:
                rgb = Color.HSBtoRGB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                rgb = Color.HSBtoRGB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                rgb = Color.HSBtoRGB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                rgb = this.custom1.getValue();
                break;
            case 4:
                float cycle4 = this.getColorCycle(time, offset);
                rgb = interpolateRGB(
                        (float) (2.0 * Math.abs(cycle4 - Math.floor(cycle4 + 0.5))),
                        this.custom1.getValue(),
                        this.custom2.getValue()
                );
                break;
            case 5:
                float cycle5 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle5 - Math.floor(cycle5 + 0.5)));
                if (floor <= 0.5F) {
                    rgb = interpolateRGB(floor * 2.0F, this.custom1.getValue(), this.custom2.getValue());
                } else {
                    rgb = interpolateRGB((floor - 0.5F) * 2.0F, this.custom2.getValue(), this.custom3.getValue());
                }
                break;
            default:
                rgb = 0xFFFFFF;
        }
        float saturation = this.colorSaturation.getValue().floatValue() / 100.0F;
        float brightness = this.colorBrightness.getValue().floatValue() / 100.0F;
        if (saturation != 1.0F || brightness != 1.0F) {
            Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, HSB_BUFFER);
            rgb = Color.HSBtoRGB(HSB_BUFFER[0], HSB_BUFFER[1] * saturation, HSB_BUFFER[2] * brightness);
        }
        return rgb;
    }

    private static int interpolateRGB(float progress, int start, int end) {
        progress = Math.min(Math.max(progress, 0.0F), 1.0F);
        int sr = (start >> 16) & 0xFF;
        int sg = (start >> 8) & 0xFF;
        int sb = start & 0xFF;
        int er = (end >> 16) & 0xFF;
        int eg = (end >> 8) & 0xFF;
        int eb = end & 0xFF;
        return (0xFF << 24)
                | (sr + (int) (progress * (er - sr))) << 16
                | (sg + (int) (progress * (eg - sg))) << 8
                | (sb + (int) (progress * (eb - sb)));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            ArrayList<RenderEntry> entries = new ArrayList<>();
            for (Module module : Myau.moduleManager.modules.values()) {
                if (!module.isEnabled() || module.isHidden()) {
                    continue;
                }
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                int nameWidth = mc.fontRendererObj.getStringWidth(moduleName);
                int totalWidth = nameWidth;
                int[] suffixWidths = new int[moduleSuffix.length];
                if (this.suffixes.getValue()) {
                    for (int i = 0; i < moduleSuffix.length; i++) {
                        suffixWidths[i] = mc.fontRendererObj.getStringWidth(moduleSuffix[i]);
                        totalWidth += 3 + suffixWidths[i];
                    }
                }
                entries.add(new RenderEntry(moduleName, moduleSuffix, nameWidth, suffixWidths, totalWidth));
            }
            entries.sort(Comparator.comparingInt(RenderEntry::totalWidth).reversed());
            this.renderEntries = entries;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColorRGB(System.currentTimeMillis(), 0L)
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            ScaledResolution sr = new ScaledResolution(mc);
            float height = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
            float x = (float) this.offsetX.getValue()
                    + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * this.scale.getValue();
            float y = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
            if (this.posX.getValue() == 1) {
                x = (float) sr.getScaledWidth() - x;
            }
            if (this.posY.getValue() == 1) {
                y = (float) sr.getScaledHeight() - y - height * this.scale.getValue();
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
            long l = System.currentTimeMillis();
            long offset = 0L;
            int bgColor = 0;
            if (this.background.getValue() > 0) {
                int alpha = (int) (this.background.getValue().floatValue() / 100.0F * 255.0F);
                bgColor = (alpha & 0xFF) << 24;
            }
            for (RenderEntry entry : this.renderEntries) {
                String moduleName = entry.name;
                String[] moduleSuffix = entry.suffix;
                float totalWidth = (float) (entry.totalWidth - (this.shadow.getValue() ? 0 : 1));
                int color = this.getColorRGB(l, offset);
                float pad = this.padding.getValue();
                float bgX1 = x / this.scale.getValue() - 1.0F - pad - (this.posX.getValue() == 0 ? 0.0F : totalWidth);
                float bgY1 = y / this.scale.getValue() - pad - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F));
                float bgX2 = x / this.scale.getValue() + 1.0F + pad + (this.posX.getValue() == 0 ? totalWidth : 0.0F);
                float bgY2 = y / this.scale.getValue() + height + pad + (this.posY.getValue() == 0 ? (this.shadow.getValue() ? 1.0F : 0.0F) : (offset == 0L ? 1.0F : 0.0F));
                RenderUtil.enableRenderState();
                if (this.background.getValue() > 0) {
                    if (this.rounded.getValue()) {
                        float bgW = bgX2 - bgX1;
                        float bgH = bgY2 - bgY1;
                        float rad = this.cornerRadius.getValue();
                        boolean isFirst = (offset == 0L);
                        boolean isLast = (offset == this.renderEntries.size() - 1);
                        boolean sideLeft = this.posX.getValue() == 1;
                        boolean sideRight = this.posX.getValue() == 0;
                        boolean isTopEntry = (this.posY.getValue() == 0) ? isFirst : isLast;
                        boolean isBottomEntry = (this.posY.getValue() == 0) ? isLast : isFirst;
                        RenderUtil.drawRoundedRect(
                                bgX1, bgY1, bgW, bgH, rad, bgColor,
                                sideLeft && isTopEntry, sideRight && isTopEntry,
                                sideLeft && isBottomEntry, sideRight && isBottomEntry
                        );
                    } else {
                        RenderUtil.drawRect(bgX1, bgY1, bgX2, bgY2, bgColor);
                    }
                }
                if (this.showBar.getValue()) {
                    if (this.shadow.getValue()) {
                        RenderUtil.drawRect(
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -3.0F : 1.0F),
                                y / this.scale.getValue() - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 1.0F),
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                                y / this.scale.getValue() + height + (this.posY.getValue() == 0 ? 1.0F : (offset == 0L ? 1.0F : 0.0F)),
                                color
                        );
                        RenderUtil.drawRect(
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                                y / this.scale.getValue() - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 1.0F),
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -1.0F : 3.0F),
                                y / this.scale.getValue() + height + (this.posY.getValue() == 0 ? 1.0F : (offset == 0L ? 1.0F : 0.0F)),
                                (color & 16579836) >> 2 | color & 0xFF000000
                        );
                    } else {
                        RenderUtil.drawRect(
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -2.0F : 1.0F),
                                y / this.scale.getValue() - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 0.0F),
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -1.0F : 2.0F),
                                y / this.scale.getValue() + height + (this.posY.getValue() == 0 ? 0.0F : (offset == 0L ? 1.0F : 0.0F)),
                                color
                        );
                    }
                }
                RenderUtil.disableRenderState();
                GlStateManager.disableDepth();
                if (this.shadow.getValue()) {
                    mc.fontRendererObj
                            .drawStringWithShadow(moduleName, x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F), y / this.scale.getValue(), color);
                } else {
                    mc.fontRendererObj
                            .drawString(
                                    moduleName,
                                    x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F),
                                    y / this.scale.getValue() + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                                    color,
                                    false
                            );
                }
                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    float width = (float) entry.nameWidth + 3.0F;
                    for (int i = 0; i < moduleSuffix.length; i++) {
                        String string = moduleSuffix[i];
                        if (this.shadow.getValue()) {
                            mc.fontRendererObj
                                    .drawStringWithShadow(
                                            string,
                                            x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F) + width,
                                            y / this.scale.getValue(),
                                            ChatColors.GRAY.toAwtColor()
                                    );
                        } else {
                            mc.fontRendererObj
                                    .drawString(
                                            string,
                                            x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F) + width,
                                            y / this.scale.getValue() + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                                            ChatColors.GRAY.toAwtColor(),
                                            false
                                    );
                        }
                        width += (float) entry.suffixWidths[i] + (this.shadow.getValue() ? 3.0F : 2.0F);
                    }
                }
                y += (height + (this.shadow.getValue() ? 1.0F : 0.0F) + this.padding.getValue() * 2.0F) * this.scale.getValue() * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
                offset++;
            }
            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = Myau.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        String sizeText = String.valueOf(movementPacketSize);
                        mc.fontRendererObj
                                .drawString(
                                        sizeText,
                                        (float) sr.getScaledWidth() / 2.0F / this.scale.getValue()
                                                - (float) mc.fontRendererObj.getStringWidth(sizeText) / 2.0F,
                                        (float) sr.getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                                        this.getColorRGB(l, offset) & 16777215 | -1090519040,
                                        this.shadow.getValue()
                                );
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
        renderNotifications();
    }

    private void renderNotifications() {
        if (!this.notifications.getValue()) return;

        try {
            if (Myau.notificationManager == null) return;

            java.util.List<myau.management.NotificationManager.NotificationEntry> entries = Myau.notificationManager.getActive();
            if (entries.isEmpty()) return;

            float notificationScale = Math.max(0.5F, Math.min(1.5F, this.scale.getValue()));
            ScaledResolution sr = new ScaledResolution(mc);
            float scaledWidth = sr.getScaledWidth() / notificationScale;
            float scaledHeight = sr.getScaledHeight() / notificationScale;
            float margin = 8.0F;
            float paddingX = 8.0F;
            float paddingY = 5.0F;
            float spacing = 4.0F;
            float y = scaledHeight - margin;

            GlStateManager.pushMatrix();
            GlStateManager.scale(notificationScale, notificationScale, 1.0F);

            for (int i = entries.size() - 1; i >= 0; i--) {
                myau.management.NotificationManager.NotificationEntry entry = entries.get(i);
                float alpha = notificationAlpha(entry);
                if (alpha <= 0.01F) continue;

                String text = modernNotificationText(entry.message);
                float textWidth = mc.fontRendererObj.getStringWidth(text);
                float textHeight = mc.fontRendererObj.FONT_HEIGHT;
                float boxWidth = Math.max(86.0F, textWidth + paddingX * 2.0F + 2.0F);
                float boxHeight = textHeight + paddingY * 2.0F + 3.0F;
                float x = scaledWidth - margin - boxWidth;
                y -= boxHeight;

                drawModernNotification(entry, text, x, y, boxWidth, boxHeight, paddingX, paddingY, alpha);
                y -= spacing;
            }

            GlStateManager.popMatrix();
        } catch (Exception ignored) {
        }
    }

    private void drawModernNotification(myau.management.NotificationManager.NotificationEntry entry, String text,
                                        float x, float y, float boxWidth, float boxHeight,
                                        float paddingX, float paddingY, float alpha) {
        float motion = notificationMotion(entry);
        float slide = (1.0F - motion) * 14.0F + (1.0F - alpha) * 5.0F;
        float renderX = x + slide;
        int statusColor = notificationStatusColor(text, alpha);
        int bg = argb(10, 12, 16, (int) (92 * alpha));
        int border = argb(255, 255, 255, (int) (24 * alpha));
        int neutralText = argb(238, 241, 245, (int) (242 * alpha));
        int depth = argb(0, 0, 0, (int) (28 * alpha));
        float radius = 5.0F;

        RenderUtil.drawRoundedRect(renderX + 1.0F, y + 1.5F, boxWidth, boxHeight, radius + 1.0F,
                depth, true, true, true, true);
        RenderUtil.drawRoundedRect(renderX, y, boxWidth, boxHeight, radius, bg, true, true, true, true);
        RenderUtil.drawRoundedRectOutline(renderX + 0.5F, y + 0.5F, boxWidth - 1.0F, boxHeight - 1.0F,
                radius, 1.0F, border, true, true, true, true);

        float progress = notificationProgress(entry);
        float progressX = renderX + 6.0F;
        float progressY = y + boxHeight - 2.0F;
        float progressW = boxWidth - 12.0F;
        RenderUtil.drawRoundedRect(progressX, progressY, progressW, 1.0F, 0.5F,
                argb(255, 255, 255, (int) (8 * alpha)), true, true, true, true);
        RenderUtil.drawRoundedRect(progressX, progressY, Math.max(1.0F, progressW * progress), 1.0F, 0.5F,
                statusColor, true, true, true, true);

        drawNotificationText(text, renderX + paddingX + 1.0F, y + paddingY + 1.0F, neutralText, statusColor);
    }

    private float notificationAlpha(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0) return 1.0F;
        float age = entry.getAge();
        float remaining = entry.durationMillis - age;
        float fade = Math.min(220.0F, entry.durationMillis / 3.0F);
        float alpha = Math.min(1.0F, Math.min(age / fade, remaining / fade));
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private float notificationProgress(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - entry.getAge() / (float) entry.durationMillis));
    }

    private float notificationMotion(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0) return 1.0F;
        float age = entry.getAge();
        float remaining = entry.durationMillis - age;
        float in = Math.max(0.0F, Math.min(1.0F, age / 260.0F));
        float out = Math.max(0.0F, Math.min(1.0F, remaining / 220.0F));
        float motion = Math.min(in, out);
        return motion * motion * (3.0F - 2.0F * motion);
    }

    private String modernNotificationText(String message) {
        if (message == null) return "";
        return message
                .replace(" was toggled successfully", " enabled")
                .replace(" was untoggled successfully", " disabled");
    }

    private int notificationStatusColor(String text, float alpha) {
        String lower = text.toLowerCase(Locale.ROOT);
        int rgb = lower.endsWith(" enabled") ? 0x41D982 : lower.endsWith(" disabled") ? 0xFF5C6C : 0xE5E9F0;
        int a = Math.max(0, Math.min(255, (int) (245 * alpha)));
        return argb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
    }

    private static int argb(int red, int green, int blue, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    private void drawNotificationText(String text, float x, float y, int neutralColor, int statusColor) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" enabled")) {
            drawSplitNotificationText(text, " enabled", x, y, neutralColor, statusColor);
        } else if (lower.endsWith(" disabled")) {
            drawSplitNotificationText(text, " disabled", x, y, neutralColor, statusColor);
        } else {
            mc.fontRendererObj.drawString(text, (int) x, (int) y, neutralColor, false);
        }
    }

    private void drawSplitNotificationText(String text, String suffix, float x, float y, int neutralColor, int statusColor) {
        String main = text.substring(0, text.length() - suffix.length());
        mc.fontRendererObj.drawString(main, (int) x, (int) y, neutralColor, false);
        mc.fontRendererObj.drawString(suffix.trim(), (int) (x + mc.fontRendererObj.getStringWidth(main + " ")), (int) y, statusColor, false);
    }

    /**
     * 预计算的 HUD 行渲染数据，每 tick 构建一次，渲染循环直接复用，
     * 避免每帧重复做模块名/后缀字符串处理与字体宽度计算。
     */
    private static final class RenderEntry {
        final String name;
        final String[] suffix;
        final int nameWidth;
        final int[] suffixWidths;
        final int totalWidth;

        RenderEntry(String name, String[] suffix, int nameWidth, int[] suffixWidths, int totalWidth) {
            this.name = name;
            this.suffix = suffix;
            this.nameWidth = nameWidth;
            this.suffixWidths = suffixWidths;
            this.totalWidth = totalWidth;
        }

        int totalWidth() {
            return this.totalWidth;
        }
    }
}
