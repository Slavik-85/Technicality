package keystrokesmod.mixin.impl.render;

import keystrokesmod.Technicality;
import keystrokesmod.event.KeyPressEvent;
import keystrokesmod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen
{
    @Shadow
    public Minecraft mc;

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void messageSend(String msg, boolean addToChat, final CallbackInfo callbackInfo) {
        if (msg.startsWith(".") && addToChat && ModuleManager.canExecuteChatCommand()) {
            this.mc.ingameGUI.getChatGUI().addToSentMessages(msg);

            Technicality.commandManager.executeCommand(msg);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    private void injectHandleKeyboardInput(CallbackInfo callbackInfo) {
        KeyPressEvent event = new KeyPressEvent(Keyboard.getEventCharacter(), Keyboard.getEventKey());

        MinecraftForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            callbackInfo.cancel();
        }
    }

}