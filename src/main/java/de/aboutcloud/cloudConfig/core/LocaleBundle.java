package de.aboutcloud.cloudConfig.core;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class LocaleBundle {
    private final Map<String, Object> tree;

    private LocaleBundle(Map<String, Object> tree) { this.tree = tree; }

    static LocaleBundle load(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            Yaml yaml = new Yaml();
            Object o = yaml.load(r);
            Map<String, Object> map = (o instanceof Map<?,?> m) ? (Map<String, Object>) m : Map.of();
            return new LocaleBundle(map);
        }
    }

    String get(String key) {
        Object v = dig(tree, key);
        return (v == null) ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Object dig(Map<String, Object> map, String dotted) {
        String[] parts = dotted.split("\\.");
        Object cur = map;
        for (String part : parts) {
            if (!(cur instanceof Map<?,?> m)) return null;
            cur = m.get(part);
            if (cur == null) return null;
        }
        return cur;
    }
}
