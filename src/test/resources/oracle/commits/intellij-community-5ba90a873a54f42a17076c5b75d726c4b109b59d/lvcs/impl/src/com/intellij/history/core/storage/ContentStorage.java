package com.intellij.history.core.storage;

import com.intellij.util.io.RecordDataOutput;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

public class ContentStorage implements IContentStorage {
  private com.intellij.util.io.storage.Storage myStore;

  public ContentStorage(File f) throws IOException {
    myStore = com.intellij.util.io.storage.Storage.create(f.getPath());
  }

  public static IContentStorage createContentStorage(File f) throws IOException {
    IContentStorage s = new ContentStorage(f);
    s = new CachingContentStorage(s);
    s = new CompressingContentStorage(s);
    s = new ThreadSafeContentStorage(s);
    return s;
  }

  public void save() {
    // make storage flush all data to prevent
    // its corruption when idea process is killed by force
    myStore.force();
  }

  public void close() {
    myStore.dispose();
  }

  public int store(byte[] content) throws BrokenStorageException {
    try {
      RecordDataOutput r = myStore.createStream();
      r.writeInt(content.length);
      r.write(content);
      r.close();
      return r.getRecordId();
    }
    catch (Throwable e) {
      throw new BrokenStorageException(e);
    }
  }

  public byte[] load(int id) throws BrokenStorageException {
    try {
      DataInputStream r = myStore.readStream(id);
      byte[] buffer = new byte[r.readInt()];
      r.readFully(buffer);
      r.close();
      return buffer;
    }
    catch (Throwable e) {
      throw new BrokenStorageException(e);
    }
  }

  public void remove(int id) {
    myStore.deleteRecord(id);
  }

  public int getVersion() {
    return myStore.getVersion();
  }

  public void setVersion(final int version) {
    myStore.setVersion(version);
  }
}