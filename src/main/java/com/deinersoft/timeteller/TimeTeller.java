package com.deinersoft.timeteller;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Properties;

public class TimeTeller {

    public static void main(String [] args) {

        System.out.println(getFormattedTime(TimeZone.LOCAL,TimeFormatting.NUMERIC,false));
        System.out.println(getFormattedTime(TimeZone.UTC, TimeFormatting.NUMERIC,false));
        System.out.println(getFormattedTime(TimeZone.LOCAL, TimeFormatting.APPROXIMATE_WORDING,false));
        System.out.println(getFormattedTime(TimeZone.UTC, TimeFormatting.APPROXIMATE_WORDING,false));
        System.out.println(getFormattedTime(TimeZone.LOCAL,TimeFormatting.NUMERIC,true));
        System.out.println(getFormattedTime(TimeZone.UTC,TimeFormatting.NUMERIC,true));
        System.out.println(getFormattedTime(TimeZone.LOCAL, TimeFormatting.APPROXIMATE_WORDING,true));
        System.out.println(getFormattedTime(TimeZone.UTC, TimeFormatting.APPROXIMATE_WORDING,true));

    }

    public static final int SECONDS_IN_A_HALF_MINUTE = 30;
    public static final int HOURS_IN_A_QUARTER_OF_A_DAY = 6;
    public static final int MINUTE_TO_START_FUZZY_WORDING = 3;
    public static final int MINUTE_TO_START_FUZZING_INTO_NEXT_HOUR = 35;

    public static String getFormattedTime(TimeZone whichTimeZone, TimeFormatting typeOfFormatting, boolean eMailTimeFlag) {

        String formattedTime = formatTime(whichTimeZone, typeOfFormatting);

        if (eMailTimeFlag) {
            try {
                sendEmail(formattedTime);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }

        return formattedTime;
    }

    private static String formatTime(TimeZone whichTimeZone, TimeFormatting typeOfFormatting) {
        String formattedTime = "";

        LocalDateTime localDateTime = getLocalDateTime(whichTimeZone);
        int hour = localDateTime.getHour();
        int minute = localDateTime.getMinute();
        int second = localDateTime.getSecond();

        switch (typeOfFormatting) {
            case NUMERIC:
                formattedTime = formatTimeNumeric(hour, minute, second);
                if (whichTimeZone == TimeZone.UTC) {
                    formattedTime += "Z";
                }
                break;
            case APPROXIMATE_WORDING:
                formattedTime = formatTimeApproximateWording(hour, minute, second);
                if (whichTimeZone == TimeZone.UTC) {
                    formattedTime += " Zulu";
                }
                break;
        }

        return formattedTime;
    }

    private static String formatTimeNumeric(int hour, int minute, int second){
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    private static String formatTimeApproximateWording(int hour, int minute, int second){
        String formattedTime = "";

        String[] namesOfTheHours = {"twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven"};
        String[] fuzzyTimeWords = {"", "almost ten after", "ten after", "a quarter after", "twenty after", "almost half past", "half past", "almost twenty before", "twenty before", "a quarter of", "ten of", "almost"};
        String[] quadrantOfTheDay = {"at night", "in the morning", "in the afternoon", "in the evening"};

        if (second >= SECONDS_IN_A_HALF_MINUTE) minute++;

        if (minute >= MINUTE_TO_START_FUZZY_WORDING) {
            formattedTime += fuzzyTimeWords[(minute+2)/5] + " ";
        }

        if (minute < MINUTE_TO_START_FUZZING_INTO_NEXT_HOUR) {
            formattedTime += namesOfTheHours[hour % namesOfTheHours.length];
        }  else {
            formattedTime += namesOfTheHours[(hour+1) % namesOfTheHours.length];
        }

        formattedTime += " " + quadrantOfTheDay[hour/HOURS_IN_A_QUARTER_OF_A_DAY];

        return formattedTime;
    }

    private static LocalDateTime getLocalDateTime(TimeZone whichTimeZone) {
        LocalDateTime localDateTime = null;
        switch (whichTimeZone) {
            case LOCAL:
                localDateTime = LocalDateTime.now();
                break;
            case UTC:
                localDateTime = LocalDateTime.now(Clock.systemUTC());
                break;
        }
        return localDateTime;

    }

    private static void sendEmail(String formattedTime) throws MessagingException {
        Properties localConfiguration = getLocalConfiguration();
        Session eMailSession = getEmailSession(localConfiguration);
        Message message = new MimeMessage(eMailSession);
        message.setFrom(new InternetAddress(localConfiguration.getProperty("email.sender")));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(localConfiguration.getProperty("email.recipient")));
        message.setSubject(localConfiguration.getProperty("email.subject"));
        message.setText(localConfiguration.getProperty("email.message") + " " + formattedTime);

        Transport.send(message);
    }

    private static Session getEmailSession(Properties localConfiguration) {
        Properties systemConfiguration = getSystemConfiguration(localConfiguration);
        return Session.getInstance(systemConfiguration,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(localConfiguration.getProperty("smtp.username.to.use"), localConfiguration.getProperty("smtp.password.to.use"));
                    }
                });
    }

    private static Properties getLocalConfiguration() {
        Properties localConfiguration = new Properties();

        try {
            InputStream input = new FileInputStream("config.properties");
            localConfiguration.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return localConfiguration;
    }

    private static Properties getSystemConfiguration(Properties localConfiguration) {
        Properties systemProperties = System.getProperties();

        systemProperties.put("mail.smtp.auth", localConfiguration.getProperty("smtp.authentication.enabled"));
        systemProperties.put("mail.smtp.starttls.enable", localConfiguration.getProperty("smtp.starttls.enabled"));
        systemProperties.put("mail.smtp.host", localConfiguration.getProperty("smtp.host.to.use"));
        systemProperties.put("mail.smtp.port", localConfiguration.getProperty("smtp.port.to.use"));

        return systemProperties;
    }
}