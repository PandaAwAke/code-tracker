/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Oct 31, 2002
 * Time: 6:33:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.reporter.ConnectionException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * XML sample:
 * <idea>
 * <build>456</build>
 * <version>4.5.2</version>
 * <title>New Intellij IDEA Version</title>
 * <message>
 * New version of IntelliJ IDEA is available.
 * Please visit http://www.intellij.com/ for more info.
 * </message>
 * </idea>
 */
public final class UpdateChecker implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker");
  @NonNls private static String UPDATE_URL = null;

  private static long checkInterval = 0;
  private static boolean myVeryFirstOpening = true;
  @NonNls private static final String BUILD_NUMBER_STUB = "__BUILD_NUMBER__";
  @NonNls private static final String ELEMENT_BUILD = "build";
  @NonNls private static final String ELEMENT_VERSION = "version";

  private static String getUpdateUrl() {
    if (UPDATE_URL == null) {
      ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
      UPDATE_URL = appInfo.getUpdateUrls().getCheckingUrl();
    }
    return UPDATE_URL;
  }

  public String getComponentName() {
    return "UpdateChecker";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static boolean isMyVeryFirstOpening() {
    return myVeryFirstOpening;
  }

  public static void setMyVeryFirstOpening(final boolean myVeryFirstProjectOpening) {
    UpdateChecker.myVeryFirstOpening = myVeryFirstProjectOpening;
  }

  public static boolean checkNeeded() {

    final UpdateSettingsConfigurable settings = UpdateSettingsConfigurable.getInstance();
    if (settings == null || getUpdateUrl() == null) return false;

    final String checkPeriod = settings.CHECK_PERIOD;
    if (checkPeriod.equals(UpdateSettingsConfigurable.ON_START_UP)) {
      checkInterval = 0;
    }
    if (checkPeriod.equals(UpdateSettingsConfigurable.DAILY)) {
      checkInterval = DateFormatUtil.DAY;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.WEEKLY)) {
      checkInterval = DateFormatUtil.WEEK;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.MONTHLY)) {
      checkInterval = DateFormatUtil.MONTH;
    }

    final long timeDelta = System.currentTimeMillis() - settings.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < checkInterval) return false;

    return settings.CHECK_NEEDED;
  }

  @Nullable
  public static NewVersion checkForUpdates() throws ConnectionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: checkForUpdates()");
    }

    final Document document;
    try {
      document = loadVersionInfo();
      if (document == null) return null;
    }
    catch (Throwable t) {
      LOG.debug(t);
      throw new ConnectionException(t);
    }

    final String availBuild = document.getRootElement().getChild(ELEMENT_BUILD).getTextTrim();
    final String availVersion = document.getRootElement().getChild(ELEMENT_VERSION).getTextTrim();
    String ourBuild = ApplicationInfo.getInstance().getBuildNumber().trim();
    if (BUILD_NUMBER_STUB.equals(ourBuild)) ourBuild = Integer.toString(Integer.MAX_VALUE);

    if (LOG.isDebugEnabled()) {
      LOG.debug("build available:'" + availBuild + "' ourBuild='" + ourBuild + "' ");
    }

    try {
      final int iAvailBuild = Integer.parseInt(availBuild);
      final int iOurBuild = Integer.parseInt(ourBuild);
      if (iAvailBuild > iOurBuild) {
        return new NewVersion(iAvailBuild, availVersion);
      }
      return null;
    }
    catch (Throwable t) {
      LOG.debug(t);
      return null;
    }
    finally {
      UpdateSettingsConfigurable.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
    }
  }

  private static Document loadVersionInfo() throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: loadVersionInfo(UPDATE_URL='" + getUpdateUrl() + "' )");
    }
    final Document[] document = new Document[] {null};
    final Exception[] exception = new Exception[] {null};
    Future<?> downloadThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          HttpConfigurable.getInstance().prepareURL(getUpdateUrl());
          final InputStream inputStream = new URL(getUpdateUrl()).openStream();
          try {
            document[0] = JDOMUtil.loadDocument(inputStream);
          }
          finally {
            inputStream.close();
          }
        }
        catch (IOException e) {
          exception[0] = e;
        }
        catch (JDOMException e) {
          // Broken xml downloaded. Don't bother telling user.
        }
      }
    });

    try {
      downloadThreadFuture.get(5, TimeUnit.SECONDS);
    }
    catch (ExecutionException e) {
      throw e;
    }
    catch (TimeoutException e) {
    }

    if (!downloadThreadFuture.isDone()) {
      downloadThreadFuture.cancel(true);
      throw new ConnectionException(IdeBundle.message("updates.timeout.error"));
    }

    if (exception[0] != null) throw exception[0];
    return document[0];
  }

  public static void showNoUpdatesDialog(boolean enableLink) {
    NoUpdatesDialog dialog = new NoUpdatesDialog(true);
    dialog.setLinkEnabled(enableLink);
    dialog.setResizable(false);
    dialog.show();
  }

  public static void showUpdateInfoDialog(boolean enableLink, final NewVersion version) {
    UpdateInfoDialog dialog = new UpdateInfoDialog(true, version);
    dialog.setLinkEnabled(enableLink);
    dialog.setResizable(false);
    dialog.show();
  }

  public static class NewVersion {
    private int latestBuild;
    private String latestVersion;

    public int getLatestBuild() {
      return latestBuild;
    }

    public String getLatestVersion() {
      return latestVersion;
    }

    public NewVersion(int build, String version) {
      latestBuild = build;
      latestVersion = version;
    }
  }
}
