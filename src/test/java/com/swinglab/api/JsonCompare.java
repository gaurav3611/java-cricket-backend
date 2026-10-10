package com.swinglab.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Deep comparison of parsed JSON, used to check real answers against recorded or documented ones. */
final class JsonCompare {

    static final double NUMBER_TOLERANCE = 1e-9;

    private JsonCompare() {
    }

    /**
     * Returns one line per difference, or an empty list when the values match.
     * Numbers must agree within {@link #NUMBER_TOLERANCE}. A volatile key must be present with
     * the same JSON type, but its value may differ: ids and timestamps change on every run.
     */
    static List<String> diff(Object expected, Object actual, Set<String> volatileKeys) {
        List<String> diffs = new ArrayList<>();
        compare("$", expected, actual, volatileKeys, diffs);
        return diffs;
    }

    private static void compare(String path, Object expected, Object actual, Set<String> volatileKeys, List<String> diffs) {
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            if (!expectedMap.keySet().equals(actualMap.keySet())) {
                diffs.add(path + ": keys expected " + new TreeSet<>(expectedMap.keySet())
                        + " but were " + new TreeSet<>(actualMap.keySet()));
                return;
            }
            for (Object key : expectedMap.keySet()) {
                Object e = expectedMap.get(key);
                Object a = actualMap.get(key);
                if (volatileKeys.contains(key)) {
                    if (!kind(e).equals(kind(a))) {
                        diffs.add(path + "." + key + ": expected a " + kind(e) + " but was " + a);
                    }
                } else {
                    compare(path + "." + key, e, a, volatileKeys, diffs);
                }
            }
        } else if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            if (expectedList.size() != actualList.size()) {
                diffs.add(path + ": expected " + expectedList.size() + " items but were " + actualList.size());
                return;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                compare(path + "[" + i + "]", expectedList.get(i), actualList.get(i), volatileKeys, diffs);
            }
        } else if (expected instanceof Number e && actual instanceof Number a) {
            if (Math.abs(e.doubleValue() - a.doubleValue()) > NUMBER_TOLERANCE) {
                diffs.add(path + ": expected " + e + " but was " + a);
            }
        } else if (!Objects.equals(expected, actual)) {
            diffs.add(path + ": expected " + expected + " but was " + actual);
        }
    }

    private static String kind(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof List) {
            return "array";
        }
        return value.getClass().getSimpleName().toLowerCase();
    }
}
