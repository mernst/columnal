package records.data;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

/**
 * A class keeping track of any pending table and/or column renames.  Column renames
 * are stored with a reference to the table that they originate from.
 */
@OnThread(Tag.Any)
public class TableAndColumnRenames
{
    private final ImmutableMap<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>> renames;
    private final @Nullable TableId defaultTableId;

    private TableAndColumnRenames(@Nullable TableId defaultTableId, ImmutableMap<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>> renames)
    {
        this.defaultTableId = defaultTableId;
        this.renames = renames;
    }
    
    public TableAndColumnRenames(ImmutableMap<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>> renames)
    {
        this(null, renames);
    }

    public TableId tableId(TableId tableId)
    {
        Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>> info = renames.get(tableId);
        if (info != null && info.getFirst() != null)
            return info.getFirst();
        else
            return tableId;
    }

    // Note: pass the OLD TableId, not the new one.  If you pass null, the default is used (if set)
    public Pair<@Nullable TableId, ColumnId> columnId(@Nullable TableId oldTableId, ColumnId columnId)
    {
        @Nullable Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>> info = null;
        if (oldTableId != null)
            info = renames.get(oldTableId);
        else if (defaultTableId != null)
            info = renames.get(defaultTableId);
        
        if (info != null)
            return info.<@Nullable TableId, ColumnId>map(t -> oldTableId != null || t == null ? t : (t.equals(defaultTableId) ? oldTableId : t), c -> c.getOrDefault(columnId, columnId));
        else
            return new Pair<>(oldTableId, columnId);
    }
    
    public static final TableAndColumnRenames EMPTY = new TableAndColumnRenames(ImmutableMap.of());

    public TableAndColumnRenames withDefaultTableId(TableId tableId)
    {
        return new TableAndColumnRenames(tableId, renames);
    }

    // Are we renaming the given table, or any of its columns?
    public boolean isRenamingTableId(TableId tableId)
    {
        return renames.containsKey(tableId);
    }
}
