package test.expressions;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import records.data.*;
import records.data.Table.InitialLoadDetails;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.error.UnimplementedException;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.ExplanationLocation;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Check;
import records.transformations.Check.CheckType;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.CheckedExp;
import records.transformations.expression.Expression.LocationInfo;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import styled.StyledString;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListExList;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@OnThread(Tag.Simulation)
public class TestExpressionExplanation
{
    private final TableManager tableManager;
    
    public TestExpressionExplanation() throws UserException, InternalException
    {
        tableManager = DummyManager.make();
        List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
        columns.add(bools("all true", true, true, true, true));
        columns.add(bools("half false", false, true, false, true));
        columns.add(bools("all false", false, false, false, false));
        tableManager.record(new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId("T1"), null, null), new EditableRecordSet(columns, () -> 4)));
        
        columns.clear();
        columns.add(nums("asc", 1, 2, 3, 4));
        tableManager.record(new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId("T2"), null, null), new EditableRecordSet(columns, () -> 4)));
    }

    private static SimulationFunction<RecordSet, EditableColumn> bools(String name, boolean... values)
    {
        return rs -> new MemoryBooleanColumn(rs, new ColumnId(name), Utility.<Boolean, Either<String, Boolean>>mapList(Booleans.asList(values), Either::right), false);
    }

    private static SimulationFunction<RecordSet, EditableColumn> nums(String name, Number... values)
    {
        return rs -> new MemoryNumericColumn(rs, new ColumnId(name), new NumberInfo(Unit.SCALAR), Utility.<Number, Either<String, Number>>mapList(Arrays.asList(values), Either::right), 0);
    }

    @Test
    public void testLiterals() throws Exception
    {
        testExplanation("1", e("1", null, 1, null));
        testExplanation("true", e("true", null, true, null));
    }
    
    @Test
    public void testExplainedElement() throws Exception
    {
        testExplanation("@call @function element(@entire T1:all true, 3)", e("@call @function element(@entire T1:all true, 3)", null, true, l("T1", "all true", 2), entire("T1", "all true"), lit(3)));
        testExplanation("@call @function element(@entire T2:asc, 2) > 5", e("@call @function element(@entire T2:asc, 2) > 5", null, false, null, e("@call @function element(@entire T2:asc, 2)", null, 2, l("T2", "asc", 1), entire("T2", "asc"), lit(2)), lit(5)));
        testExplanation("(@call @function element(@entire T2:asc, 1) < 5) & (@call @function element(@entire T2:asc, 2) = 5)",
            e("(@call @function element(@entire T2:asc, 1) < 5) & (@call @function element(@entire T2:asc, 2) = 5)", null, false, null,
                e("@call @function element(@entire T2:asc, 1) < 5", null, true, null,
                    e("@call @function element(@entire T2:asc, 1)", null, 1, l("T2", "asc", 0), entire("T2", "asc"), lit(1)),
                    lit(5)),
                e("@call @function element(@entire T2:asc, 2) = 5", null, false, null,
                        e("@call @function element(@entire T2:asc, 2)", null, 2, l("T2", "asc", 1), entire("T2", "asc"), lit(2)),
                        lit(5))
            )
        );
    }

    protected Explanation entire(String table, String column, Object... values) throws InternalException, UserException
    {
        return e("@entire " + table + ":" + column, null, new ListExList(TestUtil.streamFlattened(tableManager.getSingleTableOrThrow(new TableId(table)).getData().getColumn(new ColumnId(column))).collect(ImmutableList.toImmutableList())), l(table, column));
    }

    @Test
    public void testExplainedAll() throws Exception
    {
        testExplanation("@call @function all(@entire T1:all false, (? = true))", 
            e("@call @function all(@entire T1:all false, (? = true))", null, false, l("T1", "all false", 0),
                entire("T1", "all false"),
                    e("? = true", null, null, null),
                    e("? = true", q(false), false, null, e("?", q(false), false, null), lit(true))));
        testExplanation("@call @function all(@entire T1:half false, @function not)",
            e("@call @function all(@entire T1:half false, @function not)", null, false, l("T1", "half false", 1), entire("T1", "half false"), e("@function not", null, null, null)));
        testExplanation("@call @function all(@entire T2:asc, (? < 3))",
            e("@call @function all(@entire T2:asc, (? < 3))", null, false, l("T2", "asc", 2), entire("T2", "asc"), e("? < 3", null, false, null, e("?", null, 3, null), lit(3))));
        testExplanation("@call @function all(@entire T2:asc, (? = (1.8 \u00B1 1.2)))",
                e("@call @function all(@entire T2:asc, (? = (1.8 \u00B1 1.2)))", null, false, l("T2", "asc", 3), entire("T2", "asc"), e("? = (1.8 \u00B1 1.2)", null, false, null, e("?", null, 4, null), e("1.8 \u00B1 1.2", null, null, null, lit(new BigDecimal("1.8")), lit(new BigDecimal("1.2"))))));
        testExplanation("@call @function none(@entire T2:asc, (? <> (1.8 \u00B1 0.9)))",
                e("@call @function none(@entire T2:asc, (? <> (1.8 \u00B1 0.9)))", null, false, l("T2", "asc", 2), entire("T2", "asc"), e("? <> (1.8 \u00B1 0.9)", null, true, null, e("?", null, 3, null), e("1.8 \u00B1 0.9", null, null, null, lit(new BigDecimal("1.8")), lit(new BigDecimal("0.9"))))));
        
        testCheckExplanation("T1", "@column half false", CheckType.ALL_ROWS, e("@column half false", r(0), false, l("T1", "half false", 0)));

        testCheckExplanation("T1", "@if @column half false @then @column all false @else @column all true @endif", CheckType.ALL_ROWS, e("@if @column half false @then @column all false @else @column all true @endif", r(1), false, null,
            e("@column half false", r(1), true, l("T1", "half false", 1)),
            e("@column all false", r(1), false, l("T1", "all false", 1))
                ));

        testCheckExplanation("T1", "@match @column half false @case true @then @column all false @case false @then @column all true @endmatch", CheckType.ALL_ROWS, e("@match @column half false @case true @then @column all false @case false @then @column all true @endmatch", r(1), false, null,
                e("@column half false", r(1), true, l("T1", "half false", 1)),
                e("@column all false", r(1), false, l("T1", "all false", 1))
        ));
    }

    // No row index, and a mapping from a single implicit lambda arg param to the given value
    @SuppressWarnings("value")
    private Pair<OptionalInt, ImmutableMap<String, @Value Object>> q(Object value)
    {
        return new Pair<>(OptionalInt.empty(), ImmutableMap.of("?1", value));
    }

    // Just a row index, no variables
    private Pair<OptionalInt, ImmutableMap<String, @Value Object>> r(int rowIndex)
    {
        return new Pair<>(OptionalInt.of(rowIndex), ImmutableMap.<String, @Value Object>of());
    }    
    
    @SuppressWarnings("value")
    private Explanation e(String expressionSrc, @Nullable Pair<OptionalInt, ImmutableMap<String, @Value Object>> rowIndexAndVars, @Nullable Object result, @Nullable ExplanationLocation location, Explanation... children) throws InternalException, UserException
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression expression = Expression.parse(null, expressionSrc, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        EvaluateState evaluateState = new EvaluateState(typeManager, rowIndexAndVars == null ? OptionalInt.empty() : rowIndexAndVars.getFirst(), (m, e) -> {
            throw new InternalException("No type lookup in TestExpressionExplanation");
        });
        if (rowIndexAndVars != null)
        {
            for (Entry<String, Object> var : rowIndexAndVars.getSecond().entrySet())
            {
                evaluateState = evaluateState.add(var.getKey(), var.getValue());
            }
        }
        return new Explanation(expression, evaluateState, result, Utility.streamNullable(location).collect(ImmutableList.<ExplanationLocation>toImmutableList()))
        {
            @Override
            public @OnThread(Tag.Simulation) StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation) throws InternalException, UserException
            {
                return StyledString.s("No description in TestExpressionExplanation");
            }

            @Override
            public @OnThread(Tag.Simulation) ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
            {
                return ImmutableList.copyOf(children);
            }
        };
    }
    
    private Explanation lit(Object value) throws UserException, InternalException
    {
        return e(value.toString(), null, value, null);
    }

    private ExplanationLocation l(String tableName, String columnName)
    {
        return new ExplanationLocation(new TableId(tableName), new ColumnId(columnName));
    }

    private ExplanationLocation l(String tableName, String columnName, int rowIndex)
    {
        return new ExplanationLocation(new TableId(tableName), new ColumnId(columnName), DataItemPosition.row(rowIndex));
    }
    
    private void testExplanation(String src, Explanation expectedExplanation) throws Exception
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression expression = Expression.parse(null, src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));

        ErrorAndTypeRecorderStorer errorAndTypeRecorderStorer = new ErrorAndTypeRecorderStorer();
        CheckedExp typeCheck = expression.check(new MultipleTableLookup(null, tableManager, null), new TypeState(typeManager.getUnitManager(), typeManager), LocationInfo.UNIT_DEFAULT, errorAndTypeRecorderStorer);
        assertNotNull(errorAndTypeRecorderStorer.getAllErrors().collect(StyledString.joining("\n")).toPlain(), typeCheck);
        Explanation actual = expression.calculateValue(new EvaluateState(typeManager, OptionalInt.empty(), true, errorAndTypeRecorderStorer)).makeExplanation();
        assertEquals(expectedExplanation, actual);
    }

    private void testCheckExplanation(String srcTable, String src, CheckType checkType, @Nullable Explanation expectedExplanation) throws Exception
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression expression = Expression.parse(null, src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        
        Check check = new Check(tableManager, TestUtil.ILD, new TableId(srcTable), checkType, expression);
        boolean result = Utility.cast(check.getData().getColumns().get(0).getType().getCollapsed(0), Boolean.class);
        // null explanation means we expect a pass:
        assertEquals(expectedExplanation == null, result);
        assertEquals(expectedExplanation, check.getExplanation());
    }
}
