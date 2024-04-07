package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.MethodInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 18, 2003
 * Time: 6:32:32 PM
 */
public class CacheUtils {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.CacheUtils");

  public static String[] getParameterSignatures(MethodInfo methodDeclarationId, SymbolTable symbolTable) throws CacheCorruptedException {
    String descriptor = symbolTable.getSymbol(methodDeclarationId.getDescriptor());
    int endIndex = descriptor.indexOf(')');
    if (endIndex <= 0) {
      LOG.assertTrue(false, "Corrupted method descriptor: "+descriptor);
    }
    return parseSignature(descriptor.substring(1, endIndex));
  }

  private static String[] parseSignature(String signature) {
    final ArrayList<String> list = new ArrayList<String>();
    String paramSignature = parseParameterSignature(signature);
    while (paramSignature != null && !"".equals(paramSignature)) {
      list.add(paramSignature);
      signature = signature.substring(paramSignature.length());
      paramSignature = parseParameterSignature(signature);
    }
    return list.toArray(new String[list.size()]);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static @Nullable String parseParameterSignature(String signature) {
    if (StringUtil.startsWithChar(signature, 'B')) {
      return "B";
    }
    if (StringUtil.startsWithChar(signature, 'C')) {
      return "C";
    }
    if (StringUtil.startsWithChar(signature, 'D')) {
      return "D";
    }
    if (StringUtil.startsWithChar(signature, 'F')) {
      return "F";
    }
    if (StringUtil.startsWithChar(signature, 'I')) {
      return "I";
    }
    if (StringUtil.startsWithChar(signature, 'J')) {
      return "J";
    }
    if (StringUtil.startsWithChar(signature, 'S')) {
      return "S";
    }
    if (StringUtil.startsWithChar(signature, 'Z')) {
      return "Z";
    }
    if (StringUtil.startsWithChar(signature, 'L')) {
      return signature.substring(0, signature.indexOf(";") + 1);
    }
    if (StringUtil.startsWithChar(signature, '[')) {
      String s = parseParameterSignature(signature.substring(1));
      return (s != null) ? ("[" + s) : null;
    }
    return null;
  }

  public static String getMethodSignature(String name, String descriptor) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(name);
      builder.append(descriptor.substring(0, descriptor.indexOf(')') + 1));
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public static boolean areArraysContentsEqual(int[] exceptions1, int[] exceptions2) {
    if (exceptions1.length != exceptions2.length) {
      return false;
    }
    if (exceptions1.length != 0) { // optimization
      TIntHashSet exceptionsSet = new TIntHashSet(exceptions1);
      for (int exception : exceptions2) {
        if (!exceptionsSet.contains(exception)) {
          return false;
        }
      }
    }
    return true;
  }
}
