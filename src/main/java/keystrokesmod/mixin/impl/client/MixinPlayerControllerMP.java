package keystrokesmod.mixin.impl.client;

import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.UseItemEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Shadow @Final private Minecraft mc;
    @Shadow private int currentPlayerItem;
    @Shadow @Final private NetHandlerPlayClient netClientHandler;

    @Inject(method = "sendUseItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    public void injectUseItemEvent(EntityPlayer p_sendUseItem_1_, World p_sendUseItem_2_, ItemStack p_sendUseItem_3_, CallbackInfoReturnable<Boolean> ci) {
        MinecraftForge.EVENT_BUS.post(new UseItemEvent(p_sendUseItem_3_));
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void syncCurrentPlayItem() {
        int i = this.mc.thePlayer.inventory.currentItem;
        if (i != this.currentPlayerItem) {
            this.currentPlayerItem = i;
            this.netClientHandler.addToSendQueue(new C09PacketHeldItemChange(this.currentPlayerItem));
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void injectAttackEntity(EntityPlayer playerIn, Entity targetEntity, CallbackInfo callbackInfo) {
        AttackEvent event = new AttackEvent(targetEntity, playerIn);

        MinecraftForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            callbackInfo.cancel();
        }
    }
}
