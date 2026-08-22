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


    public HybridDialog() {
        super("@hybridManagerTile");
        addCloseButton();

        shown(() -> {
            setup();
        });

        onResize(this::setup);

        hidden(() -> {
            manager.save();
        });
    }

    void setup(){
        float w = Math.max(Core.graphics.getWidth() / 8f, 200f);
        float h = Math.max(Core.graphics.getHeight() / 8f, 100f);

        allMods.clear();
        mods.getMods().each(mod -> {
            if(mod.enabled() && !mod.meta.hidden){
                allMods.add(mod);
            }
        });

        if(choosePlanet == null) choosePlanet = Planets.erekir;

        loadedMods.clear();
        unloadedMods.clear();
        allMods.each(mod -> {
            manager.hybridData.each(hybridData -> {
                if(hybridData.planet == choosePlanet){
                    if(hybridData.loadedMods.contains(mod)){
                        loadedMods.add(mod);
                    }else{
                        unloadedMods.add(mod);
                    }
                }
            });
        });

        cont.clear();

        cont.table(left -> {
            left.top();
            left.background(Styles.grayPanel);

            left.table(t -> {
                t.add(new Label("@hybridMode")).height(50f).row();
                var group = new ButtonGroup<>();
                for(var hm : HybridMode.all){
                    t.button(hm.localized(), Styles.flatTogglet, () -> {
                        Core.settings.put("hybridMode", hm.name());
                        setup();
                    }).height(50f).growX().group(group).checked(Core.settings.getString("hybridMode", "mod") == hm.name()).row();
                }
            }).height(150f).top().growX().row();

            left.pane(side -> {
                side.add(Core.bundle.get("mod.hybrid.planets")).row();
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
            if(Core.settings.getString("hybridMode", "mod").equals("mod")){
                right.table(top -> {
                    showModHybridState(top, h);
                }).height(h*2).growX().top().row();

                right.add(Core.bundle.get("planetDatabase")).row();
                right.image().color(Pal.accent).height(3.0F).left().fillX().padBottom(5.0F).row();

                right.pane(data -> {
                    showPlanetDataBase(choosePlanet, data);
                }).marginTop(10f).grow();
            }
            else if(Core.settings.getString("hybridMode", "planet").equals("planet")){
                //TODO 星球分类显示
            }
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
                                Image image = list.add(new Image(u.uiIcon)).size(Mathf.clamp((Core.graphics.getWidth() - Math.max(Core.graphics.getWidth() / 8f, 200f)) / 40f, 32f, 80f)).pad(3).get();

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

    void showModHybridState(Table top, float h){
        top.top();

        top.pane(t -> {
            showMods(loadedMods, t, Core.bundle.get("mod.hybrid.loaded"), h/2);
        }).height(h).growX().padBottom(8f);
        top.row();

        top.pane(t -> {
            showMods(unloadedMods, t, Core.bundle.get("mod.hybrid.unloaded"), h/2);
        }).height(h).growX().padBottom(8f);
    }

    void changeModHybridState(Mods.LoadedMod mod){
        if(choosePlanet != null){
            var planetData = manager.hybridData.find(data -> data.planet == choosePlanet);
            var tmps = planetData.loadedMods;
            if(tmps == null){
                tmps = new Seq<>();
            }else if(tmps.contains(mod)){
                tmps.remove(mod);
            }else{
                tmps.add(mod);
            }
            planetData.loadedMods = tmps;
            manager.reloadUnlockableContents();
        }
    }

    void showMods(Seq<Mods.LoadedMod> seqs, Table table, String tableTitle, float h){
        table.left();
        table.background(Styles.grayPanelDark);
        table.add(tableTitle).padLeft(4f).padRight(4f);
        if(seqs.size > 0){
            seqs.each(lm -> {
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
                    changeModHybridState(lm);
                    setup();
                });
            });
        }else{
            table.add("@empty");//TODO
        }
    }

    void showVanilla(Table table, float h){

    }
}
