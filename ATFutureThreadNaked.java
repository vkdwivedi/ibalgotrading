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
public class ATFutureThreadNaked extends AlgoTradingBase {
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
    //Trigger at which sell position will be attempted
    private double sellProfitTrigger = 0;
    //Target profit when attempted a sell position
    private double sellTargetProfit = 0;
    //Trigger at which buy position will be attempted
    private double buyProfitTrigger = 0;
    //Target profit when attempted a buy position
    private double buyTargetProfit = 0;
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
    private int buyIndex = 0; //Index of the future to buy
    private int sellIndex = 0; //Index of the future of sell
    private int orderId=1;
    private boolean isPositionOpen = false;
    private int positionIndex = -1;
    private double positionPrice = 0;
    private double squareOffPrice = 0;
    private double stopLossPrice = 0;
    private int positionLots=-1;
    private Date positionTime = null;
    private boolean positionBUY=false; //true for BUY and false for SELL
    private int positionOrderId = 0;
    private double positionDiff = 0;
    private int squareOffOrderId = 0;
    private int stopLossOrderId = 0;
    private String position = ""; // BUY or SELL
    private String squareOffPosition = "";
    private double cumProfit = 0;
    private double cumNetProfit = 0;
    private static double grossProfit = 0; //Gross profit from all the trades
    private static double grossTrasactionCost = 0;
    private double transactionPercentage = 0;
    private static DecimalFormat decimalFormatter = new DecimalFormat("###,###.##");
    private boolean previousOrderOpen = true;
    private DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT);
    private DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.MEDIUM);
    private TimeZone tzIndia = TimeZone.getTimeZone(" Asia/Calcutta");
    

    public ATFutureThreadNaked(String _symbol, double _portfolioLimit,int _lotSize,int _requestIdBase,int _clientId,Properties _ATProperties) {
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
        contracts = new Contract[totFutures];
        timeFormatter.setTimeZone(tzIndia);
        dateFormatter.setTimeZone(tzIndia);
        //Now read other varibles from properties 
        String[] liquidityRatiosStrings = _ATProperties.getProperty("LIQUIDITY_RATIOS").split(",");
        for (int i=0;i<totFutures;i++)
            liquidityRatios[i] = Double.parseDouble(liquidityRatiosStrings[i]);
        this.tickSize = Double.parseDouble(_ATProperties.getProperty("TICK_SIZE"));
        this.monthsToFirstFuture = Double.parseDouble(_ATProperties.getProperty("MONTHS_TO_FIRST_FUTURE"));
        this.maxLossINR = Double.parseDouble(_ATProperties.getProperty("MAX_LOSS_INR"));
        this.MIBORMonthlyInterest = Double.parseDouble(_ATProperties.getProperty("MIBOR_MONTHLY_INTEREST"));
        this.shareLoanMonthlyInterest = Double.parseDouble(_ATProperties.getProperty("SHARE_LOAN_MONTHLY_INTEREST"));
        this.sellProfitTrigger = Double.parseDouble(_ATProperties.getProperty("SELL_PROFIT_TRIGGER"));
        this.sellTargetProfit = Double.parseDouble(_ATProperties.getProperty("SELL_TARGET_PROFIT"));
        this.buyProfitTrigger = Double.parseDouble(_ATProperties.getProperty("BUY_PROFIT_TRIGGER"));
        this.buyTargetProfit = Double.parseDouble(_ATProperties.getProperty("BUY_TARGET_PROFIT"));
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

            while (!isSuccess && waitCount < 2 * MAX_WAIT_COUNT) {
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
                while ((lossCount < 2) && (exchangeClosed == false) && (cumNetProfit > maxLossINR)) {
                    //sleep for 50 ms and exit only in the above conditions
                    sleep(50);
                }
                //Print the message
                System.out.println(symbol + ": [Error] Closing the thread..check your positions");
                //Print Transaction Summary
                System.out.println("[Summary:] "+symbol+": Cummulative Net Profit/Loss="+decimalFormatter.format(cumNetProfit));
                if (this.isPositionOpen=true)
                    System.out.println("[Summary:] Position Open on "+symbol+"-"+this.futureDates[positionIndex]+": "+positionLots+" lots");
                if (this.twsClientId == 1)
                    System.out.println("[Summary:] Gross Profit/Loss = "+decimalFormatter.format(grossProfit)+", Net Profit/Loss = "+decimalFormatter.format(grossProfit-grossTrasactionCost));

            } else {
                System.out.println(" [Error] Failed to retrieve future prices for " + symbol);
            }
        } catch (Throwable t) {
            System.out.println("ATFutureThreadNaked.run() :: Problem occurred during processing: " + t.getMessage());
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
            if (price == -1) {
                //close the thread
                exchangeClosed = true;
                return;
            }
            if (true == checkNewPosition()) {
                placeNakedOrder();
            }
        }       
    }
    //update bid and ask sizes
    public void tickSize(int tickerId, int field, int size) {
        
    }
    
    public boolean checkNewPosition() {
        if ((this.startTrading == false) && (this.isPositionOpen==false)) {
            return false;
        }
        int i=0;
        int j=0;
        double diff=0;
        for (i=1;i<totFutures;i++) {
            //first try for buy of this future by selling stocks first     
            diff = (((1 + ( i + monthsToFirstFuture - 1)*(MIBORMonthlyInterest-shareLoanMonthlyInterest)) * this.futureBidPrices[0]) - this.futureAskPrices[i])/this.futureBidPrices[0];
            if (diff>(liquidityRatios[i]*buyProfitTrigger) && diff<maxPossibleProfit) {
                this.buyIndex = i;
                this.positionBUY = true;
                this.sellIndex = -1;
                this.positionDiff = diff;
                //Do not look for other positions as positions nearer to current date have more liquidity
                return true; 
            }
        }
        for (i=1;i<totFutures;i++) {
            //then try for sell this future by after buying the stocks now
            diff = (this.futureBidPrices[i] - ((1 + ( i + monthsToFirstFuture - 1)* MIBORMonthlyInterest) * this.futureAskPrices[0]))/this.futureAskPrices[0];
            if (diff>(liquidityRatios[i]*sellProfitTrigger) && diff<maxPossibleProfit) {
                this.positionBUY = false;
                this.sellIndex = i;
                this.buyIndex = -1;
                this.positionDiff = diff;
                //Do not look for other positions as positions nearer to current date have more liquidity
                return true; 
            }          
        }
        return false;
    }
    
    public void placeNakedOrder() {
        //Exit if there is an open position
        if (isPositionOpen == true) {
            //System.out.println("Position Open on "+symbol+" : Cannot place below order");
            //System.out.println("BUY "+symbol+"-"+futureDates[buyIndex]+" AT "+futureAskPrices[buyIndex]+" AND SELL "+symbol+"-"+futureDates[sellIndex]+" AT "+futureBidPrices[sellIndex]);
            return;
        }
        double limit;
        if (true == positionBUY) {
            position = "BUY";
            limit = futureAskPrices[buyIndex];
            positionIndex = buyIndex;
        }else {
            position = "SELL";
            limit = futureBidPrices[sellIndex];
            positionIndex = sellIndex;
        }        
        //Adjust the limit to conform to minimum tick size
        //Lets take Rs 0.10 as default
        limit = (Math.ceil(limit/tickSize))*tickSize;
        Order order = createOrder(position, defaultLotSize, "LMT",limit);
        //Order order = createOrder(position, 2, "LMT",limit);
        order.m_tif = "IOC"; //immediate or cancel
        positionOrderId = orderId++;
        eClientSocket.placeOrder(positionOrderId,contracts[positionIndex],order);
        isPositionOpen = true;
        Date curTime = new Date();
        System.out.println("["+timeFormatter.format(curTime)+"=>"+positionOrderId+"] Attempting " + defaultLotSize +" lots of " + symbol+"-"+futureDates[positionIndex]+" "+position+" AT " + decimalFormatter.format(limit)+ ", Diff %age = "+decimalFormatter.format(positionDiff*100));
        System.out.println("ASK\t\tBID");
        for (int i=0;i<totFutures;i++)
            System.out.println(futureAskPrices[i]+"\t\t"+futureBidPrices[i]);     
    }
    
    public void placeSquareOffOrder() {
        //If first order in the position was buy create a two sell orders
        //Both the orders will be part of an OCA
        //One will be square off order and the other one will be stop loss order
        double limit;
        double stopLoss;
        if (positionBUY == true) {
            limit = (1 + liquidityRatios[positionIndex] * buyTargetProfit) * positionPrice;
            stopLoss = (1 - liquidityRatios[positionIndex] * stopLossLimit) * positionPrice;
            squareOffPosition = "SELL";

        }else {
            limit = (1 - liquidityRatios[positionIndex] * sellTargetProfit) * positionPrice;
            stopLoss = (1 + liquidityRatios[positionIndex] * stopLossLimit) * positionPrice;
            squareOffPosition = "BUY";
        }
        //Change limit and stopLoss to minimum tick size
        limit = (Math.ceil(limit/tickSize))*tickSize;
        stopLoss = (Math.ceil(stopLoss/tickSize))*tickSize;
        //take the reverse position
        Order squareOffOrder = createOrder(squareOffPosition, positionLots, "LMT",limit);
        squareOffOrderId = orderId++;
        String curOCAGroup = symbol+(int)(Math.random()*1000) +String.valueOf(squareOffOrderId);
        squareOffOrder.m_ocaGroup = curOCAGroup;
        squareOffOrder.m_ocaType = 3;
        eClientSocket.placeOrder(squareOffOrderId,contracts[positionIndex],squareOffOrder);
        System.out.println("["+squareOffOrderId+"] Placing square off order on " + symbol+"-"+futureDates[positionIndex]+" "+squareOffPosition+" AT LIMIT = " + decimalFormatter.format(limit));
        //Now place stop loss order
        Order stopLossOrder = createOrder(squareOffPosition, positionLots, "STP",stopLoss);
        stopLossOrder.m_auxPrice = stopLoss;
        stopLossOrder.m_ocaGroup = curOCAGroup;
        stopLossOrder.m_ocaType = 3;
        stopLossOrderId = orderId++;
        eClientSocket.placeOrder(stopLossOrderId,contracts[positionIndex],stopLossOrder);
        System.out.println("["+stopLossOrderId+"] Placing stop-loss order on " + symbol+"-"+futureDates[positionIndex]+" "+squareOffPosition+" AT STOP-LOSS = "+decimalFormatter.format(stopLoss)); 
    }
    //EWrapper call
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        if (orderId == positionOrderId) {
            if (filled > 0) {
                //reset position order id
                positionOrderId = -1;
                positionLots = (filled/lotSize);
                positionPrice = avgFillPrice;
                positionTime = new Date();
                System.out.println("["+timeFormatter.format(positionTime)+"] Got "+ positionLots + " lots of " + symbol+"-"+futureDates[positionIndex]+" "+position + " AT "+positionPrice+" Status:"+status);
                placeSquareOffOrder();
                return;
            } else if (filled == 0){
                //Order is not filled..It may be cancelled because it is an IOC order
                //Close the position and look for new opportunity
                if (status.equals("Cancelled")) {
                    isPositionOpen = false;
                    positionOrderId = -1;
                    System.out.println("["+orderId+"] Unable to get " + symbol+"-"+futureDates[positionIndex]+" "+position);
                }
                return; 
            }
        }else if ((orderId == squareOffOrderId)) {
            if (status.equals("Cancelled")) {
                System.out.println("LMT Order Id "+ orderId+ " is cancelled");
                squareOffOrderId = -1;
                return;
            }
            if ((0 == remaining) && (filled > 0)) {
                //what if an order is partially filled ?????
                //reset squqre off id
                squareOffOrderId = -1;
                squareOffPrice = avgFillPrice;
                Date squareOffTime = new Date();
                System.out.println("["+orderId+"] Squared Off "+ positionLots + " lots of " + symbol+"-"+futureDates[positionIndex]+" "+squareOffPosition + " AT "+squareOffPrice);
                double profit = (squareOffPrice - positionPrice) * positionLots * lotSize;
                if (positionBUY == false)
                    profit = profit * (-1);
                lossCount--;
                cumProfit = cumProfit + profit;
                grossProfit = grossProfit + profit;
                double transactCost= positionLots * lotSize * positionPrice * transactionPercentage;
                cumNetProfit = cumProfit - transactCost;
                grossTrasactionCost = grossTrasactionCost + transactCost;
                double netProfit = grossProfit - grossTrasactionCost;
                System.out.println("["+timeFormatter.format(squareOffTime)+"] "+symbol+" position: Profit = "+ decimalFormatter.format(profit) + ", Total Profit/Loss = "+decimalFormatter.format(cumNetProfit)+ ", Gross Profit/Loss = "+decimalFormatter.format(grossProfit)+", Net Profit/Loss = "+decimalFormatter.format(netProfit));
                //Print log in CSV format
                System.out.println("[LOG],"+dateFormatter.format(squareOffTime)+","+timeFormatter.format(positionTime)+","+timeFormatter.format(squareOffTime)+","+symbol+","+futureDates[positionIndex]+","+positionIndex+","+position+","+positionLots+","+lotSize+","+positionPrice+","+squareOffPrice+","+profit+","+transactCost+","+(profit-transactCost)+","+decimalFormatter.format(positionDiff*100));
                isPositionOpen = false;
                System.out.println("ASK\t\tBID");
                for (int i=0;i<totFutures;i++)
                    System.out.println(futureAskPrices[i]+"\t\t"+futureBidPrices[i]); 
            }
            
        }else if (orderId == stopLossOrderId) {
            if (status.equals("Cancelled")) {
                System.out.println("STP Order Id "+ orderId+ " is cancelled");
                stopLossOrderId = -1;
                return;
            }
            if ((0 == remaining) && (filled > 0)) {
                //what if an order is partially filled ?????
                //reset stop Loss  off id
                stopLossOrderId = -1;
                stopLossPrice = avgFillPrice;
                Date stopLossTime = new Date();
                int diffTime = stopLossTime.compareTo(positionTime);
                System.out.println("["+orderId+"] Stop Loss on  "+ positionLots +" lots of "+ symbol+"-"+futureDates[positionIndex]+" "+squareOffPosition + " AT "+stopLossPrice);
                double profit = (stopLossPrice - positionPrice) * positionLots * lotSize;
                if (positionBUY == false)
                    profit = profit * (-1);
                //Increase loss count
                lossCount++;
                cumProfit = cumProfit + profit;
                grossProfit = grossProfit + profit;
                double transactCost= positionLots * lotSize * positionPrice * transactionPercentage;
                grossTrasactionCost = grossTrasactionCost + transactCost;
                double netProfit = grossProfit - grossTrasactionCost;
                System.out.println("["+timeFormatter.format(stopLossTime)+"]"+symbol+" position: Loss = "+ decimalFormatter.format(profit) + ", Total Profit/Loss = "+decimalFormatter.format(cumProfit)+ " Gross Profit/Loss = "+decimalFormatter.format(grossProfit)+", Net Profit/Loss = "+decimalFormatter.format(netProfit));
                //Print log in CSV format
                System.out.println("[LOG],"+dateFormatter.format(stopLossTime)+","+timeFormatter.format(positionTime)+","+timeFormatter.format(stopLossTime)+","+symbol+","+futureDates[positionIndex]+","+positionIndex+","+position+","+positionLots+","+lotSize+","+positionPrice+","+squareOffPrice+","+profit+","+transactCost+","+(profit-transactCost)+","+decimalFormatter.format(positionDiff*100));
                isPositionOpen = false;
                for (int i=0;i<totFutures;i++)
                    System.out.println(futureAskPrices[i]+"\t\t"+futureBidPrices[i]); 
            }
        } else {
            //Cancel the order
            if (previousOrderOpen){
                if (status.equals("Submitted")||status.equals("PreSubmitted")) {
                    previousOrderOpen = false;
                    eClientSocket.cancelOrder(orderId);
                    System.out.println("["+orderId+"] Cancelled the order for "+symbol );             
                }
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
            if ((contract.m_symbol.equals(symbol)) && (contract.m_secType.equals("FUT")) && (isPositionOpen == false)) {
                positionLots = (position/lotSize);
                String pos= "";
                if (position > 0){
                    positionBUY = true;
                    pos = "BUY";
                    this.position = "BUY";
                }else {
                    positionBUY = false;
                    positionLots = -1 * positionLots;
                    pos = "SELL";
                    this.position = "SELL";
                }
                positionPrice = averageCost;
                positionTime = new Date();
                positionTime.setTime(0);
                contract.m_exchange = "NSE";
                for (int i=0;i<totFutures;i++) {
                    if (futureDates[i].equals(contract.m_expiry)) {
                        positionIndex = i;
                    }
                }
                System.out.println("Open Position: "+ positionLots + " lots of " + symbol+"-"+futureDates[positionIndex]+" "+pos + " AT "+decimalFormatter.format(positionPrice)+" .");
                if (positionIndex != -1) {
                    placeSquareOffOrder();
                    isPositionOpen = true;
                }

            }
        }
        return;
    }           
}
