package records.data;

import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryStringColumn extends Column
{
    private final StringColumnStorage storage;

    public MemoryStringColumn(RecordSet recordSet, ColumnId title, List<String> values) throws InternalException
    {
        super(recordSet, title);
        this.storage = new StringColumnStorage();
        this.storage.addAll(values);
    }

    public void add(String value) throws InternalException
    {
        storage.add(value);
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        return new MemoryStringColumn(rs, getName(), storage._test_getShrunk(shrunkLength));
    }
}
