package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeId;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility.ListEx;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public class ColumnReference extends NonOperatorExpression
{
    public static enum ColumnReferenceType
    {
        // Column is in same table as referring item, use the same row as that item
        // E.g. if doing a transform, score_percent = score / 100;
        CORRESPONDING_ROW,

        // Column may or may not be in same table, use whole column as item,
        // e.g. if normalising, cost = cost {CORRESPONDING_ROW}/sum(cost{WHOLE_COLUMN})
        WHOLE_COLUMN;
    }
    private final @Nullable TableId tableName;
    private final ColumnId columnName;
    private @MonotonicNonNull Column column;
    private final ColumnReferenceType referenceType;

    public ColumnReference(@Nullable TableId tableName, ColumnId columnName, ColumnReferenceType referenceType)
    {
        this.tableName = tableName;
        this.columnName = columnName;
        this.referenceType = referenceType;
    }

    public ColumnReference(ColumnId columnName, ColumnReferenceType type)
    {
        this(null, columnName, type);
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable RecordSet recordSet = dataLookup.getTable(tableName);
        if (recordSet == null)
            throw new UserException("Could not find source table" + (tableName == null ? "" : tableName.getRaw()));
        column = recordSet.getColumn(columnName);
        switch (referenceType)
        {
            case CORRESPONDING_ROW:
                return onError.recordType(this, TypeExp.fromConcrete(this, column.getType()));
            case WHOLE_COLUMN:
                return onError.recordType(this, TypeExp.fromConcrete(this, DataType.array(column.getType())));
        }
        throw new InternalException("Unknown reference type: " + referenceType);
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        if (column == null)
            throw new InternalException("Attempting to fetch value despite type check failure");
        switch (referenceType)
        {
            case CORRESPONDING_ROW:
                return column.getType().getCollapsed(rowIndex);
            case WHOLE_COLUMN:
                @NonNull Column columnFinal = column;
                return DataTypeUtility.value(new ListEx() {

                    @Override
                    @OnThread(Tag.Simulation)
                    public int size() throws InternalException, UserException
                    {
                        return columnFinal.getLength();
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public @Value Object get(int index) throws UserException, InternalException
                    {
                        return columnFinal.getType().getCollapsed(index);
                    }
                });
        }
        throw new InternalException("Unknown reference type: " + referenceType);
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return (referenceType == ColumnReferenceType.WHOLE_COLUMN ? "@wholecolumn " : "@column ")
            + OutputBuilder.quotedIfNecessary(renames.columnId(tableName, columnName).getOutput());
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.concat(
            StyledString.s(referenceType == ColumnReferenceType.WHOLE_COLUMN ? GeneralExpressionEntry.ARROW_WHOLE : GeneralExpressionEntry.ARROW_SAME_ROW),
            StyledString.s(OutputBuilder.quotedIfNecessary(columnName.getOutput()))
        );
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.of(this);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        if (column == null)
            throw new InternalException("Asking for solver when type checking failed");
        Pair<@Nullable TableId, ColumnId> key = new Pair<>(tableName, columnName);
        return column.getType().apply(new DataTypeVisitor<Formula>()
        {
            @Override
            public Formula number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return columnVariables.computeIfAbsent(key, (Pair x) -> formulaManager.getRationalFormulaManager().makeVariable(columnName.getOutput()));
            }

            @Override
            public Formula text() throws InternalException, UserException
            {
                return columnVariables.computeIfAbsent(key, (Pair x) -> formulaManager.getBitvectorFormulaManager().makeVariable(32* MAX_STRING_SOLVER_LENGTH, columnName.getOutput()));
            }

            @Override
            public Formula date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                throw new UserException("Can't do dates...");
            }

            @Override
            public Formula tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                throw new UserException("Can't do tags...");
            }

            @Override
            public Formula tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                throw new UserException("Can't do tuples...");
            }

            @Override
            public Formula array(@Nullable DataType inner) throws InternalException, UserException
            {
                throw new UserException("Can't do arrays...");
            }

            @Override
            public Formula bool() throws InternalException, UserException
            {
                return columnVariables.computeIfAbsent(key, (Pair x) -> formulaManager.getBooleanFormulaManager().makeVariable(columnName.getOutput()));
            }
        });
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new GeneralExpressionEntry(new GeneralExpressionEntry.ColumnRef(this), p, s);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColumnReference that = (ColumnReference) o;

        if (tableName != null ? !tableName.equals(that.tableName) : that.tableName != null) return false;
        return columnName.equals(that.columnName);
    }

    @Override
    public int hashCode()
    {
        int result = tableName != null ? tableName.hashCode() : 0;
        result = 31 * result + columnName.hashCode();
        return result;
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO could replace with an invalid column name
        return null;
    }

    @Pure
    public @Nullable TableId getTableId()
    {
        return tableName;
    }

    public ColumnId getColumnId()
    {
        return columnName;
    }


    public ColumnReferenceType getReferenceType()
    {
        return referenceType;
    }
}


