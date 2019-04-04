package records.data.datatype;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataType.TagTypeDetails;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The data-type of a homogeneously-typed list of values,
 * including a facility to get the item at a particular index.
 */
public final class DataTypeValue
{
    private final DataType dataType;
    private final @Nullable GetValue<@Value Number> getNumber;
    private final @Nullable GetValue<@Value String> getText;
    private final @Nullable GetValue<@Value TemporalAccessor> getDate;
    private final @Nullable GetValue<@Value Boolean> getBoolean;
    private final @Nullable GetValue<@Value TaggedValue> getTag;
    private final @Nullable GetValue<@Value Object @Value[]> getTuple;
    private final @Nullable GetValue<@Value ListEx> getArrayContent;

    // package-visible
    DataTypeValue(DataType dataType, @Nullable GetValue<@Value Number> getNumber, @Nullable GetValue<@Value String> getText, @Nullable GetValue<@Value TemporalAccessor> getDate, @Nullable GetValue<@Value Boolean> getBoolean, @Nullable GetValue<@Value TaggedValue> getTag, @Nullable GetValue<@Value Object @Value []> getTuple, @Nullable GetValue<@Value ListEx> getArrayContent)
    {
        this.dataType = dataType;
        this.getNumber = getNumber;
        this.getText = getText;
        this.getDate = getDate;
        this.getBoolean = getBoolean;
        this.getTag = getTag;
        this.getTuple = getTuple;
        this.getArrayContent = getArrayContent;
    }
    
    public static DataTypeValue bool(GetValue<@Value Boolean> getValue)
    {
        return new DataTypeValue(DataType.BOOLEAN,null, null, null, getValue, null, null, null);
    }

    public static DataTypeValue tagged(TypeId name, ImmutableList<Either<Unit, DataType>> tagTypeVariableSubsts, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> getTag)
    {
        return new DataTypeValue(DataType.tagged(name, tagTypeVariableSubsts, tagTypes),null, null, null, null, getTag, null, null);
    }

    public static DataTypeValue text(GetValue<@Value String> getText)
    {
        return new DataTypeValue(DataType.TEXT, null, getText, null, null, null, null, null);
    }

    public static DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> getDate)
    {
        return new DataTypeValue(DataType.date(dateTimeInfo),null, null, getDate, null, null, null, null);
    }

    public static DataTypeValue number(NumberInfo numberInfo, GetValue<@Value Number> getNumber)
    {
        return new DataTypeValue(DataType.number(numberInfo),getNumber, null, null, null, null, null, null);
    }

    public static DataTypeValue array(DataType innerType, GetValue<@Value ListEx> getContent)
    {
        return new DataTypeValue(DataType.array(innerType),null, null, null, null, null, null, getContent);
    }

    public static DataTypeValue tuple(List<DataType> types, GetValue<@Value Object @Value[]> getContent)
    {
        return new DataTypeValue(DataType.tuple(types), null, null, null, null, null, getContent, null);
    }

    public void setCollapsed(int rowIndex, Either<String, @Value Object> value) throws InternalException, UserException
    {
        applyGet(new DataTypeVisitorGet<Void>()
        {
            @SuppressWarnings("value")
            @OnThread(Tag.Simulation)
            private <T> void set(GetValue<@Value T> g, Class<T> castTo) throws UserException, InternalException
            {
                g.set(rowIndex, value.<@Value T>map(v -> castTo.cast(v)));
            }
            
            @Override
            @OnThread(Tag.Simulation)
            public Void number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                set(g, Number.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void text(GetValue<@Value String> g) throws InternalException, UserException
            {
                set(g, String.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                set(g, Boolean.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                set(g, TemporalAccessor.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                set(g, TaggedValue.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value []> g) throws InternalException, UserException
            {
                g.set(rowIndex, value.<@Value Object @Value []>mapInt(v -> Utility.castTuple(v, types.size())));
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                set(g, ListEx.class);
                return null;
            }
        });
    }

    @Pure
    public DataType getType()
    {
        return dataType;
    }

    public static class SpecificDataTypeVisitorGet<R> implements DataTypeVisitorGet<R>
    {
        private final @Nullable InternalException internal;
        private final @Nullable UserException user;
        private final @Nullable R value;

        public SpecificDataTypeVisitorGet(InternalException e)
        {
            this.internal = e;
            this.user = null;
            this.value = null;
        }

        public SpecificDataTypeVisitorGet(UserException e)
        {
            this.internal = null;
            this.user = e;
            this.value = null;
        }

        public SpecificDataTypeVisitorGet(R value)
        {
            this.value = value;
            this.internal = null;
            this.user = null;
        }

        @Override
        public R number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
        {
            return defaultOp("Unexpected number data type");
        }

        private R defaultOp(String msg) throws InternalException, UserException
        {
            if (internal != null)
                throw internal;
            if (user != null)
                throw user;
            if (value != null)
                return value;
            throw new InternalException(msg);
        }

        @Override
        public R text(GetValue<@Value String> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected text data type");
        }

        @Override
        public R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags, GetValue<@Value TaggedValue> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected tagged data type");
        }

        @Override
        public R bool(GetValue<@Value Boolean> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected boolean type");
        }

        @Override
        public R date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected date type");
        }

        @Override
        public R tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value []> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected tuple type");
        }

        @Override
        public R array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected array type");
        }
    }

    public static interface DataTypeVisitorGetEx<R, E extends Throwable>
    {
        R number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, E;
        R text(GetValue<@Value String> g) throws InternalException, E;
        R bool(GetValue<@Value Boolean> g) throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, E;

        R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, E;
        R tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value[]> g) throws InternalException, E;

        // Each item is an array.  The inner type gives the type
        // of each entry
        R array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, E;
    }

    public static interface DataTypeVisitorGet<R> extends DataTypeVisitorGetEx<R, UserException>
    {
        
    }
    

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    public final <R, E extends Throwable> R applyGet(DataTypeVisitorGetEx<R, E> visitor) throws InternalException, E
    {
        return dataType.apply(new DataTypeVisitorEx<R, E>()
        {
            @Override
            public R number(NumberInfo numberInfo) throws InternalException, E
            {
                return visitor.number(getNumber, numberInfo);
            }

            @Override
            public R text() throws InternalException, E
            {
                return visitor.text(getText);
            }

            @Override
            public R date(DateTimeInfo dateTimeInfo) throws InternalException, E
            {
                return visitor.date(dateTimeInfo, getDate);
            }

            @Override
            public R bool() throws InternalException, E
            {
                return visitor.bool(getBoolean);
            }

            @Override
            public R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, E
            {
                return visitor.tagged(typeName, typeVars, tags, getTag);
            }

            @Override
            public R tuple(ImmutableList<DataType> inner) throws InternalException, E
            {
                return visitor.tuple(inner, getTuple);
            }

            @Override
            public R array(DataType inner) throws InternalException, E
            {
                return visitor.array(inner, getArrayContent);
            }

            @Override
            public R function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, E
            {
                throw new InternalException("Cannot store collections of functions");
            }
        });
    }
/*
    public int getArrayLength(int index) throws InternalException, UserException
    {
        if (getArrayLength == null)
            throw new InternalException("Trying to get array length of non-array: " + this);
        return getArrayLength.apply(index);
    }*/

    public static interface GetValue<@Value T>
    {
        @OnThread(Tag.Simulation)
        @NonNull @Value T getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException;

        @OnThread(Tag.Simulation)
        default @NonNull @Value T get(int index) throws UserException, InternalException { return getWithProgress(index, null); }

        @OnThread(Tag.Simulation)
        default void set(int index, Either<String, @Value T> value) throws InternalException, UserException
        {
            throw new InternalException("Attempted to set value for uneditable column: " + getClass());
        };
    }


    /**
     * Gets the collapsed, dynamically typed value at the given index
     *
     * Number: Byte/Short/Integer/Long/BigDecimal
     * Text: String
     * Boolean: Boolean
     * Datetime: LocalDate/LocalTime/ZonedDateTime/....
     * Tagged type: TaggedValue
     * Tuple: array
     * Array: List
     */
    @OnThread(Tag.Simulation)
    public final @Value Object getCollapsed(int index) throws InternalException, UserException
    {
        return applyGet(new DataTypeVisitorGet<@Value Object>()
        {
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object text(GetValue<@Value String> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value[] >g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return g.get(index);
            }
        });
    }

    /**
     * Copies the type of this item, but allows you to pull the data from an arbitrary DataTypeValue
     * (i.e. not necessarily the original).  Useful for implementing concat, sort and similar, as it
     * allows arbitrary mapping of an index back to any index of any DataTypeValue.
     *
     * @param original The original type which we are copying from
     * @param getOriginalValueAndIndex A function which takes the final index which we are interested
     *                                 in and gives back the DataTypeValue to draw from, and the index
     *                                 to ask for that in that DataTypeValue.  Any DataTypeValue returned
     *                                 must be of the same type as original.
     */
    public static DataTypeValue copySeveral(DataType original, SimulationFunction<Integer, Pair<DataTypeValue, Integer>> getOriginalValueAndIndex) throws InternalException
    {
        return new DataTypeValue(original,
            DataTypeValue.<@Value Number>several(getOriginalValueAndIndex, dtv -> dtv.getNumber),
            DataTypeValue.<@Value String>several(getOriginalValueAndIndex, dtv -> dtv.getText),
            DataTypeValue.<@Value TemporalAccessor>several(getOriginalValueAndIndex, dtv -> dtv.getDate),
            DataTypeValue.<@Value Boolean>several(getOriginalValueAndIndex, dtv -> dtv.getBoolean),
            several(getOriginalValueAndIndex, dtv -> dtv.getTag),
            DataTypeValue.<@Value Object @Value[]>several(getOriginalValueAndIndex, dtv -> dtv.getTuple),
            several(getOriginalValueAndIndex, dtv -> dtv.getArrayContent));

    }

    public DataTypeValue copyReorder(SimulationFunction<Integer, Integer> mapToOriginalIndex) throws InternalException
    {
        return copySeveral(dataType, i -> new Pair<DataTypeValue, Integer>(this, mapToOriginalIndex.apply(i)));
    }

    private static <T> @Nullable GetValue<@Value T> several(SimulationFunction<Integer, Pair<DataTypeValue, Integer>> getOriginalIndex, @Nullable Function<DataTypeValue, @Nullable GetValue<@Value T>> g)
    {
        if (g == null)
            return null;
        @NonNull Function<DataTypeValue, @Nullable GetValue<@Value T>> gFinal = g;
        return (int destIndex, final @Nullable ProgressListener prog) -> {
            @OnThread(Tag.Simulation) @NonNull Pair<DataTypeValue, Integer> src = getOriginalIndex.apply(destIndex);
            @Nullable GetValue<@Value T> innerGet = gFinal.apply(src.getFirst());
            if (innerGet == null)
                throw new InternalException("Inner get in several was null");
            return innerGet.getWithProgress(src.getSecond(), prog == null ? null : prog);
        };
    }
    
    public static interface OverrideSet
    {
        @OnThread(Tag.Simulation)
        public void set(int index, Either<String, @Value Object> value) throws UserException;
    }

    /**
     * A copy of this DataTypeValue with the given set operation
     */
    public DataTypeValue withSet(OverrideSet set) throws InternalException
    {
        return applyGet(new DataTypeVisitorGetEx<DataTypeValue, InternalException>()
        {
            private <@NonNull @Value T> GetValue<@NonNull @Value T> overrideSet(GetValue<@NonNull @Value T> g)
            {
                return new GetValue<@NonNull @Value T>()
                {
                    @Override
                    public @NonNull @Value T getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        return g.getWithProgress(index, progressListener);
                    }

                    @Override
                    // @SuppressWarnings("nullness") // I guess checker thinks T could be @Nullable
                    public @OnThread(Tag.Simulation) void set(int index, Either<String, @NonNull @Value T> value) throws InternalException, UserException
                    {
                        set.set(index, value.<@Value Object>map(t -> t));
                    }
                };
            }
            
            @Override
            public DataTypeValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
            {
                return DataTypeValue.number(displayInfo, overrideSet(g));
            }

            @Override
            public DataTypeValue text(GetValue<@Value String> g) throws InternalException
            {
                return DataTypeValue.text(overrideSet(g));
            }

            @Override
            public DataTypeValue bool(GetValue<@Value Boolean> g) throws InternalException
            {
                return DataTypeValue.bool(overrideSet(g));
            }

            @Override
            public DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
            {
                return DataTypeValue.date(dateTimeInfo, overrideSet(g));
            }

            @Override
            public DataTypeValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException
            {
                return DataTypeValue.tagged(typeName, typeVars, tagTypes, overrideSet(g));
            }

            @Override
            public DataTypeValue tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value[]> g) throws InternalException
            {
                return DataTypeValue.tuple(types, overrideSet(g));
            }

            @Override
            public DataTypeValue array(DataType inner, GetValue<@Value ListEx> g) throws InternalException
            {
                return DataTypeValue.array(inner, overrideSet(g));
            }
        });
    }
}
