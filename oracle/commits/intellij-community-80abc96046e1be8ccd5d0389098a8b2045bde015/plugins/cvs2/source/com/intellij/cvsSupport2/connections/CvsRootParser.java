package com.intellij.cvsSupport2.connections;

import com.intellij.openapi.util.text.StringUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/**
 * author: lesya
 */
public final class CvsRootParser {

  //:pserver;username=lesya;password=password111;hostname=hostname111;port=port111;proxy=proxy111;proxyport=proxyport111;tunnel=tumnnel111;proxyuser=proxyuser111;proxypassword=proxypassword111:c:/RepositoryPath

  private static final Pattern ourPattern = Pattern.compile("^((.*?{0,1}(:.*?){0,1})@){0,1}([a-zA-Z0-9\\._-]+)(:(\\d*){0,1}){0,1}(.+)$");

  private static final int GROUP_USER_NAME_AND_PWD = 2;
  private static final int GROUP_HOST = 4;
  private static final int GROUP_PORT = 5;
  private static final int GROUP_REPOSITORY = 7;

  public CvsMethod METHOD;
  public String USER_NAME;
  public String HOST;
  public String REPOSITORY;
  public String PROXY_HOST;
  public String PROXY_PORT;
  public String PORT;
  public String PASSWORD;


  @NotNull public static CvsRootParser valueOf(String str, boolean check) {

    CvsRootParser result = new CvsRootParser();

    if (!StringUtil.startsWithChar(str, ':')) {
      result.METHOD = CvsMethod.LOCAL_METHOD;
      result.REPOSITORY = str;
      return result;
    }

    String local2 = ":local:";

    if (str.startsWith(local2)){
      result.METHOD = CvsMethod.LOCAL_METHOD;
      result.REPOSITORY = str.substring(local2.length());
      return result;
    }

    String suffix = result.extractMethod(str, result, check);

    if (CvsMethod.LOCAL_METHOD.equals(result.METHOD)) {
      result.REPOSITORY = suffix;
      skipTrailingRepositorySlash(result);
    }
    else {
      if (result.HOST != null && result.HOST.length() > 0 && result.USER_NAME != null && result.USER_NAME.length() > 0) {
        result.REPOSITORY = suffix.trim();
      }
      else if (suffix.indexOf("@") >= 0 || suffix.indexOf(":") >= 0){
        Matcher matcher = ourPattern.matcher(suffix);

        if (matcher.matches()) {
          extractUserNameAndPassword(matcher, result);
          extractHostAndPort(matcher, result);
          extractRepository(matcher, result);
        }
        else {
          if (check) {
            throw new IllegalArgumentException("wrong remote repository: " + str);
          }
          else {
            result.REPOSITORY = suffix;
          }
        }
      } else {
        result.REPOSITORY = suffix;
      }
    }

    return result;
  }

  private static void extractRepository(Matcher matcher, CvsRootParser cvsRoot) {
    cvsRoot.REPOSITORY = matcher.group(GROUP_REPOSITORY);
    skipTrailingRepositorySlash(cvsRoot);

  }

  private static void skipTrailingRepositorySlash(CvsRootParser cvsRoot) {
    if (StringUtil.endsWithChar(cvsRoot.REPOSITORY, '/')) {
      cvsRoot.REPOSITORY =
      cvsRoot.REPOSITORY.substring(0, cvsRoot.REPOSITORY.length() - 1);
    }
  }

  private static void extractHostAndPort(Matcher matcher, CvsRootParser cvsRoot) {
    String host = matcher.group(GROUP_HOST);
    String port = matcher.group(GROUP_PORT);

    if (port != null) {
      cvsRoot.HOST = host + port;
    }
    else {
      if (StringUtil.endsWithChar(host, ':')) {
        host = host.substring(0, host.length() - 1);
      }
      cvsRoot.HOST = host;
    }

  }

  private static void extractUserNameAndPassword(Matcher matcher, CvsRootParser cvsRoot) {
    String userNameAndPwd = matcher.group(GROUP_USER_NAME_AND_PWD);
    if (userNameAndPwd != null && cvsRoot.USER_NAME.length() == 0) {
      cvsRoot.USER_NAME = userNameAndPwd;
    }
  }

  private String tryToCutMethod(CvsMethod method, String cvsRoot) {
    String methodentry = methodEntry(method.getName());
    if (cvsRoot.startsWith(methodentry)) {
      return cvsRoot.substring(methodentry.length());
    }
    if (!method.supportsProxyConnection()) {
      return null;
    }

    String proxyBegin = ":" + method.getName() + ";";

    if (!cvsRoot.startsWith(proxyBegin)) {
      return null;
    }

    String tail = cvsRoot.substring(proxyBegin.length() - 1);

    int endOfProxySettings = tail.indexOf(':');
    if (endOfProxySettings == -1){
      return null;
    }

    String proxySettings = tail.substring(0, endOfProxySettings);

    final String[] paramValueStrings = proxySettings.split(";");

    for (int i = 0; i < paramValueStrings.length; i++) {
      String paramValueString = paramValueStrings[i];
      final int eqIndex = paramValueString.indexOf("=");
      if (eqIndex >=0 ){
        setValue(paramValueString.substring(0, eqIndex), paramValueString.substring(eqIndex + 1));
      }
    }

    return tail.substring(endOfProxySettings + 1);
  }

  private void setValue(final String paramName, final String paramValue) {
    if (paramName.length() == 0 || paramValue.length() == 0) return;

    if ("username".equals(paramName)){
      USER_NAME = paramValue;
    }
    else if("password".equals(paramName)){
      PASSWORD = paramValue;
    }
    else if ("hostname".equals(paramName)){
      HOST=paramValue;
    }
    else if ("proxy".equals(paramName)){
      PROXY_HOST= paramValue;
    }
    else if ("proxyport".equals(paramName)){
      PROXY_PORT= paramValue;
    }
    else if ("port".equals(paramName)){
      PORT= paramValue;
    }

  }

  private static String methodEntry(String method) {
    return ":" + method + ":";
  }


  private String extractMethod(String str, CvsRootParser cvsRoot, boolean check) {
    for (int i = 0; i < CvsMethod.AVAILABLE_METHODS.length; i++) {
      CvsMethod cvsMethod = CvsMethod.AVAILABLE_METHODS[i];
      String tail = tryToCutMethod(cvsMethod, str);
      if (tail != null) {
        cvsRoot.METHOD = cvsMethod;
        return tail;
      }
    }
    if (check) {
      throw new IllegalArgumentException("wrong method: " + str);
    }
    cvsRoot.METHOD = CvsMethod.AVAILABLE_METHODS[0];
    if (!StringUtil.startsWithChar(str, ':')) return str;
    int nextSep = str.indexOf(":", 1);
    if (nextSep < 0) return str;
    return str.substring(nextSep + 1);
  }

  private CvsRootParser() {
    METHOD = null;
    USER_NAME = "";
    HOST = "";
    REPOSITORY = "";
    PORT = null;
    PASSWORD = null;
  }
}
