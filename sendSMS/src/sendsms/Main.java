package sendsms;

import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/*
 input parameters are: DEFAULT_LOG4J_PATH TO SUBJECT MESSAGE
 */
public class Main {

    static Properties props = System.getProperties();

    private static String DEFAULT_LOG4J_PATH;
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    public static ParseXMLUtilities xml;

    public static void main(String[] args) {
        if (args.length == 0 || args.length < 3) {
            System.err.println("ERROR; Input parameters example : java sendSMS pathToParameters.xml");
            return;
        } else {
            xml = new ParseXMLUtilities(args[0]);
            xml.initiate();
            DEFAULT_LOG4J_PATH = xml.getNodeValue(xml.getChildNodes("parameters"), "log4jPath");
//            Logging.put_log("args[0]=" + args[0]);
//            Logging.put_log("args[1]=" + args[1]);
//            Logging.put_log("args[2]=" + args[2]);
//            Logging.put_log("args[3]=" + args[3]);
        }
        String log4jPath = System.getProperty("jsmpp.client.log4jPath", DEFAULT_LOG4J_PATH);
        PropertyConfigurator.configure(log4jPath);

        Worker w = new Worker();
        try {
            try {
                w.start();
                w.send(args[3]);
            } catch (Exception e) {
            } finally {
                w.stop();
            }
        } catch (Exception e) {
        }

    }

}
