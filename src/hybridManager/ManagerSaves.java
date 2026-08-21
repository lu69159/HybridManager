package hybridManager;

import arc.Core;
import arc.Events;
import arc.files.*;
import arc.struct.*;
import arc.util.serialization.*;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.*;
import mindustry.type.Planet;
import java.io.*;
import java.nio.charset.StandardCharsets;


public class ManagerSaves {
    private static final String saveJsonName = "hybridData.json";
    public ObjectMap<Planet, Seq<Planet>> loadedModMap = new ObjectMap<>();

    public ManagerSaves(){
        Events.on(EventType.ClientLoadEvent.class, e -> {
            load();
        });
        Events.on(EventType.DisposeEvent.class, e -> {
            save();
        });
    }

    public void load(){
        Fi saveJson = Core.settings.getDataDirectory().child(saveJsonName);
        if(saveJson.exists()){
            try{
                Jval roots = Jval.read(new InputStreamReader(new FileInputStream(Core.settings.getDataDirectory().path() + "/" + saveJsonName), StandardCharsets.UTF_8));

                for(Jval p : roots.asArray()){
                    Planet planet = Vars.content.planet(p.getString("planet"));
                    Seq<Planet> planets = new Seq<>();
                    for(Jval loadedPlanet : p.get("loadedPlanets").asArray()){
                        planets.add(Vars.content.planet(loadedPlanet.getString("planetName")));
                    }

                    loadedModMap.put(planet, planets);
                }
            }catch (Exception e){
                Vars.content.planets().each(planet -> {
                    loadedModMap.put(planet, new Seq<>());
                });
            }
        }else{
            Vars.content.planets().each(planet -> {
                loadedModMap.put(planet, new Seq<>());
            });
        }
    }

    public void save(){
        Jval roots = Jval.newArray();
        loadedModMap.each(((planet, loadedPlanets) -> {
            Jval planetLoadedPlanets = Jval.newArray();
            loadedPlanets.each(p -> planetLoadedPlanets.add(Jval.newObject().put("planetName", p.name)));
            roots.add(Jval.newObject().put("planet", planet.name).put("loadedPlanets", planetLoadedPlanets));
        }));

        try(Writer w = new OutputStreamWriter(new FileOutputStream(Core.settings.getDataDirectory().path() + "/" + saveJsonName), StandardCharsets.UTF_8)){
            roots.writeTo(w);
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }
}
