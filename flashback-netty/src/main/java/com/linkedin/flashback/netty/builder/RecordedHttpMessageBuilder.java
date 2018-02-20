/*
 * Copyright (c) LinkedIn Corporation. All rights reserved. Licensed under the BSD-2 Clause license.
 * See LICENSE in the project root for license information.
 */

package com.linkedin.flashback.netty.builder;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.linkedin.flashback.factory.RecordedHttpBodyFactory;
import com.linkedin.flashback.serializable.RecordedHttpBody;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import java.io.IOException;
import java.io.InputStream;


/**
 * Abstract builder for {@link com.linkedin.flashback.serializable.RecordedHttpMessage}
 * It plays adapter role between netty http message and total recall serializable http message.
 *
 * @author shfeng
 */
public abstract class RecordedHttpMessageBuilder {
  protected HttpMessage _nettyHttpMessage;
  private final Multimap<String, String> _headers = LinkedHashMultimap.create();
  protected final CompositeByteBuf _bodyByteBuf = Unpooled.compositeBuffer();

  private static final String DEFAULT_CONTENT_ENCODING = "identity";
  private static final String DEFAULT_CONTENT_TYPE = MediaType.OCTET_STREAM.toString();
  private static final String DEFAULT_CHARSET = Charsets.UTF_8.toString();

  protected RecordedHttpMessageBuilder(HttpMessage nettyHttpMessage) {
    _nettyHttpMessage = nettyHttpMessage;
    addHeaders(nettyHttpMessage);
  }

  /**
   *  Append http content to temporary byte buffer.
   *  @param chunk netty http content chunk
   * */
  public void appendHttpContent(HttpContent chunk)
      throws IOException {
    _bodyByteBuf.addComponent(Unpooled.copiedBuffer(chunk.content()));
    _bodyByteBuf.writerIndex(_bodyByteBuf.writerIndex() + chunk.content().readableBytes());
  }

  /**
   *  Extract headers from {@link io.netty.handler.codec.http.HttpMessage} and put in temporary
   *  headers. Headers are stored as multi-map because given the same key, it can have more than
   *  one values.
   *  @param httpMessage netty http message
   * */
  public void addHeaders(HttpMessage httpMessage) {
    if (httpMessage.headers() == null) {
      return;
    }
    for (String name : httpMessage.headers().names()) {
      for (String value : httpMessage.headers().getAll(name)) {
        if (!_headers.containsEntry(name, value)) {
          _headers.put(name, value);
        }
      }
    }
  }

  /**
   * Convert temporary headers to permanent serializable headers.
   * */
  Multimap<String, String> getHeaders() {
    return _headers;
  }

  /**
   * Get content type from headers
   *
   * */
  protected String getContentType() {
    // Content_Type cannot have multiple, commas-separated values, so this is safe.
    String header = _headers.get(HttpHeaders.CONTENT_TYPE).iterator().next();
    if (Strings.isNullOrEmpty(header)) {
      return DEFAULT_CONTENT_TYPE;
    } else {
      return MediaType.parse(header).withoutParameters().toString();
    }
  }

  /**
   * Get content encoding from headers
   *
   * */
  protected String getContentEncoding() {
    // Content_Encoding cannot have multiple, commas-separated values, so this is safe.
    String header = _headers.get(HttpHeaders.CONTENT_ENCODING).iterator().next();
    if (Strings.isNullOrEmpty(header)) {
      return DEFAULT_CONTENT_ENCODING;
    } else {
      return header;
    }
  }

  /**
   * Get char set from headers
   *
   * */
  protected String getCharset() {
    // Content_Type cannot have multiple, commas-separated values, so this is safe.
    String header = _headers.get(HttpHeaders.CONTENT_TYPE).iterator().next();
    if (Strings.isNullOrEmpty(header)) {
      return DEFAULT_CHARSET;
    } else {
      return MediaType.parse(header).charset().or(Charsets.UTF_8).toString();
    }
  }

  /**
   * Build serializable {@Link RecordedHttpBody} using temporary byte buffers.
   * Based on Charset, we will build concrete Http body either binary or characters.
   * TODO: throw customized exception if failed to create http body
   *
   * */
  protected RecordedHttpBody getBody() {
    try {
      InputStream byteBufInputStream = new ByteBufInputStream(_bodyByteBuf);
      return RecordedHttpBodyFactory.create(getContentType(), getContentEncoding(), byteBufInputStream, getCharset());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create Httpbody");
    }
  }
}
