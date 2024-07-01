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
public class NSResourceRecord extends AbstractResourceRecord {

  private static final long serialVersionUID = 1023178649874976277L;
  private String nameserver;


  @Override
  public void decode(NetworkData buffer, boolean partial) {
    super.decode(buffer, partial);
    if (!partial) {
      setNameserver(DNSStringUtil.readNameUsingBuffer(buffer));
    }
  }

  @Override
  public void encode(NetworkData buffer) {
    super.encode(buffer);

    // write rdlength
    buffer.writeChar(nameserver.length() + 2); // + 2 for leading lenght byte + the terminating root

    DNSStringUtil.writeName(nameserver, buffer);

  }

  @Override
  public String toZone(int maxLength) {
    return super.toZone(maxLength) + "\t" + nameserver;
  }

  @Override
  public JsonObject toJSon() {
    JsonObjectBuilder builder = super.createJsonBuilder();
    return builder.add("rdata", Json.createObjectBuilder().add("nameserver", nameserver)).build();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    NSResourceRecord other = (NSResourceRecord) obj;
    if (nameserver == null) {
      if (other.nameserver != null)
        return false;
    } else if (!nameserver.equals(other.nameserver))
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((nameserver == null) ? 0 : nameserver.hashCode());
    return result;
  }
  
  @Override
  public String rDataToString() {
	  return nameserver;
  }

}
