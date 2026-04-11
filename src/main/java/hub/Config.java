package hub;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import arc.util.serialization.Jval;
import mindurka.api.Events;
import mindurka.api.RulesContext;
import mindurka.api.SpecialSettings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Iterator;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * LightweightHub config.
 */
public class Config {
    private static final String PREFIX = SpecialSettings.PREFIX+".hub";
    private static final String SERVERS = PREFIX+".servers";

    public Config(RulesContext rc) {
        String json = rc.r(SERVERS, "[]");
        try {
            Jval.JsonArray array = Jval.read(json).asArray();
            array.forEach(x -> {
                try {
                    Jval.JsonMap object = x.asObject();
                    assert object.get("version").asInt() == 1;
                    String serverName = object.get("name").asString();
                    int serverX = object.get("x").asInt();
                    int serverY = object.get("y").asInt();
                    int serverSize = object.get("size").asInt();
                    Server server = new Server(serverName, serverX, serverY, serverSize);
                    servers.add(server);
                } catch (Exception e) {
                    Log.err("Failed to parse server", e);
                }
            });
        } catch (Exception e) {
            Log.err("Failed to parse servers", e);
        }
    }

    public Seq<Server> servers = new Seq<>();
}
