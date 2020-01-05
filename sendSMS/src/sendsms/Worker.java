package sendsms;

import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.TimeFormatter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Random;
import static sendsms.Main.xml;

/**
 * Created by anatoliyvazhenin on 12/22/14.
 */
public class Worker {

    private SMPPSession session = null;
    private String SMPP_LOGIN;
    private String SMPP_PASS;
    private String SMPP_IP;
    private int SMPP_PORT;

    private static TimeFormatter timeFormatter = new AbsoluteTimeFormatter();
    private byte SMPP_PROTOCOL_ID;
    private byte SMPP_PRIORITY_FLAG;
    private byte SMPP_REP_IF_P_FLAG;
    private String SOURCE_NUMBER;
    private String DEST_NUMBER;
    private final int MAX_MSG_LENGTH = 126;
    private ArrayList<String> recipient_List = new ArrayList<>();
    private boolean IF_SMS = false;
    private String[] allowedIP;

    void start() throws IOException {
        loadParameters();
        session = new SMPPSession();
        session.connectAndBind(SMPP_IP, SMPP_PORT,
                new BindParameter(
                        BindType.BIND_TX,
                        SMPP_LOGIN,
                        SMPP_PASS,
                        "cp",
                        TypeOfNumber.UNKNOWN,
                        NumberingPlanIndicator.UNKNOWN,
                        null));
    }

    void stop() throws IOException {
        if (session != null) {
            session.unbindAndClose();
        }
    }

    void send(String messageText) throws PDUException, ResponseTimeoutException, InvalidResponseException, NegativeResponseException, IOException {

        GeneralDataCoding coding = new GeneralDataCoding(
                false,
                false,
                MessageClass.CLASS0,
                Alphabet.ALPHA_UCS2);

        int totalSegments = 1;
        Random random = new Random();
        OptionalParameter sarMsgRefNum = OptionalParameters.newSarMsgRefNum((short) random.nextInt());
        OptionalParameter sarTotalSegments = OptionalParameters.newSarTotalSegments(totalSegments);
        OptionalParameter sarSegmentSeqnum = OptionalParameters.newSarSegmentSeqnum(totalSegments);
        ArrayList<String> msg = new ArrayList<>();

        /**
         * parse messaget before we send it if message exceeds limit of 126
         * characters, then we split it but we split it nicely, so it will look
         * neat in a cell phone
         */
        msg = parseMsg(messageText);
        /* check if message could be sent */
        setIF_SMS(msg);

        /* when IF_SMS is true , we send message to recipients, otherwise we don't */
        if (IF_SMS) {
            try {
                /**
                 * loop through recipients list
                 */
                for (int o = 0; o < recipient_List.size(); o++) {
                    DEST_NUMBER = recipient_List.get(o); // get number
                    /**
                     * loop through messages array, array size = amount of
                     * messages to be sent
                     */
                    for (int i = 0; i < msg.size(); i++) {
                        String get = msg.get(i);
                        String messageId = session.submitShortMessage(
                                "CMT",
                                TypeOfNumber.valueOf((byte) 0), /* TON */
                                NumberingPlanIndicator.valueOf((byte) 1), /* NPI */
                                SOURCE_NUMBER,
                                TypeOfNumber.valueOf((byte) 1), /* TON */
                                NumberingPlanIndicator.valueOf((byte) 1), /* NPI */
                                DEST_NUMBER,
                                new ESMClass(),
                                SMPP_PROTOCOL_ID, // (byte)1
                                SMPP_PRIORITY_FLAG, // (byte)1
                                null,
                                null,
                                new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT),
                                SMPP_REP_IF_P_FLAG, // (byte)0
                                coding,
                                (byte) 1,
                                msg.get(i).getBytes(Charset.forName("UTF-16")),
                                sarMsgRefNum,
                                OptionalParameters.newSarSegmentSeqnum(1),
                                OptionalParameters.newSarTotalSegments(1));

                        System.out.println("Message submitted, message_id is " + messageId);
                    }
                }
            } catch (Exception e) {
                Logging.put_log(e);
            }
        } else {
            Logging.put_log("Message didnot meet requirements to be sent. \n" + msg);
        }

    }

    /**
     * here we check body content and cut useless info, to reduce body size Also
     * we format message the way that it would look nice when delivered to the
     * cell phone
     */
    ArrayList<String> parseMsg(String text) {
        String line = new String(), temp = new String();
        ArrayList<String> result = new ArrayList<>();

        if (text.length() > MAX_MSG_LENGTH) {
            /* convert string to char array */
//            char[] msg = Main.xml.getNodeValue(xml.getChildNodes("parameters"), "msg").toCharArray();
            char[] msg = text.toCharArray();

            /* loop , when we find (char)10 or (char)10, consider it as the line end*/
            for (int i = 0; i < msg.length; i++) {
                char n = msg[i];

                /* if we reached the end of the line, we consider the content */
                if ((char) n == 10 || (char) n == 13) {
                    /* check if we need this message to be included in the body */
                    if (!ifUselessMSG(temp)) {
                        /**
                         * if concatenated line length > MAX_MSG_LENGTH, then we
                         * don't concatenate line + temp + (char) 10, we first
                         * add line to the messages array list after we
                         * concatenate line + temp + (char) 10
                         */
                        if ((line + temp + (char) 10).length() > 126) {
                            result.add(line);
                            line = temp + (char) 10;
                        } else {
                            line += temp + (char) 10;
                        }
                        temp = new String();
                    } else {
                        temp = new String();
                    }

                } else { /* if it's not a line end, we continue line forming */

                    temp += n;
                }

            }
            /* check if we need this message to be included in the body */
            if (!ifUselessMSG(temp)) {
                result.add(temp + (char) 10);
            }
            /**
             * if we quit the loop, but line variable still has content , we
             * consider it as a message part and add it to the messages array
             * list
             */
            if (line.length() != 0) {
                result.add(line);
            }
        } /**
         * if message length less than MAX_MSG_LENGTH, we just simply add text
         * to the messages array list
         */
        else {
            result.add(text);
        }
        return result;
    }

    /* contains list of messages to be excluded from body */
    boolean ifUselessMSG(String line) {
        if (line.trim().indexOf("UNKNOWN") != -1
                || line.trim().indexOf("Trigger severity:") != -1
                || line.trim().indexOf("Trigger URL:") != -1
                || line.trim().indexOf("Item values:") != -1
                || line.trim().indexOf("Host") != -1
                || line.trim().indexOf("Original event ID") != -1
                || line.trim().length() == 0) {
            return true;
        }
        return false;
    }

    /* load xml file parameters */
    void loadParameters() {
        SMPP_LOGIN = Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smppLogin");
        SMPP_PASS = Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smppPass");
        SMPP_IP = Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smppIP");
        SMPP_PORT = Integer.valueOf(Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smppPort"));
        SMPP_PROTOCOL_ID = Byte.valueOf(Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smppProtocolID"));
        SMPP_PRIORITY_FLAG = Byte.valueOf(Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smppPriorityFlag"));
        SMPP_REP_IF_P_FLAG = Byte.valueOf(Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smpp_ref_if_p_flag"));
        SOURCE_NUMBER = Main.xml.getNodeValue(Main.xml.getChildNodes("parameters"), "smppSourceNumber");
        fillRecipientsList();
        allowedIP = xml.getNodeArrayValues("monitoredIP");
    }

    /* this method intends to find an IP address in a message , that we allowed to send it with */
    void setIF_SMS(ArrayList<String> message) {
        /* loop through ip addresses we got from parameters file */
        for (int i = 0; i < allowedIP.length; i++) {
            String allowedIP1 = allowedIP[i];
            /* loop through message and search an IP address in the line */
            for (int j = 0; j < message.size(); j++) {
                if (message.get(j).trim().toLowerCase().indexOf(allowedIP1) != -1) {
                    /* if we found such line, we set IF_SMS = true, it means that sms will be sent */
                    IF_SMS = true;
                }
            }
        }

    }

    /* fill recipients list , who we want to deliver messages to */
    void fillRecipientsList() {
        String list = Main.xml.getNodeValue(xml.getChildNodes("parameters"), "smppDestNumbers"), temp = "";

        while (true) {
            if (list.indexOf(',') != -1) {
                temp = list.substring(0, list.indexOf(','));
                list = list.substring(temp.length() + 1, list.length());
            } else {
                temp = list.trim();
                recipient_List.add(temp);
                break;
            }
//            System.out.println(temp);
            recipient_List.add(temp);

            if (list.trim().length() == 0) {
                break;
            }
        }
    }
}
