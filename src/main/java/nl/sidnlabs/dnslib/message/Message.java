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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import nl.sidnlabs.dnslib.message.records.ResourceRecord;
import nl.sidnlabs.dnslib.message.records.ResourceRecordFactory;
import nl.sidnlabs.dnslib.message.records.edns0.OPTResourceRecord;
import nl.sidnlabs.dnslib.message.util.DNSStringUtil;
import nl.sidnlabs.dnslib.message.util.NetworkData;
import nl.sidnlabs.dnslib.types.OpcodeType;
import nl.sidnlabs.dnslib.types.ResourceRecordClass;
import nl.sidnlabs.dnslib.types.ResourceRecordType;

@Log4j2
@Getter
@Setter
public class Message {

  private boolean partial;
  private boolean allowFail;

  // size of msg in bytes
  private int bytes;
  private Header header;

  // Lazy initialization for better performance
  private List<Question> questions;
  private List<RRset> answer;
  private List<RRset> authority;
  private List<RRset> additional;

  // Cache for O(1) RRset lookup instead of O(n) linear search
  private Map<RRsetKey, RRset> answerMap;
  private Map<RRsetKey, RRset> authorityMap;
  private Map<RRsetKey, RRset> additionalMap;

  private OPTResourceRecord pseudo;

  // Inner class for composite key in RRset lookup
  private static class RRsetKey {
    final String name;
    final ResourceRecordClass classz;
    final ResourceRecordType type;
    final int hashCode;

    RRsetKey(String name, ResourceRecordClass classz, ResourceRecordType type) {
      this.name = name.toLowerCase(); // case-insensitive
      this.classz = classz;
      this.type = type;
      // Pre-compute hash
      this.hashCode = 31 * (31 * this.name.hashCode() + classz.hashCode()) + type.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof RRsetKey)) return false;
      RRsetKey key = (RRsetKey) o;
      return classz == key.classz && type == key.type && name.equals(key.name);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }

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

  public List<Question> getQuestions() {
    return questions == null ? Collections.emptyList() : questions;
  }

  public List<RRset> getAnswer() {
    return answer == null ? Collections.emptyList() : answer;
  }

  public List<RRset> getAuthority() {
    return authority == null ? Collections.emptyList() : authority;
  }

  public Message addQuestion(Question question) {
    ensureQuestionsInitialized(1);
    this.questions.add(question);
    return this;
  }

  private RRsetKey createKey(ResourceRecord rr) {
    return new RRsetKey(rr.getName(), rr.getClassz(), rr.getType());
  }

  private void ensureAnswerInitialized(int capacity) {
    if (answer == null) {
      answer = new ArrayList<>(capacity);
      answerMap = new HashMap<>(capacity);
    }
  }

  private void ensureAuthorityInitialized(int capacity) {
    if (authority == null) {
      authority = new ArrayList<>(capacity);
      authorityMap = new HashMap<>(capacity);
    }
  }

  private void ensureAdditionalInitialized(int capacity) {
    if (additional == null) {
      additional = new ArrayList<>(capacity);
      additionalMap = new HashMap<>(capacity);
    }
  }

  private void ensureQuestionsInitialized(int capacity) {
    if (questions == null) {
      questions = new ArrayList<>(capacity);
    }
  }

  public void addAnswer(ResourceRecord answer) {
    ensureAnswerInitialized(4);
    RRsetKey key = createKey(answer);
    RRset rrset = answerMap.get(key);
    if (rrset == null) {
      rrset = RRset.createAs(answer);
      this.answer.add(rrset);
      answerMap.put(key, rrset);
    } else {
      rrset.add(answer);
    }
  }

  public void addAnswer(RRset rrset) {
    ensureAnswerInitialized(4);
    answer.add(rrset);
  }

  public void addAuthority(ResourceRecord authority) {
    ensureAuthorityInitialized(4);
    RRsetKey key = createKey(authority);
    RRset rrset = authorityMap.get(key);
    if (rrset == null) {
      rrset = RRset.createAs(authority);
      this.authority.add(rrset);
      authorityMap.put(key, rrset);
    } else {
      rrset.add(authority);
    }
  }

  public void addAuthority(RRset authority) {
    ensureAuthorityInitialized(4);
    this.authority.add(authority);
  }

  public List<RRset> getAdditional() {
    return additional == null ? Collections.emptyList() : additional;
  }

  public void addAdditional(ResourceRecord rr) {
    ensureAdditionalInitialized(4);
    RRsetKey key = createKey(rr);
    RRset rrset = additionalMap.get(key);
    if (rrset == null) {
      rrset = RRset.createAs(rr);
      this.additional.add(rrset);
      additionalMap.put(key, rrset);
    } else {
      rrset.add(rr);
    }
  }

  public void addAdditional(RRset additional) {
    if (additional.getType() != ResourceRecordType.OPT) {
      ensureAdditionalInitialized(4);
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

    // Cache header counts to avoid repeated method calls
    final int qdCount = header.getQdCount();
    final int anCount = header.getAnCount();
    final int nsCount = header.getNsCount();
    final int arCount = header.getArCount();

    // Pre-initialize lists with known capacities
    if (qdCount > 0) {
      ensureQuestionsInitialized(qdCount);
      for (int i = 0; i < qdCount; i++) {
        Question question = decodeQuestion(buffer);
        questions.add(question);
      }
    }

    if (!partial && anCount > 0) {
      ensureAnswerInitialized(anCount);
      for (int i = 0; i < anCount; i++) {
        ResourceRecord rr = decodeResourceRecord(buffer, false);
        addAnswer(rr);
      }
    } else if (partial) {
      // Skip answer section in partial mode
      for (int i = 0; i < nsCount; i++) {
        decodeResourceRecord(buffer, true);
      }
    }

    if (!partial && nsCount > 0) {
      ensureAuthorityInitialized(nsCount);
      for (int i = 0; i < nsCount; i++) {
        ResourceRecord rr = decodeResourceRecord(buffer, false);
        addAuthority(rr);
      }
    } else if (partial) {
      // Skip authority section in partial mode
      for (int i = 0; i < arCount; i++) {
        decodeResourceRecord(buffer, true);
      }
    }

    // In partial mode, we still want the OPT record
    if (arCount > 0) {
      if (!partial) {
        ensureAdditionalInitialized(arCount);
      }
      for (int i = 0; i < arCount; i++) {
        ResourceRecord rr = decodeResourceRecord(buffer, partial);
        if (rr != null) {
          if (rr.getType() == ResourceRecordType.OPT) {
            pseudo = (OPTResourceRecord) rr;
          } else if (!partial) {
            addAdditional(rr);
          }
        }
      }
    }
  }

  private ResourceRecord decodeResourceRecord(NetworkData buffer, boolean partialDecode) {

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

    if (partialDecode) {
      // only decode the opt record when doing partial decoding
      if (rr.getType() == ResourceRecordType.OPT) {
        rr.decode(buffer, true);
        return rr;
      }
      // For other types in partial mode, just skip over them
      return null;
    }

    // decode the entire rr now
    rr.decode(buffer, false);
    return rr;
  }


  private Question decodeQuestion(NetworkData buffer) {

    Question question = new Question();

    question.decode(buffer);

    return question;
  }

  @Override
  public String toString() {
    // Pre-size StringBuilder to reduce allocations
    StringBuilder builder = new StringBuilder(512);
    builder.append("\nheader\n");
    builder.append("_______________________________________________\n");
    builder.append("Message [header=").append(header).append("] ");
    builder.append("\n");

    builder.append("question\n");
    builder.append("_______________________________________________\n");
    if (questions != null) {
      for (Question question : questions) {
        builder.append(question.toString());
        builder.append("\n");
      }
    }

    builder.append("answer\n");
    builder.append("_______________________________________________\n");
    if (answer != null) {
      for (RRset rrset : answer) {
        builder.append(rrset.toString());
        builder.append("\n");
      }
    }

    builder.append("authority\n");
    builder.append("_______________________________________________\n");
    if (authority != null) {
      for (RRset rrset : authority) {
        builder.append(rrset.toString());
        builder.append("\n");
      }
    }

    builder.append("additional\n");
    builder.append("_______________________________________________\n");
    if (additional != null) {
      for (RRset rrset : additional) {
        builder.append(rrset.toString());
        builder.append("\n");
      }
    }

    return builder.toString();
  }

  public Object toZone() {
    StringBuilder builder = new StringBuilder(512);
    builder.append("; header: ").append(header.toZone()).append("\n");

    int maxLength = maxLength();
    builder.append("; answer section:\n");
    if (answer != null) {
      for (RRset rrset : answer) {
        builder.append(rrset.toZone(maxLength));
      }
    }

    builder.append("; authority section:\n");
    if (authority != null) {
      for (RRset rrset : authority) {
        builder.append(rrset.toZone(maxLength));
      }
    }

    builder.append("; additional section:\n");
    if (additional != null) {
      for (RRset rrset : additional) {
        builder.append(rrset.toZone(maxLength));
      }
    }

    return builder.toString();
  }

  public JsonObject toJson() {
    JsonObjectBuilder builder = Json.createObjectBuilder();
    builder.add("header", header.toJSon());

    JsonArrayBuilder questionsBuilder = Json.createArrayBuilder();
    if (questions != null) {
      for (Question question : questions) {
        questionsBuilder.add(question.toJSon());
      }
    }
    builder.add("question", questionsBuilder.build());

    JsonArrayBuilder rrBuilder = Json.createArrayBuilder();
    if (answer != null) {
      for (RRset rrset : answer) {
        rrBuilder.add(rrset.toJSon());
      }
    }
    builder.add("answer", rrBuilder.build());

    rrBuilder = Json.createArrayBuilder();
    if (authority != null) {
      for (RRset rrset : authority) {
        rrBuilder.add(rrset.toJSon());
      }
    }
    builder.add("authority", rrBuilder.build());

    rrBuilder = Json.createArrayBuilder();
    if (additional != null) {
      for (RRset rrset : additional) {
        rrBuilder.add(rrset.toJSon());
      }
    }
    builder.add("additional", rrBuilder.build());

    return builder.build();
  }

  public int maxLength() {
    int length = 0;

    if (answer != null) {
      for (RRset rrset : answer) {
        int ownerLen = rrset.getOwner().length();
        if (ownerLen > length) {
          length = ownerLen;
        }
      }
    }

    if (authority != null) {
      for (RRset rrset : authority) {
        int ownerLen = rrset.getOwner().length();
        if (ownerLen > length) {
          length = ownerLen;
        }
      }
    }

    if (additional != null) {
      for (RRset rrset : additional) {
        int ownerLen = rrset.getOwner().length();
        if (ownerLen > length) {
          length = ownerLen;
        }
      }
    }

    return length;
  }

}
