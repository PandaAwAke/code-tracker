/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"HardCodedStringLiteral"})
public class XmlTagTextUtil {
  private static Map<String, Character> ourCharacterEntities;

  static {
    ourCharacterEntities = new HashMap<String, Character>();
    ourCharacterEntities.put("lt", new Character('<'));
    ourCharacterEntities.put("gt", new Character('>'));
    ourCharacterEntities.put("apos", new Character('\''));
    ourCharacterEntities.put("quot", new Character('\"'));
    ourCharacterEntities.put("nbsp", new Character('\u00a0'));
    ourCharacterEntities.put("amp", new Character('&'));
  }

  private XmlTagTextUtil() {}

  /**
   * if text contains XML-sensitive characters (<,>), quote text with ![CDATA[ ... ]]
   *
   * @param text
   * @return quoted text
   */
  public static String getCDATAQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&\n";
    final int textLength = text.length();
    if(textLength > 0 && (Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(textLength - 1))))
      return "<![CDATA[" + text + "]]>";
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }

  public static String getInlineQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&";
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }


  public static String composeTagText(String tagName, String tagValue) {
    String result = "<" + tagName;
    if (tagValue == null || "".equals(tagValue)) {
      result += "/>";
    }
    else {
      result += ">" + getCDATAQuote(tagValue) + "</" + tagName + ">";
    }
    return result;
  }

  public static String[] getCharacterEntityNames() {
    Set<String> strings = ourCharacterEntities.keySet();
    return strings.toArray(new String[strings.size()]);
  }

  public static Character getCharacterByEntityName(String entityName) {
    return ourCharacterEntities.get(entityName);
  }

}