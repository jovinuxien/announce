package org.linlinjava.announce.cqrs.command;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Subject conventions: an aggregate instance lives under {@code /<aggregate>/<id>}, a child entity under
 * {@code /<aggregate>/<id>/<child>/<childId>}. Segments are URL-safe (letters, digits, {@code - _ . ~}).
 *
 * <p>Event handling groups fetch a whole aggregate type with {@code fetch.subject=/<aggregate>} and
 * {@code fetch.recursive=true}.
 */
public final class Subjects {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");

    private Subjects() {
    }

    /** {@code of("announce", "42")} gives {@code /announce/42}. */
    public static String of(String aggregate, Object id) {
        return "/" + segment(aggregate, "aggregate") + "/" + segment(String.valueOf(id), "id");
    }

    /** {@code child("/announce/42", "variant", 7)} gives {@code /announce/42/variant/7}. */
    public static String child(String subject, String child, Object childId) {
        return requireSubject(subject) + "/" + segment(child, "child") + "/" + segment(String.valueOf(childId), "childId");
    }

    /** The subject of the aggregate type: {@code typeOf("announce")} gives {@code /announce}. */
    public static String typeOf(String aggregate) {
        return "/" + segment(aggregate, "aggregate");
    }

    /** The last segment of the subject: {@code idOf("/announce/42")} gives {@code 42}. */
    public static String idOf(String subject) {
        String s = requireSubject(subject);
        return s.substring(s.lastIndexOf('/') + 1);
    }

    /** The first segment of the subject: {@code aggregateOf("/announce/42/variant/7")} gives {@code announce}. */
    public static String aggregateOf(String subject) {
        String s = requireSubject(subject).substring(1);
        int slash = s.indexOf('/');
        return slash < 0 ? s : s.substring(0, slash);
    }

    /** The aggregate root subject: {@code rootOf("/announce/42/variant/7")} gives {@code /announce/42}. */
    public static String rootOf(String subject) {
        String[] parts = requireSubject(subject).substring(1).split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Subject has no id segment: " + subject);
        }
        return "/" + parts[0] + "/" + parts[1];
    }

    public static boolean isValid(String subject) {
        if (subject == null || !subject.startsWith("/") || subject.length() < 2 || subject.endsWith("/")) {
            return false;
        }
        for (String part : subject.substring(1).split("/", -1)) {
            if (!SEGMENT.matcher(part).matches()) {
                return false;
            }
        }
        return true;
    }

    public static String requireSubject(String subject) {
        if (!isValid(subject)) {
            throw new IllegalArgumentException("Invalid subject: " + subject);
        }
        return subject;
    }

    private static String segment(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid subject " + what + ": '" + value + "'");
        }
        return value;
    }
}
