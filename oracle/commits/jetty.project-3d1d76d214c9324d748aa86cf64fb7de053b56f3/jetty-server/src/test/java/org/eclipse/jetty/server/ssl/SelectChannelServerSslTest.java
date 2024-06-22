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

package org.eclipse.jetty.server.ssl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpServerTestBase;
import org.eclipse.jetty.server.SelectChannelConnector;
import org.eclipse.jetty.server.HttpServerTestBase.CommittedErrorHandler;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * HttpServer Tester.
 */
public class SelectChannelServerSslTest extends HttpServerTestBase
{
    static SSLContext __sslContext;
    {
        _scheme="https";
    }

    @Override
    protected Socket newSocket(String host, int port) throws Exception
    {
        return __sslContext.getSocketFactory().createSocket(host,port);
    }

    @Before
    public void init() throws Exception
    {
        String keystorePath = System.getProperty("basedir",".") + "/src/test/resources/keystore";
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(keystorePath);
        sslContextFactory.setKeyStorePassword("storepwd");
        sslContextFactory.setKeyManagerPassword("keypwd");
        sslContextFactory.setTrustStore(keystorePath);
        sslContextFactory.setTrustStorePassword("storepwd");
        SelectChannelConnector connector = new SelectChannelConnector(_server, sslContextFactory);

        startServer(connector);

        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(new FileInputStream(connector.getSslContextFactory().getKeyStorePath()), "storepwd".toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keystore);
        __sslContext = SSLContext.getInstance("TLS");
        __sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        try
        {
            HttpsURLConnection.setDefaultHostnameVerifier(__hostnameverifier);
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, SslContextFactory.TRUST_ALL_CERTS, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRequest2FixedFragments() throws Exception
    {
        configureServer(new EchoHandler());

        byte[] bytes=REQUEST2.getBytes();
        int[] points=new int[]{74,325};

        // Sort the list
        Arrays.sort(points);

        Socket client=newSocket(HOST,_connector.getLocalPort());
        try
        {
            OutputStream os=client.getOutputStream();

            int last=0;

            // Write out the fragments
            for (int j=0; j<points.length; ++j)
            {
                int point=points[j];
                os.write(bytes,last,point-last);
                last=point;
                os.flush();
                Thread.sleep(PAUSE);

            }

            // Write the last fragment
            os.write(bytes,last,bytes.length-last);
            os.flush();
            Thread.sleep(PAUSE);


            // Read the response
            String response=readResponse(client);

            // Check the response
            assertEquals(RESPONSE2,response);
        }
        finally
        {
            client.close();
        }
    }

    @Override
    @Ignore
    public void testAvailable() throws Exception
    {
    }
    

}
