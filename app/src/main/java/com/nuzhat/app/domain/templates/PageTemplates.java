package com.nuzhat.app.domain.templates;

import com.nuzhat.app.R;
import com.nuzhat.app.data.entity.BackgroundStyleFactory;
import com.nuzhat.app.domain.model.CanvasElement;
import com.nuzhat.app.domain.model.elements.ShapeElement;
import com.nuzhat.app.domain.model.elements.TextElement;
import com.nuzhat.app.utils.DateFormats;

import java.util.ArrayList;
import java.util.List;

/**
 * Starter layouts for new pages. A template only seeds editable objects (headings, labels, rules) —
 * never fake photos or placeholder art — so every page starts as real user content.
 */
public final class PageTemplates {

    public static final int INK = 0xFF2C251C;
    public static final int MUTED_INK = 0xFF7A6A58;

    /** One selectable starter. {@link #build} needs the page size so layouts scale to any format. */
    public static final class Template {
        public final String key;
        public final int labelRes;
        public final String backgroundKey;

        Template(String key, int labelRes, String backgroundKey) {
            this.key = key;
            this.labelRes = labelRes;
            this.backgroundKey = backgroundKey;
        }

        public String backgroundJson() {
            return BackgroundStyleFactory.presetJson(backgroundKey);
        }

        public List<CanvasElement> build(int pageWidth, int pageHeight) {
            List<CanvasElement> elements = new ArrayList<>();
            float margin = Math.max(48f, pageWidth * 0.07f);
            float width = pageWidth - margin * 2f;
            switch (key) {
                case "JOURNAL":
                    elements.add(heading(margin, pageHeight * 0.09f, width, "Journal", 46f));
                    elements.add(caption(margin, pageHeight * 0.09f + 66f, width,
                            DateFormats.longDate(System.currentTimeMillis()), 18f));
                    elements.add(rule(margin, pageHeight * 0.09f + 104f, width));
                    break;
                case "RESEARCH":
                    elements.add(heading(margin, pageHeight * 0.08f, width, "Research note", 40f));
                    float y = pageHeight * 0.16f;
                    String[] labels = {"Question", "Sources", "Findings", "Open threads"};
                    for (String label : labels) {
                        elements.add(caption(margin, y, width, label, 20f));
                        elements.add(rule(margin, y + 30f, width));
                        y += 110f;
                    }
                    break;
                case "QUOTE":
                    elements.add(centred(margin, pageHeight * 0.30f, width,
                            "A quote worth keeping", 38f));
                    elements.add(centred(margin, pageHeight * 0.44f, width, "— Author", 20f));
                    elements.add(rule(pageWidth * 0.35f, pageHeight * 0.40f, pageWidth * 0.30f));
                    break;
                case "INDEX":
                    elements.add(heading(margin, pageHeight * 0.08f, width, "Index", 42f));
                    float row = pageHeight * 0.16f;
                    for (int i = 1; i <= 6; i++) {
                        elements.add(caption(margin, row, 70f, String.format("%02d", i), 18f));
                        elements.add(rule(margin + 70f, row + 20f, width - 70f));
                        row += 84f;
                    }
                    break;
                default:
                    // "BLANK" intentionally seeds nothing; the paper itself is the template.
                    break;
            }
            return elements;
        }
    }

    private PageTemplates() {}

    public static List<Template> all() {
        List<Template> templates = new ArrayList<>();
        templates.add(new Template("JOURNAL", R.string.template_journal, "RULED"));
        templates.add(new Template("RESEARCH", R.string.template_research, "GRID"));
        templates.add(new Template("QUOTE", R.string.template_quote, "VINTAGE"));
        templates.add(new Template("INDEX", R.string.template_index, "PLAIN"));
        return templates;
    }

    public static Template blank() {
        return new Template("BLANK", R.string.bg_plain, "PLAIN");
    }

    private static TextElement heading(float x, float y, float width, String text, float size) {
        TextElement element = new TextElement();
        element.x = x;
        element.y = y;
        element.width = width;
        element.height = size * 1.6f;
        element.setText(text);
        element.setFontSize(size);
        element.setColour(INK);
        element.setBold(true);
        element.setAlign(TextElement.ALIGN_LEFT);
        element.zIndex = 1;
        return element;
    }

    private static TextElement caption(float x, float y, float width, String text, float size) {
        TextElement element = new TextElement();
        element.x = x;
        element.y = y;
        element.width = width;
        element.height = size * 2.2f;
        element.setText(text);
        element.setFontSize(size);
        element.setColour(MUTED_INK);
        element.setAlign(TextElement.ALIGN_LEFT);
        element.zIndex = 1;
        return element;
    }

    private static TextElement centred(float x, float y, float width, String text, float size) {
        TextElement element = new TextElement();
        element.x = x;
        element.y = y;
        element.width = width;
        element.height = size * 2.4f;
        element.setText(text);
        element.setFontSize(size);
        element.setColour(INK);
        element.setAlign(TextElement.ALIGN_CENTER);
        element.zIndex = 1;
        return element;
    }

    private static ShapeElement rule(float x, float y, float width) {
        ShapeElement shape = new ShapeElement();
        shape.setShape(ShapeElement.LINE);
        shape.x = x;
        shape.y = y;
        shape.width = width;
        shape.height = 2f;
        shape.setFillColour(INK);
        shape.setStrokeColour(INK);
        shape.setStrokeWidth(1.5f);
        shape.zIndex = 0;
        return shape;
    }
}
