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
  public void tldIs2ndLevelTest() {
    Assert.assertEquals("sidn.nl", NameUtil.getDomain("test.www.sidn.nl.").getName());
    Assert.assertEquals("sidn.nl", NameUtil.getDomain("www.sidn.nl.").getName());
    Assert.assertEquals("sidn.nl", NameUtil.getDomain("sidn.nl.").getName());
    Assert.assertEquals("sidn.nl", NameUtil.getDomain("sidn.nl.").getName());
    Assert.assertEquals("nl.", NameUtil.getDomain("nl.").getName());
    Assert.assertEquals(".nl.", NameUtil.getDomain(".nl.").getName());
    Assert.assertEquals("nl", NameUtil.getDomain("nl").getName());
    Assert.assertEquals(".", NameUtil.getDomain(".").getName());
    Assert.assertEquals(null, NameUtil.getDomain("").getName());
    Assert.assertEquals(null, NameUtil.getDomain(null).getName());
    Assert.assertEquals("test .sidn.nl.", NameUtil.getDomain("test .sidn.nl.").getName());
  }

  @Test
  public void emailAddress2ndLevelTest() {
    // email address should not happen, but sometimes we do see these in the qname.
    Assert
        .assertEquals("email.test@example.com.",
            NameUtil.getDomain("email.test@example.com.").getName());
  }

  @Test
  public void domainWith2ndLevelAndTldSuffix() {

    // test fqdn (including final dot)
    Domaininfo info = NameUtil.getDomain("name.example.co.uk");

    Assert.assertNotNull(info);
    Assert.assertNotNull(info.getName());
    Assert.assertEquals("example.co.uk", info.getName());
    Assert.assertTrue(info.getLabels() == 4);

  }
}
