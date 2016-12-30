package arbitrage;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.sql.*;
import java.util.Properties;

/**
 * Created by Miller on 2016/12/24.
 */
public class ConnectionUtil {
    private static Connection conn;
    private static Long lastTime;

    public static Connection getConnection() {
        try {
            if (conn == null) {
                conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/price_margin", "root", "24646464qq");
                conn.setAutoCommit(true);
                lastTime = System.currentTimeMillis();
                new checkConnection().start();
            }
            if (conn.isClosed()){
                conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/price_margin", "root", "24646464qq");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("数据库连接获取失败！");
        }
        return conn;
    }

    private static class checkConnection extends Thread{
        @Override
        public void run() {
            while (true){
                try {
                    Thread.sleep(1000*60*60*6);//睡眠6小时
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
               Connection connection = getConnection();
                try {
                    Statement statement = conn.createStatement();
                    statement.execute("SELECT gains from initialization_data limit 1");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
