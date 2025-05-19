package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import keystrokesmod.module.impl.combat.Velocity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class TargetStrafe extends Module {
    private ButtonSetting autoVelocityReverse;
    private double originalVelocityMode = -1.0;
    private boolean wasVelocityEnabled = false;
    private ButtonSetting requireBhop;
    private ButtonSetting requireJump;
    private SliderSetting radiusSetting;
    private SliderSetting speedFactor;
    private ButtonSetting autoDirection;
    private SliderSetting airControl;
    private int currentDirection = 1;

    private static class Vector2D {
        public final double x;
        public final double z;

        public Vector2D(double x, double z) {
            this.x = x;
            this.z = z;
        }

        public Vector2D add(Vector2D other) {
            return new Vector2D(x + other.x, z + other.z);
        }

        public Vector2D subtract(Vector2D other) {
            return new Vector2D(x - other.x, z - other.z);
        }

        public Vector2D scale(double factor) {
            return new Vector2D(x * factor, z * factor);
        }

        public Vector2D rotate(double degrees) {
            double rad = Math.toRadians(degrees);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            return new Vector2D(
                    x * cos - z * sin,
                    x * sin + z * cos
            );
        }

        public double length() {
            return Math.sqrt(x * x + z * z);
        }

        public Vector2D normalize() {
            double len = length();
            return len > 1E-5 ? new Vector2D(x / len, z / len) : this;
        }

        public double dot(Vector2D other) {
            return x * other.x + z * other.z;
        }
    }

    public TargetStrafe() {
        super("TargetStrafe", category.movement);
        this.registerSetting(requireBhop = new ButtonSetting("Require bhop", false));
        this.registerSetting(requireJump = new ButtonSetting("Require space", false));
        this.registerSetting(autoVelocityReverse = new ButtonSetting("Velocity Full Strafe", false));
        this.registerSetting(radiusSetting = new SliderSetting("Radius", 1.5, 0.8, 3.2, 0.1));
        this.registerSetting(speedFactor = new SliderSetting("Speed Factor", 1.0, 0.5, 2.0, 0.1));
        this.registerSetting(autoDirection = new ButtonSetting("Auto Direction", true));
        this.registerSetting(airControl = new SliderSetting("Air Control", 1.0, 0.5, 2.0, 0.1));
    }

    public void guiUpdate() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMoveInput(PrePlayerInputEvent e) {
        if (autoVelocityReverse.isToggled() && KillAura.target != null) {
            Velocity velocityModule = ModuleManager.velocity;
            if (velocityModule != null) {

                if (originalVelocityMode == -1.0) {
                    originalVelocityMode = velocityModule.velocityModes.getInput();
                    wasVelocityEnabled = velocityModule.isEnabled();
                }

                velocityModule.velocityModes.setValue(2.0);
                if (!velocityModule.isEnabled()) {
                    velocityModule.enable();
                }
            }
        }
        if (requireBhop.isToggled() && !ModuleManager.bhop.isEnabled()) return;
        if (requireJump.isToggled() && !Utils.jumpDown()) return;
        if (ModuleManager.scaffold.isEnabled) return;
        if (KillAura.target == null) return;

        EntityLivingBase target = KillAura.target;
        float radius = (float) radiusSetting.getInput();
        double speedMulti = speedFactor.getInput();

        Vector2D targetPos = new Vector2D(target.posX, target.posZ);
        Vector2D playerPos = new Vector2D(mc.thePlayer.posX, mc.thePlayer.posZ);
        Vector2D toTarget = targetPos.subtract(playerPos);
        double distance = toTarget.length();

        if (!mc.thePlayer.onGround) {
            boolean pressingLeft = mc.gameSettings.keyBindLeft.isKeyDown();
            boolean pressingRight = mc.gameSettings.keyBindRight.isKeyDown();

            if (pressingLeft && !pressingRight) {
                currentDirection = -1;
            } else if (pressingRight && !pressingLeft) {
                currentDirection = 1;
            } else if (!autoDirection.isToggled()) {
                currentDirection = (distance > radius * 1.1) ? 1 : -1;
            }
        }

        double currentBhopSpeed = ModuleManager.bhop.isEnabled() ?
                Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ) : 0.2873;

        Vector2D targetMotion = new Vector2D(
                target.posX - target.prevPosX,
                target.posZ - target.prevPosZ
        ).scale(0.25);
        targetPos = targetPos.add(targetMotion);
        if (distance < 0.1) return;

        double dynamicCompensation = Math.min(1.0, (distance - radius) * 0.2);
        Vector2D perpendicular = toTarget.rotate(90 * currentDirection)
                .normalize()
                .scale(radius * (1.0 - dynamicCompensation));

        Vector2D desiredDirection = targetPos.add(perpendicular)
                .subtract(playerPos)
                .normalize()
                .scale(currentBhopSpeed * speedMulti);

        float yaw = mc.thePlayer.rotationYaw;
        Vector2D relativeMove = desiredDirection.rotate(-yaw);

        double airMultiplier = mc.thePlayer.onGround ? 1.0 : airControl.getInput();
        double adaptiveBoost = 1.0 + (Math.abs(distance - radius) * 0.3) * airMultiplier;
        double strafe = relativeMove.x * adaptiveBoost;
        double forward = relativeMove.z * adaptiveBoost;

        Vector2D currentMotion = new Vector2D(mc.thePlayer.motionX, mc.thePlayer.motionZ);
        double motionAlignment = currentMotion.normalize().dot(desiredDirection.normalize());

        if (motionAlignment < -0.1) {
            double correction = 1.0 + motionAlignment * 1.5;
            strafe *= Math.max(0.6, correction);
            forward *= Math.max(0.6, correction);
        }

        e.setStrafe((float) strafe);
        e.setForward((float) forward);
    }

    @Override
    public void onDisable() {
        if (originalVelocityMode != -1.0) {
            Velocity velocityModule = ModuleManager.velocity;
            if (velocityModule != null) {

                velocityModule.velocityModes.setValue(originalVelocityMode);

                if (!wasVelocityEnabled && velocityModule.isEnabled()) {
                    velocityModule.disable();
                }
            }
            originalVelocityMode = -1.0;
            wasVelocityEnabled = false;
        }
        super.onDisable();
    }
}