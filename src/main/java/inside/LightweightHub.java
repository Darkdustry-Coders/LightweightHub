package inside;

import arc.Core;
import arc.Events;
import arc.util.*;
import com.google.gson.*;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.net.Host;
import useful.Bundle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.*;
import static mindustry.net.Administration.ActionType.*;

public class LightweightHub extends Plugin {

    public static final float updateInterval = 3f;

    public static final Interval interval = new Interval();
    public static final AtomicInteger counter = new AtomicInteger();
    public static final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES).setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

    public static Config config;

    public static void showOnlineLabel(Player player, Server server, Host host) {
        Call.label(player.con, host.name, updateInterval, server.titleX * tilesize, server.titleY * tilesize);
        Call.label(player.con, Bundle.format(!server.isNear(player.tileX(), player.tileY()) ?
                "server.online" :
                "server.online.full",
                player, host.players, host.mapname, host.wave, server.ip + ":" + server.port
        ), updateInterval, server.labelX * tilesize, server.labelY * tilesize);
    }

    public static void showOfflineLabel(Player player, Server server) {
        Call.label(player.con, Bundle.format("server.offline", player), updateInterval, server.labelX * tilesize, server.labelY * tilesize);
    }

    public static void teleport(Player player) {
        teleport(player, player.tileX(), player.tileY());
    }

    public static void teleport(Player player, int x, int y) {
        config.servers.forEach(server -> {
            if (server.isInside(x, y))
                server.pingHost(host -> Call.connect(player.con, server.ip, server.port), e -> {});
        });
    }

    @Override
    public void init() {
        var configFile = dataDirectory.child("config-hub.json");
        if (configFile.exists()) {
            config = gson.fromJson(configFile.reader(), Config.class);
            Log.info("[Hub] Config loaded. (@)", configFile.absolutePath());
        } else {
            configFile.writeString(gson.toJson(config = new Config()));
            Log.info("[Hub] Config file generated. (@)", configFile.absolutePath());
        }

        Bundle.load(LightweightHub.class);

        Events.run(Trigger.update, () -> {
            if (interval.get(updateInterval))
                Groups.player.each(LightweightHub::teleport);
        });

        Events.on(TapEvent.class, event -> teleport(event.player, event.tile.x, event.tile.y));

        Events.on(PlayerJoin.class, event -> config.servers.forEach(server -> server.pingHost(host -> showOnlineLabel(event.player, server, host), error -> showOfflineLabel(event.player, server))));

        Events.on(WorldLoadEvent.class, event -> {
            state.rules.blockDamageMultiplier = 0f;
            state.rules.unitDamageMultiplier = 0f;

            content.units().each(type -> type.payloadCapacity = 0f);

            Structs.each(team -> team.rules().cheat = true, Team.all);
        });

        Timer.schedule(() -> {
            var tasks = config.servers.stream().map(server -> CompletableFuture.runAsync(() -> server.pingHost(host -> {
                counter.addAndGet(host.players);
                Groups.player.each(player -> showOnlineLabel(player, server, host));
            }, error -> Groups.player.each(player -> showOfflineLabel(player, server))))).toArray(CompletableFuture<?>[]::new);

            CompletableFuture.allOf(tasks).thenRun(() -> {
                counter.addAndGet(Groups.player.size());
                Core.settings.put("totalPlayers", counter.getAndSet(0));
            }).join();
        }, 0f, updateInterval);

        netServer.admins.addActionFilter(action -> action.type != placeBlock && action.type != breakBlock && (action.type != configure || action.config instanceof Boolean));
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("reload-config", "Reload LightweightHub config .", args -> {
            config = gson.fromJson(dataDirectory.child("config-hub.json").readString(), Config.class);
            Log.info("[Hub] Config reloaded.");
        });
    }
}