package hybridManager;

import arc.Core;
import arc.Events;
import arc.files.*;
import arc.func.Boolf;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.dialogs.DatabaseDialog;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;

//1.模组内容 2.原版星球内容
//区分 按模组筛选/按星球筛选

public class ManagerSave {
    public Fi saveFile = Vars.modDirectory.child("hybridManager");
    public Seq<PlanetHybridData> hybridData = new Seq<>();
    public HybridMode mode;

    private ObjectMap<Planet,Seq<UnlockableContent>> planetContentMaps = new ObjectMap<>();
    private static final String saveJsonName = "hybridData.json";
    Seq<Jval> unloadedMainPlanets = new Seq<>();

    public ManagerSave(){
        Events.on(EventType.ClientLoadEvent.class, e -> {
            if(!saveFile.exists()){
                saveFile.mkdirs();
            }

            for(var p : Vars.content.planets()){
                Seq<UnlockableContent> planetContents = new Seq<>();
                for(var seq : Vars.content.getContentMap()){
                    for(var thing : seq){
                        if(thing instanceof UnlockableContent u && (u.shownPlanets.contains(p) || u.databaseTabs.contains(p))){
                            if(p == Planets.serpulo || p == Planets.erekir){
                                if(!u.isVanilla()) continue;
                            }
                            planetContents.add(u);
                        }
                    }
                }
                planetContentMaps.put(p, planetContents);
            }

            load();
            Log.info("Loaded Hybrid Data");
        });

        Events.on(EventType.SectorLaunchEvent.class, (e) -> { overrideRule(); });
        Events.on(EventType.SaveLoadEvent.class, (e) -> { overrideRule(); });
    }

    public void load(){
        Fi saveJson = saveFile.child(saveJsonName);
        if(saveJson.exists()){
            try{
                Jval json = Jval.read(new InputStreamReader(new FileInputStream(saveFile.path() + "/" + saveJsonName), StandardCharsets.UTF_8));
                mode = json.getString("hybridMode", "planet").equals("planet") ? HybridMode.planet : HybridMode.mod;
                Jval roots = json.get("roots");

                for(Jval p : roots.asArray()){
                    Planet planet = Vars.content.planet(p.getString("planet"));
                    if(planet == null){
                        unloadedMainPlanets.add(p);
                        continue;
                    }

                    Seq<Mods.LoadedMod> mods = new Seq<>();
                    Seq<Jval> unloadedMods = new Seq<>();
                    for(Jval loadedMod : p.get("loadedMods").asArray()){
                        String mName = loadedMod.getString("name");
                        @Nullable Mods.LoadedMod m = Vars.mods.getMod(mName);
                        if(m != null && m.enabled()){
                            mods.add(m);
                        }else{
                            unloadedMods.add(Jval.newObject().put("name", mName));
                        }
                    }

                    Seq<Planet> planets = new Seq<>();
                    Seq<Jval> unloadedPlanets = new Seq<>();
                    for(Jval loadedPlanet : p.get("loadedPlanets").asArray()){
                        String pName = loadedPlanet.getString("name");
                        @Nullable Planet pl = Vars.content.planet(pName);
                        if(pl != null){
                            planets.add(pl);
                        }else{
                            unloadedPlanets.add(Jval.newObject().put("name", pName));
                        }
                    }

                    hybridData.add(new PlanetHybridData(planet, mods, unloadedMods, planets, unloadedPlanets, p.getBool("addS", true), p.getBool("addE", false)));
                }
            }catch (Exception e){
                Vars.content.planets().each(this::init);
            }
        }else{
            Vars.content.planets().each(this::init);
        }
        if(mode == null) mode = HybridMode.planet;

        reloadUnlockableContents(mode);
    }

    public void save(){
        Jval json = Jval.newObject().put("hybridMode", mode.name());
        Jval roots = Jval.newArray();

        hybridData.each(data -> {
            Jval planetLoadedMods = Jval.newArray();
            data.loadedMods.each(m -> planetLoadedMods.add(Jval.newObject().put("name", m.name)));
            data.unloadedMods.each(planetLoadedMods::add);

            Jval planetLoadedPlanets = Jval.newArray();
            data.loadedPlanets.each(p -> planetLoadedPlanets.add(Jval.newObject().put("name", p.name)));
            data.unloadedPlanets.each(planetLoadedPlanets::add);

            roots.add(Jval.newObject().put("planet", data.planet.name).put("loadedMods", planetLoadedMods).put("loadedPlanets", planetLoadedPlanets).put("addS", data.loadSerpulo).put("addE", data.loadErekir));
        });
        unloadedMainPlanets.each(roots::add);

        json.put("roots", roots);

        try(Writer w = new OutputStreamWriter(new FileOutputStream(saveFile.path() + "/" + saveJsonName), StandardCharsets.UTF_8)){
            json.writeTo(w);
        }catch(IOException e){
            Vars.ui.showException(e);
        }
    }

    public void init(Planet planet){
        if(planet.accessible){
            if(planet == Planets.serpulo){
                hybridData.add(new PlanetHybridData(planet, new Seq<>(), Seq.with(Planets.serpulo), true, false));
            }else if(planet == Planets.erekir){
                hybridData.add(new PlanetHybridData(planet, new Seq<>(), Seq.with(Planets.erekir), false, true));
            }else if(!planet.isVanilla()){
                hybridData.add(new PlanetHybridData(planet, Seq.with(planet.minfo.mod), Seq.with(planet), false, false));
            }else{
                hybridData.add(new PlanetHybridData(planet, new Seq<>(), Seq.with(planet), false, false));
            }
        }
    }

    public void reset(){
        Fi saveJson = saveFile.child(saveJsonName);
        if(saveJson.exists()){
            saveJson.delete();
        }
        hybridData.clear();

        Vars.content.planets().each(this::init);
        mode = HybridMode.planet;
        reloadUnlockableContents(mode);
    }

    public void reloadUnlockableContents(HybridMode mode){
        if(mode == HybridMode.mod){
            hybridData.each(data -> {
                ChangeDatabase(data.planet, u -> {
                    boolean includeBase = (data.loadSerpulo && planetContentMaps.get(Planets.serpulo).contains(u)) || (data.loadErekir && planetContentMaps.get(Planets.erekir).contains(u));
                    boolean includeMod = !u.isVanilla() && data.loadedMods.contains(u.minfo.mod);
                    return includeBase || includeMod;
                });
            });
        }else if(mode == HybridMode.planet){
            hybridData.each(data -> {
                ChangeDatabase(data.planet, u -> {
                    for(var p : data.loadedPlanets){
                        if(planetContentMaps.get(p) != null && planetContentMaps.get(p).contains(u)){
                            return true;
                        }
                    }
                    return false;
                });
            });
        }
        try{
            Field f = DatabaseDialog.class.getDeclaredField("allTabs");
            f.setAccessible(true);
            f.set(Vars.ui.database, null);
        }catch(ReflectiveOperationException e){
            Vars.ui.showException(e);
        }
    }

    private void ChangeDatabase(Planet planet, Boolf<UnlockableContent> pred){
        for(var seq : Vars.content.getContentMap()){
            for(var thing : seq){
                if(thing instanceof UnlockableContent u && !u.hideDatabase){
                    if(pred.get(u)){
                        if(!u.databaseTabs.contains(planet)) u.databaseTabs.add(planet);
                        if(!u.shownPlanets.contains(planet)) u.shownPlanets.add(planet);
                    }else{
                        if(u.databaseTabs.contains(planet)) u.databaseTabs.remove(planet);
                        if(u.shownPlanets.contains(planet)) u.shownPlanets.remove(planet);
                    }
                    if(u.shownPlanets.isEmpty()) u.shownPlanets.add(Planets.sun);
                    if(u.databaseTabs.isEmpty()) u.databaseTabs.add(Planets.sun);
                    if(u instanceof UnitType unit) unit.envDisabled = 0;
                }
            }
        }
    }

    private void overrideRule(){
        if(Vars.state.isCampaign()){
            if(Vars.state.rules.bannedBlocks.size > 0){
                var blocks = Vars.state.rules.bannedBlocks;
                if(Vars.state.rules.blockWhitelist){
                    for(var b : Vars.content.blocks()){
                        if(b.shownPlanets.contains(Vars.state.getPlanet())){
                            blocks.add(b);
                        }
                    }
                }else{
                    for(var b : blocks){
                        if(b.shownPlanets.contains(Vars.state.getPlanet())){
                            blocks.remove(b);
                        }
                    }
                }
            }
            if(Vars.state.rules.bannedUnits.size > 0){
                var units = Vars.state.rules.bannedUnits;
                if(Vars.state.rules.blockWhitelist){
                    for(var u : Vars.content.units()){
                        if(u.shownPlanets.contains(Vars.state.getPlanet())){
                            units.add(u);
                        }
                    }
                }else{
                    for(var u : units){
                        if(u.shownPlanets.contains(Vars.state.getPlanet())){
                            units.remove(u);
                        }
                    }
                }
            }
        }
    }

    public static class PlanetHybridData{
        //MOD混合
        public Planet planet;
        public Seq<Mods.LoadedMod> loadedMods;
        public boolean loadSerpulo;
        public boolean loadErekir;

        public Seq<Jval> unloadedMods; //在混合列表中但是未启用的模组内容

        //星球混合
        public Seq<Planet> loadedPlanets;
        public Seq<Jval> unloadedPlanets; //在混合列表中但是未启用的星球内容

        public PlanetHybridData(Planet planet, Seq<Mods.LoadedMod> loadedMods, Seq<Planet> loadedPlanets, boolean s, boolean e){
            this(planet, loadedMods, new Seq<>(), loadedPlanets, new Seq<>(), s, e);
        }
        public PlanetHybridData(Planet planet, Seq<Mods.LoadedMod> loadedMods, Seq<Jval> unloadedMods, Seq<Planet> loadedPlanets, Seq<Jval> unloadedPlanets, boolean s, boolean e){
            this.planet = planet;
            this.loadedMods = loadedMods;
            this.unloadedMods = unloadedMods;
            this.loadedPlanets = loadedPlanets;
            this.unloadedPlanets = unloadedPlanets;

            this.loadSerpulo = s;
            this.loadErekir = e;
        }
    }
}
