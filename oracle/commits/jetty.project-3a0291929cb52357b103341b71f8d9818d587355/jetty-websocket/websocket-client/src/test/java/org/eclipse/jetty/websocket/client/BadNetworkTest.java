//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.client;

import static org.hamcrest.Matchers.*;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketConnection;
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer;
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer.ServerConnection;
import org.eclipse.jetty.websocket.common.io.AbstractWebSocketConnection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for conditions due to bad networking.
 */
@Ignore("Not working yet")
public class BadNetworkTest
{
    @Rule
    public TestTracker tt = new TestTracker();

    private BlockheadServer server;
    private WebSocketClientFactory factory;

    @Before
    public void startFactory() throws Exception
    {
        factory = new WebSocketClientFactory();
        factory.getPolicy().setIdleTimeout(250);
        factory.start();
    }

    @Before
    public void startServer() throws Exception
    {
        server = new BlockheadServer();
        server.start();
    }

    @After
    public void stopFactory() throws Exception
    {
        factory.stop();
    }

    @After
    public void stopServer() throws Exception
    {
        server.stop();
    }

    @Test
    public void testAbruptClientClose() throws Exception
    {
        TrackingSocket wsocket = new TrackingSocket();
        WebSocketClient client = factory.newWebSocketClient(wsocket);

        URI wsUri = server.getWsUri();
        Future<ClientUpgradeResponse> future = client.connect(wsUri);

        ServerConnection ssocket = server.accept();
        ssocket.upgrade();

        // Validate that we are connected
        future.get(500,TimeUnit.MILLISECONDS);
        wsocket.waitForConnected(500,TimeUnit.MILLISECONDS);

        // Have client disconnect abruptly
        WebSocketConnection conn = wsocket.getConnection();
        Assert.assertThat("Connection",conn,instanceOf(AbstractWebSocketConnection.class));
        AbstractWebSocketConnection awsc = (AbstractWebSocketConnection)conn;
        awsc.disconnect(false);

        // Client Socket should see close
        wsocket.waitForClose(10,TimeUnit.SECONDS);

        // Client Socket should see a close event, with status NO_CLOSE
        // This event is automatically supplied by the underlying WebSocketClientConnection
        // in the situation of a bad network connection.
        wsocket.assertCloseCode(StatusCode.NO_CLOSE);
    }

    @Test
    public void testAbruptServerClose() throws Exception
    {
        TrackingSocket wsocket = new TrackingSocket();
        WebSocketClient client = factory.newWebSocketClient(wsocket);

        URI wsUri = server.getWsUri();
        Future<ClientUpgradeResponse> future = client.connect(wsUri);

        ServerConnection ssocket = server.accept();
        ssocket.upgrade();

        // Validate that we are connected
        future.get(500,TimeUnit.MILLISECONDS);
        wsocket.waitForConnected(500,TimeUnit.MILLISECONDS);

        // Have server disconnect abruptly
        ssocket.disconnect();

        // Wait for close
        wsocket.waitForClose(10,TimeUnit.SECONDS);

        // Client Socket should see a close event, with status NO_CLOSE
        // This event is automatically supplied by the underlying WebSocketClientConnection
        // in the situation of a bad network connection.
        wsocket.assertCloseCode(StatusCode.NO_CLOSE);
    }
}
