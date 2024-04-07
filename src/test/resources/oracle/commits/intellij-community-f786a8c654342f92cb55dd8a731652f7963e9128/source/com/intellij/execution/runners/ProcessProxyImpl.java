package com.intellij.execution.runners;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */

class ProcessProxyImpl implements ProcessProxy {
  public static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");
  private int myPortNumber;

  private static final int SOCKET_NUMBER_START = 7532;
  private static final int SOCKET_NUMBER = 100;
  private static final boolean[] ourUsedSockets = new boolean[SOCKET_NUMBER];

  private PrintWriter myWriter;
  private Socket mySocket;
  @NonNls private static final String DONT_USE_LAUNCHER_PROPERTY = "idea.no.launcher";
  @NonNls public static final String PROPERTY_BINPATH = "idea.launcher.bin.path";
  @NonNls public static final String PROPERTY_PORT_NUMBER = "idea.launcher.port";
  @NonNls public static final String LAUNCH_MAIN_CLASS = "com.intellij.rt.execution.application.AppMain";
  @NonNls
  protected static final String LOCALHOST = "localhost";

  public int getPortNumber() {
    return myPortNumber;
  }

  public static class NoMoreSocketsException extends Exception {
  }

  public ProcessProxyImpl () throws NoMoreSocketsException {
    ServerSocket s;
    synchronized (ourUsedSockets) {
      for (int j = 0; j < SOCKET_NUMBER; j++) {
        if (ourUsedSockets[j]) continue;
        try {
          s = new ServerSocket(j + SOCKET_NUMBER_START);
          s.close();
          myPortNumber = j + SOCKET_NUMBER_START;
          ourUsedSockets[j] = true;

          return;
        } catch (IOException e) {
          continue;
        }
      }
    }
    throw new NoMoreSocketsException();
  }

  public void finalize () throws Throwable {
    if (myWriter != null) {
      myWriter.close();
    }
    ourUsedSockets[myPortNumber - SOCKET_NUMBER_START] = false;
    super.finalize();
  }

  public void attach(final ProcessHandler processHandler) {
    processHandler.putUserData(KEY, this);
  }

  private synchronized void writeLine (@NonNls final String s) {
    if (myWriter == null) {
      try {
        if (mySocket == null)
          mySocket = new Socket(InetAddress.getByName(LOCALHOST), myPortNumber);
        myWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mySocket.getOutputStream())));
      } catch (IOException e) {
        return;
      }
    }
    myWriter.println(s);
    myWriter.flush();
  }

  public void sendBreak () {
    writeLine("BREAK");
  }

  public void sendStop () {
    writeLine("STOP");
  }

  public static boolean useLauncher() {
    if (Boolean.valueOf(System.getProperty(DONT_USE_LAUNCHER_PROPERTY))) {
      return false;
    }

    if (!SystemInfo.isWindows && !SystemInfo.isLinux) {
      return false;
    }
    return new File(getLaunchertLibName()).exists();
  }

  public static String getLaunchertLibName() {
    @NonNls final String libName = SystemInfo.isWindows ? "breakgen.dll" : "libbreakgen.so";
    return PathManager.getBinPath() + File.separator + libName;
  }
}
