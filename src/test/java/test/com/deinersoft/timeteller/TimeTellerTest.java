package test.com.deinersoft.timeteller;

import com.deinersoft.timeteller.TimeFormatting;
import com.deinersoft.timeteller.TimeTeller;
import com.deinersoft.timeteller.TimeZone;
import com.sun.mail.imap.IMAPFolder;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

public class TimeTellerTest {

    private TimeTeller timeTeller;

    @Before
    public void initialize(){
         timeTeller = new TimeTeller();
    }

    @Test
    public void localTimeCurrent(){
        assertThat(timeTeller.getFormattedTime(TimeZone.LOCAL, TimeFormatting.NUMERIC,false), is(getFormattedTime(LocalDateTime.now())));
    }

    @Test
    public void zuluTimeCurrent(){
        assertThat(timeTeller.getFormattedTime(TimeZone.UTC, TimeFormatting.NUMERIC,false), is(getFormattedTime(LocalDateTime.now(Clock.systemUTC()))+"Z"));
    }

    @Test
    public void localTimeInWordsCurrent(){
        assertThat(timeTeller.getFormattedTime(TimeZone.LOCAL, TimeFormatting.APPROXIMATE_WORDING,false), matchesPattern("^(\\s|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|twenty|about|almost|a|little|after|quarter|half|of|past|before|at|night|in|the|morning|afternoon|evening|night)+$"));
    }

    @Test
    public void zuluTimeInWordsCurrent(){
        assertThat(timeTeller.getFormattedTime(TimeZone.UTC, TimeFormatting.APPROXIMATE_WORDING,false), matchesPattern("^(\\s|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|twenty|about|almost|a|little|after|quarter|half|of|past|before|at|night|in|the|morning|afternoon|evening|night)+Zulu$"));
    }

    @Test
    public void emailForLocalTime(){
        String localTimeNowFormatted = getFormattedTime(LocalDateTime.now());
        timeTeller.getFormattedTime(TimeZone.LOCAL, TimeFormatting.NUMERIC,true);

        boolean receivedEmail = false;
        for (int readAttempts = 1; (readAttempts <= 5) && (!receivedEmail); readAttempts++ ) {
            receivedEmail = lookForTimeTellerEmail(localTimeNowFormatted);
        }
        assertThat(receivedEmail, is(true));
    }

    private String getFormattedTime(LocalDateTime clock){
        int localHour = clock.getHour();
        int localMinute = clock.getMinute();
        int localSecond = clock.getSecond();
        return String.format("%02d:%02d:%02d", localHour, localMinute, localSecond);
    }

    private boolean lookForTimeTellerEmail(String localTimeNowFormatted){
        Properties localProperties = new Properties();
        try {
            InputStream input = new FileInputStream("config.properties");
            localProperties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean receivedEmail = false;
        IMAPFolder folder = null;
        Store store = null;
        try {
            Properties props = System.getProperties();
            props.setProperty("mail.store.protocol", "imaps");

            Session session = Session.getDefaultInstance(props, null);
            store = session.getStore("imaps");
            store.connect(localProperties.getProperty("imap.host.to.use"),localProperties.getProperty("imap.username.to.use"), localProperties.getProperty("imap.password.to.use"));

            folder = (IMAPFolder) store.getFolder("inbox");
            if(!folder.isOpen()) {
                folder.open(Folder.READ_WRITE);
                Message[] messages = folder.getMessages();
                for (Message msg : messages) {
                    if (msg.getSubject().equals(localProperties.getProperty("email.subject"))) {
                        if (((String) msg.getContent()).contains(localTimeNowFormatted)) {
                            receivedEmail = true;
                            msg.setFlag(Flag.DELETED, true);
                        }
                    }
                }
            }
        }
        catch (Exception e) { }
        finally {
            try {
                if (folder != null && folder.isOpen()) folder.close(true);
                if (store != null) store.close();
            }
            catch (Exception e) { }
        }

        if (!receivedEmail) {
            try { TimeUnit.SECONDS.sleep(1); }
            catch(InterruptedException e){ }
        }

        return receivedEmail;
    }
}