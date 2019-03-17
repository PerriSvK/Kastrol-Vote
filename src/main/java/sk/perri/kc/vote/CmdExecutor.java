package sk.perri.kc.vote;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CmdExecutor implements CommandExecutor
{

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if(!(sender instanceof Player))
            return false;

        if(!Main.self.hraci.containsKey(sender.getName().toLowerCase()))
        {
            sender.sendMessage("§cNastala chyba");
            Main.self.db.ping();
            Main.self.getServer().getScheduler().runTaskLater(Main.self, () -> Main.self.hraci.put(sender.getName().toLowerCase(), new Hrac((Player) sender)), 2);
            Main.self.getServer().getScheduler().runTaskLater(Main.self, () -> this.onCommand(sender, cmd, label, args), 4);
            return false;
        }

        Hrac h = Main.self.hraci.get(sender.getName().toLowerCase());

        if(cmd.getName().equalsIgnoreCase("fakevote") && sender.isOp())
        {
            Main.self.voteparty++;

            if(Main.self.voteparty >= Main.self.votepartylimit)
            {
                Main.self.voteparty();
            }

            Main.self.votepartyStr = "§e"+Main.self.voteparty+"§7/§e"+Main.self.votepartylimit;
            if(Main.self.hraci.containsKey(sender.getName().toLowerCase()))
            {
                Main.self.vp.add(sender.getName().toLowerCase());
                Random r = new Random();
                h.getPlayer().playSound(h.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, 50, 1);
                h.getPlayer().playSound(h.getPlayer().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 50, 1.2f);
                Main.self.getConfig().getMapList("vote-cmd").forEach(m ->
                {
                    try
                    {
                        double d = r.nextDouble();
                       // Main.self.getLogger().info("[Vote][D] Random number: "+d+" limit: "+new Double(m.get("perc").toString()));
                        if (d <= new Double(m.get("perc").toString()))
                        {
                            Main.self.getServer().dispatchCommand(Main.self.getServer().getConsoleSender(),
                                    m.get("cmd").toString().replace("%Player%", sender.getName()));
                        }
                    }
                    catch(Exception e)
                    {
                        Main.self.getLogger().warning("[Vote][E] Neviem zacastit object e: "+e.toString());
                    }
                });

            }

            TextComponent tc = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                    String.format(Main.self.getConfig().getString("msg.bc-vote"), sender.getName(),
                            (Main.self.votepartylimit-Main.self.voteparty+Main.self.votepartylimit) % Main.self.votepartylimit)));

            TextComponent tc2 = new TextComponent("§8(§c/vote§8)");
            tc2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vote"));
            tc.addExtra(tc2);
            Main.self.getServer().spigot().broadcast(tc);
        }
        else if(cmd.getName().equalsIgnoreCase("vote"))
        {
            //Main.self.getLogger().info("[Vote][D] Vote cmd! isOP? "+sender.isOp()+" args lenght: "+args.length+" args: "+String.join(", ", args));
            if(sender.isOp() && args.length == 1 && args[0].equalsIgnoreCase("reload"))
            {
                sender.sendMessage("§7Reloadujem config");
                Main.self.reloadConfig();
                sender.sendMessage("§7Config reloadovany");
                return true;
            }

            TextComponent session;
            TextComponent sprava;
            if(h.voted())
            {
                sprava = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                        Main.self.getConfig().getString("msg.already-voted")));
                sprava.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.translateAlternateColorCodes('&',
                                Main.self.getConfig().getString("msg.voted-hover"))).create()));
                session = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                        String.format(Main.self.getConfig().getString("msg.session"), (Main.self.session + 2) % 24,
                                (Main.self.session + 4) % 24)));
            }
            else
            {
                sprava = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                        Main.self.getConfig().getString("msg.address")));
                sprava.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.translateAlternateColorCodes('&',
                                Main.self.getConfig().getString("msg.address-hover"))).create()));
                sprava.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Main.self.getConfig().getString("address")+sender.getName()));
                session = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                        String.format(Main.self.getConfig().getString("msg.session"),  Main.self.session,
                                (Main.self.session + 2) % 24)));
            }

            Main.self.getConfig().getStringList("msg.vote").forEach(l ->
            {
                TextComponent tc = new TextComponent();
                if(l.contains("%sprava%"))
                {
                    String[] s = ChatColor.translateAlternateColorCodes('&', l).split("%sprava%");
                    tc.addExtra(s[0]);
                    tc.addExtra(sprava);
                    tc.addExtra(" ");
                    tc.addExtra(session);
                    if(s.length > 1)
                        tc.addExtra(s[1]);
                    sender.spigot().sendMessage(tc);
                }
                else if(l.contains("%offline%"))
                {
                    if(h.getOfflineVotes() > 0)
                    {
                        TextComponent tt = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                                l.replace("%offline%", Main.self.getConfig().getString("msg.offline-vote").replace("%d",
                                        Integer.toString(h.getOfflineVotes())))));
                        tt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/offvote"));
                        sender.spigot().sendMessage(tt);
                    }
                }
                else
                {
                    sender.sendMessage(String.format(ChatColor.translateAlternateColorCodes('&', l), h.getVotes()));
                }
            });
        }
        else if(cmd.getName().equalsIgnoreCase("hlasy"))
        {
            Main.self.getConfig().getStringList("msg.votes").forEach(l ->
            {
                if(l.contains("%offline%"))
                {
                    if (h.getOfflineVotes() > 0)
                    {
                        TextComponent tt = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                                l.replace("%offline%", Main.self.getConfig().getString("msg.offline-vote").replace("%d",
                                        Integer.toString(h.getOfflineVotes())))));
                        tt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/offvote"));
                        sender.spigot().sendMessage(tt);
                    }
                }
                else
                {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format(l, h.getVotes())));
                }
            });
        }
        else if(cmd.getName().equalsIgnoreCase("voteparty"))
        {
            if(sender.isOp() && args.length == 1)
            {
                Main.self.voteparty = Integer.valueOf(args[0]);
                if(Main.self.voteparty >= Main.self.votepartylimit)
                {
                    Main.self.voteparty();
                    Main.self.getLogger().info("[Vote][I] Forced voteparty");
                 }

                Main.self.votepartyStr = "§e"+Main.self.voteparty+"§7/§e"+Main.self.votepartylimit;
                Main.self.getLogger().info("[Vote][D] Nastavujem voteparty!");
            }

            Main.self.getConfig().getStringList("msg.voteparty").forEach(l ->
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', l.replace("%vp%", Main.self.votepartyStr))));
        }
        else if(cmd.getName().equalsIgnoreCase("offlinevote"))
        {
            Inventory inv = Bukkit.createInventory(null, 27, "§c§lOffline hlasy");

            ItemStack is = new ItemStack(Material.STAINED_CLAY, Math.max(1, h.getOfflineVotes()),  h.getOfflineVotes() > 0 ? (byte) 5 : (byte) 14);
            ItemMeta im = is.getItemMeta();
            if(h.getOfflineVotes() > 0)
            {
                im.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        Main.self.getConfig().getString("msg.offline-vote-block-title")));
                List<String> ll = new ArrayList<>();
                Main.self.getConfig().getStringList("msg.offline-vote-block-lore").forEach(l ->
                        ll.add(ChatColor.translateAlternateColorCodes('&',
                                l.replace("%d", Integer.toString(h.getOfflineVotes())))));
                im.setLore(ll);
            }
            else
            {
                im.setDisplayName(ChatColor.translateAlternateColorCodes('&', Main.self.getConfig().getString("msg.offline-vote-block-title-no")));
            }
            is.setItemMeta(im);
            inv.setItem(13, is);
            Bukkit.getScheduler().runTaskLater(Main.self, () -> h.getPlayer().openInventory(inv), 3L);
        }
        else if(cmd.getName().equalsIgnoreCase("voteinfo"))
        {
            Main.self.db.ping();

            Bukkit.getScheduler().runTaskLater(Main.self, ()->
            {
                Map<String, Integer> stats = Main.self.db.getPlayerStats(sender.getName());
                stats.put("total", h.getVotes());
                if(stats == null || stats.size() < 4)
                {
                   sender.sendMessage("&cNepodarilo se najst hrace!");
                   return;
                }

                ((Player) sender).openInventory(createInfoInv(stats));

            }, 2L);
        }

        return true;
    }

    public Inventory createInfoInv(Map<String, Integer> data)
    {
        Inventory inv = Bukkit.createInventory(null, 27, "§3§lInfo o hlasoch");
        for(int i = 0; i < inv.getSize(); i++)
        {
            inv.setItem(i, new ItemStack(Material.STAINED_GLASS_PANE, 1, (byte) 15));
        }

        // Celkovo
        ItemStack celkovo = new ItemStack(Material.STAINED_CLAY, 1, (byte) 5);
        ItemMeta imcelk = celkovo.getItemMeta();
        imcelk.setDisplayName("§a§lPOCET HLASU CELKEM");
        imcelk.setLore(Arrays.asList("", "§7Celkem mas §c"+data.get("total")+" §7hlasu"));
        celkovo.setItemMeta(imcelk);
        inv.setItem(10, celkovo);

        // Mesacne
        ItemStack mesacne = new ItemStack(Material.STAINED_CLAY, 1, (byte) 4);
        ItemMeta immes = mesacne.getItemMeta();
        immes.setDisplayName("§e§lPOCET HLASU MESICNE");
        immes.setLore(Arrays.asList("", "§7Celkem mas §c"+data.get("month")+" §7hlasu tento mesic"));
        mesacne.setItemMeta(immes);
        inv.setItem(12, mesacne);

        // Tyzdenne
        ItemStack tyzd = new ItemStack(Material.STAINED_CLAY, 1, (byte) 1);
        ItemMeta imty = tyzd.getItemMeta();
        imty.setDisplayName("§6§lPOCET HLASU TYDNE");
        imty.setLore(Arrays.asList("", "§7Celkem mas §c"+data.get("week")+" §7hlasu tento tyden"));
        tyzd.setItemMeta(imty);
        inv.setItem(14, tyzd);

        // Denne
        ItemStack den = new ItemStack(Material.STAINED_CLAY, 1, (byte) 14);
        ItemMeta imd = den.getItemMeta();
        imd.setDisplayName("§c§lPOCET HLASU ZA DEN");
        imd.setLore(Arrays.asList("", "§7Celkem mas §c"+data.get("day")+" §7hlasu za den"));
        den.setItemMeta(imd);
        inv.setItem(16, den);

        // Offline
        ItemStack off = new ItemStack(Material.STAINED_CLAY, 1, (byte) 3);
        ItemMeta imoff = off.getItemMeta();
        imoff.setDisplayName("§3§lPOCET OFFLINE HLASU");
        imoff.setLore(Arrays.asList("", "§7Celkem mas §c"+data.get("offline")+" §7offline hlasu"));
        off.setItemMeta(imoff);
        inv.setItem(22, off);

        return inv;
    }
}
