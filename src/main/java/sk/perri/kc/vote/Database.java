package sk.perri.kc.vote;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

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
        }
        catch(SQLException e)
        {
            Main.self.getLogger().warning("[Vote] [E] Error pri ziskavani hraca: "+e.toString());
            res.put("votes", -1);
            res.put("last", null);
        }

        return res;
    }

    void addVote(String player)
    {
        try
        {
            PreparedStatement ps = conn.prepareStatement("UPDATE kc_votes SET votes=votes+1, time=CURRENT_TIMESTAMP() WHERE nick=?");
            ps.setString(1, player);
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
}
