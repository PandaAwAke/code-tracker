/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import org.jdom.Element;

import java.awt.*;

public class TextAttributes implements JDOMExternalizable, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.markup.TextAttributes");

  public static TextAttributes merge(TextAttributes under, TextAttributes above) {
    if (under == null) return above;
    if (above == null) return under;
    
    TextAttributes attrs = under.clone();
    if (above.getBackgroundColor() != null){
      attrs.setBackgroundColor(above.getBackgroundColor());
    }
    if (above.getForegroundColor() != null){
      attrs.setForegroundColor(above.getForegroundColor());
    }
    attrs.setFontType(above.getFontType() | under.getFontType());

    if (above.getEffectColor() != null){
      attrs.setEffectColor(above.getEffectColor());
      attrs.setEffectType(above.getEffectType());
    }
    return attrs;
  }

  private static class Externalizable implements Cloneable, JDOMExternalizable {
    public Color FOREGROUND = null;
    public Color BACKGROUND = null;

    public int FONT_TYPE = Font.PLAIN;

    public Color EFFECT_COLOR = null;
    public int EFFECT_TYPE = EFFECT_BORDER;
    public Color ERROR_STRIPE_COLOR = null;

    private static final int EFFECT_BORDER = 0;
    private static final int EFFECT_LINE = 1;
    private static final int EFFECT_WAVE = 2;
    private static final int EFFECT_STRIKEOUT = 3;

    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
      if (FONT_TYPE < 0 || FONT_TYPE > 3) {
        LOG.info("Wrong font type: " + FONT_TYPE);
        FONT_TYPE = 0;
      }
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  private Externalizable myExternalizable;

  public TextAttributes() {
    myExternalizable = new Externalizable();
  }

  public TextAttributes(Color foregroundColor, Color backgroundColor, Color effectColor, EffectType effectType, int fontType) {
    myExternalizable = new Externalizable();
    setForegroundColor(foregroundColor);
    setBackgroundColor(backgroundColor);
    setEffectColor(effectColor);
    setEffectType(effectType);
    setFontType(fontType);
  }

  public boolean isEmpty(){
    return getForegroundColor() == null && getBackgroundColor() == null && getEffectColor() == null && getFontType() == Font.PLAIN;
  }

  public Color getForegroundColor() {
    return myExternalizable.FOREGROUND;
  }

  public void setForegroundColor(Color color) {
    myExternalizable.FOREGROUND = color;
  }

  public Color getBackgroundColor() {
    return myExternalizable.BACKGROUND;
  }

  public void setBackgroundColor(Color color) {
    myExternalizable.BACKGROUND = color;
  }

  public Color getEffectColor() {
    return myExternalizable.EFFECT_COLOR;
  }

  public void setEffectColor(Color color) {
    myExternalizable.EFFECT_COLOR = color;
  }

  public Color getErrorStripeColor() {
    return myExternalizable.ERROR_STRIPE_COLOR;
  }

  public void setErrorStripeColor(Color color) {
    myExternalizable.ERROR_STRIPE_COLOR = color;
  }

  public EffectType getEffectType() {
    switch (myExternalizable.EFFECT_TYPE) {
      case Externalizable.EFFECT_BORDER:
        return EffectType.BOXED;
      case Externalizable.EFFECT_LINE:
        return EffectType.LINE_UNDERSCORE;
      case Externalizable.EFFECT_STRIKEOUT:
        return EffectType.STRIKEOUT;
      case Externalizable.EFFECT_WAVE:
        return EffectType.WAVE_UNDERSCORE;
      default:
        return null;
    }
  }

  public void setEffectType(EffectType effectType) {
    if (effectType == EffectType.BOXED) {
      myExternalizable.EFFECT_TYPE = Externalizable.EFFECT_BORDER;
    } else if (effectType == EffectType.LINE_UNDERSCORE) {
      myExternalizable.EFFECT_TYPE = Externalizable.EFFECT_LINE;
    } else if (effectType == EffectType.STRIKEOUT) {
      myExternalizable.EFFECT_TYPE = Externalizable.EFFECT_STRIKEOUT;
    } else if (effectType == EffectType.WAVE_UNDERSCORE) {
      myExternalizable.EFFECT_TYPE = Externalizable.EFFECT_WAVE;
    } else {
      myExternalizable.EFFECT_TYPE = -1;
    }
  }

  public int getFontType() {
    return myExternalizable.FONT_TYPE;
  }

  public void setFontType(int type) {
    if (type < 0 || type > 3) {
      LOG.error("Wrong font type: " + type);
      type = 0;
    }
    myExternalizable.FONT_TYPE = type;
  }

  public TextAttributes clone() {
    try {
      TextAttributes cloned = new TextAttributes();
      cloned.myExternalizable = (Externalizable) myExternalizable.clone();
      return cloned;
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public boolean equals(Object obj) {
    if(!(obj instanceof TextAttributes)) {
      return false;
    }
    TextAttributes textAttributes = (TextAttributes)obj;
    if(!Comparing.equal(textAttributes.getForegroundColor(), getForegroundColor())) {
      return false;
    }
    if(!Comparing.equal(textAttributes.getBackgroundColor(), getBackgroundColor())) {
      return false;
    }
    if(!Comparing.equal(textAttributes.getErrorStripeColor(), getErrorStripeColor())) {
      return false;
    }
    if(!Comparing.equal(textAttributes.getEffectColor(), getEffectColor())) {
      return false;
    }
    if (textAttributes.getEffectType() != getEffectType()) {
      return false;
    }
    if(textAttributes.getFontType() != getFontType()) {
      return false;
    }
    return true;
  }

  public int hashCode() {
    int hashCode = 0;
    if(getForegroundColor() != null) {
      hashCode += getForegroundColor().hashCode();
    }
    if(getBackgroundColor() != null) {
      hashCode += getBackgroundColor().hashCode();
    }
    if(getErrorStripeColor() != null) {
      hashCode += getErrorStripeColor().hashCode();
    }
    if(getEffectColor() != null) {
      hashCode += getEffectColor().hashCode();
    }
    hashCode += getFontType();
    return hashCode;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myExternalizable.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myExternalizable.writeExternal(element);
  }
}
