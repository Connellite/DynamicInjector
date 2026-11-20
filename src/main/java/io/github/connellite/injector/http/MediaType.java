/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.connellite.injector.http;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Media type parser adapted from Apache Tomcat 9.0 {@code org.apache.tomcat.util.http.parser.MediaType}
 * and the token reader it uses in {@code HttpParser}. Only the charset parameter is retained.
 *
 * <p>Sources:
 * <a href="https://github.com/apache/tomcat/blob/9.0.x/java/org/apache/tomcat/util/http/parser/MediaType.java">MediaType</a>,
 * <a href="https://github.com/apache/tomcat/blob/9.0.x/java/org/apache/tomcat/util/http/parser/HttpParser.java">HttpParser</a>
 * (Apache License 2.0).
 */
public final class MediaType {

    private static final int ARRAY_SIZE = 256;
    private static final boolean[] IS_TOKEN = new boolean[ARRAY_SIZE];

    static {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            boolean separator = i == '(' || i == ')' || i == '<' || i == '>' || i == '@' || i == ',' || i == ';'
                    || i == ':' || i == '\\' || i == '"' || i == '/' || i == '[' || i == ']' || i == '?'
                    || i == '=' || i == '{' || i == '}' || i == ' ' || i == '\t';
            boolean control = i < 32 || i == 127;
            // Token: anything 0-127 that is not a control and not a separator.
            if (!control && !separator && i < 128) {
                IS_TOKEN[i] = true;
            }
        }
    }

    private final String charset;

    private MediaType(String charset) {
        this.charset = charset;
    }

    /**
     * Charset parameter, still quoted when the header used a quoted string that could not be unquoted.
     * {@code null} when the header has no charset parameter.
     */
    public String getCharset() {
        return charset;
    }

    /**
     * @return the parsed type, or {@code null} when the type or subtype is missing
     */
    public static MediaType parse(String contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            return parseMediaType(new StringReader(contentType));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Parses a media type. Returns {@code null} when the required type or subtype is missing.
     * A dangling {@code ;} is accepted.
     */
    static MediaType parseMediaType(Reader input) throws IOException {
        String type = readToken(input);
        if (type == null || type.isEmpty()) {
            return null;
        }

        if (skipConstant(input, "/") == SkipResult.NOT_FOUND) {
            return null;
        }

        String subtype = readToken(input);
        if (subtype == null || subtype.isEmpty()) {
            return null;
        }

        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();

        SkipResult lookForSemiColon = skipConstant(input, ";");
        if (lookForSemiColon == SkipResult.NOT_FOUND) {
            return null;
        }
        while (lookForSemiColon == SkipResult.FOUND) {
            String attribute = readToken(input);

            String value;
            if (skipConstant(input, "=") == SkipResult.FOUND) {
                value = readTokenOrQuotedString(input, true);
            } else {
                value = "";
            }

            if (attribute != null && !attribute.isEmpty()) {
                parameters.put(attribute.toLowerCase(Locale.ENGLISH), value);
            }

            lookForSemiColon = skipConstant(input, ";");
            if (lookForSemiColon == SkipResult.NOT_FOUND) {
                return null;
            }
        }

        String cs = parameters.get("charset");
        if (cs != null && !cs.isEmpty() && cs.charAt(0) == '"') {
            cs = unquote(cs);
        }
        return new MediaType(cs);
    }

    /**
     * Removes surrounding quotes from a string, handling escaped characters.
     *
     * @return the unquoted string, or {@code null} if the input is invalid
     */
    static String unquote(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }

        int start;
        int end;
        if (input.charAt(0) == '"') {
            start = 1;
            end = input.length() - 1;
        } else {
            start = 0;
            end = input.length();
        }

        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) {
            char c = input.charAt(i);
            if (c == '\\') {
                i++;
                if (i == end) {
                    return null;
                }
                result.append(input.charAt(i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static boolean isToken(int c) {
        try {
            return IS_TOKEN[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }

    private static int skipWhitespace(Reader input) throws IOException {
        input.mark(1);
        int c = input.read();

        while (c == 32 || c == 9) {
            input.mark(1);
            c = input.read();
        }

        input.reset();
        return c;
    }

    private static SkipResult skipConstant(Reader input, String constant) throws IOException {
        int len = constant.length();

        skipWhitespace(input);
        input.mark(len);
        int c = input.read();

        for (int i = 0; i < len; i++) {
            if (i == 0 && c == -1) {
                return SkipResult.EOF;
            }
            if (c != constant.charAt(i)) {
                input.reset();
                return SkipResult.NOT_FOUND;
            }
            if (i != (len - 1)) {
                c = input.read();
            }
        }
        return SkipResult.FOUND;
    }

    /**
     * @return the token if one was found, the empty string if no data was available to read,
     * or {@code null} if data other than a token was found
     */
    private static String readToken(Reader input) throws IOException {
        StringBuilder result = new StringBuilder();

        skipWhitespace(input);
        input.mark(1);
        int c = input.read();

        while (c != -1 && isToken(c)) {
            result.append((char) c);
            input.mark(1);
            c = input.read();
        }
        input.reset();

        if (c != -1 && result.isEmpty()) {
            return null;
        }
        return result.toString();
    }

    /**
     * @return the quoted string if one was found, or {@code null} if the quoted string was not terminated
     */
    private static String readQuotedString(Reader input, boolean returnQuoted) throws IOException {
        skipWhitespace(input);
        int c = input.read();

        if (c != '"') {
            return null;
        }

        StringBuilder result = new StringBuilder();
        if (returnQuoted) {
            result.append('"');
        }
        c = input.read();

        while (c != '"') {
            if (c == -1) {
                return null;
            } else if (c == '\\') {
                c = input.read();
                if (c == -1) {
                    return null;
                }
                if (returnQuoted) {
                    result.append('\\');
                }
                result.append((char) c);
            } else {
                result.append((char) c);
            }
            c = input.read();
        }
        if (returnQuoted) {
            result.append('"');
        }
        return result.toString();
    }

    private static String readTokenOrQuotedString(Reader input, boolean returnQuoted) throws IOException {
        int c = skipWhitespace(input);
        if (c == '"') {
            return readQuotedString(input, returnQuoted);
        }
        return readToken(input);
    }

    private enum SkipResult {
        EOF,
        FOUND,
        NOT_FOUND
    }
}
