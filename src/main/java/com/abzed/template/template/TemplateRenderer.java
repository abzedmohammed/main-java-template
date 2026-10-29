package com.abzed.template.template;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal {@code {{placeholder}}} interpolation. Unknown placeholders resolve to "". */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    public String render(String template, Map<String, ?> variables) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = variables == null ? null : variables.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
