package sk.perri.kc.vote;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.util.Map;

public class Hrac
{
    private String nick;
    private Player player;
    private boolean voted = false;
    private int votes = -1;
    private int offline = 0;
    private Timestamp last;

    public Hrac(Player player)
    {
        this.player = player;
        nick = player.getName();

        Map<String, Object> tmp = Main.self.db.hlasy(nick);
        votes = tmp.get("votes") != null ? (int) tmp.get("votes") : -1;
        last = (Timestamp) tmp.get("last");
        if(last != null)
            voted = Main.self.timestamp.getTime().getTime() < last.getTime();

        offline = tmp.get("offline") != null ? (int) tmp.get("offline") : 0;
    }

    void setVoted(boolean voted)
    {
        this.voted = voted;
    }

    void addVote(Timestamp ts)
    {
        votes++;
        voted = true;
        last = ts;
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                String.format(Main.self.getConfig().getString("msg.voted"), votes)));
    }

    Player getPlayer()
    {
        return player;
    }

    public boolean voted()
    {
        return voted;
    }

    public int getOfflineVotes()
    {
        return offline;
    }

    public int getVotes()
    {
        return votes;
    }

    public void setOfflineVotes(int votes)
    {
        offline = votes;
    }

    void resetVoted()
    {
        voted = false;

            Main.self.getConfig().getStringList("msg.restart").forEach(l ->
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', l)));
            player.sendTitle(ChatColor.translateAlternateColorCodes('&',
                    Main.self.getConfig().getString("msg.title")),
                    ChatColor.translateAlternateColorCodes('&',
                            Main.self.getConfig().getString("msg.subtitle")), 20, 70, 10);
            player.performCommand("vote:vote");
    }
}
