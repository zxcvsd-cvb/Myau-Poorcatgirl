package myau.module;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.modules.GuiModule;
import myau.module.modules.HUD;
import myau.util.ChatUtil;
import myau.util.SoundUtil;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class ModuleManager {
    private boolean sound = false;
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();

    /**
     * 模块名称（小写）到模块的惰性索引。模块仅在初始化阶段注册，
     * 之后保持不变，因此首次按名查找后即可复用该索引，避免每次线性扫描。
     */
    private Map<String, Module> nameIndex;

    public Module getModule(String string) {
        if (string == null) {
            return null;
        }
        Map<String, Module> index = this.nameIndex;
        if (index == null || index.size() != this.modules.size()) {
            index = buildNameIndex();
            this.nameIndex = index;
        }
        return index.get(string.toLowerCase(Locale.ROOT));
    }

    private Map<String, Module> buildNameIndex() {
        final Map<String, Module> index = new LinkedHashMap<>(this.modules.size());
        for (Module module : this.modules.values()) {
            index.put(module.getName().toLowerCase(Locale.ROOT), module);
        }
        return index;
    }

    public Module getModule(Class<?> clazz){
        return this.modules.get(clazz);
    }

    public void playSound() {
        this.sound = true;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if(module instanceof GuiModule){
                shouldNotify = false;
            }
            if (shouldNotify) {
                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                String message = String.format("%s%s: %s&r", Myau.clientName, module.getName(), status);
                ChatUtil.sendFormatted(message);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.sound) {
                this.sound = false;
                SoundUtil.playSound("random.click");
            }
        }
    }
}
