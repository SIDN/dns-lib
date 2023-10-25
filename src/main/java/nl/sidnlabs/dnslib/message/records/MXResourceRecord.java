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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.message.util.DNSStringUtil;
import nl.sidnlabs.dnslib.message.util.NetworkData;

@Getter
@Setter
public class MXResourceRecord extends AbstractResourceRecord {

  private static final long serialVersionUID = 6877262886026363052L;
  private char preference;
  private String exchange;


  @Override
  public void decode(NetworkData buffer, boolean partial) {
    super.decode(buffer, partial);

    if (!partial) {
      preference = buffer.readUnsignedChar();
      exchange = DNSStringUtil.readNameUsingBuffer(buffer);
    }
  }

  @Override
  public void encode(NetworkData buffer) {
    super.encode(buffer);

    // write rdlength exchange lentg + prefix and postfix and 2 bytes for preference

    buffer.writeChar(exchange.length() + 2 + 2);

    // write prefs
    buffer.writeChar(preference);

    DNSStringUtil.writeName(exchange, buffer);
  }

  @Override
  public String toZone(int maxLength) {
    return super.toZone(maxLength) + "\t" + (int) preference + " " + exchange;
  }

  @Override
  public JsonObject toJSon() {
    JsonObjectBuilder builder = super.createJsonBuilder();
    return builder
        .add("rdata",
            Json
                .createObjectBuilder()
                .add("preference", (int) preference)
                .add("exchange", exchange))
        .build();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((exchange == null) ? 0 : exchange.hashCode());
    result = prime * result + preference;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    MXResourceRecord other = (MXResourceRecord) obj;
    if (exchange == null) {
      if (other.exchange != null)
        return false;
    } else if (!exchange.equals(other.exchange))
      return false;
    if (preference != other.preference)
      return false;
    return true;
  }

}
