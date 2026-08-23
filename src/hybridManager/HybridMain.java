package hybridManager;

import arc.*;
import hybridManager.ui.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;

import static mindustry.Vars.*;

public class HybridMain extends Mod{
    public static ManagerSave manager = new ManagerSave();
    public HybridDialog hybrid;

    public HybridMain(){
        Events.on(ClientLoadEvent.class, e -> {
            hybrid = new HybridDialog();
            ui.menufrag.addButton("@hybridManagerTitle", Icon.planet, () -> hybrid.show());
        });
    }
}
