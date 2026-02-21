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

import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.exception.DnsDecodeException;
import nl.sidnlabs.dnslib.message.util.NetworkData;

@Getter
@Setter
public class AResourceRecord extends AbstractResourceRecord {

  private static final long serialVersionUID = -1960441085310394001L;
  private String address;
  private int[] ipv4Bytes;


  @Override
  public void decode(NetworkData buffer, boolean partial) {
    super.decode(buffer, partial);

    InetAddress ip;
    byte[] addrBytes = buffer.readBytes(4, 4);
    try {
      ip = InetAddress.getByAddress(addrBytes);
    } catch (UnknownHostException e) {
      throw new DnsDecodeException("Invalid IP address", e);
    }
    setAddress(ip.getHostAddress());
  }

  @Override
  public void encode(NetworkData buffer) {
    super.encode(buffer);

    // write rdlength
    buffer.writeChar(rdLength);
    if (ipv4Bytes != null && ipv4Bytes.length == 4) {
      for (int i = 0; i < 4; i++) {
        buffer.writeByte(ipv4Bytes[i]);
      }
    }
  }

  @Override
  public String toZone(int maxLength) {
    return super.toZone(maxLength) + "\t" + address;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((address == null) ? 0 : address.hashCode());
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
    AResourceRecord other = (AResourceRecord) obj;
    if (address == null) {
      if (other.address != null)
        return false;
    } else if (!address.equals(other.address))
      return false;
    return true;
  }
  
  @Override
  public String rDataToString() {
	  return address;
  }

}
