package simplegenomehub.gui;

import simplegenomehub.util.fileio.SetNoteSupport;

import java.awt.Color;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Glue between the set dialogs / the browsing cards and {@link SetNoteSupport}: opens the
 * note editor, carries the notes of the ids the user typed over to the ids that are saved,
 * and paints a stored colour.
 */
final class SetNoteUiSupport {

    private static final Logger logger = Logger.getLogger(SetNoteUiSupport.class.getName());

    private SetNoteUiSupport() {
    }

    /** Opens the note editor for {@code keys}; {@code null} when the user cancelled. */
    static Map<String, SetNoteSupport.Note> editNotes(Window parent, String title, List<String> keys,
                                                      Map<String, SetNoteSupport.Note> currentNotes) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        SetNoteEditorDialog dialog = new SetNoteEditorDialog(parent, title, keys, currentNotes);
        dialog.setVisible(true);
        return dialog.isConfirmed() ? dialog.getNotes() : null;
    }

    /**
     * Moves the notes the user typed onto the ids that are actually written into the set.
     * A typed gene id that expands to several transcripts carries its note to all of them.
     */
    static LinkedHashMap<String, SetNoteSupport.Note> alignToSavedIds(List<String> inputOrder,
                                                                      Map<String, List<String>> resolvedByInput,
                                                                      Map<String, SetNoteSupport.Note> notes,
                                                                      List<String> savedIds) {
        LinkedHashMap<String, SetNoteSupport.Note> aligned = new LinkedHashMap<>();
        if (savedIds == null || savedIds.isEmpty() || notes == null || notes.isEmpty()) {
            return aligned;
        }

        Set<String> saved = new LinkedHashSet<>(savedIds);
        if (inputOrder != null) {
            for (String inputId : inputOrder) {
                if (inputId == null) {
                    continue;
                }
                String trimmedInputId = inputId.trim();
                SetNoteSupport.Note note = notes.get(trimmedInputId);
                if (note == null || note.isEmpty()) {
                    continue;
                }
                if (saved.contains(trimmedInputId)) {
                    aligned.putIfAbsent(trimmedInputId, note);
                }
                List<String> resolvedIds = resolvedByInput == null ? null : resolvedByInput.get(trimmedInputId);
                if (resolvedIds != null) {
                    for (String resolvedId : resolvedIds) {
                        if (resolvedId != null && saved.contains(resolvedId)) {
                            aligned.putIfAbsent(resolvedId, note);
                        }
                    }
                }
            }
        }

        // Notes loaded straight from a sidecar are already keyed by the saved ids.
        for (String savedId : savedIds) {
            SetNoteSupport.Note note = notes.get(savedId);
            if (note != null && !note.isEmpty()) {
                aligned.putIfAbsent(savedId, note);
            }
        }
        return aligned;
    }

    /**
     * Writes the sidecar of a saved set: one record per saved id, in the set's order. A
     * failing sidecar never fails the set itself, so the error only reaches the log.
     */
    static void writeNotes(File setFile, List<String> savedIds, Map<String, SetNoteSupport.Note> notes) {
        if (setFile == null) {
            return;
        }
        try {
            SetNoteSupport.write(setFile, savedIds, notes);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to save the set notes of " + setFile.getName(), ex);
        }
    }

    static Color colorOf(SetNoteSupport.Note note) {
        return note == null ? null : colorOf(note.getRgb());
    }

    private static Color colorOf(Integer rgb) {
        return rgb == null ? null : new Color(rgb);
    }

    /** True when white text reads better on {@code color} than the usual dark ink. */
    static boolean isDark(Color color) {
        if (color == null) {
            return false;
        }
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255d;
        return luminance < 0.55d;
    }

    static Color mix(Color base, Color other, float ratio) {
        if (base == null) {
            return other;
        }
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        int red = Math.round(base.getRed() * (1f - safeRatio) + other.getRed() * safeRatio);
        int green = Math.round(base.getGreen() * (1f - safeRatio) + other.getGreen() * safeRatio);
        int blue = Math.round(base.getBlue() * (1f - safeRatio) + other.getBlue() * safeRatio);
        return new Color(red, green, blue);
    }

    static Color darker(Color base, float ratio) {
        return mix(base, Color.BLACK, ratio);
    }

    static Color lighter(Color base, float ratio) {
        return mix(base, Color.WHITE, ratio);
    }
}
