/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations.expression.explanation;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ExpressionStyler;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An explanation of how a value came to be calculated.
 * Used for display (with hyperlinks), and also potentially
 * for analysing which data locations were used to produce
 * a given value.
 */
public abstract class Explanation
{
    // Is this a normal fetch of an expression's value,
    // or is it being asked to match against a given value?
    // or is it constructing an implicit lambda function?
    public static enum ExecutionType {VALUE, MATCH, CALL_IMPLICIT};
    
    private final ExplanationSource expression;
    protected final ExecutionType executionType;
    private final EvaluateState evaluateState;
    private final Object result;
    private final ImmutableList<ExplanationLocation> directlyUsedLocations;
    private final ExplanationLocation resultIsLocation;

    protected Explanation(ExplanationSource expression, ExecutionType executionType, EvaluateState evaluateState, Object result,  ImmutableList<ExplanationLocation> directlyUsedLocations, ExplanationLocation resultIsLocation)
    {
        this.expression = expression;
        this.executionType = executionType;
        this.evaluateState = evaluateState;
        this.result = result;
        this.directlyUsedLocations = directlyUsedLocations;
        this.resultIsLocation = resultIsLocation;
    }

    /**
     * 
     * @param alreadyDescribed The set of already described explanations.  Do not
     *                         describe if this is the same explanation as one in the set.
     * @param hyperlinkLocation A function to hyperlink used locations so that they
     *                          jump to the right point in the data display.
     * @param skipIfTrivial If true, return null here if this is a trivial expression
     *                      (i.e. one that doesn't calculate, it just substitutes
     *                      a variable or column reference for a value)
     * @return
     * @throws InternalException
     * @throws UserException
     */
    public abstract StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException;

    public final ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
    {
        return directlyUsedLocations;
    }

    public final ExplanationLocation getResultIsLocation()
    {
        return resultIsLocation;
    }

    public abstract ImmutableList<Explanation> getDirectSubExplanations() throws InternalException;

    /**
     * Should we miss out explaining children if they are trivial?  If so return true.
     * This is true for things like tuples, lists and tag applications, where we don't
     * want to say x is 1, y is 2, (x, y) is (1, 2).  It's enough to say (x, y) is (1, 2)
     * and the user will work it out.
     * @return
     */
    public boolean excludeChildrenIfTrivial()
    {
        return false;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || !(o instanceof Explanation)) return false;

        Explanation that = (Explanation) o;

        if (!expression.equals(that.expression)) return false;
        if (!executionType.equals(that.executionType)) return false;
        ImmutableSet<String> usedVars = expression.allVariableReferences().collect(ImmutableSet.<String>toImmutableSet());
        if (!evaluateState.varFilteredTo(usedVars).equals(that.evaluateState.varFilteredTo(usedVars))) return false;
        if (!directlyUsedLocations.equals(that.directlyUsedLocations)) return false;
        if (!Objects.equals(resultIsLocation, that.resultIsLocation)) return false;
        try
        {
            if (!getDirectSubExplanations().equals(that.getDirectSubExplanations())) return false;
            // null compares equal to any value, for testing:
            if (result == null || that.result == null)
                return true;
            return Utility.compareValues(result, that.result) == 0;
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
            return false;
        }
    }

    @Override
    public int hashCode()
    {
        int result1 = expression.hashCode();
        result1 = 31 * result1 + executionType.hashCode();
        result1 = 31 * result1 + evaluateState.hashCode();
        result1 = 31 * result1 + directlyUsedLocations.hashCode();
        result1 = 31 * result1 + Objects.hash(resultIsLocation);
        try
        {
            result1 = 31 * result1 + getDirectSubExplanations().hashCode();
        }
        catch (InternalException e)
        {
            Log.log(e);
        }
        // We don't hash result.  This means we get more hash collisions,
        // but it's not invalid...
        return result1;
    }

    // Only used for testing:
    @Override
    public String toString()
    {
        try
        {
            return "Explanation{" +
                    "expression=" + expression +
                    ", executionType=" + executionType +
                    ", evaluateState.rowIndex=" + evaluateState._test_getOptionalRowIndex() +
                    ", evaluateState.vars=" + evaluateState._test_getVariables() +
                    ", result=" + result +
                    ", directlyUsedLocations=" + directlyUsedLocations +
                    ", resultIsLocation=" + resultIsLocation +
                    ", directSubExplanations:{\n" + getDirectSubExplanations().stream().map(o -> Arrays.stream(o.toString().split("\n", -1)).map(s -> "    " + s + "\n").collect(Collectors.joining()) + "\n").collect(Collectors.joining()) +
                    '}';
        }
        catch (InternalException e)
        {
            return "InternalException: " + e.getLocalizedMessage();
        }
    }

    public boolean isValue()
    {
        return executionType == ExecutionType.VALUE;
    }

    public boolean isDescribing(Expression e)
    {
        return expression == e;
    }
    
    // Marker interface for items which can be the (code) source of an explanation 
    public static interface ExplanationSource
    {
        public Stream<String> allVariableReferences();
    }
}
