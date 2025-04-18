/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.ozone.common;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.hadoop.hdds.protocol.datanode.proto.ContainerProtos;
import org.apache.hadoop.hdds.protocol.datanode.proto.ContainerProtos.ChecksumType;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

/**
 * Java class that represents Checksum ProtoBuf class. This helper class allows
 * us to convert to and from protobuf to normal java.
 */
public class ChecksumData {

  private final ChecksumType type;
  // Checksum will be computed for every bytesPerChecksum number of bytes and
  // stored sequentially in checksumList
  private final int bytesPerChecksum;
  private final List<ByteString> checksums;

  public ChecksumData(ChecksumType checksumType, int bytesPerChecksum) {
    this(checksumType, bytesPerChecksum, Collections.emptyList());
  }

  public ChecksumData(ChecksumType checksumType, int bytesPerChecksum,
                      List<ByteString> checksums) {
    this.type = checksumType;
    this.bytesPerChecksum = bytesPerChecksum;
    this.checksums = Collections.unmodifiableList(checksums);
  }

  /**
   * Getter method for checksumType.
   */
  public ChecksumType getChecksumType() {
    return this.type;
  }

  /**
   * Getter method for bytesPerChecksum.
   */
  public int getBytesPerChecksum() {
    return this.bytesPerChecksum;
  }

  /**
   * Getter method for checksums.
   */
  public List<ByteString> getChecksums() {
    return this.checksums;
  }

  /**
   * Construct the Checksum ProtoBuf message.
   * @return Checksum ProtoBuf message
   */
  public ContainerProtos.ChecksumData getProtoBufMessage() {
    ContainerProtos.ChecksumData.Builder checksumProtoBuilder =
        ContainerProtos.ChecksumData.newBuilder()
            .setType(this.type)
            .setBytesPerChecksum(this.bytesPerChecksum);

    checksumProtoBuilder.addAllChecksums(checksums);

    return checksumProtoBuilder.build();
  }

  /**
   * Constructs Checksum class object from the Checksum ProtoBuf message.
   * @param checksumDataProto Checksum ProtoBuf message
   * @return ChecksumData object representing the proto
   */
  public static ChecksumData getFromProtoBuf(
      ContainerProtos.ChecksumData checksumDataProto) {
    Preconditions.checkNotNull(checksumDataProto);

    return new ChecksumData(
        checksumDataProto.getType(),
        checksumDataProto.getBytesPerChecksum(),
        checksumDataProto.getChecksumsList());
  }

  /**
   * Verify that this ChecksumData from startIndex to endIndex matches with the
   * provided ChecksumData.
   * The checksum at startIndex of this ChecksumData will be matched with the
   * checksum at index 0 of the provided ChecksumData, and checksum at
   * (startIndex + 1) of this ChecksumData with checksum at index 1 of
   * provided ChecksumData and so on.
   * @param that the ChecksumData to match with
   * @param startIndex index of the first checksum from this ChecksumData
   *                   which will be used to compare checksums
   * @return true if checksums match
   * @throws OzoneChecksumException
   */
  public boolean verifyChecksumDataMatches(ChecksumData that, int startIndex)
      throws OzoneChecksumException {

    // pre checks
    if (this.checksums.isEmpty()) {
      throw new OzoneChecksumException("Original checksumData has no " +
          "checksums");
    }

    if (that.checksums.isEmpty()) {
      throw new OzoneChecksumException("Computed checksumData has no " +
          "checksums");
    }

    int numChecksums = that.checksums.size();

    try {
      // Verify that checksum matches at each index
      for (int index = 0; index < numChecksums; index++) {
        if (!matchChecksumAtIndex(this.checksums.get(startIndex + index),
            that.checksums.get(index))) {
          // checksum mismatch. throw exception.
          throw new OzoneChecksumException(index);
        }
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new OzoneChecksumException("Computed checksum has "
          + numChecksums + " number of checksums. Original checksum has " +
          (this.checksums.size() - startIndex) + " number of checksums " +
          "starting from index " + startIndex);
    }
    return true;
  }

  private static boolean matchChecksumAtIndex(
      ByteString expectedChecksumAtIndex, ByteString computedChecksumAtIndex) {
    return expectedChecksumAtIndex.equals(computedChecksumAtIndex);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ChecksumData)) {
      return false;
    }

    ChecksumData that = (ChecksumData) obj;

    if (!this.type.equals(that.getChecksumType())) {
      return false;
    }
    if (this.bytesPerChecksum != that.getBytesPerChecksum()) {
      return false;
    }
    if (this.checksums.size() != that.checksums.size()) {
      return false;
    }

    // Match checksum at each index
    for (int index = 0; index < this.checksums.size(); index++) {
      if (!matchChecksumAtIndex(this.checksums.get(index),
          that.checksums.get(index))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    HashCodeBuilder hc = new HashCodeBuilder();
    hc.append(type);
    hc.append(bytesPerChecksum);
    hc.append(checksums.toArray());
    return hc.toHashCode();
  }
}
