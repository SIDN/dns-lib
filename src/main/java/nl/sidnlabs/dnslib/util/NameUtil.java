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
import lombok.extern.log4j.Log4j2;

@Log4j2
public class NameUtil {

  private NameUtil() {}

  /**
   * Get domain info
   * 
   * @param name name to check
   * @return Domaininfo with the domain name that is one level beneath the public suffix and the #
   *         of labels in the input name
   */
  public static Domaininfo getDomain(String name) {
    return getDomain(name, false);
  }

  public static Domaininfo getDomain(String name, boolean allowInvalid) {

    if (name == null || name.length() == 0) {
      return new Domaininfo(null, 0);
    } else if (StringUtils.equals(name, ".")) {
      return new Domaininfo(name, 0);
    }

    try {
      InternetDomainName domainname = InternetDomainName.from(name, allowInvalid);

      // check if the name is an exact macth for public suffix
      if (domainname.isRegistrySuffix()) {
        return new Domaininfo(domainname.registrySuffix().toString(), domainname.parts().size());
      }

      if (domainname.isUnderRegistrySuffix()) {
        return new Domaininfo(domainname.topDomainUnderRegistrySuffix().toString(),
            domainname.parts().size());
      }
    } catch (Exception e) {
      // bad name or the name is an exact match with a public suffix entry
      // do nothing here, continue with fallback
      if (log.isDebugEnabled()) {
        log.debug("InternetDomainName error", e);
      }
    }

    // fallback
    // name is not a valid domain name, remove any trailing dots and count # of labels by splitting
    // on the remaining dots
    // return null for the name to indicate it is an invalid name
    return new Domaininfo(null, StringUtils.split(StringUtils.removeEnd(name, "."), ".").length);
  }

}
