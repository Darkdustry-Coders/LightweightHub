package hub;

import arc.Core;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Time;
import buj.tl.Tl;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import mindurka.annotations.ConsoleCommand;
import mindurka.api.Events;
import mindurka.api.Lifetime;
import mindurka.api.SpecialSettingsLoad;
import mindurka.api.Timer;
import mindurka.coreplugin.PlayerDataKt;
import mindurka.coreplugin.RabbitMQ;
import mindurka.coreplugin.messages.BringPlayerBack;
import mindurka.coreplugin.messages.ServerInfo;
import mindurka.coreplugin.messages.ServersRefresh;
import mindurka.util.Async;
import mindurka.util.AsyncCall;
import mindurka.util.ClassLoaders;
import mindurka.util.StringKt;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Call;
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

    private enum SendReason {
        BringBack,
    }
    private static class SendToServerData {
        public long until = Time.nanos() + 10000000000L;
        public final String profileId;
        public String serverName;
        public SendReason reason;

        public SendToServerData(String profileId, String serverName, SendReason reason) {
            this.profileId = profileId;
            this.serverName = serverName;
            this.reason = reason;
        }
    }
    private final Seq<SendToServerData> bringBack = new Seq<>();
    private SendToServerData bringBackData(String profileId) {
        for (var data : bringBack) if (data.profileId.equals(profileId)) return data;
        return null;
    }
    private SendToServerData bringBackData(Player player) {
        return bringBackData(PlayerDataKt.getSessionData(player).getProfileId());
    }

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

                i = server.getLocalHost().lastIndexOf(":");
                var localHost = server.getLocalHost().substring(0, i);
                var localPort = Integer.parseInt(server.getLocalHost().substring(i + 1));

                tasks.add(() -> AsyncCall.connect(player, host, port, localHost, localPort, new Continuation<>() {
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

    private void updatePlayerCount() {
        if (config == null) return;
        int playerCount = 0;
        for (int i = 0; i < config.servers.size; i++) {
            Server server = config.servers.items[i];
            playerCount += Math.max(server.playerCount(), 0);
        }
        playerCount += Groups.player.size();
        Core.settings.put("totalPlayers", playerCount);
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
        Gamemode.hasStats = false;
        Gamemode.adminCommands = false;
        Gamemode.sendHub = false;

        Events.on(SpecialSettingsLoad.class, event -> {
            if (event.getCurrentMap()) config = new Config(event.getRc());
            else new Config(event.getRc());
        });

        Events.on(WorldLoadEvent.class, event -> {
            Vars.state.rules.blockDamageMultiplier = 0f;
            Vars.state.rules.unitDamageMultiplier = 0f;
            Vars.state.rules.bannedBlocks.addAll(Vars.content.blocks());

            Vars.content.units().each(type -> type.payloadCapacity = 0f);

            Timer.interval(expireInterval, 2f, Lifetime.Round, () -> {
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
            updatePlayerCount();
        });

        Events.on(PlayerJoin.class, x -> updatePlayerCount());
        Events.on(PlayerLeave.class, x -> updatePlayerCount());

        Events.on(PlayerJoin.class, x -> {
            SendToServerData data = bringBackData(x.player);
            if (data == null) return;
            if (data.until < Time.nanos()) {
                bringBack.remove(data);
                return;
            }
            switch (data.reason) {
                case BringBack:
                    Tl.send(x.player).done("{hub.bring-back}");
                    break;
            }
        });
        Events.on(PlayerLeave.class, x -> {
            SendToServerData data = bringBackData(x.player);
            if (data == null) return;
            bringBack.remove(data);
        });
        Events.on(BringPlayerBack.class, x -> {
            String sender = RabbitMQ.sentBy(x);
            for (String id : x.getProfileIDs()) {
                var data = bringBackData(id);
                if (data == null) {
                    data = new SendToServerData(id, sender, SendReason.BringBack);
                    bringBack.add(data);
                } else {
                    data.serverName = sender;
                    data.reason = SendReason.BringBack;
                }
                var player = Groups.player.find(y -> PlayerDataKt.getSessionData(y).getProfileId().equals(id));
                if (player == null) continue;
                Tl.send(player).done("{hub.bring-back}");
            }
        });
        Timer.interval(1000f, () -> {
            long time = Time.nanos();
            bringBack.retainAll(x ->
                x.until < time && Groups.player.find(y -> PlayerDataKt.getSessionData(y).getProfileId().equals(x.profileId)) == null);
        });
        Events.on(ServerInfo.class, x -> {
            String server = RabbitMQ.sentBy(x);
            bringBack.retainAll(y -> {
                if (!y.serverName.equals(server)) return true;
                var player = Groups.player.find(z -> PlayerDataKt.getSessionData(z).getProfileId().equals(y.profileId));
                if (player == null) {
                    return false;
                }
                StringKt.splitOnceLast(x.getIp(), ":", (ip, port) -> {
                    assert port != null;
                    Call.connect(player.con, ip, Integer.parseInt(port));
                    return Unit.INSTANCE;
                });
                return false;
            });
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