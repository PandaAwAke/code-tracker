package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.junit2.SegmentedInputStream;
import com.intellij.execution.junit2.segments.DeferedActionsQueue;
import com.intellij.execution.junit2.segments.DispatchListener;
import com.intellij.execution.junit2.segments.PacketExtractorBase;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.rt.execution.junit2.segments.PacketProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * @author dyoma
 */
public class JUnitProcessHandler extends OSProcessHandler {
  private final Extractor myOut;
  private final Extractor myErr;
  private final Charset myCharset;

  public JUnitProcessHandler(final Process process, final String commandLine, final Charset charset) {
    super(process, commandLine);
    myOut = new Extractor(getProcess().getInputStream());
    myErr = new Extractor(getProcess().getErrorStream());
    myCharset = charset;
  }

  protected Reader createProcessOutReader() {
    return myOut.getReader();
  }

  protected Reader createProcessErrReader() {
    return myErr.getReader();
  }

  public PacketExtractorBase getErr() {
    return myErr;
  }

  public PacketExtractorBase getOut() {
    return myOut;
  }

  public Charset getCharset() {
    return myCharset;
  }

  public static JUnitProcessHandler runJava(final JavaParameters javaParameters) throws ExecutionException {
    return runCommandLine(GeneralCommandLine.createFromJavaParameters(javaParameters));
  }

  public static JUnitProcessHandler runCommandLine(final GeneralCommandLine commandLine) throws ExecutionException {
    final JUnitProcessHandler processHandler = new JUnitProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString(),
                                                                 commandLine.getCharset());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  private class Extractor extends PacketExtractorBase {
    private final SegmentedInputStream myStream;

    public Extractor(final InputStream stream) {
      myStream = new SegmentedInputStream(stream);
    }

    public void setPacketProcessor(final PacketProcessor packetProcessor) {
      myStream.setEventsDispatcher(new PacketProcessor() {
        public void processPacket(final String packet) {
          perform(new Runnable() {
            public void run() {
              packetProcessor.processPacket(packet);
            }
          });
        }
      });
    }

    public void setFulfilledWorkGate(final DeferedActionsQueue fulfilledWorkGate) {
      super.setFulfilledWorkGate(new DeferedActionsQueue() {
        public void addLast(final Runnable runnable) {
          ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
              fulfilledWorkGate.addLast(runnable);
            }
          }, ModalityState.NON_MMODAL);
        }

        public void setDispactchListener(final DispatchListener listener) {
          fulfilledWorkGate.setDispactchListener(listener);
        }
      });
    }

    public Reader getReader() {
      return new Reader() {
        public void close() throws IOException {
          myStream.close();
        }

        public int read(final char[] cbuf, final int off, final int len) throws IOException {
          for (int i = 0; i < len; i++) {
            final int aChar = myStream.read();
            if (aChar == -1) return i == 0 ? -1 : i;
            cbuf[off + i] = (char)aChar;
          }
          return len;
        }
      };
      //return new InputStreamReader(myStream, myCharset);
    }
  }
}
