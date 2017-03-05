package records.transformations.function;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static records.transformations.function.ToTemporalFunction.F.DAY;
import static records.transformations.function.ToTemporalFunction.F.MONTH_NUM;
import static records.transformations.function.ToTemporalFunction.F.MONTH_TEXT;
import static records.transformations.function.ToTemporalFunction.F.YEAR2;
import static records.transformations.function.ToTemporalFunction.F.YEAR4;

/**
 * Created by neil on 14/12/2016.
 */
public class ToDate extends ToTemporalFunction
{
    public ToDate()
    {
        super("date", "date.short");
    }

    public static List<List<DateTimeFormatter>> FORMATS = Arrays.asList(
        l(m("/", DAY, MONTH_TEXT, YEAR4)), // dd/MMM/yyyy
        l(m("-", DAY, MONTH_TEXT, YEAR4)), // dd-MMM-yyyy
        l(m(" ", DAY, MONTH_TEXT, YEAR4)), // dd MMM yyyy

        l(m(" ", MONTH_TEXT, DAY, YEAR4)), // MMM dd yyyy

        l(m("-", YEAR4, MONTH_TEXT, DAY)), // yyyy-MMM-dd

        l(m("-", YEAR4, MONTH_NUM, DAY)), // yyyy-MM-dd

        l(m("/", DAY, MONTH_NUM, YEAR4), m("/", MONTH_NUM, DAY, YEAR4)), // dd/MM/yyyy or MM/dd/yyyy
        l(m("-", DAY, MONTH_NUM, YEAR4), m("-", MONTH_NUM, DAY, YEAR4)), // dd-MM-yyyy or MM-dd-yyyy
        l(m(".", DAY, MONTH_NUM, YEAR4), m(".", MONTH_NUM, DAY, YEAR4)), // dd.MM.yyyy or MM.dd.yyyy

        l(m("/", DAY, MONTH_NUM, YEAR2), m("/", MONTH_NUM, DAY, YEAR2)), // dd/MM/yy or MM/dd/yy
        l(m("-", DAY, MONTH_NUM, YEAR2), m("-", MONTH_NUM, DAY, YEAR2)), // dd-MM-yy or MM-dd-yy
        l(m(".", DAY, MONTH_NUM, YEAR2), m(".", MONTH_NUM, DAY, YEAR2)) // dd.MM.yy or MM.dd.yy
    );

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString("date.string"));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), "date.datetime"));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), "date.datetimez"));
        r.add(new FunctionType(FromYearMonth_Day::new, DataType.date(getResultType()), DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), DataType.number(new NumberInfo(mgr.loadBuiltIn("day"), 0))), "date.ym_d"));
        r.add(new FunctionType(FromNumbers::new, DataType.date(getResultType()), DataType.tuple(
            DataType.number(new NumberInfo(mgr.loadBuiltIn("year"), 0)),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("month"), 0)),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("day"), 0))
        ), "date.y_m_d"));
        return r;
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DataType.DateTimeInfo(DateTimeType.YEARMONTHDAY);
    }

    @Override
    protected List<List<@NonNull DateTimeFormatter>> getFormats()
    {
        return FORMATS;
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDate.from(temporalAccessor);
    }

    private class FromYearMonth_Day extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            YearMonth ym = (YearMonth) paramList[0];
            int day = Utility.requireInteger(paramList[1]);
            try
            {
                return LocalDate.of(ym.getYear(), ym.getMonth(), day);
            }
            catch (DateTimeException e)
            {
                throw new UserException("Invalid date: " + ym.getYear() + ", " + ym.getMonth() + ", " + day + " " + e.getLocalizedMessage(), e);
            }
        }
    }

    private class FromNumbers extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 3);
            int year = Utility.requireInteger(paramList[0]);
            int month = Utility.requireInteger(paramList[1]);
            int day = Utility.requireInteger(paramList[2]);
            try
            {
                return LocalDate.of(year, month, day);
            }
            catch (DateTimeException e)
            {
                throw new UserException("Invalid date: " + year + ", " + month + ", " + day + " " + e.getLocalizedMessage(), e);
            }
        }
    }
}
