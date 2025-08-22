package hub;

import arc.func.Func;
import arc.util.io.Writes;
import mindustry.gen.Player;
import mindustry.gen.WorldLabel;
import mindustry.io.TypeIO;

public class EmbokrifiedWorldLabel extends WorldLabel {
    private Player borkedFor = null;
    public Func<Player, String> mainText = null;

    public static EmbokrifiedWorldLabel create() {
        return new EmbokrifiedWorldLabel();
    }

    @Override
    public int classId() {
        return 35;
    }

    @Override
    public boolean isSyncHidden(Player player) {
        borkedFor = player;
        return super.isSyncHidden(player);
    }

    private String renderText() {
        if (mainText != null) {
            return mainText.get(borkedFor);
        }
        return text;
    }

    public void write(Writes write) {
        write.s(0);
        write.b(this.flags);
        write.f(this.fontSize);
        TypeIO.writeString(write, renderText());
        write.f(this.x);
        write.f(this.y);
        write.f(this.z);
    }

    public void writeSync(Writes write) {
        write.b(this.flags);
        write.f(this.fontSize);
        TypeIO.writeString(write, renderText());
        write.f(this.x);
        write.f(this.y);
        write.f(this.z);
    }
}
