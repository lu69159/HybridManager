package hybridManager;

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
import mindustry.type.Planet;
import mindustry.ui.dialogs.DatabaseDialog;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;

//1.模组内容 2.原版星球内容
//区分 按模组筛选/按星球筛选

public class ManagerSave {
    public Fi saveFile = Vars.modDirectory.child("hybridManager");
    public Seq<PlanetHybridData> hybridData = new Seq<>();

    private static final String saveJsonName = "hybridData.json";
    Seq<Jval> unloadedMainPlanets = new Seq<>();

    public ManagerSave(){
        Events.on(EventType.ClientLoadEvent.class, e -> {
            if(!saveFile.exists()){
                saveFile.mkdirs();
            }
            load();
        });
    }

    public void load(){
        Fi saveJson = saveFile.child(saveJsonName);
        if(saveJson.exists()){
            try{
                Jval roots = Jval.read(new InputStreamReader(new FileInputStream(saveFile.path() + "/" + saveJsonName), StandardCharsets.UTF_8));

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
                    if(!planet.isVanilla() && !mods.contains(planet.minfo.mod)) mods.add(planet.minfo.mod);

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
            save();
        }

        reloadUnlockableContents();
    }

    public void save(){
        Jval roots = Jval.newArray();
        hybridData.each(data -> {
            Jval planetLoadedMods = Jval.newArray();
            data.loadedMods.each(m -> planetLoadedMods.add(Jval.newObject().put("name", m.name)));
            data.unloadedMods.each(planetLoadedMods::add);

            Jval planetLoadedPlanets = Jval.newArray();
            data.loadedPlanets.each(p -> planetLoadedPlanets.add(Jval.newObject().put("name", p.name)));
            data.unloadedPlanets.each(planetLoadedPlanets::add);

            roots.add(Jval.newObject().put("planet", data.planet.name).put("loadedMods", planetLoadedMods).put("loadedPlanets", planetLoadedPlanets).put("addS", data.addSerpulo).put("addE", data.addErekir));
        });
        unloadedMainPlanets.each(roots::add);

        try(Writer w = new OutputStreamWriter(new FileOutputStream(saveFile.path() + "/" + saveJsonName), StandardCharsets.UTF_8)){
            roots.writeTo(w);
        }catch(IOException e){
            Vars.ui.showException(e);
        }
    }

    void init(Planet planet){
        if(planet.accessible){
            if(planet == Planets.serpulo){
                hybridData.add(new PlanetHybridData(planet, new Seq<>(), new Seq<>(), Seq.with(Planets.serpulo), new Seq<>(), true, false));
            }else if(planet == Planets.erekir){
                hybridData.add(new PlanetHybridData(planet, new Seq<>(), new Seq<>(), Seq.with(Planets.erekir), new Seq<>(), false, true));
            }else{
                hybridData.add(new PlanetHybridData(planet, new Seq<>(), new Seq<>(), new Seq<>(), new Seq<>(), false, false));
            }
        }
    }

    public void reloadUnlockableContents(){
        hybridData.each(data -> {
            ChangeDatabase(data.planet, u -> !u.isVanilla() && data.loadedMods.contains(u.minfo.mod));
        });
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

                }
            }
        }
    }

    public static class PlanetHybridData{
        //MOD混合
        public Planet planet;
        public Seq<Mods.LoadedMod> loadedMods;
        public boolean addSerpulo;
        public boolean addErekir;

        public Seq<Jval> unloadedMods; //在混合列表中但是未启用的模组内容

        //星球混合
        public Seq<Planet> loadedPlanets;
        public Seq<Jval> unloadedPlanets; //在混合列表中但是未启用的星球内容

        public PlanetHybridData(Planet planet, Seq<Mods.LoadedMod> loadedMods, Seq<Jval> unloadedMods, Seq<Planet> loadedPlanets, Seq<Jval> unloadedPlanets, boolean s, boolean e){
            this.planet = planet;
            this.loadedMods = loadedMods;
            this.unloadedMods = unloadedMods;
            this.loadedPlanets = loadedPlanets;
            this.unloadedPlanets = unloadedPlanets;

            this.addSerpulo = s;
            this.addErekir = e;
        }

        public void reset(){
            loadedMods.clear();
            unloadedMods.clear();
            loadedPlanets.clear();
            unloadedPlanets.clear();
            if(planet != Planets.serpulo) addSerpulo = false;
            if(planet != Planets.erekir) addErekir = false;
        }
    }
}
