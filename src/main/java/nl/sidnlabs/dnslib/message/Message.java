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

import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import nl.sidnlabs.dnslib.message.records.ResourceRecord;
import nl.sidnlabs.dnslib.message.records.ResourceRecordFactory;
import nl.sidnlabs.dnslib.message.records.edns0.OPTResourceRecord;
import nl.sidnlabs.dnslib.message.util.DNSStringUtil;
import nl.sidnlabs.dnslib.message.util.NetworkData;
import nl.sidnlabs.dnslib.types.OpcodeType;
import nl.sidnlabs.dnslib.types.ResourceRecordType;

@Log4j2
@Data
public class Message {

  private boolean partial;
  private boolean allowFail;

  // size of msg in bytes
  private int bytes;
  private Header header;

  private List<Question> questions = new ArrayList<>();
  private List<RRset> answer = new ArrayList<>();
  private List<RRset> authority = new ArrayList<>();
  private List<RRset> additional = new ArrayList<>();

  private OPTResourceRecord pseudo;

  public Message() {}

  public Message(NetworkData data) {
    this(data, false, false);
  }

  /**
   * Decode network bytes into a DNS Message
   * 
   * @param data buffer with network data
   * @param partial do not fully decode the message, only the header, questions and OPT record.
   * @param allowFail if true, do not throw an exception when decoding fails.
   */
  public Message(NetworkData data, boolean partial, boolean allowFail) {
    this.bytes = data.length();
    this.partial = partial;
    this.allowFail = allowFail;
    try {
      decode(data);

      if (log.isTraceEnabled()) {
        String qname = questions.isEmpty() ? "" : questions.get(0).getQName();
        log.trace("Decoded DNS message type: {} and qname: {}", header.getQr(), qname);
      }
    } catch (Exception e) {
      if (!allowFail) {
        // not allowed to fail, rethrow exception
        throw e;
      }
      // failing is allowed, can be case when incomplete dns message is received
      // e.g. in the case of an ICMP payload
    }
  }

  public Header getHeader() {
    return header;
  }

  public Message addQuestion(Question question) {
    this.questions.add(question);
    return this;
  }

  private RRset findRRset(List<RRset> setList, ResourceRecord rr) {
    for (RRset rrset : setList) {
      if (rrset.getOwner().equalsIgnoreCase(rr.getName()) && rrset.getClassz() == rr.getClassz()
          && rrset.getType() == rr.getType()) {
        return rrset;
      }
    }

    return null;
  }

  private RRset createRRset(List<RRset> setList, ResourceRecord rr) {
    RRset rrset = RRset.createAs(rr);
    setList.add(rrset);
    return rrset;
  }

  public void addAnswer(ResourceRecord answer) {
    RRset rrset = findRRset(this.answer, answer);
    if (rrset == null) {
      rrset = createRRset(this.answer, answer);
    } else {
      rrset.add(answer);
    }
  }

  public void addAnswer(RRset rrset) {
    answer.add(rrset);
  }

  public void addAuthority(ResourceRecord authority) {
    RRset rrset = findRRset(this.authority, authority);
    if (rrset == null) {
      rrset = createRRset(this.authority, authority);
    } else {
      rrset.add(authority);
    }
  }

  public void addAuthority(RRset authority) {
    this.authority.add(authority);

  }

  public List<RRset> getAdditional() {
    return additional;
  }

  public void addAdditional(ResourceRecord rr) {
    RRset rrset = findRRset(this.additional, rr);
    if (rrset == null) {
      rrset = createRRset(this.additional, rr);
    } else {
      rrset.add(rr);
    }
  }

  public void addAdditional(RRset additional) {
    if (additional.getType() != ResourceRecordType.OPT) {
      this.additional.add(additional);
    }
  }

  public void decode(NetworkData buffer) {
    header = new Header();
    header.decode(buffer);

    if (header.getOpCode() != OpcodeType.STANDARD) {
      if (log.isDebugEnabled()) {
        log
            .debug("Unsupported OPCODE {}, do not continue to decode messsage past header",
                header.getOpCode());
      }
      return;
    }


    for (int i = 0; i < header.getQdCount(); i++) {
      Question question = decodeQuestion(buffer);
      addQuestion(question);
    }

    for (int i = 0; i < header.getAnCount(); i++) {
      ResourceRecord rr = decodeResourceRecord(buffer);
      if (!partial) {
        addAnswer(rr);
      }
    }

    for (int i = 0; i < header.getNsCount(); i++) {
      ResourceRecord rr = decodeResourceRecord(buffer);
      if (!partial) {
        addAuthority(rr);
      }
    }

    // because in partial decode mode we do want the OPT record the check
    // for the addition records is done in the decodeResourceRecord method
    for (int i = 0; i < header.getArCount(); i++) {
      ResourceRecord rr = decodeResourceRecord(buffer);
      if (rr != null && rr.getType() != ResourceRecordType.OPT) {
        if (!partial) {
          addAdditional(rr);
        }
      } else {
        pseudo = (OPTResourceRecord) (rr);
      }
    }

  }

  private ResourceRecord decodeResourceRecord(NetworkData buffer) {

    /*
     * read ahead to the type bytes to find out what type of RR needs to be created.
     */

    // skip 16bits with name
    buffer.markReaderIndex();
    // read the name to get to the type bytes after the name
    DNSStringUtil.readNameUsingBuffer(buffer);

    // read 16 bits with type
    int type = buffer.readUnsignedChar();

    // go back bits to the start of the RR
    buffer.resetReaderIndex();

    ResourceRecord rr = ResourceRecordFactory.getInstance().createResourceRecord(type);

    if (partial) {
      // only decode the opt record when doing partial decoding
      if (rr.getType() == ResourceRecordType.OPT) {
        rr.decode(buffer, partial);
      }
      return rr;
    }

    // decode the entire rr now
    rr.decode(buffer, partial);
    return rr;
  }


  private Question decodeQuestion(NetworkData buffer) {

    Question question = new Question();

    question.decode(buffer);

    return question;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("\nheader\n");
    builder.append("_______________________________________________\n");
    builder.append("Message [header=" + header + "] ");
    builder.append("\n");

    builder.append("question\n");
    builder.append("_______________________________________________\n");
    for (Question question : questions) {
      builder.append(question.toString());
      builder.append("\n");
    }

    builder.append("answer\n");
    builder.append("_______________________________________________\n");
    for (RRset rrset : answer) {
      builder.append(rrset.toString());
      builder.append("\n");
    }

    builder.append("authority\n");
    builder.append("_______________________________________________\n");
    for (RRset rrset : authority) {
      builder.append(rrset.toString());
      builder.append("\n");
    }

    builder.append("additional\n");
    builder.append("_______________________________________________\n");
    for (RRset rrset : additional) {
      builder.append(rrset.toString());
      builder.append("\n");
    }

    return builder.toString();
  }

  public Object toZone() {
    StringBuilder builder = new StringBuilder();
    builder.append("; header: " + header.toZone() + "\n");

    int maxLength = maxLength();
    builder.append("; answer section:\n");
    for (RRset rrset : answer) {
      builder.append(rrset.toZone(maxLength));
    }

    builder.append("; authority section:\n");
    for (RRset rrset : authority) {
      builder.append(rrset.toZone(maxLength));
    }

    builder.append("; additional section:\n");
    for (RRset rrset : additional) {
      builder.append(rrset.toZone(maxLength));
    }

    return builder.toString();
  }

  public JsonObject toJson() {
    JsonObjectBuilder builder = Json.createObjectBuilder();
    builder.add("header", header.toJSon());

    JsonArrayBuilder questionsBuilder = Json.createArrayBuilder();
    for (Question question : questions) {
      questionsBuilder.add(question.toJSon());
    }

    builder.add("question", questionsBuilder.build());

    JsonArrayBuilder rrBuilder = Json.createArrayBuilder();
    for (RRset rrset : answer) {
      rrBuilder.add(rrset.toJSon());
    }
    builder.add("answer", rrBuilder.build());

    rrBuilder = Json.createArrayBuilder();
    for (RRset rrset : authority) {
      rrBuilder.add(rrset.toJSon());
    }
    builder.add("authority", rrBuilder.build());

    rrBuilder = Json.createArrayBuilder();
    for (RRset rrset : additional) {
      rrBuilder.add(rrset.toJSon());
    }
    builder.add("additional", rrBuilder.build());

    return builder.build();
  }

  public int maxLength() {
    int length = 0;

    for (RRset rrset : answer) {
      if (rrset.getOwner().length() > length) {
        length = rrset.getOwner().length();
      }
    }

    for (RRset rrset : authority) {
      if (rrset.getOwner().length() > length) {
        length = rrset.getOwner().length();
      }
    }

    for (RRset rrset : additional) {
      if (rrset.getOwner().length() > length) {
        length = rrset.getOwner().length();
      }
    }

    return length;
  }

}
