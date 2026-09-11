package hub;

import arc.math.Mathf;
import arc.util.Nullable;
import buj.tl.Tl;
import mindurka.coreplugin.messages.ServerInfo;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.WorldLabel;

import java.util.HashMap;
import java.util.Map;

public class Server {
    public final String serverName;
    public float serverX;
    public float serverY;
    public float serverSize;
    public boolean currentlyFetching = false;

    private final Map<Player, PlayerLabels> playerLabels = new HashMap<>();

    private @Nullable ServerInfo lastInfo = null;
    private @Nullable String host = null;
    private @Nullable String name = null;

    public Server(String name, float x, float y, float size) {
        this.serverName = name;
        this.serverX = x;
        this.serverY = y;
        this.serverSize = size;
    }

    public void update(@Nullable ServerInfo server) {
        currentlyFetching = false;
        lastInfo = server;

        if (server == null) {
            host = null;
            name = null;
        } else {
            host = server.getIp();
            name = server.getName();
        }

        for (var entry : playerLabels.entrySet()) {
            updateLabelsFor(entry.getKey(), entry.getValue());
        }
    }

    public void showFor(Player player) {
        if (player.con == null) return;
        var labels = new PlayerLabels();
        playerLabels.put(player, labels);
        updateLabelsFor(player, labels);
    }

    public void hideFor(Player player) {
        var labels = playerLabels.remove(player);
        if (labels != null) labels.hideAll(player);
    }

    private void updateLabelsFor(Player player, PlayerLabels labels) {
        if (player.con == null || player.con.hasDisconnected) {
            return;
        }

        if (host == null) {
            labels.hideName(player);
            labels.hideStatus(player);
            labels.showOffline(player, this);
        } else {
            labels.hideOffline(player);
            labels.showName(player, this);
            labels.showStatus(player, this, lastInfo);
        }
    }

    public void remove() {
        for (var entry : playerLabels.entrySet()) {
            entry.getValue().hideAll(entry.getKey());
        }
        playerLabels.clear();
    }

    public void moved() {
        for (var entry : playerLabels.entrySet()) {
            entry.getValue().hideAll(entry.getKey());
        }
        playerLabels.clear();
        Groups.player.each(p -> !p.isLocal() && p.con != null, this::showFor);
    }

    public String getHost() {
        return host;
    }

    public String getName() {
        return name;
    }

    public boolean isInside(int x, int y) {
        return x >= serverX && x <= serverX + serverSize &&
               y >= serverY && y <= serverY + serverSize;
    }

    public boolean isNear(int x, int y) {
        return x >= serverX - 3 && x <= serverX + serverSize + 3 &&
               y >= serverY - 3 && y <= serverY + serverSize + 3;
    }

    private static class PlayerLabels {
        @Nullable WorldLabel nameLabel;
        @Nullable WorldLabel offlineLabel;
        @Nullable WorldLabel statusLabel;
        int lastInfoHash;

        void showName(Player player, Server server) {
            if (nameLabel == null) {
                nameLabel = WorldLabel.create();
                nameLabel.x = (server.serverX + server.serverSize / 2f - 0.5f) * Vars.tilesize;
                nameLabel.y = (server.serverY + server.serverSize / 2f - 0.5f) * Vars.tilesize + server.serverSize * Vars.tilesize / 2 + Vars.tilesize;
                nameLabel.flags = WorldLabel.flagBackground + WorldLabel.flagOutline + WorldLabel.flagAutoscale;
                nameLabel.fontSize *= 1.5f;
                nameLabel.text = server.name;
                nameLabel.show(player);
            } else {
                nameLabel.text = server.name;
            }
        }

        void hideName(Player player) {
            if (nameLabel != null) {
                nameLabel.hide(player);
                nameLabel = null;
            }
        }

        void showOffline(Player player, Server server) {
            if (offlineLabel == null) {
                offlineLabel = WorldLabel.create();
                offlineLabel.x = (server.serverX + server.serverSize / 2f - 0.5f) * Vars.tilesize;
                offlineLabel.y = (server.serverY + server.serverSize / 2f - 0.5f) * Vars.tilesize;
                offlineLabel.text = Tl.fmt(player).done("{hub.server.offline}");
                offlineLabel.flags = WorldLabel.flagBackground + WorldLabel.flagOutline + WorldLabel.flagAutoscale;
                offlineLabel.show(player);
            }
        }

        void hideOffline(Player player) {
            if (offlineLabel != null) {
                offlineLabel.hide(player);
                offlineLabel = null;
            }
        }

        void showStatus(Player player, Server server, @Nullable ServerInfo info) {
            if (info == null) return;

            int hash = info.hashCode();
            if (statusLabel != null && lastInfoHash == hash) return;
            lastInfoHash = hash;

            if (statusLabel != null) {
                statusLabel.hide(player);
            }

            statusLabel = WorldLabel.create();
            statusLabel.x = (server.serverX + server.serverSize / 2f - 0.5f) * Vars.tilesize;
            statusLabel.y = (server.serverY + server.serverSize / 2f) * Vars.tilesize - server.serverSize * Vars.tilesize / 2 - Vars.tilesize;
            statusLabel.flags = WorldLabel.flagBackground + WorldLabel.flagOutline + WorldLabel.flagAutoscale + WorldLabel.flagAlignLeft;

            var text = new StringBuilder();
            text.append("{hub.server.online.players}\n{hub.server.online.map}");
            var verbose = server.isInside(
                Mathf.floor(player.mouseX / Vars.tilesize),
                Mathf.floor(player.mouseY / Vars.tilesize)) ||
                server.isNear(
                    Mathf.floor(player.x / Vars.tilesize),
                    Mathf.floor(player.y / Vars.tilesize));
            if (info.getWave() != -1 && verbose) {
                text.append("\n{hub.server.online.wave}");
            }
            if (verbose) text.append("\n{hub.server.online.address}");

            statusLabel.text = Tl.fmt(player)
                .put("players", info.getMaxPlayers() == -1
                    ? Integer.toString(info.getPlayers())
                    : (info.getPlayers() + "/" + info.getMaxPlayers()))
                .put("wave", info.getMaxWaves() == -1
                    ? Integer.toString(info.getWave())
                    : (info.getWave() + "/" + info.getMaxWaves()))
                .put("map", info.getMap())
                .put("address", info.getIp())
                .done(text.toString());

            statusLabel.show(player);
        }

        void hideStatus(Player player) {
            if (statusLabel != null) {
                statusLabel.hide(player);
                statusLabel = null;
            }
        }

        void hideAll(Player player) {
            hideName(player);
            hideOffline(player);
            hideStatus(player);
        }
    }
}