package keystrokesmod.module.impl.movement;

import keystrokesmod.event.*;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public class LongJump extends Module {
	private final SliderSetting mode;

	private final SliderSetting boostSetting;
	private final SliderSetting verticalMotion;
	private final SliderSetting motionDecay;

	private final ButtonSetting manual;
	private final ButtonSetting onlyWithVelocity;
	private final KeySetting disableKey, flatKey;

	private final ButtonSetting allowStrafe;
	private final ButtonSetting invertYaw;
	private final ButtonSetting stopMovement;
	private final ButtonSetting hideExplosion;
	public final ButtonSetting spoofItem;
	private final ButtonSetting beginFlat;
	private final ButtonSetting silentSwing;
	private final ButtonSetting renderFloatProgress;

	private final KeySetting verticalKey;

	public final String[] modes = new String[]{"Hypixel", "Boost", "WatchDog Damage"};

	private boolean manualWasOn;

	private boolean notMoving;
	private boolean enabled, swapped;
	public static boolean function;

	private int boostTicks;
	private int dotAnimationCounter = 0;
	private long lastDotUpdate;
	public int lastSlot = -1, spoofSlot = -1;
	private int stopTime;
	private int rotateTick;

	private float yaw, pitch;

	private long fireballTime;
	private final long MAX_EXPLOSION_DIST_SQ = 9;
	private final long FIREBALL_TIMEOUT = 750L;

	public static boolean stopVelocity;
	public static boolean stopModules;
	public static boolean slotReset;
	public static int slotResetTicks;

	private int firstSlot = -1;

	private int color = new Color(0, 187, 255, 255).getRGB();

	// 新增进度条变量
	private final float barWidth = 80;
	private final float barHeight = 5;
	private float filledWidth;
	private float barX, barY;
	private final int borderColor = new Color(30, 30, 30, 200).getRGB();


	/*MoonLight Code Begin*/
	static class DamageContainer {
		static final int MODE = 2;
		static int dmgTicks = 0;

		static boolean velo = false;
		static int ticksSinceVelocity = 0;
		static int ticks = 0;
		static double distance = 0;
		static int lastSlot = -1;

	}
	/*MoonLight Code End*/

	public LongJump() {
		super("Long Jump", category.movement);
		this.registerSetting(mode = new SliderSetting("Mode", 0, modes));

		this.registerSetting(manual = new ButtonSetting("Manual", false));
		this.registerSetting(onlyWithVelocity = new ButtonSetting("Only while velocity enabled", false));
		this.registerSetting(disableKey = new KeySetting("Disable key", Keyboard.KEY_SPACE));

		this.registerSetting(boostSetting = new SliderSetting("Horizontal boost", 1.7, 0.0, 2.0, 0.05));
		this.registerSetting(verticalMotion = new SliderSetting("Vertical motion", 0, 0.4, 0.9, 0.01));
		this.registerSetting(motionDecay = new SliderSetting("Motion decay", 17, 1, 40, 1));
		this.registerSetting(allowStrafe = new ButtonSetting("Allow strafe", false));
		this.registerSetting(invertYaw = new ButtonSetting("Invert yaw", true));
		this.registerSetting(stopMovement = new ButtonSetting("Stop movement", false));
		this.registerSetting(hideExplosion = new ButtonSetting("Hide explosion", false));
		this.registerSetting(spoofItem = new ButtonSetting("Spoof item", false));
		this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
		this.registerSetting(renderFloatProgress = new ButtonSetting("Render float progress", false));

		this.registerSetting(beginFlat = new ButtonSetting("Begin flat", false));
		this.registerSetting(verticalKey = new KeySetting("Vertical key", Keyboard.KEY_SPACE));
		this.registerSetting(flatKey = new KeySetting("Flat key", Keyboard.KEY_SPACE));

	}

	public void guiUpdate() {
		this.onlyWithVelocity.setVisible(manual.isToggled(), this);
		this.disableKey.setVisible(manual.isToggled(), this);
		this.spoofItem.setVisible(!manual.isToggled(), this);
		this.silentSwing.setVisible(!manual.isToggled(), this);

		this.renderFloatProgress.setVisible(mode.getInput() == 0, this);

		this.verticalMotion.setVisible(mode.getInput() == 0, this);
		this.motionDecay.setVisible(mode.getInput() == 0, this);
		this.beginFlat.setVisible(mode.getInput() == 0, this);
		this.verticalKey.setVisible(mode.getInput() == 0 && beginFlat.isToggled(), this);
		this.flatKey.setVisible(mode.getInput() == 0 && !beginFlat.isToggled(), this);
	}

	public void onEnable() {
		if (ModuleUtils.profileTicks <= 1) return;
		if (!manual.isToggled()) {
			if (Utils.getTotalHealth(mc.thePlayer) <= 3) {
				Utils.sendMessage("&cPrevented throwing fireball due to low health");
				disable();
				return;
			}
			enabled();
		}
		filledWidth = 0;
		final ScaledResolution scaledResolution = new ScaledResolution(mc);
		int[] disp = {scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight(), scaledResolution.getScaleFactor()};
		barX = scaledResolution.getScaledWidth() / 2 - barWidth / 2;
		barY = scaledResolution.getScaledHeight() / 2 + 28; // 下移16像素




		/*MoonLight Code Begin*/

		DamageContainer.lastSlot = mc.thePlayer.inventory.currentItem;
		DamageContainer.ticks = 0;
		DamageContainer.distance = 0;


		if (mode.getInput() == DamageContainer.MODE) {
			DamageContainer.dmgTicks = 0;
		}
		/*MoonLight Code End*/

	}

	public void onDisable() {
		DamageContainer.velo = false;
		DamageContainer.ticksSinceVelocity = 0;
		/*disabled();*/
	}

    /*public boolean onChat(String chatMessage) {
        String msg = util.strip(chatMessage);

        if (msg.equals("Build height limit reached!")) {
            client.print("fb fly build height");
            modules.disable(scriptName);
            return false;
        }
        return true;
    }*/

	@SubscribeEvent
	public void onPreUpdate(PreUpdateEvent e) {
		if (manual.isToggled()) {
			manualWasOn = true;
		} else {
			if (manualWasOn) {
				disabled();
			}
			manualWasOn = false;
		}

		if (manual.isToggled() && disableKey.isPressed() && Utils.jumpDown()) {
			function = false;
			disabled();
		}

		if (spoofItem.isToggled() && lastSlot != -1 && !manual.isToggled()) {
			((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
			((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
		}

		if (swapped && rotateTick == 0) {
			resetSlot();
			swapped = false;
		}

		if (!function) {
			if (manual.isToggled() && !enabled && (!onlyWithVelocity.isToggled() || onlyWithVelocity.isToggled() && ModuleManager.velocity.isEnabled())) {
				if (ModuleUtils.threwFireballLow) {
					ModuleManager.velocity.disableVelo = true;
					enabled();
				}
			}
			return;
		}

		if (enabled) {
			if (!Utils.isMoving()) notMoving = true;


			if (mode.getInput() == DamageContainer.MODE) {
				if (DamageContainer.velo)
					DamageContainer.ticksSinceVelocity++;

			} else {
				if (boostSetting.getInput() == 0 && verticalMotion.getInput() == 0) {
					Utils.print("&cValues are set to 0!");
					disabled();
					return;
				}

				int fireballSlot = setupFireballSlot(true);
				if (fireballSlot != -1) {
					if (!manual.isToggled()) {
						lastSlot = spoofSlot = mc.thePlayer.inventory.currentItem;
						if (mc.thePlayer.inventory.currentItem != fireballSlot) {
							mc.thePlayer.inventory.currentItem = fireballSlot;
							swapped = true;
						}

					}
					//("Set fireball slot");
					rotateTick = 1;
					if (stopMovement.isToggled()) {
						stopTime = 1;
					}
				} // auto disables if -1
				enabled = false;
			}


		}

		if (stopTime == -1 && ++boostTicks > (!verticalKey() ? 33/*flat motion ticks*/ : (!notMoving ? 32/*normal motion ticks*/ : 33/*vertical motion ticks*/))) {
			disabled();
			return;
		}

		if (fireballTime > 0 && (System.currentTimeMillis() - fireballTime) > FIREBALL_TIMEOUT) {
			Utils.print("&cFireball timed out.");
			disabled();
			return;
		}
		if (boostTicks > 0) {
			if (mode.getInput() == 0) {
				modifyVertical(); // has to be onPreUpdate
			}
			//Utils.print("Modifying vertical");
			if (allowStrafe.isToggled() && boostTicks < 32) {
				Utils.setSpeed(Utils.getHorizontalSpeed(mc.thePlayer));
				//Utils.print("Speed");
			}
		}

		filledWidth = (barWidth * boostTicks) / 33.0f;

		if (stopMovement.isToggled() && !notMoving) {
			if (stopTime > 0) {
				++stopTime;
			}
		}

		if (mc.thePlayer.onGround && boostTicks > 2) {
			disabled();
		}

		if (firstSlot != -1) {
			mc.thePlayer.inventory.currentItem = firstSlot;
		}
		if (boostTicks > 0) {
			filledWidth = (barWidth * boostTicks) / 33.0f;
		}
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent ev) {
		if (!Utils.nullCheck()) return;
		/*MoonLight Code Begin*/
		if (mode.getInput() == DamageContainer.MODE) {
			double distance = Math.round(DamageContainer.distance * 100.0) / 100.0;
			String fullText = distance + " blocks";
			ScaledResolution sr = new ScaledResolution(mc);
			int xPos = (sr.getScaledWidth() - mc.fontRendererObj.getStringWidth(fullText)) / 2;
			int yPos = sr.getScaledHeight() / 2 + 8;
			mc.fontRendererObj.drawStringWithShadow(fullText, xPos, yPos, color);
			return;
		}
		/*MoonLight Code End*/

		if (mc.currentScreen != null || !renderFloatProgress.isToggled() || mode.getInput() != 0 || !function) return;

		// 更新动画计时（333ms间隔）
		if (System.currentTimeMillis() - lastDotUpdate > 333) {
			dotAnimationCounter = (dotAnimationCounter + 1) % 4;
			lastDotUpdate = System.currentTimeMillis();
		}

		color = Theme.getGradient((int) HUD.theme.getInput(), 0);
		String baseText = "Flying";

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

		// 绘制文字
		String fullText = baseText + dots;
		int textWidth = mc.fontRendererObj.getStringWidth(fullText);
		ScaledResolution sr = new ScaledResolution(mc);
		int xPos = (sr.getScaledWidth() - textWidth) / 2;
		int yPos = sr.getScaledHeight() / 2 + 8;
		mc.fontRendererObj.drawStringWithShadow(fullText, xPos, yPos, color);

		// 绘制进度条
		if (boostTicks > 0) {
			// 背景阴影
			drawRoundedRect(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 3, 0xAA000000);

			// 背景条
			drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, 2, 0xAA222222);

			// 渐变填充
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

		// 绘制圆角矩形
		glBegin(GL_POLYGON);
		for (int i = 0; i <= 360; i += 30) {
			float rad = (float) Math.toRadians(i);
			float cx = x + radius + (float) Math.cos(rad) * radius;
			float cy = y + radius + (float) Math.sin(rad) * radius;
			glVertex2f(cx, cy);
		}
		glEnd();

		glEnable(GL_TEXTURE_2D);
		glDisable(GL_BLEND);
	}

	private void drawGradientRect(float left, float top, float right, float bottom, int startColor, int endColor) {
		glEnable(GL_BLEND);
		glDisable(GL_TEXTURE_2D);
		glShadeModel(GL_SMOOTH);

		glBegin(GL_QUADS);
		glColor4f(
				(startColor >> 16 & 0xFF) / 255.0F,
				(startColor >> 8 & 0xFF) / 255.0F,
				(startColor & 0xFF) / 255.0F,
				(startColor >> 24 & 0xFF) / 255.0F
		);
		glVertex2f(left, top);
		glVertex2f(left, bottom);

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

	@SubscribeEvent
	public void onSlotUpdate(SlotUpdateEvent e) {
		if (lastSlot != -1) {
			spoofSlot = e.slot;
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPreMotion(PreMotionEvent e) {
		if (!Utils.nullCheck()) {
			return;
		}


		if (mode.getInput() == DamageContainer.MODE) {
			++DamageContainer.dmgTicks;
			if (DamageContainer.dmgTicks < 44 && DamageContainer.dmgTicks % 2 == 0) {
				e.setPosX(e.getPosX() - (double) handleX() * 0.09D);
				e.setPosZ(e.getPosZ() - (double) handleZ() * 0.09D);
			}

			if (DamageContainer.dmgTicks == 1) {
				e.setPosY(e.getPosY() + 0.034939999302625656D);
				e.setOnGround(false);
			} else if (DamageContainer.dmgTicks < 45) {
				e.setPosY(e.getPosY() + (DamageContainer.dmgTicks % 2 != 0 ? 0.1449999064207077D : Math.random() / 5000.0D));
				e.setOnGround(false);
				if (DamageContainer.dmgTicks == 44) {
					Utils.strafe((0.2830 + Utils.getSpeedAmplifier() * 0.2) - 0.005);
					mc.thePlayer.jump();
				}
			} else if (DamageContainer.dmgTicks == 45) {
				((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.6f;
				e.setPosY(e.getPosY() + 1.0E-5D);
				mc.getNetHandler().addToSendQueue(new C03PacketPlayer(true));

			} else if (DamageContainer.dmgTicks == 47) {
				((IAccessorMinecraft) mc).getTimer().timerSpeed = 1;
			}

			if (DamageContainer.ticksSinceVelocity > 3 && DamageContainer.ticksSinceVelocity < 38 || DamageContainer.ticksSinceVelocity > 37 && mc.thePlayer.motionY <= 0.0D) {
				mc.thePlayer.motionY += 0.02830D;
			}

			switch (DamageContainer.ticksSinceVelocity) {
				case 1:
					mc.thePlayer.motionX *= 2.1;
					mc.thePlayer.motionZ *= 2.1;
				case 2:
				case 3:
				case 4:
				case 5:
				case 8:
				case 9:
				case 10:
				case 11:
				default:
					break;
				case 6:
					mc.thePlayer.motionY = 0.02999D;
					break;
				case 7:
					mc.thePlayer.motionY = 0.02995D;
					break;
				case 12:
					mc.thePlayer.motionY = 0.0D;
					break;
				case 13:
					mc.thePlayer.motionY += 0.00999D;
			}

			if (DamageContainer.velo && mc.thePlayer.onGround)
				toggle();
			return;
		}


		if (rotateTick >= 3) {
			rotateTick = 0;
		}
		if (rotateTick >= 1) {
			if ((invertYaw.isToggled() || stopMovement.isToggled()) && !notMoving) {
				if (!stopMovement.isToggled()) {
					yaw = mc.thePlayer.rotationYaw - 180f;
					pitch = 90f;
				} else {
					yaw = mc.thePlayer.rotationYaw - 180f;
					pitch = 66.3f;//(float) pitchVal.getInput();
				}
			} else {
				yaw = mc.thePlayer.rotationYaw;
				pitch = 90f;
			}
			e.setRotations(yaw, pitch);
		}
		if (rotateTick > 0 && ++rotateTick >= 3) {
			int fireballSlot = setupFireballSlot(false);
			if (fireballSlot != -1) {
				fireballTime = System.currentTimeMillis();
				if (!manual.isToggled()) {
					mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
					if (silentSwing.isToggled()) {
						mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
					} else {
						mc.thePlayer.swingItem();
						if (!spoofItem.isToggled()) {
							mc.getItemRenderer().resetEquippedProgress();
						}
					}
				}
				stopVelocity = true;
				//Utils.print("Right click");
			}
		}
		if (boostTicks == 1) {
			if (invertYaw.isToggled()) {
				//client.setMotion(client.getMotion().x, client.getMotion().y + 0.035d, client.getMotion().z);
			}
			modifyHorizontal();
			stopVelocity = false;
			if (!manual.isToggled() && !allowStrafe.isToggled() && mode.getInput() == 1) {
				disabled();
			}
		}
	}

	@SubscribeEvent
	public void onPostPlayerInput(PostPlayerInputEvent e) {
		if (!function) {
			return;
		}
		mc.thePlayer.movementInput.jump = false;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST) // called last in order to apply fix
	public void onMoveInput(PrePlayerInputEvent e) {
		if (!function) {
			return;
		}
		if (rotateTick > 0 || fireballTime > 0) {
			if (Utils.isMoving()) e.setForward(1);
			e.setStrafe(0);
		}
		if (notMoving && boostTicks < 3) {
			e.setForward(0);
			e.setStrafe(0);
			Utils.setSpeed(0);
		}
		if (stopMovement.isToggled() && !notMoving && stopTime >= 1) {
			e.setForward(0);
			e.setStrafe(0);
			Utils.setSpeed(0);
		}
	}

	@SubscribeEvent
	public void onReceivePacket(ReceivePacketEvent e) {
		if (!function) {
			return;
		}
		Packet packet = e.getPacket();
		if (packet instanceof S27PacketExplosion) {
			S27PacketExplosion s27 = (S27PacketExplosion) packet;
			if (fireballTime == 0 || mc.thePlayer.getPosition().distanceSq(s27.getX(), s27.getY(), s27.getZ()) > MAX_EXPLOSION_DIST_SQ) {
				e.setCanceled(true);
				//Utils.print("0 fb time / out of dist");
			}

			stopTime = -1;
			fireballTime = 0;
			resetSlot();
			boostTicks = 0; // +1 on next pre update
			//Utils.print("set start vals");

			//client.print(client.getPlayer().getTicksExisted() + " s27 " + boostTicks + " " + client.getPlayer().getHurtTime() + " " + client.getPlayer().getSpeed());
		} else if (packet instanceof S08PacketPlayerPosLook) {
			Utils.print("&cReceived setback, disabling.");
			disabled();
		}/*MoonLight Code Begin*/ else if (packet instanceof S12PacketEntityVelocity) {
			S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) packet;
			if (velocityPacket.getEntityID() != mc.thePlayer.getEntityId() || e.isCanceled()) return;
			double x = (double) velocityPacket.getMotionX() / 8000.0D;
			double z = (double) velocityPacket.getMotionZ() / 8000.0D;
			double speed = Math.hypot(x, z);
			Utils.strafe(MathHelper.clamp_double(speed, 0.44D, 0.48D));
			mc.thePlayer.motionY = (double) velocityPacket.getMotionY() / 8000.0D;
			DamageContainer.ticksSinceVelocity = 0;
			e.setCanceled(true);
			DamageContainer.velo = true;
		}/*MoonLight Code End*/

		if (hideExplosion.isToggled() && fireballTime != 0 && (packet instanceof S0EPacketSpawnObject || packet instanceof S2APacketParticles || packet instanceof S29PacketSoundEffect)) {
			e.setCanceled(true);
		}
	}

	private int getFireballSlot() {
		int n = -1;
		for (int i = 0; i < 9; ++i) {
			final ItemStack getStackInSlot = mc.thePlayer.inventory.getStackInSlot(i);
			if (getStackInSlot != null && getStackInSlot.getItem() == Items.fire_charge) {
				n = i;
				break;
			}
		}
		return n;
	}

	public static void drawRect(float x, float y, float x2, float y2, int color) {
		float var5;
		if (x < x2) {
			var5 = x;
			x = x2;
			x2 = var5;
		}
		if (y < y2) {
			var5 = y;
			y = y2;
			y2 = var5;
		}
		float alpha = (color >> 24 & 0xFF) / 255.0F;
		float red = (color >> 16 & 0xFF) / 255.0F;
		float green = (color >> 8 & 0xFF) / 255.0F;
		float blue = (color & 0xFF) / 255.0F;

		glEnable(GL_BLEND);
		glDisable(GL_TEXTURE_2D);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glColor4f(red, green, blue, alpha);

		glBegin(GL_QUADS);
		glVertex2f(x, y2);
		glVertex2f(x2, y2);
		glVertex2f(x2, y);
		glVertex2f(x, y);
		glEnd();

		glEnable(GL_TEXTURE_2D);
		glDisable(GL_BLEND);
	}

	private void enabled() {
		slotReset = false;
		slotResetTicks = 0;
		enabled = function = true;

		stopModules = true;
	}

	private void disabled() {
		fireballTime = rotateTick = stopTime = 0;
		boostTicks = -1;
		resetSlot();
		enabled = function = notMoving = stopVelocity = stopModules = swapped = false;
		filledWidth = 0;
		if (!manual.isToggled()) {
			disable();
		}
	}

	private int setupFireballSlot(boolean pre) {
		// only cancel bad packet right-click on the tick we are sending it
		int fireballSlot = getFireballSlot();
		if (fireballSlot == -1) {
			Utils.print("&cFireball not found.");
			disabled();
		} else if ((pre && Utils.distanceToGround(mc.thePlayer) > 3)/* || (!pre && !PacketUtil.canRightClickItem())*/) { //needs porting
			Utils.print("&cCan't throw fireball right now.");
			disabled();
			fireballSlot = -1;
		}
		return fireballSlot;
	}

	private void resetSlot() {
		if (lastSlot != -1 && !manual.isToggled()) {
			mc.thePlayer.inventory.currentItem = lastSlot;
			lastSlot = -1;
			spoofSlot = -1;
			firstSlot = -1;
			if (spoofItem.isToggled()) {
				((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(false);
				((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(false);
			}
		}
		slotReset = true;
		stopModules = false;
	}

	private int getSpeedLevel() {
		for (PotionEffect potionEffect : mc.thePlayer.getActivePotionEffects()) {
			if (potionEffect.getEffectName().equals("potion.moveSpeed")) {
				return potionEffect.getAmplifier() + 1;
			}
			return 0;
		}
		return 0;
	}

	// only apply horizontal boost once
	void modifyHorizontal() {
		if (boostSetting.getInput() != 0) {

			double speed = boostSetting.getInput() - Utils.randomizeDouble(0.0001, 0);
			if (Utils.isMoving()) {
				Utils.setSpeed(speed);
			}
		}
	}

	private void modifyVertical() {
		if (verticalMotion.getInput() != 0) {
			double ver = ((!notMoving ? verticalMotion.getInput() : 1.16 /*vertical*/) * (1.0 / (1.0 + (0.05 * getSpeedLevel())))) + Utils.randomizeDouble(0.0001, 0.1);
			double decay = motionDecay.getInput() / 1000;
			if (boostTicks > 1 && !verticalKey()) {
				if (boostTicks > 1 || boostTicks <= (!notMoving ? 32/*horizontal motion ticks*/ : 33/*vertical motion ticks*/)) {
					mc.thePlayer.motionY = Utils.randomizeDouble(0.0101, 0.01);
				}
			} else {
				if (boostTicks >= 1 && boostTicks <= (!notMoving ? 32/*horizontal motion ticks*/ : 33/*vertical motion ticks*/)) {
					mc.thePlayer.motionY = ver - boostTicks * decay;
				} else if (boostTicks >= (!notMoving ? 32/*horizontal motion ticks*/ : 33/*vertical motion ticks*/) + 3) {
					mc.thePlayer.motionY = mc.thePlayer.motionY + 0.028;
					Utils.print("?");
				}
			}
		}
	}

	private boolean verticalKey() {
		if (notMoving) return true;
		return beginFlat.isToggled() ? verticalKey.isPressed() : !flatKey.isPressed();
	}

	private static float handleX() {
		return (float) (-Math.sin(Math.toRadians((float) mc.thePlayer.motionX)));
	}

	private static float handleZ() {
		return (float) Math.cos(Math.toRadians((float) mc.thePlayer.motionZ));
	}
}