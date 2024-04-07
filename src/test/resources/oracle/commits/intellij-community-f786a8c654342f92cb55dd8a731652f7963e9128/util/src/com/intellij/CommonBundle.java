package com.intellij;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.08.2005
 * Time: 18:06:12
 * To change this template use File | Settings | File Templates.
 */
public class CommonBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.CommonBundle");

  private CommonBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.CommonBundle") String key, Object... params) {
    return message(ourBundle, key, params);
  }

  public static String messageOrDefault(@Nullable final ResourceBundle bundle, final String key, String defaultValue, final Object... params) {
    if (bundle == null) return defaultValue;

    String value;
    try {
      value = bundle.getString(key);
    }
    catch (MissingResourceException e) {
      return defaultValue;
    }

    value = UIUtil.replaceMnemonicAmpersand(value);

    if (params.length > 0) {
      return MessageFormat.format(value, params);
    }

    return value;
  }
  public static String message(final ResourceBundle bundle, final String key, final Object... params) {
    return messageOrDefault(bundle, key, "!" + key + "!", params);
  }

  public static String getCancelButtonText() {
    return message("button.cancel");
  }

  public static String getBackgroundButtonText() {
    return message("button.background");
  }

  public static String getHelpButtonText() {
    return message("button.help");
  }

  public static String getErrorTitle() {
    return message("title.error");
  }

  public static String getWarningTitle() {
    return message("title.warning");
  }

  public static String getLoadingTreeNodeText() {
    return CommonBundle.message("tree.node.loading");
  }

  public static String getOkButtonText(){
    return message("button.ok");
  }

  public static String getYesButtonText(){
    return CommonBundle.message("button.yes");
  }

  public static String getNoButtonText(){
    return CommonBundle.message("button.no");
  }

  public static String getContinueButtonText(){
    return CommonBundle.message("button.continue");
  }


  public static String getYesForAllButtonText() {
    return CommonBundle.message("button.yes.for.all");
  }

  public static String getCloseButtonText() {
    return CommonBundle.message("button.close");
  }

  public static String getNoForAllButtonText() {
    return CommonBundle.message("button.no.for.all");
  }

  public static String getApplyButtonText() {
    return CommonBundle.message("button.apply");
  }
}
