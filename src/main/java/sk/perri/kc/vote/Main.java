package sk.perri.kc.vote;

import be.maximvdw.placeholderapi.PlaceholderAPI;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Timestamp;
import java.util.*;

public class Main extends JavaPlugin implements Listener
{
    static Main self;
    int session = 0;
    Calendar timestamp;
    Database db;
    Map<String, Hrac> hraci = new HashMap<>();
    Vector<String> vp = new Vector<>();
    int votepartylimit = 100;
    int voteparty = 0;
    String votepartyStr = "§e0§7/§e150";

    @Override
    public void onEnable()
    {
        self = this;

        if(!getDataFolder().exists())
            getDataFolder().mkdir();

        getConfig().options().copyDefaults(true);
        saveConfig();

        CmdExecutor ce = new CmdExecutor();
        getCommand("vote").setExecutor(ce);
        getCommand("hlasy").setExecutor(ce);
        getCommand("voteparty").setExecutor(ce);
        getCommand("fakevote").setExecutor(ce);
        getServer().getPluginManager().registerEvents(this, this);

        db = new Database();
        db.connect();

        Calendar nowt = Calendar.getInstance();
        session = (int) Math.round(Math.floor(nowt.get(Calendar.HOUR_OF_DAY) / 2.0))*2;
        timestamp = Calendar.getInstance();
        timestamp.set(Calendar.HOUR_OF_DAY, session);
        timestamp.set(Calendar.MINUTE, 0);
        timestamp.set(Calendar.SECOND, 0);

        votepartylimit = getConfig().getInt("voteparty");
        int[] reset = {10};

        Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(this, ()->
        {
            Calendar now = Calendar.getInstance();

            if(now.get(Calendar.HOUR_OF_DAY) % 2 == 0 && now.get(Calendar.MINUTE) == 0 && reset[0] > 8)
            {
                session = (int) Math.round(Math.floor(now.get(Calendar.HOUR_OF_DAY) / 2.0))*2;
                timestamp = now;
                hraci.forEach((n, h) -> h.resetVoted());
                reset[0] = 0;
            }
            else if(now.get(Calendar.MINUTE) != 0)
            {
                reset[0] = Math.min(reset[0]+1, 10);
            }

        }, 10L, getConfig().getInt("update-time")*20);

        PlaceholderAPI.registerPlaceholder(this, "voteparty_status", placeholderReplaceEvent -> votepartyStr);
        PlaceholderAPI.registerPlaceholder(this, "vote_status", event ->
        {
            if(event.isOnline() && hraci.containsKey(event.getPlayer().getName().toLowerCase()))
                return String.valueOf(hraci.get(event.getPlayer().getName().toLowerCase()).getVotes());

            return "xx";
        });

        getServer().getOnlinePlayers().forEach(p ->
                hraci.put(p.getName().toLowerCase(), new Hrac(p)));

        getLogger().info("[I] Plugin sa aktivoval");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        db.ping();

        getServer().getScheduler().runTaskLater(this, ()->
            hraci.put(event.getPlayer().getName().toLowerCase(), new Hrac(event.getPlayer())), 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        hraci.remove(event.getPlayer().getName().toLowerCase());
    }

    @EventHandler
    public void onVote(VotifierEvent event)
    {
        Vote v = event.getVote();
        db.ping();

        getServer().getScheduler().runTaskLater(this, () ->
        {
            db.addVote(v.getUsername());
            vp.add(v.getUsername().toLowerCase());
            voteparty++;

            if(voteparty >= votepartylimit)
            {
                voteparty();
            }

            votepartyStr = "§e"+voteparty+"§7/§e"+votepartylimit;
            if(hraci.containsKey(v.getUsername().toLowerCase()))
            {
                Hrac h = hraci.get(v.getUsername().toLowerCase());
                h.addVote(new Timestamp(System.currentTimeMillis()));
                Random r = new Random();
                getConfig().getMapList("vote-cmd").forEach(m ->
                {
                    try
                    {
                        if (r.nextDouble() <= new Double(m.get("perc").toString()))
                        {
                            getServer().dispatchCommand(getServer().getConsoleSender(),
                                    m.get("cmd").toString().replace("%Player%", v.getUsername()));
                        }
                    }
                    catch(Exception e)
                    {
                        getLogger().warning("[Vote][E] Neviem zacastit object e: "+e.toString());
                    }
                });

            }

            TextComponent tc = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                    String.format(Main.self.getConfig().getString("msg.bc-vote"), v.getUsername(),
                            (votepartylimit-voteparty+votepartylimit) % votepartylimit)));

            TextComponent tc2 = new TextComponent("§8(§c/vote§8)");
            tc2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vote"));
            tc.addExtra(tc2);
            getServer().spigot().broadcast(tc);
        }, 10L);
    }

    public void voteparty()
    {
        voteparty %= votepartylimit;
        Random r = new Random();
        getServer().getOnlinePlayers().forEach(p ->
        {
            if(vp.contains(p.getName().toLowerCase()))
            {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.bc-voteparty")));
                getConfig().getMapList("voteparty-cmd").forEach(m ->
                {
                        if(r.nextDouble() <= new Double(m.get("perc").toString()))
                        getServer().dispatchCommand(getServer().getConsoleSender(), m.get("cmd").toString().replace("%Player%", p.getName()));
                });
            }
        });
    }
}
