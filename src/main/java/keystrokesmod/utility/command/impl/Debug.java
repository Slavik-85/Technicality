package keystrokesmod.utility.command.impl;

import keystrokesmod.Technicality;
import keystrokesmod.helper.DebugHelper;
import keystrokesmod.utility.command.Command;

public class Debug extends Command {
    public Debug() {
        super("debug");
    }

    @Override
    public void onExecute(String[] args) {
        if (args.length <= 1) {
            Technicality.debug = !Technicality.debug;
            chatWithPrefix("&7Debug " + (Technicality.debug ? "&aenabled" : "&cdisabled") + "&7.");
        }
        else if (args.length == 2) {
            if (args[1].equals("mixin")) {
                DebugHelper.MIXIN = !DebugHelper.MIXIN;
                chatWithPrefix("&dMixin &7debug " + (DebugHelper.MIXIN ? "&aenabled" : "&cdisabled") + "&7.");
            }
            else if (args[1].equals("bg") || args[1].equals("background")) {
                DebugHelper.BACKGROUND = !DebugHelper.BACKGROUND;
                chatWithPrefix("&6Background &7debug " + (DebugHelper.BACKGROUND ? "&aenabled" : "&cdisabled") + "&7.");
            }
            else {
                syntaxError();
            }
        }
        else {
            syntaxError();
        }
    }
}
