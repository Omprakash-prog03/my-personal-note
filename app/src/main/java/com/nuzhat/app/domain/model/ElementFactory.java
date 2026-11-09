package com.nuzhat.app.domain.model;

import com.nuzhat.app.data.entity.CanvasElementEntity;
import com.nuzhat.app.domain.model.elements.AudioElement;
import com.nuzhat.app.domain.model.elements.DocumentElement;
import com.nuzhat.app.domain.model.elements.DrawElement;
import com.nuzhat.app.domain.model.elements.EnvelopeElement;
import com.nuzhat.app.domain.model.elements.ImageElement;
import com.nuzhat.app.domain.model.elements.LinkElement;
import com.nuzhat.app.domain.model.elements.ShapeElement;
import com.nuzhat.app.domain.model.elements.StickerElement;
import com.nuzhat.app.domain.model.elements.StickyNoteElement;
import com.nuzhat.app.domain.model.elements.TextElement;

/** Single construction point for canvas objects, so persistence and duplication stay in sync. */
public final class ElementFactory {

    private ElementFactory() {}

    public static CanvasElement create(ElementType type) {
        CanvasElement element;
        switch (type) {
            case IMAGE: element = new ImageElement(); break;
            case SHAPE: element = new ShapeElement(); break;
            case DRAWING: element = new DrawElement(); break;
            case STICKER: element = new StickerElement(); break;
            case STICKY_NOTE: element = new StickyNoteElement(); break;
            case ENVELOPE: element = new EnvelopeElement(); break;
            case DOCUMENT: element = new DocumentElement(); break;
            case AUDIO: element = new AudioElement(); break;
            case LINK_CARD: element = new LinkElement(); break;
            case TEXT:
            default: element = new TextElement(); break;
        }
        element.width = element.defaultWidth();
        element.height = element.defaultHeight();
        return element;
    }

    public static CanvasElement create(String typeName) {
        try {
            return create(ElementType.valueOf(typeName));
        } catch (Exception e) {
            return create(ElementType.TEXT);
        }
    }

    public static CanvasElement fromEntity(CanvasElementEntity entity) {
        CanvasElement element = create(entity.type);
        element.loadFromEntity(entity);
        return element;
    }
}
