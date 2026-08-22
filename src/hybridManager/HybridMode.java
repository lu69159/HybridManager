package hybridManager;

import arc.Core;

public enum HybridMode{
    mod,
    planet;

    public static final HybridMode[] all = values();

    public String localized(){
        return Core.bundle.get("hybridMode." + name());
    }
}
