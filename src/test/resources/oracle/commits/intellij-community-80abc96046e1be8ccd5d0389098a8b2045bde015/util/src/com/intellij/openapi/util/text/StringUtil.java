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
package com.intellij.openapi.util.text;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class StringUtil {
  private static final String VOWELS = "aeiouy";

  public static String replace(@NotNull String text, @NotNull String oldS, @Nullable String newS) {
    return replace(text, oldS, newS, false);
  }

  public static String replaceIgnoreCase(@NotNull String text, @NotNull String oldS, @Nullable String newS) {
    return replace(text, oldS, newS, true);
  }

  public static String replace(@NotNull String text, @NotNull String oldS, @Nullable String newS, boolean ignoreCase) {
    if (text.length() < oldS.length()) return text;

    String text1 = ignoreCase ? text.toLowerCase() : text;
    String oldS1 = ignoreCase ? oldS.toLowerCase() : oldS;
    StringBuffer newText = null;
    int i = 0;
    while (i < text1.length()) {
      int i1 = text1.indexOf(oldS1, i);
      if (i1 < 0) {
        if (i == 0) return text;
        newText.append(text.substring(i));
        break;
      }
      else {
        if (newS == null) return null;
        if (newText == null) {
          newText = new StringBuffer();
        }
        newText.append(text.substring(i, i1));
        newText.append(newS);
        i = i1 + oldS.length();
      }
    }
    return newText != null ? newText.toString() : "";
  }

  /**
   * Converts line separators to <code>"\n"</code>
   */
  public static String convertLineSeparators(@NotNull String text) {
    return convertLineSeparators(text, "\n", null);
  }

  public static String convertLineSeparators(@NotNull String text, String newSeparator) {
    return convertLineSeparators(text, newSeparator, null);
  }

  public static String convertLineSeparators(@NotNull String text, String newSeparator, int[] offsetsToKeep) {
    StringBuffer buffer = new StringBuffer(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        buffer.append(newSeparator);
        shiftOffsets(offsetsToKeep, buffer.length(), 1, newSeparator.length());
      }
      else if (c == '\r') {
        buffer.append(newSeparator);
        if (i < text.length() - 1 && text.charAt(i + 1) == '\n') {
          i++;
          shiftOffsets(offsetsToKeep, buffer.length(), 2, newSeparator.length());
        }
        else {
          shiftOffsets(offsetsToKeep, buffer.length(), 1, newSeparator.length());
        }
      }
      else {
        buffer.append(c);
      }
    }
    return buffer.toString();
  }

  private static void shiftOffsets(int[] offsets, int changeOffset, int oldLength, int newLength) {
    if (offsets == null) return;
    int shift = newLength - oldLength;
    if (shift == 0) return;
    for (int i = 0; i < offsets.length; i++) {
      int offset = offsets[i];
      if (offset >= changeOffset + oldLength) {
        offsets[i] += shift;
      }
    }
  }

  public static int getLineBreakCount(@NotNull CharSequence text) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        count++;
      }
      else if (c == '\r') {
        if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
          count++;
        }
        else {
          count++;
        }
      }
    }
    return count;
  }

  public static int lineColToOffset(@NotNull CharSequence text, int line, int col) {
    int curLine = 0;
    int offset = 0;
    while (true) {
      if (line == curLine) {
        return offset + col;
      }
      if (offset == text.length()) return -1;
      char c = text.charAt(offset);
      if (c == '\n') {
        curLine++;
      }
      else if (c == '\r') {
        curLine++;
        if (offset < text.length() - 1 && text.charAt(offset + 1) == '\n') {
          offset++;
        }
      }
      offset++;
    }
  }

  public static int offsetToLineNumber(@NotNull CharSequence text, int offset) {
    int curLine = 0;
    int curOffset = 0;
    while (true) {
      if (offset <= curOffset) {
        return curLine;
      }
      if (curOffset == text.length()) return -1;
      char c = text.charAt(curOffset);
      if (c == '\n') {
        curLine++;
      }
      else if (c == '\r') {
        curLine++;
        if (curOffset < text.length() - 1 && text.charAt(curOffset + 1) == '\n') {
          curOffset++;
        }
      }
      curOffset++;
    }
  }

  /**
   * Classic dynamic programming algorithm for string differences.
   */
  public static int difference(@NotNull String s1, @NotNull String s2) {
    int[][] a = new int[s1.length()][s2.length()];

    for (int i = 0; i < s1.length(); i++) {
      a[i][0] = i;
    }

    for (int j = 0; j < s2.length(); j++) {
      a[0][j] = j;
    }

    for (int i = 1; i < s1.length(); i++) {
      for (int j = 1; j < s2.length(); j++) {

        a[i][j] = Math.min(Math.min(a[i - 1][j - 1] + (s1.charAt(i) == s2.charAt(j) ? 0 : 1),
                                    a[i - 1][j] + 1),
                           a[i][j - 1] + 1);
      }
    }

    return a[s1.length() - 1][s2.length() - 1];
  }

  public static final String wordsToBeginFromUpperCase(@NotNull String s) {
    StringBuffer buffer = null;
    for (int i = 0; i < s.length(); i++) {
      char prevChar = i == 0 ? ' ' : s.charAt(i - 1);
      char currChar = s.charAt(i);
      if (!Character.isLetterOrDigit(prevChar)) {
        if (Character.isLetterOrDigit(currChar)) {
          if (!Character.isUpperCase(currChar)) {
            int j = i;
            for (; j < s.length(); j++) {
              if (!Character.isLetterOrDigit(s.charAt(j))) {
                break;
              }
            }
            if (!isPreposition(s, i, j - 1)) {
              if (buffer == null) {
                buffer = new StringBuffer(s);
              }
              buffer.setCharAt(i, Character.toUpperCase(currChar));
            }
          }
        }
      }
    }
    if (buffer == null) {
      return s;
    }
    else {
      return buffer.toString();
    }
  }

  private static final String[] ourPrepositions = new String[]{
    "at", "the", "and", "not", "if", "a", "or", "to", "in", "on", "into"
  };


  public static final boolean isPreposition(@NotNull String s, int firstChar, int lastChar) {
    for (String preposition : ourPrepositions) {
      boolean found = false;
      if (lastChar - firstChar + 1 == preposition.length()) {
        found = true;
        for (int j = 0; j < preposition.length(); j++) {
          if (!(Character.toLowerCase(s.charAt(firstChar + j)) == preposition.charAt(j))) {
            found = false;
          }
        }
      }
      if (found) {
        return true;
      }
    }
    return false;
  }

  public static void escapeStringCharacters(int length, final String str, StringBuffer buffer) {
    for (int idx = 0; idx < length; idx++) {
      char ch = str.charAt(idx);
      switch (ch) {
        case '\b':
          buffer.append("\\b");
          break;

        case '\t':
          buffer.append("\\t");
          break;

        case '\n':
          buffer.append("\\n");
          break;

        case '\f':
          buffer.append("\\f");
          break;

        case '\r':
          buffer.append("\\r");
          break;

        case '\"':
          buffer.append("\\\"");
          break;

        case '\\':
          buffer.append("\\\\");
          break;

        default:
          if (Character.isISOControl(ch)) {
            String hexCode = Integer.toHexString(ch).toUpperCase();
            buffer.append("\\u");
            int paddingCount = 4 - hexCode.length();
            while (paddingCount-- > 0) {
              buffer.append(0);
            }
            buffer.append(hexCode);
          }
          else {
            buffer.append(ch);
          }
      }
    }
  }

  public static String escapeStringCharacters(@NotNull String s) {
    StringBuffer buffer = new StringBuffer();
    escapeStringCharacters(s.length(), s, buffer);
    return buffer.toString();
  }


  public static String unescapeStringCharacters(@NotNull String s) {
    StringBuffer buffer = new StringBuffer();
    unescapeStringCharacters(s.length(), s, buffer);
    return buffer.toString();
  }

  private static void unescapeStringCharacters(int length, String s, StringBuffer buffer) {
    boolean escaped = false;
    for (int idx = 0; idx < length; idx++) {
      char ch = s.charAt(idx);
      if (!escaped) {
        if (ch == '\\') {
          escaped = true;
        }
        else {
          buffer.append(ch);
        }
      }
      else {
        switch (ch) {
          case 'n':
            buffer.append('\n');
            break;

          case 'r':
            buffer.append('\r');
            break;

          case 'b':
            buffer.append('\b');
            break;

          case 't':
            buffer.append('\t');
            break;

          case 'f':
            buffer.append('\f');
            break;

          case '\'':
            buffer.append('\'');
            break;

          case '\"':
            buffer.append('\"');
            break;

          case '\\':
            buffer.append('\\');
            break;

          case 'u':
            int sum = 0;
            int i;
            for (i = idx + 1; i < idx + 5 && i < length; i++) {
              sum *= 16;
              sum += Integer.valueOf(s.substring(i, i + 1), 16).intValue();
            }
            idx = i;
            buffer.append((char)sum);
            break;
          default:
            buffer.append(ch);
            break;
        }
        escaped = false;
      }
    }

    if (escaped) buffer.append('\\');
  }

  public static String pluralize(@NotNull String suggestion) {
    if (StringUtil.endsWithChar(suggestion, 's') || StringUtil.endsWithChar(suggestion, 'x') ||
        suggestion.endsWith("ch")) {
      suggestion += "es";
    }
    else {
      int len = suggestion.length();
      if (StringUtil.endsWithChar(suggestion, 'y') && len > 1 && !isVowel(suggestion.charAt(len - 2))) {
        suggestion = suggestion.substring(0, len - 1) + "ies";
      }
      else {
        suggestion += "s";
      }
    }
    return suggestion;
  }

  public static String capitalizeWords(@NotNull String text, boolean allWords) {
    StringTokenizer tokenizer = new StringTokenizer(text);
    String out = "";
    String delim = "";
    boolean toCapitalize = true;
    while (tokenizer.hasMoreTokens()) {
      String word = tokenizer.nextToken();
      out += delim + (toCapitalize ? capitalize(word) : word);
      delim = " ";
      if (!allWords) {
        toCapitalize = false;
      }
    }
    return out;
  }

  public static String decapitalize(String s) {
    return Introspector.decapitalize(s);
  }

  public static boolean isVowel(char c) {
    return VOWELS.indexOf(c) >= 0;
  }

  public static String capitalize(@NotNull String s) {
    if (s.length() == 0) return s;
    if (s.length() == 1) return s.toUpperCase();
    if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  public static int stringHashCode(CharSequence chars) {
    if (chars instanceof String) return chars.hashCode();
    if (chars instanceof CharSequenceWithStringHash) return chars.hashCode();
    int h = 0;
    int to = chars.length();
    for( int off = 0; off < to; off++) {
      h = 31*h + chars.charAt(off);
    }
    return h;
  }

  public static int stringHashCode( char chars[], int from, int len ) {
    int h = 0;
    int to = from + len;
    for( int off = from; off < to; off++) {
      h = 31*h + chars[off];
    }
    return h;
  }

  public static int stringHashCodeInsensitive( char chars[], int from, int len ) {
    int h = 0;
    int to = from + len;
    for( int off = from; off < to; off++) {
      h = 31*h + Character.toLowerCase(chars[off]);
    }
    return h;
  }

  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    int h = 0;
    final int len = chars.length();
    for( int i = 0; i < len; i++) {
      h = 31*h + Character.toLowerCase(chars.charAt(i));
    }
    return h;
  }

  public static String trimEnd(@NotNull String s, @NotNull String suffix) {
    if (s.endsWith(suffix)) {
      return s.substring(0, s.lastIndexOf(suffix));
    }
    return s;
  }

  public static boolean startsWithChar(CharSequence s, char prefix) {
    return s != null && s.length() != 0 && s.charAt(0) == prefix;
  }
  public static boolean endsWithChar(CharSequence s, char suffix) {
    return s != null && s.length() != 0 && s.charAt(s.length()-1) == suffix;
  }

  public static String trimStart(@NotNull String s, @NotNull String prefix) {
    if (s.startsWith(prefix)) {
      return s.substring(prefix.length());
    }
    return s;
  }

  public static String pluralize(@NotNull String base, int n) {
    if (n == 1) return base;
    return pluralize(base);
  }

  public static void repeatSymbol(StringBuffer buffer, char symbol, int times) {
    for (int i = 0; i < times; i++) {
      buffer.append(symbol);
    }
  }

  public static boolean isNotEmpty(final String s) {
    return s != null && s.length() > 0;
  }

  public static boolean isEmpty(final String s) {
    return s == null || s.length() == 0;
  }

  public static boolean isEmptyOrSpaces(final String s) {
    return s == null || s.trim().length() == 0;
  }


  public static String getThrowableText(Throwable aThrowable) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    aThrowable.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
  }

  public static String getMessage(Throwable e) {
    String result = e.getMessage();
    final String exceptionPattern = "Exception: ";
    final String errorPattern = "Error: ";

    while ( (result.indexOf(exceptionPattern) >=0 || result.indexOf(errorPattern) >=0 ) && e.getCause() != null) {
      e = e.getCause();
      result = e.getMessage();
    }

    result = extractMessage(result, exceptionPattern);
    result = extractMessage(result, errorPattern);

    return result;
  }

  private static String extractMessage(@NotNull String result, @NotNull final String errorPattern) {
    if (result.lastIndexOf(errorPattern) >= 0 ) {
      result = result.substring(result.lastIndexOf(errorPattern) + errorPattern.length());
    }
    return result;
  }

  public static String repeatSymbol(final char aChar, final int count) {
    final StringBuffer buffer = new StringBuffer();
    repeatSymbol(buffer,aChar, count);
    return buffer.toString();
  }

  public static List<String> split(@NotNull String s, @NotNull String separator) {
    if (separator.length() == 0) {
      return Collections.singletonList(s);
    }
    ArrayList<String> result = new ArrayList<String>();
    int pos = 0;
    while (true) {
      int index = s.indexOf(separator, pos);
      if (index == -1) break;
      String token = s.substring(pos, index);
      if (token.length() != 0) {
        result.add(token);
      }
      pos = index + separator.length();
    }
    if (pos < s.length()) {
      result.add(s.substring(pos, s.length()));
    }
    return result;
  }

  public static List<String> getWordsIn(@NotNull String text) {
    List<String> result = new SmartList<String>();
    int start = -1;
    for (int i=0;i<text.length();i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = Character.isJavaIdentifierPart(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length()-1 && start != -1) {
        result.add(text.substring(start, i+1));
      }
      else if (!isIdentifierPart && start != -1) {
        result.add(text.substring(start, i));
        start = -1;
      }
    }
    return result;
  }

  public static String join(@NotNull final String[] strings, final String separator) {
    final StringBuffer result = new StringBuffer();
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  public static String join(@NotNull Collection<String> strings, final String separator) {
    final StringBuffer result = new StringBuffer();
    for (String string : strings) {
      if (string != null && string.length() != 0) {
        if (result.length() != 0) result.append(separator);
        result.append(string);
      }
    }
    return result.toString();
  }

  public static String join(@NotNull final int[] strings, final String separator) {
    final StringBuffer result = new StringBuffer();
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  public static String stripQuotesAroundValue(String text) {
    if (startsWithChar(text, '\"') || startsWithChar(text, '\'')) text = text.substring(1);
    if (endsWithChar(text, '\"') || endsWithChar(text, '\'')) text = text.substring(0, text.length() - 1);
    return text;
  }

  /**
   * Formats the specified file size as a string.
   *
   * @param fileSize the size to format.
   * @return the size formatted as a string.
   * @since 5.0.1
   */

  public static String formatFileSize(final long fileSize) {
    if (fileSize < 0x400) {
        return fileSize + "b";
    }
    if (fileSize < 0x100000) {
        long kbytes = fileSize * 100 / 1024;
        return kbytes / 100 + "." + kbytes % 100 + "Kb";
    }
    long mbytes = fileSize * 100 / 1024;
    return mbytes / 100 + "." + mbytes % 100 + "Mb";
  }
}
