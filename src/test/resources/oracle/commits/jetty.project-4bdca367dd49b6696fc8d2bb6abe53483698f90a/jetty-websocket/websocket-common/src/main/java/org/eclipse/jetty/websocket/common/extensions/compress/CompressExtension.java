//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.common.extensions.compress;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.ConcurrentArrayQueue;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.BadPayloadException;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.extensions.AbstractExtension;
import org.eclipse.jetty.websocket.common.frames.DataFrame;

public abstract class CompressExtension extends AbstractExtension
{
    protected static final byte[] TAIL_BYTES = new byte[]{0x00, 0x00, (byte)0xFF, (byte)0xFF};
    private static final Logger LOG = Log.getLogger(CompressExtension.class);

    private final Queue<FrameEntry> entries = new ConcurrentArrayQueue<>();
    private final IteratingCallback flusher = new Flusher();
    private final Deflater compressor;
    private final Inflater decompressor;

    protected CompressExtension()
    {
        compressor = new Deflater(Deflater.BEST_COMPRESSION, true);
        decompressor = new Inflater(true);
    }

    public Deflater getDeflater()
    {
        return compressor;
    }

    public Inflater getInflater()
    {
        return decompressor;
    }

    /**
     * Indicates use of RSV1 flag for indicating deflation is in use.
     */
    @Override
    public boolean isRsv1User()
    {
        return true;
    }

    protected void forwardIncoming(Frame frame, ByteAccumulator accumulator)
    {
        DataFrame newFrame = new DataFrame(frame);
        // Unset RSV1 since it's not compressed anymore.
        newFrame.setRsv1(false);

        ByteBuffer buffer = getBufferPool().acquire(accumulator.getLength(), false);
        try
        {
            BufferUtil.flipToFill(buffer);
            accumulator.transferTo(buffer);
            newFrame.setPayload(buffer);
            nextIncomingFrame(newFrame);
        }
        finally
        {
            getBufferPool().release(buffer);
        }
    }

    protected ByteAccumulator decompress(byte[] input)
    {
        // Since we don't track text vs binary vs continuation state, just grab whatever is the greater value.
        int maxSize = Math.max(getPolicy().getMaxTextMessageSize(), getPolicy().getMaxBinaryMessageBufferSize());
        ByteAccumulator accumulator = new ByteAccumulator(maxSize);

        decompressor.setInput(input, 0, input.length);
        LOG.debug("Decompressing {} bytes", input.length);

        try
        {
            // It is allowed to send DEFLATE blocks with BFINAL=1.
            // For such blocks, getRemaining() will be > 0 but finished()
            // will be true, so we need to check for both.
            // When BFINAL=0, finished() will always be false and we only
            // check the remaining bytes.
            while (decompressor.getRemaining() > 0 && !decompressor.finished())
            {
                byte[] output = new byte[Math.min(input.length * 2, 32 * 1024)];
                int decompressed = decompressor.inflate(output);
                if (decompressed == 0)
                {
                    if (decompressor.needsInput())
                    {
                        throw new BadPayloadException("Unable to inflate frame, not enough input on frame");
                    }
                    if (decompressor.needsDictionary())
                    {
                        throw new BadPayloadException("Unable to inflate frame, frame erroneously says it needs a dictionary");
                    }
                }
                else
                {
                    accumulator.addChunk(output, 0, decompressed);
                }
            }
            LOG.debug("Decompressed {}->{} bytes", input.length, accumulator.getLength());
            return accumulator;
        }
        catch (DataFormatException x)
        {
            throw new BadPayloadException(x);
        }
    }

    @Override
    public void outgoingFrame(Frame frame, WriteCallback callback, FlushMode flushMode)
    {
        // We use a queue and an IteratingCallback to handle concurrency.
        // We must compress and write atomically, otherwise the compression
        // context on the other end gets confused.

        if (flusher.isFailed())
        {
            notifyCallbackFailure(callback, new ZipException());
            return;
        }

        FrameEntry entry = new FrameEntry(frame, callback, flushMode);
        LOG.debug("Queuing {}", entry);
        entries.offer(entry);
        flusher.iterate();
    }

    protected void notifyCallbackSuccess(WriteCallback callback)
    {
        try
        {
            if (callback != null)
                callback.writeSuccess();
        }
        catch (Throwable x)
        {
            LOG.debug("Exception while notifying success of callback " + callback, x);
        }
    }

    protected void notifyCallbackFailure(WriteCallback callback, Throwable failure)
    {
        try
        {
            if (callback != null)
                callback.writeFailed(failure);
        }
        catch (Throwable x)
        {
            LOG.debug("Exception while notifying failure of callback " + callback, x);
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    private static class FrameEntry
    {
        private final Frame frame;
        private final WriteCallback callback;
        private final FlushMode flushMode;

        private FrameEntry(Frame frame, WriteCallback callback, FlushMode flushMode)
        {
            this.frame = frame;
            this.callback = callback;
            this.flushMode = flushMode;
        }

        @Override
        public String toString()
        {
            return frame.toString();
        }
    }

    private class Flusher extends IteratingCallback implements WriteCallback
    {
        private FrameEntry current;
        private ByteBuffer payload;
        private boolean finished = true;

        @Override
        protected Action process() throws Exception
        {
            if (finished)
            {
                current = entries.poll();
                LOG.debug("Processing {}", current);
                if (current == null)
                    return Action.IDLE;
                deflate(current);
            }
            else
            {
                compress(current, false);
            }
            return Action.SCHEDULED;
        }

        private void deflate(FrameEntry entry)
        {
            Frame frame = entry.frame;
            FlushMode flushMode = entry.flushMode;
            if (OpCode.isControlFrame(frame.getOpCode()) || !frame.hasPayload())
            {
                nextOutgoingFrame(frame, this, flushMode);
                return;
            }

            compress(entry, true);
        }

        private void compress(FrameEntry entry, boolean first)
        {
            // Get a chunk of the payload to avoid to blow
            // the heap if the payload is a huge mapped file.
            Frame frame = entry.frame;
            ByteBuffer data = frame.getPayload();
            int remaining = data.remaining();
            int inputLength = Math.min(remaining, 32 * 1024);
            LOG.debug("Compressing {}: {} bytes in {} bytes chunk", entry, remaining, inputLength);

            // Avoid to copy the bytes if the ByteBuffer
            // is backed by an array.
            int inputOffset;
            byte[] input;
            if (data.hasArray())
            {
                input = data.array();
                int position = data.position();
                inputOffset = position + data.arrayOffset();
                data.position(position + inputLength);
            }
            else
            {
                input = new byte[inputLength];
                inputOffset = 0;
                data.get(input, 0, inputLength);
            }
            finished = inputLength == remaining;

            compressor.setInput(input, inputOffset, inputLength);

            // Use an additional space in case the content is not compressible.
            byte[] output = new byte[inputLength + 64];
            int outputOffset = 0;
            int outputLength = 0;
            while (true)
            {
                int space = output.length - outputOffset;
                int compressed = compressor.deflate(output, outputOffset, space, Deflater.SYNC_FLUSH);
                outputLength += compressed;
                if (compressed < space)
                {
                    // Everything was compressed.
                    break;
                }
                else
                {
                    // The compressed output is bigger than the uncompressed input.
                    byte[] newOutput = new byte[output.length * 2];
                    System.arraycopy(output, 0, newOutput, 0, output.length);
                    outputOffset += output.length;
                    output = newOutput;
                }
            }

            // Skip the last tail bytes bytes generated by SYNC_FLUSH.
            payload = ByteBuffer.wrap(output, 0, outputLength - TAIL_BYTES.length);
            LOG.debug("Compressed {}: {}->{} chunk bytes", entry, inputLength, outputLength);

            boolean continuation = frame.getType().isContinuation() || !first;
            DataFrame chunk = new DataFrame(frame, continuation);
            chunk.setRsv1(true);
            chunk.setPayload(payload);
            boolean fin = frame.isFin() && finished;
            chunk.setFin(fin);

            nextOutgoingFrame(chunk, this, entry.flushMode);
        }

        @Override
        protected void completed()
        {
            // This IteratingCallback never completes.
        }

        @Override
        public void writeSuccess()
        {
            if (finished)
                notifyCallbackSuccess(current.callback);
            succeeded();
        }

        @Override
        public void writeFailed(Throwable x)
        {
            notifyCallbackFailure(current.callback, x);
            // If something went wrong, very likely the compression context
            // will be invalid, so we need to fail this IteratingCallback.
            failed(x);
            // Now no more frames can be queued, fail those in the queue.
            FrameEntry entry;
            while ((entry = entries.poll()) != null)
                notifyCallbackFailure(entry.callback, x);
        }
    }
}
