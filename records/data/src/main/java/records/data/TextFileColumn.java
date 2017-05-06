package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnStorage.BeforeGet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.ExFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ReadState;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Created by neil on 22/10/2016.
 */
public final class TextFileColumn extends Column
{
    private final @Nullable String sep;
    private final int columnIndex;
    private final boolean lastColumn;
    private ReadState reader;
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    protected <S extends ColumnStorage<?>> TextFileColumn(RecordSet recordSet, ReadState reader, @Nullable String sep,
                             ColumnId columnName, int columnIndex, int totalColumns,
                             ExFunction<@Nullable BeforeGet<S>, S> createStorage,
                             ExBiConsumer<S, ArrayList<String>> addValues) throws InternalException, UserException
    {
        super(recordSet, columnName);
        this.sep = sep;
        this.reader = reader;
        this.columnIndex = columnIndex;
        this.lastColumn = columnIndex == totalColumns - 1;
        S theStorage = createStorage.apply((storage, rowIndex, prog) -> {
            try
            {
                while (rowIndex >= storage.filled())
                {
                    // Should we share loading across columns for the same file?
                    ArrayList<String> next = new ArrayList<>();
                    this.reader = Utility.readColumnChunk(this.reader, sep, columnIndex, next);
                    addValues.accept(storage, next);
                }
            }
            catch (IOException e)
            {
                throw new FetchException("Error reading file " + reader.getAbsolutePath(), e);
            }
        });
        type = theStorage.getType();

    }

    @Override
    @OnThread(Tag.Any)
    public final synchronized DataTypeValue getType() throws UserException, InternalException
    {
        return type;
    }

    @Override
    public DisplayValue storeValue(EnteredDisplayValue writtenValue) throws InternalException, UserException
    {
        throw new InternalException("Cannot edit data which is linked to a text file");
    }

    public static TextFileColumn dateColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, ColumnId columnName, int columnIndex, int totalColumns, DateTimeInfo dateTimeInfo, DateTimeFormatter dateTimeFormatter, TemporalQuery<? extends TemporalAccessor> query) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, columnName, columnIndex, totalColumns,
            (BeforeGet<TemporalColumnStorage> fill) -> new TemporalColumnStorage(dateTimeInfo, fill),
            (storage, values) -> storage.addAll(Utility.<String, TemporalAccessor>mapList(values, s -> dateTimeFormatter.parse(s, query)))
        );

    }

    public static TextFileColumn numericColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, ColumnId columnName, int columnIndex, int totalColumns, NumberInfo numberInfo, @Nullable UnaryOperator<String> processString) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, columnName, columnIndex, totalColumns,
            (BeforeGet<NumericColumnStorage> fill) -> new NumericColumnStorage(numberInfo, fill),
            (storage, values) ->
            {
                for (String value : values)
                {
                    String processed = value;
                    if (processString != null)
                        processed = processString.apply(processed);
                    storage.addRead(processed);
                }
            }
        );
    }

    public static TextFileColumn stringColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, ColumnId columnName, int columnIndex, int totalColumns) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, columnName, columnIndex, totalColumns,
            (BeforeGet<StringColumnStorage> fill) -> new StringColumnStorage(fill),
            (storage, values) -> storage.addAll(values)
        );
    }

    public static <DT extends DataType> TextFileColumn taggedColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, ColumnId columnName, int columnIndex, int totalColumns, TypeId typeName, List<TagType<DT>> tagTypes, ExFunction<String, TaggedValue> parseValue) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, columnName, columnIndex, totalColumns,
            (BeforeGet<TaggedColumnStorage> fill) -> new TaggedColumnStorage(typeName, tagTypes, fill),
            (storage, values) -> {
                storage.addAll(Utility.mapListEx(values, parseValue));
            }
        );
    }
}
