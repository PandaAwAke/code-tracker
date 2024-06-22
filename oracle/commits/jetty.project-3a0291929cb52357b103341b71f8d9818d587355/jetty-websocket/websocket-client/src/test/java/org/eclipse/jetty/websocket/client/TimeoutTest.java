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
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer;
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer.ServerConnection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Various tests for Timeout handling
 */
@Ignore("Idle timeouts not working yet")
public class TimeoutTest
{
    @Rule
    public TestTracker tt = new TestTracker();

    private BlockheadServer server;
    private WebSocketClientFactory factory;

    @Before
    public void startFactory() throws Exception
    {
        factory = new WebSocketClientFactory();
        factory.getPolicy().setIdleTimeout(250); // idle timeout (for all tests here)
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

    /**
     * In a situation where the upgrade/connection is successfull, and there is no activity for a while, the idle timeout triggers on the client side and
     * automatically initiates a close handshake.
     */
    @Test
    public void testIdleDetectedByClient() throws Exception
    {
        TrackingSocket wsocket = new TrackingSocket();

        WebSocketClient client = factory.newWebSocketClient(wsocket);

        URI wsUri = server.getWsUri();
        Future<ClientUpgradeResponse> future = client.connect(wsUri);

        ServerConnection ssocket = server.accept();
        ssocket.upgrade();

        // Validate that connect occurred
        future.get(500,TimeUnit.MILLISECONDS);
        wsocket.waitForConnected(500,TimeUnit.MILLISECONDS);

        // Wait for inactivity idle timeout.
        long start = System.currentTimeMillis();
        wsocket.waitForClose(10,TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long dur = (end - start);
        // Make sure idle timeout takes less than 5 total seconds
        Assert.assertThat("Idle Timeout",dur,lessThanOrEqualTo(5000L));

        // Client should see a close event, with status NO_CLOSE
        wsocket.assertCloseCode(StatusCode.NORMAL);
    }
}
