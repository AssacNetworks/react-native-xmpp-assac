package rnxmpp.service;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.WindowManager;

import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.StringReader;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.carbons.packet.CarbonExtension;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoMessage;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener;
import org.jivesoftware.smackx.omemo.signal.SignalCachingOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback;
import org.jivesoftware.smackx.omemo.trust.TrustState;
import org.jivesoftware.smackx.push_notifications.PushNotificationsManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;
import org.jivesoftware.smackx.search.ReportedData;
import org.jivesoftware.smackx.search.UserSearchManager;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import rnxmpp.R;
import rnxmpp.database.MessagesDbHelper;
import rnxmpp.ssl.UnsafeSSLContext;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */
public class XmppServiceSmackImpl implements XmppService, ChatManagerListener, StanzaListener, ConnectionListener, ChatMessageListener, RosterLoadedListener {

    public static final String JSON_TEXT = "text";
    public static final String JSON_ID = "_id";
    public static final String JSON_CREATED_AT = "createdAt";
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
    DeliveryReceiptManager deliveryReceiptManager;
    OmemoManager omemoManager;
    SignalOmemoService service;
    XMPPTCPConnection connection;
    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String password;
    String extension;
    private ReactApplicationContext reactApplicationContext;
    private final String CHAR_LIST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    private HashMap<Chat, Message> lostMessages = null;
    private boolean isOmemoInitialized = false;
    private String ENCRYPTED_XML_TAG = "encrypted";
//    FileTransferManager manager;

    private OmemoTrustCallback trustCallback = new OmemoTrustCallback() {
        @Override
        public TrustState getTrust(OmemoDevice device, OmemoFingerprint fingerprint) {
            return TrustState.trusted;
        }

        @Override
        public void setTrust(OmemoDevice device, OmemoFingerprint fingerprint, TrustState state) {

        }
    };

    private OmemoMessageListener messageListener = new OmemoMessageListener() {
        @Override
        public void onOmemoMessageReceived(Stanza stanza, OmemoMessage.Received decryptedMessage) {
            logger.log(Level.INFO, "in the on message receive");
            if (decryptedMessage.isKeyTransportMessage()) {
                return;
            }

            if (appIsInForground()) {
                xmppServiceListener.onOmemoMessage(stanza, decryptedMessage);
            } else {
                insertMessageToDB(stanza, decryptedMessage);
            }
        }

        @Override
        public void onOmemoCarbonCopyReceived(CarbonExtension.Direction direction, Message carbonCopy, Message wrappingMessage, OmemoMessage.Received decryptedCarbonCopy) {

        }
    };

    public XmppServiceSmackImpl(ReactApplicationContext reactApplicationContext) {
        this.xmppServiceListener = new RNXMPPCommunicationBridge(reactApplicationContext);
        this.reactApplicationContext = reactApplicationContext;
    }

    @Override
    public void trustHosts(ReadableArray trustedHosts) {
        for (int i = 0; i < trustedHosts.size(); i++) {
            this.trustedHosts.add(trustedHosts.getString(i));
        }
    }

    @Override
    public void connect(String jid, String password, String authMethod, String hostname, Integer port) {
        final String[] jidParts = jid.split("@");
        String[] serviceNameParts = jidParts[1].split("/");
        String serviceName = serviceNameParts[0];

        XMPPTCPConnectionConfiguration.Builder confBuilder = null;
        try {
            confBuilder = XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(serviceName)
                    .setUsernameAndPassword(jidParts[0], password)
                    .setConnectTimeout(3000)
                    //.setDebuggerEnabled(true)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.required);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }

        try {
            if (serviceNameParts.length > 1) {
                confBuilder.setResource(serviceNameParts[1]);
            } else {
                confBuilder.setResource(Long.toHexString(Double.doubleToLongBits(Math.random())));
            }
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        if (hostname != null) {
            confBuilder.setHost(hostname);
        }
        if (port != null) {
            confBuilder.setPort(port);
        }
        if (trustedHosts.contains(hostname) || (hostname == null && trustedHosts.contains(serviceName))) {
            confBuilder.setCustomSSLContext(UnsafeSSLContext.INSTANCE.getContext());
        }
        XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
        SmackConfiguration.DEBUG = true;
        connection = new XMPPTCPConnection(connectionConfiguration);

        connection.addAsyncStanzaListener(this, new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class)));
        connection.addConnectionListener(this);

        ChatManager.getInstanceFor(connection).addChatListener(this);
        roster = Roster.getInstanceFor(connection);
        roster.addRosterLoadedListener(this);

        // Create the listener for file sending

        // manager = FileTransferManager.getInstanceFor(connection);
        // manager.addFileTransferListener(new FileTransferListener() {
        //     public void fileTransferRequest(FileTransferRequest request) {
        //     // Check to see if the request should be accepted
        //     try {
        //         // Accept it
        //         IncomingFileTransfer transfer = request.accept();
        //          transfer.recieveFile(new File("/storage/emulated/0/" + request.getFileName()));
        //     } catch(IOException | SmackException ex) {

        //     }
        // }
        // });

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (connection.isConnected()) {
                        connection.disconnect();
                    }

                    if (lostMessages == null) {
                        lostMessages = new HashMap<>();
                    }

                    connection.connect().login();

                    DeliveryReceiptManager.getInstanceFor(connection).setAutoReceiptMode(DeliveryReceiptManager.AutoReceiptMode.always);
                    deliveryReceiptManager.getInstanceFor(connection).autoAddDeliveryReceiptRequests();
                    deliveryReceiptManager.getInstanceFor(connection).addReceiptReceivedListener(new ReceiptReceivedListener() {
                        @Override
                        public void onReceiptReceived(Jid fromJid, Jid toJid, String receiptId, Stanza receipt) {
                            logger.log(Level.WARNING, "recepit ");
                            XmppServiceSmackImpl.this.xmppServiceListener.onMessageReceipt(receiptId);
                        }
                    });

                    SignalOmemoService.acknowledgeLicense();
                    if (!SignalOmemoService.isServiceRegistered()) {
                        SignalOmemoService.setup();
                        service = (SignalOmemoService) SignalOmemoService.getInstance();
                        service.setOmemoStoreBackend(new SignalCachingOmemoStore(new SignalFileBasedOmemoStore(new File(reactApplicationContext.getCacheDir().getPath()))));

                        omemoManager = OmemoManager.getInstanceFor(connection);
                        omemoManager.setTrustCallback(trustCallback);
                        omemoManager.addOmemoMessageListener(messageListener);

                        omemoManager.initializeAsync(new OmemoManager.InitializationFinishedCallback() {
                            @Override
                            public void initializationFinished(OmemoManager manager) {
                                try {
                                    isOmemoInitialized = true;
                                    omemoManager.purgeDeviceList();

                                    sendLostMessages();

                                    xmppServiceListener.onOmemoInitResult(true);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Exception: ", e);
                                }
                            }

                            @Override
                            public void initializationFailed(Exception cause) {
                                xmppServiceListener.onOmemoInitResult(false);
                            }
                        });
                    } else {
                        omemoManager = OmemoManager.getInstanceFor(connection);
                        omemoManager.setTrustCallback(trustCallback);
                        omemoManager.addOmemoMessageListener(messageListener);
                    }

                } catch (XMPPException | SmackException | InterruptedException | IOException e) {
                    logger.log(Level.SEVERE, "Could not login for user " + jidParts[0], e);
                    if (e instanceof SASLErrorException) {
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    } else {
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Exception: ", e);
                }
                return null;
            }

            private void sendLostMessages() throws SmackException.NotLoggedInException {
                if (lostMessages.size() > 0) {
                    OmemoService omemoService = OmemoService.getInstance();
                    for (HashMap.Entry<Chat, Message> entry : lostMessages.entrySet()) {
                        omemoService.onOmemoMessageStanzaReceived(entry.getValue(), new OmemoManager.LoggedInOmemoManager(omemoManager));
                    }
                    lostMessages = null;
                }
            }

            @Override
            protected void onPostExecute(Void dummy) {

            }
        }.execute();
    }

    private boolean appIsInForground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }

    private void insertMessageToDB(Stanza stanza, OmemoMessage.Received decryptedMessage) {
        MessagesDbHelper dbHelper = new MessagesDbHelper(reactApplicationContext);

        String fullContactString = stanza.getFrom().toString();
        String contactAsExtension = fullContactString.substring(0, fullContactString.indexOf("@"));

        JsonObject messageJsonObject = new JsonParser().parse(decryptedMessage.getBody()).getAsJsonObject();
        JsonObject userJsonObject = messageJsonObject.get("user").getAsJsonObject();

        String messageText = messageJsonObject.get("text").getAsString();
        String createdAt = messageJsonObject.get("createdAt").getAsString();
        String messageId = messageJsonObject.get("_id").getAsString();
        String messageUrl = null;
        String messageKey = null;
        Integer recipientId = Integer.valueOf(userJsonObject.get("_id").getAsString());

        try {
            messageUrl = messageJsonObject.get("url").getAsString();
            messageKey = messageJsonObject.get("key").getAsString();
        } catch (Exception e) {
        }

        int chatId = dbHelper.getChatIdForContactOfMessage(contactAsExtension, messageText, createdAt);

        dbHelper.insertMessage(messageId, messageText, createdAt, contactAsExtension, recipientId, messageUrl, chatId, messageKey);

        dbHelper.closeTransaction();

        displayNotification(messageText, contactAsExtension, messageUrl != null);
    }

    private boolean userExists(String user) {
        UserSearchManager search = new UserSearchManager(connection);
        Form searchForm = null;
        ReportedData data = null;
        try {
            searchForm = search
                    .getSearchForm(connection.getXMPPServiceDomain());

            org.jivesoftware.smackx.xdata.Form answerForm = searchForm.createAnswerForm();
            answerForm.setAnswer("email", user);
            FormField formField = new FormField();
            formField.addValue(user);
            formField.setLabel("email");
            answerForm.addField(formField);

            data = search
                    .getSearchResults(answerForm, connection.getXMPPServiceDomain());

        } catch (SmackException.NoResponseException e) {
            e.printStackTrace();
        } catch (XMPPException.XMPPErrorException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (data != null) {
            return true;
        }
        return false;
    }

    @Override
    public void message(String text, String to, String id, String thread) {
        if (!userExists(to)) {
            xmppServiceListener.onOmemoOutgoingMessageResult(false, id);
        } else {
            String chatIdentifier = (thread == null ? to : thread);

            ChatManager chatManager = ChatManager.getInstanceFor(connection);
            Chat chat = chatManager.getThreadChat(chatIdentifier);
            EntityBareJid recipientJid = null;
            if (chat == null) {
                try {
                    recipientJid = JidCreate.entityBareFrom(to);
                    if (thread == null) {
                        chat = chatManager.createChat(JidCreate.entityBareFrom(to), this);
                    } else {
                        chat = chatManager.createChat(JidCreate.entityBareFrom(to), thread, this);
                    }
                } catch (XmppStringprepException e) {
                    e.printStackTrace();
                }
            }

            OmemoMessage.Sent encrypted = null;
            try {
                omemoManager.requestDeviceListUpdateFor(recipientJid);

                Set<OmemoDevice> deviceList = omemoManager.getDevicesOf(recipientJid);

                if (deviceList.size() == 0)
                {
                    sendRegularMessage(text, recipientJid, chat, id);
                }
                else
                {
                    for (OmemoDevice device : deviceList) {
                        OmemoFingerprint fingerprint = omemoManager.getFingerprint(device);
                        omemoManager.trustOmemoIdentity(device, fingerprint);
                    }

                    encrypted = omemoManager.encrypt(recipientJid, text);
                }
            }
            // In case of undecided devices
            catch (UndecidedOmemoIdentityException e) {
                try {
                    omemoManager.purgeDeviceList();
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, "Exception: ", e);
                }
                for (OmemoDevice device : e.getUndecidedDevices()) {
                    try {
                        omemoManager.trustOmemoIdentity(device, omemoManager.getFingerprint(device));
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }

                try {
                    encrypted = omemoManager.encrypt(recipientJid, text);
                } catch (Exception e1) {
                    xmppServiceListener.onOmemoOutgoingMessageResult(false, id);
                    e1.printStackTrace();
                }
            } catch (Exception e) {
                sendRegularMessage(text, recipientJid, chat, id);
            }

            //send
            if (encrypted != null) {
                try {
                    Message message = encrypted.asMessage(recipientJid);
                    message.setStanzaId(id);
                    chat.sendMessage(message);
                    xmppServiceListener.onOmemoOutgoingMessageResult(true, id);
                } catch (SmackException | InterruptedException e) {
                    xmppServiceListener.onOmemoOutgoingMessageResult(false, id);
                    logger.log(Level.WARNING, "Could not send message", e);
                }
            }
        }
    }

    private void sendRegularMessage(
            String text,
            EntityBareJid recipient,
            Chat chat,
            String id
    ) {
        String newMessageBody = getMessageBodyForIos(text);

        Message messageStanza = new Message(recipient, Message.Type.chat);
        messageStanza.setBody(newMessageBody);

        try {
            chat.sendMessage(messageStanza);
            xmppServiceListener.onOmemoOutgoingMessageResult(true, id);
        } catch (SmackException.NotConnectedException e) {
            xmppServiceListener.onOmemoOutgoingMessageResult(false, id);
        } catch (InterruptedException e) {
            xmppServiceListener.onOmemoOutgoingMessageResult(false, id);
        }
    }

    private String getMessageBodyForIos(String text) {
        JsonObject newMessageBody = new JsonObject();

        JsonObject messageJsonObject = new JsonParser().parse(text).getAsJsonObject();

        String messageText = messageJsonObject.get(JSON_TEXT).getAsString();
        String messageId = messageJsonObject.get(JSON_ID).getAsString();

        SimpleDateFormat newFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZZZ", Locale.US
        );

        Date dateFromMessage = new Date();

        newMessageBody.addProperty(JSON_TEXT,messageText);
        newMessageBody.addProperty(JSON_CREATED_AT,newFormat.format(dateFromMessage));
        newMessageBody.addProperty(JSON_ID,messageId);

        return newMessageBody.toString();
    }

    private String generateRandomString() {
        StringBuffer randStr = new StringBuffer();
        for (int i = 0; i < 16; i++) {
            int number = getRandomNumber();
            char ch = CHAR_LIST.charAt(number);
            randStr.append(ch);
        }
        return randStr.toString();
    }

    private int getRandomNumber() {
        int randomInt = 0;
        SecureRandom randomGenerator = new SecureRandom();
        randomInt = randomGenerator.nextInt(CHAR_LIST.length());
        if (randomInt - 1 == -1) {
            return randomInt;
        } else {
            return randomInt - 1;
        }
    }

    private void fileProcessor(int cipherMode, String key, File inputFile, File outputFile) {
        try {
            Key secretKey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(cipherMode, secretKey);

            FileInputStream inputStream = new FileInputStream(inputFile);
            byte[] inputBytes = new byte[(int) inputFile.length()];
            inputStream.read(inputBytes);

            byte[] outputBytes = cipher.doFinal(inputBytes);

            FileOutputStream outputStream = new FileOutputStream(outputFile);
            outputStream.write(outputBytes);

            inputStream.close();
            outputStream.close();

        } catch (NoSuchPaddingException | NoSuchAlgorithmException
                | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void decryptFile(String fileURI, String key) {
        File inputFile = new File(fileURI);
        File decryptedFile = new File(fileURI.replace(".assac", ""));
        fileProcessor(Cipher.DECRYPT_MODE, key, inputFile, decryptedFile);
    }

    @Override
    public void sendFile(String fileURI) {
        HttpFileUploadManager manager = HttpFileUploadManager.getInstanceFor(connection);
        manager.setTlsContext(UnsafeSSLContext.INSTANCE.getContext());
        try {
            String key = generateRandomString();
            File inputFile = new File(fileURI);
            File encryptedFile = new File(fileURI + ".assac");
            fileProcessor(Cipher.ENCRYPT_MODE, key, inputFile, encryptedFile);
            //URL fileURL = manager.uploadFile(new File(fileURI));
            URL fileURL = manager.uploadFile(encryptedFile);
            encryptedFile.delete();
            this.xmppServiceListener.onFileReceived(fileURL.toString(), fileURI, key);
        } catch (SmackException | InterruptedException | IOException | XMPPException e) {
            logger.log(Level.WARNING, "Could not upload and send file", e);
        }
    }

    @Override
    public void enablePushNotifications(String pushJid, String node, String secret) {
        PushNotificationsManager pushNotificationsManager = PushNotificationsManager.getInstanceFor(connection);
        HashMap<String, String> publishOptions = new HashMap<String, String>();
        publishOptions.put("secret", secret);
        publishOptions.put("endpoint", "https://assac.phone.gs:5281/push_appserver/v1/push");

        try {
            logger.log(Level.INFO, "Now we are going to try enabling push notifications");
            //pushNotificationsManager.enable(JidCreate.entityBareFrom(pushJid), node, publishOptions);
            pushNotificationsManager.enable(JidCreate.from("assac.phone.gs"), node, publishOptions);
        } catch (XmppStringprepException | SmackException.NotConnectedException | InterruptedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
            logger.log(Level.WARNING, "Could not enable push notifications", e);
        }
    }

    @Override
    public void setupOmemo() {

    }

    @Override
    public void presence(String to, String type) {
        try {
            connection.sendStanza(new Presence(Presence.Type.fromString(type), type, 1, Presence.Mode.fromString(type)));
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send presence", e);
        }
    }

    @Override
    public void removeRoster(String to) {
        Roster roster = Roster.getInstanceFor(connection);
        RosterEntry rosterEntry = null;
        try {
            rosterEntry = roster.getEntry(JidCreate.entityBareFrom(to));
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        if (rosterEntry != null) {
            try {
                roster.removeEntry(rosterEntry);
            } catch (SmackException.NotLoggedInException | InterruptedException | SmackException.NotConnectedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
                logger.log(Level.WARNING, "Could not remove roster entry: " + to);
            }
        }
    }

    @Override
    public void disconnect() {
        connection.disconnect();
        xmppServiceListener.onDisconnect(null);
    }

    @Override
    public void fetchRoster() {
        try {
            roster.reload();
        } catch (SmackException.NotLoggedInException | InterruptedException | SmackException.NotConnectedException e) {
            logger.log(Level.WARNING, "Could not fetch roster", e);
        }
    }

    @Override
    public void processStanza(Stanza packet) throws SmackException.NotConnectedException, InterruptedException, SmackException.NotLoggedInException {

    }

    public class StanzaPacket extends org.jivesoftware.smack.packet.Stanza {
        private String xmlString;

        public StanzaPacket(String xmlString) {
            super();
            this.xmlString = xmlString;
        }

        @Override
        public String toString() {
            return null;
        }

        //         @Override
//        public XmlStringBuilder toXML() {
//            XmlStringBuilder xml = new XmlStringBuilder();
//            xml.append(this.xmlString);
//            return xml;
//        }

        @Override
        public CharSequence toXML(XmlEnvironment xmlEnvironment) {
            return toXML().toString();
        }

        @Override
        public CharSequence toXML(String enclosingNamespace) {
            return null;
        }

//        @Override
//        public CharSequence toXML(String enclosingNamespace) {
//            return null;
//        }
    }

    @Override
    public void sendStanza(String stanza) {
        StanzaPacket packet = new StanzaPacket(stanza);
        try {
            connection.sendStanza(packet);
        } catch (SmackException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send stanza", e);
        }
    }

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        chat.addMessageListener(this);
    }

    //    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        if (packet instanceof IQ) {
            this.xmppServiceListener.onIQ((IQ) packet);
        } else if (packet instanceof Presence) {
            this.xmppServiceListener.onPresence((Presence) packet);
        } else {
            logger.log(Level.WARNING, "Got a Stanza, of unknown subclass");
        }
    }

    @Override
    public void connected(XMPPConnection connection) {
        this.xmppServiceListener.onConnnect("a", password);
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        this.xmppServiceListener.onLogin(connection.getUser().toString(), password);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        //Meaning message was received before omemo initialized
        if (isEncryptedMessage(message.toXML().toString()))
        {
            if (!isOmemoInitialized) {
                lostMessages.put(chat, message);
            }
        }
        else
        {
            if (appIsInForground()) {
                xmppServiceListener.onRegularMessage(message);
            } else {
                insertRegularMessageToDB(message);
            }
        }
    }

    private void insertRegularMessageToDB(Message message) {
        MessagesDbHelper dbHelper = new MessagesDbHelper(reactApplicationContext);

        String fullContactString = message.getFrom().toString();
        String contactAsExtension = fullContactString.substring(0, fullContactString.indexOf("@"));

        JsonObject messageJsonObject = new JsonParser().parse(message.getBody()).getAsJsonObject();

        String messageText = messageJsonObject.get("text").getAsString();
        String createdAt = messageJsonObject.get("createdAt").getAsString();
        String messageId = messageJsonObject.get("_id").getAsString();
        String messageUrl = null;
        String messageKey = null;

        try {
            messageUrl = messageJsonObject.get("url").getAsString();
            messageKey = messageJsonObject.get("key").getAsString();
        } catch (Exception e) {
        }

        int chatId = dbHelper.getChatIdForContactOfMessage(contactAsExtension, messageText, createdAt);

        dbHelper.insertMessage(messageId, messageText, createdAt, contactAsExtension, getExtension(), messageUrl, chatId, messageKey);

        dbHelper.closeTransaction();

        displayNotification(messageText, contactAsExtension, messageUrl != null);
    }

    private Integer getExtension() {
        return Integer.valueOf(connection.getUser().getLocalpart().toString());
    }

    private boolean isEncryptedMessage(String messageAsXML) {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        Document inputDoc = null;
        try {
            inputDoc = documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(messageAsXML)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        NodeList nodeList = inputDoc.getElementsByTagName(ENCRYPTED_XML_TAG);
        return nodeList.getLength() != 0;
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        this.xmppServiceListener.onRosterReceived(roster);
    }

    @Override
    public void onRosterLoadingFailed(Exception exception) {

    }

    @Override
    public void connectionClosedOnError(Exception e) {
        this.xmppServiceListener.onDisconnect(e);
    }

    @Override
    public void connectionClosed() {
        logger.log(Level.INFO, "Connection was closed.");
    }

    //    @Override
    public void reconnectionSuccessful() {
        logger.log(Level.INFO, "Did reconnect");
    }

    //    @Override
    public void reconnectingIn(int seconds) {
        logger.log(Level.INFO, "Reconnecting in {0} seconds", seconds);
    }

    //    @Override
    public void reconnectionFailed(Exception e) {
        logger.log(Level.WARNING, "Could not reconnect", e);

    }

    private static final String NOTIFICATION_CHANNEL_ID = "rn-push-notification-channel-id";
    private static final String NOTIFICATION_CHANNEL_NAME = "ShieldiT Chat Channel";
    private static final String NOTIFICATION_GROUP_KEY = "com.assacnetworks.shieldit.chat";
    private static final String NOTIFICATION_TITLE = "You've got a new message";
    private static final String EXTRA_CHAT_FROM = "EXTRA_CHAT_FROM";
    private static final String EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID";
    private static final int SUMMARY_NOTIFICATION_ID = 111;

    public void displayNotification(String text, String from, boolean isFile) {
        createNotificationChannel();

        int requestID = (int) System.currentTimeMillis();
        PendingIntent contentIntent = createChatMessagePendingIntent(from, requestID);

        NotificationCompat.Builder publicBuilder =
                new NotificationCompat.Builder(reactApplicationContext, NOTIFICATION_CHANNEL_ID)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSmallIcon(R.mipmap.ic_stat_icon)
                        .setContentTitle(NOTIFICATION_TITLE)
                        .setGroup(NOTIFICATION_GROUP_KEY);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(reactApplicationContext, NOTIFICATION_CHANNEL_ID)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSmallIcon(R.mipmap.ic_stat_icon)
                        .setContentTitle(NOTIFICATION_TITLE)
                        .setContentText(isFile ? from + " sent you a file " : from + ": " + text)
                        .setGroup(NOTIFICATION_GROUP_KEY)
                        .setContentIntent(contentIntent)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setPublicVersion(publicBuilder.build());

        final Activity activity = reactApplicationContext.getCurrentActivity();
        if (activity != null) {

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                }
            });
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(reactApplicationContext);
        notificationManager.notify(requestID, builder.build());

        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M) {
            createChatSummaryNotification(notificationManager);
        }
    }

    public void clearAllNotifications() {
        NotificationManager notificationManager = reactApplicationContext.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 100, 200, 300});
            NotificationManager notificationManager = reactApplicationContext.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void createChatSummaryNotification(NotificationManagerCompat notificationManager) {
        PendingIntent contentIntent = createChatMessagePendingIntent("", SUMMARY_NOTIFICATION_ID);
        NotificationCompat.Builder publicBuilder =
                new NotificationCompat.Builder(reactApplicationContext, NOTIFICATION_CHANNEL_ID)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSmallIcon(R.mipmap.ic_stat_icon)
                        .setContentTitle(NOTIFICATION_TITLE)
                        .setGroup(NOTIFICATION_GROUP_KEY);
        Notification summaryNotification =
                new NotificationCompat.Builder(reactApplicationContext, NOTIFICATION_CHANNEL_ID)
                        .setSound(null)
                        .setVibrate(null)
                        .setSmallIcon(R.mipmap.ic_stat_icon)
                        .setStyle(new NotificationCompat.InboxStyle())
                        .setGroup(NOTIFICATION_GROUP_KEY)
                        .setGroupSummary(true)
                        .setContentIntent(contentIntent)
                        .setPublicVersion(publicBuilder.build())
                        .build();

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification);
    }

    private PendingIntent createChatMessagePendingIntent(String from, int requestID) {
        PackageManager pm = reactApplicationContext.getPackageManager();
        Intent notificationIntent = pm.getLaunchIntentForPackage(reactApplicationContext.getPackageName());
        notificationIntent.putExtra(EXTRA_CHAT_FROM, from);
        notificationIntent.putExtra(EXTRA_NOTIFICATION_ID, requestID);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getActivity(
                reactApplicationContext,
                requestID,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    public void handleIntent(Intent intent) {
        String from = intent.getStringExtra(EXTRA_CHAT_FROM);
        if (from != null) {
            xmppServiceListener.onNotificationOpened(from);
        }

        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (notificationId != -1) {
            NotificationManager notificationManager = reactApplicationContext.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.cancel(notificationId);
            }
        }
    }
}
