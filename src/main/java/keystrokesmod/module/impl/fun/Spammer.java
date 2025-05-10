package keystrokesmod.module.impl.fun;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Spammer extends Module {
    private ButtonSetting spamBypass;

    private SliderSetting delay;

    public String message;
    private String bypass;
    private int delayTicks, half, uses;

    public Spammer() {
        super("Spammer", category.fun);
        //this.registerSetting(new DescriptionSetting("Usage:\" .spammer <message> \""));
        this.registerSetting(delay = new SliderSetting("Delay", "s", 3, 0.5, 30, 0.5));
        this.registerSetting(spamBypass = new ButtonSetting("Spam bypass", false));
    }

    @Override
    public void onEnable() {
        reset();
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {

        if (uses == 0) {
            bypass = "15L2QVJSHU95DPQZX3";
        }
        else if (uses == 1) {
            bypass = "LO4MDH1798JX5YQ935";
        }
        else if (uses == 2) {
            bypass = "987YQM6ND5AG01LFU3";
        }
        else if (uses == 3) {
            bypass = "83NMQJX5HQID83L32G";
        }
        else if (uses == 4) {
            bypass = "PL256GHBTNZQ38HJFM";
        }
        else if (uses == 5) {
            bypass = "LMP8B4GZ96BHMDU328";
        }
        else if (uses >= 6) {
            bypass = "OPF3HJ2K3J167YGUQW";
            uses = 0;
        }

        if (!spamBypass.isToggled()) {
            bypass = "";
        }

        if (!message.isEmpty()) {
            ++delayTicks;
            if (delayTicks >= 10) {
                half++;
                delayTicks = 0;
            }

            if (half >= delay.getInput()) {
                mc.thePlayer.sendChatMessage(message + " " + bypass);
                half = delayTicks = 0;
                uses++;
            }


        }
    }

    public void reset() {
        message = "";
        delayTicks = half = 0;
    }

}