package sk.perri.kc.vote;

import be.maximvdw.placeholderapi.PlaceholderAPI;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import javafx.util.Pair;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
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
    HashMap<String, Hrac> hraci = new HashMap<>();
    Vector<String> vp = new Vector<>();
    static List<Pair<String, Integer>> topVoters = new ArrayList<>();
    static List<Pair<String, Integer>> topVotersMonth = new ArrayList<>();
    int votepartylimit = 100;
    int voteparty = 0;
    String votepartyStr = "§e0§7/§e150";

    @Override
    public void onEnable()
    {
        self = this;
        if (!getDataFolder().exists())
            getDataFolder().mkdir();

        getConfig().options().copyDefaults(true);
        saveConfig();

        CmdExecutor ce = new CmdExecutor();
        getCommand("vote").setExecutor(ce);
        getCommand("hlasy").setExecutor(ce);
        getCommand("voteparty").setExecutor(ce);
        getCommand("fakevote").setExecutor(ce);
        getCommand("offlinevote").setExecutor(ce);
        getCommand("voteinfo").setExecutor(ce);
        getServer().getPluginManager().registerEvents(this, this);

        db = new Database();
        db.connect();

        Calendar nowt = Calendar.getInstance();
        session = (int) Math.round(Math.floor(nowt.get(Calendar.HOUR_OF_DAY) / 2.0)) * 2;
        timestamp = Calendar.getInstance();
        timestamp.set(Calendar.HOUR_OF_DAY, session);
        timestamp.set(Calendar.MINUTE, 0);
        timestamp.set(Calendar.SECOND, 0);

        votepartylimit = getConfig().getInt("voteparty");
        int[] reset = {10};

        topVoters = db.getTopVoters();
        topVotersMonth = db.getTopVotersMonth();

        // register top voters placeholder
        registerPlaceholders();

        // Jebat - nefunguje ten plugin
        /*try
        {
            Plugin leaderheads = Bukkit.getPluginManager().getPlugin("LeaderHeads");
            if (leaderheads != null)
            {
                new LeaderClass();
                new LeaderMonthClass();
                getLogger().info("LeaderHeads data collections registered");
            }
            else
                getLogger().info("LeaderHeads plugin not found");
        }
        catch (Exception e)
        {
            getLogger().info("LeaderHeads plugin is null");
        }*/

        Bukkit.getServer().getScheduler().runTaskTimer(this, () ->
        {
            Calendar now = Calendar.getInstance();

            if (now.get(Calendar.HOUR_OF_DAY) % 2 == 0 && now.get(Calendar.MINUTE) == 0 && reset[0] > 8)
            {
                session = (int) Math.round(Math.floor(now.get(Calendar.HOUR_OF_DAY) / 2.0)) * 2;
                timestamp = now;
                hraci.forEach((n, h) -> h.resetVoted());
                reset[0] = 0;
                db.ping();
                //update placeholder;
                getServer().getScheduler().runTaskLater(this, () ->
                {
                    topVoters = db.getTopVoters();
                    topVotersMonth = db.getTopVotersMonth();
                    registerPlaceholders();
                }, 3L);
            }
            else if (now.get(Calendar.MINUTE) != 0)
            {
                reset[0] = Math.min(reset[0] + 1, 10);
            }

        }, 10L, getConfig().getInt("update-time") * 20);

        PlaceholderAPI.registerPlaceholder(this, "voteparty_status", placeholderReplaceEvent -> votepartyStr);
        PlaceholderAPI.registerPlaceholder(this, "vote_status", event ->
        {
            if (event.isOnline() && hraci.containsKey(event.getPlayer().getName().toLowerCase()))
                return String.valueOf(hraci.get(event.getPlayer().getName().toLowerCase()).getVotes());

            return "xx";
        });

        getServer().getOnlinePlayers().forEach(p ->
                hraci.put(p.getName().toLowerCase(), new Hrac(p)));

        getLogger().info("[I] Plugin sa aktivoval");
    }

    void registerPlaceholders()
    {
        for(int i = 0; i < 10; i++)
        {
            int finalI = i;
            PlaceholderAPI.registerPlaceholder(this, "vote_top_"+i+"_nick", event -> topVoters.get(finalI).getKey());
            PlaceholderAPI.registerPlaceholder(this, "vote_top_"+i+"_votes", event -> Integer.toString(topVoters.get(finalI).getValue()));
            PlaceholderAPI.registerPlaceholder(this, "vote_top_month_"+i+"_nick", event -> topVotersMonth.get(finalI).getKey());
            PlaceholderAPI.registerPlaceholder(this, "vote_top_month_"+i+"_votes", event -> Integer.toString(topVotersMonth.get(finalI).getValue()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        db.ping();

        getServer().getScheduler().runTaskLater(this, () ->
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
            db.addVote(v.getUsername(), getServer().getPlayer(v.getUsername()) != null ? getServer().getServerName() : null);
            vp.add(v.getUsername().toLowerCase());
            voteparty++;

            if (voteparty >= votepartylimit)
            {
                voteparty();
            }

            votepartyStr = "§e" + voteparty + "§7/§e" + votepartylimit;
            if (hraci.containsKey(v.getUsername().toLowerCase()))
            {
                Hrac h = hraci.get(v.getUsername().toLowerCase());
                h.addVote(new Timestamp(System.currentTimeMillis()));
                h.getPlayer().playSound(h.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, 50, 1);
                h.getPlayer().playSound(h.getPlayer().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 50, 1.2f);
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
                    catch (Exception e)
                    {
                        getLogger().warning("[Vote][E] Neviem zacastit object e: " + e.toString());
                    }
                });

            }

            TextComponent tc = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                    String.format(Main.self.getConfig().getString("msg.bc-vote"), v.getUsername(),
                            (votepartylimit - voteparty + votepartylimit) % votepartylimit)));

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
            if (vp.contains(p.getName().toLowerCase()))
            {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.bc-voteparty")));
                getConfig().getMapList("voteparty-cmd").forEach(m ->
                {
                    if (r.nextDouble() <= new Double(m.get("perc").toString()))
                        getServer().dispatchCommand(getServer().getConsoleSender(), m.get("cmd").toString().replace("%Player%", p.getName()));
                });
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 50, 1.8f);
            }
            else
            {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.bc-voteparty-no")));
            }
        });
    }

    @EventHandler
    public void onInvInteract(InventoryInteractEvent event)
    {
        if(event.getInventory()!= null && event.getInventory().getName() != null &&
                event.getInventory().getName().toLowerCase().contains("offline hlasy"))
        {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event)
    {

        if(event.getClickedInventory()!= null && event.getClickedInventory().getName() != null &&
                (event.getClickedInventory().getName().toLowerCase().contains("offline hlasy") ||
                        event.getClickedInventory().getName().toLowerCase().contains("info o hlasoch")))
        {
            event.setCancelled(true);

            if(event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta() &&
                    event.getCurrentItem().getItemMeta().hasDisplayName() &&
                    event.getCurrentItem().getItemMeta().getDisplayName().toLowerCase().contains("klikni"))
            {
                db.ping();

                Bukkit.getScheduler().runTaskLater(this, () ->
                {
                    event.getWhoClicked().closeInventory();
                    if(hraci.containsKey(event.getWhoClicked().getName().toLowerCase()))
                    {
                        Hrac h = hraci.get(event.getWhoClicked().getName().toLowerCase());
                        int opakovat = event.getClick().isShiftClick() ? h.getOfflineVotes() : 1;

                        for(int i = 0; i < opakovat; i++)
                        {
                            h.setOfflineVotes(h.getOfflineVotes() - 1);
                            Random r = new Random();
                            h.getPlayer().playSound(h.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, 50, 1);
                            h.getPlayer().playSound(h.getPlayer().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 50, 1.2f);
                            getConfig().getMapList("vote-cmd").forEach(m ->
                            {
                                try
                                {
                                    if (r.nextDouble() <= new Double(m.get("perc").toString()))
                                    {
                                        getServer().dispatchCommand(getServer().getConsoleSender(),
                                                m.get("cmd").toString().replace("%Player%", event.getWhoClicked().getName()));
                                    }
                                }
                                catch (Exception e)
                                {
                                    getLogger().warning("[Vote][E] Neviem zacastit object e: " + e.toString());
                                }
                            });
                            db.updateOfflineVote(event.getWhoClicked().getName(), getServer().getServerName());
                        }
                    }
                }, 3L);
            }
        }
    }
}
