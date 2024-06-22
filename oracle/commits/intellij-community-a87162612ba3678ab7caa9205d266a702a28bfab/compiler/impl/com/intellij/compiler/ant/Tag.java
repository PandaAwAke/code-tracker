package com.intellij.compiler.ant;

import com.intellij.openapi.util.Pair;

import java.io.DataOutput;
import java.io.IOException;

import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Tag extends CompositeGenerator {
  private final String myTagName;
  private final Pair[] myTagOptions;

  public Tag(@NonNls String tagName, Pair[] tagOptions) {
    myTagName = tagName;
    myTagOptions = tagOptions;
  }

  public void generate(DataOutput out) throws IOException {
    out.writeBytes("<");
    out.writeBytes(myTagName);
    if (myTagOptions != null && myTagOptions.length > 0) {
      out.writeBytes(" ");
      int generated = 0;
      for (int idx = 0; idx < myTagOptions.length; idx++) {
        final Pair option = myTagOptions[idx];
        if (option == null) {
          continue;
        }
        if (generated > 0) {
          out.writeBytes(" ");
        }
        out.writeBytes((String)option.getFirst());
        out.writeBytes("=\"");
        out.writeBytes((String)option.getSecond());
        out.writeBytes("\"");
        generated += 1;
      }
    }
    if (getGeneratorCount() > 0) {
      out.writeBytes(">");
      shiftIndent();
      try {
        super.generate(out);
      }
      finally {
        unshiftIndent();
      }
      crlf(out);
      out.writeBytes("</");
      out.writeBytes(myTagName);
      out.writeBytes(">");
    }
    else {
      out.writeBytes("/>");
    }
  }

  protected static Pair<String, String> pair(@NonNls String v1, @NonNls String v2) {
    if (v2 == null) {
      return null;
    }
    return new Pair<String, String>(v1, v2);
  }
}
