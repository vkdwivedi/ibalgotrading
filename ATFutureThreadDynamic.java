package com.ib.client.AlgoTrading;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.TickType;
import java.util.Date;
import java.util.Properties;
import java.text.*;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * This thread try to grab profitable futures. 
 * Then it will place a square off position at target profit
 * All the positions will have a stop loss defined
 * If two consecutive positions are loss making or cumulative profit is 
 * 
 * $Id$
 */
public class ATFutureThreadDynamic extends AlgoTradingBase {
    private String acctCode = null;
    //total number of futures dates available for trading
    //It will also include stock 
    private int totFutures = 0;
    private double tickSize = 0;
    //months remaining for the first future date from current date
    private double monthsToFirstFuture = 0;
    //Maximum permissible loss 
    private double maxLossINR = 0;
    //Monthly interest rate for Mumbai Inter Bank
    private double MIBORMonthlyInterest = 0;
    //Interest payable if shares are taken on loan
    private double shareLoanMonthlyInterest = 0;
    //Target profit when attempted a sell position
    private double sellTargetProfit = 0;
    //Target profit when attempted a buy position
    private double buyTargetProfit = 0;
    private double sellPositionRatio = 0;
    private double buyPositionRatio = 0;
    private double readjustTrigger = 1;
    //cut your loss when prices does not move favourably
    public double stopLossLimit = 0;
    //Maximum possible profit..any value above this is suspicious
    private double maxPossibleProfit = 0;
    //default no of lots in attempting position
    private int defaultLotSize = -1;   
    private boolean exchangeClosed = false;
    private String symbol = null;
    //No. of stocks in one lot
    private int lotSize = 0;
    private int requestIdBase=0;
    private int requestId=0;
    private String[] futureDates = null;
    //data related to near future
    private double[] futureBidPrices = null;
    private Date[] futureBidDates = null;
    private int[] futureBidSizes = null;
    private double[] futureAskPrices = null;
    private double[] baseStockAskPrices = null;
    private double[] baseStockBidPrices = null;
    private Date[] futureAskDates = null;
    private int[] futureAskSizes = null;
    private double[] futureLastPrices = null;
    private Date[] futureLastDates = null;
    private double[] liquidityRatios = null;
    private Contract[] contracts = null;
    private boolean startTrading = false;
    private double symbolLimit = 0;
    //Number of times transaction has run into loss. Thread with exit if there are
    //more than two conseccutive losses or loss is more than 5% for a single position.
    private int lossCount = 0; 
    private int orderId=1;
    private boolean[] isPositionOpen = null;
    private double[] positionPrice = null;
    private double[] squareOffPrice = null;
    private int[] positionLots=null;
    private Date[] positionTime = null;
    private Date[] squareOffTime = null;
    private boolean[] positionBUY=null; //true for BUY and false for SELL
    private int[] sellOrderIds = null;
    private int[] buyOrderIds = null;  
    private int[] lmtOrderIds = null;
    private int[] stopLossOrderIds = null;
    private String[] position = null; // BUY or SELL
    private String[] squareOffPosition = null;
    private double cumProfit = 0;
    private double cumNetProfit = 0;
    private static double grossProfit = 0; //Gross profit from all the trades
    private static double grossTrasactionCost = 0;
    private double transactionPercentage = 0;
    private static DecimalFormat decimalFormatter = new DecimalFormat("###,###.##");
    private DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT);
    private DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.MEDIUM);
    private TimeZone tzIndia = TimeZone.getTimeZone("Asia/Calcutta");
    private static int CURRENT_HISTORY_SIZE = 50;
    private boolean[] currentHistoryBUY = null;
    private int[] currentHistoryMonth = null;
    

    public ATFutureThreadDynamic(String _symbol, double _portfolioLimit,int _lotSize,int _requestIdBase,int _clientId,Properties _ATProperties) {
        this.symbol = _symbol;
        this.lotSize = _lotSize;
        this.symbolLimit = _portfolioLimit;
        this.requestIdBase=_requestIdBase;
        this.requestId = this.requestIdBase;
        this.twsClientId = _clientId;
        //make sure orderId is unique across threads
        //this.orderId = requestIdBase*100000+(int)(Math.random()*10000);
        Calendar cal = Calendar.getInstance();
        int doy = cal.get(Calendar.DAY_OF_YEAR);
        int year = cal.get(Calendar.YEAR) - 2010;
        this.orderId = year * 100000000 + doy * 1000000 + requestIdBase * 10000 + ((int)(Math.random()*50)) * 100;
        //Read other values from properties
        futureDates = _ATProperties.getProperty("FUTURE_DATES").split(",");
        totFutures=futureDates.length;
        //Now we know the total number of futures ..initialize other variables
        //data related to near future
        futureBidPrices = new double[totFutures];
        futureBidDates = new Date[totFutures];
        futureBidSizes = new int[totFutures];
        futureAskPrices = new double[totFutures];
        futureAskDates = new Date[totFutures];
        futureAskSizes = new int[totFutures];
        futureLastPrices = new double[totFutures];
        futureLastDates = new Date[totFutures];
        liquidityRatios = new double[totFutures];
        baseStockAskPrices = new double[totFutures];
        baseStockBidPrices = new double[totFutures];
        positionPrice = new double[totFutures];
        squareOffPrice = new double[totFutures];
        positionLots = new int[totFutures];
        positionTime = new Date[totFutures];
        squareOffTime = new Date[totFutures];
        positionBUY= new boolean[totFutures]; //true for BUY and false for SELL
        sellOrderIds = new int[totFutures];
        buyOrderIds = new int[totFutures];  
        lmtOrderIds = new int[totFutures];
        stopLossOrderIds = new int[totFutures];
        position = new String[totFutures]; // BUY or SELL
        squareOffPosition = new String[totFutures];
        contracts = new Contract[totFutures];
        timeFormatter.setTimeZone(tzIndia);
        dateFormatter.setTimeZone(tzIndia);
        isPositionOpen = new boolean[totFutures];
        currentHistoryBUY = new boolean[CURRENT_HISTORY_SIZE];
        currentHistoryMonth = new int[CURRENT_HISTORY_SIZE];
        for (int i=0;i<totFutures;i++)
            this.isPositionOpen[i] = false;
        //Now read other varibles from properties 
        String[] liquidityRatiosStrings = _ATProperties.getProperty("LIQUIDITY_RATIOS").split(",");
        for (int i=0;i<totFutures;i++)
            liquidityRatios[i] = Double.parseDouble(liquidityRatiosStrings[i]);
        this.tickSize = Double.parseDouble(_ATProperties.getProperty("TICK_SIZE"));
        this.monthsToFirstFuture = Double.parseDouble(_ATProperties.getProperty("MONTHS_TO_FIRST_FUTURE"));
        this.maxLossINR = Double.parseDouble(_ATProperties.getProperty("MAX_LOSS_INR"));
        this.MIBORMonthlyInterest = Double.parseDouble(_ATProperties.getProperty("MIBOR_MONTHLY_INTEREST"));
        this.shareLoanMonthlyInterest = Double.parseDouble(_ATProperties.getProperty("SHARE_LOAN_MONTHLY_INTEREST"));
        this.sellTargetProfit = Double.parseDouble(_ATProperties.getProperty("SELL_TARGET_PROFIT"));
        this.buyTargetProfit = Double.parseDouble(_ATProperties.getProperty("BUY_TARGET_PROFIT"));
        this.buyPositionRatio = Double.parseDouble(_ATProperties.getProperty("BUY_POSITION_RATIO"));
        this.sellPositionRatio = Double.parseDouble(_ATProperties.getProperty("SELL_POSITION_RATIO"));
        this.readjustTrigger = Double.parseDouble(_ATProperties.getProperty("READJUST_TRIGGER"));
        this.stopLossLimit = Double.parseDouble(_ATProperties.getProperty("STOP_LOSS_LIMIT"));
        this.maxPossibleProfit = Double.parseDouble(_ATProperties.getProperty("MAX_POSSIBLE_PROFIT"));
        this.transactionPercentage = Double.parseDouble(_ATProperties.getProperty("TRANSACTION_COST_RATIO"));
        this.acctCode = _ATProperties.getProperty("ACCOUNT_CODE");
        this.defaultLotSize = Integer.parseInt(_ATProperties.getProperty("DEFAULT_LOT_SIZE"));
    }

    public void run() {
        try {
            boolean isSuccess = false;
            int waitCount = 0;
            //Initialize Ask and Bid Prices
            for (int i=0;i<totFutures;i++) {
                futureAskPrices[i] = -2;
            }
            for (int i=0;i<totFutures;i++) {
                futureBidPrices[i] = -2;
            }
            // Make connection
            connectToTWS(); 
            //Create contract and request market data 
            contracts[0] = createContract(symbol, "STK", "NSE", "INR");
            //Request continuously updating market data
            eClientSocket.reqMktData(requestId++, contracts[0], null, false);
           
            //Create contracts and request market data for all futures
            for (int i=1;i<totFutures;i++) {
                contracts[i] = createContract(symbol, "FUT", "NSE", "INR",futureDates[i],null,0.0);
                // Requests snapshot market data
                //eClientSocket.reqMktData(requestId++, contracts[i], null, true);
                eClientSocket.reqMktData(requestId++, contracts[i], null, false);
            }
            //Request Open Positions
            eClientSocket.reqAccountUpdates(true, acctCode);
            //Request Order Status
            eClientSocket.reqOpenOrders();
            
            //Request clinets are created..now wait for 30 seconds

            while (!isSuccess && waitCount < 200 * MAX_WAIT_COUNT) {
                // Check if ask price loaded for all the future date
                isSuccess = true;
                for (int i=0;i<totFutures;i++) {
                    if (futureAskPrices[i] <= 0)
                        isSuccess = false;
                }
                for (int i=0;i<totFutures;i++) {
                    if (futureBidPrices[i] <= 0)
                        isSuccess = false;
                }

                if (!isSuccess) {
                    sleep(WAIT_TIME); // Pause for 1 second
                    waitCount++;
                } else {
                    //Stop subscription to account update
                    eClientSocket.reqAccountUpdates(false, acctCode);
                    //Start checking for new positions
                    this.startTrading = true;
                }
            }

            // Display results
            if (isSuccess) {
                System.out.println(symbol + "-STOCK - Last Price: "+ this.futureLastPrices[0]);
                //Print last price and time stamp for all future prices
                for (int i=1;i<totFutures;i++) {
                    System.out.println(symbol + "-FUTURE ("+ this.futureDates[i] +") - Last Price: "+ this.futureLastPrices[i]);
                }
                //Place the original buy and sell orders
                for (int i=1;i<totFutures;i++) {
                    this.readjustPosition(i);
                }
                while ((lossCount < 2) && (exchangeClosed == false) && (cumNetProfit > maxLossINR)) {
                    //sleep for 50 ms and exit only in the above conditions
                    sleep(50);
                }
                //Print the message
                System.out.println(symbol + ": [Error] Closing the thread..check your positions");
                //Print Transaction Summary
                System.out.println("[Summary:] "+symbol+": Cummulative Net Profit/Loss="+decimalFormatter.format(cumNetProfit));
                for (int i=0;i<this.totFutures;i++) {
                    if (this.isPositionOpen[i] == true)
                        System.out.println("[Summary:] Position Open on "+symbol+"-"+this.futureDates[i]+": "+positionLots[i]+" lots");
                
                }
                if (this.twsClientId == 1)
                    System.out.println("[Summary:] Gross Profit/Loss = "+decimalFormatter.format(grossProfit)+", Net Profit/Loss = "+decimalFormatter.format(grossProfit-grossTrasactionCost));

            } else {
                System.out.println(" [Error] Failed to retrieve future prices for " + symbol);
            }
        } catch (Throwable t) {
            System.out.println("ATFutureThreadDynamic.run() :: Problem occurred during processing: " + t.getMessage());
        } finally {
            disconnectFromTWS();
        }
    }
     
    public void readjustPosition(int positionMonth) {
        //If there is an open position return
        if (true == this.isPositionOpen[positionMonth]) {
            return;
        }else {
            //cancel previous sell order ..buy order will automatically get cancelled because of OCA
            if (0 != buyOrderIds[positionMonth]) {
                eClientSocket.cancelOrder(buyOrderIds[positionMonth]);
                //OCA sell order will automatically get cancelled
            }
            //set the base stock price for this position
            baseStockAskPrices[positionMonth] = futureAskPrices[0];
            baseStockBidPrices[positionMonth] = futureBidPrices[0];
       
            double buyLimit,sellLimit;
            sellLimit = (1 + liquidityRatios[positionMonth] * sellPositionRatio) * baseStockAskPrices[positionMonth];
            buyLimit = (1 - liquidityRatios[positionMonth] * buyPositionRatio) * baseStockBidPrices[positionMonth];
            
            //Change limit and stopLoss to minimum tick size
            buyLimit = (Math.ceil(buyLimit/tickSize))*tickSize;
            sellLimit = (Math.ceil(sellLimit/tickSize))*tickSize;
            //take the reverse position
            Order buyOrder = createOrder("BUY", this.defaultLotSize, "LMT",buyLimit);
            buyOrderIds[positionMonth] = orderId++;
            String curOCAGroup = symbol +String.valueOf(buyOrderIds[positionMonth]);
            buyOrder.m_ocaGroup = curOCAGroup;
            buyOrder.m_ocaType = 3;
            eClientSocket.placeOrder(buyOrderIds[positionMonth],contracts[positionMonth],buyOrder);
            //System.out.println("["+buyOrderIds[positionMonth]+"] Placing BUY order on " + symbol+"-"+futureDates[positionMonth]+" AT LIMIT = " + decimalFormatter.format(buyLimit));
            //Now place stop loss order
            Order sellOrder = createOrder("SELL", this.defaultLotSize, "LMT",sellLimit);
            sellOrder.m_auxPrice = sellLimit;
            sellOrder.m_ocaGroup = curOCAGroup;
            sellOrder.m_ocaType = 3;
            sellOrderIds[positionMonth] = orderId++;
            eClientSocket.placeOrder(sellOrderIds[positionMonth],contracts[positionMonth],sellOrder);
            Date curDate = new Date();
            System.out.println("["+timeFormatter.format(curDate)+":"+buyOrderIds[positionMonth]+","+sellOrderIds[positionMonth]+"] Adjusting Position on " + symbol+"-"+futureDates[positionMonth]+" BUY = " + decimalFormatter.format(buyLimit)+", SELL = "+decimalFormatter.format(sellLimit)+" : "+symbol+" = "+futureAskPrices[0]+"/"+futureBidPrices[0]);
            //set the history
            int buyHist = buyOrderIds[positionMonth]%CURRENT_HISTORY_SIZE;
            int sellHist = sellOrderIds[positionMonth]%CURRENT_HISTORY_SIZE;
            this.currentHistoryBUY[buyHist] = true;
            this.currentHistoryBUY[sellHist] = true;
            this.currentHistoryMonth[buyHist] = positionMonth;
            this.currentHistoryMonth[sellHist] = positionMonth;
                    
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
        if ((0 == index) && ((field == TickType.ASK) || (field == TickType.BID))) {
            if (price == -1) {
                //close the thread
                exchangeClosed = true;
                return;
            } 
            for (int i=1;i<this.totFutures;i++) {
                if ((baseStockAskPrices[i]!=0) && (baseStockBidPrices[i]!=0)) {
                    double priceVariation = 0;
                    if (field == TickType.ASK)
                        priceVariation = (futureAskPrices[0] - baseStockAskPrices[i])/baseStockAskPrices[i];
                    else
                        priceVariation = (futureBidPrices[0] - baseStockBidPrices[i])/baseStockBidPrices[i];
                    if (Math.abs(priceVariation) > this.readjustTrigger)
                        readjustPosition(i);
                }
            }
        }       
    }
    //update bid and ask sizes
    public void tickSize(int tickerId, int field, int size) {
        
    }
    
    
    public void placeSquareOffOrder(int positionMonth) {
        //If first order in the position was buy create a two sell orders
        //Both the orders will be part of an OCA
        //One will be square off order and the other one will be stop loss order
        double limit;
        double stopLoss;
        if (positionBUY[positionMonth] == true) {
            limit = (1 + liquidityRatios[positionMonth] * buyTargetProfit) * positionPrice[positionMonth];
            stopLoss = (1 - liquidityRatios[positionMonth] * stopLossLimit) * positionPrice[positionMonth];
            squareOffPosition[positionMonth] = "SELL";

        }else {
            limit = (1 - liquidityRatios[positionMonth] * sellTargetProfit) * positionPrice[positionMonth];
            stopLoss = (1 + liquidityRatios[positionMonth] * stopLossLimit) * positionPrice[positionMonth];
            squareOffPosition[positionMonth] = "BUY";
        }
        //Change limit and stopLoss to minimum tick size
        limit = (Math.ceil(limit/tickSize))*tickSize;
        stopLoss = (Math.ceil(stopLoss/tickSize))*tickSize;
        //take the reverse position
        Order squareOffOrder = createOrder(squareOffPosition[positionMonth], positionLots[positionMonth], "LMT",limit);
        lmtOrderIds[positionMonth] = orderId++;
        String curOCAGroup = symbol + String.valueOf(lmtOrderIds[positionMonth]);
        squareOffOrder.m_ocaGroup = curOCAGroup;
        squareOffOrder.m_ocaType = 3;
        eClientSocket.placeOrder(lmtOrderIds[positionMonth],contracts[positionMonth],squareOffOrder);
        System.out.println("["+lmtOrderIds[positionMonth]+"] Placing square off order on " + symbol+"-"+futureDates[positionMonth]+" "+squareOffPosition[positionMonth]+" AT LIMIT = " + decimalFormatter.format(limit));
        //Now place stop loss order
        Order stopLossOrder = createOrder(squareOffPosition[positionMonth], positionLots[positionMonth], "STP",stopLoss);
        stopLossOrder.m_auxPrice = stopLoss;
        stopLossOrder.m_ocaGroup = curOCAGroup;
        stopLossOrder.m_ocaType = 3;
        stopLossOrderIds[positionMonth] = orderId++;
        eClientSocket.placeOrder(stopLossOrderIds[positionMonth],contracts[positionMonth],stopLossOrder);
        System.out.println("["+stopLossOrderIds[positionMonth]+"] Placing stop-loss order on " + symbol+"-"+futureDates[positionMonth]+" "+squareOffPosition[positionMonth]+" AT STOP-LOSS = "+decimalFormatter.format(stopLoss)); 
    }
    //EWrapper call
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        if (status.equals("Cancelled"))
            return;
        int positionMonth = 0;
        int orderType = 0; //1 for buy, 2 for sell, 3 for squareOff and 4 for stop loss
        //Find out the order type and positionMonth
        for (int i=1;i<this.totFutures;i++) {
            if (orderId == buyOrderIds[i]) {
                positionMonth = i;
                orderType = 1;
                break;
            } else if (orderId == sellOrderIds[i]) {
                positionMonth = i;
                orderType = 2;
                break;
            }else if (orderId == lmtOrderIds[i]) {
                positionMonth = i;
                orderType = 3;
                break;
            }else if (orderId == stopLossOrderIds[i]) {
                positionMonth = i;
                orderType = 4;
                break;
            }
        }
        if ((0 == orderType) && (filled > 0)) {
            //Order is cancelled but it got filled just prior to that
            //Set the position month, buy/sell and change the orderType
            if (Math.abs(orderId - this.orderId) > 100) {
                //Looks like order from some other client is fulfilled
                return;
            }
            int histId = orderId % CURRENT_HISTORY_SIZE;
            positionMonth = this.currentHistoryMonth[histId];
            if (this.currentHistoryBUY[histId] == true)
                orderType = 1;
            else
                orderType = 2;
            //cancell the current open order
            eClientSocket.cancelOrder(buyOrderIds[positionMonth]);       
        }
        if ((orderType == 1) || (orderType == 2)) {  //Position is filled
            //Return if this position is open..sometimes the status is given multiple times
            if (this.isPositionOpen[positionMonth] == true) {
                return;
            }
            if (filled > 0) {
                //cancel if there is any remaining 
                if (remaining > 0) {
                    eClientSocket.cancelOrder(orderId);
                }
                this.isPositionOpen[positionMonth] = true;
                if (orderType == 1){
                    this.positionBUY[positionMonth] = true;
                    position[positionMonth] = "BUY";
                }else {
                     this.positionBUY[positionMonth] = false;
                     position[positionMonth] = "SELL";
                }
                //reset buy and sell order ids for this future
                sellOrderIds[positionMonth] = 0;
                buyOrderIds[positionMonth] = 0;
                positionLots[positionMonth] = (filled/lotSize);
                positionPrice[positionMonth] = avgFillPrice;
                positionTime[positionMonth] = new Date();
                System.out.println("["+timeFormatter.format(positionTime[positionMonth])+","+orderId+"] Got "+ positionLots[positionMonth] + " lots of " + symbol+"-"+futureDates[positionMonth]+" "+position[positionMonth] + " AT "+positionPrice[positionMonth]+" Status:"+status);
                placeSquareOffOrder(positionMonth);
                //Print stock and future prices
                System.out.println("ASK\t\tBID");
                for (int i=0;i<totFutures;i++)
                    System.out.println(futureAskPrices[i]+"\t\t"+futureBidPrices[i]); 
            }           
        }else if ((orderType == 3) || (orderType == 4)) {   //Position squared off
            //Return if this position is closed..sometimes the status is given multiple times
            if (this.isPositionOpen[positionMonth] == false) {
                return;
            }
            if ((0 == remaining) && (filled > 0)) {
                //what if an order is partially filled ?????
                //reset squqre off id
                lmtOrderIds[positionMonth] = 0;
                stopLossOrderIds[positionMonth] = 0;
                squareOffPrice[positionMonth] = avgFillPrice;
                double profit = (squareOffPrice[positionMonth] - positionPrice[positionMonth]) * positionLots[positionMonth] * lotSize;
                if (true != this.positionBUY[positionMonth])
                    profit = -1 * profit;
                squareOffTime[positionMonth] = new Date();
                System.out.println("["+orderId+"] Squared Off "+ positionLots[positionMonth] + " lots of " + symbol+"-"+futureDates[positionMonth]+" "+squareOffPosition[positionMonth] + " AT "+squareOffPrice[positionMonth]);
                cumProfit = cumProfit + profit;
                grossProfit = grossProfit + profit;
                double transactCost= positionLots[positionMonth] * lotSize * positionPrice[positionMonth] * transactionPercentage;
                cumNetProfit = cumProfit - transactCost;
                grossTrasactionCost = grossTrasactionCost + transactCost;
                double netProfit = grossProfit - grossTrasactionCost;
                System.out.println("["+timeFormatter.format(squareOffTime[positionMonth])+"] "+symbol+" position: Profit = "+ decimalFormatter.format(profit) + ", Total Profit/Loss = "+decimalFormatter.format(cumNetProfit)+ ", Gross Profit/Loss = "+decimalFormatter.format(grossProfit)+", Net Profit/Loss = "+decimalFormatter.format(netProfit));
                //Print log in CSV format
                System.out.println("[LOG],"+dateFormatter.format(squareOffTime[positionMonth])+","+timeFormatter.format(positionTime[positionMonth])+","+timeFormatter.format(squareOffTime[positionMonth])+","+symbol+","+futureDates[positionMonth]+","+positionMonth+","+position[positionMonth]+","+positionLots[positionMonth]+","+lotSize+","+positionPrice[positionMonth]+","+squareOffPrice[positionMonth]+","+profit+","+transactCost+","+(profit-transactCost));
                isPositionOpen[positionMonth] = false;
                System.out.println("ASK\t\tBID");
                for (int i=0;i<totFutures;i++)
                    System.out.println(futureAskPrices[i]+"\t\t"+futureBidPrices[i]);
                this.readjustPosition(positionMonth);
            }        
        } else {
            //System.out.println("Could not find order id for "+ orderId);
            //Cancel the order
            if (status.equals("Submitted")||status.equals("PreSubmitted")) {
                eClientSocket.cancelOrder(orderId);
                System.out.println("["+orderId+"] Cancelled the order for "+symbol );             
            }
        } 
    }
    //This function check the open position of the symbol at the start of the thread and places square off order on them
    //If there are open positions on more than one futures then this will take any one randomly
    //Other one will bot be squared off
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        if ( 0 == position ) {
            //ignore
            return;
        }else {
            int positionMonth = -1;
            for (int i=1;i<totFutures;i++) {
                if (futureDates[i].equals(contract.m_expiry)) {
                    positionMonth = i;
                }
            }
            if (positionMonth == -1) {
                return;
            }
            if ((contract.m_symbol.equals(symbol)) && (contract.m_secType.equals("FUT")) && (isPositionOpen[positionMonth] == false)) {
                positionLots[positionMonth] = (position/lotSize);
                if (position > 0){
                    positionBUY[positionMonth] = true;
                    this.position[positionMonth] = "BUY";
                }else {
                    positionBUY[positionMonth] = false;
                    positionLots[positionMonth] = -1 * positionLots[positionMonth];
                    this.position[positionMonth] = "SELL";
                }
                positionPrice[positionMonth] = averageCost;
                positionTime[positionMonth] = new Date();
                positionTime[positionMonth].setTime(0);
                contract.m_exchange = "NSE";
                System.out.println("Open Position: "+ positionLots[positionMonth] + " lots of " + symbol+"-"+futureDates[positionMonth]+" "+this.position[positionMonth] + " AT "+decimalFormatter.format(positionPrice[positionMonth])+" .");
                placeSquareOffOrder(positionMonth);
                isPositionOpen[positionMonth] = true;

            }
        }
        return;
    }           
}
