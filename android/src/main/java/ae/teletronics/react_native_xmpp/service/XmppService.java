package ae.teletronics.react_native_xmpp.service;

import com.facebook.react.bridge.ReactMethod;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public interface XmppService {
    @ReactMethod
    void connect(String jid, String password, String authMethod, String hostname, Integer port);

    @ReactMethod
    void message(String text, String to);

    @ReactMethod
    void presence(String to, String type);

    @ReactMethod
    void removeRoster(String to);

    @ReactMethod
    void disconnect();

    @ReactMethod
    void fetchRoster();

    @ReactMethod
    void sendStanza(String stanza);

    @ReactMethod
    void joinRoom(String roomId, String nickName);

    void sendRoomMessage(String message);
}