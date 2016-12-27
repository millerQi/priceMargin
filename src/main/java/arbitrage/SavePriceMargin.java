package arbitrage;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import huobi.HuobiService;
import model.priceMargin.Order;
import model.priceMargin.OrderGain;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import strategy.ApiResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by tonyqi on 16-12-20.
 */
public class SavePriceMargin {

    private static HuobiService huobiApi = new HuobiService();

    private static Long lastTime = System.currentTimeMillis();

    public static BigDecimal priceMargin = BigDecimal.valueOf(0.8);//程序启动时赋值

    public static BigDecimal tradeAmount = BigDecimal.valueOf(0.1);//程序启动后检测完账号持币情况后赋值

    private static String priceM;//比较价差 okcoin价高且价差大于priceMargin为1 火币大为-1  交易前赋值

    public static String hasCoin = "-1";//启动时检查账户并赋值,1为okcoin持币 0 为都不持币 -1为火币网持币 交易后更改值

    private static Log log = LogFactory.getLog(SavePriceMargin.class);

    private static String a = "1";

    private static String b = "-1";

    private static Lock lock = new ReentrantLock(true);

    private static String okApiKey = "8a134f3f-51b8-4993-a916-bad635fdaf15";

    private static String okSecretKey = "D67E022D0242FC3FE737C78BF8B546E4";

    private static ExecutorService cachedThreadPool = Executors.newSingleThreadExecutor();

    private static PriceMarginService priceMarginService = new PriceMarginService();

    private static long tickerDepthTime = 0;

    //比较价格,3s比较一次
    public static void startComparePrice(BigDecimal okcoinPrice) {
        Long now;
        if ((now = System.currentTimeMillis()) - lastTime < 3000)
            return;
        lastTime = now;
        lock.lock();
        try {
            tickerDepthTime = System.currentTimeMillis();
            BigDecimal huobiPrice = huobiApi.ticker();//获取火币lastPrice
            if (huobiPrice == null) {
                log.error("huobi ticker 获取失败！");
                return;
            }
            BigDecimal pm = okcoinPrice.subtract(huobiPrice);//差价
            if (pm.compareTo(BigDecimal.ZERO) >= 0) {//价差大于等于0
                if (pm.compareTo(priceMargin) <= 0)//小于等于价差,不满足搬砖条件
                    return;
                /**-----------搬砖-----------*/
                priceM = a;
                trade(okcoinPrice, huobiPrice);
            } else {//价差小于0
                BigDecimal temp = pm.subtract(pm.multiply(BigDecimal.valueOf(2)));//价差-2*价差
                if (temp.compareTo(priceMargin) <= 0)//小于等于价差,不满足搬砖条件
                    return;
                /**-----------搬砖-----------*/
                priceM = b;
                trade(okcoinPrice, huobiPrice);
            }
        } finally {
            lock.unlock();
        }
    }

    private static BigDecimal gains = BigDecimal.ZERO;//总盈利

    /**
     * 搬砖套利
     */
    private static void trade(BigDecimal okPrice, BigDecimal hbPrice) {
        if (priceM.equals(hasCoin)) {//符合交易条件
            boolean checkDepth = checkDepth(priceM);
            log.info("从抓取huobi Ticker到用时:" + (tickerDepthTime - System.currentTimeMillis()));
            if (!checkDepth) {
                log.warn("|||||||||||||||||||||||||||||||||||||深度不符合要求||||||||||||||||||||||||||||||||||||||||||||||");
                return;
            }
            String amount = String.valueOf(tradeAmount);
            long okTID = 0, hbTID = 0;
            String hbP, okP;
            /**------okcoin卖 huobi买-------*/
            if (priceM.equals(a) && hasCoin.equals(a)) {//1-okcoin价高
                log.info("--------------okcoin价高开始交易--------------");
                String result = huobiApi.buy(1, hbP = getMuchBigPrice(hbPrice), amount, null, "buy");//huobi通过rest接口下单
                ApiResult.Trade huobiTrade = jsonHandle(result);
                if (huobiTrade.getResult().equals("false")) {
                    log.error("程序异常!火币接口下单失败,方向buy,单价" + hbP + ",订单数量" + amount);
                    System.exit(0);
                }
                hbTID = huobiTrade.getOrderId();
//                log.info("=========huobi trade success，buy：" + amount + " btc，tid :" + hbTID + "=========");
                ApiResult.Trade trade = ApiResult.getTradeRet(okApiKey, okSecretKey, "btc_cny", okP = getMuchSmallPrice(okPrice), amount, "sell");//okcoin通过webSocket下单
                if (trade == null) {
                    log.error("程序异常!OKCOIN接口下单失败,方向sell,单价" + okP + ",订单数量" + amount);
                    System.exit(0);
                }
                hasCoin = b;
                okTID = trade.getOrderId();
//                log.info("okcoin trade success，sell：" + amount + " btc，tid :" + okTID);
            }
            /**------okcoin买 huobi卖-------*/
            else if (priceM.equals(b) && hasCoin.equals(b)) {//-1huobi价高
                log.info("--------------huobi价高开始交易--------------");
                String result = huobiApi.sell(1, hbP = getMuchSmallPrice(hbPrice), amount, null, "sell");
                ApiResult.Trade huobiTrade = jsonHandle(result);
                if (huobiTrade.getResult().equals("false")) {
                    log.error("程序异常!火币接口下单失败,方向sell,单价" + hbP + ",订单数量" + amount);
                    System.exit(0);
                }
                hbTID = huobiTrade.getOrderId();
//                log.info("=========huobi trade success，sell：" + amount + " btc，tid :" + hbTID + "=========");
                ApiResult.Trade trade = ApiResult.getTradeRet(okApiKey, okSecretKey, "btc_cny", okP = getMuchBigPrice(okPrice), amount, "buy");
                if (trade == null) {
                    log.error("程序异常!OKCOIN接口下单失败,方向buy,单价" + okP + ",订单数量" + amount);
                    System.exit(0);
                }
                hasCoin = a;
                okTID = trade.getOrderId();
//                log.info("okcoin trade success，buy：" + amount + " btc，tid :" + okTID);
            }
            reckonGains(okTID, hbTID, okPrice, hbPrice, priceM);
        } else {
//            log.info("*****Trade failed okPrice = " + okPrice + " , hbPrice = " + hbPrice + " , hasCoin = " + hasCoin + "*****");
        }
    }

    private static boolean checkDepth(String priceM) {
        Map<String, BigDecimal[]> huobiDepth = huobiApi.depth();
        if (huobiDepth == null) {
            log.error("深度获取失败");
            return false;
        }
        BigDecimal[] hbAsks = huobiDepth.get("hbAsks");
        BigDecimal[] hbBids = huobiDepth.get("hbBids");
        BigDecimal[] okAsks = huobiDepth.get("okAsks");
        BigDecimal[] okBids = huobiDepth.get("okBids");
        if (priceM.equals(a)) {//ok价高
            if (okBids[0].subtract(hbAsks[0]).compareTo(priceMargin.divide(BigDecimal.valueOf(2), 2)) >= 0)//比较以okcoin的深度卖出，huobi买入是否有利润
                return true;
            if (okBids[1].compareTo(tradeAmount) == -1
                    || hbAsks[1].compareTo(tradeAmount) == -1)//深度数量不足
                return false;
        } else if (priceM.equals(b)) {
            if (hbBids[0].subtract(okAsks[0]).compareTo(priceMargin) >= 0)//比较以huobi的深度卖出，okcoin买入是否有利润
                return true;
            if (hbBids[1].compareTo(tradeAmount) == -1
                    || okAsks[1].compareTo(tradeAmount) == -1)//深度数量不足
                return false;
        }
        return false;
    }

    private static void reckonGains(long okTID, long hbTID, BigDecimal okPrice, BigDecimal hbPrice, String priceM) {
        cachedThreadPool.execute(new ReckonGains(okTID, hbTID, okPrice, hbPrice, priceM));
    }

    private static class ReckonGains implements Runnable {
        private long okTID;
        private long hbTID;
        private BigDecimal okPrice;
        private BigDecimal hbPrice;
        private String priceM;

        ReckonGains(long okTID, long hbTID, BigDecimal okPrice, BigDecimal hbPrice, String priceM) {
            this.okTID = okTID;
            this.hbTID = hbTID;
            this.okPrice = okPrice;
            this.hbPrice = hbPrice;
            this.priceM = priceM;
        }

        public void run() {
            try {
                Thread.sleep(888);//okcoin和huobi订单信息延时
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String ret = huobiApi.getOrderInfo(1, hbTID, "order_info");
            ApiResult.OrderInfo hbOrderInfo = handleOrderInfo(ret);
            ApiResult.OrderInfo okOrderInfo = ApiResult.getOrderInfoRet(okApiKey, okSecretKey, "btc_cny", String.valueOf(okTID));
            BigDecimal okAvgPrice = okOrderInfo.getAvgPrice();
            BigDecimal hbAvgPrice = hbOrderInfo.getAvgPrice();
            BigDecimal expectGains, realGains;
            if (priceM.equals(a)) {
                expectGains = tradeAmount.multiply(okPrice.subtract(hbPrice));
                realGains = tradeAmount.multiply(okAvgPrice.subtract(hbAvgPrice));
            } else {
                expectGains = tradeAmount.multiply(hbPrice.subtract(okPrice));
                realGains = tradeAmount.multiply(hbAvgPrice.subtract(okAvgPrice));
            }
            Long hbId = saveOrder(hbOrderInfo, "huobi", hbPrice);
            Long okId = saveOrder(okOrderInfo, "okcoin", okPrice);
            saveOrderGain(hbId, okId, realGains, priceM);
            log.warn("搬砖完成--起始价格--okcoin:" + okPrice + ",huobi:" + hbPrice + "--实际成交价--okcoin:" + okAvgPrice + ",huobi:" + hbAvgPrice);
            BigDecimal huadian = expectGains.subtract(realGains);
            if (huadian.compareTo(BigDecimal.ZERO) == -1)
                huadian = huadian.subtract(huadian.multiply(BigDecimal.valueOf(2)));
            log.warn("==========预计盈利:" + expectGains + " , 实际盈利:" + realGains + " ，滑点" + huadian + "==========");
            gains = gains.add(realGains);
            log.warn("==================================总盈利:" + gains + "====================================");
        }

        private void saveOrderGain(Long hbId, Long okId, BigDecimal realGains, String priceM) {
            OrderGain orderGain = new OrderGain();
            Long buyId, sellId;
            if (priceM.equals(a)) {//okcoin为卖
                sellId = okId;
                buyId = hbId;
            } else {
                sellId = hbId;
                buyId = okId;
            }
            orderGain.setSellOrderId(sellId);
            orderGain.setBuyOrderId(buyId);
            orderGain.setGains(realGains);
            priceMarginService.saveOrderGain(orderGain);
        }

        private Long saveOrder(ApiResult.OrderInfo orderInfo, String center, BigDecimal price) {
            Order order = new Order();
            order.setTickerPrice(price);
            order.setAmount(orderInfo.getAmount());
            order.setTradeDirection(orderInfo.getTradeDirection());
            order.setTradeCenter(center);
            order.setDealAmount(orderInfo.getDealAmount());
            order.setDealPrice(orderInfo.getAvgPrice());
            order.setCreateTime(new Timestamp(new Date().getTime()));
            return priceMarginService.saveOrder(order);
        }

        private ApiResult.OrderInfo handleOrderInfo(String ret) {
            JSONObject o = JSON.parseObject(ret);
            ApiResult.OrderInfo orderInfo = new ApiResult.OrderInfo();
            orderInfo.setAmount(o.getBigDecimal("order_amount"));
            orderInfo.setAvgPrice(o.getBigDecimal("processed_price"));
            orderInfo.setDealAmount(o.getBigDecimal("processed_amount"));
            orderInfo.setPrice(o.getBigDecimal("order_price"));
            int type = o.getInteger("type");
            String direction;
            if (type == 1 || type == 3)
                direction = "buy";
            else
                direction = "sell";
            orderInfo.setTradeDirection(direction);
            orderInfo.setResult("true");
            return orderInfo;
        }
    }

    private static String getMuchSmallPrice(BigDecimal price) {
        return String.valueOf(price.multiply(BigDecimal.valueOf(0.95)).setScale(2, BigDecimal.ROUND_DOWN));
    }

    private static String getMuchBigPrice(BigDecimal price) {
        return String.valueOf(price.multiply(BigDecimal.valueOf(1.05)).setScale(2, BigDecimal.ROUND_DOWN));
    }

    private static ApiResult.Trade jsonHandle(String huobiTradeRet) {
        JSONObject object = JSON.parseObject(huobiTradeRet);
        return new ApiResult.Trade(object.getLong("id"), object.getString("result"));
    }

    public static void initData() {//修改初始数据

    }

    public static void main(String[] args) {
        ApiResult.Trade trade = ApiResult.getTradeRet(okApiKey, okSecretKey, "btc_cny", "5000", "0.01", "buy");
        System.out.println();
    }

}
