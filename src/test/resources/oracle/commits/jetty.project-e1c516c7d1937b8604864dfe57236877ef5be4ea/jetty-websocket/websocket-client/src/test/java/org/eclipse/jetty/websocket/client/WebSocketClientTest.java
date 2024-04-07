//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.test.AdvancedRunner;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer;
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer.ServerConnection;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AdvancedRunner.class)
public class WebSocketClientTest
{
    private BlockheadServer server;
    private WebSocketClientFactory factory;

    @Before
    public void startFactory() throws Exception
    {
        factory = new WebSocketClientFactory();
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
    public void testBasicEcho_FromClient() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        WebSocketClient client = factory.newWebSocketClient(cliSock);
        client.getPolicy().setIdleTimeout(10000);

        URI wsUri = server.getWsUri();
        UpgradeRequest request = client.getUpgradeRequest();
        request.setSubProtocols("echo");
        Future<ClientUpgradeResponse> future = client.connect(wsUri);

        final ServerConnection srvSock = server.accept();
        srvSock.upgrade();

        UpgradeResponse resp = future.get(500,TimeUnit.MILLISECONDS);
        Assert.assertThat("Response",resp,notNullValue());
        Assert.assertThat("Response.success",resp.isSuccess(),is(true));

        cliSock.assertWasOpened();
        cliSock.assertNotClosed();

        Assert.assertThat("Factory.sockets.size",factory.getConnectionManager().getClients().size(),is(1));

        cliSock.getConnection().write("Hello World!");
        srvSock.echoMessage(1,TimeUnit.MILLISECONDS,500);
        // wait for response from server
        cliSock.waitForMessage(500,TimeUnit.MILLISECONDS);

        cliSock.assertMessage("Hello World!");
    }

    @Test
    public void testBasicEcho_FromServer() throws Exception
    {
        TrackingSocket wsocket = new TrackingSocket();
        WebSocketClient client = factory.newWebSocketClient(wsocket);
        Future<ClientUpgradeResponse> future = client.connect(server.getWsUri());

        // Server
        final ServerConnection srvSock = server.accept();
        srvSock.upgrade();

        // Validate connect
        UpgradeResponse resp = future.get(500,TimeUnit.MILLISECONDS);
        Assert.assertThat("Response",resp,notNullValue());
        Assert.assertThat("Response.success",resp.isSuccess(),is(true));

        // Have server send initial message
        srvSock.write(WebSocketFrame.text("Hello World"));

        // Verify connect
        future.get(500,TimeUnit.MILLISECONDS);
        wsocket.assertWasOpened();
        wsocket.awaitMessage(1,TimeUnit.SECONDS,2);

        wsocket.assertMessage("Hello World");
    }

    @Test
    public void testLocalRemoteAddress() throws Exception
    {
        WebSocketClientFactory fact = new WebSocketClientFactory();
        fact.start();
        try
        {
            TrackingSocket wsocket = new TrackingSocket();
            WebSocketClient client = fact.newWebSocketClient(wsocket);

            URI wsUri = server.getWsUri();
            Future<ClientUpgradeResponse> future = client.connect(wsUri);

            ServerConnection ssocket = server.accept();
            ssocket.upgrade();

            future.get(500,TimeUnit.MILLISECONDS);

            Assert.assertTrue(wsocket.openLatch.await(1,TimeUnit.SECONDS));

            InetSocketAddress local = wsocket.getConnection().getLocalAddress();
            InetSocketAddress remote = wsocket.getConnection().getRemoteAddress();

            Assert.assertThat("Local Socket Address",local,notNullValue());
            Assert.assertThat("Remote Socket Address",remote,notNullValue());

            // Hard to validate (in a portable unit test) the local address that was used/bound in the low level Jetty Endpoint
            Assert.assertThat("Local Socket Address / Host",local.getAddress().getHostAddress(),notNullValue());
            Assert.assertThat("Local Socket Address / Port",local.getPort(),greaterThan(0));

            Assert.assertThat("Remote Socket Address / Host",remote.getAddress().getHostAddress(),is(wsUri.getHost()));
            Assert.assertThat("Remote Socket Address / Port",remote.getPort(),greaterThan(0));
        }
        finally
        {
            fact.stop();
        }
    }

    @Test
    public void testMessageBiggerThanBufferSize() throws Exception
    {
        WebSocketClientFactory factSmall = new WebSocketClientFactory();
        factSmall.start();
        try
        {
            int bufferSize = 512;
            factSmall.getPolicy().setBufferSize(512);

            TrackingSocket wsocket = new TrackingSocket();
            WebSocketClient client = factSmall.newWebSocketClient(wsocket);

            URI wsUri = server.getWsUri();
            Future<ClientUpgradeResponse> future = client.connect(wsUri);

            ServerConnection ssocket = server.accept();
            ssocket.upgrade();

            future.get(500,TimeUnit.MILLISECONDS);

            Assert.assertTrue(wsocket.openLatch.await(1,TimeUnit.SECONDS));

            int length = bufferSize + (bufferSize / 2); // 1.5 times buffer size
            ssocket.write(0x80 | 0x01); // FIN + TEXT
            ssocket.write(0x7E); // No MASK and 2 bytes length
            ssocket.write(length >> 8); // first length byte
            ssocket.write(length & 0xFF); // second length byte
            for (int i = 0; i < length; ++i)
            {
                ssocket.write('x');
            }
            ssocket.flush();

            Assert.assertTrue(wsocket.dataLatch.await(1000,TimeUnit.SECONDS));
        }
        finally
        {
            factSmall.stop();
        }
    }
}
