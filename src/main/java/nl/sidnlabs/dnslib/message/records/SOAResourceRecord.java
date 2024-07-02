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
import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.message.util.DNSStringUtil;
import nl.sidnlabs.dnslib.message.util.NetworkData;

@Getter
@Setter
public class SOAResourceRecord extends AbstractResourceRecord {

  private static final long serialVersionUID = -5562024832624364745L;
  private String mName;
  private String rName;
  private long serial;
  private long refresh;
  private long retry;
  private long expire;
  private long minimum;


  @Override
  public void decode(NetworkData buffer, boolean partial) {
    super.decode(buffer, partial);

    if (!partial) {
      mName = DNSStringUtil.readNameUsingBuffer(buffer);

      rName = DNSStringUtil.readNameUsingBuffer(buffer);

      serial = buffer.readUnsignedInt();

      refresh = buffer.readUnsignedInt();

      retry = buffer.readUnsignedInt();

      expire = buffer.readUnsignedInt();

      minimum = buffer.readUnsignedInt();
    }
  }

  @Override
  public void encode(NetworkData buffer) {
    super.encode(buffer);

    /*
     * length is names + leading size byte and terminating zero byte plus 5 4byte fields.
     */
    char rdLength = (char) ((mName.length() + 2) + (rName.length() + 2) + (5 * 4));

    // write rdlength
    buffer.writeChar(rdLength);

    DNSStringUtil.writeName(mName, buffer);

    DNSStringUtil.writeName(rName, buffer);

    buffer.writeInt((int) serial);

    buffer.writeInt((int) refresh);

    buffer.writeInt((int) retry);

    buffer.writeInt((int) expire);

    buffer.writeInt((int) minimum);

  }

  @Override
  public String toZone(int maxLength) {
    return super.toZone(maxLength) + "\t" + mName + " " + rName + " " + serial + " " + refresh + " "
        + retry + " " + expire + " " + minimum;
  }

  @Override
  public JsonObject toJSon() {
    JsonObjectBuilder builder = super.createJsonBuilder();
    return builder
        .add("rdata",
            Json
                .createObjectBuilder()
                .add("mname", mName)
                .add("rname", rName)
                .add("serial", serial)
                .add("refresh", refresh)
                .add("retry", retry)
                .add("expire", expire)
                .add("minimum", minimum))
        .build();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (int) (expire ^ (expire >>> 32));
    result = prime * result + ((mName == null) ? 0 : mName.hashCode());
    result = prime * result + (int) (minimum ^ (minimum >>> 32));
    result = prime * result + ((rName == null) ? 0 : rName.hashCode());
    result = prime * result + (int) (refresh ^ (refresh >>> 32));
    result = prime * result + (int) (retry ^ (retry >>> 32));
    result = prime * result + (int) (serial ^ (serial >>> 32));
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
    SOAResourceRecord other = (SOAResourceRecord) obj;
    if (expire != other.expire)
      return false;
    if (mName == null) {
      if (other.mName != null)
        return false;
    } else if (!mName.equals(other.mName))
      return false;
    if (minimum != other.minimum)
      return false;
    if (rName == null) {
      if (other.rName != null)
        return false;
    } else if (!rName.equals(other.rName))
      return false;
    if (refresh != other.refresh)
      return false;
    if (retry != other.retry)
      return false;
    if (serial != other.serial)
      return false;
    return true;
  }

}
