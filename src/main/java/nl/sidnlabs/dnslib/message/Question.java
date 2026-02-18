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
package nl.sidnlabs.dnslib.message;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.message.util.DNSStringUtil;
import nl.sidnlabs.dnslib.message.util.NetworkData;
import nl.sidnlabs.dnslib.types.ResourceRecordClass;
import nl.sidnlabs.dnslib.types.ResourceRecordType;

@Getter
@Setter
public class Question {

  private String qName;
  private ResourceRecordType qType;
  private int qTypeValue;
  private ResourceRecordClass qClass;
  private int qClassValue;

  public Question() {}

  public Question(String qName, ResourceRecordType qType, ResourceRecordClass qClass) {
    this.qName = qName;
    this.qType = qType;
    this.qClass = qClass;
  }

  public void decode(NetworkData buffer) {

    setQName(DNSStringUtil.readNameUsingBuffer(buffer));

    qTypeValue = buffer.readUnsignedChar();
    setQType(ResourceRecordType.fromValue(qTypeValue));

    qClassValue = buffer.readUnsignedChar();
    setQClass(ResourceRecordClass.fromValue(qClassValue));

  }

  public JsonObject toJSon() {
    return Json
        .createObjectBuilder()
        .add("qName", qName)
        .add("qType", qType != null ? qType.name() : "")
        .add("qClass", qClass != null ? qClass.name() : "")
        .build();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + qClassValue;
    result = prime * result + ((qName == null) ? 0 : qName.hashCode());
    result = prime * result + qTypeValue;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Question other = (Question) obj;
    if (qClassValue != other.qClassValue)
      return false;
    if (qName == null) {
      if (other.qName != null)
        return false;
    } else if (!qName.equals(other.qName))
      return false;
    if (qTypeValue != other.qTypeValue)
      return false;
    return true;
  }
}
