package hybridManager;

import arc.Core;

public enum HybridMode{
    planet,
    mod;

    public static final HybridMode[] all = values();

    public String localized(){
        return Core.bundle.get("hybridMode." + name());
    }
}
