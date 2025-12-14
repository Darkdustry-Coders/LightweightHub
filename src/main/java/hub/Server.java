package hub;

import arc.math.Mathf;
import arc.util.Nullable;
import buj.tl.Tl;
import mindurka.coreplugin.messages.ServerInfo;
import mindurka.util.K;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.gen.WorldLabel;

import java.util.WeakHashMap;

public class Server {
    public final String serverName;
    public float serverX;
    public float serverY;
    public float serverSize;
    public boolean currentlyFetching = false;

    private @Nullable EmbokrifiedWorldLabel nameLabel = null;
    private final WeakHashMap<Player, Integer> namePlayers = new WeakHashMap<>();
    private @Nullable EmbokrifiedWorldLabel offlineLabel = null;
    private final WeakHashMap<Player, K> offlinePlayers = new WeakHashMap<>();
    private @Nullable EmbokrifiedWorldLabel statusLabel = null;
    private final WeakHashMap<Player, Integer> statusPlayers = new WeakHashMap<>();

    public Server(String name, float x, float y, float size) {
        this.serverName = name;
        this.serverX = x;
        this.serverY = y;
        this.serverSize = size;
    }

    public void update(@Nullable ServerInfo server) {
        currentlyFetching = false;

        if (server == null) {
            host = null;
            name = null;
        } else {
            host = server.getIp();
            name = server.getName();
        }

        if (host == null) {
            if (nameLabel != null) {
                nameLabel.hide();
                nameLabel = null;
            }
            if (statusLabel != null) {
                statusLabel.hide();
                statusLabel = null;
            }

            if (offlineLabel == null) {
                offlineLabel = EmbokrifiedWorldLabel.create();
                offlineLabel.x = serverX * Vars.tilesize;
                offlineLabel.y = serverY * Vars.tilesize;
                offlineLabel.text = "[scarlet]Whoops! You shouldn't be able to see this text!";
                offlineLabel.mainText = player -> Tl.fmt(player).done("{hub.server.offline}");
                offlineLabel.flags = WorldLabel.flagBackground + WorldLabel.flagOutline;
                offlineLabel.add();
            }

            offlinePlayers.clear();
            offlineLabel.syncIf = player -> {
                var sync = offlinePlayers.containsKey(player);
                if (!sync) offlinePlayers.put(player, K.INSTANCE);
                return sync;
            };
        }
        else {
            if (offlineLabel != null) {
                offlineLabel.hide();
                offlineLabel = null;
            }

            if (nameLabel == null) {
                nameLabel = EmbokrifiedWorldLabel.create();
                nameLabel.x = serverX * Vars.tilesize;
                nameLabel.y = serverY * Vars.tilesize + serverSize * Vars.tilesize / 2 + Vars.tilesize;
                nameLabel.flags = WorldLabel.flagBackground + WorldLabel.flagOutline;
                nameLabel.fontSize *= 1.5f;
                nameLabel.add();
            }
            nameLabel.text = name;

            namePlayers.clear();
            nameLabel.syncIf = player -> {
                var hash1 = namePlayers.getOrDefault(player, 0);
                var hash2 = server.hashCode();
                var ne = hash1 != hash2;

                if (ne) namePlayers.put(player, hash1);
                return ne;
            };

            if (statusLabel == null) {
                statusLabel = EmbokrifiedWorldLabel.create();
                statusLabel.x = serverX * Vars.tilesize;
                statusLabel.y = serverY * Vars.tilesize - serverSize * Vars.tilesize / 2 - Vars.tilesize;
                statusLabel.text = "[scarlet]Whoops! You shouldn't be able to see this text!";
                statusLabel.flags = WorldLabel.flagBackground + WorldLabel.flagOutline;
                statusLabel.add();
            }
            statusLabel.mainText = player -> {
                var text = new StringBuilder();
                text.append("{hub.server.online.players}\n{hub.server.online.map}");
                var verbose = isInside(Mathf.floor(player.mouseX / Vars.tilesize), Mathf.floor(player.mouseY / Vars.tilesize)) ||
                    isNear(Mathf.floor(player.x / Vars.tilesize), Mathf.floor(player.y / Vars.tilesize));
                if (server.getWave() != -1 && verbose) {
                    text.append("\n{hub.server.online.wave}");
                }
                if (verbose) text.append("\n{hub.server.online.address}");
                return Tl.fmt(player)
                    .put("players", server.getMaxPlayers() == -1
                        ? Integer.toString(server.getPlayers())
                        : (server.getPlayers() + "/" + server.getMaxPlayers()))
                    .put("wave", server.getMaxWaves() == -1
                        ? Integer.toString(server.getWave())
                        : (server.getWave() + "/" + server.getMaxWaves()))
                    .put("map", server.getMap())
                    .put("address", server.getIp())
                    .done(text.toString());
            };

            statusPlayers.clear();
            statusLabel.syncIf = player -> {
                var hash1 = statusPlayers.getOrDefault(player, 0);
                var hash2 = server.hashCode();
                var ne = hash1 != hash2;

                if (ne) statusPlayers.put(player, hash1);
                return ne;
            };
        }
    }

    public void remove() {
        if (nameLabel != null) {
            nameLabel.hide();
            nameLabel = null;
        }
        if (offlineLabel != null) {
            offlineLabel.hide();
            offlineLabel = null;
        }
        if (statusLabel != null) {
            statusLabel.hide();
            statusLabel = null;
        }
        namePlayers.clear();
        offlinePlayers.clear();
        statusPlayers.clear();
    }

    public void moved() {
        if (nameLabel != null) {
            nameLabel.x = serverX * Vars.tilesize;
            nameLabel.y = serverY * Vars.tilesize + serverSize * Vars.tilesize / 2 + Vars.tilesize;
        }
        if (offlineLabel != null) {
            offlineLabel.x = serverX * Vars.tilesize;
            offlineLabel.y = serverY * Vars.tilesize;
        }
        if (statusLabel != null) {
            statusLabel.x = serverX * Vars.tilesize;
            statusLabel.y = serverY * Vars.tilesize - serverSize * Vars.tilesize / 2 - Vars.tilesize;
        }
        namePlayers.clear();
        offlinePlayers.clear();
        statusPlayers.clear();
    }

    private String host = null;
    private String name = null;

    public String getHost() {
        return host;
    }

    public String getName() {
        return name;
    }

    public boolean isInside(int x, int y) {
        return x <= serverX + (serverSize / 2 + ((serverSize + 1) % 2))
            && x >= serverX - serverSize / 2
            && y <= serverY + (serverSize / 2 + ((serverSize + 1) % 2))
            && y >= serverY - serverSize / 2;
    }

    public boolean isNear(int x, int y) {
        return x <= serverX + (serverSize / 2 + ((serverSize + 1) % 2) + 3)
            && x >= serverX - (serverSize / 2 + 3)
            && y <= serverY + (serverSize / 2 + ((serverSize + 1) % 2) + 3)
            && y >= serverY - (serverSize / 2 + 3);
    }
}