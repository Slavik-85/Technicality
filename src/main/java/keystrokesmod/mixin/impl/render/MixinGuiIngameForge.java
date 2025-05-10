package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraftforge.client.GuiIngameForge", remap = false)
public class MixinGuiIngameForge {

    @Redirect(method = "renderGameOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
    private boolean redirectRenderGameOverlay(EntityPlayerSP gui, Potion potion) {
        if (ModuleManager.antiDebuff != null && ModuleManager.antiDebuff.canRemoveNausea(potion)) {
            return false;
        }
        //12312312
        return Minecraft.getMinecraft().thePlayer.isPotionActive(potion);
    }

}
