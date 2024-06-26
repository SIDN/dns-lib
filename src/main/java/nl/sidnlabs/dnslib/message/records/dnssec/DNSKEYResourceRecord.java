
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
package nl.sidnlabs.dnslib.message.records.dnssec;

import java.security.PublicKey;

import org.apache.commons.codec.binary.Base64;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.message.records.AbstractResourceRecord;
import nl.sidnlabs.dnslib.message.util.NetworkData;
import nl.sidnlabs.dnslib.types.AlgorithmType;
import nl.sidnlabs.dnslib.util.KeyUtil;

@Getter
@Setter
public class DNSKEYResourceRecord extends AbstractResourceRecord {

  private static final long serialVersionUID = 1L;

  /*
   * The RDATA for a DNSKEY RR consists of a 2 octet Flags Field, a 1 octet Protocol Field, a 1
   * octet Algorithm Field, and the Public Key Field.
   * 
   * 1 1 1 1 1 1 1 1 1 1 2 2 2 2 2 2 2 2 2 2 3 3 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
   * 6 7 8 9 0 1 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ | Flags |
   * Protocol | Algorithm | +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ / / /
   * Public Key / / / +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * 
   */


  private char flags;
  private short protocol;
  private AlgorithmType algorithm;
  private PublicKey publicKey;
  private byte[] keydata;

  private int keytag;
  private boolean isZoneKey;
  private boolean isSepKey;


  @Override
  public void decode(NetworkData buffer, boolean partial) {
    super.decode(buffer, partial);
    if (!partial) {
      flags = buffer.readUnsignedChar();

      protocol = buffer.readUnsignedByte();

      short alg = buffer.readUnsignedByte();
      this.algorithm = AlgorithmType.fromValue(alg);

      char keysize = (char) (rdLength - 4); // 4 is length flags+proto+alg
      keydata = new byte[keysize];
      buffer.readBytes(keydata);

      publicKey = KeyUtil.createPublicKey(keydata, alg);

      keytag = KeyUtil.createKeyTag(rdata, alg);

      isZoneKey = KeyUtil.isZoneKey(this);

      isSepKey = KeyUtil.isSepKey(this);
    }
  }

  public boolean isValid() {

    if (protocol != 3) {
      // invalid key, skip checking this rrsig
      return false;
    }
    // The Flag field MUST be represented as an unsigned decimal integer.
    // Given the currently defined flags, the possible values are: 0, 256,
    // and 257.
    return (flags == 0 || flags == 256 || flags == 257);
  }

  @Override
  public void encode(NetworkData buffer) {
    super.encode(buffer);

    buffer.writeChar(rdLength);

    buffer.writeChar(flags);

    buffer.writeByte(protocol);

    buffer.writeByte(algorithm.getValue());

    buffer.writeBytes(keydata);
  }

  @Override
  public String toZone(int maxLength) {
    return super.toZone(maxLength) + " " + (int) flags + " " + protocol + " " + algorithm.getValue()
        + "(\n\t\t\t\t\t\t" + new Base64(36, "\n\t\t\t\t\t\t".getBytes()).encodeAsString(keydata)
        + " )";
  }

  @Override
  public JsonObject toJSon() {
    JsonObjectBuilder builder = super.createJsonBuilder();
    return builder
        .add("rdata", Json.createObjectBuilder().add("flags", (int) flags))
        .add("protocol", protocol)
        .add("algorithm", algorithm.name())
        .add("zone-key", isZoneKey)
        .add("sep-key", isSepKey)
        .add("keytag", keytag)
        .build();
  }

}

