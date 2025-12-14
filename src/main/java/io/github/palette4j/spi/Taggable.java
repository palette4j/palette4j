package io.github.palette4j.spi;

import java.util.Map;

public interface Taggable {
    Taggable addTag(String key, Object value);
    Map<String, Object> getTags();
}
