package net.asmrcraft.deposits;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public final class Deposits extends JavaPlugin implements CommandExecutor {
    static final String DB_URL = "jdbc:mysql://localhost/";
    static final String USER = "admin";
    static final String PASS = "admin";
    static final String TABLENAME = "DEPOSITS";
    static final String DATABASENAME = "DEPOSITS";
    static final String TABLECREATE = String.format("CREATE TABLE %s.%s (Username varchar(50), " +
            "CreateTimestamp varchar(50)," +
            "Amount integer," +
            "Duration integer," +
            "EndingTimestamp varchar(50)," +
            "Interest float)", DATABASENAME, TABLENAME);
    String PATH;
    public static Economy econ = null;



    @Override
    public void onEnable() {
        // Config
        saveDefaultConfig();

        // Check economy plugin
        if (!setupEconomy() ) {
            System.out.println("No Economy Plugin Found.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupEconomy();

        System.out.println("==================================== Deposits ====================================");

        // Create A folder for data file, if it doesn't exist
        String userDir = System.getProperty("user.dir");
        System.out.println("Current Path : " + userDir);

        System.out.println("Verifying or creating new folder.");
        try {
            Files.createDirectories(Paths.get(PATH + "\\plugins\\Deposits"));
        } catch (IOException e){
            System.out.println("Error creating a new folder.");
            e.printStackTrace();
            return;
        }
        System.out.println("Folder Verified/Created");

        // Save info about the path
        this.PATH = userDir + "\\plugins\\Deposits";

        // Create Database if it doesn't already exist
        try(Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
            Statement statement = conn.createStatement()) {

            String sql = String.format("CREATE DATABASE %s", DATABASENAME);
            statement.executeUpdate(sql);
            System.out.println("Database created successfully...");

        } catch (SQLException e) {
            if (e.getErrorCode() == 1007){
                System.out.println("Database already created, skipping.");
            } else {
                e.printStackTrace();
            }
        }

        // Create the Deposits Table if it doesn't already exist
        try(Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
            Statement statement = conn.createStatement()) {


            try {
                statement.execute(TABLECREATE);
                System.out.println("Table Created Successfully!");

            } catch (SQLException e) {
                if (e.getErrorCode() == 1050){
                    System.out.println("Table Already Exists!");
                } else {
                    e.printStackTrace();
                }

            }
        }   catch (SQLException e) {
            e.printStackTrace();
            }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private boolean setupEconomy() {
        // If vault detects no economy, don't activate plugin
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();

        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    @SuppressWarnings("SpellCheckingInspection")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        // Check if the command is a deposit command
        if (command.getName().equalsIgnoreCase("deposit")) {
            Plugin plugin = getServer().getPluginManager().getPlugin("Vault");


            // Since this plugin only works from players POV, check if player is doing it!
            if (sender instanceof Player){
                Player p = (Player) sender;
                String playerName = p.getName();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String currentTimeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());

                if (args.length > 0){
                    // Since correct synax is 'deposit <create> <amount> <days>', check if we have 3 values inputed.
                    if (args.length == 3){
                        if (args[0].equalsIgnoreCase("create")){
                            int amount;
                            int min_deposit = (int) getConfig().get("min_deposit");
                            int max_deposit = (int) getConfig().get("max_deposit");

                            try{
                                amount = Integer.parseInt(args[1]);
                            } catch (NumberFormatException e){
                                p.sendMessage("Please input an integer, as the amount!");
                                return true;
                            }

                            if (amount < min_deposit || amount > max_deposit){
                                p.sendMessage(String.format("The amount must be between: $%,d and $%,d", min_deposit, max_deposit));
                                return true;
                            }

                            int days;
                            int max_days = (int) getConfig().get("max_days");

                            try{
                                days = Integer.parseInt(args[2]);
                            } catch (NumberFormatException e){
                                p.sendMessage("Please input an integer, as the amount of days!");
                                return true;
                            }

                            if (days > max_days){
                                p.sendMessage(String.format("Days must be %d or lower!", max_days));
                                return true;
                            }
                            double ddays = (double) days;

                            // Needed for date differenec calcuation.
                            Calendar cal = Calendar.getInstance();

                            try{
                                cal.setTime(sdf.parse(currentTimeStamp));
                            } catch (ParseException e) {
                                p.sendMessage(playerErrorMessage());
                                e.printStackTrace();
                            }

                            cal.add(Calendar.DAY_OF_MONTH, days);

                            String endingTimeStamp = sdf.format(cal.getTime());

                            // Profit
                            double r = ddays / 100.0 * Math.pow((1.0 + ddays/100.0), Math.log(ddays)/Math.log(2.0)) * 100;
                            r = Math.floor(r * 100) / 100;


                            // Check if player already has a deposit
                            try(Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
                                Statement statement = conn.createStatement()) {

                                String query = String.format("select count(1) from %s.%s where Username = '%s'",
                                                            DATABASENAME, TABLENAME, playerName);
                                ResultSet existingDeposit = statement.executeQuery(query);
                                existingDeposit.next();

                                // returns 1 if already existing deposit
                                if (Integer.parseInt(existingDeposit.getString(1)) != 0){
                                    p.sendMessage("You already have a deposit!");
                                    return true;
                                } else {
                                    // If no deposit, then create one, but check if they have enough money beforehands.
                                    Economy eco = getEconomy();

                                    if (eco.hasAccount(p)) {
                                        if (eco.has(p, amount)) {
                                            String createQuery = String.format("insert into %s.%s (Username, CreateTimestamp, Amount, Duration, EndingTimestamp, Interest) " +
                                                            "values('%s','%s',%d,%d,'%s',%3.2f)", DATABASENAME, TABLENAME,
                                                    playerName, currentTimeStamp, amount, days, endingTimeStamp, r);
                                            statement.execute(createQuery);

                                            eco.withdrawPlayer(p, amount);


                                            p.sendMessage("Congrats! Your Deposit has been created. You can use '/deposit info' to check information about the deposit.");
                                        } else {
                                            p.sendMessage("You don't have enough money!");
                                        }
                                    }
                                }
                            } catch (SQLException e) {
                                p.sendMessage(playerErrorMessage());
                                e.printStackTrace();
                            }





                        }
                    } else if (args.length == 1) {
                        if (args[0].equalsIgnoreCase("info")) {

                            // Check if player already has a deposit
                            try(Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
                                Statement statement = conn.createStatement()) {

                                String query = String.format("select count(1) from %s.%s where Username = '%s'",
                                        DATABASENAME, TABLENAME, playerName);
                                ResultSet existingDeposit = statement.executeQuery(query);
                                existingDeposit.next();

                                // returns 1 if already existing deposit
                                if (Integer.parseInt(existingDeposit.getString(1)) == 0){
                                    p.sendMessage("You Don't have a deposit! Create one using '/deposit create <amount> <days>'");
                                    return true;
                                } else {
                                    // If no deposit, then create one
                                    String infoQuery = String.format("select * from %s.%s where Username = '%s'",
                                            DATABASENAME, TABLENAME, playerName);

                                    ResultSet depositSet = statement.executeQuery(infoQuery);
                                    depositSet.next();

                                    Date endingTimestampDate = sdf.parse(depositSet.getString(5));
                                    Date curentTimestampDate = sdf.parse(currentTimeStamp);

                                    long remainingTime = (endingTimestampDate.getTime() - curentTimestampDate.getTime()) / 1000;

                                    p.sendMessage("Username: " + depositSet.getString(1) + "\n" +
                                            "Amount: " + depositSet.getString(3) + "\n" +
                                            "Duration: " + depositSet.getString(4) + "\n" +
                                            "Interest: " + depositSet.getString(6) + "%" + "\n" +
                                            String.format("Remaining Time: %d:%02d:%02d", remainingTime / (3600*24),
                                                    (remainingTime % (3600*24)) / 3600, ((remainingTime % (3600*24)) % 3600) / 60));

                                }
                            } catch (SQLException | ParseException e) {
                                p.sendMessage(playerErrorMessage());
                                e.printStackTrace();
                            }

                        } else if (args[0].equalsIgnoreCase("delete")) {

                            // Check if player already has a deposit
                            try(Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
                                Statement statement = conn.createStatement()) {

                                Economy eco = getEconomy();

                                String amountQuery = String.format("select Amount from %s.%s where Username = '%s'",
                                        DATABASENAME, TABLENAME, playerName);

                                ResultSet depositSet = statement.executeQuery(amountQuery);
                                depositSet.next();

                                eco.depositPlayer(p, Integer.parseInt(depositSet.getString(1)));

                                String query = String.format("delete from %s.%s where Username = '%s'",
                                        DATABASENAME, TABLENAME, playerName);
                                statement.execute(query);
                                p.sendMessage("Your Deposit has been deleted!");

                                } catch (SQLException e) {
                                p.sendMessage(playerErrorMessage());
                                e.printStackTrace();
                            }
                        } else if (args[0].equalsIgnoreCase("claim")) {

                            // Check if player already has a deposit
                            try(Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
                                Statement statement = conn.createStatement()) {

                                Economy eco = getEconomy();

                                String amountQuery = String.format("select EndingTimestamp,Amount,Interest from %s.%s where Username = '%s'",
                                        DATABASENAME, TABLENAME, playerName);

                                ResultSet depositSet = statement.executeQuery(amountQuery);
                                depositSet.next();

                                Date endingTimestampDate = sdf.parse(depositSet.getString(1));
                                Date curentTimestampDate = sdf.parse(currentTimeStamp);

                                long remainingTime = (endingTimestampDate.getTime() - curentTimestampDate.getTime()) / 1000;

                                if (endingTimestampDate.after(curentTimestampDate)){
                                    int Amount = Integer.parseInt(depositSet.getString(2));
                                    double Interest = Double.parseDouble(depositSet.getString(3));

                                    double depositAmount = Amount * (1 + Interest/100);

                                    eco.depositPlayer(p, depositAmount);

                                    String query = String.format("delete from %s.%s where Username = '%s'",
                                            DATABASENAME, TABLENAME, playerName);
                                    statement.execute(query);
                                    p.sendMessage(String.format("$%.2f has been added to your account!", depositAmount));
                                } else {
                                    p.sendMessage(String.format("You can't claim the deposit yet! The remaining time is: %d:%02d:%02d",
                                            remainingTime / (3600*24), (remainingTime % (3600*24)) / 3600, ((remainingTime % (3600*24)) % 3600) / 60));
                                }

                            } catch (SQLException | ParseException e) {
                                p.sendMessage(playerErrorMessage());
                                e.printStackTrace();
                            }
                        }


                    } else {
                        // Here when arg length >0, but not 1 or 3.
                        return false;
                    }

                } else
                {
                    // Here when args lenght is 0.
                    return false;
                }

            } else {
                // Here when the command isn't executed by a player.
                System.out.println("This plugin currently only works from players side.");
            }


        }

        return true;

    }

    public String playerErrorMessage(){
        return "Something went wrong! Please contact Admins to report it.";
    }

}
