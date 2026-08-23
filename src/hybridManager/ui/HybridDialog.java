package hybridManager.ui;

import arc.*;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.Strings;
import hybridManager.HybridMode;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;
import static hybridManager.HybridMain.*;

public class HybridDialog extends BaseDialog {
    Planet choosePlanet;
    Seq<Mods.LoadedMod> allMods = new Seq<>(), loadedMods = new Seq<>(), unloadedMods = new Seq<>();
    Seq<Planet> loadedPlanets = new Seq<>(), unloadedPlanets = new Seq<>();
    boolean loadSerpulo, loadErekir;

    public HybridDialog() {
        super("@hybridManagerTitle");
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

    void setup(){
        float w = Math.max(Core.graphics.getWidth() / 8f, 50f);
        float h = Math.max(Core.graphics.getHeight() / 8f, 40f);

        allMods.clear();
        mods.getMods().each(mod -> {
            if(mod.enabled() && !mod.meta.hidden){
                allMods.add(mod);
            }
        });

        if(choosePlanet == null) choosePlanet = Planets.erekir;

        loadedMods.clear();
        unloadedMods.clear();
        loadedPlanets.clear();
        unloadedPlanets.clear();

        var hybridData = manager.hybridData.find(d -> d.planet == choosePlanet);
        if(hybridData == null){
            manager.init(choosePlanet);
            hybridData = manager.hybridData.find(d -> d.planet == choosePlanet);
        }

        hybridManager.ManagerSave.PlanetHybridData finalHybridData = hybridData;
        for(var mod : allMods){
            if(finalHybridData.loadedMods.contains(mod)){
                loadedMods.add(mod);
            }else{
                unloadedMods.add(mod);
            }
        };
        for(var planet : content.planets()){
            if(planet.accessible){
                if(finalHybridData.loadedPlanets.contains(planet)){
                    loadedPlanets.add(planet);
                }else{
                    unloadedPlanets.add(planet);
                }
            }
        }
        loadSerpulo = hybridData.loadSerpulo;
        loadErekir = hybridData.loadErekir;

        cont.clear();

        cont.table(left -> {
            left.top();
            left.background(Styles.grayPanel);

            left.table(t -> {
                t.add(new Label("@hybridMode")).height(50f).row();
                var group = new ButtonGroup<>();
                for(var hm : HybridMode.all){
                    t.button(hm.localized(), Styles.flatTogglet, () -> {
                        manager.mode = hm;
                        manager.reloadUnlockableContents(hm);
                        setup();
                    }).height(50f).growX().group(group).checked(manager.mode == hm).row();
                }
            }).height(150f).top().growX().row();

            left.pane(side -> {
                side.add(Core.bundle.get("hybrid.planets")).row();
                side.image().color(Pal.accent).height(3.0F).left().fillX().padBottom(5.0F).row();

                content.planets().sort().each(planet -> {
                    if(planet.accessible){
                        side.button("[#" + planet.iconColor + "]" + Iconc.planet + "[#FFFFFF]" + planet.localizedName, Styles.clearTogglet, () -> {
                            choosePlanet = planet;
                            setup();
                        }).checked(b -> choosePlanet == planet).height(80f).growX().row();
                    }
                });
            }).left().top().grow();
        }).width(w).growY();

        cont.table(right -> {
                right.table(top -> {
                    showHybridState(top, manager.mode, h);
                }).height(h*2).growX().top().row();

                right.add(Core.bundle.get("planetDatabase")).row();
                right.image().color(Pal.accent).height(3.0F).left().fillX().padBottom(5.0F).row();

                right.pane(data -> {
                    showPlanetDataBase(choosePlanet, data);
                }).marginTop(10f).grow();
        }).grow();
    }

    void showPlanetDataBase(Planet planet, Table inner){
        inner.left().top();

        OrderedMap<String, OrderedMap<String, Seq<UnlockableContent>>> cats = new OrderedMap<>();

        for(Seq<Content> list : content.getContentMap()){
            for(Content c : list){
                if(c instanceof UnlockableContent u){
                    String cat = u.databaseCategory == null ? u.getContentType().name() : u.databaseCategory;
                    String tag = u.databaseTag == null ? "default" : u.databaseTag;

                    if(u.isHidden() || u.hideDatabase || !(u.allDatabaseTabs || u.databaseTabs.contains(planet))) continue;

                    var m = cats.get(cat, new OrderedMap<>());
                    var arr = m.get(tag, new Seq<>());
                    arr.add(u);
                    m.put(tag, arr);
                    cats.put(cat, m);
                }
            }
        }
        if(cats.isEmpty()){
            inner.add("@none.found");
            return;
        }

        int cols = (int) Mathf.clamp((Core.graphics.getWidth() - Scl.scl(30)) / Scl.scl(32 + 12), 1, 22);

        inner.pane(p -> {
            for(int ci = 0; ci < cats.size; ci++){
                String catName = cats.orderedKeys().get(ci);
                OrderedMap<String, Seq<UnlockableContent>> m = cats.get(catName);
                if(m.isEmpty()) continue;

                p.add("@database-category." + catName).growX().left().color(Pal.accent);
                p.row();
                p.image().pad(5).padLeft(0).padRight(0).height(3).color(Pal.accent).growX();
                p.row();

                p.table(sub -> {
                    for(int ti = 0; ti < m.size; ti++){
                        String tagName = m.orderedKeys().get(ti);
                        Seq<UnlockableContent> arr = m.get(tagName);
                        if(arr.isEmpty()) continue;

                        if(!"default".equals(tagName)){
                            sub.table(tg -> {
                                tg.add("@database-tag." + tagName).left().color(Pal.gray);
                                tg.image().growX().pad(5).height(3).color(Pal.gray);
                            }).pad(4, 8, 4, 8).growX().row();
                        }

                        sub.table(list -> {
                            list.left();
                            int count = 0;
                            for(UnlockableContent u : arr){
                                Image image = list.add(new Image(u.uiIcon)).size(Mathf.clamp((Core.graphics.getWidth() - Math.max(Core.graphics.getWidth() / 8f, 200f)) / 40f, 32f, Core.app.isMobile() ? 40f : 80f)).pad(3).get();

                                image.clicked(() -> ui.content.show(u));          // Vars.ui
                                image.addListener(new Tooltip(tip -> tip.background(Tex.button).add(u.localizedName)));

                                if((++count) % cols == 0) list.row();
                            }
                            // 补齐最后一行剩下的空格（可选，对齐原版 DatabaseDialog.java:221）
                            for(int k = 0; k < cols - count; k++){
                                Image filler = new Image();
                                filler.setColor(Color.clear);
                                list.add(filler).size(32).pad(3);
                            }
                        }).growX().left().padBottom(10).row();
                    }
                }).width(cols * Scl.scl(38f)).growX().left().padBottom(10);   // 给分组内表格一个稳定的列宽

                p.row();
            }
        }).grow().scrollX(false);
    }

    void showHybridState(Table top, HybridMode mode, float h){
        top.top();
        top.pane(t -> {
            showHybridTable(manager.mode, true, t, Core.bundle.get("hybrid.loaded"), h/2);
        }).height(h).growX().padBottom(8f);
        top.row();

        top.pane(t -> {
            showHybridTable(manager.mode, false, t, Core.bundle.get("hybrid.unloaded"), h/2);
        }).height(h).growX().padBottom(8f);

    }

    void showHybridTable(HybridMode mode, boolean isLoaded, Table table, String tableTitle, float h){
        table.left();
        table.background(Styles.grayPanelDark);
        table.add(tableTitle).padLeft(4f).padRight(4f);

        if(mode == HybridMode.mod){
            var seqs = isLoaded ? loadedMods : unloadedMods;

            if(showVanilla(table, h, isLoaded) & showMods(seqs, table, tableTitle, h)){
                table.add("@empty");
            }
        }else if(mode == HybridMode.planet){
            var seqs = isLoaded ? loadedPlanets : unloadedPlanets;

            if(showPlanets(seqs, table, h)){
                table.add("@empty");
            }
        }
    }

    boolean showMods(Seq<Mods.LoadedMod> seqs, Table table, String tableTitle, float h){
        if(seqs.size > 0){
            for(var lm : seqs){
                table.button(t -> {
                    t.defaults().left().top();
                    t.margin(12f);
                    t.table(title1 -> {
                        title1.left();
                        title1.add(new BorderImage(){{
                            if(lm.iconTexture != null){
                                setDrawable(new TextureRegion(lm.iconTexture));
                            }else{
                                setDrawable(Tex.nomap);
                            }
                            border(Pal.accent);
                        }}).size(h - 8f).padTop(-8f).padLeft(-8f).padRight(8f);
                        title1.table(text -> {
                            String shortDesc = lm.meta.shortDescription();
                            text.add("[accent]" + Strings.stripColors(lm.meta.displayName) + "\n" + (shortDesc.length() > 0 ? "[lightgray]" + shortDesc + "\n" : "")).wrap().top().width(300f).growX().left();
                        }).top().growX();

                        title1.add().growX();
                    });
                }, Styles.grayt, () -> {
                    if(choosePlanet != null){
                        var planetData = manager.hybridData.find(data -> data.planet == choosePlanet);
                        var tmps = planetData.loadedMods;
                        if(tmps == null){
                            tmps = new Seq<>();
                        }else if(tmps.contains(lm)){
                            tmps.remove(lm);
                        }else{
                            tmps.add(lm);
                        }
                        planetData.loadedMods = tmps;
                        manager.reloadUnlockableContents(HybridMode.mod);
                    }
                    setup();
                });
            };
            return false;
        }else{
            return true;
        }
    }

    boolean showVanilla(Table table, float h, boolean isLoaded){
        boolean tmp = true;
        if(isLoaded == loadSerpulo){
            table.button(t -> {
                t.defaults().left().top();
                t.margin(12f);
                t.table(title1 -> {
                    title1.left();
                    title1.add(new BorderImage(){{
                        setDrawable(Icon.planet.getRegion());
                        setColor(Planets.serpulo.iconColor);
                        border(Pal.accent);
                    }}).size(h - 8f).padTop(-8f).padLeft(-8f).padRight(8f);
                    title1.table(text -> {
                        text.add(Planets.serpulo.localizedName + "\n[white]" + Core.bundle.get("hybrid.vanilla")).wrap().top().width(300f).growX().left();
                    }).top().growX();

                    title1.add().growX();
                });
            }, Styles.grayt, () -> {
                if(choosePlanet != null){
                    var planetData = manager.hybridData.find(data -> data.planet == choosePlanet);
                    loadSerpulo = planetData.loadSerpulo = !planetData.loadSerpulo;
                    manager.reloadUnlockableContents(HybridMode.mod);
                }
                setup();
            });
            tmp = false;
        }
        if(isLoaded == loadErekir){
            table.button(t -> {
                t.defaults().left().top();
                t.margin(12f);
                t.table(title1 -> {
                    title1.left();
                    title1.add(new BorderImage(){{
                        setDrawable(Icon.planet.getRegion());
                        setColor(Planets.erekir.iconColor);
                        border(Pal.accent);
                    }}).size(h - 8f).padTop(-8f).padLeft(-8f).padRight(8f);
                    title1.table(text -> {
                        text.add(Planets.erekir.localizedName + "\n[white]" + Core.bundle.get("hybrid.vanilla")).wrap().top().width(300f).growX().left();
                    }).top().growX();

                    title1.add().growX();
                });
            }, Styles.grayt, () -> {
                if(choosePlanet != null){
                    var planetData = manager.hybridData.find(data -> data.planet == choosePlanet);
                    loadErekir = planetData.loadErekir = !planetData.loadErekir;
                    manager.reloadUnlockableContents(HybridMode.mod);
                }
                setup();
            });
            tmp = false;
        }
        return tmp;
    }

    boolean showPlanets(Seq<Planet> seqs, Table table, float h){
        if(seqs.size > 0){
            for(var p : seqs){
                table.button(t -> {
                    t.defaults().left().top();
                    t.margin(12f);
                    t.table(title1 -> {
                        title1.left();
                        title1.add(new BorderImage(){{
                            if(Core.atlas.isFound(p.fullIcon)){
                                setDrawable(p.fullIcon);
                            }else{
                                setDrawable(Icon.planet.getRegion());
                                setColor(p.iconColor);
                            }
                            border(Pal.accent);
                        }}).size(h - 8f).padTop(-8f).padLeft(-8f).padRight(8f);
                        title1.table(text -> {
                            text.add(p.localizedName + "\n" + (p.isVanilla() ? "" : "[lightgray]" + p.minfo.mod.meta.displayName)).wrap().top().width(300f).growX().left();
                        }).top().growX();

                        title1.add().growX();
                    });
                }, Styles.grayt, () -> {
                    if(choosePlanet != null){
                        var planetdata = manager.hybridData.find(data -> data.planet == choosePlanet);
                        if(planetdata.loadedPlanets.contains(p)){
                            planetdata.loadedPlanets.remove(p);
                        }else{
                            planetdata.loadedPlanets.add(p);
                        }
                        manager.reloadUnlockableContents(HybridMode.planet);
                    }
                    setup();
                });
            }
            return false;
        }else{
            return true;
        }
    }
}
