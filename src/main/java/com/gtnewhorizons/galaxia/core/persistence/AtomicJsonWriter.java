package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.annotation.Nonnull;

import com.google.gson.Gson;

/**
 * Writes JSON through a temp file and renames it over the target, so an interrupted write leaves the previous file
 * intact instead of truncating it.
 */
final class AtomicJsonWriter {

    private AtomicJsonWriter() {}

    static void write(@Nonnull File file, @Nonnull Gson gson, @Nonnull Object value, @Nonnull String failureContext) {
        File tmp = new File(file.getParent(), file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp)) {
            gson.toJson(value, writer);
        } catch (IOException e) {
            tmp.delete();
            throw new IllegalStateException(
                "[PERSIST] SAVE FAILED: " + failureContext + " write error " + file + ": " + e.getMessage(),
                e);
        }
        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallback) {
                throw new IllegalStateException(
                    "[PERSIST] SAVE FAILED: " + failureContext + " replace error " + file + ": "
                        + fallback.getMessage(),
                    fallback);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                "[PERSIST] SAVE FAILED: " + failureContext + " replace error " + file + ": " + e.getMessage(),
                e);
        }
    }
}
