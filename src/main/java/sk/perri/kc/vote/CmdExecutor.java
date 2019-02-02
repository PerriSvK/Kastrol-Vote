package sk.perri.kc.vote;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Random;

public class CmdExecutor implements CommandExecutor
{

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if(!(sender instanceof Player) || !Main.self.hraci.containsKey(sender.getName().toLowerCase()))
            return false;

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
                Random r = new Random();
                Main.self.getConfig().getMapList("vote-cmd").forEach(m ->
                {
                    try
                    {
                        double d = r.nextDouble();
                        Main.self.getLogger().info("[Vote][D] Random number: "+d+" limit: "+new Double(m.get("perc").toString()));
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
            if(sender.isOp() && args.length == 1 && args[0].equalsIgnoreCase("reload"))
            {
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
                else
                {
                    sender.sendMessage(String.format(ChatColor.translateAlternateColorCodes('&', l), h.getVotes()));
                }
            });
        }
        else if(cmd.getName().equalsIgnoreCase("hlasy"))
        {
            Main.self.getConfig().getStringList("msg.votes").forEach(l ->
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format(l, h.getVotes()))));
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

        return true;
    }
}
