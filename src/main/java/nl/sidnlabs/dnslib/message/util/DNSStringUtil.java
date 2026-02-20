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

  private static final int MAX_POINTER_CHAIN_LENGTH = 10;

  private static final byte DEC_DOT_CHAR_LABEL_SEP = 46;

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

  private static final byte COMPRESSED_NAME_BIT_MASK = (byte) 0xc0; // 1100 0000

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
    return readNameUsingBuffer(buffer, buffer.getStringDecodeBuffer());

  }

  private static String readNameUsingBuffer(NetworkData buffer, byte[] stringBuffer) {
    int currentPosition = -1;
    short length = buffer.readUnsignedByte();

    if (length == 0) {
      /* zero length label means "." root */
      return ".";
    }

    // keep track of position in dst buffer
    int bufferIndex = 0;
    int totalLabels = 0;
    // keep reading labels until zero length (end of string) is reached
    while (length > 0) {

      if (totalLabels == MAX_LABELS) {
        // too many labels used, stop now to prevent possible infinite loop
        throw new DnsEncodeException(
            "Too many labels (max 127) for name: " + toLowerCaseAsciiInPlace(stringBuffer, 0, bufferIndex));
      }

      if (bufferIndex > MAX_CHARACTER_STRING_LENGTH) {
        // protection against OOM
        throw new DnsEncodeException("total name length length exceeding max (253) for name: "
            + toLowerCaseAsciiInPlace(stringBuffer, 0, bufferIndex));
      }

      // inline bit check: uncompressed if top 2 bits are 00 (i.e., (length & 0xC0) == 0)
      if (((byte) length & COMPRESSED_NAME_BIT_MASK) == 0) {

        if (length > MAX_LABEL_LENGTH) {
          throw new DnsDecodeException("Unsupported label length found, value: " + length);
        }

        buffer.readBytes(stringBuffer, bufferIndex, length);
        bufferIndex += length;
        stringBuffer[bufferIndex++] = DEC_DOT_CHAR_LABEL_SEP;
        totalLabels++;

      } else if (((byte) length & COMPRESSED_NAME_BIT_MASK) == COMPRESSED_NAME_BIT_MASK) {
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
    
    return toLowerCaseAsciiInPlace(stringBuffer, 0, bufferIndex);
  }

  /**
   * 
   * Follow 1 or more pointers to the data label After this method the index for the buffer param
   * will be at the length byte of a data label.
   * 
   * @param buffer bytes with DNS message
   */
  private static void followPointerChain(NetworkData buffer) {
    int length = 0;
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
    } while (((byte) length & COMPRESSED_NAME_BIT_MASK) == COMPRESSED_NAME_BIT_MASK);


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

  /**
   * Efficiently converts ASCII string to lowercase.
   * This method is optimized for DNS domain names and only handles ASCII characters.
   * Characters A-Z (65-90) are converted to a-z (97-122), all other characters remain unchanged.
   * This is faster than String.toLowerCase() as it avoids locale handling and String object creation
   * until the final result.
   * 
   * @param input the ASCII string to convert to lowercase
   * @return lowercase version of the input string
   */
  public static String toLowerCaseAscii(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    byte[] bytes = input.getBytes(StandardCharsets.US_ASCII);
    boolean changed = false;

    for (int i = 0; i < bytes.length; i++) {
      byte b = bytes[i];
      // Check if byte is an uppercase ASCII letter (A-Z: 65-90)
      if (b >= 65 && b <= 90) {
        bytes[i] = (byte) (b + 32); // Convert to lowercase (a-z: 97-122)
        changed = true;
      }
    }

    // Only create new string if changes were made, otherwise return original
    return changed ? new String(bytes, StandardCharsets.US_ASCII) : input;
  }

  /**
   * Efficiently converts ASCII byte array to lowercase in place.
   * This method modifies the input array directly for maximum performance.
   * Characters A-Z (65-90) are converted to a-z (97-122).
   * 
   * @param bytes the byte array to convert to lowercase (modified in place)
   * @param offset starting offset in the array
   * @param length number of bytes to process
   */
  public static String toLowerCaseAsciiInPlace(byte[] bytes, int offset, int length) {
    int end = offset + length;
  
    for (int i = offset; i < end; i++) {
      byte b = bytes[i];
      // Check if byte is an uppercase ASCII letter (A-Z: 65-90)
      if (b >= 65 && b <= 90) {
        bytes[i] = (byte) (b + 32); // Convert to lowercase (a-z: 97-122)
      }
    }

    return new String(bytes, offset, length, StandardCharsets.US_ASCII);
  }

}
