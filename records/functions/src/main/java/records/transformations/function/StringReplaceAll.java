package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;

public class StringReplaceAll extends FunctionGroup
{
    public StringReplaceAll()
    {
        super("replace.all", "replace.all.short");
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return ImmutableList.of(new FunctionType(Instance::new, DataType.TEXT, DataType.tuple(DataType.TEXT, DataType.TEXT, DataType.TEXT), null));
    }

    private class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            @Value Object[] params = Utility.castTuple(param, 3);
            return DataTypeUtility.value(Utility.cast(params[2], String.class).replace(Utility.cast(params[0], String.class), Utility.cast(params[1], String.class)));
        }
    }
}
