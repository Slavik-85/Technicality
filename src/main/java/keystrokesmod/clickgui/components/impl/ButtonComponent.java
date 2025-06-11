package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Technicality;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.profile.ProfileModule;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class ButtonComponent extends Component {
    private final int enabledColor = (new Color(20, 255, 0)).getRGB();

    private Module mod;
    public ButtonSetting buttonSetting;
    private ModuleComponent moduleComponent;

    public float o;
    public float x;
    private float y;
    public float xOffset;
    public boolean renderLine;

    public ButtonComponent(Module mod, ButtonSetting op, ModuleComponent b, float o) {
        this.mod = mod;
        this.buttonSetting = op;
        this.moduleComponent = b;
        this.x = b.categoryComponent.getX() + b.categoryComponent.getWidth();
        this.y = b.categoryComponent.getY() + b.yPos;
        this.o = o;
    }

    public void render() {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        Minecraft.getMinecraft().fontRendererObj.drawString((this.buttonSetting.isMethodButton ? "[=]  " : (this.buttonSetting.isToggled() ? "[+]  " : "[-]  ")) + this.buttonSetting.getName(), (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + 4) * 2), this.buttonSetting.isToggled() ? this.enabledColor : -1, false);
        GL11.glScaled(1, 1, 1);
        if (renderLine) {
            //RenderUtils.drawRectangleGL((float) ((this.p.categoryComponent.getX() + 4) * 2), (float) ((this.p.categoryComponent.getY() + this.o) * 2), (float) ((this.p.categoryComponent.getX() + 4) * 2) + 1, (float) ((this.p.categoryComponent.getY() + this.o + 4) * 2) + 16, new Color(192, 192, 192).getRGB());
        }
        GL11.glPopMatrix();
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    public void drawScreen(int x, int y) {
        this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
        this.x = this.moduleComponent.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int b) {
        if (this.i(x, y) && b == 0 && this.moduleComponent.isOpened && this.moduleComponent.isVisible(this)) {
            if (this.buttonSetting.isMethodButton) {
                this.buttonSetting.runMethod();
                return false;
            }
            this.buttonSetting.toggle();
            this.mod.guiButtonToggled(this.buttonSetting);
            if (Technicality.currentProfile != null) {
                ((ProfileModule) Technicality.currentProfile.getModule()).saved = false;
            }
        }
        return false;
    }

    public boolean i(int x, int y) {
        return x > this.x && x < this.x + this.moduleComponent.categoryComponent.getWidth() && y > this.y && y < this.y + 11;
    }
}
