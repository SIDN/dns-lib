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
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import nl.sidnlabs.dnslib.exception.DnsDecodeException;
import nl.sidnlabs.dnslib.exception.DnsEncodeException;

/**
 * DNS Label Types
 * 
 * Registration Procedures IESG Approval Reference [RFC-ietf-dnsext-rfc2671bis-edns0-10] Note IETF
 * standards action required to allocate new types The top 2 bits of the first byte of an DNS label
 * indicates the type of label. Registration of further Extended Label Types is closed per
 * [RFC-ietf-dnsext-rfc2671bis-edns0-10].
 * 
 * Value Type Status Reference 0 0 Normal label lower 6 bits is the length of the label Standard
 * [RFC1035] 1 1 Compressed label the lower 6 bits and the 8 bits from next octet form a pointer to
 * the compression target. Standard [RFC1035] 0 1 Extended label type the lower 6 bits of this type
 * (section 3) indicate the type of label in use Proposed [RFC-ietf-dnsext-rfc2671bis-edns0-10] 0 1
 * 0 0 0 0 0 1 Binary Label Experimental not recommended [RFC3364][RFC3363][RFC2673] 0 1 1 1 1 1 1 1
 * Reserved for future expansion. Proposed [RFC-ietf-dnsext-rfc2671bis-edns0-10] 1 0 Unallocated
 *
 * 
 */
public class DNSStringUtil {

  private DNSStringUtil() {}

  // max length of a rfc1035 character-string (excluding length byte)
  private static final int MAX_CHARACTER_STRING_LENGTH = 253;
  private static final int MAX_LABEL_LENGTH = 63;
  private static final int MAX_LABELS = 127;

  private static final int MAX_POINTER_CHAIN_LENGTH = 10; // TODO: what is the optimal value?


  private static final byte DEC_DOT_CHAR_LABEL_SEP = 46;
  private static final byte[] SHARED_STRING_DATA_BUFFER = new byte[255];

  /*
   * 
   * 4.1.4. Message compression
   * 
   * In order to reduce the size of messages, the domain system utilizes a compression scheme which
   * eliminates the repetition of domain names in a message. In this scheme, an entire domain name
   * or a list of labels at the end of a domain name is replaced with a pointer to a prior occurance
   * of the same name.
   * 
   * The pointer takes the form of a two octet sequence:
   * 
   * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+ | 1 1| OFFSET |
   * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
   * 
   * The first two bits are ones. This allows a pointer to be distinguished from a label, since the
   * label must begin with two zero bits because labels are restricted to 63 octets or less. (The 10
   * and 01 combinations are reserved for future use.) The OFFSET field specifies an offset from the
   * start of the message (i.e., the first octet of the ID field in the domain header). A zero
   * offset specifies the first byte of the ID field, etc.
   * 
   * The compression scheme allows a domain name in a message to be represented as either:
   * 
   * - a sequence of labels ending in a zero octet
   * 
   * - a pointer
   * 
   * - a sequence of labels ending with a pointer
   * 
   * Pointers can only be used for occurances of a domain name where the format is not class
   * specific. If this were not the case, a name server or resolver would be required to know the
   * format of all RRs it handled. As yet, there are no such cases, but they may occur in future
   * RDATA formats.
   * 
   * 
   */

  private static final byte UNCOMPRESSED_NAME_BIT_MASK = (byte) 0x3f; // 0011 1111
  private static final byte COMPRESSED_NAME_BIT_MASK = (byte) 0xc0; // 1100 0000


  public static boolean isUncompressedName(byte namePrefix) {
    return (namePrefix | UNCOMPRESSED_NAME_BIT_MASK) == UNCOMPRESSED_NAME_BIT_MASK;
  }

  public static boolean isCompressedName(byte namePrefix) {
    return (namePrefix & COMPRESSED_NAME_BIT_MASK) == COMPRESSED_NAME_BIT_MASK;
  }

  public static String readName(NetworkData buffer) {
    int currentPosition = -1;
    StringBuilder nameBuilder = new StringBuilder();

    short length = buffer.readUnsignedByte();

    if (length == 0) {
      /* zero length label means "." root */
      return ".";
    }

    // keep track of the total length of the name
    // prevent creating huge name and and getting OOM exception
    int totalLength = 0;
    int totalLabels = 0;
    // keep reading labels until zero length (end of string) is reached
    while (length > 0) {

      if (totalLabels == MAX_LABELS) {
        // too many labels used, stop now to prevent possible infinite loop
        throw new DnsEncodeException(
            "Too many labels (max 127) for name: " + nameBuilder.toString());
      }

      if (totalLength > MAX_CHARACTER_STRING_LENGTH) {
        // protection against OOM
        throw new DnsEncodeException(
            "total name length length exceeding max (253) for name: " + nameBuilder.toString());
      }

      if (isUncompressedName((byte) length)) {

        if (length > MAX_LABEL_LENGTH) {
          throw new DnsDecodeException("Unsupported label length found, value: " + (int) length);
        }

        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        String label = new String(bytes, StandardCharsets.US_ASCII);

        nameBuilder.append(label);
        nameBuilder.append(".");
        // add label len + dot to total length
        totalLength = totalLength + label.length() + 1;
        totalLabels++;

      } else if (isCompressedName((byte) length)) {
        // save location in the stream (after reading the 2 (offset) bytes)
        if (currentPosition == -1) {
          // only save first pointer location, there may be multiple
          // pointers forming a chain
          //
          currentPosition = buffer.getReaderIndex();
        }
        // follow 1 or more pointers to the data label.
        followPointerChain(buffer);
      } else {
        throw new DnsDecodeException("Unsupported label type found");
      }

      length = buffer.readUnsignedByte();
    }

    // set index position to the first byte after the first pointer (16 bytes)
    if (currentPosition >= 0) {
      buffer.setReaderIndex(currentPosition + 1);
    }

    return nameBuilder.toString();
  }

  private static String bytesToString(byte[] data, int length) {
    return new String(data, 0, length, StandardCharsets.US_ASCII);
  }


  /**
   * Optimized version of the readName method, this version uses a shared buffer to prevent having
   * to keep allocating memory for new strings. the buffers is used to store all bytes for the
   * string and when all bytes have been found the bytes are converted into a String.
   * 
   * NOTE: This method is not thread safe, due to the shared buffer
   * 
   * @param buffer
   * @return
   */
  public static String readNameUsingBuffer(NetworkData buffer) {
    int currentPosition = -1;
    short length = buffer.readUnsignedByte();

    if (length == 0) {
      /* zero length label means "." root */
      return ".";
    }

    // keep track of position in dst buffer
    int bufferIndex = 0;
    // keep track of the total length of the name
    // prevent creating huge name and and getting OOM exception
    int totalLength = 0;
    int totalLabels = 0;
    // keep reading labels until zero length (end of string) is reached
    while (length > 0) {

      if (totalLabels == MAX_LABELS) {
        // too many labels used, stop now to prevent possible infinite loop
        throw new DnsEncodeException("Too many labels (max 127) for name: "
            + bytesToString(SHARED_STRING_DATA_BUFFER, bufferIndex));
      }

      if (totalLength > MAX_CHARACTER_STRING_LENGTH) {
        // protection against OOM
        throw new DnsEncodeException("total name length length exceeding max (253) for name: "
            + bytesToString(SHARED_STRING_DATA_BUFFER, bufferIndex));
      }

      if (isUncompressedName((byte) length)) {

        if (length > MAX_LABEL_LENGTH) {
          throw new DnsDecodeException("Unsupported label length found, value: " + (int) length);
        }

        buffer.readBytes(SHARED_STRING_DATA_BUFFER, bufferIndex, length);
        bufferIndex += length;
        SHARED_STRING_DATA_BUFFER[bufferIndex++] = DEC_DOT_CHAR_LABEL_SEP;
        totalLength += length + 1;
        totalLabels++;

      } else if (isCompressedName((byte) length)) {
        // save location in the stream (after reading the 2 (offset) bytes)
        if (currentPosition == -1) {
          // only save first pointer location, there may be multiple
          // pointers forming a chain
          //
          currentPosition = buffer.getReaderIndex();
        }
        // follow 1 or more pointers to the data label.
        followPointerChain(buffer);
      } else {
        throw new DnsDecodeException("Unsupported label type found");
      }

      length = buffer.readUnsignedByte();
    }

    // set index position to the first byte after the first pointer (16 bytes)
    if (currentPosition >= 0) {
      buffer.setReaderIndex(currentPosition + 1);
    }

    return bytesToString(SHARED_STRING_DATA_BUFFER, bufferIndex);
  }

  /**
   * 
   * Follow 1 or more pointers to the data label After this method the index for the buffer param
   * will be at the length byte of a data label.
   * 
   * @param buffer bytes with DNS message
   */
  private static void followPointerChain(NetworkData buffer) {
    short length = 0;
    // protected against infinite loop (attack)
    int jumps = 0;
    do {
      jumps++;
      // go back one byte to read the 16bit offset as a char
      buffer.rewind(1);

      /*
       * some servers support pointer chaining (Knot) check for too long or infinite chain length
       */

      if (jumps == MAX_POINTER_CHAIN_LENGTH) {
        // protection against infinite loops
        throw new DnsDecodeException("Illegal pointer chain size: " + jumps);
      }

      // read 16 bits
      char offset = buffer.readUnsignedChar();
      // clear the first 2 bits used to indicate compressen vs uncompressed label
      offset = (char) (offset ^ (1 << 14)); // flip bit 14 to 0
      offset = (char) (offset ^ (1 << 15)); // flip bit 15 to 0

      if ((byte) offset >= (buffer.getReaderIndex() - 2)) {
        throw new DnsDecodeException(
            "Message compression pointer offset higher than current index");
      }

      // goto the pointer location in the buffer
      buffer.setReaderIndex(offset, true);
      // check for next pointre in case of pointer chaining
      length = buffer.readUnsignedByte();
    } while (isCompressedName((byte) length));


    // go 1 byte because we read the length of the next label already
    buffer.rewind(1);
  }



  public static void writeName(String name, NetworkData buffer) {

    // write nameserver string
    String[] labels = StringUtils.split(name, ".");
    for (String label : labels) {
      // write label length
      buffer.writeByte(label.length());
      buffer.writeBytes(label.getBytes());
    }

    // write root with zero byte
    buffer.writeByte(0);

  }

  public static byte[] writeName(String name) {

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);

    try {
      // write nameserver string
      String[] labels = StringUtils.split(name, ".");
      for (String label : labels) {
        // write label length
        dos.writeByte(label.length());
        dos.write(label.getBytes());
      }

      // write root with zero byte
      dos.writeByte(0);
    } catch (IOException e) {
      throw new DnsEncodeException("Error while writing name", e);
    }

    return bos.toByteArray();

  }


  public static String readLabelData(NetworkData buffer) {
    int length = buffer.readUnsignedByte();
    if (length > MAX_CHARACTER_STRING_LENGTH) {
      throw new DnsDecodeException("Illegal character string length (> 253), length = " + length);
    }
    if (length > 0) {
      byte[] characterString = new byte[length];
      buffer.readBytes(characterString);
      return new String(characterString, StandardCharsets.US_ASCII);
    }

    return "";

  }

  public static void writeLabelData(String value, NetworkData buffer) {
    byte[] data = value.getBytes();
    if (data.length > MAX_CHARACTER_STRING_LENGTH) {
      throw new DnsEncodeException(
          "Illegal character string length (> 253), length = " + data.length);
    }
    if (data.length > 0) {
      buffer.writeByte(data.length);
      buffer.writeBytes(data);
    }
  }

}
