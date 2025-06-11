package keystrokesmod.module.impl.movement;

import keystrokesmod.Technicality;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.*;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public class LongJump extends Module {
	private SliderSetting mode;

	private SliderSetting boostSetting;
	private SliderSetting motionTicks;
	private SliderSetting verticalMotion;
	private SliderSetting motionDecay;

	private ButtonSetting manual;
	private ButtonSetting onlyWithVelocity;
	private KeySetting disableKey, flatKey;

	private ButtonSetting allowStrafe;
	private ButtonSetting invertYaw;
	private ButtonSetting stopMovement;
	private ButtonSetting hideExplosion;
	public ButtonSetting spoofItem;
	private ButtonSetting beginFlat;
	private ButtonSetting silentSwing;
	private ButtonSetting renderFloatProgress;

	private KeySetting verticalKey;
	private SliderSetting pitchVal;

	public String[] modes = new String[]{"Hypixel", "Boost"};

	private boolean manualWasOn;

	private float yaw;
	private float pitch;

	private boolean notMoving;
	private boolean enabled, swapped;
	public static boolean function;

	private int boostTicks;
	private int dotAnimationCounter = 0;
	private long lastDotUpdate;
	public int lastSlot = -1, spoofSlot = -1;
	private int stopTime;
	private int rotateTick;
	private int motionDecayVal;

	private long fireballTime;
	private long MAX_EXPLOSION_DIST_SQ = 9;
	private long FIREBALL_TIMEOUT = 750L;

	public static boolean stopVelocity;
	public static boolean stopModules;
	public static boolean slotReset;
	public static int slotResetTicks;

	private int firstSlot = -1;

	private int color = new Color(0, 187, 255, 255).getRGB();

	// 新增进度条变量
	private float barWidth = 80;
	private float barHeight = 5;
	private float filledWidth;
	private float barX, barY;
	private final int borderColor = new Color(30, 30, 30, 200).getRGB();


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
	}

	public void onDisable() {
		disabled();
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

		if (notMoving) {
			motionDecayVal = 21;
		} else {
			motionDecayVal = (int) motionDecay.getInput();
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
		for(int i = 0; i <= 360; i += 30) {
			float rad = (float) Math.toRadians(i);
			float cx = x + radius + (float)Math.cos(rad) * radius;
			float cy = y + radius + (float)Math.sin(rad) * radius;
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
					}
					else {
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
		}

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

	private int getKeyCode(String keyName) {
		switch (keyName) {
			case "0": return 11;
			case "1": return 2;
			case "2": return 3;
			case "3": return 4;
			case "4": return 5;
			case "5": return 6;
			case "6": return 7;
			case "7": return 8;
			case "8": return 9;
			case "9": return 10;
			case "A": return 30;
			case "B": return 48;
			case "C": return 46;
			case "D": return 32;
			case "E": return 18;
			case "F": return 33;
			case "G": return 34;
			case "H": return 35;
			case "I": return 23;
			case "J": return 36;
			case "K": return 37;
			case "L": return 38;
			case "M": return 50;
			case "N": return 49;
			case "O": return 24;
			case "P": return 25;
			case "Q": return 16;
			case "R": return 19;
			case "S": return 31;
			case "T": return 20;
			case "U": return 22;
			case "V": return 47;
			case "W": return 17;
			case "X": return 45;
			case "Y": return 21;
			case "Z": return 44;
			case "BACK": return 14;
			case "CAPITAL": return 58;
			case "COMMA": return 51;
			case "DELETE": return 211;
			case "DOWN": return 208;
			case "END": return 207;
			case "ESCAPE": return 1;
			case "F1": return 59;
			case "F2": return 60;
			case "F3": return 61;
			case "F4": return 62;
			case "F5": return 63;
			case "F6": return 64;
			case "F7": return 65;
			case "HOME": return 199;
			case "INSERT": return 210;
			case "LBRACKET": return 26;
			case "LCONTROL": return 29;
			case "LMENU": return 56;
			case "LMETA": return 219;
			case "LSHIFT": return 42;
			case "MINUS": return 12;
			case "NUMPAD0": return 82;
			case "NUMPAD1": return 79;
			case "NUMPAD2": return 80;
			case "NUMPAD3": return 81;
			case "NUMPAD4": return 75;
			case "NUMPAD5": return 76;
			case "NUMPAD6": return 77;
			case "NUMPAD7": return 71;
			case "NUMPAD8": return 72;
			case "NUMPAD9": return 73;
			case "PERIOD": return 52;
			case "RETURN": return 28;
			case "RCONTROL": return 157;
			case "RSHIFT": return 54;
			case "RBRACKET": return 27;
			case "SEMICOLON": return 39;
			case "SLASH": return 53;
			case "SPACE": return 57;
			case "TAB": return 15;
			case "GRAVE": return 41;
			default: return -1;
		}
	}
}