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

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.message.util.DNSStringUtil;
import nl.sidnlabs.dnslib.message.util.NetworkData;
import nl.sidnlabs.dnslib.types.ResourceRecordClass;
import nl.sidnlabs.dnslib.types.ResourceRecordType;


@Getter
@Setter
public abstract class AbstractResourceRecord implements ResourceRecord, Serializable {

  private static final long serialVersionUID = -2781381098732827757L;
  protected String name;
  protected char rawType;
  protected char rawClassz;
  protected ResourceRecordType type;
  protected ResourceRecordClass classz;
  protected long ttl;
  protected char rdLength;
  protected byte[] rdata;

  @Override
  public void decode(NetworkData buffer, boolean partial) {
    setName(DNSStringUtil.readNameUsingBuffer(buffer));

    rawType = buffer.readUnsignedChar();
    setType(ResourceRecordType.fromValue(rawType));
    rawClassz = buffer.readUnsignedChar();
    setClassz(ResourceRecordClass.fromValue(rawClassz));
    setTtl(buffer.readUnsignedInt());

    // read 16 bits rdlength field
    rdLength = buffer.readUnsignedChar();

    // rdata = readRdata(rdLength, buffer);

    if (partial) {
      // do not create rdata object, just update the buffer index
      buffer.setReaderIndex(buffer.getReaderIndex() + rdLength);
    } else if (rdLength <= buffer.bytesAvailable()) {
      // invalid length, ignore
      buffer.markReaderIndex();
      rdata = new byte[rdLength];
      buffer.readBytes(rdata);
      buffer.resetReaderIndex();
    }

  }

  @Override
  public void encode(NetworkData buffer) {
    DNSStringUtil.writeName(getName(), buffer);

    buffer.writeChar(getType().getValue());

    buffer.writeChar(getClassz().getValue());

    buffer.writeInt((int) getTtl());
  }

  // protected byte[] readRdata(int rdlength, NetworkData buffer) {
  //
  // if (rdlength > buffer.bytesAvailable()) {
  // // invalid length, ignore
  // return new byte[0];
  // }
  //
  // buffer.markReaderIndex();
  // byte[] data = new byte[rdlength];
  // buffer.readBytes(data);
  // buffer.resetReaderIndex();
  // return data;
  // }

  public int getRawType() {
    return (int) rawType;
  }

  public int getRawClassz() {
    return (int) rawClassz;
  }

  @Override
  public String toZone(int maxLength) {
    int paddedSize = (maxLength - name.length()) + name.length();
    String ownerWithPadding = StringUtils.rightPad(name, paddedSize, " ");
    return ownerWithPadding + "\t" + ttl + "\t" + classz + "\t" + type;
  }

  public JsonObjectBuilder createJsonBuilder() {
    return Json
        .createObjectBuilder()
        .add("name", name)
        .add("type", type.name())
        .add("class", classz.name())
        .add("ttl", ttl)
        .add("rdLength", (int) rdLength);
  }

  public JsonObject toJSon() {
    JsonObjectBuilder builder = createJsonBuilder();
    return builder.add("rdata", Json.createObjectBuilder().add("dummy", "toddo")).build();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AbstractResourceRecord other = (AbstractResourceRecord) obj;
    if (classz != other.classz)
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (rdLength != other.rdLength)
      return false;
    if (ttl != other.ttl)
      return false;
    if (type != other.type)
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((classz == null) ? 0 : classz.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + rdLength;
    result = prime * result + (int) (ttl ^ (ttl >>> 32));
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  @Override
  public String rDataToString() {
	  return null;
  }
}
