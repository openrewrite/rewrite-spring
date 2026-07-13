/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring;

import lombok.Value;
import org.openrewrite.properties.tree.Properties;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a parsed Spring Boot {@code .properties} file into equivalent YAML text,
 * preserving comments and entry order, and quoting scalars so the values Spring binds
 * are unchanged.
 */
final class PropertiesToYamlConverter {

    private static final String YAML_SPECIAL_CHARS = ":#[]{}|>&*!'\"%@`";
    private static final Resolver YAML_RESOLVER = new Resolver();
    // Splits a key on its first index: base, index, rest (e.g. my.servers[0].host → my.servers, 0, .host)
    private static final Pattern INDEXED_KEY_PATTERN = Pattern.compile("(.+?)\\[(\\d+)](.*)");

    private PropertiesToYamlConverter() {
    }

    /**
     * An entry with key and value already unescaped, plus the comment lines that preceded it.
     */
    @Value
    private static class KeyValue {
        String key;
        String value;
        List<String> comments;
    }

    static String convert(Properties.File file) {
        List<KeyValue> entries = new ArrayList<>();
        List<String> pendingComments = new ArrayList<>();
        for (Properties.Content content : file.getContent()) {
            if (content instanceof Properties.Comment) {
                pendingComments.add(((Properties.Comment) content).getMessage());
            } else if (content instanceof Properties.Entry) {
                entries.add(toKeyValue((Properties.Entry) content, pendingComments));
                pendingComments = new ArrayList<>();
            }
        }
        List<String> lines = renderMap(buildTree(entries), 0);
        for (String comment : pendingComments) {
            lines.add("#" + comment);
        }
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    /**
     * Round-trips the raw entry through {@link java.util.Properties#load(Reader)} so key and
     * value are unescaped exactly as Spring would see them at runtime.
     */
    private static KeyValue toKeyValue(Properties.Entry entry, List<String> comments) {
        java.util.Properties loaded = new java.util.Properties();
        try {
            loaded.load(new StringReader(entry.getKey() + "=" + entry.getValue().getText()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String key = loaded.stringPropertyNames().iterator().next();
        return new KeyValue(key, loaded.getProperty(key), comments);
    }

    /**
     * Builds a nested structure of mappings (in first-seen key order), sequences and scalars.
     * A key that would nest inside another key's value (e.g. {@code a.b.c=2} alongside
     * {@code a.b=1}) keeps its conflicting remainder as a literal dotted key, which Spring's
     * relaxed binding reads the same way.
     */
    private static Map<String, Object> buildTree(List<KeyValue> entries) {
        Map<String, NavigableMap<Integer, List<KeyValue>>> sequences = groupSequences(entries);
        Set<String> terminalKeys = new HashSet<>();
        for (KeyValue entry : entries) {
            Matcher indexed = INDEXED_KEY_PATTERN.matcher(entry.getKey());
            terminalKeys.add(indexed.matches() && sequences.containsKey(indexed.group(1)) ?
                    indexed.group(1) : entry.getKey());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        Set<String> insertedSequences = new HashSet<>();
        for (KeyValue entry : entries) {
            Matcher indexed = INDEXED_KEY_PATTERN.matcher(entry.getKey());
            if (indexed.matches() && sequences.containsKey(indexed.group(1))) {
                if (insertedSequences.add(indexed.group(1))) {
                    insert(root, indexed.group(1), buildSequence(sequences.get(indexed.group(1))), terminalKeys);
                }
                continue;
            }
            insert(root, entry.getKey(), entry, terminalKeys);
        }
        return root;
    }

    /**
     * A sequence item is either a scalar (single entry with an empty key) or a nested mapping.
     */
    private static List<Object> buildSequence(NavigableMap<Integer, List<KeyValue>> items) {
        List<Object> sequence = new ArrayList<>();
        for (List<KeyValue> item : items.values()) {
            sequence.add(item.get(0).getKey().isEmpty() ? item.get(0) : buildTree(item));
        }
        return sequence;
    }

    /**
     * Never creates a mapping at a path that is itself a terminal key: from that point on the
     * remainder stays a literal dotted key, so a scalar (or sequence) and its dotted descendants
     * can coexist without duplicate YAML keys, regardless of entry order.
     */
    @SuppressWarnings("unchecked")
    private static void insert(Map<String, Object> root, String key, Object value, Set<String> terminalKeys) {
        Map<String, Object> current = root;
        String[] segments = key.split("\\.");
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            path.append(i == 0 ? "" : ".").append(segments[i]);
            if (terminalKeys.contains(path.toString())) {
                current.put(String.join(".", Arrays.asList(segments).subList(i, segments.length)), value);
                return;
            }
            current = (Map<String, Object>) current.computeIfAbsent(segments[i], k -> new LinkedHashMap<>());
        }
        current.put(segments[segments.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private static List<String> renderMap(Map<String, Object> map, int depth) {
        List<String> lines = new ArrayList<>();
        String indent = indent(depth);
        map.forEach((key, value) -> {
            if (value instanceof KeyValue) {
                KeyValue kv = (KeyValue) value;
                addComments(lines, indent, kv);
                lines.add(indent + key + ": " + quoteYamlValue(kv.getValue()));
            } else if (value instanceof Map) {
                lines.add(indent + key + ":");
                lines.addAll(renderMap((Map<String, Object>) value, depth + 1));
            } else {
                lines.add(indent + key + ":");
                lines.addAll(renderSequence((List<Object>) value, depth + 1));
            }
        });
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<String> renderSequence(List<Object> sequence, int depth) {
        List<String> lines = new ArrayList<>();
        String indent = indent(depth);
        for (Object item : sequence) {
            if (item instanceof KeyValue) {
                KeyValue kv = (KeyValue) item;
                addComments(lines, indent, kv);
                lines.add(indent + "- " + quoteYamlValue(kv.getValue()));
            } else {
                // Hang the mapping's first line off the dash, hoisting any comments above it,
                // and align the remaining lines with the first
                List<String> itemLines = renderMap((Map<String, Object>) item, 0);
                int first = 0;
                while (itemLines.get(first).startsWith("#")) {
                    lines.add(indent + itemLines.get(first++));
                }
                lines.add(indent + "- " + itemLines.get(first));
                for (int i = first + 1; i < itemLines.size(); i++) {
                    lines.add(indent + "  " + itemLines.get(i));
                }
            }
        }
        return lines;
    }

    private static void addComments(List<String> lines, String indent, KeyValue kv) {
        for (String comment : kv.getComments()) {
            lines.add(indent + "#" + comment);
        }
    }

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    /**
     * Groups indexed keys by the base key before their first index (e.g. {@code my.list[0]} and
     * {@code my.servers[0].host} group under {@code my.list} / {@code my.servers}), keeping only
     * groups that can be faithfully represented as a YAML sequence: indices must be exactly
     * {@code 0..n-1}, each index must be either a single scalar or a set of object keys (not both),
     * and the base key must not also be used as a plain key. Directly nested indices
     * ({@code a[0][1]}) are not converted.
     */
    private static Map<String, NavigableMap<Integer, List<KeyValue>>> groupSequences(List<KeyValue> entries) {
        Map<String, NavigableMap<Integer, List<KeyValue>>> sequences = new LinkedHashMap<>();
        Set<String> invalid = new HashSet<>();
        Set<String> plainKeys = new HashSet<>();
        for (KeyValue entry : entries) {
            Matcher indexed = INDEXED_KEY_PATTERN.matcher(entry.getKey());
            if (!indexed.matches()) {
                plainKeys.add(entry.getKey());
                continue;
            }
            String base = indexed.group(1);
            int index = Integer.parseInt(indexed.group(2));
            String rest = indexed.group(3);
            if (rest.startsWith("[")) {
                invalid.add(base);
                continue;
            }
            String itemKey = rest.startsWith(".") ? rest.substring(1) : rest;
            sequences.computeIfAbsent(base, k -> new TreeMap<>())
                    .computeIfAbsent(index, k -> new ArrayList<>())
                    .add(new KeyValue(itemKey, entry.getValue(), entry.getComments()));
        }
        sequences.entrySet().removeIf(e -> invalid.contains(e.getKey()) ||
                plainKeys.contains(e.getKey()) ||
                e.getValue().firstKey() != 0 ||
                e.getValue().lastKey() != e.getValue().size() - 1 ||
                e.getValue().values().stream().anyMatch(PropertiesToYamlConverter::isInvalidSequenceItem));
        return sequences;
    }

    /**
     * A sequence item must be either exactly one scalar (empty item key) or one or more object keys.
     */
    private static boolean isInvalidSequenceItem(List<KeyValue> item) {
        boolean scalar = item.get(0).getKey().isEmpty();
        if (scalar) {
            return item.size() > 1;
        }
        return item.stream().anyMatch(kv -> kv.getKey().isEmpty());
    }

    /**
     * Quotes a scalar when leaving it plain would change its meaning: YAML special or control
     * characters, leading/trailing whitespace, block indicators, or re-typed values.
     */
    private static String quoteYamlValue(String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        boolean needsQuoting = false;
        for (char c : value.toCharArray()) {
            if (c < ' ' || YAML_SPECIAL_CHARS.indexOf(c) >= 0) {
                needsQuoting = true;
                break;
            }
        }
        needsQuoting = needsQuoting ||
                Character.isWhitespace(value.charAt(0)) ||
                Character.isWhitespace(value.charAt(value.length() - 1)) ||
                value.startsWith("- ") || "-".equals(value) ||
                value.startsWith("? ") || "?".equals(value) ||
                typeChangesWhenPlain(value);
        if (!needsQuoting) {
            return value;
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\f", "\\f");
        return "\"" + escaped + "\"";
    }

    /**
     * True when YAML 1.1 resolves the plain scalar to a non-string type whose rendering differs
     * from the original text (e.g. {@code on} → {@code true}, {@code 0x1A} → {@code 26},
     * {@code 2001-12-14} → a timestamp), which would change the value Spring binds.
     * Values that round-trip textually (e.g. {@code 8080}, {@code true}, {@code 1.5}) stay plain.
     */
    private static boolean typeChangesWhenPlain(String value) {
        if (YAML_RESOLVER.resolve(NodeId.scalar, value, true) == Tag.STR) {
            return false;
        }
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(value);
        return loaded == null || !value.equals(loaded.toString());
    }
}
