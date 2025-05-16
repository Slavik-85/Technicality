package keystrokesmod.module.impl.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceiveAllPacketsEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.ModuleUtils;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import org.lwjgl.input.Keyboard;

public class Velocity extends Module {
    public SliderSetting velocityModes;
    // Normal模式设置
    public static SliderSetting vertical, horizontal;
    // Hypixel模式设置
    public static SliderSetting hypixelVertical, hypixelHorizontal, hypixelExplosionsHorizontal, hypixelExplosionsVertical;
    // Reverse模式设置
    public static SliderSetting reverseHorizontal, reverseExplosionsHorizontal, reverseExplosionsVertical;
    // AirMotion模式设置
    public static SliderSetting airMotionHorizontal, airMotionVertical, airExplosionsHorizontal, airExplosionsVertical;
    // Delay模式设置
    public static SliderSetting delaySlider;
    public static ButtonSetting delayReverse;

    public static SliderSetting minExtraSpeed, extraSpeedBoost;
    private SliderSetting chance;
    private ButtonSetting onlyWhileAttacking;
    private ButtonSetting onlyWhileTargeting;
    private ButtonSetting disableS;
    private ButtonSetting zzWhileNotTargeting, delayPacket;
    public ButtonSetting allowSelfFireball;
    public static ButtonSetting reverseDebug;
    private KeySetting switchToReverse, switchToPacket;
    private boolean stopFBvelo;
    public boolean disableVelo;
    private long delay;
    // AirMotion设置
    private ButtonSetting disableInLobby;
    private ButtonSetting cancelBurning;
    private ButtonSetting cancelExplosion;
    private ButtonSetting cancelWhileFalling;
    private ButtonSetting cancelOffGround;

    // Delay模式变量
    private final Map<Object, Integer> delayedPackets = new LinkedHashMap<>();
    private boolean isDelaying = false;
    private boolean isReleasing = false;

    private String[] velocityModesString = new String[]{"Normal", "Hypixel", "Reverse", "AirMotion", "Delay"};

    public Velocity() {
        super("Velocity", category.combat);
        this.registerSetting(velocityModes = new SliderSetting("Mode", 0, velocityModesString));

        // Normal模式设置
        this.registerSetting(horizontal = new SliderSetting("Horizontal", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(vertical = new SliderSetting("Vertical", 0.0, 0.0, 100.0, 1.0));

        // Hypixel模式设置
        this.registerSetting(new DescriptionSetting("Hypixel Settings:"));
        this.registerSetting(hypixelHorizontal = new SliderSetting("Hypixel H", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(hypixelVertical = new SliderSetting("Hypixel V", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(hypixelExplosionsHorizontal = new SliderSetting("Hypixel EH", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(hypixelExplosionsVertical = new SliderSetting("Hypixel EV", 0.0, 0.0, 100.0, 1.0));

        // Reverse模式设置
        this.registerSetting(new DescriptionSetting("Reverse Settings:"));
        this.registerSetting(reverseHorizontal = new SliderSetting("Reverse H", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(reverseExplosionsHorizontal = new SliderSetting("Reverse EH", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(reverseExplosionsVertical = new SliderSetting("Reverse EV", 0.0, 0.0, 100.0, 1.0));

        // AirMotion模式设置
        this.registerSetting(new DescriptionSetting("AirMotion Settings:"));
        this.registerSetting(airMotionHorizontal = new SliderSetting("AM H", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(airMotionVertical = new SliderSetting("AM V", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(airExplosionsHorizontal = new SliderSetting("AM EH", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(airExplosionsVertical = new SliderSetting("AM EV", 0.0, 0.0, 100.0, 1.0));

        // Delay模式设置
        this.registerSetting(new DescriptionSetting("Delay Settings:"));
        this.registerSetting(delayReverse = new ButtonSetting("Reverse", false));
        this.registerSetting(delaySlider = new SliderSetting("Delay Ticks", 5, 1, 40, 1));

        // 通用设置
        this.registerSetting(minExtraSpeed = new SliderSetting("Max Boost Speed", 0, 0, 0.7, 0.01));
        this.registerSetting(extraSpeedBoost = new SliderSetting("Boost Multi", "%", 0, 0, 100, 1));
        this.registerSetting(chance = new SliderSetting("Chance", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(onlyWhileAttacking = new ButtonSetting("Only attacking", false));
        this.registerSetting(onlyWhileTargeting = new ButtonSetting("Only targeting", false));
        this.registerSetting(disableS = new ButtonSetting("Disable on S", false));
        this.registerSetting(zzWhileNotTargeting = new ButtonSetting("00 not target", false));
        this.registerSetting(allowSelfFireball = new ButtonSetting("Self fireball", false));
        this.registerSetting(switchToReverse = new KeySetting("To Reverse", Keyboard.KEY_SPACE));
        this.registerSetting(switchToPacket = new KeySetting("To Packet", Keyboard.KEY_SPACE));
        this.registerSetting(reverseDebug = new ButtonSetting("Debug Msg", false));

        // AirMotion其他设置
        this.registerSetting(disableInLobby = new ButtonSetting("Disable lobby", false));
        this.registerSetting(cancelBurning = new ButtonSetting("Cancel burning", true));
        this.registerSetting(cancelExplosion = new ButtonSetting("Cancel explosion", true));
        this.registerSetting(cancelWhileFalling = new ButtonSetting("Cancel falling", true));
        this.registerSetting(cancelOffGround = new ButtonSetting("Cancel off ground", true));
    }

    public void guiUpdate() {
        int mode = (int) velocityModes.getInput();
        boolean isNormal = mode == 0;
        boolean isHypixel = mode == 1;
        boolean isReverse = mode == 2;
        boolean isAirMotion = mode == 3;
        boolean isDelay = mode == 4;

        // Normal模式可见性
        horizontal.setVisible(isNormal, this);
        vertical.setVisible(isNormal, this);
        onlyWhileAttacking.setVisible(isNormal, this);
        onlyWhileTargeting.setVisible(isNormal, this);
        disableS.setVisible(isNormal, this);

        // Hypixel模式可见性
        hypixelHorizontal.setVisible(isHypixel, this);
        hypixelVertical.setVisible(isHypixel, this);
        hypixelExplosionsHorizontal.setVisible(isHypixel, this);
        hypixelExplosionsVertical.setVisible(isHypixel, this);
        zzWhileNotTargeting.setVisible(isHypixel, this);
        allowSelfFireball.setVisible(isHypixel, this);
        switchToReverse.setVisible(isHypixel, this);

        // Reverse模式可见性
        reverseHorizontal.setVisible(isReverse, this);
        reverseExplosionsHorizontal.setVisible(isReverse, this);
        reverseExplosionsVertical.setVisible(isReverse, this);
        switchToPacket.setVisible(isReverse, this);
        minExtraSpeed.setVisible(isReverse, this);
        extraSpeedBoost.setVisible(isReverse, this);
        reverseDebug.setVisible(isReverse, this);

        // AirMotion模式可见性
        airMotionHorizontal.setVisible(isAirMotion, this);
        airMotionVertical.setVisible(isAirMotion, this);
        airExplosionsHorizontal.setVisible(isAirMotion, this);
        airExplosionsVertical.setVisible(isAirMotion, this);
        disableInLobby.setVisible(isAirMotion, this);
        cancelBurning.setVisible(isAirMotion, this);
        cancelExplosion.setVisible(isAirMotion, this);
        cancelWhileFalling.setVisible(isAirMotion, this);
        cancelOffGround.setVisible(isAirMotion, this);

        // Delay模式可见性
        delayReverse.setVisible(isDelay, this);
        delaySlider.setVisible(isDelay, this);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (Utils.tabbedIn()) {
            if (switchToReverse.isPressed() && velocityModes.getInput() == 1 && delay == 0) {
                velocityModes.setValue(2);
                delay = Utils.time();
                Utils.print(Utils.formatColor("&7[&dR&7]&7 Switched to &bReverse&7 Velocity mode"));
            }
            if (switchToPacket.isPressed() && velocityModes.getInput() == 2 && delay == 0) {
                velocityModes.setValue(1);
                delay = Utils.time();
                Utils.print(Utils.formatColor("&7[&dR&7]&7 Switched to &bPacket&7 Velocity mode"));
            }
        }
        if (delay > 0 && (Utils.time() - delay) > 100) {
            delay = 0;
        }
        // Delay模式逻辑
        if ((int) velocityModes.getInput() == 4) {
            isReleasing = false;
            List<Object> toRelease = new ArrayList<>();

            for (Map.Entry<Object, Integer> entry : new ArrayList<>(delayedPackets.entrySet())) {
                int ticksLeft = entry.getValue() - 1;
                if (ticksLeft <= 0) {
                    toRelease.add(entry.getKey());
                } else {
                    delayedPackets.put(entry.getKey(), ticksLeft);
                }
            }

            for (Object packet : toRelease) {
                if (packet instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
                    if (delayReverse.isToggled()) {
                        mc.thePlayer.motionX = -s12.getMotionX() / 8000.0;
                        mc.thePlayer.motionZ = -s12.getMotionZ() / 8000.0;
                    } else {
                        mc.thePlayer.motionX = s12.getMotionX() / 8000.0;
                        mc.thePlayer.motionZ = s12.getMotionZ() / 8000.0;
                    }
                    mc.thePlayer.motionY = s12.getMotionY() / 8000.0;
                } else if (packet instanceof C03PacketPlayer || packet instanceof C0FPacketConfirmTransaction) {
                    mc.getNetHandler().addToSendQueue((Packet) packet);
                }
                delayedPackets.remove(packet);
            }
            isDelaying = !delayedPackets.isEmpty();
        }
    }

    // 修改所有旧版爆炸设置引用
    @SubscribeEvent
    public void onReceivePacketAll(ReceiveAllPacketsEvent e) {
        if (velocityModes.getInput() == 4) { // Delay模式
            handleDelayMode(e);
        }
        if (velocityModes.getInput() == 3) { // AirMotion模式
            handleAirMotion(e);
        } else if (velocityModes.getInput() >= 1) {
            if (!Utils.nullCheck() || LongJump.stopVelocity || e.isCanceled() ||
                    (velocityModes.getInput() == 2 && ModuleUtils.firstDamage) ||
                    (ModuleManager.bhop.isEnabled() && ModuleManager.bhop.damageBoost.isToggled() &&
                            ModuleUtils.firstDamage && (!ModuleManager.bhop.damageBoostRequireKey.isToggled() ||
                            ModuleManager.bhop.damageBoostKey.isPressed()))) {
                return;
            }

            if (e.getPacket() instanceof S27PacketExplosion) {
                handleExplosionPacket(e);
            } else if (e.getPacket() instanceof S12PacketEntityVelocity) {
                handleVelocityPacket(e);
            }
        }
    }

    private void handleDelayMode(ReceiveAllPacketsEvent e) {
        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) e.getPacket();
            if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
                if (!delayedPackets.containsKey(s12)) {
                    delayedPackets.put(s12, (int) delaySlider.getInput());
                    isDelaying = true;
                    e.setCanceled(true);
                }
            }
        } else if (e.getPacket() instanceof C03PacketPlayer || e.getPacket() instanceof C0FPacketConfirmTransaction) {
            if (isDelaying) {
                if (!delayedPackets.containsKey(e.getPacket())) {
                    delayedPackets.put(e.getPacket(), (int) delaySlider.getInput());
                    e.setCanceled(true);
                }
            }
        }
    }

    private void handleExplosionPacket(ReceiveAllPacketsEvent e) {
        S27PacketExplosion packet = (S27PacketExplosion) e.getPacket();
        int mode = (int) velocityModes.getInput();
        double eh = 0, ev = 0;

        switch(mode) {
            case 1: // Hypixel
                eh = hypixelExplosionsHorizontal.getInput();
                ev = hypixelExplosionsVertical.getInput();
                break;
            case 2: // Reverse
                eh = reverseExplosionsHorizontal.getInput();
                ev = reverseExplosionsVertical.getInput();
                break;
        }

        if (allowSelfFireball.isToggled() && ModuleUtils.threwFireball) {
            if ((mc.thePlayer.getPosition().distanceSq(packet.getX(), packet.getY(), packet.getZ()) <= ModuleUtils.MAX_EXPLOSION_DIST_SQ) || disableVelo) {
                disableVelo = true;
                ModuleUtils.threwFireball = false;
                e.setCanceled(false);
                return;
            }
        }

        if (!dontEditMotion() && !disableVelo) {
            mc.thePlayer.motionX += packet.func_149149_c() * (eh / 100.0);
            mc.thePlayer.motionY += packet.func_149144_d() * (ev / 100.0);
            mc.thePlayer.motionZ += packet.func_149147_e() * (eh / 100.0);
        }

        stopFBvelo = true;
        e.setCanceled(true);
        disableVelo = false;
    }

    private void handleVelocityPacket(ReceiveAllPacketsEvent e) {
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        int mode = (int) velocityModes.getInput();
        double h = 0, v = 0, eh = 0, ev = 0;

        switch(mode) {
            case 1: // Hypixel
                h = hypixelHorizontal.getInput();
                v = hypixelVertical.getInput();
                eh = hypixelExplosionsHorizontal.getInput();
                ev = hypixelExplosionsVertical.getInput();
                break;
            case 2: // Reverse
                h = reverseHorizontal.getInput();
                v = 100; // 保持原垂直
                eh = reverseExplosionsHorizontal.getInput();
                ev = reverseExplosionsVertical.getInput();
                break;
        }

        if (!stopFBvelo) {
            if (!dontEditMotion() && !disableVelo) {
                mc.thePlayer.motionX = (packet.getMotionX() / 8000.0) * (h / 100.0);
                mc.thePlayer.motionY = (packet.getMotionY() / 8000.0) * (v / 100.0);
                mc.thePlayer.motionZ = (packet.getMotionZ() / 8000.0) * (h / 100.0);
            }
        } else {
            if (!dontEditMotion() && !disableVelo) {
                if (eh == 0 && ev > 0) {
                    mc.thePlayer.motionY = (packet.getMotionY() / 8000.0) * (ev / 100.0);
                } else if (eh > 0 && ev == 0) {
                    mc.thePlayer.motionX = (packet.getMotionX() / 8000.0) * (eh / 100.0);
                    mc.thePlayer.motionZ = (packet.getMotionZ() / 8000.0) * (eh / 100.0);
                } else if (eh > 0 && ev > 0) {
                    mc.thePlayer.motionX = (packet.getMotionX() / 8000.0) * (eh / 100.0);
                    mc.thePlayer.motionY = (packet.getMotionY() / 8000.0) * (ev / 100.0);
                    mc.thePlayer.motionZ = (packet.getMotionZ() / 8000.0) * (eh / 100.0);
                }
            }
        }

        stopFBvelo = false;
        if (!disableVelo) {
            e.setCanceled(true);
        }
    }

    private void handleAirMotion(ReceiveAllPacketsEvent e) {
        if (!Utils.nullCheck() || LongJump.stopVelocity || e.isCanceled()) return;

        // 核心条件检查
        if (shouldCancelAllVelocity()) {
            e.setCanceled(true);
            return;
        }

        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            handleEntityVelocity((S12PacketEntityVelocity) e.getPacket(), e);
        } else if (e.getPacket() instanceof S27PacketExplosion) {
            handleExplosionVelocity((S27PacketExplosion) e.getPacket(), e);
        }
    }

    private boolean shouldCancelAllVelocity() {
        return false;
    }

    private void handleEntityVelocity(S12PacketEntityVelocity packet, ReceiveAllPacketsEvent e) {
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        // 新增条件检查链
        if (checkAirMotionConditions()) {
            e.setCanceled(true);
            return;
        }

        // 运动计算
        double h = horizontal.getInput() / 100.0;
        double v = vertical.getInput() / 100.0;

        mc.thePlayer.motionX = (packet.getMotionX() / 8000.0) * h;
        mc.thePlayer.motionY = (packet.getMotionY() / 8000.0) * v;
        mc.thePlayer.motionZ = (packet.getMotionZ() / 8000.0) * h;

        e.setCanceled(true);
    }

    private void handleExplosionVelocity(S27PacketExplosion packet, ReceiveAllPacketsEvent e) {
        // 爆炸包专用检查
        if (cancelExplosion.isToggled() || checkAirMotionConditions()) {
            e.setCanceled(true);
            return;
        }

        // 运动计算
        double h = horizontal.getInput() / 100.0;
        double v = vertical.getInput() / 100.0;

        mc.thePlayer.motionX += packet.func_149149_c() * h;
        mc.thePlayer.motionY += packet.func_149144_d() * v;
        mc.thePlayer.motionZ += packet.func_149147_e() * h;

        e.setCanceled(true);
    }

    // 新增统一条件检查方法
    private boolean checkAirMotionConditions() {
        // 大厅禁用检查
        if (disableInLobby.isToggled() && Utils.isLobby()) {
            return true;
        }

        // 燃烧状态检查
        if (cancelBurning.isToggled() && mc.thePlayer.isBurning()) {
            return true;
        }

        // 空中状态检查
        if (cancelOffGround.isToggled() && !mc.thePlayer.onGround) {
            return true;
        }

        // 下落状态检查
        if (cancelWhileFalling.isToggled() && mc.thePlayer.fallDistance > 0) {
            return true;
        }

        // 全局取消条件
        return (horizontal.getInput() == 0 && vertical.getInput() == 0) ||
                ModuleManager.bedAura.cancelKnockback();
    }

    @Override
    public String getInfo() {
        return (int) horizontal.getInput() + "%" + " " + (int) vertical.getInput() + "%";
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent ev) {
        if (velocityModes.getInput() == 0) {
            if (Utils.nullCheck() && !LongJump.stopVelocity && !ModuleManager.bedAura.cancelKnockback()) {
                if (mc.thePlayer.maxHurtTime <= 0 || mc.thePlayer.hurtTime != mc.thePlayer.maxHurtTime) {
                    return;
                }
                if (onlyWhileAttacking.isToggled() && !ModuleUtils.isAttacking) {
                    return;
                }
                if (dontEditMotion()) {
                    return;
                }
                if (onlyWhileTargeting.isToggled() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
                    return;
                }
                if (disableS.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
                    return;
                }
                if (chance.getInput() == 0) {
                    return;
                }
                if (chance.getInput() != 100) {
                    double ch = Math.random();
                    if (ch >= chance.getInput() / 100.0D) {
                        return;
                    }
                }
                if (horizontal.getInput() != 100.0D) {
                    mc.thePlayer.motionX *= horizontal.getInput() / 100;
                    mc.thePlayer.motionZ *= horizontal.getInput() / 100;
                }
                if (vertical.getInput() != 100.0D) {
                    mc.thePlayer.motionY *= vertical.getInput() / 100;
                }
            }
        }
    }

    public boolean dontEditMotion() {
        if (velocityModes.getInput() == 1 && zzWhileNotTargeting.isToggled() && KillAura.attackingEntity != null || ModuleManager.noFall.start || ModuleManager.blink.isEnabled() && ModuleManager.blink.cancelKnockback.isToggled() || ModuleManager.bedAura.cancelKnockback() || ModuleManager.tower.cancelKnockback()) {
            return true;
        }
        return false;
    }

    private boolean blinkModules() {
        if (ModuleManager.killAura.isEnabled() && ModuleManager.killAura.blinking.get()) {
            return true;
        }
        if (ModuleManager.blink.isEnabled() && ModuleManager.blink.started) {
            return true;
        }
        if (ModuleManager.antiVoid.isEnabled() && ModuleManager.antiVoid.started) {
            return true;
        }
        return false;
    }

}