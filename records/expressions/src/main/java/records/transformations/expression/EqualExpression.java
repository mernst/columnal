package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.typeExp.MutVar;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Random;

/**
 * Created by neil on 30/11/2016.
 */
public class EqualExpression extends NaryOpExpression
{
    public EqualExpression(List<@Recorded Expression> operands)
    {
        super(operands);
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new EqualExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "=";
    }


    @Override
    public @Nullable TypeExp checkNaryOp(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable TypeExp argType = checkAllOperandsSameType(new MutVar(this, TypeClassRequirements.require("Equatable", "=")), dataLookup, typeState, onError, p -> new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionNodeParent>>>(null, p.getOurType() instanceof NumTypeExp ? ImmutableList.copyOf(
                ExpressionEditorUtil.getFixesForMatchingNumericUnits(typeState, p)
        ) : ImmutableList.of()));
        if (argType == null)
            return null;
        else
            return onError.recordType(this, TypeExp.bool(this));
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        @Value Object first = expressions.get(0).getValue(state);
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object rhsVal = expressions.get(i).getValue(state);
            if (0 != Utility.compareValues(first, rhsVal))
                return DataTypeUtility.value(false);
        }

        return DataTypeUtility.value(true);
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }
}
