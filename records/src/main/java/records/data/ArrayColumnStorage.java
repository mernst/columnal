package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * For some reason, thi is the difficult one to wrap your head around.  An array
 * is a vector of items, which could be a vector of strings, a vector of tagged items,
 * or a vector of arrays.  Several crucial facts:
 *
 *  - Since this is a column storage, each item is one row, which is itself an array.
 *  - This storage only stores the head (in Haskell terms)
 *    of the item, not the full item, which might be cached elsewhere (e.g. if this storage
 *    is the result of a sort, no point duplicating the array).
 *
 * Thus the storage is just a list of DataTypeValue, i.e. accessors of the array content.
 */
public class ArrayColumnStorage implements ColumnStorage<Pair<Integer, DataTypeValue>>
{
    // For arrays, each element is storage for an individual array element (i.e. a row)
    // Thus confusing, ColumnStorage here is being used as RowStorage (think of it as VectorStorage)
    private final ArrayList<Pair<Integer, DataTypeValue>> storage = new ArrayList<>();
    private final DataType innerType;
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    // Constructor for array version
    public ArrayColumnStorage(DataType innerToCopy) throws InternalException
    {
        innerType = innerToCopy;
        this.type = DataTypeValue.array(innerType, (i, prog) -> storage.get(i));
    }

    @Override
    public int filled()
    {
        return storage.size();
    }
/*
    public List<Object> get(int index) throws InternalException, UserException
    {
        return (List)storage.get(index).getFullList(type.getArrayLength(index));
    }*/

    @Override
    public void addAll(List<Pair<Integer, DataTypeValue>> items) throws InternalException
    {
        storage.addAll(items);
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }
}
