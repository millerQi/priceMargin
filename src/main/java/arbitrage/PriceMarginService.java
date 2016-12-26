package arbitrage;

import model.priceMargin.Order;
import model.priceMargin.OrderGain;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;

/**
 * 保存交易信息相关操作
 * Created by Miller on 2016/12/24.
 */
public class PriceMarginService {
    //return id
    public Long saveOrder(Order order) {
        StringBuilder sql = new StringBuilder("insert into `order` (trade_center,trade_direction,ticker_price," +
                "amount,deal_amount,deal_price,create_time) VALUES (")
                .append("'").append(order.getTradeCenter() == null ? "" : order.getTradeCenter()).append("',")
                .append("'").append(order.getTradeDirection() == null ? "" : order.getTradeDirection()).append("',")
                .append("'").append(order.getTickerPrice() == null ? BigDecimal.ZERO : order.getTickerPrice()).append("',")
                .append("'").append(order.getAmount() == null ? BigDecimal.ZERO : order.getAmount()).append("',")
                .append("'").append(order.getDealAmount() == null ? BigDecimal.ZERO : order.getDealAmount()).append("',")
                .append("'").append(order.getDealPrice() == null ? BigDecimal.ZERO : order.getDealPrice()).append("',")
                .append("'").append(order.getCreateTime() == null ? new Timestamp(new Date().getTime()) : order.getCreateTime()).append("')");
        return getIdAndExecute(ConnectionUtil.getConnection(), sql.toString());
    }

    private Long getIdAndExecute(Connection conn, String sql) {
        Statement statement = null;
        ResultSet rs;
        Long id;
        try {
            statement = conn.createStatement();
            statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            rs = statement.getGeneratedKeys();
            rs.next();
            id = rs.getLong(1);
        } catch (SQLException e) {
            try {
                if (statement != null)
                    statement.close();
                conn.close();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
            throw new RuntimeException("order save failed ! SQLException");
        }
        return id;
    }

    public Long saveOrderGain(OrderGain orderGain) {
        StringBuilder sql = new StringBuilder("insert into `order_gain` (sell_order_id,buy_order_id,gains,ok_free_price,hb_free_price) values(")
                .append("'").append(orderGain.getSellOrderId() == null ? -1L : orderGain.getSellOrderId()).append("',")
                .append("'").append(orderGain.getBuyOrderId() == null ? -1L : orderGain.getBuyOrderId()).append("',")
                .append("'").append(orderGain.getGains() == null ? BigDecimal.ZERO : orderGain.getGains()).append("',")
                .append("'").append(orderGain.getOkFreePrice() == null ? BigDecimal.ZERO : orderGain.getOkFreePrice()).append("',")
                .append("'").append(orderGain.getHbFreePrice() == null ? BigDecimal.ZERO : orderGain.getHbFreePrice()).append("')");
        return getIdAndExecute(ConnectionUtil.getConnection(), sql.toString());
    }

    public static void main(String[] args) {
        PriceMarginService service = new PriceMarginService();
        OrderGain orderGain = new OrderGain();
        orderGain.setBuyOrderId(1L);
        orderGain.setSellOrderId(2L);
        orderGain.setGains(BigDecimal.valueOf(21));
        orderGain.setOkFreePrice(BigDecimal.valueOf(341));
        System.out.println(service.saveOrderGain(orderGain));
    }
}
