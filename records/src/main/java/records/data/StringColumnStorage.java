package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;
import utility.ExBiConsumer;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public class StringColumnStorage implements ColumnStorage<String>
{
    private final ArrayList<@Value String> values;
    private final DumbObjectPool<String> pool = new DumbObjectPool<>(String.class, 1000, null);
    private final @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet;
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private DataTypeValue dataType;

    public StringColumnStorage(@Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet)
    {
        values = new ArrayList<>();
        this.beforeGet = beforeGet;
    }

    public StringColumnStorage()
    {
        this(null);
    }

    @Override
    public int filled()
    {
        return values.size();
    }
    
    public @Value String get(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        if (beforeGet != null)
            beforeGet.accept(index, progressListener);
        if (index < 0 || index >= values.size())
            throw new InternalException("Attempting to access invalid element: " + index + " of " + values.size());
        return values.get(index);
    }

    @Override
    public void addAll(List<String> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (String s : items)
        {
            this.values.add(Utility.value(pool.pool(s)));
        }
    }

    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        /*
        if (longs != null)
        {
            for (long l : longs)
                if (l == SEE_BIGDEC)
                    return v -> v.number();
        }
        */
        if (dataType == null)
        {
            dataType = DataTypeValue.text((i, prog) -> get(i, prog));
        }
        return dataType;
    }
}
