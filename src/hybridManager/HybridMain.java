package hybridManager;

import arc.*;
import arc.struct.Seq;
import hybridManager.ui.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;

import static mindustry.Vars.*;

public class HybridMain extends Mod{
    public Seq<Mods.LoadedMod> loadedMods = new Seq<>();
    public ManagerSaves manager = new ManagerSaves();
    public HybridDialog hybrid;

    public HybridMain(){
        Events.on(ClientLoadEvent.class, e -> {
            hybrid = new HybridDialog();
            ui.menufrag.addButton("@hybridManagerTile", Icon.planet, () -> hybrid.show());
        });
    }

}
