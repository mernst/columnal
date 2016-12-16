package records.transformations.function;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import utility.ExConsumer;
import utility.Pair;

import java.time.DateTimeException;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by neil on 15/12/2016.
 */
public abstract class StringToTemporalFunction extends FunctionDefinition
{
    public StringToTemporalFunction(String name)
    {
        super(name);
    }

    static DateTimeFormatter m(String sep, F... items)
    {
        DateTimeFormatterBuilder b = new DateTimeFormatterBuilder();
        for (int i = 0; i < items.length; i++)
        {
            if (i != 0 && items[i] != F.FRAC_SEC_OPT && items[i] != F.AMPM)
                b.appendLiteral(sep);
            switch (items[i])
            {
                case FRAC_SEC_OPT:
                    // From http://stackoverflow.com/questions/30090710/java-8-datetimeformatter-parsing-for-optional-fractional-seconds-of-varying-sign
                    b.appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true);
                    break;
                case SEC:
                    b.appendValue(ChronoField.SECOND_OF_MINUTE, 2, 2, SignStyle.NEVER);
                    break;
                case MIN:
                    b.appendValue(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NEVER);
                    break;
                case HOUR:
                    b.appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NEVER);
                    break;
                case HOUR12:
                    b.appendValue(ChronoField.CLOCK_HOUR_OF_AMPM, 1, 2, SignStyle.NEVER);
                    break;
                case AMPM:
                    b.optionalStart().appendLiteral(" ").optionalEnd().appendText(ChronoField.AMPM_OF_DAY);
                    break;
                case DAY:
                    b.appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NEVER);
                    break;
                case MONTH_TEXT:
                    b.appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT);
                    break;
                case MONTH_NUM:
                    b.appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NEVER);
                    break;
                case YEAR2:
                    // From http://stackoverflow.com/questions/29490893/parsing-string-to-local-date-doesnt-use-desired-century
                    b.appendValueReduced(ChronoField.YEAR, 2, 2, Year.now().getValue() - 80);
                    break;
                case YEAR4:
                    b.appendValue(ChronoField.YEAR, 4, 4, SignStyle.NEVER);
                    break;
            }
        }
        return b.toFormatter();
    }

    static List<DateTimeFormatter> l(DateTimeFormatter... args)
    {
        return Arrays.asList(args);
    }

    @Override
    public final @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        // Must be either temporal, or string + string
        if (params.size() == 0 || params.size() > 2)
        {
            onError.accept("Must supply either 1 or 2 parameters");
            return null;
        }
        if (params.size() == 1 && params.get(0).isDateTime())
        {
            DataType t = params.get(0);
            if (!t.isDateTime() || !checkTemporalParam(t.getDateTimeInfo(), onError))
                return null;
            return new Pair<>(new FromTemporalInstance(), DataType.date(getResultType()));
        }
        else
        {
            if (!params.stream().allMatch(DataType::isText))
            {
                onError.accept("Parameters must be text (source string to convert, and a format string)");
                return null;
            }
            return new Pair<>(new FromStringInstance(), DataType.date(getResultType()));
        }

    }

    // Return true if fine, false if not
    abstract boolean checkTemporalParam(DateTimeInfo srcTemporalType, ExConsumer<String> onError) throws InternalException, UserException;

    abstract DateTimeInfo getResultType();

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType) throws UserException, InternalException
    {
        //TODO test giving units
        return new Pair<>(Collections.emptyList(), Collections.singletonList(newExpressionOfDifferentType.getType(t -> {
            try
            {
                return !t.isText() && (!t.isDateTime() || checkTemporalParam(t.getDateTimeInfo(), s -> {}));
            }
            catch (InternalException | UserException e)
            {
                return false;
            }
        })));
    }

    static enum F {FRAC_SEC_OPT, SEC, MIN, HOUR, HOUR12, AMPM, DAY, MONTH_TEXT, MONTH_NUM, YEAR2, YEAR4 }

    private class FromStringInstance extends FunctionInstance
    {
        private ArrayList<Pair<List<DateTimeFormatter>, Integer>> usedFormats = new ArrayList<>();
        private ArrayList<List<DateTimeFormatter>> unusedFormats = new ArrayList<>(getFormats());

        @Override
        public List<Object> getValue(int rowIndex, List<List<Object>> params) throws UserException
        {
            String src = (String) params.get(0).get(0);

            for (int i = 0; i < usedFormats.size(); i++)
            {
                Pair<List<DateTimeFormatter>, Integer> formats = usedFormats.get(i);
                List<Pair<DateTimeFormatter, Temporal>> possibilities = getPossibles(src, formats.getFirst());
                if (possibilities.size() == 1)
                {
                    // Didn't throw, so record as used once more:
                    usedFormats.set(i, formats.replaceSecond(formats.getSecond() + 1));
                    // We only need to sort if we passed the one before us (equal is still fine):
                    if (i > 0 && usedFormats.get(i).getSecond() < usedFormats.get(i - 1).getSecond())
                        Collections.sort(usedFormats, Comparator.comparing(Pair::getSecond));
                    return Collections.singletonList(possibilities.get(0).getSecond());
                }
                else if (possibilities.size() > 1)
                {
                    throw new UserException("Ambiguous date, can be parsed as " + possibilities.stream().map(p -> p.getSecond().toString()).collect(Collectors.joining(" or ")) + ".  Supply your own format string to disambiguate.");
                }
            }

            // Try other formats:
            for (Iterator<List<DateTimeFormatter>> iterator = unusedFormats.iterator(); iterator.hasNext(); )
            {
                List<DateTimeFormatter> formats = iterator.next();
                List<Pair<DateTimeFormatter, Temporal>> possibilities = getPossibles(src, formats);
                if (possibilities.size() == 1)
                {
                    // Didn't throw, so record as used:
                    iterator.remove();
                    // No need to sort; frequency 1 will always be at end of list:
                    usedFormats.add(new Pair<>(formats, 1));
                    return Collections.singletonList(possibilities.get(0).getSecond());
                }
                else if (possibilities.size() > 1)
                {
                    throw new UserException("Ambiguous date, can be parsed as " + possibilities.stream().map(p -> p.getSecond().toString()).collect(Collectors.joining(" or ")) + ".  Supply your own format string to disambiguate.");
                }
            }

            throw new UserException("Could not parse date/time: \"" + src + "\"");
        }

        @NotNull
        private List<Pair<DateTimeFormatter, Temporal>> getPossibles(String src, List<DateTimeFormatter> format)
        {
            List<Pair<DateTimeFormatter, Temporal>> possibilities = new ArrayList<>();
            for (DateTimeFormatter dateTimeFormatter : format)
            {
                try
                {
                    possibilities.add(new Pair<>(dateTimeFormatter, dateTimeFormatter.parse(src, StringToTemporalFunction.this::fromTemporal)));
                }
                catch (DateTimeParseException e)
                {
                    // Not this one, then...
                }
            }
            return possibilities;
        }
    }

    // If two formats may be mistaken for each other, put them in the same inner list:
    protected abstract List<List<@NonNull DateTimeFormatter>> getFormats();

    private class FromTemporalInstance extends FunctionInstance
    {
        @Override
        public List<Object> getValue(int rowIndex, List<List<Object>> params) throws UserException
        {
            try
            {
                return Collections.singletonList(fromTemporal((TemporalAccessor) params.get(0).get(0)));
            }
            catch (DateTimeException e)
            {
                throw new UserException("Could not convert to date: " + e.getLocalizedMessage(), e);
            }
        }
    }

    abstract Temporal fromTemporal(TemporalAccessor temporalAccessor);
}
