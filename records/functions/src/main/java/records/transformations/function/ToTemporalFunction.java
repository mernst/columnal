package records.transformations.function;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 15/12/2016.
 */
public abstract class ToTemporalFunction
{
    // Public for testing purposes only
    public final FunctionDefinition _test_fromString(@FuncDocKey String name) throws InternalException
    {
        return fromString(name);
    }
    
    protected final FunctionDefinition fromString(@FuncDocKey String name) throws InternalException
    {
        return new FunctionDefinition(name) {
            @Override
            public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
            {
                return new FromStringInstance(name);
            }
        };
    }

    abstract DateTimeInfo getResultType();

    private class FromStringInstance extends ValueFunction
    {
        private ArrayList<Pair<List<DateTimeFormatter>, Integer>> usedFormats = new ArrayList<>();
        private ArrayList<List<DateTimeFormatter>> unusedFormats = new ArrayList<>(getFormats());
        private final String name;

        private FromStringInstance(String name)
        {
            this.name = name;
        }

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            String src = Utility.preprocessDate(Utility.cast(param, String.class));

            for (int i = 0; i < usedFormats.size(); i++)
            {
                Pair<List<DateTimeFormatter>, Integer> formats = usedFormats.get(i);
                List<Pair<DateTimeFormatter, @Value Temporal>> possibilities = getPossibles(src, formats.getFirst());
                if (possibilities.size() == 1)
                {
                    // Didn't throw, so record as used once more:
                    usedFormats.set(i, formats.replaceSecond(formats.getSecond() + 1));
                    // We only need to sort if we passed the one before us (equal is still fine):
                    if (i > 0 && usedFormats.get(i).getSecond() < usedFormats.get(i - 1).getSecond())
                        Collections.<Pair<List<DateTimeFormatter>, Integer>>sort(usedFormats, Comparator.<Pair<List<DateTimeFormatter>, Integer>, Integer>comparing(p -> p.getSecond()));
                    return possibilities.get(0).getSecond();
                }
                else if (possibilities.size() > 1)
                {
                    throw new UserException("Ambiguous date, can be parsed as " + possibilities.stream().map((Pair<DateTimeFormatter, @Value Temporal> p) -> p.getSecond().toString()).collect(Collectors.joining(" or ")) + ".  Supply your own format string to disambiguate.");
                }
            }

            // Try other formats:
            for (Iterator<List<DateTimeFormatter>> iterator = unusedFormats.iterator(); iterator.hasNext(); )
            {
                List<DateTimeFormatter> formats = iterator.next();
                List<Pair<DateTimeFormatter, @Value Temporal>> possibilities = getPossibles(src, formats);
                if (possibilities.size() == 1)
                {
                    // Didn't throw, so record as used:
                    iterator.remove();
                    // No need to sort; frequency 1 will always be at end of list:
                    usedFormats.add(new Pair<>(formats, 1));
                    return possibilities.get(0).getSecond();
                }
                else if (possibilities.size() > 1)
                {
                    throw new UserException("Ambiguous date, can be parsed as " + possibilities.stream().map((Pair<DateTimeFormatter, @Value Temporal> p) -> p.getSecond().toString()).collect(Collectors.joining(" or ")) + ".  Supply your own format string to disambiguate.");
                }
            }

            throw new UserException("Function " + name + " could not parse date/time: \"" + src + "\"");
        }

        @NonNull
        private List<Pair<DateTimeFormatter, @Value Temporal>> getPossibles(String src, List<DateTimeFormatter> format)
        {
            List<Pair<DateTimeFormatter, @Value Temporal>> possibilities = new ArrayList<>();
            for (DateTimeFormatter dateTimeFormatter : format)
            {
                try
                {
                    possibilities.add(new Pair<>(dateTimeFormatter, dateTimeFormatter.parse(src, ToTemporalFunction.this::fromTemporal)));
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
    protected final ImmutableList<ImmutableList<@NonNull DateTimeFormatter>> getFormats()
    {
        return getResultType().getFlexibleFormatters();
    }

    class FromTemporal extends FunctionDefinition
    {
        public FromTemporal(@FuncDocKey String funcDocKey) throws InternalException
        {
            super(funcDocKey);
        }

        @Override
        public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
        {
            return new ValueFunction()
            {
                @Override
                public @Value Object call (@Value Object param) throws UserException
                {
                    try
                    {
                        return fromTemporal((TemporalAccessor) param);
                    }
                    catch (DateTimeException e)
                    {
                        throw new UserException("Could not convert to date: " + e.getLocalizedMessage(), e);
                    }
                }
            };
        }
    }

    abstract @Value Temporal fromTemporal(TemporalAccessor temporalAccessor);

    abstract ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException;
}
