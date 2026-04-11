package hub;

import arc.struct.Seq;
import arc.util.Log;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import mindurka.annotations.ConsoleCommand;
import mindurka.api.Events;
import mindurka.api.Lifetime;
import mindurka.api.SpecialSettingsLoad;
import mindurka.api.Timer;
import mindurka.coreplugin.RabbitMQ;
import mindurka.coreplugin.messages.ServerInfo;
import mindurka.coreplugin.messages.ServersRefresh;
import mindurka.util.Async;
import mindurka.util.AsyncCall;
import mindurka.util.ClassLoaders;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

import static mindustry.net.Administration.ActionType.*;
import mindurka.api.Gamemode;
import org.jetbrains.annotations.NotNull;

public class LightweightHub extends Plugin {
    public static final float expireInterval = 30f;
    public static final float expireLeeway = 5f;

    private Config config;

    public static LightweightHub instance;

    private void teleport(Player player) {
        teleport(player, player.tileX(), player.tileY());
    }

    private void teleport(Player player, int x, int y) {
        final var tasks = new Seq<Runnable>();
        for (var server : config.servers)
            if (server.isInside(x, y)) {
                if (server.getHost() == null) continue;

                var i = server.getHost().lastIndexOf(":");
                var host = server.getHost().substring(0, i);
                var port = Integer.parseInt(server.getHost().substring(i + 1));

                tasks.add(() -> AsyncCall.connect(player, host, port, new Continuation<>() {
                    @NotNull
                    @Override
                    public CoroutineContext getContext() {
                        return Async.mainScope.getCoroutineContext();
                    }

                    @Override
                    public void resumeWith(@NotNull Object o) {
                        if (!((Boolean) o) && !tasks.isEmpty())
                            tasks.pop().run();
                    }
                }));
            }
        if (!tasks.isEmpty()) {
            tasks.reverse();
            tasks.pop().run();
        }
    }

    @Override
    public void init() {
        instance = this;

        Gamemode.init(ClassLoaders.prefixed(getClass().getClassLoader(), "hub"));
        Gamemode.unlockSpecialBlocks = false;
        Gamemode.restoreTeams = false;
        Gamemode.enableSpectate = false;
        Gamemode.enableSurrender = false;
        Gamemode.enableRtv = false;
        Gamemode.enableVnw = false;

        Events.on(SpecialSettingsLoad.class, event -> {
            if (event.getCurrentMap()) config = new Config(event.getRc());
            else new Config(event.getRc());
        });

        Events.on(WorldLoadEvent.class, event -> {
            Vars.state.rules.blockDamageMultiplier = 0f;
            Vars.state.rules.unitDamageMultiplier = 0f;
            Vars.state.rules.bannedBlocks.addAll(Vars.content.blocks());

            Vars.content.units().each(type -> type.payloadCapacity = 0f);

            Timer.interval(expireInterval, 1f, Lifetime.Round, () -> {
                final var currentConfig = config;

                for (var server : config.servers) {
                    server.currentlyFetching = true;
                }

                Events.fire(new ServersRefresh());

                Timer.timer(expireLeeway, () -> {
                    if (config != currentConfig) return;
                    for (var server : currentConfig.servers)
                        if (server.currentlyFetching)
                            server.update(null);
                });
            });
        });

        Events.on(ServerInfo.class, event -> {
            if (config == null) return;
            var server = RabbitMQ.sentBy(event);
            config.servers.each(x -> x.serverName.equals(server), x -> x.update(event));
        });

        Vars.netServer.admins.addActionFilter(action -> action.type != placeBlock &&
                action.type != breakBlock && (action.type != configure || action.config instanceof Boolean)
                && action.type != rotate);

        Timer.schedule(() -> {
            for (var item : Vars.content.items()) {
                for (var team : Team.all) {
                    var core = team.core();
                    if (core == null) continue;
                    core.items.remove(item, core.items.get(item) / 2);
                }
            }
        }, 60f, 60f);

        Events.on(TapEvent.class, event -> teleport(event.player, event.tile.x, event.tile.y));
        Timer.schedule(() -> Groups.player.each(this::teleport), 0.25f, 0.25f);
    }

    /** Add or modify a server portal. */
    @ConsoleCommand("server-set")
    static void setServer(String name, float x, float y, float size) {
        if (instance.config == null) {
            Log.info("Server's not yet loaded");
            return;
        }

        var server = instance.config.servers.find(it -> it.serverName.equals(name));
        if (server == null) {
            server = new Server(name, x, y, size);
            server.update(null);
            instance.config.servers.add(server);
        } else {
            server.serverX = x;
            server.serverY = y;
            server.serverSize = size;
            server.moved();
        }
        Events.fire(new ServersRefresh());
    }

    /** Remove a server portal. */
    @ConsoleCommand("server-unset")
    static void removeServer(String name) {
        if (instance.config == null) {
            Log.info("Server's not yet loaded");
            return;
        }

        var server = instance.config.servers.find(it -> it.serverName.equals(name));
        if (server == null) return;
        instance.config.servers.remove(server);
        server.remove();
    }

    /** List all servers. */
    @ConsoleCommand("servers")
    static void servers() {
        if (instance.config == null) {
            Log.info("Server's not yet loaded");
            return;
        }

        for (var server : instance.config.servers) {
            Log.info("Server " + server.serverName + "(" + server.serverX + ":"  + server.serverY + ", size=" + server.serverSize + ")");
            if (server.getHost() == null) {
                Log.info("Offline");
            } else {
                Log.info("Name: " + server.getName());
                Log.info("Host: " + server.getHost());
            }
        }
    }
}