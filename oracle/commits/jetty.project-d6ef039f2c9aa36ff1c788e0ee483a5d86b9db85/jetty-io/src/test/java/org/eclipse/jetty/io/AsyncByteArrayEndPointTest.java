package org.eclipse.jetty.io;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.FutureCallback;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AsyncByteArrayEndPointTest
{
    private ScheduledExecutorService _scheduler;

    @Before
    public void before()
    {
        _scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void after()
    {
        _scheduler.shutdownNow();
    }

    @Test
    public void testReadable() throws Exception
    {
        AsyncByteArrayEndPoint endp = new AsyncByteArrayEndPoint(_scheduler, 5000);
        endp.setInput("test input");

        ByteBuffer buffer = BufferUtil.allocate(1024);
        FutureCallback<String> fcb = new FutureCallback<>();

        endp.fillInterested("CTX", fcb);
        assertTrue(fcb.isDone());
        assertEquals("CTX", fcb.get());
        assertEquals(10, endp.fill(buffer));
        assertEquals("test input", BufferUtil.toString(buffer));

        fcb = new FutureCallback<>();
        endp.fillInterested("CTX", fcb);
        assertFalse(fcb.isDone());
        assertEquals(0, endp.fill(buffer));

        endp.setInput(" more");
        assertTrue(fcb.isDone());
        assertEquals("CTX", fcb.get());
        assertEquals(5, endp.fill(buffer));
        assertEquals("test input more", BufferUtil.toString(buffer));

        fcb = new FutureCallback<>();
        endp.fillInterested("CTX", fcb);
        assertFalse(fcb.isDone());
        assertEquals(0, endp.fill(buffer));

        endp.setInput((ByteBuffer)null);
        assertTrue(fcb.isDone());
        assertEquals("CTX", fcb.get());
        assertEquals(-1, endp.fill(buffer));

        fcb = new FutureCallback<>();
        endp.fillInterested("CTX", fcb);
        assertTrue(fcb.isDone());
        assertEquals("CTX", fcb.get());
        assertEquals(-1, endp.fill(buffer));

        endp.close();

        fcb = new FutureCallback<>();
        endp.fillInterested("CTX", fcb);
        assertTrue(fcb.isDone());
        try
        {
            fcb.get();
            fail();
        }
        catch (ExecutionException e)
        {
            assertThat(e.toString(), containsString("Closed"));
        }
    }

    @Test
    public void testWrite() throws Exception
    {
        AsyncByteArrayEndPoint endp = new AsyncByteArrayEndPoint(_scheduler, 5000, (byte[])null, 15);
        endp.setGrowOutput(false);
        endp.setOutput(BufferUtil.allocate(10));

        ByteBuffer data = BufferUtil.toBuffer("Data.");
        ByteBuffer more = BufferUtil.toBuffer(" Some more.");

        FutureCallback<String> fcb = new FutureCallback<>();
        endp.write("CTX", fcb, data);
        assertTrue(fcb.isDone());
        assertEquals("CTX", fcb.get());
        assertEquals("Data.", endp.getOutputString());

        fcb = new FutureCallback<>();
        endp.write("CTX", fcb, more);
        assertFalse(fcb.isDone());

        assertEquals("Data. Some", endp.getOutputString());
        assertEquals("Data. Some", endp.takeOutputString());

        assertTrue(fcb.isDone());
        assertEquals("CTX", fcb.get());
        assertEquals(" more.", endp.getOutputString());
    }

    @Test
    public void testIdle() throws Exception
    {
        AsyncByteArrayEndPoint endp = new AsyncByteArrayEndPoint(_scheduler, 500);
        endp.setInput("test");
        endp.setGrowOutput(false);
        endp.setOutput(BufferUtil.allocate(5));

        // no idle check
        assertTrue(endp.isOpen());
        Thread.sleep(1000);
        assertTrue(endp.isOpen());

        // normal read
        ByteBuffer buffer = BufferUtil.allocate(1024);
        FutureCallback<Void> fcb = new FutureCallback<>();

        endp.fillInterested(null, fcb);
        assertTrue(fcb.isDone());
        assertEquals(null, fcb.get());
        assertEquals(4, endp.fill(buffer));
        assertEquals("test", BufferUtil.toString(buffer));

        // read timeout
        fcb = new FutureCallback<>();
        endp.fillInterested(null, fcb);
        long start = System.currentTimeMillis();
        try
        {
            fcb.get();
            fail();
        }
        catch (ExecutionException t)
        {
            assertThat(t.getCause(), Matchers.instanceOf(TimeoutException.class));
        }
        assertThat(System.currentTimeMillis() - start, Matchers.greaterThan(100L));
        assertTrue(endp.isOpen());

        // write timeout
        fcb = new FutureCallback<>();
        start = System.currentTimeMillis();

        endp.write(null, fcb, BufferUtil.toBuffer("This is too long"));
        try
        {
            fcb.get();
            fail();
        }
        catch (ExecutionException t)
        {
            assertThat(t.getCause(), Matchers.instanceOf(TimeoutException.class));
        }
        assertThat(System.currentTimeMillis() - start, Matchers.greaterThan(100L));
        assertTrue(endp.isOpen());

        // Still no idle close
        Thread.sleep(1000);
        assertTrue(endp.isOpen());

        // shutdown out
        endp.shutdownOutput();

        // idle close
        Thread.sleep(1000);
        assertFalse(endp.isOpen());
    }
}
