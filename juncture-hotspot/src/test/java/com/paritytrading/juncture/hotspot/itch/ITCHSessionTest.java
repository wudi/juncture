package com.paritytrading.juncture.hotspot.itch;

import static com.paritytrading.juncture.hotspot.itch.ITCHClientEvents.*;
import static com.paritytrading.juncture.hotspot.itch.ITCHServerEvents.*;
import static com.paritytrading.juncture.hotspot.itch.ITCHSessionEvents.*;
import static java.util.Arrays.*;
import static org.junit.Assert.*;

import com.paritytrading.foundation.ASCII;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class ITCHSessionTest {

    private static final int RX_BUFFER_CAPACITY = 1024;

    @Rule
    public Timeout timeout = new Timeout(1000);

    private ITCH.LoginAccepted       loginAccepted;
    private ITCH.LoginRejected       loginRejected;
    private ITCH.ErrorNotification   errorNotification;
    private ITCH.InstrumentDirectory instrumentDirectory;

    private ITCH.LoginRequest                 loginRequest;
    private ITCH.MarketSnapshotRequest        marketSnapshotRequest;
    private ITCH.TickerSubscribeRequest       tickerSubscribeRequest;
    private ITCH.TickerUnsubscribeRequest     tickerUnsubscribeRequest;
    private ITCH.MarketDataSubscribeRequest   marketDataSubscribeRequest;
    private ITCH.MarketDataUnsubscribeRequest marketDataUnsubscribeRequest;

    private FixedClock clock;

    private ITCHClientEvents clientEvents;
    private ITCHServerEvents serverEvents;

    private ITCHClient client;
    private ITCHServer server;

    @Before
    public void setUp() throws Exception {
        loginAccepted       = new ITCH.LoginAccepted();
        loginRejected       = new ITCH.LoginRejected();
        errorNotification   = new ITCH.ErrorNotification();
        instrumentDirectory = new ITCH.InstrumentDirectory();

        loginRequest                 = new ITCH.LoginRequest();
        marketSnapshotRequest        = new ITCH.MarketSnapshotRequest();
        tickerSubscribeRequest       = new ITCH.TickerSubscribeRequest();
        tickerUnsubscribeRequest     = new ITCH.TickerUnsubscribeRequest();
        marketDataSubscribeRequest   = new ITCH.MarketDataSubscribeRequest();
        marketDataUnsubscribeRequest = new ITCH.MarketDataUnsubscribeRequest();

        clock = new FixedClock();

        ServerSocketChannel acceptor = ServerSocketChannel.open();
        acceptor.bind(null);

        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.connect(acceptor.getLocalAddress());

        SocketChannel serverChannel = acceptor.accept();
        acceptor.close();

        clientEvents = new ITCHClientEvents();
        serverEvents = new ITCHServerEvents();

        client = new ITCHClient(clock, clientChannel, RX_BUFFER_CAPACITY, clientEvents);
        server = new ITCHServer(clock, serverChannel, RX_BUFFER_CAPACITY, serverEvents);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        server.close();
    }

    @Test
    public void loginAccepted() throws Exception {
        ASCII.putLongRight(loginAccepted.sequenceNumber, 1);

        server.accept(loginAccepted);

        while (clientEvents.collect().size() != 1)
            client.receive();

        assertEquals(asList(new LoginAccepted(1)), clientEvents.collect());
    }

    @Test
    public void loginRejected() throws Exception {
        ASCII.putLeft(loginRejected.reason, "foo");

        server.reject(loginRejected);

        while (clientEvents.collect().size() != 1)
            client.receive();

        assertEquals(asList(new LoginRejected("foo                 ")),
                    clientEvents.collect());
    }

    /* Sequenced Data */

    @Test
    public void endOfSession() throws Exception {
        server.endSession();

        while (clientEvents.collect().size() != 1)
            client.receive();

        assertEquals(asList(new EndOfSession()), clientEvents.collect());
    }

    @Test
    public void errorNotification() throws Exception {
        ASCII.putLeft(errorNotification.errorExplanation, "foo");

        server.notifyError(errorNotification);

        while (clientEvents.collect().size() != 1)
            client.receive();

        assertEquals(asList(new ErrorNotification(
                        "foo                                               " +
                        "                                                  ")),
                clientEvents.collect());
    }

    @Test
    public void instrumentDirectory() throws Exception {
        ASCII.putLongRight(instrumentDirectory.numberOfCurrencyPairs, 3);
        ASCII.putLeft(instrumentDirectory.currencyPair[0], "FOO/BAR");
        ASCII.putLeft(instrumentDirectory.currencyPair[1], "BAR/BAZ");
        ASCII.putLeft(instrumentDirectory.currencyPair[2], "BAZ/FOO");

        server.instrumentDirectory(instrumentDirectory);

        while (clientEvents.collect().size() != 1)
            client.receive();

        assertEquals(asList(new InstrumentDirectory(asList("FOO/BAR", "BAR/BAZ", "BAZ/FOO"))),
                clientEvents.collect());
    }

    @Test
    public void loginRequest() throws Exception {
        ASCII.putLeft(loginRequest.loginName, "foo");
        ASCII.putLeft(loginRequest.password, "bar");
        loginRequest.marketDataUnsubscribe = ITCH.TRUE;
        ASCII.putLongRight(loginRequest.reserved, 0);

        client.login(loginRequest);

        while (serverEvents.collect().size() != 1)
            server.receive();

        assertEquals(asList(new LoginRequest(
                        "foo                                     ",
                        "bar                                     ",
                        ITCH.TRUE, 0)),
                serverEvents.collect());
    }

    @Test
    public void logoutRequest() throws Exception {
        client.logout();

        while (serverEvents.collect().size() != 1)
            server.receive();

        assertEquals(asList(new LogoutRequest()), serverEvents.collect());
    }

    @Test
    public void marketSnapshotRequest() throws Exception {
        ASCII.putLeft(marketSnapshotRequest.currencyPair, "FOO/BAR");

        client.request(marketSnapshotRequest);

        while (serverEvents.collect().size() != 1)
            server.receive();

        assertEquals(asList(new MarketSnapshotRequest("FOO/BAR")),
                serverEvents.collect());
    }

    @Test
    public void tickerSubscription() throws Exception {
        ASCII.putLeft(tickerSubscribeRequest.currencyPair, "FOO/BAR");
        ASCII.putLeft(tickerUnsubscribeRequest.currencyPair, "FOO/BAR");

        client.request(tickerSubscribeRequest);
        client.request(tickerUnsubscribeRequest);

        while (serverEvents.collect().size() != 2)
            server.receive();

        assertEquals(asList(new TickerSubscribeRequest("FOO/BAR"),
                    new TickerUnsubscribeRequest("FOO/BAR")),
                serverEvents.collect());
    }

    @Test
    public void marketDataSubscription() throws Exception {
        ASCII.putLeft(marketDataSubscribeRequest.currencyPair, "FOO/BAR");
        ASCII.putLeft(marketDataUnsubscribeRequest.currencyPair, "FOO/BAR");

        client.request(marketDataSubscribeRequest);
        client.request(marketDataUnsubscribeRequest);

        while (serverEvents.collect().size() != 2)
            server.receive();

        assertEquals(asList(new MarketDataSubscribeRequest("FOO/BAR"),
                    new MarketDataUnsubscribeRequest("FOO/BAR")),
                serverEvents.collect());
    }

    @Test
    public void instrumentDirectoryRequest() throws Exception {
        client.requestInstrumentDirectory();

        while (serverEvents.collect().size() != 1)
            server.receive();

        assertEquals(asList(new InstrumentDirectoryRequest()), serverEvents.collect());
    }

    @Test
    public void serverKeepAlive() throws Exception {
        clock.setCurrentTimeMillis(1500);

        client.keepAlive();
        server.keepAlive();

        server.receive();

        clock.setCurrentTimeMillis(15500);

        server.keepAlive();

        clock.setCurrentTimeMillis(16750);

        server.keepAlive();

        assertEquals(asList(new HeartbeatTimeout()), serverEvents.collect());
    }

    @Test
    public void clientKeepAlive() throws Exception {
        clock.setCurrentTimeMillis(1500);

        client.keepAlive();
        server.keepAlive();

        client.receive();

        clock.setCurrentTimeMillis(15500);

        client.keepAlive();

        clock.setCurrentTimeMillis(16750);

        client.keepAlive();

        assertEquals(asList(new HeartbeatTimeout()), clientEvents.collect());
    }

}