package com.ib.client.AlgoTrading;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.TickType;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple example which will pull the last price for a given symbol. 
 * 
 * API requests:
 *  eConnect
 *  reqMktData (snapshot)
 *  eDisconnect
 * 
 * API callbacks:
 *  tickPrice
 * 
 * $Id$
 */
public class ATFutureThreadHedged extends AlgoTradingBase {
    //total number of futures dates available for trading
    //It will be 
    private static final int TOTAL_FUTURES = 4; 
    //Modify this as current dates gets closure to first future date
    private static final double MONTHS_TO_FIRST_FUTURE = 1;
    private static final double MIBOR_MONTHLY_INTEREST = 0.005;
    private static final double TARGET_MONTHLY_INTEREST = 0.01;
    private static final double TARGET_PROFIT = 0.003;
    //Maximum possible profit..any value above this is suspicious
    private static final double MAX_POSSIBLE_PROFIT = 0.05;
    private static final double MIN_SQUARE_OFF_RATIO = 0.002;
    private static final double ORDER_BUFFER = 0.0005;
    private static int DEFAULT_LOT_SIZE = 5;
    
    private boolean exchangeClosed = false;
    private String symbol = null;
    private int LotSize = 0;
    private double symbolLimit = 0.0; //Limit on total exposure on this symbol 
    private int requestIdBase=0;
    private int requestId=0;
    private String[] futureDates = new String[TOTAL_FUTURES];
    //data related to near future
    private double[] futureBidPrices = new double[TOTAL_FUTURES];
    private Date[] futureBidDates = new Date[TOTAL_FUTURES];
    private int[] futureBidSizes = new int[TOTAL_FUTURES];
    private double[] futureAskPrices = new double[TOTAL_FUTURES];
    private Date[] futureAskDates = new Date[TOTAL_FUTURES];
    private int[] futureAskSizes = new int[TOTAL_FUTURES];
    private double[] futureLastPrices = new double[TOTAL_FUTURES];
    private Date[] futureLastDates = new Date[TOTAL_FUTURES];
    private boolean startTrading = false;
    //Number of times transaction has run into loss. Thread with exit if there are
    //more than two conseccutive losses or loss is more than 5% for a single position.
    private int lossCount = 0; 
    private int buyIndex = 0; //Index of the future to buy
    private int sellIndex = 0; //Index of the future of sell
    private int buySellLots = 0; //No. of lots for buying/selling 
    private Contract[] contracts = new Contract[TOTAL_FUTURES];
    private int orderId=1;
    private boolean isPositionOpen = false;
    private int positionBuyIndex = -1;
    private int positionSellIndex = -1;
    private double positionBuyPrice = 0;
    private double positionSellPrice = 0;
    private int positionLots=0;
    private Date positionTimeStamp;
    private boolean firstOrderBUY=false; //true for BUY and false for SELL
    private int orderIdBUY = 0;
    private int orderIdSELL = 0;
    
    
    


    public ATFutureThreadHedged(String _symbol, String[] _futureDates, double _portfolioLimit,int _lotSize,int _requestIdBase,int _clientId) {
        this.symbol = _symbol;
        this.LotSize = _lotSize;
        this.symbolLimit = _portfolioLimit;
        this.requestIdBase=_requestIdBase;
        this.requestId = this.requestIdBase;
        this.twsClientId = _clientId;
        System.arraycopy(_futureDates,0,this.futureDates,0,TOTAL_FUTURES );

        //Now fill the current position

    }

    public void run() {
        try {
            boolean isSuccess = false;
            int waitCount = 0;

            // Make connection
            connectToTWS(); 
            //Create contract and request market data 
            contracts[0] = createContract(symbol, "STK", "NSE", "INR");
            //Request continuously updating market data
            eClientSocket.reqMktData(requestId++, contracts[0], null, false);
           
            //Create contracts and request market data for all futures
            for (int i=1;i<TOTAL_FUTURES;i++) {
                contracts[i] = createContract(symbol, "FUT", "NSE", "INR",futureDates[i],null,0.0);
                // Requests snapshot market data
                //eClientSocket.reqMktData(requestId++, contracts[i], null, true);
                eClientSocket.reqMktData(requestId++, contracts[i], null, false);
            }
            
            //Request clinets are created..now wait for 30 seconds

            while (!isSuccess && waitCount < 30 * MAX_WAIT_COUNT) {
                // Check if ask price loaded for all the future date
                isSuccess = true;
                for (int i=0;i<TOTAL_FUTURES;i++) {
                    if (futureAskPrices[i] == 0)
                        isSuccess = false;
                }
                for (int i=0;i<TOTAL_FUTURES;i++) {
                    if (futureBidPrices[i] == 0)
                        isSuccess = false;
                }

                if (!isSuccess) {
                    sleep(WAIT_TIME); // Pause for 1 second
                    waitCount++;
                } else {
                    this.startTrading = true;
                }
            }

            // Display results
            if (isSuccess) {
                System.out.println(symbol + "-STOCK - Last Price: "+ this.futureLastPrices[0]);
                //Print last price and time stamp for all future prices
                for (int i=1;i<TOTAL_FUTURES;i++) {
                    System.out.println(symbol + "-FUTURE ("+ this.futureDates[i] +") - Last Price: "+ this.futureLastPrices[i]);
                }
                while ((lossCount < 2) && (exchangeClosed == false)) {
                    //sleep for 50 ms
                    sleep(50);
                }
                //Print the message
                System.out.println(symbol + ": [Error] Closing the thread..check your positions");

            } else {
                System.out.println(" [Error] Failed to retrieve future prices for " + symbol);
            }
        } catch (Throwable t) {
            System.out.println("Example1.run() :: Problem occurred during processing: " + t.getMessage());
        } finally {
            disconnectFromTWS();
        }
    }

    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        int index = tickerId - this.requestIdBase;
        switch(field) {
            case TickType.LAST: this.futureLastPrices[index] = price;
                                break;
            case TickType.ASK: this.futureAskPrices[index] = price;
                                this.futureAskDates[index] = new Date();
                                break;
            case TickType.BID: this.futureBidPrices[index] = price;
                                this.futureBidDates[index] = new Date();
                                break;
            default: break;
        }

        //data has come ..do some claculation
        if ((field == TickType.ASK) || (field == TickType.BID)) {
            if (price <= 0) {
                //close the thread
                exchangeClosed = true;
                return;
            }
            if (true == checkNewPosition()) {
                placeArbitrageOrder();
                //Check if the order is processed successfully
                //If yes, create an open position
                this.isPositionOpen = true;
                
                
            }
            //If there is an open position ..settle it
            if ((this.isPositionOpen == true) && (checkSquareOffPosition() > 0.005)) {
                placeSquareOffOrder();
                //Check if the order is processed successfully
                //If yes, close the open position
                //this.isPositionOpen = false;
                
            }
        }       
    }
    //update bid and ask sizes
    public void tickSize(int tickerId, int field, int size) {
        
    }
    
    public boolean checkNewPosition() {
        if (this.startTrading == false) {
            return false;
        }
        int i=0;
        int j=0;
        double diff=0;
        double maxDiff = 0;
        double stockLastPrice = futureLastPrices[0];
        for (i=1;i<TOTAL_FUTURES;i++) {
            //first try for buy of this future
            
            for (j = i+1;j<TOTAL_FUTURES;j++) {
                diff = (this.futureBidPrices[j] - (j-i)*(1+MIBOR_MONTHLY_INTEREST) * this.futureAskPrices[i])/ this.futureAskPrices[i];
                if (diff > maxDiff && diff>TARGET_PROFIT && diff<MAX_POSSIBLE_PROFIT) {
                    maxDiff = diff;
                    this.buyIndex = i;
                    this.sellIndex = j;
                }
            }
            if (maxDiff > 0)
                return true;
        }

        for (i=1;i<TOTAL_FUTURES;i++) {
            //then try for sell this future by taking share loan
            for (j =i+1;j<TOTAL_FUTURES;j++) {
                diff = ( (j-i)*(1+MIBOR_MONTHLY_INTEREST) * this.futureBidPrices[i] - this.futureAskPrices[j])/this.futureBidPrices[i];
                if (diff > maxDiff && diff>TARGET_PROFIT && diff<MAX_POSSIBLE_PROFIT) {
                    maxDiff = diff;
                    this.buyIndex = j;
                    this.sellIndex = i;
                }
                        
            }
            if (maxDiff > 0)
                return true;
            
        }
        return false;
    }
    public double checkSquareOffPosition() {
        if (positionBuyPrice == 0)
            return 0;
        double positionBuyProfit = this.futureBidPrices[buyIndex] - this.positionBuyPrice;
        double positionSellProfit =   this.positionSellPrice - this.futureAskPrices[sellIndex];
        double totalProfit = positionBuyProfit + positionSellProfit;
        double totalProfitRatio = totalProfit/positionBuyPrice;
        return totalProfitRatio;
        //return 0.0001;
        
    }
    
    public void placeArbitrageOrder() {
        //find out parent order
        if (isPositionOpen == true) {
            System.out.println("Position Open on "+symbol+" : Cannot place below order");
            System.out.println("BUY "+symbol+"-"+futureDates[buyIndex]+" AT "+futureAskPrices[buyIndex]+" AND SELL "+symbol+"-"+futureDates[sellIndex-1]+" AT "+futureBidPrices[sellIndex]);
            return;
        }
        double expAskPrice = (1 + (MONTHS_TO_FIRST_FUTURE + buyIndex - 1) * MIBOR_MONTHLY_INTEREST) * futureAskPrices[0];
        double buyDiff = (expAskPrice - futureAskPrices[buyIndex])/futureAskPrices[buyIndex];
        
        double expBidPrice = (1 + (MONTHS_TO_FIRST_FUTURE + sellIndex - 1) * MIBOR_MONTHLY_INTEREST) * futureBidPrices[0];
        double sellDiff = ( futureBidPrices[sellIndex] - expBidPrice)/futureBidPrices[sellIndex];
        
        if (buyDiff > sellDiff) {
            firstOrderBUY = true; //place BUY order first
        }else {
            firstOrderBUY = false;
        }
        if (firstOrderBUY == true) {
            double buyLimit = (1 + ORDER_BUFFER) * futureAskPrices[buyIndex];
            Order buyOrder = createOrder("BUY", DEFAULT_LOT_SIZE, "LMT",buyLimit);
            buyOrder.m_tif = "IOC"; //immediate or cancel
            orderIdBUY = orderId;
            eClientSocket.placeOrder(orderId++,contracts[buyIndex],buyOrder);
            System.out.println("Attempting " + symbol+"-"+futureDates[buyIndex]+" BUY AT " + buyLimit);
            Order sellOrder = createOrder("SELL", DEFAULT_LOT_SIZE, "MKT");
            //Create sell order as a child order
            sellOrder.m_parentId = orderIdBUY;
            orderIdSELL = orderId;
            eClientSocket.placeOrder(orderId++,contracts[sellIndex],sellOrder);
        } else {
            //place sell order first
            double sellLimit = (1- ORDER_BUFFER)* futureBidPrices[sellIndex];
            System.out.println("SELL: " + sellLimit);
            Order sellOrder = createOrder("SELL", DEFAULT_LOT_SIZE, "LMT",sellLimit);
            sellOrder.m_tif = "IOC";
            orderIdSELL = orderId;
            eClientSocket.placeOrder(orderId++,contracts[sellIndex],sellOrder);
            System.out.println("Attempting" + symbol+"-"+futureDates[sellIndex]+" SELL AT " + sellLimit);
            Order buyOrder = createOrder("BUY",DEFAULT_LOT_SIZE , "MKT");
            buyOrder.m_parentId = orderIdSELL;
            orderIdBUY = orderId;
            eClientSocket.placeOrder(orderId++,contracts[buyIndex],buyOrder);
        }
        //place order as  a parent child order
        System.out.println("BUY "+symbol+"-"+futureDates[buyIndex]+" AT "+futureAskPrices[buyIndex]+" AND SELL "+symbol+"-"+futureDates[sellIndex-1]+" AT "+futureBidPrices[sellIndex]);
        positionBuyPrice = futureAskPrices[buyIndex];
        positionSellPrice = futureBidPrices[sellIndex];
               
        
        //sleep for 1 second check if the order is successfully placed
        try {
        sleep(1000);
        eClientSocket.cancelOrder(orderIdBUY);
        eClientSocket.cancelOrder(orderIdSELL);
        //print message for success or failure
        //If success place the value of position parameters
        //this.positionBuyPrice = futureAskPrices[buyIndex];

            //Now check if the order have been fulfiled else cancel

        //sleep(15000);
        } catch (InterruptedException ex) {
            Logger.getLogger(ATFutureThreadDynamic.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void placeSquareOffOrder() {
        //If first order in the position was buy create a sell order first
        if (firstOrderBUY == true) {
            double sellLimit = (1 + ORDER_BUFFER) * futureBidPrices[buyIndex];
            Order sellOrder = createOrder("BUY", 1, "LMT",sellLimit);
            eClientSocket.placeOrder(orderId++,contracts[buyIndex],sellOrder);
            Order buyOrder = createOrder("SELL", 1, "MKT");
            //Create sell order as a child order
            buyOrder.m_parentId = orderId -1;
            eClientSocket.placeOrder(orderId++,contracts[sellIndex],buyOrder);
        } else {
            //place buy order first
            double buyLimit = (1- ORDER_BUFFER)* futureAskPrices[sellIndex];
            Order buyOrder = createOrder("SELL", 1, "LMT",buyLimit);
            eClientSocket.placeOrder(orderId++,contracts[sellIndex],buyOrder);
            Order sellOrder = createOrder("BUY", 1, "MKT");
            sellOrder.m_parentId = orderId -1;
            eClientSocket.placeOrder(orderId++,contracts[buyIndex],sellOrder);
        }
    }
    //EWrapper call
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        
    }
}
