/*
 * ENTRADA, a big data platform for network data analytics
 *
 * Copyright (C) 2016 SIDN [https://www.sidn.nl]
 * 
 * This file is part of ENTRADA.
 * 
 * ENTRADA is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * ENTRADA is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with ENTRADA. If not, see
 * [<http://www.gnu.org/licenses/].
 *
 */
package nl.sidnlabs.dnslib.message.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import nl.sidnlabs.dnslib.exception.DnsEncodeException;


public class NetworkData {

  private final byte[] stringDecodeBuffer = new byte[255];

  private byte[] buf;

  private int index = 0;
  private int baseIndex = 0;
  private int markedIndex = 0;
  private int length = 0;

  // write part
  private ByteArrayOutputStream backing;
  private DataOutput writeBuffer;
  private int writerIndex;

  public NetworkData(int size) {
    backing = new ByteArrayOutputStream(size);
    writeBuffer = new DataOutputStream(backing);
  }

  public NetworkData() {
    this(4096);
  }

  public NetworkData(byte[] data) {
    this.buf = data;
    index = 0;
    length = data.length;
  }

  /**
   * Allow for data buffer with additional data. Data before position marked by offset and after
   * position marked by length will be ignored.
   * 
   * @param data
   * @param offset
   * @param length
   */
  public NetworkData(byte[] data, int offset, int length) {
    this.buf = data;
    this.index = offset;
    this.baseIndex = offset;
    this.length = length;
  }

  public NetworkData update(byte[] data, int offset, int length) {
    this.buf = data;
    this.index = offset;
    this.baseIndex = offset;
    this.length = length;

    return this;
  }

  public int length() {
    return length;
  }

  public int bytesAvailable() {
    return length - index;
  }

  public boolean isBytesAvailable() {
    return index < length;
  }

  public long readUnsignedInt() {

    int byte1 = (0xFF & buf[index]);
    int byte2 = (0xFF & buf[index + 1]);
    int byte3 = (0xFF & buf[index + 2]);
    int byte4 = (0xFF & buf[index + 3]);
    index = index + 4;

    return ((long) (byte1 << 24 | byte2 << 16 | byte3 << 8 | byte4)) & 0xFFFFFFFFL;
  }


  public short readUnsignedByte() {
    int byte1 = (0xFF & buf[index]);
    index++;
    return (short) byte1;
  }


  public char readUnsignedChar() {
    int byte1 = (0xFF & buf[index]);
    int byte2 = (0xFF & buf[index + 1]);
    index = index + 2;
    return (char) (byte1 << 8 | byte2);
  }

  public byte[] readBytes() {
    byte[] destination = new byte[length - index];
    System.arraycopy(buf, index, destination, 0, destination.length);
    index = index + destination.length;
    return destination;
  }

  public void readBytes(byte[] destination) {
    System.arraycopy(buf, index, destination, 0, destination.length);
    index = index + destination.length;
  }

  /**
   * Read limited number of bytes into result (max param), but always return result with size of len
   * param. addional bytes are zero.
   * 
   * @param len the size of return byte[]
   * @param max the max number of bytes read from buffer
   * @return fixed sized array
   */
  public byte[] readBytes(int len, int max) {
    byte[] destination = new byte[len];
    System.arraycopy(buf, index, destination, 0, max);
    index = index + max;
    return destination;
  }

  public void readBytes(byte[] dst, int pos, int len) {
    System.arraycopy(buf, index, dst, pos, len);
    index = index + len;
  }

  public void writeChar(int c) {
    try {
      writeBuffer.writeChar(c);
      writerIndex += 2;
    } catch (IOException e) {
      throw new DnsEncodeException("Error while writing char", e);
    }
  }

  public void writeByte(int b) {
    try {
      writeBuffer.write(b);
      writerIndex++;
    } catch (IOException e) {
      throw new DnsEncodeException("Error while writing byte", e);
    }
  }

  public void writeBytes(byte[] b) {
    try {
      writeBuffer.write(b);
      writerIndex = writerIndex + b.length;
    } catch (IOException e) {
      throw new DnsEncodeException("Error while writing byte[]", e);
    }
  }

  public void writeInt(long i) {
    try {
      writeBuffer.writeInt((int) i);
      writerIndex += 4;
    } catch (IOException e) {
      throw new DnsEncodeException("Error while writing int", e);
    }
  }

  public byte[] write() {
    byte[] data = backing.toByteArray();
    return Arrays.copyOf(data, writerIndex);
  }

  public int getReaderIndex() {
    return index;
  }

  public void setReaderIndex(int index) {
    setReaderIndex(index, false);
  }

  public void setReaderIndex(int index, boolean abs) {
    if (abs) {
      // when setting absolute index in the buffer, make sure to include the baseindex
      // baseindex is the start of the data in the buffer
      this.index = baseIndex + index;
    } else {
      this.index = index;
    }
  }

  public int getWriterIndex() {
    return writerIndex;
  }

  public void markReaderIndex() {
    markedIndex = index;
  }

  public void resetReaderIndex() {
    index = markedIndex;
  }

  public void rewind(int bytes) {
    this.index = this.index - bytes;
  }

  public byte[] getStringDecodeBuffer() {
    return stringDecodeBuffer;
  }

}
