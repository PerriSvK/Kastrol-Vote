package sk.perri.kc.vote;

import be.maximvdw.placeholderapi.PlaceholderAPI;
import javafx.util.Pair;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.util.*;

public class Database
{
    private Connection conn;

    /*
     *  hlasy
     *  nick | server | time
     */

    Database() { }

    public boolean connect()
    {
        try
        {
            conn = null;
            DriverManager.setLoginTimeout(1000);
            conn = DriverManager.getConnection("jdbc:mysql://" + Main.self.getConfig().getString("db.host") +
                    ":3306/" +Main.self.getConfig().getString("db.db") + "?useSSL=no&user="+
                    Main.self.getConfig().getString("db.user")+"&password=" +
                    Main.self.getConfig().getString("db.pass") + "&useUnicode=true&characterEncoding=UTF-8" +
                    "&autoReconnect=true&failOverReadOnly=false&maxReconnects=5&connectTimeout=2000&socketTimeout=2000");
            return conn != null;
        }
        catch (Exception e)
        {
            Main.self.getLogger().warning("[Vote] [E] Neviem sa pripojit ku databaze: "+e.toString());
            return false;
        }
    }

    public Map<String, Object> hlasy(String player)
    {
        Map<String, Object> res = new HashMap<>();

        try
        {
            PreparedStatement ps2 = conn.prepareStatement("INSERT INTO kc_votes VALUES (?, 0, '2019-01-26 23:38:43') ON DUPLICATE KEY UPDATE nick=nick");
            ps2.setString(1, player);
            ps2.execute();

            PreparedStatement ps = conn.prepareStatement("SELECT * FROM kc_votes WHERE UPPER(nick) LIKE UPPER(?)");
            ps.setString(1, player);

            ResultSet rs = ps.executeQuery();

            if (rs.next())
            {
                res.put("votes", rs.getInt("votes"));
                res.put("last", rs.getTimestamp("time"));
            }
            else
            {
                res.put("votes", -1);
                res.put("last", null);
            }

            PreparedStatement ps3 = conn.prepareStatement("SELECT COUNT(*) AS offline FROM kc_hlasy WHERE UPPER(nick) LIKE UPPER(?) AND server IS NULL");
            ps3.setString(1, player);

            ResultSet rs3 = ps3.executeQuery();

            if(rs3.next())
                res.put("offline", rs3.getInt("offline"));
            else
                res.put("offline", 0);
        }
        catch(SQLException e)
        {
            Main.self.getLogger().warning("[Vote] [E] Error pri ziskavani hraca: "+e.toString());
            res.put("votes", -1);
            res.put("last", null);
            res.put("offline", 0);
        }

        return res;
    }

    void updateOfflineVote(String player, String server)
    {
        try
        {
            PreparedStatement ps = conn.prepareStatement("UPDATE kc_hlasy SET server=? WHERE UPPER(nick) LIKE UPPER(?) AND server IS NULL AND time = (SELECT time FROM (SELECT * FROM kc_hlasy WHERE UPPER(nick) LIKE UPPER(?) AND server IS NULL ORDER BY time LIMIT 1) AS tab)");
            ps.setString(1, server);
            ps.setString(2, player);
            ps.setString(3, player);
            ps.execute();
        }
        catch (Exception e)
        {
            Main.self.getLogger().info("[Vote][E] SQLException in update offline vote: "+e.getMessage());
        }
    }

    void addVote(String player, String server)
    {
        try
        {
            PreparedStatement ps = conn.prepareStatement("UPDATE kc_votes SET votes=votes+1, time=CURRENT_TIMESTAMP() WHERE nick=?");
            ps.setString(1, player);
            ps.execute();

            ps = conn.prepareStatement("INSERT INTO kc_hlasy VALUES (?, ?, DEFAULT)");
            ps.setString(1, player);
            ps.setString(2, server);
            ps.execute();
        }
        catch (Exception e)
        {
            Main.self.getLogger().warning("[Vote] [E] Error pri zapisavani hlasu: "+e.toString());
        }
    }

    void ping()
    {
        try
        {
            conn.createStatement().execute("SELECT * FROM kc_votes LIMIT 0");
        }
        catch (Exception e)
        {
            Main.self.getLogger().warning("[Vote] [W] Pripojenie ku DB pingnute: "+e.getMessage());
        }
    }

    List<Pair<String, Integer>> getTopVoters()
    {
        List<Pair<String, Integer>> res = new ArrayList<>();
        try
        {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM kc_votes ORDER BY votes DESC LIMIT 10");
            while(rs.next())
            {
                res.add(new Pair<>(rs.getString("nick"), rs.getInt("votes")));
            }
        }
        catch (Exception e)
        {
            Main.self.getLogger().warning("[Vote] [E] Error pri top voters: "+e.getMessage());
        }

        return res;
    }

    List<Pair<String, Integer>> getTopVotersMonth()
    {
        List<Pair<String, Integer>> res = new ArrayList<>();
        try
        {
            Calendar now = Calendar.getInstance();
            String dd = now.get(Calendar.YEAR)+"-"+now.get(Calendar.MONTH)+"-01 00:00:01";
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(nick) AS votes, nick FROM kc_hlasy WHERE time >= ? GROUP BY nick ORDER BY COUNT(nick) DESC LIMIT 10");
            ps.setString(1, dd);
            ResultSet rs = ps.executeQuery();
            while(rs.next())
            {
                res.add(new Pair<>(rs.getString("nick"), rs.getInt("votes")));
            }
        }
        catch (Exception e)
        {
            Main.self.getLogger().warning("[Vote] [E] Error pri top voters month: "+e.getMessage());
        }

        return res;
    }

    Map<String, Integer> getPlayerStats(String nick)
    {
        Map<String, Integer> res = new HashMap<>();
        Map<String, String> q = new HashMap<>();
        Calendar now = Calendar.getInstance();
        q.put("month", "MONTH(time) = MONTH(CURRENT_DATE()) AND YEAR(time) = YEAR(CURRENT_DATE())");
        q.put("week", "yearweek(time) = yearweek(CURRENT_DATE()) AND YEAR(time) = YEAR(CURRENT_DATE())");
        q.put("day", "DAY(time) = DAY(CURRENT_DATE()) AND YEAR(time) = YEAR(CURRENT_DATE())");

        q.forEach((k, v) ->
        {
            try
            {
                ResultSet rs = conn.createStatement().executeQuery(
                        "SELECT COUNT(nick) FROM kc_hlasy WHERE "+v+" AND UPPER(nick) LIKE UPPER('"+nick+"') GROUP BY nick LIMIT 1");
                if(rs.next())
                {
                    res.put(k, rs.getInt(1));
                    /*if(Main.self.getServer().getPlayer(nick).isOp())
                    {
                        Main.self.getLogger().info("[Vote][D] Hrac "+nick+" k: "+k+" v: "+v+" val: "+rs.getInt(1));
                    }*/
                }
                else
                    res.put(k, 0);
            }
            catch (Exception e)
            {
                Main.self.getLogger().warning("[Vote][E] SQLError - getPlayerStats - "+k+": "+e.toString());
                res.put(k, -1);
            }
        });

        try
        {
            ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(nick) FROM kc_hlasy WHERE server IS NULL AND UPPER(nick) LIKE UPPER('"+nick+"') GROUP BY nick LIMIT 1");
            if(rs.next())
            {
                res.put("offline", rs.getInt(1));
            }
            else
                res.put("offline", 0);
        }
        catch (Exception e)
        {
            Main.self.getLogger().warning("[Vote][E] SQLError - getPlayerStats - offline: "+e.toString());
            res.put("offline", -1);
        }

        return res;
    }
}
