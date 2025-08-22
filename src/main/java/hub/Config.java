package hub;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindurka.api.Events;
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
    private Config() {}

    public Seq<Server> servers = new Seq<>();

    /**
     * Create default config.
     * <p>
     * Default config contains no entries.
     */
    @NotNull
    @Contract(" -> new")
    public static Config def() {
        return new Config();
    }

    /**
     * Load config from stream.
     */
    public static Config load(Fi fi) throws IOException, ConfigException {
        var config = new Config();
        config.read(fi);
        return config;
    }

    public void read(Fi fi) throws IOException, ConfigException {
        servers.clear();

        try (var reader = fi.reader(8192, "UTF-8")) {
            var lines = reader.lines();

            for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                var line = it.next();
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;

                if (line.startsWith("server ")) {
                    var server = line.substring("server ".length()).split(" +");
                    if (server.length != 4) throw new ConfigException("'server' entry must have 4 parameters");

                    var name = server[0];
                    float x;
                    try {
                        x = Float.parseFloat(server[1]);
                    } catch (NumberFormatException ignored) {
                        throw new ConfigException("'server.x' must be a float");
                    }
                    float y;
                    try {
                        y = Float.parseFloat(server[2]);
                    } catch (NumberFormatException ignored) {
                        throw new ConfigException("'server.y' must be a float");
                    }
                    float size;
                    try {
                        size = Float.parseFloat(server[3]);
                    } catch (NumberFormatException ignored) {
                        throw new ConfigException("'server.size' must be a float");
                    }

                    servers.add(new Server(name, x, y, size));
                }
            }
        }
    }

    public void write(Fi fi) throws IOException {
        try (var write = fi.writer(false, "UTF-8")) {
            for (var server : servers) {
                write.write("server " + server.serverName + " " + server.serverX + " " + server.serverY + "\n");
            }
        }
    }

    public static class ConfigException extends Exception {
        public ConfigException(String message) {
            super(message);
        }
    }
}
