/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ozone.rocksdb.util;

import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.SstFileReader;
import org.rocksdb.SstFileReaderIterator;

import java.io.Closeable;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ManagedSSTFileReader {

  private final Collection<String> sstFiles;

  public ManagedSSTFileReader(final Collection<String> sstFiles) {
    this.sstFiles = sstFiles;
  }
  public Stream<String> getKeyStream() throws RocksDBException {
    final OMSSTFileIterator iterator = new OMSSTFileIterator(sstFiles);
    final Spliterator<String> spliterator = Spliterators
        .spliteratorUnknownSize(iterator, 0);
    return StreamSupport.stream(spliterator, false).onClose(iterator::close);
  }

  public interface ClosableIterator<T> extends Iterator<T>, Closeable {}

  private static class OMSSTFileIterator implements ClosableIterator<String> {

    private final Iterator<String> fileNameIterator;
    private final Options options;
    private final ReadOptions readOptions;
    private String currentFile;
    private SstFileReader currentFileReader;
    private SstFileReaderIterator currentFileIterator;

    private OMSSTFileIterator(Collection<String> files) throws RocksDBException {
      // TODO: Check if default Options and ReadOptions is enough.
      this.options = new Options();
      this.readOptions = new ReadOptions();
      this.fileNameIterator = files.iterator();
      moveToNextFile();
    }

    @Override
    public boolean hasNext() {
      try {
        do {
          if (currentFileIterator.isValid()) {
            return true;
          }
        } while (moveToNextFile());
      } catch (RocksDBException e) {
        return false;
      }
      return false;
    }

    @Override
    public String next() {
      if (hasNext()) {
        final String value = new String(currentFileIterator.key());
        currentFileIterator.next();
        return value;
      }
      throw new NoSuchElementException("No more keys");
    }

    @Override
    public void close() {
      closeCurrentFile();
    }

    private boolean moveToNextFile() throws RocksDBException {
      if (fileNameIterator.hasNext()) {
        closeCurrentFile();
        currentFile = fileNameIterator.next();
        currentFileReader = new SstFileReader(options);
        currentFileReader.open(currentFile);
        currentFileIterator = currentFileReader.newIterator(readOptions);
        currentFileIterator.seekToFirst();
        return true;
      }
      return false;
    }

    private void closeCurrentFile() {
      if (currentFile != null) {
        currentFileIterator.close();
        currentFileReader.close();
        currentFile = null;
      }
    }
  }

}
