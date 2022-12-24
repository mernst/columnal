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

package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.functions.TFunctionUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.function.FunctionList;

import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsFromText extends BackwardsProvider
{
    public BackwardsFromText(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, Object targetValue) throws InternalException, UserException
    {
        String val = DataTypeUtility.valueToString(targetValue);
        return ImmutableList.of(
            () -> new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), "from text to",
                new TypeLiteralExpression(TypeExpression.fromDataType(targetType)),
                TFunctionUtil.makeStringLiteral(val, r)
            )
        );
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, Object targetValue) throws InternalException, UserException
    {        
        String val = DataTypeUtility.valueToString(targetValue);
        return ImmutableList.of(
                () -> new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), "from text to",
                        new TypeLiteralExpression(TypeExpression.fromDataType(targetType)),
                        parent.make(DataType.TEXT, val, maxLevels - 1)
                )
        );
    }
}
