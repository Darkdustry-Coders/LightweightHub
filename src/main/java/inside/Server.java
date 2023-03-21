package inside;

import arc.func.Cons;
import arc.math.Mathf;
import mindustry.net.Host;

import static mindustry.Vars.*;

public class Server {

    public String ip = "darkdustry.net";
    public int port;

    public int x;
    public int y;

    public float size;

    public float titleX;
    public float titleY;

    public float labelX;
    public float labelY;

    public boolean isInside(int x, int y) {
        return x <= this.x + size && x >= this.x - size && y <= this.y + size && y >= this.y - size;
    }

    public boolean isNear(int x, int y) {
        return Mathf.dst(this.x, this.y, x, y) <= size * 4f;
    }

    public void pingHost(Cons<Host> valid, Cons<Exception> failed) {
        net.pingHost(ip, port, valid, failed);
    }
}