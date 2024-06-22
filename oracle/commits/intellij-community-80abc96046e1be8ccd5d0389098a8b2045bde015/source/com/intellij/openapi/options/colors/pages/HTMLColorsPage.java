/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.options.colors.pages;

import com.intellij.ide.highlighter.HtmlFileHighlighter;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;

import javax.swing.*;
import java.util.Map;

public class HTMLColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor("Comment", HighlighterColors.HTML_COMMENT),
    new AttributesDescriptor("Tag", HighlighterColors.HTML_TAG),
    new AttributesDescriptor("Tag name", HighlighterColors.HTML_TAG_NAME),
    new AttributesDescriptor("Attribute name", HighlighterColors.HTML_ATTRIBUTE_NAME),
    new AttributesDescriptor("Attribute value", HighlighterColors.HTML_ATTRIBUTE_VALUE)
  };

  private static final ColorDescriptor[] COLORS = new ColorDescriptor[0];

  public String getDisplayName() {
    return "HTML";
  }

  public Icon getIcon() {
    return StdFileTypes.HTML.getIcon();
  }

  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  public ColorDescriptor[] getColorDescriptors() {
    return COLORS;
  }

  public SyntaxHighlighter getHighlighter() {
    return new HtmlFileHighlighter();
  }

  public String getDemoText() {
    return "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2//EN\">\n" +
           "<!--\n" +
           "*        Sample comment\n" +
           "-->\n" +
           "<HTML>\n" +
           "<head>\n" +
           "<title>IntelliJ IDEA</title>\n" +
           "</head>\n" +
           "<body>\n" +
           "<h1>IntelliJ IDEA</h1>\n" +
           "<p><br><b><IMG border=0 height=12 src=\"images/hg.gif\" width=18 >\n" +
           "What is IntelliJ IDEA?</b><br><br>\n" +
           "</body>\n" +
           "</html>";
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}