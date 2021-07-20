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

import org.junit.Assert;
import org.junit.Test;

public class NameUtilTest {

  @Test
  public void testExtractDomainLevelOk() {
    String domainname = NameUtil.domainname("sidn.nl.");
    Assert.assertEquals("sidn.nl", domainname);
    Assert.assertEquals(2, NameUtil.labels("sidn.nl."));

    domainname = NameUtil.domainname("www.sidn.nl.");
    Assert.assertEquals("sidn.nl", domainname);
    Assert.assertEquals(3, NameUtil.labels("www.sidn.nl."));

    domainname = NameUtil.domainname("test.www.sidn.nl.");
    Assert.assertEquals("sidn.nl", domainname);
    Assert.assertEquals(4, NameUtil.labels("test.www.sidn.nl."));

    domainname = NameUtil.domainname("www.nzrs.co.nz.");
    Assert.assertEquals("nzrs.co.nz", domainname);
    Assert.assertEquals(4, NameUtil.labels("www.nzrs.co.nz."));

    domainname = NameUtil.domainname("_gc._tcp.bpw.net.nz.");
    Assert.assertEquals("bpw.net.nz", domainname);
    Assert.assertEquals(5, NameUtil.labels("_gc._tcp.bpw.net.nz."));

    domainname = NameUtil.domainname("_gc._tcp.default-first-site-name._sites.bpw.net.nz.");
    Assert.assertEquals("bpw.net.nz", domainname);
    Assert.assertEquals(7, NameUtil.labels("_gc._tcp.default-first-site-name._sites.bpw.net.nz."));
  }

  @Test
  public void testEmailAddress2ndLevelOk() {
    // email address should not happen, but sometimes we do see these in the qname.
    Assert.assertEquals("test@example.com", NameUtil.domainname("email.test@example.com."));
  }

  @Test
  public void testDomainWith2ndLevelAndTldSuffixOk() {

    // test fqdn (including final dot)
    String domainname = NameUtil.domainname("name.example.co.uk.");

    Assert.assertNotNull(domainname);
    Assert.assertEquals("example.co.uk", domainname);
    Assert.assertTrue(NameUtil.labels("name.example.co.uk.") == 4);

  }


  @Test
  public void testInvalidQnameOk() {
    String domainname = NameUtil.domainname("-sub1.sidn.nl.");

    Assert.assertEquals("sidn.nl", domainname);
    Assert.assertEquals(3, NameUtil.labels("-sub1.sidn.nl."));

    domainname = NameUtil.domainname("test .sidn.nl.");

    Assert.assertEquals("sidn.nl", domainname);
    Assert.assertEquals(3, NameUtil.labels("test .sidn.nl."));

    domainname = NameUtil.domainname("_.anzrad.co.nz.");
    Assert.assertEquals("anzrad.co.nz", domainname);
    Assert.assertEquals(4, NameUtil.labels("_.anzrad.co.nz."));

    domainname = NameUtil.domainname("r._dns-sd._udp.0x10 0x190x1xp0x190x1ds.ac.nz.");
    Assert.assertEquals("0x10 0x190x1xp0x190x1ds.ac.nz", domainname);
    Assert.assertEquals(6, NameUtil.labels("r._dns-sd._udp.0x10 0x190x1xp0x190x1ds.ac.nz."));

    domainname = NameUtil.domainname(" https.aklc-guest.govt.nz.");
    Assert.assertEquals("aklc-guest.govt.nz", domainname);
    Assert.assertEquals(4, NameUtil.labels(" https.aklc-guest.govt.nz."));

    domainname = NameUtil.domainname("xxxx.yyyy@lincolnuni.ac.nz.");
    Assert.assertEquals("yyyy@lincolnuni.ac.nz", domainname);
    Assert.assertEquals(4, NameUtil.labels("xxxx.yyyy@lincolnuni.ac.nz."));

    domainname = NameUtil.domainname("_ldap._tcp.dc._msdcs.workgroup.0x1bequ??t?enable.net.nz.");
    Assert.assertEquals("0x1bequ??t?enable.net.nz", domainname);
    Assert
        .assertEquals(8,
            NameUtil.labels("_ldap._tcp.dc._msdcs.workgroup.0x1bequ??t?enable.net.nz."));

    domainname = NameUtil.domainname("#192.168.51.52.palazzodesign.co.nz.");
    Assert.assertEquals("palazzodesign.co.nz", domainname);
    Assert.assertEquals(7, NameUtil.labels("#192.168.51.52.palazzodesign.co.nz."));
  }


  @Test
  public void testPublicSuffixOk() {
    String domainname = NameUtil.domainname("nl.");
    Assert.assertEquals(null, domainname);
    Assert.assertEquals(1, NameUtil.labels("nl."));

    domainname = NameUtil.domainname(".nl.");
    Assert.assertEquals(null, domainname);
    Assert.assertEquals(1, NameUtil.labels(".nl."));

    domainname = NameUtil.domainname("co.nz.");
    Assert.assertEquals(null, domainname);
    Assert.assertEquals(2, NameUtil.labels("co.nz."));

    domainname = NameUtil.domainname("ruanca.blogspot.co.nz.");
    Assert.assertEquals("ruanca.blogspot.co.nz", domainname);
    Assert.assertEquals(4, NameUtil.labels("ruanca.blogspot.co.nz."));
  }

  @Test
  public void testEmptyQnameOk() {
    String domainname = NameUtil.domainname(".");

    Assert.assertEquals(null, domainname);
    Assert.assertEquals(0, NameUtil.labels("."));

    domainname = NameUtil.domainname(null);
    Assert.assertEquals(null, domainname);
    Assert.assertEquals(0, NameUtil.labels(null));

    domainname = NameUtil.domainname("");
    Assert.assertEquals(null, domainname);
    Assert.assertEquals(0, NameUtil.labels(""));
  }


}
