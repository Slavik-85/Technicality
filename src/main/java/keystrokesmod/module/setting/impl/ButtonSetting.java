package keystrokesmod.module.setting.impl;

import com.google.gson.JsonObject;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.Setting;

public class ButtonSetting extends Setting {
    private String name;
    private boolean isEnabled;
    public boolean isMethodButton;
    private Runnable method;
    public GroupSetting group;
    private boolean exclusive;
    private Module parentModule; // 新增父模块引用

    // 保留原始构造函数
    public ButtonSetting(String name, boolean isEnabled) {
        super(name);
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
        this.parentModule = null;
    }

    // 新增带parentModule的构造函数
    public ButtonSetting(String name, Module parentModule, boolean isEnabled) {
        super(name);
        this.name = name;
        this.parentModule = parentModule;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
    }

    // 保留其他原始构造函数
    public ButtonSetting(GroupSetting group, String name, boolean isEnabled) {
        super(name);
        this.group = group;
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
        this.parentModule = null;
    }

    // 新增带parentModule的分组构造函数
    public ButtonSetting(GroupSetting group, String name, Module parentModule, boolean isEnabled) {
        super(name);
        this.group = group;
        this.name = name;
        this.parentModule = parentModule;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
    }

    // 原始方法按钮构造函数
    public ButtonSetting(String name, Runnable method) {
        super(name);
        this.name = name;
        this.isEnabled = false;
        this.isMethodButton = true;
        this.method = method;
        this.parentModule = null;
    }

    // 新增：获取父模块方法
    public Module getParentModule() {
        return parentModule;
    }

    public void runMethod() {
        if (method != null) {
            method.run();
        }
    }

    public String getName() {
        return this.name;
    }

    public boolean isToggled() {
        return this.isEnabled;
    }

    public void toggle() {
        this.isEnabled = !this.isEnabled;
        if (exclusive && isEnabled) {
            handleExclusiveToggle(); // 调用互斥处理
        }
    }

    // 新增：互斥切换处理方法
    private void handleExclusiveToggle() {
        if (parentModule != null) {
            for (Setting setting : parentModule.getSettings()) {
                if (setting instanceof ButtonSetting && setting != this) {
                    ButtonSetting other = (ButtonSetting) setting;
                    if (other.exclusive) {
                        other.setToggled(false);
                    }
                }
            }
        }
    }


    public void enable() {
        this.isEnabled = true;
        if (exclusive) {
            handleExclusiveToggle();
        }
    }

    public void disable() {
        this.isEnabled = false;
    }

    public void setEnabled(boolean b) {
        this.isEnabled = b;
        if (b && exclusive) {
            handleExclusiveToggle();
        }
    }
    // 在ButtonSetting类中添加
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public void guiButtonToggled(ButtonSetting b) {
        if (b.exclusive && b.isToggled()) {
            // 关闭其他互斥按钮
            for (Setting setting : parentModule.getSettings()) {
                if (setting instanceof ButtonSetting && setting != b) {
                    ((ButtonSetting) setting).setToggled(false);
                }
            }
        }
    }

    @Override
    public void loadProfile(JsonObject data) {
        if (data.has(getName()) && data.get(getName()).isJsonPrimitive() && !this.isMethodButton) {
            boolean booleanValue = isEnabled;
            try {
                booleanValue = data.getAsJsonPrimitive(getName()).getAsBoolean();
            }
            catch (Exception e) {}
            setEnabled(booleanValue);
        }
    }

    // 修复setToggled方法
    public void setToggled(boolean b) {
        if (this.isEnabled != b) {
            this.isEnabled = b;
            if (b && exclusive) {
                handleExclusiveToggle();
            }
        }
    }
}
