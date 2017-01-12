package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 16/12/2016.
 */
public class ToDateTime extends ToTemporalFunction
{
    public ToDateTime()
    {
        super("datetime");
    }

    private static List<List<DateTimeFormatter>> FORMATS = new ArrayList<>();

    @Override
    protected List<FunctionType> getOverloads(UnitManager mgr)
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString());
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionType(DateAndTimeInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY))));
        return r;
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.DATETIME);
    }

    @Override
    protected List<List<DateTimeFormatter>> getFormats()
    {
        return FORMATS();
    }

    static List<List<DateTimeFormatter>> FORMATS()
    {
        if (FORMATS.isEmpty())
        {
            for (@NonNull DateTimeFormatter timeFormat : ToTime.FORMATS)
            {
                for (List<DateTimeFormatter> dateFormats : ToDate.FORMATS)
                {
                    List<DateTimeFormatter> newFormatsSpace = Utility.<DateTimeFormatter, DateTimeFormatter>mapList(dateFormats, f -> new DateTimeFormatterBuilder().append(f).appendLiteral(" ").append(timeFormat).toFormatter());
                    List<DateTimeFormatter> newFormatsT = Utility.<DateTimeFormatter, DateTimeFormatter>mapList(dateFormats, f -> new DateTimeFormatterBuilder().append(f).appendLiteral("T").append(timeFormat).toFormatter());
                    FORMATS.add(newFormatsSpace);
                    FORMATS.add(newFormatsT);
                }
            }
        }
        return FORMATS;
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDateTime.from(temporalAccessor);
    }

    private class DateAndTimeInstance extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, ImmutableList<@Value Object> params) throws UserException, InternalException
        {
            return LocalDateTime.of((LocalDate)params.get(0), (LocalTime) params.get(1));
        }
    }
}
