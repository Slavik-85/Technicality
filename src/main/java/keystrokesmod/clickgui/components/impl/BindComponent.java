package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Technicality;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.profile.ProfileModule;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class BindComponent extends Component {
    public boolean isBinding;
    public ModuleComponent moduleComponent;
    public float o;
    public float x;
    private float y;
    public KeySetting keySetting;
    public float xOffset;

    public BindComponent(ModuleComponent moduleComponent, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.o = o;
    }

    public BindComponent(ModuleComponent moduleComponent, KeySetting keySetting, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.keySetting = keySetting;
        this.o = o;
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    public void render() {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        if (keySetting == null) {
            this.drawString(!this.moduleComponent.mod.canBeEnabled() && this.moduleComponent.mod.script == null ? "Module cannot be bound." : this.isBinding ? "Press a key..." : "Current bind: '" + getKeyAsStr(false) + "'");
        }
        else {
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(this.isBinding ? "Press a key..." : this.keySetting.getName() + ": '" + getKeyAsStr(true) + "'", (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3 : 4)) * 2), -1);
        }
        GL11.glPopMatrix();
    }

    public void drawScreen(int x, int y) {
        this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
        this.x = this.moduleComponent.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int button) {
        if (this.overSetting(x, y) && this.moduleComponent.isOpened && this.moduleComponent.mod.canBeEnabled() && this.moduleComponent.isVisible(this)) {
            if (button == 0) {
                this.isBinding = !this.isBinding;
            }
            else if (button == 1 && this.moduleComponent.mod.moduleCategory() != Module.category.profiles && this.keySetting == null) {
                this.moduleComponent.mod.setHidden(!this.moduleComponent.mod.isHidden());
                if (Technicality.currentProfile != null) {
                    ((ProfileModule) Technicality.currentProfile.getModule()).saved = false;
                }
            }
            else if (button > 1) {
                if (this.isBinding) {
                    if (this.keySetting != null) {
                        this.keySetting.setKey(button + 1000);
                    }
                    else {
                        this.moduleComponent.mod.setBind(button + 1000);
                    }
                    if (Technicality.currentProfile != null) {
                        ((ProfileModule) Technicality.currentProfile.getModule()).saved = false;
                    }
                    this.isBinding = false;
                }
            }
        }
        return false;
    }

    public void onScroll(int scroll) {
        if (this.isBinding && scroll != 0) {
            if (this.keySetting != null) {
                this.keySetting.setKey(scroll > 0 ? 1069 : 1070); // 1069 for up, 1070 for down
            }
            else {
                this.moduleComponent.mod.setBind(scroll > 0 ? 1069 : 1070); // might cause issues if your mouse has more than 69 buttons for some reason???
            }
            if (Technicality.currentProfile != null) {
                ((ProfileModule) Technicality.currentProfile.getModule()).saved = false;
            }
            this.isBinding = false;
        }
    }

    public void keyTyped(char t, int keybind) {
        if (this.isBinding) {
            if (keybind == Keyboard.KEY_0 || keybind == Keyboard.KEY_ESCAPE) {
                if (this.moduleComponent.mod instanceof Gui) {
                    this.moduleComponent.mod.setBind(54);
                }
                else {
                    if (this.keySetting != null) {
                        this.keySetting.setKey(0);
                    }
                    else {
                        this.moduleComponent.mod.setBind(0);
                    }
                }
                if (Technicality.currentProfile != null) {
                    ((ProfileModule) Technicality.currentProfile.getModule()).saved = false;
                }
            }
            else {
                if (Technicality.currentProfile != null) {
                    ((ProfileModule) Technicality.currentProfile.getModule()).saved = false;
                }
                if (this.keySetting != null) {
                    this.keySetting.setKey(keybind);
                }
                else {
                    this.moduleComponent.mod.setBind(keybind);
                }
            }

            this.isBinding = false;
        }
    }

    public boolean overSetting(int x, int y) {
        return x > this.x && x < this.x + this.moduleComponent.categoryComponent.getWidth() && y > this.y - 1 && y < this.y + 12;
    }

    public String getKeyAsStr(boolean isKey) {
        int key = isKey ? this.keySetting.getKey() : this.moduleComponent.mod.getKeycode();
        return (key >= 1000 ? ((key == 1069 || key == 1070) ? getScroll(key) : "M" + (key - 1000)) : Keyboard.getKeyName(key));
    }

    public String getScroll(int key) {
        if (key == 1069) {
            return "MScrollUp";
        }
        else if (key == 1070) {
            return "MScrollDown";
        }
        return "&cERROR";
    }

    public int getHeight() {
        if (this.keySetting != null) {
            return 0;
        }
        return 16;
    }

    private void drawString(String s) {
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(s, (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3 : 4)) * 2), -1);
    }

    public void onGuiClosed() {
        this.isBinding = false;
    }
}