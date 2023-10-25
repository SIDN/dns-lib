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
package nl.sidnlabs.dnslib.message.records.edns0;

import lombok.Getter;
import lombok.Setter;
import nl.sidnlabs.dnslib.message.util.NetworkData;


/**
 * @see https://www.rfc-editor.org/rfc/rfc8914.html#name-defined-extended-dns-errors
 *
 */
@Getter
@Setter
public class EDEOption extends EDNS0Option {

  private int code;
  private byte[] msg;

  public EDEOption() {}

  public EDEOption(int code, int len, NetworkData opt) {
    super(code, len, opt);
  }

  @Override
  public void decode(NetworkData buffer) {
    code = buffer.readUnsignedChar();
    if(len > 2) {
      msg = new byte[len-2];
      buffer.readBytes(msg);
    }
  }
}
