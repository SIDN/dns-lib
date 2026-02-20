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
package nl.sidnlabs.dnslib.message.records;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.message.util.NetworkData;

@Getter
@Setter
public class TXTResourceRecord extends AbstractResourceRecord {

  private static final long serialVersionUID = 1L;

  protected String value = "";
  protected byte[] data;


  @Override
  public void decode(NetworkData buffer, boolean partial) {
    super.decode(buffer, partial);

    if (!partial) {
      // read all rdata at once, then process length-prefixed segments
      byte[] rdata = new byte[rdLength];
      buffer.readBytes(rdata);

      StringBuilder builder = new StringBuilder(rdLength);
      int offset = 0;
      while (offset < rdLength) {
        int segmentLength = rdata[offset++] & 0xFF;
        builder.append(new String(rdata, offset, segmentLength, StandardCharsets.US_ASCII));
        offset += segmentLength;
      }

      // keep last segment bytes available for encode
      data = rdata;
      value = builder.toString();
    }
  }

  @Override
  public void encode(NetworkData buffer) {
    super.encode(buffer);

    // write rdlength
    buffer.writeChar(rdLength);
    buffer.writeByte(data.length);
    buffer.writeBytes(data);

  }

  @Override
  public String toZone(int maxLength) {
    return super.toZone(maxLength) + "\t" + value;
  }

  @Override
  public JsonObject toJSon() {
    JsonObjectBuilder builder = super.createJsonBuilder();
    return builder.add("rdata", Json.createObjectBuilder().add("txt-data", value)).build();
  }

  @Override
  public String rDataToString() {
	  return value;
  }
}
