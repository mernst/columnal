package records.gui;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Primitives;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@OnThread(Tag.Simulation)
public class UndoManager
{
    // Max number of backups for each file
    private static final int MAX_DETAILS = 20;
    private final HashFunction hashFunction = Hashing.goodFastHash(32);
    // From https://stackoverflow.com/questions/893977/java-how-to-find-out-whether-a-file-name-is-valids
    private static final int[] ILLEGAL_CHARACTERS = { '/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':' };


    class SaveDetails
    {
        // Path of the backed up file, not the original which was backed up:
        private final File backupFile;
        private final Instant instant;
        private final @Nullable HashCode hash;

        public SaveDetails(File backupFile, Instant instant, @Nullable HashCode hash)
        {
            this.backupFile = backupFile;
            this.instant = instant;
            this.hash = hash;
        }
    }
    
    private final HashMap<File, ArrayList<SaveDetails>> backups = new HashMap<>();
    
    @OnThread(Tag.Any)
    public UndoManager()
    {
    }
    
    public void backupForUndo(File file, Instant saveTime)
    {
        @Nullable File undoPath = null;
        @Nullable HashCode hashCode = null;
        try
        {
            File f = new File(Utility.getUndoDirectory(),
                    "undo-" + munge(file) + "-" + saveTime.toEpochMilli()
                    );
            
            Files.copy(file, f);
            // Only record once it's copied:
            undoPath = f;
            
            // Hash last as we can survive if it fails:
            hashCode = Files.asByteSource(file).hash(hashFunction);
        }
        catch (IOException e)
        {
            Log.log("Problem backing up", e);
        }
        
        if (undoPath != null)
        {
            ArrayList<SaveDetails> details = backups.merge(file, new ArrayList<>(), (old, blank) -> old);
            SaveDetails newSave = new SaveDetails(undoPath, saveTime, hashCode);
            if (!details.isEmpty() && Objects.equals(details.get(details.size() - 1).hash, hashCode))
            {
                // We always replace, as in rare case of hash collision, we will
                // at least keep most recent version:
                details.set(details.size() - 1, newSave);
            }
            else
            {
                details.add(newSave);
            }
            
            if (details.size() >= 2)
            {
                details.get(details.size() - 1).backupFile.deleteOnExit();
            }
            
            while (details.size() > MAX_DETAILS)
            {
                details.remove((int)Utility.streamIndexed(details).min(Comparator.comparing(p -> p.getSecond().instant)).map(Pair::getFirst).orElse(0)).backupFile.delete();
            }
        }
    }

    private static String munge(File file)
    {
        int[] replaced = file.getAbsolutePath().codePoints()
            .map(i -> Ints.asList(ILLEGAL_CHARACTERS).contains(i) ? '_' : i).toArray();
        return new String(replaced, 0, replaced.length);
    }

    /**
     * Undoes last change and returns the content as a String.
     */
    public @Nullable String undo(File file)
    {
        ArrayList<SaveDetails> details = backups.get(file);
        if (details != null)
        {
            SaveDetails latest = details.stream()
                .min(Comparator.comparing(d -> d.instant))
                .orElse(null);
            
            if (latest != null)
            {
                try
                {
                    return FileUtils.readFileToString(latest.backupFile, StandardCharsets.UTF_8);
                }
                catch (IOException e)
                {
                    Log.log(e);
                    return null;
                }
            }
        }
        return null;
    }
}
