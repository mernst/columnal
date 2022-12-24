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

package xyz.columnal.transformations;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import xyz.columnal.id.SaveTag;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.TableManager.TransformationLoader;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DisplayLexer;
import xyz.columnal.grammar.DisplayParser;
import xyz.columnal.grammar.MainParser.DetailPrefixedContext;
import xyz.columnal.grammar.MainParser.SourceNameContext;
import xyz.columnal.grammar.MainParser.TableContext;
import xyz.columnal.grammar.MainParser.TableIdContext;
import xyz.columnal.grammar.MainParser.TransformationContext;
import xyz.columnal.grammar.MainParser.TransformationNameContext;
import xyz.columnal.grammar.TableParser2;
import xyz.columnal.grammar.TableParser2.TableTransformationContext;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.List;

/**
 * Created by neil on 02/11/2016.
 */
public class TransformationManager implements TransformationLoader
{
    private static TransformationManager instance;

    public synchronized static TransformationManager getInstance()
    {
        if (instance == null)
            instance = new TransformationManager();
        return instance;
    }

    public List<TransformationInfo> getTransformations()
    {
        // Note: the order here is the order they are shown in the transformation edit dialog,
        // but is otherwise unimportant.
        return ImmutableList.of(
            new Calculate.Info(),
            new Aggregate.Info(),
            new Filter.Info(),
            new Sort.Info(),
            new Join.Info(),
            new Concatenate.Info(),
            new HideColumns.Info(),
            new ManualEdit.Info(),
            new RTransformation.Info(),
                
            // Not shown in dialog, as shown separately:
            new Check.Info()
        );
    }

    @Override
    public Transformation loadOne(TableManager mgr, TableContext table, ExpressionVersion expressionVersion) throws UserException, InternalException
    {
        try
        {
            TransformationContext transformationContext = table.transformation();
            TransformationNameContext transformationName = transformationContext.transformationName();
            TransformationInfo t = getTransformation(transformationName.getText());
            DetailPrefixedContext detailContext = transformationContext.detailPrefixed();
            String detail = Utility.getDetail(detailContext);
            @SuppressWarnings("identifier")
            List<TableId> source = Utility.<SourceNameContext, TableId>mapList(transformationContext.sourceName(), s -> new TableId(s.item().getText()));
            TableIdContext tableIdContext = transformationContext.tableId();
            @SuppressWarnings("identifier")
            Transformation transformation = t.load(mgr, Table.loadDetails(new TableId(tableIdContext.getText()), detailContext, table.display()), source, detail, expressionVersion);
            mgr.record(transformation);
            return transformation;
        }
        catch (NullPointerException e)
        {
            throw new UserException("Could not read transformation: failed to read data", e);
        }
    }

    @Override
    public Transformation loadOne(TableManager mgr, SaveTag saveTag, TableTransformationContext table, ExpressionVersion expressionVersion) throws UserException, InternalException
    {
        try
        {
            TableParser2.TransformationContext transformationContext = table.transformation();
            TableParser2.TransformationNameContext transformationName = transformationContext.transformationName();
            TransformationInfo t = getTransformation(transformationName.getText());
            TableParser2.DetailContext detailContext = transformationContext.detail();
            String detail = Utility.getDetail(detailContext);
            @SuppressWarnings("identifier")
            List<TableId> source = Utility.<TableParser2.SourceNameContext, TableId>mapList(transformationContext.sourceName(), s -> new TableId(s.item().getText()));
            TableParser2.TableIdContext tableIdContext = transformationContext.tableId();
            @SuppressWarnings("identifier")
            Transformation transformation = Utility.parseAsOne(Utility.getDetail(table.display().detail()), DisplayLexer::new, DisplayParser::new, p ->
                t.load(mgr, Table.loadDetails(new TableId(tableIdContext.getText()), saveTag, p.tableDisplayDetails()), source, detail, expressionVersion)
            );
            mgr.record(transformation);
            return transformation;
        }
        catch (NullPointerException e)
        {
            throw new UserException("Could not read transformation: failed to read data", e);
        }
    }

    private TransformationInfo getTransformation(String text) throws UserException
    {
        for (TransformationInfo t : getTransformations())
        {
            if (t.getCanonicalName().equals(text))
                return t;
        }
        throw new UserException("Transformation not found: \"" + text + "\"");
    }
}
