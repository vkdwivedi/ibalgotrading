/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 * 
 */
package com.ib.client.AlgoTrading;

import java.util.Properties;
import java.io.*;
/**
 *
 * @author vkd
 * This class contains the main function which starts threads for each future option
 * This will get the most profitable future based on the current stock price and 
 * if successful in capturing, create  a bracket order immediately.
 * This limits the loss while the probability of success is high.
 * 
 */
public class ATFutures {
    private static final String PROPERTY_FILE = "AT.properties";
    private static Properties ATPoperties = null;
    private static String[] symbols = null;
    private static int totSymbols = 0;
    private static int[] symbolLotSizes = null;
    private static double[] symbolPortfolioLimitsInLakhs = null;
    private static int futureAlgo = -1;
   
    public static void main(String[] args) {
        //Read properties file
        try {
            InputStream is = new FileInputStream(PROPERTY_FILE);
            ATPoperties = new Properties();
            ATPoperties.load(is);
        }
        catch (Exception e) {
            System.out.println("Cannot read properties file..exiting");
            return;
        }
        //Property File is loaded..read the values
        symbols=ATPoperties.getProperty("SYMBOLS").split(",");
        totSymbols = symbols.length;
        String[] symbolLotSizesString = ATPoperties.getProperty("SYMBOL_LOT_SIZES").split(",");
        symbolLotSizes = new int[totSymbols];
        for (int i=0;i<totSymbols;i++) 
            symbolLotSizes[i] = Integer.parseInt(symbolLotSizesString[i]);
        String[] symbolPortfolioLimitsInLakhsString = ATPoperties.getProperty("SYMBOL_PORTFOLIO_LIMITS").split(",");
        symbolPortfolioLimitsInLakhs = new double[totSymbols];
        for (int i=0;i<totSymbols;i++) 
            symbolPortfolioLimitsInLakhs[i] = Double.parseDouble(symbolPortfolioLimitsInLakhsString[i]);
        futureAlgo = Integer.parseInt(ATPoperties.getProperty("FUTURE_ALGO"));
        //Print the parameters
        System.out.println("-------System Properties-------------");
        System.out.println("ACCOUNT_CODE="+ATPoperties.getProperty("ACCOUNT_CODE"));
        System.out.println("MONTHS_TO_FIRST_FUTURE="+ATPoperties.getProperty("MONTHS_TO_FIRST_FUTURE"));
        System.out.println("MIBOR_MONTHLY_INTEREST="+ATPoperties.getProperty("MIBOR_MONTHLY_INTEREST"));
        System.out.println("SHARE_LOAN_MONTHLY_INTEREST="+ATPoperties.getProperty("SHARE_LOAN_MONTHLY_INTEREST"));
        System.out.println("SELL_PROFIT_TRIGGER="+ATPoperties.getProperty("SELL_PROFIT_TRIGGER"));
        System.out.println("SELL_TARGET_PROFIT="+ATPoperties.getProperty("SELL_TARGET_PROFIT"));
        System.out.println("BUY_PROFIT_TRIGGER="+ATPoperties.getProperty("BUY_PROFIT_TRIGGER"));
        System.out.println("BUY_TARGET_PROFIT="+ATPoperties.getProperty("BUY_TARGET_PROFIT"));
        System.out.println("STOP_LOSS_LIMIT="+ATPoperties.getProperty("STOP_LOSS_LIMIT"));
        System.out.println("DEFAULT_LOT_SIZE="+ATPoperties.getProperty("DEFAULT_LOT_SIZE"));
        System.out.println("LIQUIDITY_RATIOS="+ATPoperties.getProperty("LIQUIDITY_RATIOS"));
        System.out.println("BUY_POSITION_RATIO="+ATPoperties.getProperty("BUY_POSITION_RATIO"));
        System.out.println("SELL_POSITION_RATIO="+ATPoperties.getProperty("SELL_POSITION_RATIO"));
        System.out.println("READJUST_TRIGGER="+ATPoperties.getProperty("READJUST_TRIGGER"));
        System.out.println("-------------------------------------");
                
        
        
        //All the values are read.. start now
        if (0 == futureAlgo) {
            ATFutureThreadDynamic[] futThreads = new ATFutureThreadDynamic[totSymbols];
            for (int i=0;i<totSymbols;i++) {
                //run the thread for each symbol but stop after two consecutive losses
                int clientId = i+1;
                int requestIdBase = i+1;
                //some problem in this code..do not call
                futThreads[i] = new ATFutureThreadDynamic(symbols[i],symbolPortfolioLimitsInLakhs[i],symbolLotSizes[i],requestIdBase,clientId,ATPoperties);
                futThreads[i].start(); 
            }
        }
        else if (1 == futureAlgo) {
            ATFutureThreadNaked[] futThreads = new ATFutureThreadNaked[totSymbols];
            for (int i=0;i<totSymbols;i++) {
                //run the thread for each symbol but stop after two consecutive losses
                int clientId = i+1;
                int requestIdBase = (i+1);
                futThreads[i] = new ATFutureThreadNaked(symbols[i],symbolPortfolioLimitsInLakhs[i],symbolLotSizes[i],requestIdBase,clientId,ATPoperties);
                futThreads[i].start(); 
            }
        }
    }   
}
