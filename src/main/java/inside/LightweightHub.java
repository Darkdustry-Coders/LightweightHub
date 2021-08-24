package inside;

import arc.*;
import arc.files.Fi;
import arc.func.Func;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.Streams;
import com.google.gson.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.game.*;
import mindustry.mod.Plugin;
import mindustry.net.*;
import mindustry.world.Tile;
import mindustry.content.*;
import mindustry.world.blocks.storage.CoreBlock;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.*;

public class LightweightHub extends Plugin{
    public static Config config;

    private final Interval interval = new Interval();
    private final AtomicInteger counter = new AtomicInteger();
    private final Seq<Timer.Task> tasks = new Seq<>();
    public final Func<Host, String> formatter = host -> config.onlinePattern.replace("%name%", host.name)
            .replace("%address%", host.address)
            .replace("%mapname%", host.mapname)
            .replace("%description%", host.description)
            .replace("%wave%", Integer.toString(host.wave))
            .replace("%players%", Integer.toString(host.players))
            .replace("%playerLimit%", Integer.toString(host.playerLimit))
            .replace("%version%", Integer.toString(host.version))
            .replace("%versionType%", host.versionType)
            .replace("%mode%", host.mode.name()) // NOTE: Gamemode#toString use localized string, but the server has no localization.
            .replace("%modeName%", host.modeName != null ? host.modeName : host.mode.name())
            .replace("%ping%", Integer.toString(host.ping))
            .replace("%port%", Integer.toString(host.port));

    public final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    public void teleport(Player player){
        teleport(player, null);
    }

    public void teleport(final Player player, Tile tile){
        for(HostData data : config.servers){
            if(data.inDiapason(tile != null ? tile.x : player.tileX(), tile != null ? tile.y : player.tileY())){
                net.pingHost(data.ip, data.port, host -> {
                    if(config.logConnects){
                        Log.info("[@] @ --> @:@", player.uuid(), player.name, data.ip, data.port);
                    }
                    Call.connect(player.con, data.ip, data.port);
                }, e -> {});
            }
        }
    }

    @Override
    public void init(){

        ((CoreBlock)Blocks.coreNucleus).unitType = UnitTypes.flare;

        Fi cfg = dataDirectory.child("config-hub.json");
        if(!cfg.exists()){
            cfg.writeString(gson.toJson(config = new Config()));
            Log.info("Файл конфигурации сгенерирован... (@)", cfg.absolutePath());
        }else{
            try{
                config = gson.fromJson(cfg.reader(), Config.class);
            }catch(Throwable t){
                Log.err("Ошибка загрузки файла конфигурации. Что-то не так с форматом json.");
                Log.err(t);
            }
        }

        Events.on(ServerLoadEvent.class, event -> netServer.admins.addActionFilter(playerAction -> false));

        Events.on(TapEvent.class, event -> teleport(event.player, event.tile));

        Events.run(Trigger.update, () -> {
            if(interval.get(60 * 0.15f)){
                Groups.player.each(this::teleport);
            }
        });

        Events.on(PlayerJoin.class, event -> {
            NetConnection con = event.player.con();
            for(HostData data : config.servers){
                Call.label(con, data.title, 10f, data.titleX, data.titleY);
                net.pingHost(data.ip, data.port, host -> Call.label(con, formatter.get(host), 10f, data.labelX, data.labelY),
                        e -> Call.label(con, config.offlinePattern, 10f, data.labelX, data.labelY));
            }
        });

        Timer.schedule(() -> {
            CompletableFuture<?>[] tasks = config.servers.stream()
                    .map(data -> CompletableFuture.runAsync(() -> {

                        Core.app.post(() -> Call.label(data.title, 5f, data.titleX, data.titleY));
                        net.pingHost(data.ip, data.port, host -> {
                            counter.addAndGet(host.players);
                            Call.label(formatter.get(host), 5f, data.labelX, data.labelY);
                        }, e -> Call.label(config.offlinePattern, 5f, data.labelX, data.labelY));
                    }))
                    .toArray(CompletableFuture<?>[]::new);

            CompletableFuture.allOf(tasks).thenRun(() -> {
                counter.addAndGet(Groups.player.size());
                Core.settings.put("totalPlayers", counter.get());
                counter.set(0);
            }).join();
        }, 1.5f, 5f);
    }

    @Override
    public void registerServerCommands(CommandHandler handler){

        handler.register("reload-cfg", "Перезапустить файл с конфигами.", args -> {
            try{
                tasks.each(Timer.Task::cancel);
                config = gson.fromJson(dataDirectory.child("config-hub.json").readString(), Config.class);
                Log.info("Успешно перезагружено.");
            }catch(Throwable t){
                Log.err("Ошибка загрузки файла config-hub.json.");
                Log.err(t);
            }
        });
    }
}
