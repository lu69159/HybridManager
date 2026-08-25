package hybridManager.ui;

import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.mod.Mods;
import mindustry.type.Planet;
import mindustry.ui.dialogs.BaseDialog;

import static hybridManager.HybridMain.manager;
import static mindustry.Vars.ui;

public class HybridDialogNew extends BaseDialog{ //TODO: better ui(哇我实在不擅长搓UI)
    Planet choosePlanet;
    Seq<Mods.LoadedMod> allMods = new Seq<>(), loadedMods = new Seq<>(), unloadedMods = new Seq<>();
    Seq<Planet> loadedPlanets = new Seq<>(), unloadedPlanets = new Seq<>();
    boolean loadSerpulo, loadErekir;

    public HybridDialogNew(String title) {
        super(title);
        addCloseButton();

        buttons.button("@settings.reset", Icon.trash, () -> ui.showConfirm("@confirm", "@hybrid.reset.confirm", () -> {
            manager.reset();
            setup();
        })).size(210f, 64f);

        shown(() -> {
            setup();
        });

        onResize(this::setup);

        hidden(() -> {
            manager.save();
        });
    }

    void setup(){}
}
