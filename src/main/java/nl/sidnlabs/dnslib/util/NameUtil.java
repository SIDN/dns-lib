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
package nl.sidnlabs.dnslib.util;

import org.apache.commons.lang3.StringUtils;
import com.google.common.net.InternetDomainName;

public class NameUtil {

  private NameUtil() {}

  /**
   * Get domain info
   * 
   * @param name name to check
   * @return domain info containing parsed data or original data if input was invalid.
   */
  public static Domaininfo getDomain(String name) {

    if (name == null || name.length() == 0) {
      return new Domaininfo(null, 0);
    } else if (StringUtils.equals(name, ".")) {
      return new Domaininfo(name, 0);
    }

    try {
      InternetDomainName domainname = InternetDomainName.from(name);
      return new Domaininfo(domainname.topPrivateDomain().toString(), domainname.parts().size());
    } catch (Exception e) {
      // bad name
    }

    return new Domaininfo(name, -1);

  }

  public static boolean isFqdn(String name) {

    try {
      return InternetDomainName.isValid(name);
    } catch (Exception e) {
      // bad name
    }

    return false;
  }

  public static String getSecondLevel(String name) {

    if (name == null || name.length() == 0) {
      return null;
    }

    return getDomain(name).getName();
  }

}
