package keystrokesmod.module.impl.world;

import keystrokesmod.event.*;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.util.Objects;
import org.lwjgl.opengl.GL11;
import static org.lwjgl.opengl.GL11.*;

public class Disabler extends Module {
    // 删除原来的disablerTicks
    // 修改后的成员变量
    private ButtonSetting hypixelMode;
    private ButtonSetting hypixelMotionMode;
    private SliderSetting activationDelay;

    int tickCounter = 0;
    boolean waitingForGround = false;
    boolean applyingMotion = false;
    int stateTickCounter = 0;
    boolean warningDisplayed = false;
    int sprintToggleTick = 0;
    boolean shouldRun = false;
    long lobbyTime = 0;
    long finished = 0;
    long activationDelayMillis;
    final long checkDisabledTime = 4000;
    private int color = new Color(0, 187, 255, 255).getRGB();
    private int dotAnimationCounter = 0;
    private long lastDotUpdate;
    private boolean hasPrintedRunningMessage = false; // 新增状态变量

    // 新增进度条相关变量
    private float barWidth = 80;
    private float barHeight = 5;
    private float filledWidth;
    private float barX, barY;
    private final int borderColor = new Color(30, 30, 30, 200).getRGB();

    private boolean shouldRender;
    private double firstY;
    private boolean reset;
    private float savedYaw, savedPitch;
    private boolean worldJoin;

    public boolean disablerLoaded, running;

    public Disabler() {
        super("Disabler", category.world);
        // 使用新构造函数并传递this（当前模块实例）
        this.registerSetting(hypixelMode = new ButtonSetting("Hypixel", this, true));
        this.registerSetting(hypixelMotionMode = new ButtonSetting("HypixelMotion", this, false));
        this.registerSetting(activationDelay = new SliderSetting("Activation delay", " seconds", 0, 0, 4, 0.5));

        // 设置互斥属性
        hypixelMode.setExclusive(true);
        hypixelMotionMode.setExclusive(true);
    }
    // 在按钮切换时处理互斥逻辑
    @Override
    public void guiButtonToggled(ButtonSetting b) {
        if (b == hypixelMode && b.isToggled()) {
            hypixelMotionMode.setToggled(false);
        }
        else if (b == hypixelMotionMode && b.isToggled()) {
            hypixelMode.setToggled(false);
        }
    }

    // 获取当前tick数的方法
    private int getDisablerTicks() {
        return hypixelMode.isToggled() ? 130 : 140;
    }

    public void onEnable() {
        if (!disablerLoaded) {
            resetState();
        }
        // 初始化进度条位置
        final ScaledResolution scaledResolution = new ScaledResolution(mc);
        barX = scaledResolution.getScaledWidth() / 2 - barWidth / 2;
        barY = scaledResolution.getScaledHeight() / 2 + 28; // 调整到文本下方
    }

    public void onDisable() {
        shouldRun = false;
        running = false;
    }

    private void resetState() {
        savedYaw = mc.thePlayer.rotationYaw;
        savedPitch = mc.thePlayer.rotationPitch;
        shouldRun = true;
        tickCounter = 0;
        applyingMotion = false;
        waitingForGround = true;
        stateTickCounter = 0;
        warningDisplayed = false;
        running = false;
        sprintToggleTick = 0;
        lobbyTime = Utils.time();
        finished = 0;
        shouldRender = false;
        reset = false;
        worldJoin = false;
        activationDelayMillis = (long) (activationDelay.getInput() * 1000);
        hasPrintedRunningMessage = false; // 重置打印状态
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            resetState();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPreMotion(PreMotionEvent e) {
        if (Utils.getLobbyStatus() == 1 || Utils.hypixelStatus() != 1 || Utils.isReplay()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (finished != 0 && mc.thePlayer.onGround && now - finished > checkDisabledTime) {
            Utils.print("&7[&dR&7] &adisabler enabled!");
            finished = 0;
            filledWidth = 0;
            disablerLoaded = true;
        }
        if (!shouldRun) {
            return;
        }

        if ((now - lobbyTime) < activationDelayMillis) {
            return;
        }
        running = true;
        e.setRotations(savedYaw, savedPitch);

        if (waitingForGround) {
            /*if (mc.thePlayer.ticksExisted <= 3) {
                waitingForGround = false;
                worldJoin = true;
            }
            else */
            if (mc.thePlayer.onGround) {
                mc.thePlayer.motionY = 0.42f;
                waitingForGround = false;
                worldJoin = false;
            }
            return;
        }

        if (ModuleUtils.inAirTicks >= 10 || worldJoin) {
            if (!applyingMotion) {
                applyingMotion = true;
                firstY = mc.thePlayer.posY;
            }

            if (tickCounter < getDisablerTicks()) { // 修改这里
                // 添加条件判断确保只打印一次
                if (!hasPrintedRunningMessage) {
                    Utils.print("&7[&dR&7] running disabler...");
                    hasPrintedRunningMessage = true;
                }
                shouldRender = true;

                mc.thePlayer.motionX = 0;
                mc.thePlayer.motionY = 0;
                mc.thePlayer.motionZ = 0;

                if (mc.thePlayer.posY != firstY) {
                    if (!reset) {
                        resetState();
                        activationDelayMillis = 5000;
                        reset = true;
                        Utils.print("&7[&dR&7] &adisabler reset, wait a few seconds...");
                    } else {
                        shouldRun = false;
                        applyingMotion = false;
                        running = false;
                        Utils.print("&7[&dR&7] &cfailed to reset disabler, plz try again!");
                    }
                }

                if (mc.thePlayer.ticksExisted % 2 == 0) {
                    e.setPosZ(e.getPosZ() + 0.075);
                    e.setPosX(e.getPosX() + 0.075);
                }

                tickCounter++;
            } else if (!warningDisplayed) {
                double totalTimeSeconds = (now - lobbyTime) / 1000.0;
                warningDisplayed = true;
                finished = now;
                shouldRender = false;
                shouldRun = false;
                applyingMotion = false;
                running = false;
            }
        }

        filledWidth = (float) (barWidth * tickCounter / getDisablerTicks()); // 修改这里
        final ScaledResolution scaledResolution = new ScaledResolution(mc);
        int[] disp = {scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight(), scaledResolution.getScaleFactor()};
        barX = scaledResolution.getScaledWidth() / 2 - barWidth / 2;
        barY = scaledResolution.getScaledHeight() / 2 + 28;
    }

    @SubscribeEvent()
    public void onMoveInput(PrePlayerInputEvent e) {
        if (!running || Utils.isReplay() || Utils.spectatorCheck()) {
            return;
        }
        e.setForward(0);
        e.setStrafe(0);
        mc.thePlayer.movementInput.jump = false;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent ev) {
        if (!Utils.nullCheck() || mc.currentScreen != null || !shouldRun || !shouldRender) {
            return;
        }

        // 更新动画计时
        if (System.currentTimeMillis() - lastDotUpdate > 333) {
            dotAnimationCounter = (dotAnimationCounter + 1) % 4;
            lastDotUpdate = System.currentTimeMillis();
        }

        color = Theme.getGradient((int) HUD.theme.getInput(), 0);
        String baseText = "Watchdog Sleeping";

        // 生成动态省略号
        String dots = "";
        switch (dotAnimationCounter) {
            case 1:
                dots = ".";
                break;
            case 2:
                dots = "..";
                break;
            case 3:
                dots = "...";
                break;
            case 4:
                dots = "....";
                break;
            default:
                dots = "";
                break;
        }

        String fullText = baseText + dots;
        int textWidth = mc.fontRendererObj.getStringWidth(fullText);
        ScaledResolution sr = new ScaledResolution(mc);

        // 绘制文字
        int xPos = (sr.getScaledWidth() - textWidth) / 2;
        int yPos = sr.getScaledHeight() / 2 + 8;
        mc.fontRendererObj.drawStringWithShadow(fullText, xPos, yPos, color);

        // 绘制进度条
        if (tickCounter > 0) {
            // 背景阴影
            drawRoundedRect(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 3, 0xAA000000);

            // 背景条
            drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, 2, 0xAA222222);

            // 渐变填充条
            drawGradientRect(
                    barX + 0.5f,
                    barY + 0.5f,
                    barX + filledWidth - 0.5f,
                    barY + barHeight - 0.5f,
                    color,
                    adjustColor(color, -50)
            );

            // 高光边框
            drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, 2, 0x44FFFFFF);
        }
    }

    // 新增绘图方法
    private void drawRoundedRect(float x, float y, float x2, float y2, float radius, int color) {
        glEnable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        glColor4f(red, green, blue, alpha);

        // 绘制圆角矩形（简化实现）
        glBegin(GL_POLYGON);
        for (int i = 0; i <= 90; i += 30) {
            float rad = (float) Math.toRadians(i);
            glVertex2f(x + radius + (float) Math.cos(rad) * radius,
                    y + radius + (float) Math.sin(rad) * radius);
        }
        // 类似处理其他三个角...
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    private void drawGradientRect(float left, float top, float right, float bottom, int startColor, int endColor) {
        glEnable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);

        glBegin(GL_QUADS);
        // 起始颜色
        glColor4f(
                (startColor >> 16 & 0xFF) / 255.0F,
                (startColor >> 8 & 0xFF) / 255.0F,
                (startColor & 0xFF) / 255.0F,
                (startColor >> 24 & 0xFF) / 255.0F
        );
        glVertex2f(left, top);
        glVertex2f(left, bottom);

        // 结束颜色
        glColor4f(
                (endColor >> 16 & 0xFF) / 255.0F,
                (endColor >> 8 & 0xFF) / 255.0F,
                (endColor & 0xFF) / 255.0F,
                (endColor >> 24 & 0xFF) / 255.0F
        );
        glVertex2f(right, bottom);
        glVertex2f(right, top);
        glEnd();

        glShadeModel(GL_FLAT);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    private int adjustColor(int original, int amount) {
        int r = Math.min(255, Math.max(0, (original >> 16 & 0xFF) + amount));
        int g = Math.min(255, Math.max(0, (original >> 8 & 0xFF) + amount));
        int b = Math.min(255, Math.max(0, (original & 0xFF) + amount));
        return (original & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}