package de.aboutcloud.cloudConfig.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LocaleUtil {
    private LocaleUtil() {}

    /** Accepts "en", "en-EN", "de-DE", "pt-BR" etc. */
    public static Locale parseLocale(String tag) {
        if (tag == null || tag.isBlank()) return Locale.ENGLISH;
        String norm = tag.replace('_','-');
        String[] p = norm.split("-", -1);
        return switch (p.length) {
            case 1 -> Locale.forLanguageTag(p[0]);
            case 2 -> Locale.forLanguageTag(p[0] + "-" + p[1]);
            case 3 -> Locale.forLanguageTag(p[0] + "-" + p[1] + "-" + p[2]);
            default -> Locale.forLanguageTag(norm);
        };
    }

    public static List<Locale> fallbackChain(Locale requested, Locale def) {
        List<Locale> chain = new ArrayList<>();
        if (requested != null) {
            chain.add(requested);
            if (!requested.getCountry().isEmpty()) {
                chain.add(Locale.forLanguageTag(requested.getLanguage()));
            }
        }
        if (def != null) chain.add(def);
        chain.add(Locale.ENGLISH);
        chain.add(Locale.ROOT);
        return chain;
    }
}
